package com.blacksingularities.gpxreg.update

import com.blacksingularities.gpxreg.BuildConfig
import com.blacksingularities.gpxreg.i18n.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val UPDATE_API = "https://redzikowski.com/api/github/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=20"

/** Opis jednej wersji z GitHub Releases. */
data class ReleaseNote(val version: String, val notes: String, val date: String)

data class ReleaseInfo(
    val version: String,
    /** Zmiany ze wszystkich wersji nowszych od zainstalowanej – od najnowszej. */
    val changes: List<ReleaseNote>,
    val assetUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float, val bytes: Long) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Installing(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String, val info: ReleaseInfo? = null) : UpdateState
}

/**
 * Lista wydań z GitHub API → najnowsze z plikiem APK i notatki wszystkich wersji, które
 * użytkownik ominął. Szkice i wersje testowe są pomijane.
 */
fun parseReleases(json: String, current: String): ReleaseInfo? {
    val arr = JSONArray(json)
    val releases = (0 until arr.length()).map { arr.getJSONObject(it) }
        .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
    val newer = releases.filter { isNewer(versionOf(it), current) }
        .sortedWith { a, b -> if (isNewer(versionOf(a), versionOf(b))) -1 else if (isNewer(versionOf(b), versionOf(a))) 1 else 0 }
    val latest = newer.firstOrNull { apkOf(it) != null } ?: return null
    val (url, size) = apkOf(latest)!!
    return ReleaseInfo(
        version = versionOf(latest),
        changes = newer.filter { !isNewer(versionOf(it), versionOf(latest)) }.map {
            ReleaseNote(versionOf(it), cleanNotes(it.optString("body")), it.optString("published_at").take(10))
        },
        assetUrl = url,
        sizeBytes = size,
        pageUrl = latest.optString("html_url"),
    )
}

private fun versionOf(o: JSONObject) = o.optString("tag_name").removePrefix("v")

private fun apkOf(o: JSONObject): Pair<String, Long>? {
    val assets = o.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val a = assets.getJSONObject(i)
        if (a.optString("name").endsWith(".apk")) return a.optString("browser_download_url") to a.optLong("size")
    }
    return null
}

/** Notatki bez stopki z instrukcją instalacji (po „---”). */
fun cleanNotes(body: String): String = body.replace("\r\n", "\n").substringBefore("\n---").trim()

/** Porównanie numeryczne wersji „1.1.12” > „1.1.9”; przyrostki typu „-dev” są pomijane. */
fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
    val r = parts(remote)
    val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

/** Komunikat błędu sieci zrozumiały dla użytkownika. */
fun friendlyError(e: Throwable): String = when (e) {
    is java.net.UnknownHostException -> tr("Brak połączenia z internetem")
    is java.net.SocketTimeoutException -> tr("Serwer nie odpowiada – spróbuj za chwilę")
    is javax.net.ssl.SSLException -> tr("Nie udało się nawiązać bezpiecznego połączenia")
    else -> e.message ?: tr("Nieznany błąd")
}
