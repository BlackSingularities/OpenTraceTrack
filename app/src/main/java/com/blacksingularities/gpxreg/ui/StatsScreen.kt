package com.blacksingularities.gpxreg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.gpxreg.data.Track
import com.blacksingularities.gpxreg.data.TrackDb
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.I18n
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.ui.theme.Metric
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.Segmented
import com.blacksingularities.gpxreg.ui.theme.press
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class Period { WEEK, MONTH, YEAR, ALL }

@Composable
fun StatsScreen(vm: AppViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val units = remember(settings.units) { Units(settings.units) }
    var period by remember { mutableStateOf(Period.MONTH) }
    var byMode by remember { mutableStateOf<List<TrackDb.ModeTotal>>(emptyList()) }
    var monthly by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    val since = remember(period) {
        val c = Calendar.getInstance()
        when (period) {
            Period.WEEK -> c.add(Calendar.DAY_OF_YEAR, -7)
            Period.MONTH -> c.add(Calendar.DAY_OF_YEAR, -30)
            Period.YEAR -> c.add(Calendar.DAY_OF_YEAR, -365)
            Period.ALL -> return@remember 0L
        }
        c.timeInMillis
    }
    LaunchedEffect(since, tracks) {
        withContext(Dispatchers.IO) {
            byMode = vm.db.totalsByMode(since)
            monthly = vm.db.monthlyDistance()
        }
    }
    val inPeriod = remember(tracks, since) { tracks.filter { !it.recording && it.startTime >= since } }

    Column(Modifier.fillMaxSize().background(Palette.Ink).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Text(tr("Statystyki"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        Spacer(Modifier.height(12.dp))
        Segmented(
            listOf(Period.WEEK to tr("7 dni"), Period.MONTH to tr("30 dni"), Period.YEAR to tr("Rok"), Period.ALL to tr("Całość")),
            period,
            Modifier.fillMaxWidth(),
        ) { period = it }

        if (inPeriod.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Insights, null, tint = Palette.TextDim, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(tr("Brak tras w tym okresie"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(tr("Statystyki policzą się same po pierwszym nagraniu."), color = Palette.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            return@Column
        }

        // Suma.
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Metric(tr("Dystans"), units.fmtDist(inPeriod.sumOf { it.stats.distance }), Modifier.weight(1f), big = true)
            Metric(tr("W ruchu"), Units.duration(inPeriod.sumOf { it.stats.movingTime }, seconds = false), Modifier.weight(1f), big = true)
            Metric(tr("Trasy"), inPeriod.size.toString(), Modifier.weight(0.6f), big = true)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Metric(tr("W górę"), units.fmtAlt(inPeriod.sumOf { it.stats.ascent }), Modifier.weight(1f))
            Metric(tr("Średnio na trasę"), units.fmtDist(inPeriod.sumOf { it.stats.distance } / inPeriod.size), Modifier.weight(1f))
            Spacer(Modifier.weight(0.6f))
        }

        // Środki transportu.
        if (byMode.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(tr("Środki transportu"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(10.dp))
            val maxD = byMode.maxOf { it.distance }.coerceAtLeast(1.0)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.PanelHigh).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                byMode.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(m.mode.icon, null, tint = m.mode.tint, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.mode.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(units.fmtDist(m.distance), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Palette.Ink)) {
                                Box(Modifier.fillMaxWidth((m.distance / maxD).toFloat().coerceIn(0.01f, 1f)).height(5.dp).clip(RoundedCornerShape(50)).background(m.mode.tint))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("{0} · tras: {1} · maks. {2}", Units.duration(m.movingTime, seconds = false), m.tracks, units.fmtSpeed(m.maxSpeed, 0)),
                                color = Palette.TextDim,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        // Miesiące.
        if (monthly.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(tr("Ostatnie 12 miesięcy"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(10.dp))
            val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
            val labelFmt = SimpleDateFormat("LLLLL", if (I18n.english) Locale.US else Locale.forLanguageTag("pl-PL"))
            val months = (11 downTo 0).map { back ->
                val c = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -back)
                }
                labelFmt.format(c.time) to units.dist(monthly[keyFmt.format(c.time)] ?: 0.0)
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.PanelHigh).padding(14.dp)) {
                BarChart(months, formatTop = { units.num(it, if (it < 10) 1 else 0) })
            }
        }

        // Rekordy.
        Spacer(Modifier.height(22.dp))
        Text(tr("Rekordy"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(10.dp))
        val all = tracks.filter { !it.recording }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.PanelHigh)) {
            all.maxByOrNull { it.stats.distance }?.let { RecordRow(vm, Icons.Rounded.Straighten, tr("Najdłuższa trasa"), units.fmtDist(it.stats.distance), it) }
            all.maxByOrNull { it.stats.maxSpeed }?.let { RecordRow(vm, Icons.Rounded.Speed, tr("Najwyższa prędkość"), units.fmtSpeed(it.stats.maxSpeed, 0), it) }
            all.filter { it.stats.maxEle != null }.maxByOrNull { it.stats.maxEle!! }?.let { RecordRow(vm, Icons.Rounded.Height, tr("Najwyżej"), units.fmtAlt(it.stats.maxEle), it) }
            all.maxByOrNull { it.stats.duration }?.let { RecordRow(vm, Icons.Rounded.Timer, tr("Najdłużej w drodze"), Units.duration(it.stats.duration, seconds = false), it) }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun RecordRow(vm: AppViewModel, icon: ImageVector, label: String, value: String, t: Track) {
    Row(
        Modifier.fillMaxWidth().press(label = label) {
            vm.tab = Tab.TRACKS
            vm.openTrack = t.id
        }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.TextDim, fontSize = 12.sp)
            Text(t.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
