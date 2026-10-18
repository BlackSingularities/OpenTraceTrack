package com.blacksingularities.gpxreg

import com.blacksingularities.gpxreg.data.Stats
import com.blacksingularities.gpxreg.data.StatsAccumulator
import com.blacksingularities.gpxreg.data.TrackPoint
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Waypoint
import com.blacksingularities.gpxreg.io.CsvWriter
import com.blacksingularities.gpxreg.io.GeoJsonWriter
import com.blacksingularities.gpxreg.io.GpxDocument
import com.blacksingularities.gpxreg.io.GpxReader
import com.blacksingularities.gpxreg.io.GpxSegment
import com.blacksingularities.gpxreg.io.GpxWriter
import com.blacksingularities.gpxreg.io.KmlWriter
import com.blacksingularities.gpxreg.io.parseIso
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class GpxTest {
    private val t0 = 1_760_000_000_000L

    private fun line(n: Int, startLat: Double, dLat: Double, startTime: Long, speed: Double, segId: Long, gapAt: Int = -1) =
        List(n) { i ->
            TrackPoint(
                time = startTime + i * 1000L,
                lat = startLat + i * dLat,
                lon = 19.0,
                ele = 100.0 + i,
                speed = speed,
                course = 0.0,
                accuracy = 4.0,
                vAccuracy = 6.0,
                sats = 12,
                pressure = 1001.25,
                gap = i == gapAt,
                segmentId = segId,
            )
        }

    private fun doc(): GpxDocument {
        val bike = line(30, 52.0, 0.0001, t0, 5.0, 1, gapAt = 15)
        val train = line(20, 52.004, 0.0005, t0 + 60_000, 30.0, 2)
        return GpxDocument(
            name = "Test & <trasa> „PL”",
            description = "opis",
            uuid = "11111111-2222-3333-4444-555555555555",
            time = t0,
            segments = listOf(
                GpxSegment(0, TransportMode.BIKE, "Rower", StatsAccumulator.of(bike), bike),
                GpxSegment(1, TransportMode.TRAIN, "Pociąg", StatsAccumulator.of(train), train),
            ),
            waypoints = listOf(Waypoint(0, 0, t0 + 5000, 52.0005, 19.0, 105.0, "Kawa", "dobra")),
            creator = "GPXreg test",
        )
    }

    @Test
    fun gpxRoundTripKeepsModesSpeedsAndGaps() {
        val sw = StringWriter()
        GpxWriter.write(doc(), sw)
        val xml = sw.toString()
        assertTrue(xml.contains("<type>cycling</type>"))
        assertTrue(xml.contains("<type>train</type>"))
        assertTrue(xml.contains("<gpxtpx:speed>5.00</gpxtpx:speed>"))
        assertTrue(xml.contains("Test &amp; &lt;trasa&gt;"))

        val back = GpxReader.read(xml.byteInputStream(), "fallback")
        assertEquals("Test & <trasa> „PL”", back.name)
        assertEquals("11111111-2222-3333-4444-555555555555", back.uuid)
        assertEquals(2, back.tracks.size)
        assertEquals(TransportMode.BIKE, back.tracks[0].mode)
        assertEquals(TransportMode.TRAIN, back.tracks[1].mode)
        // Pauza w środku fragmentu rowerowego = dwa <trkseg>.
        assertEquals(2, back.tracks[0].segments.size)
        val p = back.tracks[0].segments[0][3]
        assertEquals(5.0, p.speed!!, 1e-6)
        assertEquals(4.0, p.accuracy!!, 1e-6)
        assertEquals(1001.25, p.pressure!!, 1e-6)
        assertEquals(12, p.sats)
        assertEquals(t0 + 3000, p.time)
        assertTrue(back.tracks[0].segments[1].first().gap)
        assertEquals(1, back.waypoints.size)
        assertEquals("Kawa", back.waypoints[0].name)
    }

    @Test
    fun readsForeignGpxWithoutExtensions() {
        val xml = """<?xml version="1.0"?>
            <gpx version="1.1" creator="Strava" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Morning Ride</name><time>2026-05-01T06:00:00Z</time></metadata>
              <trk><name>Morning Ride</name><type>1</type><trkseg>
                <trkpt lat="50.0" lon="20.0"><ele>200</ele><time>2026-05-01T06:00:00Z</time></trkpt>
                <trkpt lat="50.001" lon="20.0"><ele>201</ele><time>2026-05-01T06:00:10.500+02:00</time></trkpt>
                <trkpt lat="50.002" lon="20.0"><ele>202</ele><time>2026-05-01T06:00:20Z</time></trkpt>
              </trkseg></trk>
            </gpx>"""
        val g = GpxReader.read(xml.byteInputStream(), "x")
        assertEquals("Morning Ride", g.name)
        assertEquals(TransportMode.RUN, g.tracks.single().mode) // Strava: 1 = bieg
        val pts = g.tracks.single().segments.single()
        assertEquals(3, pts.size)
        assertNotNull("prędkość wyliczona z odległości", pts[1].speed)
    }

    @Test
    fun routeWithoutTimesGetsSyntheticClock() {
        val xml = """<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><rte><name>R</name>
            <rtept lat="1" lon="1"/><rtept lat="1.001" lon="1"/><rtept lat="1.002" lon="1"/></rte></gpx>"""
        val g = GpxReader.read(xml.byteInputStream(), "x")
        val pts = g.tracks.single().segments.flatten()
        assertEquals(3, pts.size)
        assertEquals(1000L, pts[1].time - pts[0].time)
    }

    @Test
    fun otherFormatsAreWellFormed() {
        val d = doc()
        val gj = StringWriter().also { GeoJsonWriter.write(d, it) }.toString()
        val o = JSONObject(gj)
        assertEquals("FeatureCollection", o.getString("type"))
        assertEquals(3, o.getJSONArray("features").length())
        val csv = StringWriter().also { CsvWriter.write(d, it) }.toString().lines().filter { it.isNotBlank() }
        assertEquals(CsvWriter.HEADER, csv.first())
        assertEquals(1 + 50, csv.size)
        val kml = StringWriter().also { KmlWriter.write(d, it) }.toString()
        assertTrue(kml.contains("<gx:Track>"))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(kml.byteInputStream())
    }

    @Test
    fun isoParsingVariants() {
        assertEquals(0L, parseIso("1970-01-01T00:00:00Z"))
        assertEquals(1500L, parseIso("1970-01-01T00:00:01.5Z"))
        assertEquals(-3_600_000L, parseIso("1970-01-01T00:00:00+01:00"))
        assertEquals(0L, parseIso("1970-01-01T00:00:00"))
    }

    @Test
    fun statsIgnoreGapsAndElevationNoise() {
        val pts = line(11, 52.0, 0.001, t0, 2.0, 1, gapAt = 5)
            .mapIndexed { i, p -> p.copy(ele = 100.0 + if (i % 2 == 0) 1.0 else -1.0) }
        val s: Stats = StatsAccumulator.of(pts)
        // 10 odcinków po ~111 m, jeden pominięty przez pauzę.
        assertEquals(9 * 111.2, s.distance, 2.0)
        assertEquals(0.0, s.ascent, 1e-9)
        assertEquals(9_000L, s.duration)
        assertEquals(2.0, s.maxSpeed, 1e-9)
    }
}
