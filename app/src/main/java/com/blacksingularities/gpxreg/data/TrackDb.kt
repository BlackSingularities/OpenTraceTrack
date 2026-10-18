package com.blacksingularities.gpxreg.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.blacksingularities.gpxreg.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Baza tras: zwykły plik SQLite o czytelnym schemacie, który da się otworzyć dowolnym narzędziem
 * (DB Browser for SQLite, Python, Excel przez ODBC). Kopia zapasowa to po prostu kopia tego pliku,
 * a import scala trasy po identyfikatorze UUID – te, które już są w bazie, zostają pominięte.
 *
 * Jednostki w bazie: czas – ms od epoki (UTC), odległość i wysokość – metry, prędkość – m/s,
 * kurs – stopnie, ciśnienie – hPa.
 */
class TrackDb(private val context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    /** Zmienia się po każdej zmianie listy tras – ekrany odświeżają dane. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()
    fun notifyChanged() {
        _changes.value++
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) = createSchema(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Pierwsza wersja schematu – migracje dojdą tutaj.
    }

    // ---------------------------------------------------------------- trasy

    /** Nowe nagranie z pierwszym fragmentem; zwraca (id trasy, id fragmentu). */
    fun startTrack(name: String, start: Long, mode: TransportMode): Pair<Long, Long> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val trackId = db.insertOrThrow("tracks", null, ContentValues().apply {
                put("uuid", UUID.randomUUID().toString())
                put("name", name)
                put("notes", "")
                put("start_time", start)
                put("end_time", start)
                put("recording", 1)
                put("created", System.currentTimeMillis())
            })
            val segId = insertSegment(db, trackId, 0, mode, start)
            db.setTransactionSuccessful()
            return trackId to segId
        } finally {
            db.endTransaction()
            notifyChanged()
        }
    }

    fun addSegment(trackId: Long, index: Int, mode: TransportMode, start: Long): Long =
        insertSegment(writableDatabase, trackId, index, mode, start)

    private fun insertSegment(db: SQLiteDatabase, trackId: Long, index: Int, mode: TransportMode, start: Long): Long =
        db.insertOrThrow("segments", null, ContentValues().apply {
            put("track_id", trackId)
            put("idx", index)
            put("mode", mode.key)
            put("start_time", start)
            put("end_time", start)
        })

    fun insertPoints(trackId: Long, points: List<TrackPoint>) {
        if (points.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement(
                "INSERT INTO points(track_id, segment_id, time, lat, lon, ele, speed, course, accuracy, v_accuracy, sats, pressure, gap) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            )
            for (p in points) {
                st.clearBindings()
                st.bindLong(1, trackId)
                st.bindLong(2, p.segmentId)
                st.bindLong(3, p.time)
                st.bindDouble(4, p.lat)
                st.bindDouble(5, p.lon)
                p.ele?.let { st.bindDouble(6, it) }
                p.speed?.let { st.bindDouble(7, it) }
                p.course?.let { st.bindDouble(8, it) }
                p.accuracy?.let { st.bindDouble(9, it) }
                p.vAccuracy?.let { st.bindDouble(10, it) }
                p.sats?.let { st.bindLong(11, it.toLong()) }
                p.pressure?.let { st.bindDouble(12, it) }
                st.bindLong(13, if (p.gap) 1 else 0)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Zakończenie nagrania: przeliczenie podsumowań. Puste nagranie (mniej niż 2 punkty) jest usuwane. */
    fun finishTrack(trackId: Long): Boolean {
        val count = longQuery("SELECT COUNT(*) FROM points WHERE track_id=?", trackId)
        if (count < 2) {
            deleteTracks(listOf(trackId))
            return false
        }
        writableDatabase.execSQL("UPDATE tracks SET recording=0 WHERE id=?", arrayOf<Any>(trackId))
        recompute(trackId)
        return true
    }

    fun activeTrackId(): Long? = readableDatabase.rawQuery("SELECT id FROM tracks WHERE recording=1 ORDER BY start_time DESC LIMIT 1", null).use {
        if (it.moveToFirst()) it.getLong(0) else null
    }

    fun tracks(): List<Track> {
        val segs = HashMap<Long, MutableList<Segment>>()
        readableDatabase.rawQuery("SELECT * FROM segments ORDER BY track_id, idx", null).use { c ->
            while (c.moveToNext()) {
                val s = c.segment()
                segs.getOrPut(s.trackId) { mutableListOf() }.add(s)
            }
        }
        return readableDatabase.rawQuery("SELECT * FROM tracks ORDER BY start_time DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.track(segs)) }
        }
    }

    fun track(id: Long): Track? {
        val segs = hashMapOf(id to segments(id).toMutableList())
        return readableDatabase.rawQuery("SELECT * FROM tracks WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.track(segs) else null
        }
    }

    fun segments(trackId: Long): List<Segment> =
        readableDatabase.rawQuery("SELECT * FROM segments WHERE track_id=? ORDER BY idx", arrayOf(trackId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.segment()) }
        }

    fun points(trackId: Long): List<TrackPoint> =
        readableDatabase.rawQuery("SELECT * FROM points WHERE track_id=? ORDER BY time, id", arrayOf(trackId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.point()) }
        }

    fun rename(trackId: Long, name: String) = updateTrack(trackId, ContentValues().apply { put("name", name) })
    fun setNotes(trackId: Long, notes: String) = updateTrack(trackId, ContentValues().apply { put("notes", notes) })
    fun setFavorite(trackId: Long, fav: Boolean) = updateTrack(trackId, ContentValues().apply { put("favorite", if (fav) 1 else 0) })

    private fun updateTrack(id: Long, v: ContentValues) {
        writableDatabase.update("tracks", v, "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    fun deleteTracks(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) db.delete("tracks", "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    // ---------------------------------------------------------------- fragmenty

    fun setSegmentMode(segmentId: Long, mode: TransportMode) {
        writableDatabase.execSQL("UPDATE segments SET mode=? WHERE id=?", arrayOf<Any>(mode.key, segmentId))
        notifyChanged()
    }

    /**
     * Przenosi punkty od chwili [from] (włącznie) do fragmentu [targetSegment] – podział w trakcie
     * nagrywania, gdy zmiana środka transportu nastąpiła wcześniej, niż użytkownik ją zgłosił.
     */
    fun movePointsFrom(trackId: Long, fromSegment: Long, from: Long, targetSegment: Long) {
        writableDatabase.execSQL(
            "UPDATE points SET segment_id=? WHERE track_id=? AND segment_id=? AND time>=?",
            arrayOf<Any>(targetSegment, trackId, fromSegment, from),
        )
    }

    /** Dzieli fragment w chwili [at]; druga część dostaje [mode]. Zwraca id nowego fragmentu. */
    fun splitSegment(segmentId: Long, at: Long, mode: TransportMode): Long? {
        val seg = segment(segmentId) ?: return null
        val db = writableDatabase
        db.beginTransaction()
        val newId: Long
        try {
            db.execSQL("UPDATE segments SET idx=idx+1 WHERE track_id=? AND idx>?", arrayOf<Any>(seg.trackId, seg.index))
            newId = insertSegment(db, seg.trackId, seg.index + 1, mode, at)
            db.execSQL("UPDATE points SET segment_id=? WHERE segment_id=? AND time>=?", arrayOf<Any>(newId, segmentId, at))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        recompute(seg.trackId)
        return newId
    }

    /** Łączy fragment z następnym (zostaje środek transportu pierwszego). */
    fun mergeWithNext(segmentId: Long): Boolean {
        val seg = segment(segmentId) ?: return false
        val next = segments(seg.trackId).firstOrNull { it.index == seg.index + 1 } ?: return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE points SET segment_id=? WHERE segment_id=?", arrayOf<Any>(segmentId, next.id))
            db.delete("segments", "id=?", arrayOf(next.id.toString()))
            db.execSQL("UPDATE segments SET idx=idx-1 WHERE track_id=? AND idx>?", arrayOf<Any>(seg.trackId, next.index))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        recompute(seg.trackId)
        return true
    }

    /** Usuwa fragment razem z jego punktami (np. przypadkowy kawałek). Ostatniego nie da się usunąć. */
    fun deleteSegment(segmentId: Long): Boolean {
        val seg = segment(segmentId) ?: return false
        if (segments(seg.trackId).size <= 1) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("segments", "id=?", arrayOf(segmentId.toString()))
            db.execSQL("UPDATE segments SET idx=idx-1 WHERE track_id=? AND idx>?", arrayOf<Any>(seg.trackId, seg.index))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        recompute(seg.trackId)
        return true
    }

    fun segment(id: Long): Segment? = readableDatabase.rawQuery("SELECT * FROM segments WHERE id=?", arrayOf(id.toString())).use {
        if (it.moveToFirst()) it.segment() else null
    }

    /** Przelicza podsumowania fragmentów i trasy oraz miniaturę z zapisanych punktów. */
    fun recompute(trackId: Long) {
        val pts = points(trackId)
        val segs = segments(trackId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val bySeg = pts.groupBy { it.segmentId }
            for (s in segs) {
                val sp = bySeg[s.id].orEmpty()
                val st = StatsAccumulator.of(sp.mapIndexed { i, p -> if (i == 0) p.copy(gap = true) else p })
                db.update("segments", statsValues(st).apply {
                    if (sp.isNotEmpty()) {
                        put("start_time", sp.first().time)
                        put("end_time", sp.last().time)
                    }
                }, "id=?", arrayOf(s.id.toString()))
            }
            // Trasa: suma fragmentów (między fragmentami dystans też się liczy – to ciągła droga).
            val total = StatsAccumulator.of(pts)
            val preview = Geo.simplify(pts.map { LatLon(it.lat, it.lon) }, 80)
            db.update("tracks", statsValues(total).apply {
                if (pts.isNotEmpty()) {
                    put("start_time", pts.first().time)
                    put("end_time", pts.last().time)
                }
                put("preview", encodePreview(preview))
            }, "id=?", arrayOf(trackId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    /** Zapis podsumowań na żywo w trakcie nagrywania (bez czytania punktów). */
    fun saveLiveStats(trackId: Long, total: Stats, segmentId: Long, seg: Stats, end: Long) {
        val db = writableDatabase
        db.update("tracks", statsValues(total).apply { put("end_time", end) }, "id=?", arrayOf(trackId.toString()))
        db.update("segments", statsValues(seg).apply { put("end_time", end) }, "id=?", arrayOf(segmentId.toString()))
    }

    fun setSegmentStart(segmentId: Long, start: Long) {
        writableDatabase.execSQL("UPDATE segments SET start_time=? WHERE id=?", arrayOf<Any>(start, segmentId))
    }

    private fun statsValues(s: Stats) = ContentValues().apply {
        put("distance", s.distance)
        put("duration", s.duration)
        put("moving_time", s.movingTime)
        put("max_speed", s.maxSpeed)
        put("ascent", s.ascent)
        put("descent", s.descent)
        if (s.minEle != null) put("min_ele", s.minEle) else putNull("min_ele")
        if (s.maxEle != null) put("max_ele", s.maxEle) else putNull("max_ele")
        put("point_count", s.points)
    }

    // ---------------------------------------------------------------- punkty orientacyjne

    fun waypoints(trackId: Long): List<Waypoint> =
        readableDatabase.rawQuery("SELECT * FROM waypoints WHERE track_id=? ORDER BY time", arrayOf(trackId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Waypoint(
                            id = c.long("id"),
                            trackId = c.long("track_id"),
                            time = c.long("time"),
                            lat = c.double("lat"),
                            lon = c.double("lon"),
                            ele = c.doubleOrNull("ele"),
                            name = c.str("name"),
                            note = c.str("note"),
                        ),
                    )
                }
            }
        }

    fun addWaypoint(trackId: Long, time: Long, lat: Double, lon: Double, ele: Double?, name: String, note: String = ""): Long {
        val id = writableDatabase.insertOrThrow("waypoints", null, ContentValues().apply {
            put("track_id", trackId)
            put("time", time)
            put("lat", lat)
            put("lon", lon)
            if (ele != null) put("ele", ele)
            put("name", name)
            put("note", note)
        })
        notifyChanged()
        return id
    }

    fun updateWaypoint(id: Long, name: String, note: String) {
        writableDatabase.execSQL("UPDATE waypoints SET name=?, note=? WHERE id=?", arrayOf<Any>(name, note, id))
        notifyChanged()
    }

    fun deleteWaypoint(id: Long) {
        writableDatabase.delete("waypoints", "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------------------------------------------------------------- import

    data class ImportedSegment(val mode: TransportMode, val points: List<TrackPoint>)

    /** Zapisuje trasę z pliku (GPX lub kopii bazy). Zwraca id albo null, gdy UUID już istnieje. */
    fun importTrack(
        name: String,
        notes: String,
        segments: List<ImportedSegment>,
        waypoints: List<Waypoint>,
        uuid: String = UUID.randomUUID().toString(),
        favorite: Boolean = false,
        recompute: Boolean = true,
    ): Long? {
        val all = segments.flatMap { it.points }
        if (all.size < 2) return null
        if (longQuery("SELECT COUNT(*) FROM tracks WHERE uuid=?", uuid) > 0) return null
        val db = writableDatabase
        val trackId: Long
        db.beginTransaction()
        try {
            trackId = db.insertOrThrow("tracks", null, ContentValues().apply {
                put("uuid", uuid)
                put("name", name)
                put("notes", notes)
                put("start_time", all.minOf { it.time })
                put("end_time", all.maxOf { it.time })
                put("recording", 0)
                put("favorite", if (favorite) 1 else 0)
                put("created", System.currentTimeMillis())
            })
            var idx = 0
            for (s in segments) {
                if (s.points.isEmpty()) continue
                val segId = insertSegment(db, trackId, idx++, s.mode, s.points.first().time)
                insertPoints(trackId, s.points.map { it.copy(segmentId = segId) })
            }
            for (w in waypoints) {
                db.insertOrThrow("waypoints", null, ContentValues().apply {
                    put("track_id", trackId)
                    put("time", w.time)
                    put("lat", w.lat)
                    put("lon", w.lon)
                    if (w.ele != null) put("ele", w.ele)
                    put("name", w.name)
                    put("note", w.note)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (recompute) recompute(trackId)
        return trackId
    }

    // ---------------------------------------------------------------- kopia zapasowa

    /** Spójna kopia pliku bazy (bez trwającego nagrania nic się nie zmienia w trakcie kopiowania). */
    @Synchronized
    fun exportTo(target: File) {
        val db = writableDatabase
        runCatching { db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() } }
        context.getDatabasePath(NAME).copyTo(target, overwrite = true)
    }

    data class MergeResult(val imported: Int, val skipped: Int)

    /**
     * Scala trasy z innej bazy OpenTraceTrack. Trasy o istniejącym UUID są pomijane, więc ten sam plik
     * można importować wielokrotnie bez dublowania.
     */
    fun mergeFrom(file: File): MergeResult {
        val src = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            error(tr("To nie jest plik bazy SQLite"))
        }
        src.use { s ->
            val ok = s.rawQuery("SELECT value FROM meta WHERE key='app'", null).use { it.moveToFirst() && it.getString(0) == "gpxreg" }
            if (!ok) error(tr("Plik nie jest kopią bazy OpenTraceTrack"))
            var imported = 0
            var skipped = 0
            s.rawQuery("SELECT * FROM tracks WHERE recording=0", null).use { tc ->
                while (tc.moveToNext()) {
                    val oldId = tc.long("id")
                    val uuid = tc.str("uuid")
                    if (longQuery("SELECT COUNT(*) FROM tracks WHERE uuid=?", uuid) > 0) {
                        skipped++
                        continue
                    }
                    val segs = s.rawQuery("SELECT * FROM segments WHERE track_id=? ORDER BY idx", arrayOf(oldId.toString())).use { sc ->
                        buildList { while (sc.moveToNext()) add(sc.segment()) }
                    }
                    val pts = s.rawQuery("SELECT * FROM points WHERE track_id=? ORDER BY time, id", arrayOf(oldId.toString())).use { pc ->
                        buildList { while (pc.moveToNext()) add(pc.point()) }
                    }.groupBy { it.segmentId }
                    val wpts = s.rawQuery("SELECT * FROM waypoints WHERE track_id=?", arrayOf(oldId.toString())).use { wc ->
                        buildList {
                            while (wc.moveToNext()) {
                                add(Waypoint(0, 0, wc.long("time"), wc.double("lat"), wc.double("lon"), wc.doubleOrNull("ele"), wc.str("name"), wc.str("note")))
                            }
                        }
                    }
                    val id = importTrack(
                        name = tc.str("name"),
                        notes = tc.str("notes"),
                        segments = segs.map { ImportedSegment(it.mode, pts[it.id].orEmpty()) },
                        waypoints = wpts,
                        uuid = uuid,
                        favorite = tc.long("favorite") == 1L,
                    )
                    if (id != null) imported++ else skipped++
                }
            }
            notifyChanged()
            return MergeResult(imported, skipped)
        }
    }

    // ---------------------------------------------------------------- statystyki

    data class ModeTotal(val mode: TransportMode, val distance: Double, val movingTime: Long, val tracks: Int, val maxSpeed: Double)

    fun totalsByMode(since: Long = 0): List<ModeTotal> = readableDatabase.rawQuery(
        "SELECT s.mode, SUM(s.distance), SUM(s.moving_time), COUNT(DISTINCT s.track_id), MAX(s.max_speed) " +
            "FROM segments s JOIN tracks t ON t.id=s.track_id WHERE t.recording=0 AND s.start_time>=? " +
            "GROUP BY s.mode ORDER BY SUM(s.distance) DESC",
        arrayOf(since.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(ModeTotal(TransportMode.fromKey(c.getString(0)), c.getDouble(1), c.getLong(2), c.getInt(3), c.getDouble(4)))
        }
    }

    /** Dystans w kolejnych miesiącach (klucz „2026-10”). */
    fun monthlyDistance(): Map<String, Double> = readableDatabase.rawQuery(
        "SELECT strftime('%Y-%m', start_time/1000, 'unixepoch', 'localtime') m, SUM(distance) FROM tracks WHERE recording=0 GROUP BY m",
        null,
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getDouble(1)) } }

    // ---------------------------------------------------------------- pomocnicze

    private fun longQuery(sql: String, vararg args: Any): Long =
        readableDatabase.rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { if (it.moveToFirst()) it.getLong(0) else 0 }

    private fun Cursor.idx(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.long(name: String) = getLong(idx(name))
    private fun Cursor.double(name: String) = getDouble(idx(name))
    private fun Cursor.str(name: String) = getString(idx(name)) ?: ""
    private fun Cursor.doubleOrNull(name: String): Double? = idx(name).let { if (isNull(it)) null else getDouble(it) }
    private fun Cursor.intOrNull(name: String): Int? = idx(name).let { if (isNull(it)) null else getInt(it) }

    private fun Cursor.stats() = Stats(
        distance = double("distance"),
        duration = long("duration"),
        movingTime = long("moving_time"),
        maxSpeed = double("max_speed"),
        ascent = double("ascent"),
        descent = double("descent"),
        minEle = doubleOrNull("min_ele"),
        maxEle = doubleOrNull("max_ele"),
        points = long("point_count").toInt(),
    )

    private fun Cursor.segment() = Segment(
        id = long("id"),
        trackId = long("track_id"),
        index = long("idx").toInt(),
        mode = TransportMode.fromKey(str("mode")),
        startTime = long("start_time"),
        endTime = long("end_time"),
        stats = stats(),
    )

    private fun Cursor.track(segs: Map<Long, List<Segment>>): Track {
        val id = long("id")
        return Track(
            id = id,
            uuid = str("uuid"),
            name = str("name"),
            notes = str("notes"),
            startTime = long("start_time"),
            endTime = long("end_time"),
            stats = stats(),
            favorite = long("favorite") == 1L,
            recording = long("recording") == 1L,
            preview = decodePreview(str("preview")),
            segments = segs[id].orEmpty(),
        )
    }

    private fun Cursor.point() = TrackPoint(
        time = long("time"),
        lat = double("lat"),
        lon = double("lon"),
        ele = doubleOrNull("ele"),
        speed = doubleOrNull("speed"),
        course = doubleOrNull("course"),
        accuracy = doubleOrNull("accuracy"),
        vAccuracy = doubleOrNull("v_accuracy"),
        sats = intOrNull("sats"),
        pressure = doubleOrNull("pressure"),
        gap = long("gap") == 1L,
        segmentId = long("segment_id"),
        id = long("id"),
    )

    companion object {
        const val NAME = "gpxreg.db"
        const val VERSION = 1

        fun encodePreview(p: List<LatLon>) = p.joinToString(";") { "%.5f,%.5f".format(java.util.Locale.US, it.lat, it.lon) }
        fun decodePreview(s: String): List<LatLon> = if (s.isBlank()) emptyList() else s.split(';').mapNotNull { part ->
            val (a, b) = part.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            LatLon(a.toDoubleOrNull() ?: return@mapNotNull null, b.toDoubleOrNull() ?: return@mapNotNull null)
        }

        fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO meta VALUES('app','gpxreg'),('schema','$VERSION'),('units','time=ms UTC; distance,elevation=m; speed=m/s; course=deg; pressure=hPa')")
            db.execSQL(
                """CREATE TABLE tracks(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    notes TEXT NOT NULL DEFAULT '',
                    start_time INTEGER NOT NULL,
                    end_time INTEGER NOT NULL,
                    distance REAL NOT NULL DEFAULT 0,
                    duration INTEGER NOT NULL DEFAULT 0,
                    moving_time INTEGER NOT NULL DEFAULT 0,
                    max_speed REAL NOT NULL DEFAULT 0,
                    ascent REAL NOT NULL DEFAULT 0,
                    descent REAL NOT NULL DEFAULT 0,
                    min_ele REAL,
                    max_ele REAL,
                    point_count INTEGER NOT NULL DEFAULT 0,
                    favorite INTEGER NOT NULL DEFAULT 0,
                    recording INTEGER NOT NULL DEFAULT 0,
                    preview TEXT NOT NULL DEFAULT '',
                    created INTEGER NOT NULL
                )""",
            )
            db.execSQL(
                """CREATE TABLE segments(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                    idx INTEGER NOT NULL,
                    mode TEXT NOT NULL,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER NOT NULL,
                    distance REAL NOT NULL DEFAULT 0,
                    duration INTEGER NOT NULL DEFAULT 0,
                    moving_time INTEGER NOT NULL DEFAULT 0,
                    max_speed REAL NOT NULL DEFAULT 0,
                    ascent REAL NOT NULL DEFAULT 0,
                    descent REAL NOT NULL DEFAULT 0,
                    min_ele REAL,
                    max_ele REAL,
                    point_count INTEGER NOT NULL DEFAULT 0
                )""",
            )
            db.execSQL(
                """CREATE TABLE points(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                    segment_id INTEGER NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
                    time INTEGER NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    ele REAL,
                    speed REAL,
                    course REAL,
                    accuracy REAL,
                    v_accuracy REAL,
                    sats INTEGER,
                    pressure REAL,
                    gap INTEGER NOT NULL DEFAULT 0
                )""",
            )
            db.execSQL(
                """CREATE TABLE waypoints(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
                    time INTEGER NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    ele REAL,
                    name TEXT NOT NULL DEFAULT '',
                    note TEXT NOT NULL DEFAULT ''
                )""",
            )
            db.execSQL("CREATE INDEX points_track_time ON points(track_id, time)")
            db.execSQL("CREATE INDEX points_segment ON points(segment_id)")
            db.execSQL("CREATE INDEX segments_track ON segments(track_id, idx)")
            db.execSQL("CREATE INDEX waypoints_track ON waypoints(track_id)")
        }
    }
}
