package com.blacksingularities.gpxreg.io

import com.blacksingularities.gpxreg.data.Geo
import com.blacksingularities.gpxreg.data.Stats
import com.blacksingularities.gpxreg.data.TrackPoint
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Waypoint
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

const val REPO_URL = "https://blacksingularities.github.io/OpenTraceTrack/"
// Stabilny identyfikator formatu: nie zmieniamy go przy rebrandingu, by stare pliki pozostały zgodne.
private const val NS_GPXREG = "https://github.com/BlackSingularities/GPXreg/gpx/1"

/** Fragment do zapisu: jeden `<trk>` z typem aktywności i podsumowaniem. */
data class GpxSegment(
    val index: Int,
    val mode: TransportMode,
    val name: String,
    val stats: Stats,
    val points: List<TrackPoint>,
)

data class GpxDocument(
    val name: String,
    val description: String,
    val uuid: String?,
    val time: Long,
    val segments: List<GpxSegment>,
    val waypoints: List<Waypoint>,
    val creator: String,
)

/**
 * Zapis GPX 1.1 zgodny ze schematem, czytelny dla Stravy, Garmin Connect, Komoot, OsmAnd,
 * Google Earth, QGIS i innych:
 *  - każdy fragment (środek transportu) to osobny `<trk>` z `<type>` (walking, cycling, driving…),
 *  - przerwy po pauzach to osobne `<trkseg>`,
 *  - prędkość (m/s) i kurs (°) w standardowym rozszerzeniu Garmin TrackPointExtension v2,
 *  - dokładność, ciśnienie i podsumowania w przestrzeni nazw `gpxreg` – inne programy je pomijają,
 *    a OpenTraceTrack odtwarza z nich trasę 1:1 (także identyfikator, więc ponowny import nie dubluje).
 */
object GpxWriter {
    fun write(doc: GpxDocument, out: Writer) {
        val w = XmlOut(out)
        w.raw("""<?xml version="1.0" encoding="UTF-8"?>""").nl()
        w.raw(
            """<gpx version="1.1" creator="${esc(doc.creator)}" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v2" """ +
                """xmlns:gpxreg="$NS_GPXREG" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd """ +
                """http://www.garmin.com/xmlschemas/TrackPointExtension/v2 https://www8.garmin.com/xmlschemas/TrackPointExtensionv2.xsd">""",
        ).nl()

        val all = doc.segments.flatMap { it.points }
        w.open("metadata", 1)
        w.leaf("name", doc.name, 2)
        if (doc.description.isNotBlank()) w.leaf("desc", doc.description, 2)
        w.raw("    <link href=\"$REPO_URL\"><text>OpenTraceTrack</text></link>").nl()
        w.leaf("time", iso(doc.time), 2)
        val keywords = doc.segments.map { it.mode.gpxType }.distinct().joinToString(", ")
        if (keywords.isNotEmpty()) w.leaf("keywords", keywords, 2)
        if (all.isNotEmpty()) {
            w.raw(
                "    <bounds minlat=\"${c(all.minOf { it.lat })}\" minlon=\"${c(all.minOf { it.lon })}\" " +
                    "maxlat=\"${c(all.maxOf { it.lat })}\" maxlon=\"${c(all.maxOf { it.lon })}\"/>",
            ).nl()
        }
        if (doc.uuid != null) {
            w.open("extensions", 2)
            w.raw("      <gpxreg:track uuid=\"${esc(doc.uuid)}\" segments=\"${doc.segments.size}\"/>").nl()
            w.close("extensions", 2)
        }
        w.close("metadata", 1)

        for (wp in doc.waypoints) {
            w.raw("  <wpt lat=\"${c(wp.lat)}\" lon=\"${c(wp.lon)}\">").nl()
            wp.ele?.let { w.leaf("ele", d1(it), 2) }
            w.leaf("time", iso(wp.time), 2)
            w.leaf("name", wp.name, 2)
            if (wp.note.isNotBlank()) w.leaf("desc", wp.note, 2)
            w.leaf("sym", "Flag, Blue", 2)
            w.close("wpt", 1)
        }

        for (seg in doc.segments) {
            w.open("trk", 1)
            w.leaf("name", seg.name, 2)
            w.leaf("src", "OpenTraceTrack", 2)
            w.leaf("number", (seg.index + 1).toString(), 2)
            w.leaf("type", seg.mode.gpxType, 2)
            val s = seg.stats
            w.open("extensions", 2)
            w.raw(
                "      <gpxreg:segment mode=\"${seg.mode.key}\" index=\"${seg.index}\" distance=\"${d1(s.distance)}\" " +
                    "duration=\"${s.duration / 1000}\" moving_time=\"${s.movingTime / 1000}\" max_speed=\"${d2(s.maxSpeed)}\" " +
                    "avg_moving_speed=\"${d2(s.avgMovingSpeed)}\" ascent=\"${d1(s.ascent)}\" descent=\"${d1(s.descent)}\"/>",
            ).nl()
            w.close("extensions", 2)
            var openSeg = false
            for ((i, p) in seg.points.withIndex()) {
                if (i == 0 || p.gap) {
                    if (openSeg) w.close("trkseg", 2)
                    w.open("trkseg", 2)
                    openSeg = true
                }
                writePoint(w, p)
            }
            if (openSeg) w.close("trkseg", 2)
            w.close("trk", 1)
        }
        w.raw("</gpx>").nl()
        out.flush()
    }

    private fun writePoint(w: XmlOut, p: TrackPoint) {
        w.raw("      <trkpt lat=\"${c(p.lat)}\" lon=\"${c(p.lon)}\">").nl()
        p.ele?.let { w.leaf("ele", d1(it), 4) }
        w.leaf("time", iso(p.time), 4)
        p.sats?.let { w.leaf("sat", it.toString(), 4) }
        val hasTpx = p.speed != null || p.course != null
        val hasOwn = p.accuracy != null || p.vAccuracy != null || p.pressure != null
        if (hasTpx || hasOwn) {
            w.open("extensions", 4)
            if (hasTpx) {
                w.open("gpxtpx:TrackPointExtension", 5)
                p.speed?.let { w.leaf("gpxtpx:speed", d2(it), 6) }
                p.course?.let { w.leaf("gpxtpx:course", d1(((it % 360) + 360) % 360), 6) }
                w.close("gpxtpx:TrackPointExtension", 5)
            }
            p.accuracy?.let { w.leaf("gpxreg:hacc", d1(it), 5) }
            p.vAccuracy?.let { w.leaf("gpxreg:vacc", d1(it), 5) }
            p.pressure?.let { w.leaf("gpxreg:pressure", d2(it), 5) }
            w.close("extensions", 4)
        }
        w.close("trkpt", 3)
    }

    private class XmlOut(val out: Writer) {
        fun raw(s: String) = apply { out.write(s) }
        fun nl() = apply { out.write("\n") }
        fun open(tag: String, depth: Int) = raw("  ".repeat(depth) + "<$tag>").nl()
        fun close(tag: String, depth: Int) = raw("  ".repeat(depth) + "</$tag>").nl()
        fun leaf(tag: String, value: String, depth: Int) = raw("  ".repeat(depth) + "<$tag>" + esc(value) + "</$tag>").nl()
    }

    private fun c(v: Double) = String.format(Locale.US, "%.7f", v)
    private fun d1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun d2(v: Double) = String.format(Locale.US, "%.2f", v)
}

internal fun esc(s: String): String = buildString(s.length) {
    for (ch in s) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r') append(' ') else append(ch)
        }
    }
}

private val isoFormat = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
}

fun iso(time: Long): String = isoFormat.get()!!.format(Date(time))

/** Czas ISO 8601 w różnych odmianach spotykanych w plikach GPX (ułamki sekund, strefy). */
fun parseIso(s: String): Long? {
    val t = s.trim()
    if (t.isEmpty()) return null
    return runCatching { java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(t).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(t).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}

/** Wynik odczytu pliku GPX. */
data class GpxImport(
    val name: String,
    val description: String,
    val uuid: String?,
    val tracks: List<Trk>,
    val waypoints: List<Waypoint>,
) {
    /** Jeden `<trk>` (albo `<rte>`): środek transportu i jego kawałki `<trkseg>`. */
    data class Trk(val name: String, val mode: TransportMode, val segments: List<List<TrackPoint>>)
}

/**
 * Odczyt GPX 1.0/1.1 z dowolnego programu. Rozumie typ aktywności z `<type>`, prędkość i kurs
 * z rozszerzeń (Garmin, Cluetrust, OsmAnd, GPX 1.0), trasy `<rte>` i punkty `<wpt>`.
 * Plikom bez czasu nadaje sztuczny (co sekundę), a brakujące prędkości wylicza z odległości.
 */
object GpxReader {
    fun read(input: InputStream, fallbackName: String): GpxImport {
        val h = Handler()
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        factory.newSAXParser().parse(InputSource(input), h)
        if (!h.sawGpx) error("not a GPX file")
        val name = h.metaName.ifBlank { h.tracks.firstOrNull()?.name ?: fallbackName }.ifBlank { fallbackName }
        val tracks = fixTimes(h.tracks.filter { t -> t.segments.any { it.isNotEmpty() } }, h.metaTime)
        return GpxImport(name, h.metaDesc, h.uuid, tracks, h.waypoints)
    }

    private fun fixTimes(tracks: List<GpxImport.Trk>, metaTime: Long?): List<GpxImport.Trk> {
        val noTime = tracks.all { t -> t.segments.all { s -> s.all { it.time == 0L } } }
        var clock = metaTime ?: System.currentTimeMillis()
        return tracks.map { t ->
            t.copy(
                segments = t.segments.map { seg ->
                    val timed = if (noTime) seg.map { p -> p.copy(time = clock).also { clock += 1000 } } else seg.filter { it.time != 0L }
                    fillSpeeds(timed.sortedBy { it.time })
                },
            )
        }
    }

    /** Prędkość z odległości do sąsiadów (okno ±2 punkty), gdy plik jej nie zawiera. */
    private fun fillSpeeds(pts: List<TrackPoint>): List<TrackPoint> {
        if (pts.isEmpty() || pts.all { it.speed != null }) return pts
        return pts.mapIndexed { i, p ->
            if (p.speed != null) return@mapIndexed p
            val a = pts[maxOf(0, i - 2)]
            val b = pts[minOf(pts.lastIndex, i + 2)]
            var dist = 0.0
            for (k in maxOf(0, i - 2) until minOf(pts.lastIndex, i + 2)) dist += Geo.distance(pts[k].lat, pts[k].lon, pts[k + 1].lat, pts[k + 1].lon)
            val dt = (b.time - a.time) / 1000.0
            p.copy(speed = if (dt > 0) dist / dt else null)
        }
    }

    private class Handler : DefaultHandler() {
        var sawGpx = false
        var metaName = ""
        var metaDesc = ""
        var metaTime: Long? = null
        var uuid: String? = null
        val tracks = ArrayList<GpxImport.Trk>()
        val waypoints = ArrayList<Waypoint>()

        private val path = ArrayList<String>()
        private val text = StringBuilder()

        // bieżący trk / rte
        private var trkName = ""
        private var trkType: String? = null
        private var trkMode: TransportMode? = null
        private var trkSegs = ArrayList<List<TrackPoint>>()
        private var seg = ArrayList<TrackPoint>()

        // bieżący punkt
        private var pLat = 0.0
        private var pLon = 0.0
        private var pEle: Double? = null
        private var pTime = 0L
        private var pSpeed: Double? = null
        private var pCourse: Double? = null
        private var pSat: Int? = null
        private var pHacc: Double? = null
        private var pVacc: Double? = null
        private var pPressure: Double? = null
        private var pName = ""
        private var pDesc = ""

        private fun parent(n: Int = 1) = path.getOrNull(path.size - 1 - n) ?: ""
        private fun inPoint() = path.any { it == "trkpt" || it == "rtept" || it == "wpt" }

        override fun startElement(uri: String?, localName: String, qName: String?, attrs: Attributes) {
            val name = localName.ifEmpty { qName?.substringAfter(':') ?: "" }
            path += name
            text.setLength(0)
            when (name) {
                "gpx" -> sawGpx = true
                "trk", "rte" -> {
                    trkName = ""
                    trkType = null
                    trkMode = null
                    trkSegs = ArrayList()
                    seg = ArrayList()
                }
                "trkseg" -> seg = ArrayList()
                "trkpt", "rtept", "wpt" -> {
                    pLat = attrs.getValue("lat")?.toDoubleOrNull() ?: 0.0
                    pLon = attrs.getValue("lon")?.toDoubleOrNull() ?: 0.0
                    pEle = null
                    pTime = 0L
                    pSpeed = null
                    pCourse = null
                    pSat = null
                    pHacc = null
                    pVacc = null
                    pPressure = null
                    pName = ""
                    pDesc = ""
                }
                "track" -> if (uri == NS_GPXREG) uuid = attrs.getValue("uuid")
                "segment" -> if (uri == NS_GPXREG) trkMode = attrs.getValue("mode")?.let { TransportMode.fromKey(it) }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            val name = localName.ifEmpty { qName?.substringAfter(':') ?: "" }
            val t = text.toString().trim()
            val p1 = parent()
            if (inPoint() && name != "trkpt" && name != "rtept" && name != "wpt") {
                when (name.lowercase()) {
                    "ele" -> pEle = t.toDoubleOrNull()
                    "time" -> pTime = parseIso(t) ?: 0L
                    "sat" -> pSat = t.toIntOrNull()
                    "speed" -> pSpeed = t.toDoubleOrNull()
                    "course" -> pCourse = t.toDoubleOrNull()
                    "hacc" -> pHacc = t.toDoubleOrNull()
                    "vacc" -> pVacc = t.toDoubleOrNull()
                    "pressure" -> pPressure = t.toDoubleOrNull()
                    "name" -> if (p1 == "wpt") pName = t
                    "desc", "cmt" -> if (p1 == "wpt" && pDesc.isEmpty()) pDesc = t
                }
            } else {
                when (name) {
                    "name" -> when (p1) {
                        "metadata" -> metaName = t
                        "gpx" -> if (metaName.isEmpty()) metaName = t // GPX 1.0
                        "trk", "rte" -> trkName = t
                    }
                    "desc" -> if (p1 == "metadata" || p1 == "gpx") metaDesc = t
                    "time" -> if (p1 == "metadata" || p1 == "gpx") metaTime = parseIso(t)
                    "type" -> if (p1 == "trk" || p1 == "rte") trkType = t
                    "trkpt", "rtept" -> seg += TrackPoint(
                        time = pTime,
                        lat = pLat,
                        lon = pLon,
                        ele = pEle,
                        speed = pSpeed,
                        course = pCourse,
                        accuracy = pHacc,
                        vAccuracy = pVacc,
                        sats = pSat,
                        pressure = pPressure,
                        gap = seg.isEmpty() && trkSegs.isNotEmpty(),
                    )
                    "wpt" -> waypoints += Waypoint(0, 0, pTime, pLat, pLon, pEle, pName, pDesc)
                    "trkseg" -> if (seg.isNotEmpty()) trkSegs += seg
                    "trk", "rte" -> {
                        if (name == "rte" && seg.isNotEmpty()) trkSegs += seg
                        val mode = trkMode ?: TransportMode.fromGpxType(trkType)
                        tracks += GpxImport.Trk(trkName, mode, trkSegs)
                    }
                }
            }
            path.removeAt(path.lastIndex)
            text.setLength(0)
        }
    }
}
