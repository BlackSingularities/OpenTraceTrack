package com.blacksingularities.gpxreg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.gpxreg.LocalPlatform
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.io.ExportFormat
import com.blacksingularities.gpxreg.io.ExportSplit
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.PillButton
import com.blacksingularities.gpxreg.ui.theme.Segmented

/**
 * Eksport: format, podział na pliki i cel (udostępnienie albo zapis w telefonie).
 * [segments] – liczba fragmentów (podział ma sens od 2); [segmentIds] – tylko wybrane fragmenty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    vm: AppViewModel,
    trackIds: List<Long>,
    segments: Int,
    modes: Int,
    segmentIds: Set<Long>? = null,
    title: String = tr("Eksport"),
    onDismiss: () -> Unit,
) {
    val platform = LocalPlatform.current
    var format by remember { mutableStateOf(ExportFormat.GPX) }
    var split by remember { mutableStateOf(ExportSplit.WHOLE) }
    val canSplit = segmentIds == null && segments > 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.PanelHigh,
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(tr("Format"), color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Segmented(ExportFormat.entries.map { it to it.label }, format, Modifier.fillMaxWidth()) { format = it }
            Text(
                when (format) {
                    ExportFormat.GPX -> tr("Standard GPX 1.1 – Strava, Garmin, Komoot, OsmAnd, mapy.cz. Prędkość i kurs w rozszerzeniu Garmin, środek transportu w polu „type”.")
                    ExportFormat.GEOJSON -> tr("Dla QGIS, geojson.io i aplikacji webowych. Czasy i prędkości jako tablice we właściwościach.")
                    ExportFormat.KML -> tr("Dla Google Earth – z czasem każdego punktu, trasę można odtworzyć na osi czasu.")
                    ExportFormat.CSV -> tr("Tabela punktów do Excela lub Arkuszy: czas, współrzędne, wysokość, prędkość, dokładność.")
                },
                color = Palette.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
            if (canSplit || trackIds.size > 1) {
                Spacer(Modifier.height(18.dp))
                Text(tr("Pliki"), color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val opts = buildList {
                    add(ExportSplit.WHOLE to if (trackIds.size > 1) tr("Plik na trasę") else tr("Jeden plik"))
                    add(ExportSplit.PER_SEGMENT to tr("Na fragment"))
                    if (modes > 1 || trackIds.size > 1) add(ExportSplit.PER_MODE to tr("Na środek"))
                }
                Segmented(opts, split, Modifier.fillMaxWidth()) { split = it }
                Text(
                    when (split) {
                        ExportSplit.WHOLE -> tr("Fragmenty jako osobne ścieżki w jednym pliku.")
                        ExportSplit.PER_SEGMENT -> tr("Każda przesiadka w osobnym pliku, np. rower – pociąg – pieszo.")
                        ExportSplit.PER_MODE -> tr("Wszystkie odcinki jednym środkiem transportu razem, np. cały rower w jednym pliku.")
                    },
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            if (vm.exporting) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(end = 12.dp))
                    Text(tr("Przygotowuję pliki…"), color = Color.White)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(tr("Udostępnij"), Modifier.weight(1f), primary = false, icon = Icons.Rounded.Share) {
                        vm.prepareExport(trackIds, format, split, segmentIds) { job ->
                            platform.startActivity(vm.port.shareIntent(job.files, format.mime))
                            onDismiss()
                        }
                    }
                    PillButton(tr("Zapisz"), Modifier.weight(1f), icon = if (split == ExportSplit.WHOLE && trackIds.size == 1) Icons.Rounded.Save else Icons.Rounded.FolderOpen) {
                        vm.prepareExport(trackIds, format, split, segmentIds) { job ->
                            if (job.files.size == 1) {
                                platform.createDocument(job.files.single().name, format.mime) { uri -> vm.saveExport(job, uri) }
                            } else {
                                platform.openFolder { tree -> vm.saveExportToFolder(job, tree) }
                            }
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}
