package com.blacksingularities.gpxreg.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.gpxreg.BuildConfig
import com.blacksingularities.gpxreg.LocalPlatform
import com.blacksingularities.gpxreg.data.HudColor
import com.blacksingularities.gpxreg.data.HudStyle
import com.blacksingularities.gpxreg.data.MapStyle
import com.blacksingularities.gpxreg.data.ModeDetection
import com.blacksingularities.gpxreg.data.UnitSystem
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.AppLanguage
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.io.DataPort
import com.blacksingularities.gpxreg.io.REPO_URL
import com.blacksingularities.gpxreg.record.SensorHub
import com.blacksingularities.gpxreg.update.UpdateState
import com.blacksingularities.gpxreg.ui.theme.DarkDialog
import com.blacksingularities.gpxreg.ui.theme.Group
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.PillButton
import com.blacksingularities.gpxreg.ui.theme.RoundIcon
import com.blacksingularities.gpxreg.ui.theme.SectionTitle
import com.blacksingularities.gpxreg.ui.theme.SettingRow
import com.blacksingularities.gpxreg.ui.theme.ToggleRow
import com.blacksingularities.gpxreg.ui.theme.press
import kotlin.math.roundToInt

private class Choice<T>(val title: String, val options: List<Pair<T, String>>, val selected: T, val onPick: (T) -> Unit)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val platform = LocalPlatform.current
    val context = LocalContext.current
    val units = remember(s.units) { Units(s.units) }
    var choice by remember { mutableStateOf<Choice<*>?>(null) }
    var showModes by remember { mutableStateOf(false) }
    BackHandler { vm.settingsOpen = false }

    fun <T> pick(title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
        choice = Choice(title, options, selected, onPick)
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), size = 40.dp) { vm.settingsOpen = false }
                Text(tr("Ustawienia"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 14.dp))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                UpdateBanner(vm, Modifier.padding(top = 6.dp))

                SectionTitle(tr("Ogólne"))
                Group {
                    SettingRow(
                        tr("Język"),
                        icon = Icons.Rounded.Language,
                        value = when (s.language) {
                            AppLanguage.SYSTEM -> tr("Systemowy")
                            AppLanguage.PL -> "Polski"
                            AppLanguage.EN -> "English"
                        },
                    ) {
                        pick(tr("Język"), listOf(AppLanguage.SYSTEM to tr("Systemowy"), AppLanguage.PL to "Polski", AppLanguage.EN to "English"), s.language) { v ->
                            vm.updateSettings { it.copy(language = v) }
                        }
                    }
                    SettingRow(
                        tr("Jednostki"),
                        icon = Icons.Rounded.Straighten,
                        value = "${units.speedUnit} · ${units.altUnit}",
                    ) {
                        pick(
                            tr("Jednostki"),
                            listOf(
                                UnitSystem.METRIC to tr("Metryczne (km/h, m)"),
                                UnitSystem.IMPERIAL to tr("Imperialne (mph, ft)"),
                                UnitSystem.NAUTICAL to tr("Lotnicze / morskie (kn, ft, NM)"),
                            ),
                            s.units,
                        ) { v -> vm.updateSettings { it.copy(units = v) } }
                    }
                    ToggleRow(tr("Wibracje"), icon = Icons.Rounded.Vibration, checked = s.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
                }

                SectionTitle("HUD")
                Group {
                    SettingRow(
                        tr("Styl"),
                        tr("Lotniczy – taśmy i sztuczny horyzont; prosty – duże cyfry"),
                        Icons.Rounded.Speed,
                        value = if (s.hudStyle == HudStyle.AVIATION) tr("Lotniczy") else tr("Prosty"),
                    ) {
                        pick(tr("Styl HUD"), listOf(HudStyle.AVIATION to tr("Lotniczy"), HudStyle.SIMPLE to tr("Prosty")), s.hudStyle) { v ->
                            vm.updateSettings { it.copy(hudStyle = v) }
                        }
                    }
                    SettingRow(tr("Kolor symboli"), icon = Icons.Rounded.Palette, trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudColor.entries.forEach { c ->
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(c.argb))
                                        .border(3.dp, if (s.hudColor == c) Color.White else Color.Transparent, CircleShape)
                                        .border(5.dp, if (s.hudColor == c) Palette.PanelHigh else Color.Transparent, CircleShape)
                                        .press(label = c.name) { vm.updateSettings { it.copy(hudColor = c) } },
                                )
                            }
                        }
                    })
                    ToggleRow(tr("Poświata"), tr("Miękki blask jak na wyświetlaczu przeziernym"), Icons.Rounded.LightMode, s.hudGlow) { v -> vm.updateSettings { it.copy(hudGlow = v) } }
                    ToggleRow(
                        tr("Sztuczny horyzont"),
                        tr("Pochylenie i przechylenie telefonu – działa, gdy telefon stoi pionowo w uchwycie"),
                        Icons.Rounded.WbTwilight,
                        s.hudHorizon,
                    ) { v -> vm.updateSettings { it.copy(hudHorizon = v) } }
                    ToggleRow(tr("Ekran zawsze włączony"), tr("Na ekranie nagrywania"), Icons.Rounded.Visibility, s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
                }

                SectionTitle(tr("Nagrywanie"))
                Group {
                    SettingRow(tr("Domyślny środek transportu"), icon = s.defaultMode.icon, value = s.defaultMode.label) { showModes = true }
                    SettingRow(
                        tr("Wykrywanie zmiany środka transportu"),
                        when (s.modeDetection) {
                            ModeDetection.OFF -> tr("Tylko ręcznie")
                            ModeDetection.SUGGEST -> tr("Podpowiada przesiadkę, decydujesz Ty")
                            ModeDetection.AUTO -> tr("Sam dzieli trasę po zmianie prędkości")
                        },
                        Icons.Rounded.Compress,
                        value = when (s.modeDetection) {
                            ModeDetection.OFF -> tr("Wyłączone")
                            ModeDetection.SUGGEST -> tr("Podpowiedzi")
                            ModeDetection.AUTO -> tr("Automatycznie")
                        },
                    ) {
                        pick(
                            tr("Wykrywanie zmiany środka transportu"),
                            listOf(ModeDetection.OFF to tr("Wyłączone"), ModeDetection.SUGGEST to tr("Podpowiedzi"), ModeDetection.AUTO to tr("Automatycznie")),
                            s.modeDetection,
                        ) { v -> vm.updateSettings { it.copy(modeDetection = v) } }
                    }
                    SettingRow(tr("Odstęp punktów"), tr("Krótszy – dokładniejsza trasa, większy plik"), Icons.Rounded.Timer, value = tr("{0} s", s.interval)) {
                        pick(tr("Odstęp punktów"), listOf(1, 2, 3, 5, 10).map { it to tr("co {0} s", it) }, s.interval) { v -> vm.updateSettings { it.copy(interval = v) } }
                    }
                    SettingRow(tr("Filtr dokładności"), tr("Pozycje mniej dokładne są pomijane"), Icons.Rounded.GpsFixed, value = "±${s.maxAccuracy} m") {
                        pick(tr("Filtr dokładności"), listOf(10, 20, 35, 50, 100).map { it to "±$it m" }, s.maxAccuracy) { v -> vm.updateSettings { it.copy(maxAccuracy = v) } }
                    }
                    ToggleRow(tr("Automatyczna pauza"), tr("Postój nie zawyża czasu i nie dodaje szumu do trasy"), Icons.Rounded.PauseCircle, s.autoPause) { v ->
                        vm.updateSettings { it.copy(autoPause = v) }
                    }
                    if (s.autoPause) {
                        SettingRow(tr("Pauza po postoju"), value = tr("{0} s", s.autoPauseDelay)) {
                            pick(tr("Pauza po postoju"), listOf(10, 20, 30, 60, 120).map { it to tr("{0} s", it) }, s.autoPauseDelay) { v -> vm.updateSettings { it.copy(autoPauseDelay = v) } }
                        }
                    }
                    SettingRow(
                        tr("Ostrzeżenie o prędkości"),
                        tr("Wibracja i czerwony pasek w HUD po przekroczeniu"),
                        Icons.Rounded.Speed,
                        value = if (s.speedAlert <= 0) tr("Wyłączone") else units.fmtSpeed(s.speedAlert, 0),
                    ) {
                        val opts = listOf(0, 30, 50, 70, 90, 100, 120, 140).map { v ->
                            val ms = if (v == 0) 0.0 else units.speedToMs(v.toDouble())
                            ms to if (v == 0) tr("Wyłączone") else "$v ${units.speedUnit}"
                        }
                        val current = opts.minByOrNull { kotlin.math.abs(it.first - s.speedAlert) }?.first ?: 0.0
                        pick(tr("Ostrzeżenie o prędkości"), opts, current) { v -> vm.updateSettings { it.copy(speedAlert = v) } }
                    }
                    ToggleRow(
                        tr("Barometr"),
                        if (SensorHub.hasBarometer(context)) tr("Dokładniejsza prędkość pionowa i ciśnienie w punktach") else tr("Ten telefon nie ma barometru"),
                        Icons.Rounded.Compress,
                        s.useBarometer && SensorHub.hasBarometer(context),
                        enabled = SensorHub.hasBarometer(context),
                    ) { v -> vm.updateSettings { it.copy(useBarometer = v) } }
                    SettingRow(
                        tr("Praca w tle"),
                        tr("Wyłącz optymalizację baterii dla OpenTraceTrack, jeśli długie nagrania się przerywają"),
                        Icons.Rounded.BatterySaver,
                    ) { platform.openBatterySettings() }
                }

                SectionTitle(tr("Mapa"))
                Group {
                    SettingRow(
                        tr("Styl mapy"),
                        icon = Icons.Rounded.Layers,
                        value = when (s.mapStyle) {
                            MapStyle.DARK -> tr("Ciemna")
                            MapStyle.LIGHT -> tr("Jasna")
                            MapStyle.STREETS -> tr("Ulice (kolorowa)")
                        },
                    ) {
                        pick(tr("Styl mapy"), listOf(MapStyle.DARK to tr("Ciemna"), MapStyle.LIGHT to tr("Jasna"), MapStyle.STREETS to tr("Ulice (kolorowa)")), s.mapStyle) { v ->
                            vm.updateSettings { it.copy(mapStyle = v) }
                        }
                    }
                }

                SectionTitle(tr("Dane"))
                Group {
                    SettingRow(tr("Importuj pliki GPX"), tr("Z dowolnej aplikacji – typ aktywności zostanie rozpoznany"), Icons.Rounded.FileUpload) { platform.pickGpx() }
                    SettingRow(tr("Zapisz kopię bazy"), tr("Jeden plik SQLite ze wszystkimi trasami – otworzysz go też w DB Browser"), Icons.Rounded.Backup) {
                        platform.createDocument(DataPort.backupName(), "application/vnd.sqlite3") { vm.exportBackup(it) }
                    }
                    SettingRow(tr("Wczytaj kopię bazy"), tr("Scala trasy z kopią – istniejące nie zostaną zdublowane"), Icons.Rounded.Restore) { platform.pickBackup() }
                }

                SectionTitle(tr("Aplikacja"))
                Group {
                    val u = vm.update
                    SettingRow(
                        tr("Aktualizacje"),
                        when (u) {
                            UpdateState.Checking -> tr("Sprawdzanie…")
                            UpdateState.UpToDate -> tr("Masz najnowszą wersję")
                            is UpdateState.Available -> tr("Dostępna wersja {0}", u.info.version)
                            is UpdateState.Downloading -> tr("Pobieranie {0}%", (u.progress * 100).roundToInt())
                            is UpdateState.Ready -> tr("Gotowa do instalacji")
                            is UpdateState.Installing -> tr("Instalowanie…")
                            is UpdateState.Failed -> u.message
                            UpdateState.Idle -> tr("Wersja {0}", BuildConfig.VERSION_NAME)
                        },
                        Icons.Rounded.SystemUpdate,
                    ) {
                        if (u is UpdateState.Idle || u is UpdateState.UpToDate || u is UpdateState.Failed && u.info == null) vm.checkUpdate()
                        vm.updateSheet = true
                    }
                    ToggleRow(tr("Sprawdzaj automatycznie"), tr("Przy uruchomieniu, najwyżej co 6 godzin"), checked = s.autoUpdate) { v -> vm.updateSettings { it.copy(autoUpdate = v) } }
                    SettingRow(tr("Kod źródłowy"), "github.com/BlackSingularities/OpenTraceTrack", Icons.Rounded.Code) { platform.openUrl(REPO_URL) }
                }
                Text(
                    tr("OpenTraceTrack {0} · licencja MIT · mapy © OpenStreetMap, CARTO, OpenFreeMap", BuildConfig.VERSION_NAME),
                    color = Palette.TextFaint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp),
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    choice?.let { c ->
        @Suppress("UNCHECKED_CAST")
        val cc = c as Choice<Any?>
        DarkDialog(cc.title, onDismiss = { choice = null }, buttons = { PillButton(tr("Zamknij"), primary = false) { choice = null } }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cc.options.forEach { (value, label) ->
                    val on = value == cc.selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(if (on) Color.White else Color.Transparent)
                            .press(label = label) {
                                cc.onPick(value)
                                choice = null
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = if (on) Palette.Ink else Color.White, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }

    if (showModes) {
        DarkDialog(tr("Domyślny środek transportu"), onDismiss = { showModes = false }, buttons = { PillButton(tr("Zamknij"), primary = false) { showModes = false } }) {
            ModeGrid(s.defaultMode) { m ->
                vm.updateSettings { it.copy(defaultMode = m) }
                showModes = false
            }
        }
    }
}
