package com.blacksingularities.gpxreg.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.blacksingularities.gpxreg.BuildConfig
import com.blacksingularities.gpxreg.R
import com.blacksingularities.gpxreg.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aktualizacje z GitHub Releases, jak w OpenPDF: sprawdzenie wydań, pobranie APK i instalacja
 * przez PackageInstaller bez przeglądarki. Od Androida 12, gdy poprzednią wersję zainstalowała
 * sama aplikacja, system aktualizuje ją bez dodatkowego potwierdzenia.
 */
object UpdateManager {
    const val ACTION_STATUS = "com.blacksingularities.gpxreg.UPDATE_STATUS"
    private const val PREFS = "gpxreg_update"
    private const val RESTART_KEY = "restart"
    private const val NOTES_KEY = "notes"
    private const val NOTES_VERSION_KEY = "notesVersion"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 4712

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GPXreg/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode == 403) error(tr("GitHub chwilowo ogranicza zapytania – spróbuj za godzinę"))
            if (conn.responseCode != 200) error(tr("Serwer odpowiedział kodem {0}", conn.responseCode))
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, info: ReleaseInfo, onProgress: (Float, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "GPXreg-${info.version}.apk")
        val part = File(dir, target.name + ".part")
        val conn = (URL(info.assetUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GPXreg/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode !in 200..299) error(tr("Pobieranie nie powiodło się ({0})", conn.responseCode))
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (read - lastReport > 96 * 1024) {
                            lastReport = read
                            onProgress(if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f, read)
                        }
                    }
                }
            }
            if (!part.renameTo(target)) error(tr("Nie można zapisać pliku aktualizacji"))
            verifyApk(context, target)
            onProgress(1f, target.length())
            target
        } finally {
            conn.disconnect()
        }
    }

    /** Sprawdza, czy pobrany plik to APK tej aplikacji – uszkodzony plik nie trafi do instalatora. */
    private fun verifyApk(context: Context, file: File) {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        val expected = context.packageName
        if (info == null) {
            file.delete()
            error(tr("Pobrany plik jest uszkodzony – spróbuj ponownie"))
        }
        if (info.packageName != expected && info.packageName + ".debug" != expected) {
            file.delete()
            error(tr("Pobrany plik nie jest aktualizacją GPXreg"))
        }
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Cicha aktualizacja (bez okna systemu) – Android 12+, gdy bieżącą wersję zainstalowała ta aplikacja. */
    fun canInstallSilently(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !canInstall(context)) return false
        val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        return installer == context.packageName
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun install(context: Context, file: File, info: ReleaseInfo) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        // Dla nowej wersji: uruchom ponownie i pokaż „Co nowego” z tymi notatkami.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(RESTART_KEY, System.currentTimeMillis())
            .putString(NOTES_KEY, info.changes.joinToString("\n\n") { "## ${it.version}\n${it.notes}" })
            .putString(NOTES_VERSION_KEY, info.version)
            .apply()
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /** Notatki wersji zainstalowanej przez aktualizację – raz, po pierwszym uruchomieniu. */
    fun takeWhatsNew(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = prefs.getString(NOTES_VERSION_KEY, null) ?: return null
        val notes = prefs.getString(NOTES_KEY, "") ?: ""
        // Instalacja jeszcze się nie odbyła (albo się nie udała) – notatki czekają.
        if (isNewer(v, currentVersion)) return null
        prefs.edit().remove(NOTES_VERSION_KEY).remove(NOTES_KEY).apply()
        return v to notes
    }

    /**
     * Po aktualizacji: otwiera aplikację ponownie. Android może zablokować start z tła – wtedy
     * zostaje powiadomienie „dotknij, aby otworzyć”.
     */
    fun relaunchAfterUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val requested = prefs.getLong(RESTART_KEY, 0L)
        if (requested == 0L || System.currentTimeMillis() - requested > 30 * 60_000L) return
        prefs.edit().remove(RESTART_KEY).apply()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        runCatching { context.startActivity(launch) }
        notifyUpdated(context, launch)
    }

    private fun notifyUpdated(context: Context, launch: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Aktualizacje"), NotificationManager.IMPORTANCE_HIGH))
        val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr("GPXreg zaktualizowany do {0}", BuildConfig.VERSION_NAME))
            .setContentText(tr("Dotknij, aby otworzyć aplikację"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    fun clearUpdatedNotification(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }
}

/** Odbiera status instalacji; gdy system wymaga potwierdzenia – pokazuje jego okno. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateManager.relaunchAfterUpdate(context)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: tr("kod {0}", status)
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    val hint = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                        tr("Wersja podpisana innym kluczem – odinstaluj aplikację i zainstaluj nową z GitHuba.")
                    } else {
                        msg
                    }
                    Toast.makeText(context, tr("Aktualizacja nieudana: {0}", hint), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** Nowa wersja zainstalowana – system wysyła to do zaktualizowanej aplikacji. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateManager.relaunchAfterUpdate(context)
    }
}
