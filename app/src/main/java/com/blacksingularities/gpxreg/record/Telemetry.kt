package com.blacksingularities.gpxreg.record

import android.hardware.SensorManager
import android.location.Location
import com.blacksingularities.gpxreg.data.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Bieżące odczyty dla HUD – niezależne od tego, czy trwa nagrywanie. */
data class TelemetryState(
    val hasFix: Boolean = false,
    val time: Long = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    /** m/s */
    val speed: Double = 0.0,
    /** m n.p.m. */
    val alt: Double? = null,
    /** Kurs nad ziemią (°) – wiarygodny tylko w ruchu. */
    val course: Double? = null,
    val accuracy: Double? = null,
    val vAccuracy: Double? = null,
    /** Prędkość pionowa (m/s) – z barometru, gdy jest, inaczej z GPS. */
    val vs: Double = 0.0,
    /** Przyspieszenie wzdłużne (g). */
    val g: Double = 0.0,
    val baroAvailable: Boolean = false,
)

object Telemetry {
    private val _state = MutableStateFlow(TelemetryState())
    val state: StateFlow<TelemetryState> = _state.asStateFlow()

    private var prev: Location? = null
    private var vsSmooth = 0.0
    private var gSmooth = 0.0
    private var lastBaroAlt = Float.NaN
    private var lastBaroTime = 0L

    fun start(scope: CoroutineScope) {
        scope.launch { LocationHub.fixes.collect { onFix(it) } }
        scope.launch {
            SensorHub.pressure.collect { p ->
                if (p.isNaN()) return@collect
                val now = System.currentTimeMillis()
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, p)
                if (!lastBaroAlt.isNaN() && now > lastBaroTime) {
                    val dt = (now - lastBaroTime) / 1000.0
                    if (dt in 0.05..5.0) {
                        val v = (alt - lastBaroAlt) / dt
                        vsSmooth += (v - vsSmooth) * (dt / (dt + 2.0))
                        _state.value = _state.value.copy(vs = vsSmooth, baroAvailable = true)
                    }
                }
                lastBaroAlt = alt
                lastBaroTime = now
            }
        }
    }

    private fun onFix(l: Location) {
        val p = prev
        val speed = when {
            l.hasSpeed() -> l.speed.toDouble()
            p != null && l.time > p.time -> Geo.distance(p.latitude, p.longitude, l.latitude, l.longitude) / ((l.time - p.time) / 1000.0)
            else -> 0.0
        }
        val s = _state.value
        var vs = s.vs
        var g = gSmooth
        if (p != null && l.time > p.time) {
            val dt = (l.time - p.time) / 1000.0
            if (dt < 10) {
                if (!s.baroAvailable && l.hasAltitude() && p.hasAltitude()) {
                    val v = (l.altitude - p.altitude) / dt
                    vsSmooth += (v - vsSmooth) * 0.3
                    vs = vsSmooth
                }
                val prevSpeed = if (p.hasSpeed()) p.speed.toDouble() else s.speed
                val a = (speed - prevSpeed) / dt / 9.80665
                gSmooth += (a - gSmooth) * 0.4
                g = gSmooth
            }
        }
        prev = l
        _state.value = s.copy(
            hasFix = true,
            time = l.time,
            lat = l.latitude,
            lon = l.longitude,
            speed = speed,
            alt = if (l.hasAltitude()) l.altitude else null,
            course = when {
                speed <= 0.8 -> null
                l.hasBearing() -> l.bearing.toDouble()
                // Bez kursu z odbiornika – kierunek z dwóch ostatnich pozycji.
                p != null && Geo.distance(p.latitude, p.longitude, l.latitude, l.longitude) > 2 ->
                    Geo.bearing(p.latitude, p.longitude, l.latitude, l.longitude)
                else -> s.course
            },
            accuracy = if (l.hasAccuracy()) l.accuracy.toDouble() else null,
            vAccuracy = if (l.hasVerticalAccuracy()) l.verticalAccuracyMeters.toDouble() else null,
            vs = vs,
            g = g,
        )
    }
}
