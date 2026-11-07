package com.blacksingularities.gpxreg

import com.blacksingularities.gpxreg.feedback.Feedback
import com.blacksingularities.gpxreg.feedback.CrashReportPrompt
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.record.SensorHub
import com.blacksingularities.gpxreg.ui.AppRoot
import com.blacksingularities.gpxreg.ui.AppViewModel
import com.blacksingularities.gpxreg.ui.Tab
import com.blacksingularities.gpxreg.ui.theme.GpxRegTheme
import com.blacksingularities.gpxreg.widget.QuickActions

/** Systemowe okna i uprawnienia dostępne dla interfejsu. */
interface Platform {
    fun pickGpx()
    fun pickBackup()
    fun createDocument(name: String, mime: String, onUri: (Uri) -> Unit)
    fun openFolder(onUri: (Uri) -> Unit)
    fun startActivity(intent: Intent)
    fun openUrl(url: String)
    fun hasLocation(): Boolean
    fun requestLocation(then: (Boolean) -> Unit)
    fun requestNotifications(then: () -> Unit)
    fun openAppSettings()
    fun openLocationSettings()
    fun openBatterySettings()
    /** Systemowe okno „Zezwolić na działanie w tle bez ograniczeń?”. */
    fun requestIgnoreBattery()
    fun isIgnoringBattery(): Boolean
    /** Użytkownik dał tylko przybliżoną lokalizację – trasa byłaby bezużyteczna. */
    fun hasCoarseOnly(): Boolean
    fun keepScreenOn(on: Boolean)
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform") }

class MainActivity : ComponentActivity(), Platform {
    private val vm: AppViewModel by viewModels()

    private var onCreateDoc: ((Uri) -> Unit)? = null
    private var onFolder: ((Uri) -> Unit)? = null
    private var onLocation: ((Boolean) -> Unit)? = null
    private var onNotif: (() -> Unit)? = null

    private val gpxPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importGpx(uris)
    }
    private val backupPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    private val docCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val cb = onCreateDoc
        onCreateDoc = null
        if (uri != null) cb?.invoke(uri)
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val cb = onFolder
        onFolder = null
        if (uri != null) cb?.invoke(uri)
    }
    private val locationPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true
        onLocation?.invoke(ok)
        onLocation = null
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        onNotif?.invoke()
        onNotif = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Feedback.install(this, "opentracetrack")
        handleIntent(intent)
        setContent {
            GpxRegTheme {
                CrashReportPrompt()
                CompositionLocalProvider(LocalPlatform provides this) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        SensorHub.displayRotation = windowManager.defaultDisplay.rotation
        vm.checkUpdateIfDue()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        I18n.refresh()
        @Suppress("DEPRECATION")
        SensorHub.displayRotation = windowManager.defaultDisplay.rotation
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Plik GPX otwarty lub udostępniony z innej aplikacji. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            QuickActions.START -> {
                vm.openTrack = null
                vm.settingsOpen = false
                vm.tab = Tab.RECORD
                vm.startRequested = true
                setIntent(Intent(this, MainActivity::class.java))
                return
            }
            QuickActions.OPEN_RECORDING -> {
                vm.openTrack = null
                vm.settingsOpen = false
                vm.tab = Tab.RECORD
                setIntent(Intent(this, MainActivity::class.java))
                return
            }
            ACTION_TRACKS -> {
                vm.openTrack = null
                vm.settingsOpen = false
                vm.tab = Tab.TRACKS
                setIntent(Intent(this, MainActivity::class.java))
                return
            }
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_SEND_MULTIPLE -> (
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                ).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            vm.importGpx(uris)
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    // ------------------------------------------------------------ Platform

    override fun pickGpx() {
        runCatching { gpxPicker.launch(
                arrayOf(
                    "application/gpx+xml", "application/vnd.google-earth.kml+xml", "application/geo+json", "application/json",
                    "application/vnd.ant.fit", "application/xml", "text/xml", "application/octet-stream", "*/*",
                ),
            ) }
    }

    override fun pickBackup() {
        runCatching { backupPicker.launch(arrayOf("application/vnd.sqlite3", "application/x-sqlite3", "application/octet-stream", "*/*")) }
    }

    override fun createDocument(name: String, mime: String, onUri: (Uri) -> Unit) {
        onCreateDoc = onUri
        runCatching { docCreator.launch(name) }
    }

    override fun openFolder(onUri: (Uri) -> Unit) {
        onFolder = onUri
        runCatching { folderPicker.launch(null) }
    }

    override fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun hasLocation() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun requestLocation(then: (Boolean) -> Unit) {
        if (hasLocation()) {
            then(true)
            return
        }
        onLocation = then
        locationPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun requestNotifications(then: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            then()
            return
        }
        onNotif = then
        notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun openAppSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }

    override fun openLocationSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }

    override fun openBatterySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    override fun requestIgnoreBattery() {
        @Suppress("BatteryLife")
        val ok = runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }.isSuccess
        if (!ok) openBatterySettings()
    }

    override fun isIgnoringBattery(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    override fun hasCoarseOnly(): Boolean =
        !hasLocation() && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val ACTION_TRACKS = "com.blacksingularities.gpxreg.action.TRACKS"
    }
}
