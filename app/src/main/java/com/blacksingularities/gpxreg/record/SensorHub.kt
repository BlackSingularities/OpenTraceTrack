package com.blacksingularities.gpxreg.record

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Położenie telefonu: kierunek (°, magnetyczny), pochylenie i przechylenie (°). */
data class Attitude(
    val heading: Float = Float.NaN,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val available: Boolean = false,
)

/**
 * Czujniki dla HUD i zapisu: wektor obrotu (kompas, sztuczny horyzont) i barometr (ciśnienie do
 * dokładniejszej prędkości pionowej). Kąty liczone dla telefonu trzymanego pionowo, ekranem do
 * użytkownika – jak wyświetlacz przezierny przed oczami albo uchwyt na szybie.
 */
object SensorHub : SensorEventListener {
    private val owners = HashSet<String>()
    private var manager: SensorManager? = null

    /** Obrót ekranu (Surface.ROTATION_*), ustawiany przez aktywność. */
    @Volatile
    var displayRotation: Int = Surface.ROTATION_0

    private val _attitude = MutableStateFlow(Attitude())
    val attitude: StateFlow<Attitude> = _attitude.asStateFlow()

    /** Ciśnienie (hPa) albo NaN, gdy telefon nie ma barometru. */
    private val _pressure = MutableStateFlow(Float.NaN)
    val pressure: StateFlow<Float> = _pressure.asStateFlow()

    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orient = FloatArray(3)
    private var smoothHeading = Float.NaN
    private var smoothPitch = 0f
    private var smoothRoll = 0f

    fun hasBarometer(context: Context) =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    fun acquire(context: Context, owner: String, attitude: Boolean, barometer: Boolean) {
        val sm = manager ?: context.applicationContext.getSystemService(SensorManager::class.java)?.also { manager = it } ?: return
        if (!owners.add(owner)) return
        if (attitude) {
            sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        if (barometer) {
            sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    fun release(owner: String) {
        if (!owners.remove(owner)) return
        // Prosto: gdy zwalnia ostatni – wyłączamy wszystko; inaczej zostawiamy (koszt znikomy).
        if (owners.isEmpty()) {
            manager?.unregisterListener(this)
            _attitude.value = _attitude.value.copy(available = false)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> _pressure.value = event.values[0]
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                // Telefon pionowo: oś Z ekranu patrzy na użytkownika, tył telefonu – „przed samolot”.
                val (x, y) = when (displayRotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
                }
                SensorManager.remapCoordinateSystem(rot, x, y, remapped)
                SensorManager.getOrientation(remapped, orient)
                val heading = ((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat()
                val pitch = -Math.toDegrees(orient[1].toDouble()).toFloat()
                val roll = Math.toDegrees(orient[2].toDouble()).toFloat()
                smoothHeading = if (smoothHeading.isNaN()) heading else smoothAngle(smoothHeading, heading, 0.15f)
                smoothPitch += (pitch - smoothPitch) * 0.2f
                smoothRoll += (roll - smoothRoll) * 0.2f
                _attitude.value = Attitude(smoothHeading, smoothPitch, smoothRoll, true)
            }
        }
    }

    private fun smoothAngle(from: Float, to: Float, k: Float): Float {
        var d = to - from
        if (d > 180) d -= 360
        if (d < -180) d += 360
        if (abs(d) > 90) return to
        return (from + d * k + 360) % 360
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
