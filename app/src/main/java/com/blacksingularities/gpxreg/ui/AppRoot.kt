package com.blacksingularities.gpxreg.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.record.Recorder
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.Pill
import com.blacksingularities.gpxreg.ui.theme.press
import kotlinx.coroutines.delay

@Composable
fun AppRoot(vm: AppViewModel) {
    // Odczyt stanu języka – zmiana w ustawieniach przerysowuje cały interfejs.
    @Suppress("UNUSED_VARIABLE")
    val english = I18n.english
    val rec by Recorder.state.collectAsStateWithLifecycle()
    var snack by remember { mutableStateOf<Snack?>(null) }
    LaunchedEffect(Unit) {
        vm.snacks.collect { snack = it }
    }
    LaunchedEffect(snack) {
        if (snack != null) {
            delay(if (snack?.action != null) 5000 else 2800)
            snack = null
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(vm.tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { tab ->
                    when (tab) {
                        Tab.RECORD -> RecordScreen(vm)
                        Tab.TRACKS -> TracksScreen(vm)
                        Tab.STATS -> StatsScreen(vm)
                    }
                }
            }
            BottomBar(vm.tab, rec.active, onSelect = { vm.tab = it })
        }

        AnimatedVisibility(
            vm.openTrack != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            val id = vm.openTrack
            if (id != null) TrackDetailScreen(vm, id)
        }
        AnimatedVisibility(
            vm.settingsOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) { SettingsScreen(vm) }

        // Komunikat na dole ekranu.
        AnimatedVisibility(
            snack != null,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 84.dp, start = 14.dp, end = 14.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val s = snack
            if (s != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.text, color = Palette.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                    if (s.action != null) {
                        Text(
                            s.action,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(Pill)
                                .background(Palette.Ink)
                                .press(label = s.action) {
                                    s.onAction?.invoke()
                                    snack = null
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }

    if (vm.updateSheet) UpdateSheet(vm)
    vm.whatsNew?.let { (v, notes) -> WhatsNewDialog(v, notes) { vm.whatsNew = null } }
}

@Composable
private fun BottomBar(tab: Tab, recording: Boolean, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NavItem(Icons.Rounded.Speed, tr("Nagrywanie"), tab == Tab.RECORD, Modifier.weight(1f), dot = recording && tab != Tab.RECORD) { onSelect(Tab.RECORD) }
        NavItem(Icons.Rounded.Route, tr("Trasy"), tab == Tab.TRACKS, Modifier.weight(1f)) { onSelect(Tab.TRACKS) }
        NavItem(Icons.Rounded.BarChart, tr("Statystyki"), tab == Tab.STATS, Modifier.weight(1f)) { onSelect(Tab.STATS) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, dot: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .clip(Pill)
            .background(if (selected) Color.White else Color.Transparent)
            .press(label = label, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(icon, null, tint = if (selected) Palette.Ink else Palette.TextDim, modifier = Modifier.size(22.dp))
            if (dot) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Palette.Record))
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
