package com.blacksingularities.gpxreg.ui

import com.blacksingularities.gpxreg.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.gpxreg.data.LatLon
import com.blacksingularities.gpxreg.data.Segment
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.data.Waypoint
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.ui.map.MapLine
import com.blacksingularities.gpxreg.ui.map.TrackMap
import com.blacksingularities.gpxreg.ui.theme.DarkDialog
import com.blacksingularities.gpxreg.ui.theme.Metric
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.PillButton
import com.blacksingularities.gpxreg.ui.theme.RoundIcon
import com.blacksingularities.gpxreg.ui.theme.Segmented
import com.blacksingularities.gpxreg.ui.theme.press
import kotlin.math.roundToInt

private enum class ChartKind { SPEED, ELEVATION }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackDetailScreen(vm: AppViewModel, trackId: Long) {
    val settings by vm.settings.collectAsState()
    val units = remember(settings.units) { Units(settings.units) }
    val changes by vm.db.changes.collectAsState()
    var detail by remember { mutableStateOf<AppViewModel.TrackDetail?>(null) }
    LaunchedEffect(trackId, changes) {
        val d = vm.loadDetail(trackId)
        if (d == null) vm.openTrack = null else detail = d
    }
    BackHandler { vm.openTrack = null }

    var chart by remember { mutableStateOf(ChartKind.SPEED) }
    var cursor by remember { mutableStateOf<Int?>(null) }
    var bigMap by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var segmentSheet by remember { mutableStateOf<Segment?>(null) }
    var segmentExport by remember { mutableStateOf<Segment?>(null) }
    var waypointEdit by remember { mutableStateOf<Waypoint?>(null) }
    var splitPreview by remember { mutableStateOf<LatLon?>(null) }

    Box(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val d = detail ?: return@Box
        val t = d.track
        val modes = remember(t.segments) { t.segments.associate { it.id to it.mode } }
        val lines = remember(d.points, modes) {
            val out = ArrayList<MapLine>()
            var cur = ArrayList<LatLon>()
            var seg = -1L
            for (p in d.points) {
                if (p.segmentId != seg || p.gap) {
                    if (cur.size > 1) out += MapLine(modes[seg]?.color ?: TransportMode.OTHER.color, cur)
                    val bridge = if (p.segmentId != seg && !p.gap) cur.lastOrNull() else null
                    cur = ArrayList()
                    bridge?.let { cur += it }
                    seg = p.segmentId
                }
                cur += LatLon(p.lat, p.lon)
            }
            if (cur.size > 1) out += MapLine(modes[seg]?.color ?: TransportMode.OTHER.color, cur)
            out
        }
        val speedProfile = remember(d.points, units) { buildProfile(d.points, modes) { p -> p.speed?.let { units.speed(it) } } }
        val eleProfile = remember(d.points, units) { buildProfile(d.points, modes) { p -> p.ele?.let { units.alt(it) } } }
        val profile = if (chart == ChartKind.SPEED || eleProfile.size < 2) speedProfile else eleProfile
        val cursorPoint = (cursor?.let { profile.getOrNull(it)?.index }?.let { d.points.getOrNull(it) }?.let { LatLon(it.lat, it.lon) }) ?: splitPreview

        Column(Modifier.fillMaxSize()) {
            // Pasek tytułu.
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, tr(R.string.wstecz), size = 40.dp) { vm.openTrack = null }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp).press(label = tr(R.string.zmien_nazwe)) { renaming = true }) {
                    Text(t.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Units.dateTime(t.startTime) + " – " + Units.time(t.endTime), color = Palette.TextDim, fontSize = 12.sp, maxLines = 1)
                }
                RoundIcon(if (t.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr(R.string.ulubiona), size = 40.dp, tint = if (t.favorite) Palette.Warn else null) { vm.toggleFavorite(t) }
                Spacer(Modifier.width(6.dp))
                RoundIcon(Icons.Rounded.FileDownload, tr(R.string.eksportuj), size = 40.dp, active = true) { exporting = true }
            }

            if (bigMap) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(24.dp))) {
                    TrackMap(settings.mapStyle.url, lines, Modifier.fillMaxSize(), fitKey = "big$trackId", cursor = cursorPoint, waypoints = d.waypoints.map { LatLon(it.lat, it.lon) }, showEnds = true)
                    RoundIcon(Icons.Rounded.FullscreenExit, tr(R.string.pomniejsz_mape), Modifier.align(Alignment.BottomEnd).padding(10.dp), size = 42.dp, background = Palette.Ink) { bigMap = false }
                }
                return@Column
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(24.dp))) {
                    TrackMap(settings.mapStyle.url, lines, Modifier.fillMaxSize(), fitKey = trackId, cursor = cursorPoint, waypoints = d.waypoints.map { LatLon(it.lat, it.lon) }, showEnds = true)
                    RoundIcon(Icons.Rounded.Fullscreen, tr(R.string.powieksz_mape), Modifier.align(Alignment.BottomEnd).padding(10.dp), size = 42.dp, background = Palette.Ink) { bigMap = true }
                }
                TrackTools(vm, d, settings.mapStyle.url)

                // Podsumowanie.
                val s = t.stats
                Spacer(Modifier.height(16.dp))
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), maxItemsInEachRow = 3, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Metric(tr(R.string.dystans), units.fmtDist(s.distance), Modifier.weight(1f), big = true)
                    Metric(tr(R.string.czas), Units.duration(s.duration), Modifier.weight(1f), big = true)
                    Metric(tr(R.string.w_ruchu), Units.duration(s.movingTime), Modifier.weight(1f), big = true)
                    Metric(tr(R.string.srednia_w_ruchu), units.num(units.speed(s.avgMovingSpeed), 1), Modifier.weight(1f), unit = units.speedUnit)
                    Metric(tr(R.string.maksymalna), units.num(units.speed(s.maxSpeed), 1), Modifier.weight(1f), unit = units.speedUnit)
                    Metric(tr(R.string.tempo), units.fmtPace(s.avgMovingSpeed), Modifier.weight(1f))
                    Metric(tr(R.string.w_gore), units.alt(s.ascent).roundToInt().toString(), Modifier.weight(1f), unit = units.altUnit)
                    Metric(tr(R.string.w_dol), units.alt(s.descent).roundToInt().toString(), Modifier.weight(1f), unit = units.altUnit)
                    Metric(tr(R.string.wysokosc), if (s.minEle != null && s.maxEle != null) "${units.alt(s.minEle).roundToInt()}–${units.alt(s.maxEle).roundToInt()}" else "–", Modifier.weight(1f), unit = units.altUnit)
                }

                // Wykres.
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.PanelHigh).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(R.string.profil), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Segmented(
                            listOf(ChartKind.SPEED to tr(R.string.predkosc), ChartKind.ELEVATION to tr(R.string.wysokosc)),
                            chart,
                            Modifier.width(210.dp),
                        ) {
                            chart = it
                            cursor = null
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (profile.size < 2) {
                        Text(tr(R.string.brak_danych_do_wykresu), color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    } else {
                        ProfileChart(
                            profile,
                            formatY = { v -> if (chart == ChartKind.SPEED) units.num(v, 0) + " " + units.speedUnit else v.roundToInt().toString() + " " + units.altUnit },
                            formatX = { m -> units.fmtDist(m) },
                            cursor = cursor,
                            onScrub = { cursor = it },
                        )
                        Text(tr(R.string.przesun_palcem_po_wykresie_punkt_pokaze), color = Palette.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                // Fragmenty.
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(tr(R.string.fragmenty), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(tr(R.string.dotknij_aby_edytowac), color = Palette.TextFaint, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.PanelHigh)) {
                    t.segments.forEachIndexed { i, seg ->
                        SegmentRow(seg, i, t.segments.size, units) { segmentSheet = seg }
                        if (i < t.segments.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(Palette.Hairline))
                    }
                }
                if (t.segments.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    SegmentBar(t.segments, Modifier.padding(horizontal = 6.dp))
                }

                // Znaczniki.
                if (d.waypoints.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(tr(R.string.znaczniki), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.PanelHigh)) {
                        d.waypoints.forEach { w ->
                            Row(
                                Modifier.fillMaxWidth().press(label = w.name) { waypointEdit = w }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = Palette.Record, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(w.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        Units.timeSec(w.time) + (w.ele?.let { " · " + units.fmtAlt(it) } ?: "") + (if (w.note.isNotBlank()) " · " + w.note else ""),
                                        color = Palette.TextDim,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(Icons.Rounded.Edit, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Notatki.
                Spacer(Modifier.height(20.dp))
                NotesField(t.notes) { vm.setNotes(t.id, it) }

                Spacer(Modifier.height(18.dp))
                PillButton(tr(R.string.usun_trase), Modifier.fillMaxWidth(), danger = true, icon = Icons.Rounded.Delete) { deleting = true }
                Text(
                    tr(R.string.punkty_x_id_x, s.points, t.uuid.take(8)),
                    color = Palette.TextFaint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        if (renaming) {
            TextDialog(tr(R.string.nazwa_trasy), t.name, onDismiss = { renaming = false }) {
                vm.rename(t.id, it)
                renaming = false
            }
        }
        if (deleting) {
            DarkDialog(
                tr(R.string.usunac_trase),
                onDismiss = { deleting = false },
                icon = Icons.Rounded.Delete,
                buttons = {
                    PillButton(tr(R.string.anuluj), primary = false) { deleting = false }
                    PillButton(tr(R.string.usun), danger = true) {
                        deleting = false
                        vm.delete(listOf(t.id))
                        vm.openTrack = null
                    }
                },
            ) {
                Text(tr(R.string.x_zniknie_z_bazy_razem_z, t.name), color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        if (exporting) {
            ExportSheet(vm, listOf(t.id), t.segments.size, t.segments.map { it.mode }.distinct().size, onDismiss = { exporting = false })
        }
        segmentExport?.let { seg ->
            ExportSheet(vm, listOf(t.id), 1, 1, segmentIds = setOf(seg.id), title = tr(R.string.eksport_fragmentu_x, seg.mode.label), onDismiss = { segmentExport = null })
        }
        segmentSheet?.let { seg ->
            SegmentSheet(
                vm,
                seg,
                isLast = seg.index == t.segments.lastIndex,
                count = t.segments.size,
                points = d.points.filter { it.segmentId == seg.id }.map { it.time to LatLon(it.lat, it.lon) },
                units = units,
                onPreview = { splitPreview = it },
                onExport = {
                    segmentSheet = null
                    segmentExport = seg
                },
                onDismiss = {
                    segmentSheet = null
                    splitPreview = null
                },
            )
        }
        waypointEdit?.let { w ->
            WaypointDialog(w, onDismiss = { waypointEdit = null }, onDelete = {
                vm.deleteWaypoint(w.id)
                waypointEdit = null
            }) { name, note ->
                vm.renameWaypoint(w.id, name, note)
                waypointEdit = null
            }
        }
    }
}

@Composable
private fun SegmentRow(seg: Segment, i: Int, count: Int, units: Units, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().press(label = seg.mode.label, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(seg.mode.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(seg.mode.icon, null, tint = seg.mode.tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (count > 1) "${i + 1}. ${seg.mode.label}" else seg.mode.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                Units.time(seg.startTime) + "–" + Units.time(seg.endTime) + " · " + Units.duration(seg.stats.duration, seconds = false) + " · " + tr(R.string.sr_x, units.fmtSpeed(seg.stats.avgMovingSpeed)),
                color = Palette.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(units.fmtDist(seg.stats.distance), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

/** Edycja fragmentu: środek transportu, podział w wybranym miejscu, łączenie, eksport, usunięcie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentSheet(
    vm: AppViewModel,
    seg: Segment,
    isLast: Boolean,
    count: Int,
    points: List<Pair<Long, LatLon>>,
    units: Units,
    onPreview: (LatLon?) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var splitting by remember { mutableStateOf(false) }
    var splitFrac by remember { mutableStateOf(0.5f) }
    var splitMode by remember { mutableStateOf(seg.mode) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.PanelHigh,
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(tr(R.string.fragment_x_z_x, seg.index + 1, count), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                units.fmtDist(seg.stats.distance) + " · " + Units.time(seg.startTime) + "–" + Units.time(seg.endTime),
                color = Palette.TextDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            if (!splitting) {
                Text(tr(R.string.srodek_transportu), color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ModeGrid(seg.mode) { m ->
                    vm.setSegmentMode(seg.id, m)
                    onDismiss()
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr(R.string.podziel), Modifier.weight(1f), primary = false, icon = Icons.Rounded.ContentCut, enabled = points.size > 3) { splitting = true }
                    PillButton(tr(R.string.polacz_z_nastepnym), Modifier.weight(1f), primary = false, icon = Icons.AutoMirrored.Rounded.CallMerge, enabled = !isLast) {
                        vm.mergeWithNext(seg.id)
                        onDismiss()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr(R.string.eksportuj_fragment), Modifier.weight(1f), primary = false, icon = Icons.Rounded.FileDownload, onClick = onExport)
                    PillButton(tr(R.string.usun), Modifier.weight(1f), danger = true, icon = Icons.Rounded.Delete, enabled = count > 1) {
                        vm.deleteSegment(seg.id)
                        onDismiss()
                    }
                }
            } else {
                val idx = ((points.size - 1) * splitFrac).roundToInt().coerceIn(1, points.size - 2)
                val at = points[idx]
                LaunchedEffect(idx) { onPreview(at.second) }
                Text(tr(R.string.podzial_o_x, Units.timeSec(at.first)), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(tr(R.string.przesun_suwak_miejsce_podzialu_widac_na), color = Palette.TextDim, fontSize = 12.sp)
                Slider(
                    splitFrac,
                    { splitFrac = it },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = seg.mode.tint, inactiveTrackColor = splitMode.tint),
                )
                Text(tr(R.string.druga_czesc_to), color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ModeGrid(splitMode) { splitMode = it }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr(R.string.anuluj), Modifier.weight(1f), primary = false) {
                        splitting = false
                        onPreview(null)
                    }
                    PillButton(tr(R.string.podziel_tutaj), Modifier.weight(1f), icon = Icons.Rounded.ContentCut) {
                        vm.splitSegment(seg.id, at.first, splitMode)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesField(initial: String, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(tr(R.string.notatki)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        shape = RoundedCornerShape(18.dp),
        colors = fieldColors(),
        trailingIcon = if (text != initial) {
            { Text(tr(R.string.zapisz), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp).press { onSave(text) }) }
        } else {
            null
        },
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Palette.Hairline,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Palette.TextDim,
    cursorColor = Color.White,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
)

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    DarkDialog(
        title,
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr(R.string.anuluj), primary = false, onClick = onDismiss)
            PillButton(tr(R.string.zapisz)) { onSave(text) }
        },
    ) {
        OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
    }
}

@Composable
private fun WaypointDialog(w: Waypoint, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(w.name) }
    var note by remember { mutableStateOf(w.note) }
    DarkDialog(
        tr(R.string.znacznik),
        onDismiss = onDismiss,
        icon = Icons.Rounded.Place,
        buttons = {
            PillButton(tr(R.string.usun), danger = true, onClick = onDelete)
            Spacer(Modifier.weight(1f))
            PillButton(tr(R.string.zapisz)) { onSave(name, note) }
        },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text(tr(R.string.nazwa)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(note, { note = it }, label = { Text(tr(R.string.opis)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(16.dp), colors = fieldColors())
        Text(
            "%.6f, %.6f".format(java.util.Locale.US, w.lat, w.lon),
            color = Palette.TextFaint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** „Jedź tą trasą” i pobranie mapy okolicy trasy do pracy bez internetu. */
@Composable
private fun TrackTools(vm: AppViewModel, d: AppViewModel.TrackDetail, styleUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val guide by com.blacksingularities.gpxreg.record.RouteGuide.state.collectAsState()
    val offline by com.blacksingularities.gpxreg.ui.map.OfflineMaps.state.collectAsState()
    val following = guide?.trackId == d.track.id
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.blacksingularities.gpxreg.ui.theme.PillButton(
            if (following) tr(R.string.guide_stop) else tr(R.string.guide_follow),
            primary = !following,
            modifier = Modifier.weight(1f),
        ) {
            if (following) vm.stopFollowing() else vm.followRoute(d.track.id)
        }
        com.blacksingularities.gpxreg.ui.theme.PillButton(
            tr(R.string.offline_download),
            primary = false,
            modifier = Modifier.weight(1f),
            enabled = offline !is com.blacksingularities.gpxreg.ui.map.OfflineState.Downloading,
        ) {
            com.blacksingularities.gpxreg.ui.map.OfflineMaps.download(context, d.track.name, styleUrl, d.points.map { LatLon(it.lat, it.lon) })
        }
    }
    when (val o = offline) {
        is com.blacksingularities.gpxreg.ui.map.OfflineState.Downloading -> Text(
            tr(R.string.offline_progress, (o.progress * 100).roundToInt(), android.text.format.Formatter.formatShortFileSize(context, o.bytes)),
            color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 6.dp),
        )
        is com.blacksingularities.gpxreg.ui.map.OfflineState.Done -> Text(
            tr(R.string.offline_done, android.text.format.Formatter.formatShortFileSize(context, o.bytes)),
            color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 6.dp),
        )
        is com.blacksingularities.gpxreg.ui.map.OfflineState.Failed -> Text(
            tr(R.string.offline_failed),
            color = Palette.Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 6.dp),
        )
        else -> Unit
    }
}
