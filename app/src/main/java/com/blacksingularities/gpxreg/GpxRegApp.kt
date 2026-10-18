package com.blacksingularities.gpxreg

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import com.blacksingularities.gpxreg.data.SettingsStore
import com.blacksingularities.gpxreg.data.TrackDb
import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.io.DataPort
import com.blacksingularities.gpxreg.record.LocationHub
import com.blacksingularities.gpxreg.record.Recorder
import com.blacksingularities.gpxreg.record.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.maplibre.android.MapLibre
import java.util.Locale

class GpxRegApp : Application() {
    lateinit var db: TrackDb
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var port: DataPort
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        I18n.apply(settings.value.language, systemLanguage())
        db = TrackDb(this)
        port = DataPort(this, db)
        MapLibre.getInstance(this)
        LocationHub.init(this)
        Telemetry.start(scope)
        Recorder.init(this, db) { settings.value }
    }

    /** Język systemu (Android 13+ z uwzględnieniem języka wybranego dla tej aplikacji). */
    fun systemLanguage(): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val app = getSystemService(LocaleManager::class.java)?.applicationLocales
            if (app != null && !app.isEmpty) return app[0].language
        }
        return Locale.getDefault().language
    }
}
