package com.blacksingularities.gpxreg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.gpxreg.data.Segment
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.ui.theme.Palette
import com.blacksingularities.gpxreg.ui.theme.press

val TransportMode.icon: ImageVector
    get() = when (this) {
        TransportMode.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
        TransportMode.HIKE -> Icons.Rounded.Hiking
        TransportMode.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
        TransportMode.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
        TransportMode.SCOOTER -> Icons.Rounded.ElectricScooter
        TransportMode.MOTORCYCLE -> Icons.Rounded.TwoWheeler
        TransportMode.CAR -> Icons.Rounded.DirectionsCar
        TransportMode.BUS -> Icons.Rounded.DirectionsBus
        TransportMode.TRAM -> Icons.Rounded.Tram
        TransportMode.TRAIN -> Icons.Rounded.Train
        TransportMode.PLANE -> Icons.Rounded.Flight
        TransportMode.BOAT -> Icons.Rounded.DirectionsBoat
        TransportMode.SKI -> Icons.Rounded.DownhillSkiing
        TransportMode.OTHER -> Icons.Rounded.Explore
    }

val TransportMode.tint: Color get() = Color(color)

/** Siatka wszystkich środków transportu; wybrany – biały blok. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeGrid(selected: TransportMode?, modifier: Modifier = Modifier, onPick: (TransportMode) -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        TransportMode.entries.forEach { m ->
            val on = m == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Color.White else Palette.Ink)
                    .border(1.dp, if (on) Color.White else Palette.Hairline, RoundedCornerShape(18.dp))
                    .press(label = m.label) { onPick(m) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(m.icon, null, tint = if (on) Palette.Ink else m.tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(m.label, color = if (on) Palette.Ink else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Pasek proporcji fragmentów (dystans) w kolorach środków transportu. */
@Composable
fun SegmentBar(segments: List<Segment>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.stats.distance }.takeIf { it > 0 } ?: return
    Row(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEach { s ->
            val w = (s.stats.distance / total).toFloat()
            if (w > 0.005f) Box(Modifier.weight(w).height(4.dp).background(s.mode.tint))
        }
    }
}

/** Ikony środków transportu trasy (bez powtórzeń). */
@Composable
fun ModeIcons(modes: List<TransportMode>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        modes.distinct().take(5).forEach { Icon(it.icon, it.label, tint = it.tint, modifier = Modifier.size(16.dp)) }
        if (modes.distinct().size > 5) {
            Spacer(Modifier.width(2.dp))
            Text("+${modes.distinct().size - 5}", color = Palette.TextDim, fontSize = 11.sp)
        }
    }
}
