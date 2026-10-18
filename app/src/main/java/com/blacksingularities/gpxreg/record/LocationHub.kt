package com.blacksingularities.gpxreg.record

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stan odbiornika GNSS. */
data class GnssInfo(
    val used: Int = 0,
    val visible: Int = 0,
    /** Systemy satelitarne w użyciu (GPS, Galileo, GLONASS, BeiDou…). */
    val constellations: Set<String> = emptySet(),
    val enabled: Boolean = true,
)

/**
 * Jedno źródło pozycji dla całej aplikacji – bez Usług Google Play, prosto z odbiornika GNSS.
 * Działa, dopóki ktoś go potrzebuje: ekran nagrywania (podgląd HUD przed startem) albo usługa
 * nagrywania. Każdy użytkownik zgłasza się [acquire] i zwalnia [release].
 */
object LocationHub {
    private val owners = HashMap<String, Long>()
    private var manager: LocationManager? = null
    private var currentInterval = 0L
    private val main = Handler(Looper.getMainLooper())

    private val _fixes = MutableSharedFlow<Location>(replay = 1, extraBufferCapacity = 32)
    val fixes: SharedFlow<Location> = _fixes.asSharedFlow()

    private val _gnss = MutableStateFlow(GnssInfo())
    val gnss: StateFlow<GnssInfo> = _gnss.asStateFlow()

    private val _last = MutableStateFlow<Location?>(null)
    val last: StateFlow<Location?> = _last.asStateFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _last.value = location
            _fixes.tryEmit(location)
        }

        override fun onProviderEnabled(provider: String) {
            _gnss.value = _gnss.value.copy(enabled = true)
        }

        override fun onProviderDisabled(provider: String) {
            _gnss.value = _gnss.value.copy(enabled = false, used = 0)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            val systems = HashSet<String>()
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) {
                    used++
                    systems += when (status.getConstellationType(i)) {
                        GnssStatus.CONSTELLATION_GPS -> "GPS"
                        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
                        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
                        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
                        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                        else -> "?"
                    }
                }
            }
            _gnss.value = _gnss.value.copy(used = used, visible = status.satelliteCount, constellations = systems)
        }

        override fun onStopped() {
            _gnss.value = _gnss.value.copy(used = 0)
        }
    }

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    /** [intervalMs] – oczekiwany odstęp pozycji; przy kilku chętnych wygrywa najkrótszy. */
    fun acquire(context: Context, owner: String, intervalMs: Long = 1000) {
        main.post {
            owners[owner] = intervalMs
            restart(context.applicationContext)
        }
    }

    fun release(owner: String) {
        main.post {
            if (owners.remove(owner) == null) return@post
            if (owners.isEmpty()) stop() else manager?.let { restartWith(it) }
        }
    }

    private fun restart(context: Context) {
        if (!hasPermission(context)) return
        val lm = manager ?: context.getSystemService(LocationManager::class.java)?.also { manager = it } ?: return
        restartWith(lm)
    }

    @SuppressLint("MissingPermission")
    private fun restartWith(lm: LocationManager) {
        val interval = owners.values.minOrNull() ?: return
        if (interval == currentInterval) return
        runCatching { lm.removeUpdates(listener) }
        val first = currentInterval == 0L
        currentInterval = interval
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0f, listener, Looper.getMainLooper())
            _gnss.value = _gnss.value.copy(enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
        }
        if (first) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.registerGnssStatusCallback(context().mainExecutor, gnssCallback)
                } else {
                    @Suppress("DEPRECATION")
                    lm.registerGnssStatusCallback(gnssCallback, main)
                }
            }
            // Ostatnia znana pozycja – mapa i HUD nie startują od zera.
            runCatching {
                val lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (lastKnown != null && _last.value == null) _last.value = lastKnown
            }
        }
    }

    private var appContext: Context? = null
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun context(): Context = appContext ?: error("LocationHub.init")

    private fun stop() {
        val lm = manager ?: return
        runCatching { lm.removeUpdates(listener) }
        runCatching { lm.unregisterGnssStatusCallback(gnssCallback) }
        currentInterval = 0
    }
}
