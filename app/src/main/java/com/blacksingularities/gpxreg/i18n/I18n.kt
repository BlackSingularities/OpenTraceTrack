package com.blacksingularities.gpxreg.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.gpxreg.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Lokalizacja oparta na zasobach Androida: teksty są w `res/values…/strings.xml`.
 *
 * Dodanie języka: skopiuj `values/strings.xml` do `values-<kod>/strings.xml`, przetłumacz
 * i dopisz `<locale android:name="<kod>" />` w `res/xml/locales_config.xml`. Lista języków
 * w ustawieniach jest czytana z tego pliku, więc nic więcej nie trzeba zmieniać w kodzie.
 *
 * Szablony {0}, {1}… są zastępowane argumentami. Zasoby trzymane są w stanie Compose –
 * zmiana języka od razu odświeża cały interfejs, bez restartu aktywności.
 */
object I18n {
    private lateinit var app: Context

    /** Zasoby w wybranym języku; odczyt w kompozycji subskrybuje zmianę języka. */
    var res: Resources? by mutableStateOf(null)
        private set

    /** Wybrany znacznik języka ("" = systemowy). */
    var selected: String by mutableStateOf("")
        private set

    val locale: Locale
        get() = res?.configuration?.locales?.get(0) ?: Locale.getDefault()

    /** Znacznik języka do nazw plików i nagłówków (np. w eksporcie CSV). */
    val code: String get() = locale.language

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Języki zadeklarowane w `res/xml/locales_config.xml`. */
    val available: List<String> by lazy {
        val out = mutableListOf<String>()
        runCatching {
            val p = app.resources.getXml(R.xml.locales_config)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "locale") {
                    p.getAttributeValue("http://schemas.android.com/apk/res/android", "name")?.let { out += it }
                }
            }
        }
        out.ifEmpty { listOf("en", "pl") }
    }

    /** Nazwa języka w nim samym, np. „Polski”, „English”, „Deutsch”. */
    fun displayName(tag: String): String {
        val l = Locale.forLanguageTag(tag)
        return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }

    /** [tag] pusty = język systemu (na Androidzie 13+ także język wybrany dla aplikacji w systemie). */
    fun apply(tag: String) {
        if (!::app.isInitialized) return
        selected = tag
        val conf = Configuration(app.resources.configuration)
        if (tag.isNotBlank()) conf.setLocale(Locale.forLanguageTag(tag))
        res = app.createConfigurationContext(conf).resources
    }

    /** Ponowne odczytanie języka systemu, np. po jego zmianie w ustawieniach telefonu. */
    fun refresh() = apply(selected)

    private fun resources(): Resources? = res ?: if (::app.isInitialized) app.resources else null

    internal fun string(@StringRes id: Int): String = resources()?.getString(id) ?: ""

    internal fun plural(@PluralsRes id: Int, n: Int): String = resources()?.getQuantityString(id, n) ?: ""
}

private fun fill(text: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return text
    var out = text
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
    return out
}

/** Tekst interfejsu w bieżącym języku; {0}, {1}… zastępowane są kolejnymi argumentami. */
fun tr(@StringRes id: Int, vararg args: Any?): String = fill(I18n.string(id), args)

/** Odmiana przez liczbę (`<plurals>`); {0} to liczba, {1}… kolejne argumenty. */
fun trp(@PluralsRes id: Int, n: Int, vararg args: Any?): String = fill(I18n.plural(id, n), arrayOf(n, *args))
