package com.blacksingularities.gpxreg.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.blacksingularities.gpxreg.GpxRegApp
import com.blacksingularities.gpxreg.MainActivity
import com.blacksingularities.gpxreg.R
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Usługa pierwszoplanowa: trzyma odbiornik GNSS włączony przy zgaszonym ekranie i pokazuje
 * powiadomienie z dystansem, czasem i prędkością oraz przyciskami pauzy i znacznika.
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchJob: Job? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        goForeground()
        val app = application as GpxRegApp
        when (intent?.action) {
            ACTION_START -> {
                val mode = TransportMode.fromKey(intent.getStringExtra("mode"))
                acquire(app)
                scope.launch(Dispatchers.IO) { Recorder.begin(mode) }
            }
            ACTION_PAUSE -> Recorder.pause()
            ACTION_RESUME -> Recorder.resume()
            ACTION_WAYPOINT -> Recorder.addWaypoint()
            ACTION_REFRESH -> update()
            // Wznowienie po przerwaniu (ponowne uruchomienie przez system albo prośba użytkownika).
            ACTION_RECOVER, null -> {
                acquire(app)
                scope.launch(Dispatchers.IO) {
                    val ok = if (Recorder.state.value.active) {
                        Recorder.resume()
                        true
                    } else {
                        Recorder.recover(resume = true)
                    }
                    if (!ok) stopSelf()
                }
            }
        }
        watch()
        return START_STICKY
    }

    private var acquired = false

    private fun acquire(app: GpxRegApp) {
        if (acquired) return
        acquired = true
        val s = app.settings.value
        LocationHub.acquire(this, OWNER, s.interval * 1000L)
        if (s.useBarometer) SensorHub.acquire(this, OWNER, attitude = false, barometer = true)
    }

    private fun watch() {
        if (watchJob != null) return
        watchJob = scope.launch {
            Recorder.state
                .map { Triple(it.status, (it.total.distance / 50).toInt(), (it.elapsed / 5000).toInt()) to it.mode }
                .distinctUntilChanged()
                .collectLatest { update() }
        }
        // Lokalizacja wyłączona w trakcie nagrywania – głośne ostrzeżenie, bo punkty przestają przychodzić.
        scope.launch {
            LocationHub.gnss
                .map { it.enabled }
                .distinctUntilChanged()
                .collect { enabled -> gpsAlert(!enabled && Recorder.state.value.active) }
        }
    }

    private fun gpsAlert(show: Boolean) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (!show) {
            nm.cancel(ALERT_ID)
            return
        }
        val pi = PendingIntent.getActivity(
            this,
            ALERT_ID,
            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr(R.string.gps_off_title))
            .setContentText(tr(R.string.gps_off_text))
            .setStyle(Notification.BigTextStyle().bigText(tr(R.string.gps_off_text)))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()
        runCatching { nm.notify(ALERT_ID, n) }
    }

    private fun goForeground() {
        if (foreground) return
        foreground = true
        startForeground(NOTIFICATION_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun update() {
        if (!foreground) return
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build()) }
    }

    private fun build(): Notification {
        val s = Recorder.state.value
        val units = Units((application as GpxRegApp).settings.value.units)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when (s.status) {
            RecStatus.PAUSED -> tr(R.string.nagrywanie_wstrzymane)
            RecStatus.AUTO_PAUSED -> tr(R.string.postoj_automatyczna_pauza)
            else -> tr(R.string.nagrywanie_trasy_x, s.mode.label)
        }
        val text = tr(
            R.string.x_x_sr_x,
            units.fmtDist(s.total.distance),
            Units.duration(s.elapsed),
            units.fmtSpeed(s.total.avgMovingSpeed),
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (s.status == RecStatus.PAUSED) {
            b.addAction(action(ACTION_RESUME, tr(R.string.wznow)))
        } else if (s.active) {
            b.addAction(action(ACTION_PAUSE, tr(R.string.pauza)))
        }
        if (s.active) b.addAction(action(ACTION_WAYPOINT, tr(R.string.znacznik)))
        return b.build()
    }

    private fun action(act: String, label: String): Notification.Action {
        val pi = PendingIntent.getService(
            this,
            act.hashCode(),
            Intent(this, RecordingService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, label, pi).build()
    }

    override fun onDestroy() {
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(ALERT_ID) }
        if (acquired) {
            LocationHub.release(OWNER)
            SensorHub.release(OWNER)
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "gpxreg.START"
        const val ACTION_RECOVER = "gpxreg.RECOVER"
        const val ACTION_PAUSE = "gpxreg.PAUSE"
        const val ACTION_RESUME = "gpxreg.RESUME"
        const val ACTION_WAYPOINT = "gpxreg.WAYPOINT"
        const val ACTION_REFRESH = "gpxreg.REFRESH"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1201
        private const val ALERT_CHANNEL = "alerts"
        private const val ALERT_ID = 1202
        private const val OWNER = "service"

        @Volatile
        private var running = false

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr(R.string.nagrywanie_trasy), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL, tr(R.string.alerts_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }

        /** Odświeża powiadomienie po zmianie stanu (pauza, zmiana środka transportu). */
        fun refresh(context: Context) {
            if (!Recorder.state.value.active) return
            runCatching {
                context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_REFRESH))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
    }
}
