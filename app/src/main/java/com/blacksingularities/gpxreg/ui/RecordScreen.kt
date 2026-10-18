package com.blacksingularities.gpxreg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.gpxreg.LocalPlatform
import com.blacksingularities.gpxreg.R
import com.blacksingularities.gpxreg.data.AppSettings
import com.blacksingularities.gpxreg.data.LatLon
import com.blacksingularities.gpxreg.data.RecordView
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.record.LocationHub
import com.blacksingularities.gpxreg.record.RecState
import com.blacksingularities.gpxreg.record.RecStatus
import com.blacksingularities.gpxreg.record.Recorder
import com.blacksingularities.gpxreg.record.SensorHub
import com.blacksingularities.gpxreg.record.Telemetry
import com.blacksingularities.gpxreg.ui.hud.Hud
import com.blacksingularities.gpxreg.ui.hud.HudInput
import com.blacksingularities.gpxreg.ui.map.MapLine
import com.blacksingularities.gpxreg.ui.map.TrackMap
import com.blacksingularities.gpxreg.ui.theme.DarkDialog
import com.blacksingularities.gpxreg.ui.theme.NumberLarge
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.Pill
import com.blacksingularities.gpxreg.ui.theme.PillButton
import com.blacksingularities.gpxreg.ui.theme.RoundIcon
import com.blacksingularities.gpxreg.ui.theme.Segmented
import com.blacksingularities.gpxreg.ui.theme.press
import kotlinx.coroutines.delay

@Composable
fun RecordScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val platform = LocalPlatform.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rec by Recorder.state.collectAsStateWithLifecycle()
    val tel by Telemetry.state.collectAsStateWithLifecycle()
    val att by SensorHub.attitude.collectAsStateWithLifecycle()
    val gnss by LocationHub.gnss.collectAsStateWithLifecycle()
    val units = remember(settings.units) { Units(settings.units) }

    var hasPerm by remember { mutableStateOf(platform.hasLocation()) }
    var modeSheet by remember { mutableStateOf(false) }
    var stopDialog by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = System.currentTimeMillis()
            delay(1000)
        }
    }

    // GPS i czujniki działają, gdy ekran jest widoczny (podgląd HUD przed startem).
    DisposableEffect(hasPerm, settings.interval) {
        if (hasPerm) LocationHub.acquire(context, "ui", settings.interval * 1000L)
        onDispose { LocationHub.release("ui") }
    }
    DisposableEffect(settings.useBarometer) {
        SensorHub.acquire(context, "ui", attitude = true, barometer = settings.useBarometer)
        onDispose { SensorHub.release("ui") }
    }
    DisposableEffect(settings.keepScreenOn, rec.active) {
        platform.keepScreenOn(settings.keepScreenOn)
        onDispose { platform.keepScreenOn(false) }
    }
    LaunchedEffect(Unit) {
        if (!hasPerm) platform.requestLocation { hasPerm = it }
    }

    fun start(mode: TransportMode) {
        platform.requestLocation { ok ->
            hasPerm = ok
            if (!ok) {
                vm.snack(tr("Bez dostępu do lokalizacji nie da się nagrać trasy"), tr("Ustawienia")) { platform.openAppSettings() }
                return@requestLocation
            }
            if (!LocationHub.isGpsEnabled(context)) {
                vm.snack(tr("Lokalizacja w telefonie jest wyłączona"), tr("Włącz")) { platform.openLocationSettings() }
                return@requestLocation
            }
            platform.requestNotifications { Recorder.requestStart(context, mode) }
        }
    }

    val hudInput = HudInput(
        tel = tel,
        att = att,
        gnss = gnss,
        units = units,
        style = settings.hudStyle,
        color = Color(settings.hudColor.argb),
        glow = settings.hudGlow,
        horizon = settings.hudHorizon,
        maxSpeed = if (rec.active) rec.total.maxSpeed else 0.0,
        speedAlert = settings.speedAlert,
        clock = clock,
    )
    val me = if (tel.hasFix) LatLon(tel.lat, tel.lon) else null
    val lines = remember(rec.path) { rec.path.map { MapLine(it.mode.color, it.points) } }
    // Przed startem przycisk pokazuje domyślny środek transportu z ustawień.
    val shown = if (rec.active) rec else rec.copy(mode = settings.defaultMode)

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Ink)) {
        val landscape = maxWidth > maxHeight && maxWidth > 560.dp
        val tablet = maxWidth >= 840.dp
        val view = if (settings.mirror) RecordView.HUD else settings.recordView

        val hudBox: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                Hud(
                    hudInput,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { if (settings.mirror) scaleX = -1f },
                )
                if (!hasPerm) {
                    NoticeCard(Icons.Rounded.GpsOff, tr("Potrzebny dostęp do lokalizacji"), tr("HUD i nagrywanie korzystają z odbiornika GPS telefonu."), tr("Zezwól")) {
                        platform.requestLocation { ok -> hasPerm = ok; if (!ok) platform.openAppSettings() }
                    }
                } else if (!gnss.enabled) {
                    NoticeCard(Icons.Rounded.GpsOff, tr("Lokalizacja jest wyłączona"), tr("Włącz ją w ustawieniach telefonu, aby zobaczyć prędkość."), tr("Włącz")) {
                        platform.openLocationSettings()
                    }
                }
            }
        }
        val mapBox: @Composable (Modifier) -> Unit = { m ->
            Box(m.clip(RoundedCornerShape(22.dp))) {
                TrackMap(
                    styleUrl = settings.mapStyle.url,
                    lines = lines,
                    me = me,
                    follow = follow,
                    onFollowChange = { follow = it },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!follow) {
                    RoundIcon(
                        Icons.Rounded.MyLocation,
                        tr("Pokaż moją pozycję"),
                        Modifier.align(Alignment.BottomEnd).padding(10.dp),
                        size = 42.dp,
                        background = Palette.Ink,
                    ) { follow = true }
                }
                if (view == RecordView.MAP) MapSpeedBadge(tel.speed, units, Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }

        Column(Modifier.fillMaxSize()) {
            TopBar(vm, rec, settings, view, landscape)
            if (tablet) {
                when (view) {
                    RecordView.HUD -> hudBox(Modifier.weight(1f).fillMaxWidth())
                    RecordView.MAP -> mapBox(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp))
                    RecordView.SPLIT -> Row(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        hudBox(Modifier.weight(1f).fillMaxHeight())
                        mapBox(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp))
                    }
                }
                Banners(vm, rec)
                StatsStrip(shown, units, compact = false)
                Controls(shown, onStart = { start(settings.defaultMode) }, onStop = { stopDialog = true }, onMode = { modeSheet = true }, onLock = { locked = true })
            } else if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (view != RecordView.MAP) hudBox(Modifier.weight(if (view == RecordView.HUD) 1f else 0.55f).fillMaxHeight())
                    if (view != RecordView.HUD) mapBox(Modifier.weight(0.45f).fillMaxHeight().padding(bottom = 6.dp))
                    Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                        Banners(vm, rec)
                        StatsStrip(shown, units, compact = true)
                        Controls(shown, onStart = { start(settings.defaultMode) }, onStop = { stopDialog = true }, onMode = { modeSheet = true }, onLock = { locked = true })
                    }
                }
            } else {
                when (view) {
                    RecordView.HUD -> hudBox(Modifier.weight(1f).fillMaxWidth())
                    RecordView.MAP -> mapBox(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp))
                    RecordView.SPLIT -> {
                        hudBox(Modifier.weight(0.56f).fillMaxWidth())
                        mapBox(Modifier.weight(0.44f).fillMaxWidth().padding(horizontal = 10.dp))
                    }
                }
                Banners(vm, rec)
                StatsStrip(shown, units, compact = false)
                Controls(shown, onStart = { start(settings.defaultMode) }, onStop = { stopDialog = true }, onMode = { modeSheet = true }, onLock = { locked = true })
            }
        }

        if (locked) LockOverlay { locked = false }
    }

    if (modeSheet) ModeSheet(rec, settings, onDismiss = { modeSheet = false }) { m ->
        modeSheet = false
        if (rec.active) Recorder.switchMode(m) else vm.updateSettings { it.copy(defaultMode = m) }
    }

    if (stopDialog) {
        DarkDialog(
            tr("Zakończyć nagrywanie?"),
            onDismiss = { stopDialog = false },
            icon = Icons.Rounded.Stop,
            buttons = {
                PillButton(tr("Odrzuć"), primary = false) {
                    stopDialog = false
                    Recorder.discard()
                }
                PillButton(tr("Zapisz trasę")) {
                    stopDialog = false
                    Recorder.stop()
                }
            },
        ) {
            Text(
                tr("{0} w {1}, fragmenty: {2}. Trasa trafi na listę – stamtąd wyeksportujesz ją jako GPX.", units.fmtDist(rec.total.distance), Units.duration(rec.elapsed), rec.segments.size),
                color = Palette.TextDim,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun TopBar(vm: AppViewModel, rec: RecState, settings: AppSettings, view: RecordView, landscape: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.open_trace_track_mark),
                contentDescription = tr("OpenTraceTrack"),
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "OpenTraceTrack",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.35).sp,
            )
            Spacer(Modifier.weight(1f))
            StatusPill(rec)
            Spacer(Modifier.width(8.dp))
            RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), size = 40.dp, badge = if (vm.updatePending) "1" else null) { vm.settingsOpen = true }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!settings.mirror) {
                Segmented(
                    listOf(RecordView.HUD to "HUD", RecordView.SPLIT to tr("Oba"), RecordView.MAP to tr("Mapa")),
                    selected = view,
                    modifier = Modifier.weight(1f).widthIn(max = if (landscape) 280.dp else 320.dp),
                ) { v -> vm.updateSettings { it.copy(recordView = v) } }
            } else {
                Spacer(Modifier.weight(1f))
            }
            RoundIcon(Icons.Rounded.Flip, tr("Odbicie na szybie (lustro)"), size = 40.dp, active = settings.mirror) {
                vm.updateSettings { it.copy(mirror = !it.mirror) }
                if (!settings.mirror) vm.snack(tr("Tryb lustrzany: połóż telefon pod szybą – HUD odbije się czytelnie"))
            }
        }
    }
}

@Composable
private fun StatusPill(rec: RecState) {
    val blink = rememberInfiniteTransition(label = "rec")
    val a by blink.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot")
    val (text, color) = when (rec.status) {
        RecStatus.RECORDING -> Units.duration(rec.elapsed) to Palette.Record
        RecStatus.PAUSED -> tr("Pauza") to Palette.Warn
        RecStatus.AUTO_PAUSED -> tr("Postój") to Palette.Warn
        RecStatus.IDLE -> tr("Gotowy") to Palette.TextFaint
    }
    Row(
        Modifier.clip(Pill).background(Palette.PanelHigh).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .graphicsLayer { alpha = if (rec.status == RecStatus.RECORDING) a else 1f }
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = NumberLarge.copy(fontSize = 14.sp))
    }
}

@Composable
private fun Banners(vm: AppViewModel, rec: RecState) {
    val context = LocalContext.current
    AnimatedVisibility(rec.interrupted, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        BannerRow(
            Icons.Rounded.Warning,
            tr("Nagrywanie zostało przerwane"),
            tr("System zamknął aplikację. Punkty do tej chwili są zapisane."),
        ) {
            PillButton(tr("Zakończ"), primary = false) { Recorder.stop() }
            Spacer(Modifier.width(6.dp))
            PillButton(tr("Wznów")) { Recorder.requestResumeInterrupted(context) }
        }
    }
    val sug = rec.suggestion
    AnimatedVisibility(sug != null && !rec.interrupted, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        if (sug != null) {
            BannerRow(
                sug.mode.icon,
                tr("Wygląda na: {0}", sug.mode.label),
                tr("Od {0} prędkość pasuje do innego środka transportu.", Units.time(sug.since)),
            ) {
                RoundIcon(Icons.Rounded.Close, tr("Nie zmieniaj"), size = 38.dp) { Recorder.dismissSuggestion() }
                Spacer(Modifier.width(6.dp))
                PillButton(tr("Przełącz")) { Recorder.switchMode(sug.mode, sug.since) }
            }
        }
    }
    AnimatedVisibility(rec.active && rec.poorSignal && !rec.interrupted) {
        Text(
            tr("Słaby sygnał GPS – czekam na dokładniejszą pozycję"),
            color = Palette.Warn,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BannerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.PanelHigh)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 14.sp)
        }
        actions()
    }
}

@Composable
private fun StatsStrip(rec: RecState, units: Units, compact: Boolean) {
    val t = rec.total
    val cells = buildList {
        add(tr("Dystans") to units.fmtDist(t.distance))
        add(tr("Czas") to Units.duration(rec.elapsed))
        if (rec.mode.group == com.blacksingularities.gpxreg.data.ModeGroup.FOOT || rec.mode.group == com.blacksingularities.gpxreg.data.ModeGroup.RUN) {
            add(tr("Tempo") + " /" + units.distUnit to units.fmtPace(t.avgMovingSpeed).substringBefore(" /"))
        } else {
            add(tr("Średnia") + " " + units.speedUnit to units.num(units.speed(t.avgMovingSpeed), 1))
        }
        add(tr("W górę") to units.fmtAlt(t.ascent))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            cells.forEach { (k, v) ->
                Column(Modifier.weight(1f)) {
                    Text(v, color = if (rec.active) Color.White else Palette.TextFaint, style = NumberLarge.copy(fontSize = if (compact) 16.sp else 19.sp), maxLines = 1)
                    Text(k, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        if (rec.segments.size > 1) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                rec.segments.forEachIndexed { i, s ->
                    Icon(s.mode.icon, s.mode.label, tint = s.mode.tint, modifier = Modifier.size(16.dp))
                    Text(" " + units.fmtDist(s.stats.distance), color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (i < rec.segments.lastIndex) Text("  →  ", color = Palette.TextFaint, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Controls(rec: RecState, onStart: () -> Unit, onStop: () -> Unit, onMode: () -> Unit, onLock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Środek transportu – zmiana w trakcie zaczyna nowy fragment.
        Row(
            Modifier
                .weight(1f)
                .height(56.dp)
                .clip(Pill)
                .background(Palette.PanelHigh)
                .press(label = tr("Środek transportu")) { onMode() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(rec.mode.icon, null, tint = rec.mode.tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.mode.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (rec.active) tr("Dotknij, aby zmienić") else tr("Środek transportu"), color = Palette.TextDim, fontSize = 11.sp, maxLines = 1)
            }
            Icon(Icons.Rounded.SwapHoriz, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        if (!rec.active) {
            RecordButton(onStart)
        } else {
            val paused = rec.status == RecStatus.PAUSED
            RoundIcon(
                if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                if (paused) tr("Wznów") else tr("Pauza"),
                size = 64.dp,
                active = true,
            ) { if (paused) Recorder.resume() else Recorder.pause() }
            Spacer(Modifier.width(10.dp))
            RoundIcon(Icons.Rounded.Stop, tr("Zakończ"), size = 52.dp, background = Palette.Record) { onStop() }
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundIcon(Icons.Rounded.AddLocationAlt, tr("Dodaj znacznik"), size = 40.dp, enabled = rec.active) { Recorder.addWaypoint() }
            RoundIcon(Icons.Rounded.Lock, tr("Zablokuj ekran"), size = 40.dp) { onLock() }
        }
    }
}

/** Duży czerwony przycisk nagrywania z obwódką – jak spust migawki. */
@Composable
private fun RecordButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .border(3.dp, Color.White, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(Palette.Record)
            .press(label = tr("Rozpocznij nagrywanie"), onClick = onClick),
    )
}

@Composable
private fun MapSpeedBadge(speed: Double, units: Units, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Ink.copy(alpha = 0.82f)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(units.num(units.speed(speed), 0), color = Color.White, style = NumberLarge.copy(fontSize = 34.sp))
        Text(units.speedUnit, color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NoticeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Ink.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).clip(RoundedCornerShape(24.dp)).background(Palette.PanelHigh).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(text, color = Palette.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            PillButton(action, onClick = onAction)
        }
    }
}

/** Blokada przed przypadkowym dotykiem (np. w kieszeni lub uchwycie rowerowym). */
@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = 30.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .clip(Pill)
                .background(Palette.PanelHigh)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onUnlock() }) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.LockOpen, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("Przytrzymaj, aby odblokować"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSheet(rec: RecState, settings: AppSettings, onDismiss: () -> Unit, onPick: (TransportMode) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.PanelHigh,
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text(if (rec.active) tr("Zmień środek transportu") else tr("Czym się poruszasz?"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (rec.active) tr("Od tej chwili zacznie się nowy fragment – każdy wyeksportujesz jako osobny plik GPX.")
                else tr("Możesz go zmieniać w trakcie nagrywania, np. przy przesiadce z roweru do pociągu."),
                color = Palette.TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            ModeGrid(if (rec.active) rec.mode else settings.defaultMode, onPick = onPick)
        }
    }
}
