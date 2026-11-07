package com.blacksingularities.gpxreg.io

import com.blacksingularities.gpxreg.data.TrackPoint
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Waypoint
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/** Formaty plików, które da się wczytać jako trasę. */
enum class ImportFormat { GPX, KML, GEOJSON, FIT }

/**
 * Wybór czytnika: najpierw po zawartości (nagłówek FIT, `<kml`, `{`), potem po rozszerzeniu.
 * Wszystkie czytniki zwracają ten sam [GpxImport] co GPX – reszta importu jest wspólna.
 */
object TrackImporter {
    fun detect(head: ByteArray, fileName: String): ImportFormat {
        if (head.size >= 12 && head[8] == '.'.code.toByte() && head[9] == 'F'.code.toByte() && head[10] == 'I'.code.toByte() && head[11] == 'T'.code.toByte()) {
            return ImportFormat.FIT
        }
        val text = String(head, Charsets.UTF_8).trimStart('﻿', ' ', '\n', '\r', '\t')
        return when {
            text.startsWith("{") || text.startsWith("[") -> ImportFormat.GEOJSON
            text.contains("<kml", ignoreCase = true) -> ImportFormat.KML
            text.contains("<gpx", ignoreCase = true) -> ImportFormat.GPX
            fileName.endsWith(".fit", true) -> ImportFormat.FIT
            fileName.endsWith(".kml", true) -> ImportFormat.KML
            fileName.endsWith(".geojson", true) || fileName.endsWith(".json", true) -> ImportFormat.GEOJSON
            else -> ImportFormat.GPX
        }
    }

    fun read(bytes: ByteArray, fileName: String): GpxImport {
        val fallback = fileName.substringBeforeLast('.')
        return when (detect(bytes.copyOf(minOf(bytes.size, 512)), fileName)) {
            ImportFormat.GPX -> GpxReader.read(ByteArrayInputStream(bytes), fallback)
            ImportFormat.KML -> KmlReader.read(ByteArrayInputStream(bytes), fallback)
            ImportFormat.GEOJSON -> GeoJsonReader.read(String(bytes, Charsets.UTF_8), fallback)
            ImportFormat.FIT -> FitReader.read(bytes, fallback)
        }
    }
}

// ---------------------------------------------------------------------------------------------- KML

/** KML z Google Earth, My Maps i z eksportu OpenTraceTrack (`gx:Track`, `LineString`, `Point`). */
object KmlReader {
    fun read(input: InputStream, fallbackName: String): GpxImport {
        val h = Handler()
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        factory.newSAXParser().parse(InputSource(input), h)
        if (!h.sawKml) error("not a KML file")
        val tracks = GpxReader.fixTimes(h.tracks.filter { t -> t.segments.any { it.size > 1 } }, null)
        if (tracks.isEmpty()) error("no tracks")
        return GpxImport(h.docName.ifBlank { fallbackName }, h.docDesc, null, tracks, h.waypoints)
    }

    private class Handler : DefaultHandler() {
        var sawKml = false
        var docName = ""
        var docDesc = ""
        val tracks = ArrayList<GpxImport.Trk>()
        val waypoints = ArrayList<Waypoint>()

        private val text = StringBuilder()
        private val path = ArrayList<String>()
        private var pmName = ""
        private var pmDesc = ""
        private var pmStyle = ""
        private var pmTime: Long? = null
        private val pmSegments = ArrayList<List<TrackPoint>>()
        private var pmPoint: TrackPoint? = null
        private val whens = ArrayList<Long>()
        private val coords = ArrayList<DoubleArray>()

        override fun startElement(uri: String?, localName: String, qName: String?, attrs: Attributes) {
            if (localName == "kml") sawKml = true
            path += localName
            text.setLength(0)
            when (localName) {
                "Placemark" -> {
                    pmName = ""; pmDesc = ""; pmStyle = ""; pmTime = null; pmPoint = null
                    pmSegments.clear()
                }
                "Track" -> {
                    whens.clear(); coords.clear()
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        private fun parent(n: Int = 1) = path.getOrNull(path.size - 1 - n) ?: ""

        override fun endElement(uri: String?, localName: String, qName: String?) {
            val v = text.toString().trim()
            val inPm = "Placemark" in path
            when (localName) {
                "name" -> if (inPm && parent() == "Placemark") pmName = v else if (!inPm && (parent() == "Document" || parent() == "Folder") && docName.isEmpty()) docName = v
                "description" -> if (inPm && parent() == "Placemark") pmDesc = v else if (!inPm && parent() == "Document") docDesc = v
                "styleUrl" -> if (inPm) pmStyle = v.removePrefix("#")
                "when" -> if (parent() == "Track") parseIso(v)?.let { whens += it } else if (parent() == "TimeStamp") pmTime = parseIso(v)
                "coord" -> if (parent() == "Track") {
                    val p = v.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
                    if (p.size >= 2) coords += p.toDoubleArray()
                }
                "Track" -> {
                    val pts = coords.mapIndexed { i, c -> TrackPoint(whens.getOrElse(i) { 0L }, c[1], c[0], ele = c.getOrNull(2)?.takeIf { it != 0.0 }) }
                    if (pts.isNotEmpty()) pmSegments += pts
                }
                "coordinates" -> {
                    val pts = v.split(Regex("\\s+")).mapNotNull { t ->
                        val p = t.split(',').mapNotNull { it.toDoubleOrNull() }
                        if (p.size >= 2) TrackPoint(0L, p[1], p[0], ele = p.getOrNull(2)?.takeIf { it != 0.0 }) else null
                    }
                    when (parent()) {
                        "LineString" -> if (pts.size > 1) pmSegments += pts
                        "Point" -> pmPoint = pts.firstOrNull()
                    }
                }
                "Placemark" -> {
                    if (pmSegments.isNotEmpty()) {
                        val mode = TransportMode.entries.firstOrNull { it.key == pmStyle } ?: TransportMode.OTHER
                        tracks += GpxImport.Trk(pmName, mode, pmSegments.toList())
                    } else {
                        pmPoint?.let { p -> waypoints += Waypoint(0, 0, pmTime ?: 0L, p.lat, p.lon, p.ele, pmName, pmDesc) }
                    }
                }
            }
            path.removeAt(path.lastIndex)
            text.setLength(0)
        }
    }
}

// ------------------------------------------------------------------------------------------ GeoJSON

/** GeoJSON (LineString, MultiLineString, Point) – z czasami w `coordTimes`, jak w eksporcie OpenTraceTrack i geojson.io. */
object GeoJsonReader {
    fun read(json: String, fallbackName: String): GpxImport {
        val root = JSONObject(json)
        val features: List<JSONObject> = when (root.optString("type")) {
            "FeatureCollection" -> root.optJSONArray("features")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
            "Feature" -> listOf(root)
            else -> listOf(JSONObject().put("type", "Feature").put("geometry", root).put("properties", JSONObject()))
        }
        val tracks = ArrayList<GpxImport.Trk>()
        val waypoints = ArrayList<Waypoint>()
        for (f in features) {
            val g = f.optJSONObject("geometry") ?: continue
            val props = f.optJSONObject("properties") ?: JSONObject()
            val name = props.optString("name", "")
            val times = props.optJSONArray("coordTimes")
            val mode = props.optString("mode", "").takeIf { it.isNotEmpty() }?.let { TransportMode.fromKey(it) }
                ?: TransportMode.fromGpxType(props.optString("type", props.optString("activity", "")).ifEmpty { null })
            when (g.optString("type")) {
                "LineString" -> {
                    val pts = line(g.optJSONArray("coordinates"), times?.let { if (it.optJSONArray(0) != null) it.optJSONArray(0) else it })
                    if (pts.size > 1) tracks += GpxImport.Trk(name, mode, listOf(pts))
                }
                "MultiLineString" -> {
                    val c = g.optJSONArray("coordinates") ?: continue
                    val segs = (0 until c.length()).map { i -> line(c.optJSONArray(i), times?.optJSONArray(i)) }.filter { it.size > 1 }
                    if (segs.isNotEmpty()) tracks += GpxImport.Trk(name, mode, segs)
                }
                "Point" -> {
                    val c = g.optJSONArray("coordinates") ?: continue
                    waypoints += Waypoint(
                        0, 0, parseIso(props.optString("time", "")) ?: 0L,
                        c.optDouble(1), c.optDouble(0), c.optDouble(2).takeIf { !it.isNaN() },
                        name, props.optString("desc", props.optString("description", "")),
                    )
                }
            }
        }
        val fixed = GpxReader.fixTimes(tracks, null)
        if (fixed.isEmpty()) error("no tracks")
        return GpxImport(root.optString("name", "").ifBlank { fixed.first().name.ifBlank { fallbackName } }, "", null, fixed, waypoints)
    }

    private fun line(c: JSONArray?, times: JSONArray?): List<TrackPoint> {
        c ?: return emptyList()
        return (0 until c.length()).mapNotNull { i ->
            val p = c.optJSONArray(i) ?: return@mapNotNull null
            val lon = p.optDouble(0)
            val lat = p.optDouble(1)
            if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
            val t = times?.optString(i)?.let { s -> parseIso(s) ?: s.toLongOrNull() } ?: 0L
            TrackPoint(t, lat, lon, ele = p.optDouble(2).takeIf { !it.isNaN() })
        }
    }
}

// ---------------------------------------------------------------------------------------------- FIT

/**
 * Binarny FIT z zegarków i liczników Garmin, Wahoo, Coros, Suunto. Czytamy wiadomości `record`
 * (czas, pozycja, wysokość, prędkość), sport z `session`/`sport` i zatrzymania stopera z `event`.
 */
object FitReader {
    private const val FIT_EPOCH = 631_065_600L

    private class Field(val num: Int, val size: Int)
    private class Def(val global: Int, val bigEndian: Boolean, val fields: List<Field>, val devSize: Int)

    @Synchronized
    fun read(b: ByteArray, fallbackName: String): GpxImport {
        points.clear()
        sportMode = null
        pendingGap = false
        if (b.size < 14) error("not a FIT file")
        val headerSize = b[0].toInt() and 0xFF
        val dataSize = u32(b, 4, false)
        if (String(b, 8, 4, Charsets.US_ASCII) != ".FIT") error("not a FIT file")
        val end = minOf(b.size.toLong(), headerSize + dataSize).toInt()
        val defs = arrayOfNulls<Def>(16)
        var pos = headerSize
        var lastTs = 0L
        val segments = ArrayList<MutableList<TrackPoint>>().apply { add(ArrayList()) }

        while (pos < end) {
            val h = b[pos++].toInt() and 0xFF
            if (h and 0x80 != 0) {
                // Skompresowany nagłówek czasu.
                val local = (h shr 5) and 0x3
                val off = (h and 0x1F).toLong()
                lastTs = lastTs + ((off - (lastTs and 0x1F)) and 0x1F)
                val d = defs[local] ?: error("FIT: no definition")
                pos = data(b, pos, d, lastTs) { ts, v -> lastTs = ts; handle(d.global, v, ts) }
            } else if (h and 0x40 != 0) {
                val local = h and 0x0F
                val dev = h and 0x20 != 0
                val big = b[pos + 1].toInt() == 1
                val global = if (big) ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF) else ((b[pos + 3].toInt() and 0xFF) shl 8) or (b[pos + 2].toInt() and 0xFF)
                val n = b[pos + 4].toInt() and 0xFF
                pos += 5
                val fields = (0 until n).map { i -> Field(b[pos + i * 3].toInt() and 0xFF, b[pos + i * 3 + 1].toInt() and 0xFF) }
                pos += n * 3
                var devSize = 0
                if (dev) {
                    val nd = b[pos].toInt() and 0xFF
                    pos++
                    for (i in 0 until nd) devSize += b[pos + i * 3 + 1].toInt() and 0xFF
                    pos += nd * 3
                }
                defs[local] = Def(global, big, fields, devSize)
            } else {
                val d = defs[h and 0x0F] ?: error("FIT: no definition")
                pos = data(b, pos, d, lastTs) { ts, v -> lastTs = ts; handle(d.global, v, ts) }
            }
        }

        // Zebrane w [handle] – przypisanie po pętli, bo lambdy wyżej tylko dopisują do list.
        val sport = sportMode
        val all = points
        for (p in all) {
            if (p.gap && segments.last().isNotEmpty()) segments.add(ArrayList())
            segments.last().add(p.copy(gap = false))
        }
        points.clear()
        sportMode = null
        val segs = segments.filter { it.size > 1 }
        if (segs.isEmpty()) error("no GPS points")
        val tracks = GpxReader.fixTimes(listOf(GpxImport.Trk(fallbackName, sport ?: TransportMode.OTHER, segs)), null)
        return GpxImport(fallbackName, "", null, tracks, emptyList())
    }

    // Stan bieżącego odczytu (czytnik używany jest po kolei, jeden plik naraz).
    private val points = ArrayList<TrackPoint>()
    private var sportMode: TransportMode? = null
    private var pendingGap = false

    private fun handle(global: Int, v: Map<Int, Long>, ts: Long) {
        when (global) {
            20 -> { // record
                val lat = v[0]?.takeIf { it != 0x7FFFFFFFL }?.let { semi(it) } ?: return
                val lon = v[1]?.takeIf { it != 0x7FFFFFFFL }?.let { semi(it) } ?: return
                val ele = (v[78]?.takeIf { it != 0xFFFFFFFFL } ?: v[2]?.takeIf { it != 0xFFFFL })?.let { it / 5.0 - 500.0 }
                val speed = (v[73]?.takeIf { it != 0xFFFFFFFFL } ?: v[6]?.takeIf { it != 0xFFFFL })?.let { it / 1000.0 }
                val time = (v[253] ?: ts).let { (it + FIT_EPOCH) * 1000L }
                points += TrackPoint(time, lat, lon, ele = ele, speed = speed, gap = pendingGap)
                pendingGap = false
            }
            21 -> { // event: stoper zatrzymany → następny punkt zaczyna nowy kawałek
                if (v[0] == 0L && (v[1] == 1L || v[1] == 4L)) pendingGap = true
            }
            18, 12 -> { // session / sport
                val s = v[if (global == 18) 5 else 0]?.toInt() ?: return
                if (sportMode == null || global == 18) sportMode = sport(s) ?: sportMode
            }
        }
    }

    private fun sport(s: Int): TransportMode? = when (s) {
        1 -> TransportMode.RUN
        2 -> TransportMode.BIKE
        11 -> TransportMode.WALK
        17 -> TransportMode.HIKE
        12, 13, 14 -> TransportMode.SKI
        15, 19, 37, 41, 42 -> TransportMode.BOAT
        20 -> TransportMode.PLANE
        22 -> TransportMode.MOTORCYCLE
        23 -> TransportMode.CAR
        0, 255 -> null
        else -> TransportMode.OTHER
    }

    private fun semi(v: Long): Double = v.toInt() * (180.0 / 2147483648.0)

    /** Czyta pola wiadomości; wartości liczbowe 1/2/4 B (inne rozmiary są pomijane). */
    private inline fun data(b: ByteArray, start: Int, d: Def, ts: Long, out: (Long, Map<Int, Long>) -> Unit): Int {
        var pos = start
        val values = HashMap<Int, Long>()
        for (f in d.fields) {
            if (pos + f.size > b.size) break
            when (f.size) {
                1 -> values[f.num] = (b[pos].toInt() and 0xFF).toLong()
                2 -> values[f.num] = u16(b, pos, d.bigEndian)
                4 -> values[f.num] = u32(b, pos, d.bigEndian)
            }
            pos += f.size
        }
        pos += d.devSize
        out(values[253] ?: ts, values)
        return pos
    }

    private fun u16(b: ByteArray, p: Int, big: Boolean): Long {
        val a = b[p].toInt() and 0xFF
        val c = b[p + 1].toInt() and 0xFF
        return (if (big) (a shl 8) or c else (c shl 8) or a).toLong()
    }

    private fun u32(b: ByteArray, p: Int, big: Boolean): Long {
        var v = 0L
        for (i in 0 until 4) {
            val byte = (b[p + if (big) i else 3 - i].toLong() and 0xFF)
            v = (v shl 8) or byte
        }
        return v
    }
}
