package com.blacksingularities.gpxreg.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val Pill = RoundedCornerShape(50)

/** Dotyk ze sprężystym wciśnięciem i haptyką – jak przyciski OpenPDF. */
fun Modifier.press(enabled: Boolean = true, label: String? = null, haptic: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    val h = LocalHapticFeedback.current
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.38f
        }
        .clickable(source, null, enabled = enabled, onClickLabel = label, role = Role.Button) {
            if (haptic) h.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Przycisk-pigułka: biały (główny), ciemny (drugorzędny) albo czerwony (usuwanie). */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        danger -> Palette.Record
        primary -> Color.White
        else -> Palette.PanelHigher
    }
    val fg = if (primary && !danger) Palette.Ink else Color.White
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(Pill)
            .background(bg)
            .press(enabled, text, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Okrągły przycisk z ikoną; [active] – biały blok z czarną ikoną. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    active: Boolean = false,
    tint: Color? = null,
    background: Color = Palette.PanelHigh,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (active) Color.White else background, tween(160), label = "roundBg")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .press(enabled, label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint ?: if (active) Palette.Ink else Color.White, modifier = Modifier.size(size * 0.48f))
        }
        if (badge != null) {
            Text(
                badge,
                color = Palette.Ink,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(Pill)
                    .background(Palette.Record)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** Okno dialogowe na czarnym tle z zaokrągleniem 28 dp. */
@Composable
fun DarkDialog(
    title: String,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.PanelHigh)
                .padding(22.dp),
        ) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
            }
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
            Spacer(Modifier.size(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = buttons)
        }
    }
}

/** Nagłówek sekcji – zdaniowy, przygaszony. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Palette.TextDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
    )
}

/** Grupa wierszy ustawień na wspólnym tle. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.PanelHigh),
        content = content,
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (value != null) {
            Text(value, color = Palette.TextDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).widthIn(max = 160.dp))
        }
        trailing?.invoke()
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, icon: ImageVector? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, icon, enabled = enabled, onClick = { onChange(!checked) }, trailing = {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Ink,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Palette.TextDim,
                uncheckedTrackColor = Palette.Ink,
                uncheckedBorderColor = Palette.TextFaint,
            ),
        )
    })
}

/** Wybór jednej z kilku opcji – pigułki w rzędzie, wybrana biała. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(
        modifier
            .clip(Pill)
            .background(Palette.Ink)
            .border(1.dp, Palette.Hairline, Pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) Color.White else Color.Transparent, tween(160), label = "seg")
            Text(
                label,
                color = if (on) Palette.Ink else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(Pill)
                    .background(bg)
                    .press(label = label) { onSelect(value) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Wartość z podpisem – w siatkach statystyk. */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, big: Boolean = false) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Color.White, style = if (big) NumberLarge.copy(fontSize = 28.sp) else NumberLarge, maxLines = 1)
            if (unit != null) Text(" $unit", color = Palette.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1)
    }
}

/** Kolorowa kropka środka transportu. */
@Composable
fun ModeDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
