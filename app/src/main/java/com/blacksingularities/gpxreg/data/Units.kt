package com.blacksingularities.gpxreg.data

import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.i18n.tr
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Przeliczanie i formatowanie wartości w wybranym systemie jednostek. */
class Units(val system: UnitSystem) {
    private val locale: Locale get() = if (I18n.english) Locale.US else Locale.forLanguageTag("pl-PL")

    val speedUnit: String
        get() = when (system) {
            UnitSystem.METRIC -> "km/h"
            UnitSystem.IMPERIAL -> "mph"
            UnitSystem.NAUTICAL -> "kn"
        }
    val altUnit: String get() = if (system == UnitSystem.METRIC) "m" else "ft"
    val distUnit: String
        get() = when (system) {
            UnitSystem.METRIC -> "km"
            UnitSystem.IMPERIAL -> "mi"
            UnitSystem.NAUTICAL -> "NM"
        }
    val vsUnit: String get() = if (system == UnitSystem.METRIC) "m/s" else "ft/min"

    /** m/s → jednostka prędkości. */
    fun speed(ms: Double): Double = when (system) {
        UnitSystem.METRIC -> ms * 3.6
        UnitSystem.IMPERIAL -> ms * 2.236936
        UnitSystem.NAUTICAL -> ms * 1.943844
    }

    /** jednostka prędkości → m/s. */
    fun speedToMs(v: Double): Double = when (system) {
        UnitSystem.METRIC -> v / 3.6
        UnitSystem.IMPERIAL -> v / 2.236936
        UnitSystem.NAUTICAL -> v / 1.943844
    }

    fun alt(m: Double): Double = if (system == UnitSystem.METRIC) m else m * 3.28084

    fun dist(m: Double): Double = when (system) {
        UnitSystem.METRIC -> m / 1000.0
        UnitSystem.IMPERIAL -> m / 1609.344
        UnitSystem.NAUTICAL -> m / 1852.0
    }

    /** Prędkość pionowa: m/s albo ft/min jak w lotnictwie. */
    fun vs(ms: Double): Double = if (system == UnitSystem.METRIC) ms else ms * 196.8504

    fun fmtSpeed(ms: Double, decimals: Int = 1) = num(speed(ms), decimals) + " " + speedUnit
    fun fmtAlt(m: Double?) = if (m == null) "–" else alt(m).roundToInt().toString() + " " + altUnit

    fun fmtDist(m: Double): String {
        val d = dist(m)
        if (system == UnitSystem.METRIC && m < 1000) return m.roundToInt().toString() + " m"
        if (system != UnitSystem.METRIC && d < 0.1) return alt(m).roundToInt().toString() + " ft"
        return num(d, if (d < 10) 2 else if (d < 100) 1 else 0) + " " + distUnit
    }

    fun num(v: Double, decimals: Int): String = String.format(locale, "%.${decimals}f", v)

    /** Tempo (min/km lub min/mi) – dla pieszych i biegaczy czytelniejsze niż prędkość. */
    fun fmtPace(ms: Double): String {
        if (ms < 0.3) return "–"
        val perUnit = when (system) {
            UnitSystem.METRIC -> 1000.0
            UnitSystem.IMPERIAL -> 1609.344
            UnitSystem.NAUTICAL -> 1852.0
        }
        val sec = (perUnit / ms).roundToLong()
        if (sec > 5999) return "–"
        return "%d:%02d /%s".format(sec / 60, sec % 60, distUnit)
    }

    companion object {
        fun duration(ms: Long, seconds: Boolean = true): String {
            val s = abs(ms) / 1000
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return when {
                !seconds && h > 0 -> tr("{0} h {1} min", h, m)
                !seconds -> tr("{0} min", m)
                h > 0 -> "%d:%02d:%02d".format(h, m, sec)
                else -> "%d:%02d".format(m, sec)
            }
        }

        private fun locale(): Locale = if (I18n.english) Locale.US else Locale.forLanguageTag("pl-PL")

        fun date(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale()).format(Date(time))
        fun dateTime(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale()).format(Date(time))
        fun time(time: Long): String = SimpleDateFormat("HH:mm", locale()).format(Date(time))
        fun timeSec(time: Long): String = SimpleDateFormat("HH:mm:ss", locale()).format(Date(time))

        /** Domyślna nazwa trasy: „Trasa 18 paź 2026, 14:05”. */
        fun defaultTrackName(time: Long): String = tr("Trasa {0}", SimpleDateFormat("d MMM yyyy, HH:mm", locale()).format(Date(time)))

        fun bytes(n: Long): String = when {
            n >= 1_048_576 -> String.format(locale(), "%.1f MB", n / 1_048_576.0)
            n >= 1024 -> String.format(locale(), "%.0f kB", n / 1024.0)
            else -> "$n B"
        }
    }
}
