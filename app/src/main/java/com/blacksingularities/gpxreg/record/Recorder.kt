package com.blacksingularities.gpxreg.record

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.VibrationEffect
import android.os.Vibrator
import com.blacksingularities.gpxreg.data.AppSettings
import com.blacksingularities.gpxreg.data.Geo
import com.blacksingularities.gpxreg.data.LatLon
import com.blacksingularities.gpxreg.data.ModeDetection
import com.blacksingularities.gpxreg.data.Stats
import com.blacksingularities.gpxreg.data.StatsAccumulator
import com.blacksingularities.gpxreg.data.TrackDb
import com.blacksingularities.gpxreg.data.TrackPoint
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RecStatus { IDLE, RECORDING, PAUSED, AUTO_PAUSED }

/** Kawałek narysowanej ścieżki jednego koloru (środka transportu). */
data class PathPiece(val mode: TransportMode, val points: List<LatLon>)

data class SegmentLive(val mode: TransportMode, val start: Long, val stats: Stats)

data class RecState(
    val status: RecStatus = RecStatus.IDLE,
    val trackId: Long = 0,
    val name: String = "",
    val mode: TransportMode = TransportMode.WALK,
    val startedAt: Long = 0,
    /** Czas nagrywania bez pauz (ms). */
    val elapsed: Long = 0,
    val total: Stats = Stats(),
    val segment: Stats = Stats(),
    val segments: List<SegmentLive> = emptyList(),
    val path: List<PathPiece> = emptyList(),
    val waypoints: Int = 0,
    /** Propozycja zmiany środka transportu (tryb „proponuj”). */
    val suggestion: ModeDetector.Suggestion? = null,
    /** Nagranie przerwane (np. system zamknął aplikację) – czeka na wznowienie lub zakończenie. */
    val interrupted: Boolean = false,
    /** Ostatnia pozycja odrzucona z powodu słabej dokładności. */
    val poorSignal: Boolean = false,
) {
    val active: Boolean get() = status != RecStatus.IDLE
}

sealed interface RecEvent {
    data class ModeChanged(val from: TransportMode, val to: TransportMode, val auto: Boolean) : RecEvent
    data class Saved(val trackId: Long) : RecEvent
    data object Discarded : RecEvent
    data class WaypointAdded(val name: String) : RecEvent
    data object Overspeed : RecEvent
}

/**
 * Silnik nagrywania. Pozycje przychodzą z [LocationHub], przechodzą filtr dokładności i drgań
 * w miejscu, trafiają do bazy paczkami (odporne na zamknięcie aplikacji) i do pamięci – dla mapy
 * i liczników. Zmiana środka transportu zaczyna nowy fragment, który można potem wyeksportować
 * jako osobny plik GPX.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object Recorder {
    private lateinit var app: Context
    private lateinit var db: TrackDb
    private lateinit var settings: () -> AppSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Zapisy do bazy po kolei, w jednym wątku. */
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RecEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RecEvent> = _events.asSharedFlow()

    // Dane bieżącego nagrania (dostęp tylko z wątku [lock]).
    private val lock = Any()
    private val points = ArrayList<TrackPoint>()
    private val segmentIds = ArrayList<Long>()
    private val segmentModes = ArrayList<TransportMode>()
    private val pending = ArrayList<TrackPoint>()
    private var total = StatsAccumulator()
    private var segAcc = StatsAccumulator()
    private var pendingGap = false
    private var lastAccepted: TrackPoint? = null
    private var stillSince = 0L
    private var pausedAt: LatLon? = null
    private var activeAccum = 0L
    private var runningSince = 0L
    private var lastFlush = 0L
    private var overspeed = false
    private val detector = ModeDetector()
    private val segCache = HashMap<Long, Stats>()
    @Volatile
    private var switching = false
    private var collectJob: Job? = null
    private var tickJob: Job? = null

    fun init(context: Context, db: TrackDb, settings: () -> AppSettings) {
        app = context.applicationContext
        this.db = db
        this.settings = settings
    }

    // ------------------------------------------------------------ sterowanie (z interfejsu)

    /** Uruchamia usługę nagrywania (ona wywołuje [begin]). */
    fun requestStart(context: Context, mode: TransportMode) {
        val i = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START).putExtra("mode", mode.key)
        context.startForegroundService(i)
    }

    fun requestResumeInterrupted(context: Context) {
        val i = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_RECOVER)
        context.startForegroundService(i)
    }

    fun pause() = synchronized(lock) {
        val s = _state.value
        if (s.status != RecStatus.RECORDING && s.status != RecStatus.AUTO_PAUSED) return
        stopClock()
        pendingGap = true
        _state.value = s.copy(status = RecStatus.PAUSED, elapsed = activeAccum, suggestion = null)
        RecordingService.refresh(app)
    }

    fun resume() = synchronized(lock) {
        val s = _state.value
        if (s.status != RecStatus.PAUSED && s.status != RecStatus.AUTO_PAUSED) return
        if (s.status == RecStatus.PAUSED) startClock()
        pendingGap = true
        stillSince = 0
        detector.reset()
        _state.value = s.copy(status = RecStatus.RECORDING, interrupted = false)
        RecordingService.refresh(app)
    }

    /** Zakończenie i zapis. */
    fun stop() {
        val trackId = synchronized(lock) {
            val s = _state.value
            if (!s.active) return
            stopClock()
            s.trackId
        }
        finishCollecting()
        scope.launch(io) {
            flushNow()
            val kept = db.finishTrack(trackId)
            reset()
            _events.emit(if (kept) RecEvent.Saved(trackId) else RecEvent.Discarded)
            db.notifyChanged()
        }
        RecordingService.stop(app)
    }

    /** Odrzucenie nagrania bez zapisu. */
    fun discard() {
        val trackId = _state.value.trackId
        finishCollecting()
        scope.launch(io) {
            synchronized(lock) { pending.clear() }
            db.deleteTracks(listOf(trackId))
            reset()
            _events.emit(RecEvent.Discarded)
        }
        RecordingService.stop(app)
    }

    /**
     * Zmiana środka transportu w trakcie nagrania. Z [since] punkty od tej chwili przechodzą do
     * nowego fragmentu (przesiadka wykryta automatycznie wcześniej niż teraz).
     */
    fun switchMode(mode: TransportMode, since: Long? = null, auto: Boolean = false) {
        val s = _state.value
        if (!s.active || mode == s.mode || switching) {
            _state.value = s.copy(suggestion = null)
            return
        }
        switching = true
        scope.launch(io) {
            flushNow()
            synchronized(lock) {
                val curSeg = segmentIds.last()
                val curPoints = points.count { it.segmentId == curSeg }
                if (curPoints == 0) {
                    // Pusty fragment – zmieniamy tylko jego środek transportu.
                    db.setSegmentMode(curSeg, mode)
                    segmentModes[segmentModes.size - 1] = mode
                } else {
                    val from = since?.coerceAtLeast(points.first { it.segmentId == curSeg }.time + 1) ?: System.currentTimeMillis()
                    val newSeg = db.addSegment(s.trackId, segmentIds.size, mode, from)
                    if (since != null) {
                        db.movePointsFrom(s.trackId, curSeg, from, newSeg)
                        for (i in points.indices) {
                            val p = points[i]
                            if (p.segmentId == curSeg && p.time >= from) points[i] = p.copy(segmentId = newSeg)
                        }
                    }
                    segmentIds += newSeg
                    segmentModes += mode
                    rebuildSegmentAcc()
                }
                detector.reset()
                publish { it.copy(mode = mode, suggestion = null) }
            }
            switching = false
            _events.emit(RecEvent.ModeChanged(s.mode, mode, auto))
            RecordingService.refresh(app)
        }
    }

    fun dismissSuggestion() {
        _state.value = _state.value.copy(suggestion = null)
        detector.reset()
    }

    fun addWaypoint(name: String? = null) {
        val s = _state.value
        val p = synchronized(lock) { lastAccepted } ?: Telemetry.state.value.takeIf { it.hasFix }?.let {
            TrackPoint(it.time, it.lat, it.lon, it.alt)
        } ?: return
        val label = name?.takeIf { it.isNotBlank() } ?: tr("Znacznik {0}", s.waypoints + 1)
        scope.launch(io) {
            db.addWaypoint(s.trackId, p.time, p.lat, p.lon, p.ele, label)
            publish { it.copy(waypoints = it.waypoints + 1) }
            _events.emit(RecEvent.WaypointAdded(label))
        }
    }

    // ------------------------------------------------------------ z usługi

    /** Nowe nagranie. */
    fun begin(mode: TransportMode) {
        if (_state.value.active) return
        val now = System.currentTimeMillis()
        val (trackId, segId) = db.startTrack(Units.defaultTrackName(now), now, mode)
        synchronized(lock) {
            reset()
            segmentIds += segId
            segmentModes += mode
            startClock()
            _state.value = RecState(
                status = RecStatus.RECORDING,
                trackId = trackId,
                name = Units.defaultTrackName(now),
                mode = mode,
                startedAt = now,
                segments = listOf(SegmentLive(mode, now, Stats())),
            )
        }
        startCollecting()
    }

    /**
     * Odtwarza nagranie przerwane przez system (z bazy) – po ponownym uruchomieniu usługi albo
     * na żądanie użytkownika. [resume] – od razu nagrywa dalej, inaczej czeka w pauzie.
     */
    fun recover(resume: Boolean): Boolean {
        if (_state.value.active) return true
        val trackId = db.activeTrackId() ?: return false
        val track = db.track(trackId) ?: return false
        val segs = db.segments(trackId)
        if (segs.isEmpty()) return false
        val pts = db.points(trackId)
        synchronized(lock) {
            reset()
            segs.forEach {
                segmentIds += it.id
                segmentModes += it.mode
            }
            points.addAll(pts)
            pts.forEach { total.add(it) }
            rebuildSegmentAcc()
            lastAccepted = pts.lastOrNull()
            activeAccum = track.stats.duration
            pendingGap = true
            if (resume) startClock()
            publish {
                it.copy(
                    status = if (resume) RecStatus.RECORDING else RecStatus.PAUSED,
                    trackId = trackId,
                    name = track.name,
                    mode = segs.last().mode,
                    startedAt = track.startTime,
                    waypoints = db.waypoints(trackId).size,
                    interrupted = !resume,
                )
            }
        }
        startCollecting()
        return true
    }

    /** Przy starcie aplikacji: czy jest nagranie bez działającej usługi. */
    fun checkInterrupted() {
        if (_state.value.active) return
        scope.launch(io) { if (db.activeTrackId() != null) recover(resume = false) }
    }

    // ------------------------------------------------------------ wewnętrzne

    private fun startCollecting() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch { LocationHub.fixes.collect { onFix(it) } }
        tickJob = scope.launch {
            while (isActive) {
                delay(1000)
                synchronized(lock) {
                    val s = _state.value
                    if (s.status == RecStatus.RECORDING) _state.value = s.copy(elapsed = elapsedNow())
                }
                if (System.currentTimeMillis() - lastFlush > 10_000) launch(io) { flushNow() }
            }
        }
    }

    private fun finishCollecting() {
        collectJob?.cancel()
        tickJob?.cancel()
        collectJob = null
        tickJob = null
    }

    private fun startClock() {
        if (runningSince == 0L) runningSince = System.currentTimeMillis()
    }

    private fun stopClock() {
        if (runningSince != 0L) activeAccum += System.currentTimeMillis() - runningSince
        runningSince = 0L
    }

    private fun elapsedNow() = activeAccum + if (runningSince != 0L) System.currentTimeMillis() - runningSince else 0L

    private fun onFix(l: Location) {
        val cfg = settings()
        val st = _state.value
        if (st.status != RecStatus.RECORDING && st.status != RecStatus.AUTO_PAUSED) return
        val acc = if (l.hasAccuracy()) l.accuracy.toDouble() else null
        if (acc != null && acc > cfg.maxAccuracy) {
            if (!st.poorSignal) _state.value = st.copy(poorSignal = true)
            return
        }
        val speed = if (l.hasSpeed()) l.speed.toDouble() else null
        synchronized(lock) {
            val last = lastAccepted
            val now = l.time
            if (last != null && now - last.time < cfg.interval * 1000L - 250) return
            val moved = last?.let { Geo.distance(it.lat, it.lon, l.latitude, l.longitude) } ?: Double.MAX_VALUE
            val v = speed ?: if (last != null && now > last.time) moved / ((now - last.time) / 1000.0) else 0.0

            // Automatyczna pauza: postój dłużej niż [autoPauseDelay] s.
            if (cfg.autoPause) {
                if (st.status == RecStatus.AUTO_PAUSED) {
                    val from = pausedAt
                    val away = from?.let { Geo.distance(it.lat, it.lon, l.latitude, l.longitude) } ?: 0.0
                    if (v > 1.2 || away > maxOf(20.0, (acc ?: 10.0) * 2)) {
                        startClock()
                        pendingGap = true
                        stillSince = 0
                        publish { it.copy(status = RecStatus.RECORDING) }
                        RecordingService.refresh(app)
                    } else {
                        return
                    }
                } else if (v < 0.5 && last != null && moved < maxOf(8.0, acc ?: 8.0)) {
                    if (stillSince == 0L) stillSince = now
                    if (now - stillSince > cfg.autoPauseDelay * 1000L) {
                        stopClock()
                        pausedAt = LatLon(l.latitude, l.longitude)
                        publish { it.copy(status = RecStatus.AUTO_PAUSED, elapsed = activeAccum) }
                        RecordingService.refresh(app)
                        return
                    }
                } else {
                    stillSince = 0
                }
            }

            // Drgania w miejscu: bez ruchu zapisujemy co najwyżej punkt na 30 s.
            if (last != null && !pendingGap && v < 0.3 && moved < maxOf(3.0, (acc ?: 5.0) * 0.5) && now - last.time < 30_000) return

            val pressure = SensorHub.pressure.value.takeIf { cfg.useBarometer && !it.isNaN() }?.toDouble()
            val p = TrackPoint(
                time = now,
                lat = l.latitude,
                lon = l.longitude,
                ele = if (l.hasAltitude()) l.altitude else null,
                // Bez prędkości z odbiornika – wyliczona z przesunięcia (tylko gdy odstęp jest sensowny).
                speed = speed ?: v.takeIf { last != null && !pendingGap && now - last.time in 500..30_000 },
                course = if (l.hasBearing()) l.bearing.toDouble() else last?.let { Geo.bearing(it.lat, it.lon, l.latitude, l.longitude) }?.takeIf { moved > 2 },
                accuracy = acc,
                vAccuracy = if (l.hasVerticalAccuracy()) l.verticalAccuracyMeters.toDouble() else null,
                sats = LocationHub.gnss.value.used.takeIf { it > 0 },
                pressure = pressure,
                gap = pendingGap && last != null,
                segmentId = segmentIds.last(),
            )
            pendingGap = false
            lastAccepted = p
            points += p
            pending += p
            total.add(p)
            segAcc.add(p)

            // Środek transportu.
            var suggestion = st.suggestion
            if (cfg.modeDetection != ModeDetection.OFF) {
                detector.add(now, v, p.ele, st.mode)?.let { sug ->
                    if (cfg.modeDetection == ModeDetection.AUTO) {
                        scope.launch { switchMode(sug.mode, sug.since, auto = true) }
                    } else if (suggestion?.mode != sug.mode) {
                        suggestion = sug
                    }
                }
            }

            // Ostrzeżenie o prędkości (z histerezą 5%).
            if (cfg.speedAlert > 0) {
                if (!overspeed && v > cfg.speedAlert) {
                    overspeed = true
                    vibrate()
                    _events.tryEmit(RecEvent.Overspeed)
                } else if (overspeed && v < cfg.speedAlert * 0.95) {
                    overspeed = false
                }
            }

            val sugFinal = suggestion
            publish { it.copy(suggestion = sugFinal, poorSignal = false, elapsed = elapsedNow()) }
        }
        if (pending.size >= 10) scope.launch(io) { flushNow() }
    }

    /** Odświeża stan z akumulatorów (wywoływane pod [lock]). */
    private fun publish(change: (RecState) -> RecState) {
        val segs = segmentIds.indices.map { i ->
            val id = segmentIds[i]
            val sp = points.filter { it.segmentId == id }
            val stats = if (i == segmentIds.lastIndex) {
                segAcc.snapshot()
            } else {
                segCache.getOrPut(id) { StatsAccumulator.of(sp.mapIndexed { j, p -> if (j == 0) p.copy(gap = true) else p }) }
            }
            SegmentLive(segmentModes[i], sp.firstOrNull()?.time ?: 0L, stats)
        }
        val base = _state.value.copy(total = total.snapshot(), segment = segAcc.snapshot(), segments = segs, path = buildPath())
        _state.value = change(base)
    }

    /** Ścieżka do mapy: po jednym kawałku na fragment, przerwy po pauzach. */
    private fun buildPath(): List<PathPiece> {
        val out = ArrayList<PathPiece>()
        var cur = ArrayList<LatLon>()
        var curSeg = -1L
        for (p in points) {
            if (p.segmentId != curSeg || p.gap) {
                if (cur.size > 1) out += PathPiece(modeOf(curSeg), cur)
                // Kolejny fragment zaczyna się w ostatnim punkcie poprzedniego – linia bez przerwy.
                val bridge = if (p.segmentId != curSeg && !p.gap) cur.lastOrNull() else null
                cur = ArrayList()
                if (bridge != null) cur += bridge
                curSeg = p.segmentId
            }
            cur += LatLon(p.lat, p.lon)
        }
        if (cur.size > 1) out += PathPiece(modeOf(curSeg), cur)
        return out
    }

    private fun modeOf(segId: Long): TransportMode = segmentIds.indexOf(segId).let { if (it >= 0) segmentModes[it] else TransportMode.OTHER }

    private fun rebuildSegmentAcc() {
        segCache.clear()
        val cur = segmentIds.last()
        segAcc = StatsAccumulator()
        points.filter { it.segmentId == cur }.forEachIndexed { i, p -> segAcc.add(if (i == 0) p.copy(gap = true) else p) }
    }

    private fun flushNow() {
        val batch: List<TrackPoint>
        val s: RecState
        val segId: Long
        synchronized(lock) {
            batch = ArrayList(pending)
            pending.clear()
            s = _state.value
            segId = segmentIds.lastOrNull() ?: return
        }
        lastFlush = System.currentTimeMillis()
        if (s.trackId == 0L) return
        runCatching {
            db.insertPoints(s.trackId, batch)
            val end = batch.lastOrNull()?.time ?: s.startedAt
            db.saveLiveStats(s.trackId, s.total.copy(duration = elapsedNow()), segId, s.segment, end)
            s.segments.lastOrNull()?.let { if (it.start > 0) db.setSegmentStart(segId, it.start) }
        }
    }

    private fun reset() = synchronized(lock) {
        points.clear()
        segmentIds.clear()
        segmentModes.clear()
        pending.clear()
        total = StatsAccumulator()
        segAcc = StatsAccumulator()
        pendingGap = false
        lastAccepted = null
        stillSince = 0
        pausedAt = null
        activeAccum = 0
        runningSince = 0
        overspeed = false
        switching = false
        segCache.clear()
        detector.reset()
        _state.value = RecState()
    }

    private fun vibrate() {
        if (!settings().haptics) return
        runCatching {
            app.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1))
        }
    }

    /** Wszystkie punkty bieżącego nagrania (np. do eksportu w trakcie). */
    suspend fun snapshotPoints(): List<TrackPoint> = withContext(Dispatchers.Default) { synchronized(lock) { ArrayList(points) } }
}
