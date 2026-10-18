package com.blacksingularities.gpxreg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Czarno-biały interfejs jak w OpenPDF: akcent to biel, czerwień tylko dla nagrywania
 * i usuwania. Kolor mają wyłącznie środki transportu (ścieżka na mapie) i symbole HUD.
 */
object Palette {
    val Ink = Color(0xFF000000)
    val Panel = Color(0xFF000000)
    val PanelHigh = Color(0xFF1C1C1C)
    val PanelHigher = Color(0xFF2A2A2A)
    val Record = Color(0xFFFF3B47)
    val Warn = Color(0xFFFFC14D)
    val Ok = Color(0xFF6CFF8E)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Hairline = Color(0x22FFFFFF)
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Palette.Ink,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Panel,
    onSurface = Color.White,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Panel,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.PanelHigh,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.PanelHigher,
    outline = Color(0x33FFFFFF),
    outlineVariant = Palette.Hairline,
    error = Palette.Record,
)

/** Cyfry o stałej szerokości – liczniki nie „skaczą” przy zmianie wartości. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

val NumberLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", letterSpacing = (-0.3).sp)

@Composable
fun GpxRegTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
