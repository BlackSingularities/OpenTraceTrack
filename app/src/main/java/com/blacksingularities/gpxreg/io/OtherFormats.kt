package com.blacksingularities.gpxreg.io

import java.io.Writer
import java.util.Locale

/**
 * GeoJSON (RFC 7946): fragment = Feature z MultiLineString (kawałki między pauzami), właściwości
 * z podsumowaniem i tablicą czasów `coordTimes` (konwencja togeojson / Mapbox). Znaczniki to
 * Feature typu Point. Otwiera się w QGIS, geojson.io, Kepler.gl, Google My Maps (przez konwersję).
 */
object GeoJsonWriter {
    fun write(doc: GpxDocument, out: Writer) {
        out.write("{\"type\":\"FeatureCollection\",\"name\":${q(doc.name)},\"features\":[\n")
        var first = true
        fun sep() {
            if (!first) out.write(",\n")
            first = false
        }
        for (seg in doc.segments) {
            sep()
            val parts = ArrayList<MutableList<com.blacksingularities.gpxreg.data.TrackPoint>>()
            for ((i, p) in seg.points.withIndex()) {
                if (i == 0 || p.gap) parts.add(ArrayList())
                parts.last().add(p)
            }
            val s = seg.stats
            out.write("{\"type\":\"Feature\",\"properties\":{")
            out.write("\"name\":${q(seg.name)},\"index\":${seg.index},\"mode\":${q(seg.mode.key)},\"type\":${q(seg.mode.gpxType)},")
            out.write("\"stroke\":${q("#%06X".format(seg.mode.color and 0xFFFFFFL))},\"stroke-width\":4,")
            out.write("\"distance_m\":${n(s.distance)},\"duration_s\":${s.duration / 1000},\"moving_time_s\":${s.movingTime / 1000},")
            out.write("\"max_speed_ms\":${n(s.maxSpeed)},\"avg_moving_speed_ms\":${n(s.avgMovingSpeed)},\"ascent_m\":${n(s.ascent)},\"descent_m\":${n(s.descent)},")
            seg.points.firstOrNull()?.let { out.write("\"start_time\":${q(iso(it.time))},") }
            seg.points.lastOrNull()?.let { out.write("\"end_time\":${q(iso(it.time))},") }
            out.write("\"coordTimes\":[")
            out.write(parts.joinToString(",") { part -> part.joinToString(",", "[", "]") { q(iso(it.time)) } })
            out.write("],\"speeds_ms\":[")
            out.write(parts.joinToString(",") { part -> part.joinToString(",", "[", "]") { p -> p.speed?.let { n(it) } ?: "null" } })
            out.write("]},\"geometry\":{\"type\":\"MultiLineString\",\"coordinates\":[")
            out.write(parts.joinToString(",") { part -> part.joinToString(",", "[", "]") { coord(it.lon, it.lat, it.ele) } })
            out.write("]}}")
        }
        for (w in doc.waypoints) {
            sep()
            out.write("{\"type\":\"Feature\",\"properties\":{\"name\":${q(w.name)},\"desc\":${q(w.note)},\"time\":${q(iso(w.time))},\"marker-symbol\":\"marker\"},")
            out.write("\"geometry\":{\"type\":\"Point\",\"coordinates\":${coord(w.lon, w.lat, w.ele)}}}")
        }
        out.write("\n]}\n")
        out.flush()
    }

    private fun coord(lon: Double, lat: Double, ele: Double?) =
        if (ele != null) String.format(Locale.US, "[%.7f,%.7f,%.1f]", lon, lat, ele) else String.format(Locale.US, "[%.7f,%.7f]", lon, lat)

    private fun n(v: Double) = String.format(Locale.US, "%.2f", v)
}

/**
 * CSV – jeden wiersz na punkt, separator przecinek, kropka dziesiętna, UTF-8, nagłówek po
 * angielsku. Wartości w jednostkach SI plus km/h dla wygody w arkuszu kalkulacyjnym.
 */
object CsvWriter {
    const val HEADER = "track,segment,mode,time_utc,unix_ms,lat,lon,ele_m,speed_ms,speed_kmh,course_deg,h_accuracy_m,v_accuracy_m,satellites,pressure_hpa,new_part"

    fun write(doc: GpxDocument, out: Writer) {
        out.write(HEADER)
        out.write("\n")
        for (seg in doc.segments) {
            for (p in seg.points) {
                val row = listOf(
                    csv(doc.name),
                    (seg.index + 1).toString(),
                    seg.mode.gpxType,
                    iso(p.time),
                    p.time.toString(),
                    String.format(Locale.US, "%.7f", p.lat),
                    String.format(Locale.US, "%.7f", p.lon),
                    p.ele?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    p.speed?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                    p.speed?.let { String.format(Locale.US, "%.1f", it * 3.6) } ?: "",
                    p.course?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    p.accuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    p.vAccuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                    p.sats?.toString() ?: "",
                    p.pressure?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                    if (p.gap) "1" else "0",
                )
                out.write(row.joinToString(","))
                out.write("\n")
            }
        }
        out.flush()
    }

    private fun csv(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

/**
 * KML 2.2 – dla Google Earth: fragment = Placemark z gx:Track (czas każdego punktu, odtwarzanie
 * trasy na osi czasu) i stylem w kolorze środka transportu.
 */
object KmlWriter {
    fun write(doc: GpxDocument, out: Writer) {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\n<Document>\n")
        out.write("  <name>${esc(doc.name)}</name>\n")
        if (doc.description.isNotBlank()) out.write("  <description>${esc(doc.description)}</description>\n")
        for (mode in doc.segments.map { it.mode }.distinct()) {
            val rgb = mode.color and 0xFFFFFFL
            // KML: aabbggrr
            val abgr = "ff%02x%02x%02x".format(rgb and 0xFFL, (rgb shr 8) and 0xFFL, (rgb shr 16) and 0xFFL)
            out.write("  <Style id=\"${mode.key}\"><LineStyle><color>$abgr</color><width>4</width></LineStyle></Style>\n")
        }
        for (seg in doc.segments) {
            out.write("  <Placemark>\n    <name>${esc(seg.name)}</name>\n    <styleUrl>#${seg.mode.key}</styleUrl>\n")
            out.write("    <gx:Track>\n      <altitudeMode>clampToGround</altitudeMode>\n")
            for (p in seg.points) out.write("      <when>${iso(p.time)}</when>\n")
            for (p in seg.points) out.write(String.format(Locale.US, "      <gx:coord>%.7f %.7f %.1f</gx:coord>\n", p.lon, p.lat, p.ele ?: 0.0))
            out.write("    </gx:Track>\n  </Placemark>\n")
        }
        for (w in doc.waypoints) {
            out.write("  <Placemark>\n    <name>${esc(w.name)}</name>\n")
            if (w.note.isNotBlank()) out.write("    <description>${esc(w.note)}</description>\n")
            out.write("    <TimeStamp><when>${iso(w.time)}</when></TimeStamp>\n")
            out.write(String.format(Locale.US, "    <Point><coordinates>%.7f,%.7f,%.1f</coordinates></Point>\n  </Placemark>\n", w.lon, w.lat, w.ele ?: 0.0))
        }
        out.write("</Document>\n</kml>\n")
        out.flush()
    }
}

private fun q(s: String): String = buildString {
    append('"')
    for (ch in s) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
    append('"')
}
