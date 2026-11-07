package com.blacksingularities.gpxreg.record

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.blacksingularities.gpxreg.data.Geo
import com.blacksingularities.gpxreg.data.LatLon
import com.blacksingularities.gpxreg.data.TrackDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sqrt

/** Prowadzenie po trasie wzorcowej: odchylenie od śladu i dystans do końca. */
data class GuideState(
    val trackId: Long,
    val name: String,
    val points: List<LatLon>,
    val length: Double,
    /** Odległość od śladu (m); null – brak pozycji. */
    val deviation: Double? = null,
    /** Dystans do końca trasy wzdłuż śladu (m). */
    val remaining: Double? = null,
    val offRoute: Boolean = false,
    val finished: Boolean = false,
)

/**
 * „Jedź tą trasą”: wybrana zapisana lub zaimportowana trasa jest rysowana na mapie nagrywania,
 * a po zjechaniu z niej (ponad [OFF_ROUTE] m) telefon wibruje. Wybór przetrwa restart aplikacji.
 */
object RouteGuide {
    private const val OFF_ROUTE = 60.0
    private const val BACK_ON = 35.0
    private const val MAX_POINTS = 3000

    private lateinit var app: Context
    private lateinit var db: TrackDb
    private var haptics: () -> Boolean = { true }
    private var cum = DoubleArray(0)
    private var lastIndex = 0

    private val _state = MutableStateFlow<GuideState?>(null)
    val state: StateFlow<GuideState?> = _state.asStateFlow()

    private val prefs get() = app.getSharedPreferences("route_guide", Context.MODE_PRIVATE)

    fun init(context: Context, db: TrackDb, scope: CoroutineScope, haptics: () -> Boolean) {
        app = context.applicationContext
        this.db = db
        this.haptics = haptics
        val saved = prefs.getLong("track", 0)
        scope.launch(Dispatchers.IO) {
            if (saved > 0) load(saved)
        }
        scope.launch { LocationHub.fixes.collect { onFix(it.latitude, it.longitude) } }
    }

    /** Ustawia trasę [trackId] jako wzorcową (wczytanie z bazy – wywoływać poza wątkiem UI). */
    fun follow(trackId: Long) {
        if (load(trackId)) prefs.edit().putLong("track", trackId).apply()
    }

    fun clear() {
        _state.value = null
        cum = DoubleArray(0)
        prefs.edit().remove("track").apply()
    }

    private fun load(trackId: Long): Boolean {
        val track = db.track(trackId) ?: return false.also { prefs.edit().remove("track").apply() }
        val all = db.points(trackId).map { LatLon(it.lat, it.lon) }
        if (all.size < 2) return false
        val pts = if (all.size > MAX_POINTS) Geo.simplify(all, MAX_POINTS) else all
        val c = DoubleArray(pts.size)
        for (i in 1 until pts.size) c[i] = c[i - 1] + Geo.distance(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
        cum = c
        lastIndex = 0
        _state.value = GuideState(trackId, track.name, pts, c.last())
        return true
    }

    private fun onFix(lat: Double, lon: Double) {
        val s = _state.value ?: return
        val pts = s.points
        if (pts.size < 2 || cum.size != pts.size) return
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        var bestIdx = lastIndex
        val kx = 111_320.0 * cos(Math.toRadians(lat))
        val ky = 110_540.0
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val ax = (a.lon - lon) * kx
            val ay = (a.lat - lat) * ky
            val bx = (b.lon - lon) * kx
            val by = (b.lat - lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 > 0) (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0) else 0.0
            val px = ax + t * dx
            val py = ay + t * dy
            var d = sqrt(px * px + py * py)
            // Na pętlach i trasach tam i z powrotem preferuj miejsca bliżej poprzedniej pozycji na śladzie.
            if (i < lastIndex - 50) d += 15.0
            if (d < best) {
                best = d
                bestIdx = i
                bestAlong = cum[i] + t * sqrt(len2)
            }
        }
        lastIndex = bestIdx
        val off = if (s.offRoute) best > BACK_ON else best > OFF_ROUTE
        val remaining = (s.length - bestAlong).coerceAtLeast(0.0)
        val finished = remaining < 30 && best < OFF_ROUTE
        if (off && !s.offRoute && Recorder.state.value.active) buzz()
        _state.value = s.copy(deviation = best, remaining = remaining, offRoute = off, finished = finished)
    }

    private fun buzz() {
        if (!haptics()) return
        runCatching {
            app.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250, 150, 400), -1))
        }
    }
}
