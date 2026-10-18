package com.blacksingularities.gpxreg.record

import com.blacksingularities.gpxreg.data.ModeGroup
import com.blacksingularities.gpxreg.data.TransportMode

/**
 * Rozpoznaje środek transportu po prędkości – bez Usług Google Play i bez dodatkowych uprawnień.
 * Patrzy na ostatnie [windowMs] (mediana i 90. percentyl prędkości), a zmianę zgłasza dopiero,
 * gdy nowy charakter ruchu utrzymuje się przez [stableMs]. Zwraca też chwilę, od której zmiana
 * trwa – podział trasy trafia wtedy w miejsce faktycznej przesiadki, a nie w moment kliknięcia.
 */
class ModeDetector(
    private val windowMs: Long = 90_000,
    private val stableMs: Long = 60_000,
) {
    private data class Sample(val time: Long, val speed: Double, val alt: Double?)

    private val samples = ArrayDeque<Sample>()
    private var candidate: TransportMode? = null
    private var candidateSince = 0L

    data class Suggestion(val mode: TransportMode, val since: Long)

    fun reset() {
        samples.clear()
        candidate = null
    }

    /** Dodaje próbkę; zwraca propozycję zmiany względem [current] albo null. */
    fun add(time: Long, speed: Double, alt: Double?, current: TransportMode): Suggestion? {
        samples.addLast(Sample(time, speed, alt))
        while (samples.isNotEmpty() && time - samples.first().time > windowMs) samples.removeFirst()
        if (samples.size < 20 || time - samples.first().time < windowMs / 2) return null

        val sorted = samples.map { it.speed }.sorted()
        val median = sorted[sorted.size / 2]
        val p90 = sorted[(sorted.size * 9) / 10]
        val alt = samples.last().alt
        val guess = classify(median, p90, alt) ?: run {
            candidate = null
            return null
        }
        if (guess.group == current.group || compatible(guess, current)) {
            candidate = null
            return null
        }
        if (candidate != guess) {
            candidate = guess
            // Początek zmiany: pierwsza próbka okna, która już pasuje do nowego trybu.
            candidateSince = samples.firstOrNull { classifySingle(it.speed) == guess.group }?.time ?: time
        }
        return if (time - candidateSince >= stableMs) Suggestion(guess, candidateSince) else null
    }

    /** Ręcznie wybrany tryb, który pasuje do zmierzonych prędkości (np. tramwaj zamiast samochodu). */
    private fun compatible(guess: TransportMode, current: TransportMode): Boolean = when (guess.group) {
        ModeGroup.VEHICLE -> current.group in setOf(ModeGroup.RAIL, ModeGroup.WATER, ModeGroup.VEHICLE)
        ModeGroup.RAIL -> current.group in setOf(ModeGroup.VEHICLE, ModeGroup.AIR)
        ModeGroup.WHEELS -> current.group in setOf(ModeGroup.RUN, ModeGroup.VEHICLE, ModeGroup.WATER)
        ModeGroup.RUN -> current.group in setOf(ModeGroup.WHEELS)
        ModeGroup.FOOT -> current.group in setOf(ModeGroup.RUN) || current == TransportMode.SKI
        else -> false
    }

    companion object {
        /** Klasyfikacja okna: mediana i 90. percentyl prędkości (m/s), wysokość (m). */
        fun classify(median: Double, p90: Double, alt: Double?): TransportMode? = when {
            median < 0.4 && p90 < 1.0 -> null // postój – nic nie zmieniamy
            p90 > 75 || (median > 55 && (alt ?: 0.0) > 1500) -> TransportMode.PLANE
            median > 42 -> TransportMode.TRAIN
            median > 7.5 || p90 > 12 -> TransportMode.CAR
            median > 3.6 -> TransportMode.BIKE
            median > 2.3 -> TransportMode.RUN
            else -> TransportMode.WALK
        }

        private fun classifySingle(v: Double): ModeGroup = when {
            v > 75 -> ModeGroup.AIR
            v > 42 -> ModeGroup.RAIL
            v > 7.5 -> ModeGroup.VEHICLE
            v > 3.6 -> ModeGroup.WHEELS
            v > 2.3 -> ModeGroup.RUN
            else -> ModeGroup.FOOT
        }
    }
}
