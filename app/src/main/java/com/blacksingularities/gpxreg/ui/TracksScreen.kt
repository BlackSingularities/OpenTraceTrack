package com.blacksingularities.gpxreg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.gpxreg.LocalPlatform
import com.blacksingularities.gpxreg.data.LatLon
import com.blacksingularities.gpxreg.data.Track
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.ui.theme.DarkDialog
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.Pill
import com.blacksingularities.gpxreg.ui.theme.PillButton
import com.blacksingularities.gpxreg.ui.theme.RoundIcon
import com.blacksingularities.gpxreg.ui.theme.press
import kotlin.math.cos
import kotlin.math.max

private enum class Sort { NEWEST, OLDEST, LONGEST, FASTEST }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TracksScreen(vm: AppViewModel) {
    val platform = LocalPlatform.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val units = remember(settings.units) { Units(settings.units) }
    var query by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf<TransportMode?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.NEWEST) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exportIds by remember { mutableStateOf<List<Long>?>(null) }

    val visible = remember(tracks, query, modeFilter, favOnly, sort) {
        tracks
            .filter { !it.recording }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.notes.contains(query, ignoreCase = true) }
            .filter { modeFilter == null || it.segments.any { s -> s.mode == modeFilter } }
            .filter { !favOnly || it.favorite }
            .let { list ->
                when (sort) {
                    Sort.NEWEST -> list.sortedByDescending { it.startTime }
                    Sort.OLDEST -> list.sortedBy { it.startTime }
                    Sort.LONGEST -> list.sortedByDescending { it.stats.distance }
                    Sort.FASTEST -> list.sortedByDescending { it.stats.maxSpeed }
                }
            }
    }
    val presentModes = remember(tracks) { tracks.flatMap { t -> t.segments.map { it.mode } }.distinct() }
    val selecting = selected.isNotEmpty()

    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        // Nagłówek albo pasek zaznaczenia.
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                RoundIcon(Icons.Rounded.Close, tr("Anuluj zaznaczenie"), size = 40.dp) { selected = emptySet() }
                Text(tr("Zaznaczone: {0}", selected.size), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                RoundIcon(Icons.Rounded.FileDownload, tr("Eksportuj zaznaczone"), size = 40.dp) { exportIds = selected.toList() }
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Rounded.Delete, tr("Usuń zaznaczone"), size = 40.dp, background = Palette.Record) { confirmDelete = true }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(tr("Trasy"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    val totalDist = tracks.filter { !it.recording }.sumOf { it.stats.distance }
                    Text(
                        if (tracks.isEmpty()) tr("Twoje nagrania i zaimportowane pliki") else tr("{0} · razem {1}", tracks.count { !it.recording }, units.fmtDist(totalDist)),
                        color = Palette.TextDim,
                        fontSize = 13.sp,
                    )
                }
                RoundIcon(Icons.Rounded.FileUpload, tr("Importuj GPX"), size = 40.dp) { platform.pickGpx() }
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.AutoMirrored.Rounded.Sort, tr("Sortowanie"), size = 40.dp) {
                    sort = Sort.entries[(sort.ordinal + 1) % Sort.entries.size]
                    vm.snack(
                        when (sort) {
                            Sort.NEWEST -> tr("Od najnowszych")
                            Sort.OLDEST -> tr("Od najstarszych")
                            Sort.LONGEST -> tr("Od najdłuższych")
                            Sort.FASTEST -> tr("Od najszybszych")
                        },
                    )
                }
            }
        }

        UpdateBanner(vm, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        if (tracks.any { !it.recording }) {
            SearchField(query, { query = it }, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(tr("Wszystkie"), modeFilter == null && !favOnly) {
                    modeFilter = null
                    favOnly = false
                }
                FilterChip(tr("Ulubione"), favOnly, icon = { Icon(Icons.Rounded.Star, null, tint = if (favOnly) Palette.Ink else Palette.Warn, modifier = Modifier.size(15.dp)) }) { favOnly = !favOnly }
                presentModes.forEach { m ->
                    FilterChip(m.label, modeFilter == m, icon = { Icon(m.icon, null, tint = if (modeFilter == m) Palette.Ink else m.tint, modifier = Modifier.size(15.dp)) }) {
                        modeFilter = if (modeFilter == m) null else m
                    }
                }
            }
        }

        if (!vm.tracksLoaded) return@Column
        if (visible.isEmpty()) {
            EmptyTracks(hasAny = tracks.any { !it.recording }, onImport = { platform.pickGpx() }, onRecord = { vm.tab = Tab.RECORD })
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.id }) { t ->
                TrackCard(
                    t,
                    units,
                    selected = t.id in selected,
                    modifier = Modifier
                        .animateItem()
                        .combinedClickable(
                            onClick = {
                                if (selecting) selected = if (t.id in selected) selected - t.id else selected + t.id else vm.openTrack = t.id
                            },
                            onLongClick = { selected = if (t.id in selected) selected - t.id else selected + t.id },
                        ),
                )
            }
        }
    }

    if (confirmDelete) {
        DarkDialog(
            tr("Usunąć trasy: {0}?", selected.size),
            onDismiss = { confirmDelete = false },
            icon = Icons.Rounded.Delete,
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { confirmDelete = false }
                PillButton(tr("Usuń"), danger = true) {
                    vm.delete(selected)
                    selected = emptySet()
                    confirmDelete = false
                }
            },
        ) {
            Text(tr("Trasy znikną z bazy razem z punktami i znacznikami. Jeśli chcesz je zachować, najpierw wyeksportuj je do GPX."), color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }

    exportIds?.let { ids ->
        val chosen = tracks.filter { it.id in ids }
        ExportSheet(
            vm,
            trackIds = ids,
            segments = chosen.sumOf { it.segments.size },
            modes = chosen.flatMap { t -> t.segments.map { it.mode } }.distinct().size,
            title = tr("Eksport tras: {0}", ids.size),
            onDismiss = {
                exportIds = null
                selected = emptySet()
            },
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(Pill).background(Palette.PanelHigh).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(tr("Szukaj po nazwie lub notatce"), color = Palette.TextFaint, fontSize = 15.sp)
            BasicTextField(
                value,
                onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Icon(Icons.Rounded.Close, tr("Wyczyść"), tint = Palette.TextDim, modifier = Modifier.size(20.dp).press { onChange("") })
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(Pill)
            .background(if (on) Color.White else Palette.Ink)
            .border(1.dp, if (on) Color.White else Palette.Hairline, Pill)
            .press(label = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (on) Palette.Ink else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TrackCard(t: Track, units: Units, selected: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.PanelHigh)
            .border(2.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(22.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Ink), contentAlignment = Alignment.Center) {
            RoutePreview(t.preview, t.segments.firstOrNull()?.mode?.tint ?: Color.White, Modifier.fillMaxSize().padding(10.dp))
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (t.favorite) Icon(Icons.Rounded.Star, null, tint = Palette.Warn, modifier = Modifier.padding(start = 4.dp).size(15.dp))
            }
            Text(Units.dateTime(t.startTime), color = Palette.TextDim, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(units.fmtDist(t.stats.distance), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, style = TextStyle(fontFeatureSettings = "tnum"))
                Text("  " + Units.duration(t.stats.duration, seconds = false), color = Palette.TextDim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                ModeIcons(t.segments.map { it.mode })
            }
            if (t.segments.size > 1) {
                Spacer(Modifier.height(6.dp))
                SegmentBar(t.segments)
            }
        }
    }
}

/** Miniatura kształtu trasy (bez kafelków mapy) – rzut równoodległościowy. */
@Composable
fun RoutePreview(points: List<LatLon>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }
        val k = cos(Math.toRadians((minLat + maxLat) / 2))
        val w = max((maxLon - minLon) * k, 1e-9)
        val h = max(maxLat - minLat, 1e-9)
        val scale = minOf(size.width / w, size.height / h)
        val ox = (size.width - w * scale) / 2
        val oy = (size.height - h * scale) / 2
        fun p(l: LatLon) = Offset((ox + (l.lon - minLon) * k * scale).toFloat(), (size.height - oy - (l.lat - minLat) * scale).toFloat())
        val path = Path().apply {
            moveTo(p(points[0]).x, p(points[0]).y)
            points.drop(1).forEach { val o = p(it); lineTo(o.x, o.y) }
        }
        drawPath(path, color, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, 3.dp.toPx(), p(points.first()))
        drawCircle(Palette.Record, 3.dp.toPx(), p(points.last()))
    }
}

@Composable
private fun EmptyTracks(hasAny: Boolean, onImport: () -> Unit, onRecord: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (hasAny) Icons.Rounded.Search else Icons.Rounded.Route, null, tint = Palette.TextDim, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            if (hasAny) tr("Nic nie pasuje do filtrów") else tr("Nie masz jeszcze tras"),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        if (!hasAny) {
            Text(
                tr("Nagraj pierwszą przejażdżkę albo zaimportuj pliki GPX z innej aplikacji."),
                color = Palette.TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(tr("Importuj GPX"), primary = false, icon = Icons.Rounded.FileUpload, onClick = onImport)
                PillButton(tr("Nagrywaj"), icon = Icons.Rounded.FiberManualRecord, onClick = onRecord)
            }
        }
    }
}
