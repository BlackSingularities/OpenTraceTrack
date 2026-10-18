package com.blacksingularities.gpxreg

import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.i18n.EnglishKeys
import com.blacksingularities.gpxreg.record.ModeDetector
import com.blacksingularities.gpxreg.update.isNewer
import com.blacksingularities.gpxreg.update.parseReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LogicTest {
    @Test
    fun versionComparison() {
        assertTrue(isNewer("1.1.12", "1.1.9"))
        assertTrue(isNewer("1.2.0", "1.1.99"))
        assertTrue(isNewer("1.0.0.57", "1.0.0"))
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0.0", "1.0-dev"))
        assertFalse(isNewer("0.9", "1.0.0"))
    }

    @Test
    fun releasesCollectAllNewerNotes() {
        val json = """[
          {"tag_name":"v1.3.0","draft":false,"prerelease":true,"body":"beta","assets":[{"name":"GPXreg-1.3.0.apk","browser_download_url":"u3","size":3}]},
          {"tag_name":"v1.2.0","draft":false,"prerelease":false,"body":"- B\n\n---\nstopka","published_at":"2026-10-02T10:00:00Z","html_url":"p2","assets":[{"name":"GPXreg-1.2.0.apk","browser_download_url":"u2","size":2048}]},
          {"tag_name":"v1.1.0","draft":false,"prerelease":false,"body":"- A","published_at":"2026-09-01T10:00:00Z","assets":[{"name":"GPXreg-1.1.0.apk","browser_download_url":"u1","size":1}]},
          {"tag_name":"v1.0.0","draft":false,"prerelease":false,"body":"stare","assets":[]}
        ]"""
        val info = parseReleases(json, "1.0.0")
        assertNotNull(info)
        info!!
        assertEquals("1.2.0", info.version)
        assertEquals("u2", info.assetUrl)
        assertEquals(2048L, info.sizeBytes)
        assertEquals(listOf("1.2.0", "1.1.0"), info.changes.map { it.version })
        assertEquals("- B", info.changes[0].notes)
        assertEquals("2026-10-02", info.changes[0].date)
        assertNull(parseReleases(json, "1.2.0"))
    }

    @Test
    fun modeClassification() {
        assertNull(ModeDetector.classify(0.1, 0.3, 100.0))
        assertEquals(TransportMode.WALK, ModeDetector.classify(1.3, 1.8, 100.0))
        assertEquals(TransportMode.RUN, ModeDetector.classify(3.0, 3.4, 100.0))
        assertEquals(TransportMode.BIKE, ModeDetector.classify(5.5, 7.0, 100.0))
        assertEquals(TransportMode.CAR, ModeDetector.classify(14.0, 22.0, 100.0))
        assertEquals(TransportMode.TRAIN, ModeDetector.classify(50.0, 60.0, 100.0))
        assertEquals(TransportMode.PLANE, ModeDetector.classify(220.0, 240.0, 10000.0))
    }

    @Test
    fun detectorSuggestsAfterStableChangeAndBackdates() {
        val d = ModeDetector(windowMs = 60_000, stableMs = 30_000)
        var t = 0L
        // 2 minuty marszu – nic do zmiany.
        repeat(120) {
            assertNull(d.add(t, 1.3, 100.0, TransportMode.WALK))
            t += 1000
        }
        val switchAt = t
        var s: ModeDetector.Suggestion? = null
        repeat(120) {
            s = s ?: d.add(t, 15.0, 100.0, TransportMode.WALK)
            t += 1000
        }
        assertNotNull(s)
        assertEquals(TransportMode.CAR, s!!.mode)
        assertTrue("początek zmiany cofnięty do przesiadki", s!!.since in switchAt..(switchAt + 30_000))
    }

    @Test
    fun detectorRespectsCompatibleManualMode() {
        val d = ModeDetector(windowMs = 60_000, stableMs = 30_000)
        var t = 0L
        repeat(200) {
            assertNull("tramwaj jedzie jak samochód – bez podpowiedzi", d.add(t, 12.0, 100.0, TransportMode.TRAM))
            t += 1000
        }
    }

    @Test
    fun gpxTypeRecognition() {
        assertEquals(TransportMode.BIKE, TransportMode.fromGpxType("cycling"))
        assertEquals(TransportMode.BIKE, TransportMode.fromGpxType("Road Biking"))
        assertEquals(TransportMode.HIKE, TransportMode.fromGpxType("hiking"))
        assertEquals(TransportMode.CAR, TransportMode.fromGpxType("driving"))
        assertEquals(TransportMode.TRAIN, TransportMode.fromGpxType("train"))
        assertEquals(TransportMode.OTHER, TransportMode.fromGpxType("xyz"))
        TransportMode.entries.forEach { assertEquals(it, TransportMode.fromGpxType(it.gpxType)) }
    }

    /** Każdy tekst interfejsu `tr("…")` ma angielskie tłumaczenie. */
    @Test
    fun everyStringIsTranslated() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val pat = Regex("""\btr\(\s*"((?:[^"\\]|\\.)*)"""")
        val missing = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "I18n.kt" }
            .flatMap { f -> pat.findAll(f.readText()).map { it.groupValues[1].replace("\\\"", "\"").replace("\\$", "$") } }
            .distinct()
            .filter { it !in EnglishKeys }
            .toList()
        assertTrue("Brak tłumaczeń: $missing", missing.isEmpty())
    }
}
