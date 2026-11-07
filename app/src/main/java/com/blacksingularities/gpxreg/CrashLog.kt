package com.blacksingularities.gpxreg

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lokalny dziennik awarii – bez wysyłania czegokolwiek. Po awarii w ustawieniach pojawia się
 * pozycja „Dziennik błędów”, który użytkownik może sam udostępnić autorowi albo usunąć.
 */
object CrashLog {
    private const val MAX_BYTES = 128 * 1024
    private lateinit var dir: File

    val file: File get() = File(dir, "crash.log")

    fun install(context: Context) {
        // Katalog udostępniany przez FileProvider (res/xml/file_paths.xml), poza czyszczonymi plikami eksportu.
        dir = File(context.filesDir, "logs").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun exists(): Boolean = ::dir.isInitialized && file.exists() && file.length() > 0

    fun read(): String = runCatching { file.readText() }.getOrDefault("")

    fun clear() {
        runCatching { file.delete() }
    }

    private fun write(thread: Thread, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val entry = buildString {
            append("=== ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append(" ===\n")
            append("OpenTraceTrack ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Thread: ").append(thread.name).append('\n')
            append(sw.toString()).append('\n')
        }
        val old = if (file.exists()) file.readText() else ""
        file.writeText((old + entry).takeLast(MAX_BYTES))
    }
}
