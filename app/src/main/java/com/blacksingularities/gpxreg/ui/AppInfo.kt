package com.blacksingularities.gpxreg.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.gpxreg.BuildConfig
import com.blacksingularities.gpxreg.CrashLog
import com.blacksingularities.gpxreg.LocalPlatform
import com.blacksingularities.gpxreg.R
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.ui.theme.DarkDialog
import com.blacksingularities.gpxreg.ui.theme.Group
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.PillButton
import com.blacksingularities.gpxreg.ui.theme.RoundIcon
import com.blacksingularities.gpxreg.ui.theme.SectionTitle
import com.blacksingularities.gpxreg.ui.theme.SettingRow

private class License(val name: String, val license: String, val url: String)

private val licenses = listOf(
    License("MapLibre Native for Android", "BSD 2-Clause", "https://github.com/maplibre/maplibre-native"),
    License("Jetpack Compose, AndroidX", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    License("Kotlin, kotlinx.coroutines", "Apache License 2.0", "https://kotlinlang.org"),
    License("Material Symbols", "Apache License 2.0", "https://fonts.google.com/icons"),
    License("OpenStreetMap", "ODbL 1.0 · © OpenStreetMap contributors", "https://www.openstreetmap.org/copyright"),
    License("OpenFreeMap", "© OpenFreeMap, OpenMapTiles", "https://openfreemap.org"),
    License("CARTO basemaps", "CC BY 3.0 · © CARTO", "https://carto.com/attributions"),
)

/** Ekran „O aplikacji”: wersja, autor, prywatność i licencje użytych bibliotek oraz map. */
@Composable
fun AboutScreen(vm: AppViewModel) {
    val platform = LocalPlatform.current
    BackHandler { vm.aboutOpen = false }
    Box(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.wstecz), size = 40.dp) { vm.aboutOpen = false }
                Text(
                    tr(R.string.about_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 14.dp).semantics { heading() },
                )
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.open_trace_track_mark), null, Modifier.size(88.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("OpenTraceTrack", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(tr(R.string.wersja_x, BuildConfig.VERSION_NAME), color = Palette.TextDim, fontSize = 13.sp)
                }

                SectionTitle(tr(R.string.privacy_title))
                Group {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrivacyLine(Icons.Rounded.Lock, tr(R.string.privacy_local))
                        PrivacyLine(Icons.Rounded.LocationOn, tr(R.string.privacy_location))
                        PrivacyLine(Icons.Rounded.CheckCircle, tr(R.string.privacy_network))
                        PrivacyLine(Icons.Rounded.BugReport, tr(R.string.privacy_crash))
                    }
                }

                SectionTitle(tr(R.string.licenses_title))
                Group {
                    SettingRow("OpenTraceTrack", tr(R.string.license_app)) { platform.openUrl("https://redzikowski.com/portfolio/opentracetrack") }
                    licenses.forEach { l -> SettingRow(l.name, l.license) { platform.openUrl(l.url) } }
                }
                Text(
                    tr(R.string.about_author),
                    color = Palette.TextFaint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp),
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PrivacyLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Palette.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// ------------------------------------------------------------------------------------- powitanie

private class OnboardingPage(val icon: ImageVector, val title: String, val text: String)

/** Pierwsze uruchomienie: po co lokalizacja, powiadomienie i praca w tle – zanim system o nie zapyta. */
@Composable
fun OnboardingDialog(vm: AppViewModel) {
    val platform = LocalPlatform.current
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        OnboardingPage(Icons.Rounded.LocationOn, tr(R.string.onb_welcome_title), tr(R.string.onb_welcome_text)),
        OnboardingPage(Icons.Rounded.LocationOn, tr(R.string.onb_location_title), tr(R.string.onb_location_text)),
        OnboardingPage(Icons.Rounded.Notifications, tr(R.string.onb_notif_title), tr(R.string.onb_notif_text)),
        OnboardingPage(Icons.Rounded.BatterySaver, tr(R.string.onb_battery_title), tr(R.string.onb_battery_text)),
    )
    val p = pages[page]
    fun finish() = vm.updateSettings { it.copy(onboarded = true) }
    DarkDialog(
        p.title,
        onDismiss = { finish() },
        icon = if (page == 0) null else p.icon,
        buttons = {
            if (page < pages.lastIndex) PillButton(tr(R.string.onb_skip), primary = false) { finish() }
            when (page) {
                1 -> PillButton(tr(R.string.zezwol)) { platform.requestLocation { page++ } }
                2 -> PillButton(tr(R.string.zezwol)) { platform.requestNotifications { page++ } }
                pages.lastIndex -> {
                    if (!platform.isIgnoringBattery()) PillButton(tr(R.string.battery_allow), primary = false) { platform.requestIgnoreBattery() }
                    PillButton(tr(R.string.onb_start)) { finish() }
                }
                else -> PillButton(tr(R.string.onb_next)) { page++ }
            }
        },
    ) {
        if (page == 0) {
            Image(painterResource(R.drawable.open_trace_track_mark), null, Modifier.size(72.dp).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
        }
        Text(p.text, color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            pages.indices.forEach { i ->
                Box(Modifier.size(if (i == page) 8.dp else 6.dp).clip(CircleShape).background(if (i == page) Color.White else Palette.TextFaint))
            }
        }
    }
}

// ----------------------------------------------------------------------------------------- bateria

/** Producent telefonu → klucz strony dontkillmyapp.com i opis kroków. */
private fun vendor(): Pair<String, Int> {
    val m = (Build.MANUFACTURER ?: "").lowercase()
    return when {
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "xiaomi" to R.string.battery_xiaomi
        m.contains("samsung") -> "samsung" to R.string.battery_samsung
        m.contains("huawei") || m.contains("honor") -> "huawei" to R.string.battery_huawei
        m.contains("oneplus") -> "oneplus" to R.string.battery_oppo
        m.contains("oppo") || m.contains("realme") -> "oppo" to R.string.battery_oppo
        m.contains("vivo") -> "vivo" to R.string.battery_vivo
        m.contains("motorola") || m.contains("lenovo") -> "motorola" to R.string.battery_generic
        m.contains("sony") -> "sony" to R.string.battery_generic
        m.contains("nokia") || m.contains("hmd") -> "nokia" to R.string.battery_generic
        m.contains("asus") -> "asus" to R.string.battery_generic
        m.contains("google") -> "google" to R.string.battery_generic
        else -> "" to R.string.battery_generic
    }
}

/** Jak zapewnić, że system nie przerwie nagrywania: stan optymalizacji i wskazówki dla producenta. */
@Composable
fun BatteryGuideDialog(onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    var ignoring by remember { mutableStateOf(platform.isIgnoringBattery()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { ignoring = platform.isIgnoringBattery() }
    val (key, steps) = vendor()
    DarkDialog(
        tr(R.string.praca_w_tle),
        onDismiss = onDismiss,
        icon = Icons.Rounded.BatterySaver,
        buttons = {
            PillButton(tr(R.string.zamknij), primary = false) { onDismiss() }
            if (!ignoring) PillButton(tr(R.string.battery_allow)) {
                platform.requestIgnoreBattery()
                tick++
            }
        },
    ) {
        Text(
            if (ignoring) tr(R.string.battery_ok) else tr(R.string.battery_restricted),
            color = if (ignoring) Palette.Ok else Palette.Warn,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(tr(steps, Build.MANUFACTURER.replaceFirstChar { it.uppercase() }), color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr(R.string.battery_system_settings), primary = false) { platform.openBatterySettings() }
            PillButton(tr(R.string.battery_more), primary = false) {
                platform.openUrl("https://dontkillmyapp.com/" + key)
            }
        }
    }
}

// ----------------------------------------------------------------------------------- dziennik błędów

@Composable
fun CrashLogDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val text = remember { CrashLog.read().takeLast(6000) }
    DarkDialog(
        tr(R.string.crash_title),
        onDismiss = onDismiss,
        icon = Icons.Rounded.BugReport,
        buttons = {
            PillButton(tr(R.string.crash_delete), primary = false) {
                vm.clearCrashLog()
                onDismiss()
            }
            PillButton(tr(R.string.udostepnij)) { platform.startActivity(vm.crashLogIntent()) }
        },
    ) {
        Text(tr(R.string.crash_text), color = Palette.TextDim, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Ink).padding(10.dp),
        )
    }
}
