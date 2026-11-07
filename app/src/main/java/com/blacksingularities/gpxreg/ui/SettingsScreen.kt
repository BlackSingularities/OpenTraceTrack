package com.blacksingularities.gpxreg.ui

import com.blacksingularities.gpxreg.R
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.material.icons.rounded.Timeline
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
import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.i18n.trp
import com.blacksingularities.gpxreg.ui.map.OfflineMaps
import com.blacksingularities.gpxreg.ui.map.OfflineState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import com.blacksingularities.gpxreg.io.DataPort
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
    var battery by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf(false) }
    var deleteOffline by remember { mutableStateOf(false) }
    val offlineAreas by OfflineMaps.areas.collectAsStateWithLifecycle()
    val offline by OfflineMaps.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { OfflineMaps.refresh(context) }
    BackHandler { vm.settingsOpen = false }

    fun <T> pick(title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
        choice = Choice(title, options, selected, onPick)
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.wstecz), size = 40.dp) { vm.settingsOpen = false }
                Text(tr(R.string.ustawienia), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 14.dp))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                UpdateBanner(vm, Modifier.padding(top = 6.dp))

                SectionTitle(tr(R.string.ogolne))
                Group {
                    SettingRow(
                        tr(R.string.jezyk),
                        icon = Icons.Rounded.Language,
                        value = if (s.language.isBlank()) tr(R.string.systemowy) else I18n.displayName(s.language),
                    ) {
                        pick(tr(R.string.jezyk), listOf("" to tr(R.string.systemowy)) + I18n.available.map { it to I18n.displayName(it) }, s.language) { v ->
                            vm.updateSettings { it.copy(language = v) }
                        }
                    }
                    SettingRow(
                        tr(R.string.jednostki),
                        icon = Icons.Rounded.Straighten,
                        value = "${units.speedUnit} · ${units.altUnit}",
                    ) {
                        pick(
                            tr(R.string.jednostki),
                            listOf(
                                UnitSystem.METRIC to tr(R.string.metryczne_km_h_m),
                                UnitSystem.IMPERIAL to tr(R.string.imperialne_mph_ft),
                                UnitSystem.NAUTICAL to tr(R.string.lotnicze_morskie_kn_ft_nm),
                            ),
                            s.units,
                        ) { v -> vm.updateSettings { it.copy(units = v) } }
                    }
                    ToggleRow(tr(R.string.wibracje), icon = Icons.Rounded.Vibration, checked = s.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
                }

                SectionTitle("HUD")
                Group {
                    SettingRow(
                        tr(R.string.styl),
                        tr(R.string.tasmy_i_slad_albo_duze_cyfry),
                        Icons.Rounded.Speed,
                        value = if (s.hudStyle == HudStyle.AVIATION) tr(R.string.lotniczy) else tr(R.string.prosty),
                    ) {
                        pick(tr(R.string.styl_hud), listOf(HudStyle.AVIATION to tr(R.string.lotniczy), HudStyle.SIMPLE to tr(R.string.prosty)), s.hudStyle) { v ->
                            vm.updateSettings { it.copy(hudStyle = v) }
                        }
                    }
                    SettingRow(tr(R.string.kolor_symboli), icon = Icons.Rounded.Palette, trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudColor.entries.forEach { c ->
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(c.argb))
                                        .border(3.dp, if (s.hudColor == c) Color.White else Color.Transparent, CircleShape)
                                        .border(5.dp, if (s.hudColor == c) Palette.PanelHigh else Color.Transparent, CircleShape)
                                        .press(label = hudColorName(c)) { vm.updateSettings { it.copy(hudColor = c) } },
                                )
                            }
                        }
                    })
                    ToggleRow(tr(R.string.poswiata), tr(R.string.blask_jak_na_prawdziwym_hud), Icons.Rounded.LightMode, s.hudGlow) { v -> vm.updateSettings { it.copy(hudGlow = v) } }
                    ToggleRow(
                        tr(R.string.slad_ruchu),
                        tr(R.string.droga_i_wektor_30_s_kierunek),
                        Icons.Rounded.Timeline,
                        s.hudHorizon,
                    ) { v -> vm.updateSettings { it.copy(hudHorizon = v) } }
                    ToggleRow(tr(R.string.ekran_zawsze_wlaczony), tr(R.string.na_ekranie_nagrywania), Icons.Rounded.Visibility, s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
                }

                SectionTitle(tr(R.string.nagrywanie))
                Group {
                    SettingRow(tr(R.string.domyslny_srodek_transportu), icon = s.defaultMode.icon, value = s.defaultMode.label) { showModes = true }
                    SettingRow(
                        tr(R.string.wykrywanie_przesiadek),
                        when (s.modeDetection) {
                            ModeDetection.OFF -> tr(R.string.tylko_recznie)
                            ModeDetection.SUGGEST -> tr(R.string.podpowiada_decydujesz_ty)
                            ModeDetection.AUTO -> tr(R.string.dzieli_trase_sam)
                        },
                        Icons.Rounded.Compress,
                        value = when (s.modeDetection) {
                            ModeDetection.OFF -> tr(R.string.wylaczone)
                            ModeDetection.SUGGEST -> tr(R.string.podpowiedzi)
                            ModeDetection.AUTO -> tr(R.string.automatycznie)
                        },
                    ) {
                        pick(
                            tr(R.string.wykrywanie_przesiadek),
                            listOf(ModeDetection.OFF to tr(R.string.wylaczone), ModeDetection.SUGGEST to tr(R.string.podpowiedzi), ModeDetection.AUTO to tr(R.string.automatycznie)),
                            s.modeDetection,
                        ) { v -> vm.updateSettings { it.copy(modeDetection = v) } }
                    }
                    SettingRow(tr(R.string.odstep_punktow), tr(R.string.krotszy_dokladniejsza_trasa_wiekszy_plik), Icons.Rounded.Timer, value = tr(R.string.x_s, s.interval)) {
                        pick(tr(R.string.odstep_punktow), listOf(1, 2, 3, 5, 10).map { it to tr(R.string.co_x_s, it) }, s.interval) { v -> vm.updateSettings { it.copy(interval = v) } }
                    }
                    SettingRow(tr(R.string.filtr_dokladnosci), tr(R.string.pozycje_mniej_dokladne_sa_pomijane), Icons.Rounded.GpsFixed, value = "±${s.maxAccuracy} m") {
                        pick(tr(R.string.filtr_dokladnosci), listOf(10, 20, 35, 50, 100).map { it to "±$it m" }, s.maxAccuracy) { v -> vm.updateSettings { it.copy(maxAccuracy = v) } }
                    }
                    ToggleRow(tr(R.string.automatyczna_pauza), tr(R.string.postoj_nie_zawyza_czasu_i_nie), Icons.Rounded.PauseCircle, s.autoPause) { v ->
                        vm.updateSettings { it.copy(autoPause = v) }
                    }
                    if (s.autoPause) {
                        SettingRow(tr(R.string.pauza_po_postoju), value = tr(R.string.x_s, s.autoPauseDelay)) {
                            pick(tr(R.string.pauza_po_postoju), listOf(10, 20, 30, 60, 120).map { it to tr(R.string.x_s, it) }, s.autoPauseDelay) { v -> vm.updateSettings { it.copy(autoPauseDelay = v) } }
                        }
                    }
                    SettingRow(
                        tr(R.string.ostrzezenie_o_predkosci),
                        tr(R.string.wibracja_i_czerwony_pasek_w_hud),
                        Icons.Rounded.Speed,
                        value = if (s.speedAlert <= 0) tr(R.string.wylaczone) else units.fmtSpeed(s.speedAlert, 0),
                    ) {
                        val opts = listOf(0, 30, 50, 70, 90, 100, 120, 140).map { v ->
                            val ms = if (v == 0) 0.0 else units.speedToMs(v.toDouble())
                            ms to if (v == 0) tr(R.string.wylaczone) else "$v ${units.speedUnit}"
                        }
                        val current = opts.minByOrNull { kotlin.math.abs(it.first - s.speedAlert) }?.first ?: 0.0
                        pick(tr(R.string.ostrzezenie_o_predkosci), opts, current) { v -> vm.updateSettings { it.copy(speedAlert = v) } }
                    }
                    ToggleRow(
                        tr(R.string.barometr),
                        if (SensorHub.hasBarometer(context)) tr(R.string.dokladniejsza_predkosc_pionowa_i_cisnienie_w) else tr(R.string.ten_telefon_nie_ma_barometru),
                        Icons.Rounded.Compress,
                        s.useBarometer && SensorHub.hasBarometer(context),
                        enabled = SensorHub.hasBarometer(context),
                    ) { v -> vm.updateSettings { it.copy(useBarometer = v) } }
                    SettingRow(
                        tr(R.string.praca_w_tle),
                        if (platform.isIgnoringBattery()) tr(R.string.battery_ok) else tr(R.string.wylacz_optymalizacje_baterii_dla_opentracetr),
                        Icons.Rounded.BatterySaver,
                    ) { battery = true }
                }

                SectionTitle(tr(R.string.mapa))
                Group {
                    SettingRow(
                        tr(R.string.styl_mapy),
                        icon = Icons.Rounded.Layers,
                        value = when (s.mapStyle) {
                            MapStyle.DARK -> tr(R.string.ciemna)
                            MapStyle.LIGHT -> tr(R.string.jasna)
                            MapStyle.STREETS -> tr(R.string.ulice_kolorowa)
                        },
                    ) {
                        pick(tr(R.string.styl_mapy), listOf(MapStyle.DARK to tr(R.string.ciemna), MapStyle.LIGHT to tr(R.string.jasna), MapStyle.STREETS to tr(R.string.ulice_kolorowa)), s.mapStyle) { v ->
                            vm.updateSettings { it.copy(mapStyle = v) }
                        }
                    }
                    SettingRow(
                        tr(R.string.offline_title),
                        when (val o = offline) {
                            is OfflineState.Downloading -> tr(R.string.offline_progress, (o.progress * 100).roundToInt(), android.text.format.Formatter.formatShortFileSize(context, o.bytes))
                            else -> if (offlineAreas.isEmpty()) tr(R.string.offline_hint) else trp(R.plurals.offline_areas, offlineAreas.size)
                        },
                        Icons.Rounded.CloudDownload,
                        value = if (offlineAreas.isNotEmpty()) tr(R.string.crash_delete) else null,
                    ) { if (offlineAreas.isNotEmpty()) deleteOffline = true }
                }

                SectionTitle(tr(R.string.dane))
                Group {
                    SettingRow(tr(R.string.importuj_pliki_gpx), tr(R.string.z_dowolnej_aplikacji_typ_aktywnosci_zostanie), Icons.Rounded.FileUpload) { platform.pickGpx() }
                    SettingRow(
                        tr(R.string.autobackup_title),
                        if (s.autoBackupUri.isBlank()) tr(R.string.autobackup_hint) else tr(R.string.autobackup_folder, vm.port.folderName(android.net.Uri.parse(s.autoBackupUri))),
                        Icons.Rounded.CloudSync,
                        value = if (s.autoBackupUri.isBlank()) tr(R.string.wylaczone) else null,
                    ) { platform.openFolder { vm.setAutoBackupFolder(it) } }
                    if (s.autoBackupUri.isNotBlank()) {
                        val freq = listOf(0 to tr(R.string.autobackup_each), 1 to tr(R.string.autobackup_daily), 7 to tr(R.string.autobackup_weekly))
                        SettingRow(tr(R.string.autobackup_when), icon = Icons.Rounded.Timer, value = freq.firstOrNull { it.first == s.autoBackupDays }?.second) {
                            pick(tr(R.string.autobackup_when), freq, s.autoBackupDays) { v -> vm.updateSettings { it.copy(autoBackupDays = v) } }
                        }
                        SettingRow(
                            tr(R.string.autobackup_now),
                            if (s.lastAutoBackup > 0) tr(R.string.autobackup_last, Units.dateTime(s.lastAutoBackup)) else tr(R.string.autobackup_never),
                            Icons.Rounded.Backup,
                        ) { vm.backupNow() }
                        SettingRow(tr(R.string.autobackup_off), icon = Icons.Rounded.CloudOff) { vm.disableAutoBackup() }
                    }
                    SettingRow(tr(R.string.zapisz_kopie_bazy), tr(R.string.jeden_plik_sqlite_ze_wszystkimi_trasami), Icons.Rounded.Backup) {
                        platform.createDocument(DataPort.backupName(), "application/vnd.sqlite3") { vm.exportBackup(it) }
                    }
                    SettingRow(tr(R.string.wczytaj_kopie_bazy), tr(R.string.scala_trasy_z_kopia_istniejace_nie), Icons.Rounded.Restore) { platform.pickBackup() }
                }

                SectionTitle(tr(R.string.aplikacja))
                Group {
                    val u = vm.update
                    SettingRow(
                        tr(R.string.aktualizacje),
                        when (u) {
                            UpdateState.Checking -> tr(R.string.sprawdzanie)
                            UpdateState.UpToDate -> tr(R.string.masz_najnowsza_wersje)
                            is UpdateState.Available -> tr(R.string.dostepna_wersja_x, u.info.version)
                            is UpdateState.Downloading -> tr(R.string.pobieranie_x, (u.progress * 100).roundToInt())
                            is UpdateState.Ready -> tr(R.string.gotowa_do_instalacji)
                            is UpdateState.Installing -> tr(R.string.instalowanie)
                            is UpdateState.Failed -> u.message
                            UpdateState.Idle -> tr(R.string.wersja_x, BuildConfig.VERSION_NAME)
                        },
                        Icons.Rounded.SystemUpdate,
                    ) {
                        if (u is UpdateState.Idle || u is UpdateState.UpToDate || u is UpdateState.Failed && u.info == null) vm.checkUpdate()
                        vm.updateSheet = true
                    }
                    ToggleRow(tr(R.string.sprawdzaj_automatycznie), tr(R.string.przy_uruchomieniu_najwyzej_co_6_godzin), checked = s.autoUpdate) { v -> vm.updateSettings { it.copy(autoUpdate = v) } }
                    SettingRow(tr(R.string.about_title), tr(R.string.about_subtitle), Icons.Rounded.Info) { vm.aboutOpen = true }
                    if (vm.crashLogAvailable) {
                        SettingRow(tr(R.string.crash_title), tr(R.string.crash_subtitle), Icons.Rounded.BugReport) { crashLog = true }
                    }
                    SettingRow(tr(R.string.onb_again), icon = Icons.Rounded.School) {
                        vm.settingsOpen = false
                        vm.updateSettings { it.copy(onboarded = false) }
                    }
                    SettingRow(tr(R.string.pomysl_i_wykonanie), "Adam Rędzikowski · redzikowski.com", Icons.Rounded.Person) { platform.openUrl("https://redzikowski.com") }
                    SettingRow(tr(R.string.kontakt), "kontakt@redzikowski.com", Icons.Rounded.Email) { platform.openUrl("mailto:kontakt@redzikowski.com") }
                    SettingRow(tr(R.string.postaw_kawe), tr(R.string.wesprzyj_rozwoj_na_buycoffee_to), Icons.Rounded.LocalCafe) { platform.openUrl("https://buycoffee.to/adamredzikowski") }
                }
                Text(
                    tr(R.string.opentracetrack_x_licencja_mit_mapy_openstree, BuildConfig.VERSION_NAME),
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
        DarkDialog(cc.title, onDismiss = { choice = null }, buttons = { PillButton(tr(R.string.zamknij), primary = false) { choice = null } }) {
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

    if (battery) BatteryGuideDialog { battery = false }
    if (crashLog) CrashLogDialog(vm) { crashLog = false }
    if (deleteOffline) {
        DarkDialog(
            tr(R.string.offline_delete_title),
            onDismiss = { deleteOffline = false },
            buttons = {
                PillButton(tr(R.string.anuluj), primary = false) { deleteOffline = false }
                PillButton(tr(R.string.crash_delete), danger = true) {
                    deleteOffline = false
                    OfflineMaps.deleteAll(context)
                }
            },
        ) {
            Text(trp(R.plurals.offline_areas, offlineAreas.size), color = Palette.TextDim, fontSize = 14.sp)
        }
    }

    if (showModes) {
        DarkDialog(tr(R.string.domyslny_srodek_transportu), onDismiss = { showModes = false }, buttons = { PillButton(tr(R.string.zamknij), primary = false) { showModes = false } }) {
            ModeGrid(s.defaultMode) { m ->
                vm.updateSettings { it.copy(defaultMode = m) }
                showModes = false
            }
        }
    }
}

private fun hudColorName(c: HudColor): String = when (c) {
    HudColor.WHITE -> tr(R.string.color_white)
    HudColor.GREEN -> tr(R.string.color_green)
    HudColor.AMBER -> tr(R.string.color_amber)
    HudColor.CYAN -> tr(R.string.color_cyan)
}
