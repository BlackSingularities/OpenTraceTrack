package com.blacksingularities.gpxreg.ui

import com.blacksingularities.gpxreg.R
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
    title: String = tr(R.string.eksport),
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
            Text(tr(R.string.format), color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Segmented(ExportFormat.entries.map { it to it.label }, format, Modifier.fillMaxWidth()) { format = it }
            Text(
                when (format) {
                    ExportFormat.GPX -> tr(R.string.standard_gpx_1_1_strava_garmin)
                    ExportFormat.GEOJSON -> tr(R.string.dla_qgis_geojson_io_i_aplikacji)
                    ExportFormat.KML -> tr(R.string.dla_google_earth_z_czasem_kazdego)
                    ExportFormat.CSV -> tr(R.string.tabela_punktow_do_excela_lub_arkuszy)
                },
                color = Palette.TextDim,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
            if (canSplit || trackIds.size > 1) {
                Spacer(Modifier.height(18.dp))
                Text(tr(R.string.pliki), color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val opts = buildList {
                    add(ExportSplit.WHOLE to if (trackIds.size > 1) tr(R.string.plik_na_trase) else tr(R.string.jeden_plik))
                    add(ExportSplit.PER_SEGMENT to tr(R.string.na_fragment))
                    if (modes > 1 || trackIds.size > 1) add(ExportSplit.PER_MODE to tr(R.string.na_srodek))
                }
                Segmented(opts, split, Modifier.fillMaxWidth()) { split = it }
                Text(
                    when (split) {
                        ExportSplit.WHOLE -> tr(R.string.fragmenty_jako_osobne_sciezki_w_jednym)
                        ExportSplit.PER_SEGMENT -> tr(R.string.kazda_przesiadka_w_osobnym_pliku_np)
                        ExportSplit.PER_MODE -> tr(R.string.wszystkie_odcinki_jednym_srodkiem_transportu)
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
                    Text(tr(R.string.przygotowuje_pliki), color = Color.White)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(tr(R.string.udostepnij), Modifier.weight(1f), primary = false, icon = Icons.Rounded.Share) {
                        vm.prepareExport(trackIds, format, split, segmentIds) { job ->
                            platform.startActivity(vm.port.shareIntent(job.files, format.mime))
                            onDismiss()
                        }
                    }
                    PillButton(tr(R.string.zapisz), Modifier.weight(1f), icon = if (split == ExportSplit.WHOLE && trackIds.size == 1) Icons.Rounded.Save else Icons.Rounded.FolderOpen) {
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
