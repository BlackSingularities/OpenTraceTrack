package com.blacksingularities.gpxreg.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.blacksingularities.gpxreg.data.HudStyle
import com.blacksingularities.gpxreg.data.Units
import com.blacksingularities.gpxreg.record.Attitude
import com.blacksingularities.gpxreg.record.GnssInfo
import com.blacksingularities.gpxreg.record.TelemetryState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Wszystko, czego potrzebuje HUD w jednej klatce. */
data class HudInput(
    val tel: TelemetryState,
    val att: Attitude,
    val gnss: GnssInfo,
    val units: Units,
    val style: HudStyle,
    val color: Color,
    val glow: Boolean,
    val horizon: Boolean,
    /** Prędkość maksymalna nagrania (m/s) – znacznik na taśmie. */
    val maxSpeed: Double,
    /** Próg ostrzeżenia (m/s, 0 = brak). */
    val speedAlert: Double,
    val clock: Long,
)

private enum class Align { LEFT, CENTER, RIGHT }

/**
 * Wyświetlacz przezierny w stylu lotniczym: symbole rysowane cienką linią z poświatą, bez teł –
 * czytelny także jako odbicie na szybie (tryb lustrzany ustawia rodzic przez graphicsLayer).
 */
@Composable
fun Hud(input: HudInput, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer(cacheSize = 64)
    val ctx = remember { HudPainter(tm) }
    Canvas(modifier) {
        ctx.input = input
        with(ctx) {
            if (input.style == HudStyle.SIMPLE) drawSimple() else drawAviation()
        }
    }
}

private class HudPainter(val tm: TextMeasurer) {
    lateinit var input: HudInput
    private val c get() = input.color

    // ----------------------------------------------------------------- pomocnicze

    private fun DrawScope.line(a: Offset, b: Offset, w: Float = 2.dp(), color: Color = c, effect: PathEffect? = null) {
        if (input.glow) drawLine(color.copy(alpha = 0.16f), a, b, w * 3.2f, StrokeCap.Round, effect)
        drawLine(color, a, b, w, StrokeCap.Round, effect)
    }

    private fun DrawScope.path(p: Path, w: Float = 2.dp(), color: Color = c, fill: Color? = null) {
        if (fill != null) drawPath(p, fill)
        if (input.glow) drawPath(p, color.copy(alpha = 0.16f), style = Stroke(w * 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun DrawScope.text(
        s: String,
        x: Float,
        y: Float,
        size: TextUnit,
        align: Align = Align.CENTER,
        weight: FontWeight = FontWeight.Bold,
        color: Color = c,
        vCenter: Boolean = true,
    ) {
        val style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFeatureSettings = "tnum",
            letterSpacing = 0.sp,
            shadow = if (input.glow) Shadow(color.copy(alpha = 0.55f), Offset.Zero, 10f) else null,
        )
        val r = tm.measure(s, style)
        val dx = when (align) {
            Align.LEFT -> 0f
            Align.CENTER -> -r.size.width / 2f
            Align.RIGHT -> -r.size.width.toFloat()
        }
        val dy = if (vCenter) -r.size.height / 2f else 0f
        drawText(r, topLeft = Offset(x + dx, y + dy))
    }

    private fun DrawScope.textWidth(s: String, size: TextUnit, weight: FontWeight = FontWeight.Bold): Float =
        tm.measure(s, TextStyle(fontSize = size, fontWeight = weight, fontFeatureSettings = "tnum")).size.width.toFloat()

    private fun Float.dp(): Float = this * px
    private fun Int.dp(): Float = this * px
    private var px = 1f

    /** Kurs do wyświetlenia: z GPS w ruchu, inaczej z kompasu. */
    private fun heading(): Pair<Double?, String> {
        val t = input.tel
        t.course?.let { return it to "TRK" }
        val h = input.att.heading
        if (!h.isNaN() && input.att.available) return h.toDouble() to "MAG"
        return null to "HDG"
    }

    // ----------------------------------------------------------------- styl lotniczy

    fun DrawScope.drawAviation() {
        px = density
        val w = size.width
        val h = size.height
        val m = 10.dp()
        val compact = h < 360.dp()
        val headingH = if (compact) 38.dp() else 48.dp()
        val tapeW = min(78.dp(), w * 0.2f)
        val top = m + headingH + 14.dp()
        val bottom = h - (if (compact) 40.dp() else 52.dp())

        headingTape(Rect(w * 0.18f, m, w * 0.82f, m + headingH))
        speedTape(Rect(m, top, m + tapeW, bottom))
        altTape(Rect(w - m - tapeW, top, w - m, bottom))
        val center = Rect(m + tapeW + 14.dp(), top, w - m - tapeW - 14.dp(), bottom)
        if (input.horizon && input.att.available) horizon(center) else plainReticle(center)
        aircraftSymbol(center.center, min(center.width, center.height))
        bottomLine(Rect(m, bottom + 22.dp(), w - m, h - 2.dp()))
    }

    private fun DrawScope.headingTape(r: Rect) {
        val (hdg, src) = heading()
        val cx = r.center.x
        val pxPerDeg = r.width / 70f
        val baseY = r.bottom
        // linia bazowa
        line(Offset(r.left, baseY), Offset(r.right, baseY), 1.5f.dp())
        if (hdg != null) {
            clipRect(r.left, r.top, r.right, r.bottom + 2.dp()) {
                val start = floor((hdg - 40) / 5).toInt() * 5
                var d = start
                while (d <= hdg + 40) {
                    val x = cx + ((d - hdg) * pxPerDeg).toFloat()
                    val major = d % 10 == 0
                    line(Offset(x, baseY), Offset(x, baseY - if (major) 10.dp() else 5.dp()), 1.5f.dp())
                    if (major) {
                        val norm = ((d % 360) + 360) % 360
                        val label = when (norm) {
                            0 -> "N"
                            90 -> "E"
                            180 -> "S"
                            270 -> "W"
                            else -> "%02d".format(norm / 10)
                        }
                        if (abs(x - cx) > 66.dp()) text(label, x, baseY - 21.dp(), 13.sp, weight = if (norm % 90 == 0) FontWeight.Black else FontWeight.Bold)
                    }
                    d += 5
                }
            }
        }
        // okienko wartości
        val label = hdg?.let { "%03d".format(((it.roundToInt() % 360) + 360) % 360) } ?: "– – –"
        val bw = 54.dp()
        val bh = 24.dp()
        val box = Path().apply {
            moveTo(cx - bw / 2, r.top + 2.dp())
            lineTo(cx + bw / 2, r.top + 2.dp())
            lineTo(cx + bw / 2, r.top + 2.dp() + bh)
            lineTo(cx + 6.dp(), r.top + 2.dp() + bh)
            lineTo(cx, r.top + 2.dp() + bh + 6.dp())
            lineTo(cx - 6.dp(), r.top + 2.dp() + bh)
            lineTo(cx - bw / 2, r.top + 2.dp() + bh)
            close()
        }
        path(box, 1.8f.dp(), fill = Color.Black.copy(alpha = 0.55f))
        text(label, cx, r.top + 2.dp() + bh / 2, 17.sp, weight = FontWeight.Black)
        text(src, cx - bw / 2 - 6.dp(), r.top + 2.dp() + bh / 2, 10.sp, Align.RIGHT, color = c.copy(alpha = 0.7f))
    }

    private fun DrawScope.speedTape(r: Rect) {
        val u = input.units
        val v = u.speed(input.tel.speed)
        val span = when {
            v < 30 -> 30.0
            v < 120 -> 60.0
            else -> 160.0
        }
        val major = if (span > 100) 20 else 10
        val minor = if (span > 100) 5 else if (span > 40) 2 else 1
        val pxPer = r.height / span
        val cy = r.center.y
        val edge = r.right
        line(Offset(edge, r.top), Offset(edge, r.bottom), 1.5f.dp())
        clipRect(r.left, r.top, r.right + 2.dp(), r.bottom) {
            // Pasek ostrzeżenia nad progiem prędkości.
            if (input.speedAlert > 0) {
                val alertY = (cy - (u.speed(input.speedAlert) - v) * pxPer).toFloat()
                if (alertY > r.top) drawRect(Color(0xFFFF3B47).copy(alpha = 0.85f), Offset(edge - 4.dp(), r.top), Size(4.dp(), max(0f, alertY - r.top)))
            }
            var s = floor((v - span / 2) / minor).toInt() * minor
            while (s <= v + span / 2) {
                if (s >= 0) {
                    val y = (cy - (s - v) * pxPer).toFloat()
                    val isMajor = s % major == 0
                    line(Offset(edge, y), Offset(edge - if (isMajor) 12.dp() else 6.dp(), y), 1.5f.dp())
                    if (isMajor && abs(y - cy) > 30.dp()) text(s.toString(), edge - 16.dp(), y, 13.sp, Align.RIGHT)
                }
                s += minor
            }
            // Znacznik prędkości maksymalnej.
            if (input.maxSpeed > 0.5) {
                val y = (cy - (u.speed(input.maxSpeed) - v) * pxPer).toFloat()
                val tri = Path().apply {
                    moveTo(edge + 1.dp(), y)
                    lineTo(edge + 8.dp(), y - 5.dp())
                    lineTo(edge + 8.dp(), y + 5.dp())
                    close()
                }
                path(tri, 1.2f.dp(), fill = c)
            }
        }
        // Wektor trendu: gdzie będzie prędkość za 10 s.
        val trend = u.speed(input.tel.g * 9.80665 * 10)
        if (abs(trend) > 0.5) {
            val y2 = (cy - trend * pxPer).toFloat().coerceIn(r.top, r.bottom)
            line(Offset(edge + 4.dp(), cy), Offset(edge + 4.dp(), y2), 2.2f.dp())
        }
        valueBox(v.roundToInt().toString(), r.left, edge, cy, pointRight = true)
        text("GS  ${u.speedUnit}", r.left, r.top - 9.dp(), 11.sp, Align.LEFT, color = c.copy(alpha = 0.8f))
        if (input.maxSpeed > 0.5) text("MAX ${u.speed(input.maxSpeed).roundToInt()}", r.left, r.bottom + 10.dp(), 11.sp, Align.LEFT, color = c.copy(alpha = 0.8f))
    }

    private fun DrawScope.altTape(r: Rect) {
        val u = input.units
        val altM = input.tel.alt
        val a = altM?.let { u.alt(it) }
        val metric = u.altUnit == "m"
        val span = if (metric) 300.0 else 1000.0
        val major = if (metric) 50 else 200
        val minor = if (metric) 10 else 50
        val pxPer = r.height / span
        val cy = r.center.y
        val edge = r.left
        line(Offset(edge, r.top), Offset(edge, r.bottom), 1.5f.dp())
        if (a != null) {
            clipRect(r.left - 2.dp(), r.top, r.right, r.bottom) {
                var s = floor((a - span / 2) / minor).toInt() * minor
                while (s <= a + span / 2) {
                    val y = (cy - (s - a) * pxPer).toFloat()
                    val isMajor = s % major == 0
                    line(Offset(edge, y), Offset(edge + if (isMajor) 12.dp() else 6.dp(), y), 1.5f.dp())
                    if (isMajor && abs(y - cy) > 30.dp()) text(s.toString(), edge + 16.dp(), y, 13.sp, Align.LEFT)
                    s += minor
                }
                // Poziom morza – kreskowana linia.
                val y0 = (cy + a * pxPer).toFloat()
                if (y0 in r.top..r.bottom) line(Offset(edge, y0), Offset(r.right, y0), 1.dp(), effect = PathEffect.dashPathEffect(floatArrayOf(6.dp(), 5.dp())))
            }
        }
        valueBox(a?.roundToInt()?.toString() ?: "– – –", edge, r.right, cy, pointRight = false)
        text("ALT  ${u.altUnit}", r.right, r.top - 9.dp(), 11.sp, Align.RIGHT, color = c.copy(alpha = 0.8f))
        // Prędkość pionowa.
        val vs = u.vs(input.tel.vs)
        val vsText = when {
            abs(input.tel.vs) < 0.15 -> "VS  0"
            metric -> "VS %+.1f".format(vs)
            else -> "VS %+d".format((vs / 10).roundToInt() * 10)
        }
        text(vsText, r.right, r.bottom + 10.dp(), 11.sp, Align.RIGHT, color = c.copy(alpha = 0.8f))
        // Wskaźnik VS: krótka strzałka przy taśmie.
        val clamp = (input.tel.vs / 5.0).coerceIn(-1.0, 1.0)
        if (abs(clamp) > 0.03) {
            val len = (clamp * r.height * 0.25).toFloat()
            line(Offset(r.right + 3.dp(), cy), Offset(r.right + 3.dp(), cy - len), 2.2f.dp())
        }
    }

    /** Okienko bieżącej wartości z „dziobem” w stronę taśmy. */
    private fun DrawScope.valueBox(value: String, left: Float, right: Float, cy: Float, pointRight: Boolean) {
        val bh = 36.dp()
        val notch = 8.dp()
        val p = Path().apply {
            if (pointRight) {
                moveTo(left, cy - bh / 2)
                lineTo(right - notch, cy - bh / 2)
                lineTo(right - notch, cy - notch)
                lineTo(right, cy)
                lineTo(right - notch, cy + notch)
                lineTo(right - notch, cy + bh / 2)
                lineTo(left, cy + bh / 2)
            } else {
                moveTo(right, cy - bh / 2)
                lineTo(left + notch, cy - bh / 2)
                lineTo(left + notch, cy - notch)
                lineTo(left, cy)
                lineTo(left + notch, cy + notch)
                lineTo(left + notch, cy + bh / 2)
                lineTo(right, cy + bh / 2)
            }
            close()
        }
        path(p, 2.dp(), fill = Color.Black.copy(alpha = 0.7f))
        val size = if (value.length > 4) 19.sp else 24.sp
        val cx = if (pointRight) (left + right - notch) / 2 else (left + notch + right) / 2
        text(value, cx, cy, size, weight = FontWeight.Black)
    }

    private fun DrawScope.horizon(r: Rect) {
        val att = input.att
        val cx = r.center.x
        val cy = r.center.y
        val pxPerDeg = r.height / 44f
        val half = r.width / 2
        clipRect(r.left, r.top, r.right, r.bottom) {
            rotate(-att.roll, Offset(cx, cy)) {
                translate(0f, att.pitch * pxPerDeg) {
                    // Linia horyzontu z przerwą na symbol.
                    val gap = half * 0.22f
                    line(Offset(cx - half * 1.6f, cy), Offset(cx - gap, cy), 2.dp())
                    line(Offset(cx + gap, cy), Offset(cx + half * 1.6f, cy), 2.dp())
                    // Drabinka pochylenia co 5°, poniżej horyzontu – przerywana (konwencja lotnicza).
                    for (deg in -60..60 step 5) {
                        if (deg == 0) continue
                        val y = cy - deg * pxPerDeg
                        if (y < r.top - 40.dp() - att.pitch * pxPerDeg || y > r.bottom + 40.dp() - att.pitch * pxPerDeg) continue
                        val major = deg % 10 == 0
                        val wing = if (major) half * 0.42f else half * 0.22f
                        val inner = half * 0.16f
                        val dash = if (deg < 0) PathEffect.dashPathEffect(floatArrayOf(5.dp(), 4.dp())) else null
                        val tick = if (deg > 0) 6.dp() else -6.dp()
                        line(Offset(cx - inner - wing, y), Offset(cx - inner, y), 1.6f.dp(), effect = dash)
                        line(Offset(cx + inner, y), Offset(cx + inner + wing, y), 1.6f.dp(), effect = dash)
                        line(Offset(cx - inner - wing, y), Offset(cx - inner - wing, y + tick), 1.6f.dp())
                        line(Offset(cx + inner + wing, y), Offset(cx + inner + wing, y + tick), 1.6f.dp())
                        if (major) {
                            text(abs(deg).toString(), cx - inner - wing - 6.dp(), y, 11.sp, Align.RIGHT)
                            text(abs(deg).toString(), cx + inner + wing + 6.dp(), y, 11.sp, Align.LEFT)
                        }
                    }
                }
            }
        }
        bankScale(r, att.roll)
    }

    /** Skala przechylenia u góry z ruchomym wskaźnikiem. */
    private fun DrawScope.bankScale(r: Rect, roll: Float) {
        val cx = r.center.x
        val radius = min(r.width * 0.46f, r.height * 0.62f)
        val cy = r.top + radius + 10.dp()
        for (a in listOf(-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60)) {
            val rad = Math.toRadians(a.toDouble() - 90)
            val len = when {
                a == 0 -> 0f
                abs(a) % 30 == 0 -> 11.dp()
                else -> 6.dp()
            }
            val ox = cx + (cos(rad) * radius).toFloat()
            val oy = cy + (sin(rad) * radius).toFloat()
            if (a == 0) {
                val tri = Path().apply {
                    moveTo(ox, oy)
                    lineTo(ox - 6.dp(), oy - 9.dp())
                    lineTo(ox + 6.dp(), oy - 9.dp())
                    close()
                }
                path(tri, 1.5f.dp())
            } else {
                val ix = cx + (cos(rad) * (radius - len)).toFloat()
                val iy = cy + (sin(rad) * (radius - len)).toFloat()
                line(Offset(ox, oy), Offset(ix, iy), 1.5f.dp())
            }
        }
        val pr = Math.toRadians(-roll.toDouble().coerceIn(-65.0, 65.0) - 90)
        val bx = cx + (cos(pr) * (radius - 3.dp())).toFloat()
        val by = cy + (sin(pr) * (radius - 3.dp())).toFloat()
        rotate(-roll.coerceIn(-65f, 65f), Offset(bx, by)) {
            val tri = Path().apply {
                moveTo(bx, by)
                lineTo(bx - 6.dp(), by + 10.dp())
                lineTo(bx + 6.dp(), by + 10.dp())
                close()
            }
            path(tri, 1.5f.dp(), fill = c)
        }
        if (abs(roll) >= 3) text("%d°".format(abs(roll).roundToInt()), cx, cy - radius + 26.dp(), 11.sp, color = c.copy(alpha = 0.8f))
    }

    /** Bez czujnika orientacji: sam krzyż celownika i kompas tekstowy. */
    private fun DrawScope.plainReticle(r: Rect) {
        val cx = r.center.x
        val cy = r.center.y
        val l = min(r.width, r.height) * 0.42f
        line(Offset(cx - l, cy), Offset(cx - l * 0.35f, cy), 1.5f.dp(), effect = PathEffect.dashPathEffect(floatArrayOf(6.dp(), 6.dp())))
        line(Offset(cx + l * 0.35f, cy), Offset(cx + l, cy), 1.5f.dp(), effect = PathEffect.dashPathEffect(floatArrayOf(6.dp(), 6.dp())))
    }

    /** Stały symbol „samolotu” (W) w środku. */
    private fun DrawScope.aircraftSymbol(center: Offset, space: Float) {
        val s = (space * 0.11f).coerceIn(14.dp(), 26.dp())
        val p = Path().apply {
            moveTo(center.x - s * 2.6f, center.y)
            lineTo(center.x - s, center.y)
            lineTo(center.x - s * 0.5f, center.y + s * 0.6f)
            lineTo(center.x, center.y)
            lineTo(center.x + s * 0.5f, center.y + s * 0.6f)
            lineTo(center.x + s, center.y)
            lineTo(center.x + s * 2.6f, center.y)
        }
        path(p, 2.6f.dp())
        drawCircle(c, 2.5f.dp(), center)
    }

    private fun DrawScope.bottomLine(r: Rect) {
        val g = input.gnss
        val acc = input.tel.accuracy
        val gps = when {
            !g.enabled -> "GPS OFF"
            !input.tel.hasFix -> "GPS  ${g.used}/${g.visible}  …"
            else -> "GPS  ${g.used}/${g.visible}  ±${acc?.roundToInt() ?: "?"} m"
        }
        val gColor = when {
            !g.enabled || !input.tel.hasFix -> Color(0xFFFF3B47)
            (acc ?: 99.0) > 20 -> Color(0xFFFFC14D)
            else -> c.copy(alpha = 0.85f)
        }
        text(gps, r.left, r.center.y, 12.sp, Align.LEFT, color = gColor)
        text("%+.2f G".format(input.tel.g), r.center.x, r.center.y, 12.sp, color = c.copy(alpha = 0.85f))
        text(Units.timeSec(input.clock), r.right, r.center.y, 12.sp, Align.RIGHT, color = c.copy(alpha = 0.85f))
    }

    // ----------------------------------------------------------------- styl prosty

    fun DrawScope.drawSimple() {
        px = density
        val w = size.width
        val h = size.height
        val m = 10.dp()
        val u = input.units
        val v = u.speed(input.tel.speed)
        headingTape(Rect(w * 0.12f, m, w * 0.88f, m + 44.dp()))

        // Łuk prędkości od 0 do zakresu dopasowanego do bieżącej wartości.
        val range = listOf(10.0, 20.0, 40.0, 60.0, 100.0, 140.0, 200.0, 300.0, 500.0, 1000.0).first { it > max(v, u.speed(input.maxSpeed)) * 1.05 }
        val cx = w / 2
        val top = m + 70.dp()
        val bottomReserve = 64.dp()
        val radius = min(w * 0.42f, (h - top - bottomReserve) * 0.62f).coerceAtLeast(40.dp())
        val cy = top + radius
        val sweep = 240f
        val startAng = 150f
        val arcRect = Rect(Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2))
        if (input.glow) drawArc(c.copy(alpha = 0.12f), startAng, sweep, false, arcRect.topLeft, arcRect.size, style = Stroke(10.dp(), cap = StrokeCap.Round))
        drawArc(c.copy(alpha = 0.25f), startAng, sweep, false, arcRect.topLeft, arcRect.size, style = Stroke(3.dp(), cap = StrokeCap.Round))
        val frac = (v / range).toFloat().coerceIn(0f, 1f)
        drawArc(c, startAng, sweep * frac, false, arcRect.topLeft, arcRect.size, style = Stroke(5.dp(), cap = StrokeCap.Round))
        // podziałka
        val steps = 10
        for (i in 0..steps) {
            val ang = Math.toRadians((startAng + sweep * i / steps).toDouble())
            val o = Offset(cx + (cos(ang) * (radius - 10.dp())).toFloat(), cy + (sin(ang) * (radius - 10.dp())).toFloat())
            val o2 = Offset(cx + (cos(ang) * (radius - if (i % 5 == 0) 22.dp() else 16.dp())).toFloat(), cy + (sin(ang) * (radius - if (i % 5 == 0) 22.dp() else 16.dp())).toFloat())
            line(o, o2, 1.5f.dp())
            if (i % 5 == 0) {
                val lp = Offset(cx + (cos(ang) * (radius - 36.dp())).toFloat(), cy + (sin(ang) * (radius - 36.dp())).toFloat())
                text((range * i / steps).roundToInt().toString(), lp.x, lp.y, 11.sp, color = c.copy(alpha = 0.7f))
            }
        }
        if (input.maxSpeed > 0.5) {
            val ang = Math.toRadians((startAng + sweep * (u.speed(input.maxSpeed) / range).toFloat().coerceIn(0f, 1f)).toDouble())
            drawCircle(c, 4.dp(), Offset(cx + (cos(ang) * radius).toFloat(), cy + (sin(ang) * radius).toFloat()))
        }

        val digits = v.roundToInt().toString()
        val big = (radius * 0.85f / density).coerceIn(48f, 150f)
        val alertOn = input.speedAlert > 0 && input.tel.speed > input.speedAlert
        text(digits, cx, cy - radius * 0.05f, big.sp, weight = FontWeight.Black, color = if (alertOn) Color(0xFFFF3B47) else c)
        text(u.speedUnit, cx, cy + radius * 0.42f, 16.sp, color = c.copy(alpha = 0.8f))

        // Dolny rząd: wysokość, prędkość pionowa, kurs.
        val rowY = min(h - 34.dp(), cy + radius + 26.dp())
        val alt = input.tel.alt?.let { u.alt(it).roundToInt().toString() } ?: "–"
        val vsv = u.vs(input.tel.vs)
        val vs = if (u.altUnit == "m") "%+.1f".format(vsv) else "%+d".format((vsv / 10).roundToInt() * 10)
        val (hdg, _) = heading()
        val cells = listOf(
            "ALT" to "$alt ${u.altUnit}",
            "VS" to "$vs ${u.vsUnit}",
            "HDG" to (hdg?.let { "%03d°".format(((it.roundToInt() % 360) + 360) % 360) } ?: "–"),
        )
        val cw = (w - 2 * m) / cells.size
        cells.forEachIndexed { i, (k, value) ->
            val x = m + cw * i + cw / 2
            text(value, x, rowY, 18.sp, weight = FontWeight.Black)
            text(k, x, rowY + 20.dp(), 10.sp, color = c.copy(alpha = 0.65f))
        }
        if (h - 14.dp() > rowY + 30.dp()) bottomLine(Rect(m, h - 14.dp(), w - m, h - 2.dp()))
    }
}
