package com.blacksingularities.gpxreg

import android.app.Application
import android.net.Uri
import com.blacksingularities.gpxreg.data.SettingsStore
import com.blacksingularities.gpxreg.data.TrackDb
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.io.DataPort
import com.blacksingularities.gpxreg.record.LocationHub
import com.blacksingularities.gpxreg.record.RecEvent
import com.blacksingularities.gpxreg.record.Recorder
import com.blacksingularities.gpxreg.record.RouteGuide
import com.blacksingularities.gpxreg.record.Telemetry
import com.blacksingularities.gpxreg.ui.map.OfflineMaps
import com.blacksingularities.gpxreg.widget.QuickActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class GpxRegApp : Application() {
    lateinit var db: TrackDb
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var port: DataPort
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        settings = SettingsStore(this)
        I18n.init(this)
        I18n.apply(settings.value.language)
        db = TrackDb(this)
        port = DataPort(this, db)
        MapLibre.getInstance(this)
        OfflineMaps.init(this)
        LocationHub.init(this)
        Telemetry.start(scope)
        Recorder.init(this, db) { settings.value }
        RouteGuide.init(this, db, scope) { settings.value.haptics }
        QuickActions.publishShortcuts(this)

        // Widżet i kafelek nadążają za nagraniem (najwyżej co kilka sekund).
        scope.launch {
            Recorder.state
                .sample(3000)
                .map { Triple(it.status, (it.total.distance / 50).toInt(), it.elapsed / 60_000) }
                .distinctUntilChanged()
                .collect { QuickActions.refresh(this@GpxRegApp, Recorder.state.value, Units(settings.value.units)) }
        }
        scope.launch {
            Recorder.events.collect { e -> if (e is RecEvent.Saved) autoBackup(afterRecording = true) }
        }
        autoBackup(afterRecording = false)
    }

    /** Automatyczna kopia bazy do wybranego folderu – po nagraniu albo raz na [AppSettings.autoBackupDays] dni. */
    fun autoBackup(afterRecording: Boolean, force: Boolean = false) {
        val s = settings.value
        if (s.autoBackupUri.isBlank()) return
        val due = force ||
            (afterRecording && s.autoBackupDays == 0) ||
            (s.autoBackupDays > 0 && System.currentTimeMillis() - s.lastAutoBackup >= s.autoBackupDays * 86_400_000L)
        if (!due) return
        scope.launch(Dispatchers.IO) {
            runCatching { port.autoBackup(Uri.parse(s.autoBackupUri)) }
                .onSuccess { settings.update { it.copy(lastAutoBackup = System.currentTimeMillis()) } }
        }
    }
}
