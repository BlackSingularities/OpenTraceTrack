package com.blacksingularities.gpxreg.ui

import com.blacksingularities.gpxreg.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.gpxreg.LocalPlatform
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.i18n.tr
import com.blacksingularities.gpxreg.record.Recorder
import com.blacksingularities.gpxreg.update.ReleaseInfo
import com.blacksingularities.gpxreg.update.UpdateManager
import com.blacksingularities.gpxreg.update.UpdateState
import com.blacksingularities.gpxreg.ui.theme.DarkDialog
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.PillButton
import com.blacksingularities.gpxreg.ui.theme.press
import kotlin.math.roundToInt

/** Baner nowej wersji (lista tras, ustawienia) – dotknięcie otwiera szczegóły aktualizacji. */
@Composable
fun UpdateBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
    val u = vm.update
    AnimatedVisibility(vm.updatePending && !vm.bannerHidden, modifier = modifier) {
        val info = when (u) {
            is UpdateState.Available -> u.info
            is UpdateState.Downloading -> u.info
            is UpdateState.Ready -> u.info
            is UpdateState.Installing -> u.info
            else -> null
        } ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .press(label = tr(R.string.szczegoly_aktualizacji)) { vm.updateSheet = true }
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Palette.Ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (u) {
                            is UpdateState.Downloading -> tr(R.string.pobieranie_wersji_x_x, info.version, (u.progress * 100).roundToInt())
                            is UpdateState.Ready -> tr(R.string.wersja_x_gotowa_do_instalacji, info.version)
                            is UpdateState.Installing -> tr(R.string.instalowanie_wersji_x, info.version)
                            else -> tr(R.string.dostepna_wersja_x, info.version)
                        },
                        color = Palette.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr(R.string.dotknij_aby_zaktualizowac),
                        color = Palette.Ink.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (u is UpdateState.Available) {
                    Icon(
                        Icons.Rounded.Close,
                        tr(R.string.pozniej),
                        tint = Palette.Ink.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp).clip(CircleShape).press { vm.bannerHidden = true }.padding(8.dp),
                    )
                }
            }
            if (u is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { u.progress },
                    color = Palette.Ink,
                    trackColor = Palette.Ink.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp),
                )
            }
        }
    }
}

/**
 * Szczegóły aktualizacji: z której na którą wersję, rozmiar, postęp pobierania
 * i jasne informacje, co się stanie po instalacji.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(vm: AppViewModel) {
    val context = LocalContext.current
    val u = vm.update
    ModalBottomSheet(
        onDismissRequest = { vm.updateSheet = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.PanelHigh,
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            val info: ReleaseInfo? = when (u) {
                is UpdateState.Available -> u.info
                is UpdateState.Downloading -> u.info
                is UpdateState.Ready -> u.info
                is UpdateState.Installing -> u.info
                is UpdateState.Failed -> u.info
                else -> null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(
                        when (u) {
                            is UpdateState.UpToDate -> Icons.Rounded.CheckCircle
                            is UpdateState.Failed -> Icons.Rounded.ErrorOutline
                            else -> Icons.Rounded.NewReleases
                        },
                        null,
                        tint = Palette.Ink,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (u) {
                            UpdateState.Checking -> tr(R.string.sprawdzanie_aktualizacji)
                            UpdateState.UpToDate -> tr(R.string.masz_najnowsza_wersje)
                            is UpdateState.Failed -> tr(R.string.aktualizacja_nie_powiodla_sie)
                            else -> if (info != null) tr(R.string.opentracetrack_x, info.version) else tr(R.string.aktualizacje)
                        },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (info != null) {
                            tr(R.string.zainstalowana_x, UpdateManager.currentVersion) + (if (info.sizeBytes > 0) " · " + Units.bytes(info.sizeBytes) else "")
                        } else {
                            tr(R.string.zainstalowana_x, UpdateManager.currentVersion)
                        },
                        color = Palette.TextDim,
                        fontSize = 13.sp,
                    )
                }
            }

            if (u is UpdateState.Failed) {
                Text(u.message, color = Palette.Record, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            }

            Spacer(Modifier.height(16.dp))
            AnimatedContent(u, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "updateActions") { s ->
                Column {
                    when (s) {
                        is UpdateState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { s.progress },
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr(R.string.pobrano_x_z_x, Units.bytes(s.bytes), Units.bytes(s.info.sizeBytes)),
                                    color = Palette.TextDim,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                PillButton(tr(R.string.anuluj), primary = false) { vm.cancelDownload() }
                            }
                        }
                        is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(tr(R.string.instalowanie_aplikacja_uruchomi_sie_ponownie), color = Color.White, fontSize = 14.sp)
                        }
                        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(tr(R.string.sprawdzanie_aktualizacji), color = Color.White, fontSize = 14.sp)
                        }
                        UpdateState.UpToDate, UpdateState.Idle -> PillButton(tr(R.string.sprawdz_ponownie), Modifier.fillMaxWidth(), primary = false) { vm.checkUpdate() }
                        else -> {
                            val recording = Recorder.state.value.active
                            Text(
                                when {
                                    recording -> tr(R.string.trwa_nagrywanie_plik_mozesz_pobrac_teraz)
                                    UpdateManager.canInstallSilently(context) -> tr(R.string.aktualizacja_pobierze_sie_i_zainstaluje_bez)
                                    UpdateManager.canInstall(context) -> tr(R.string.android_poprosi_o_potwierdzenie_instalacji_t)
                                    else -> tr(R.string.za_pierwszym_razem_android_zapyta_o)
                                },
                                color = Palette.TextDim,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            PillButton(
                                when (s) {
                                    is UpdateState.Ready -> tr(R.string.zainstaluj)
                                    is UpdateState.Failed -> tr(R.string.sprobuj_ponownie)
                                    else -> tr(R.string.aktualizuj_teraz)
                                },
                                Modifier.fillMaxWidth(),
                                icon = Icons.Rounded.SystemUpdate,
                            ) { vm.downloadAndInstall() }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton(tr(R.string.pozniej), Modifier.weight(1f), primary = false) { vm.later() }
                                if (s is UpdateState.Available) {
                                    PillButton(tr(R.string.pomin_te_wersje), Modifier.weight(1f), primary = false) { vm.skipVersion() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Komunikat – raz, po zainstalowaniu aktualizacji (bez listy zmian). */
@Composable
fun WhatsNewDialog(version: String, @Suppress("UNUSED_PARAMETER") notes: String, onDismiss: () -> Unit) {
    DarkDialog(
        tr(R.string.zaktualizowano_do_x, version),
        onDismiss = onDismiss,
        icon = Icons.Rounded.NewReleases,
        buttons = { PillButton(tr(R.string.super), onClick = onDismiss) },
    ) {
        Text(tr(R.string.aplikacja_zostala_zaktualizowana), color = Palette.TextDim, fontSize = 14.sp)
    }
}
