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
    /** Ostatnie pozycje (od najstarszej) do śladu ruchu. */
    val trail: List<com.blacksingularities.gpxreg.data.LatLon> = emptyList(),
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
        if (input.horizon) trailView(center)
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
                        if (abs(x - cx) > 66.dp() && x > r.left + 12.dp() && x < r.right - 12.dp()) text(label, x, baseY - 21.dp(), 13.sp, weight = if (norm % 90 == 0) FontWeight.Black else FontWeight.Bold)
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

    /**
     * Ślad ruchu: przejechana droga z ostatnich minut w układzie „kierunek ruchu w górę”, jak
     * wyświetlacz nawigacyjny. Pozycja stała w dolnej części, przed nią przerywany wektor –
     * gdzie będziesz za 30 s przy obecnej prędkości – i pierścienie zasięgu z opisem.
     */
    private fun DrawScope.trailView(r: Rect) {
        val tel = input.tel
        val me = Offset(r.center.x, r.top + r.height * 0.74f)
        val radius = min(r.width * 0.5f, (me.y - r.top) * 0.96f)
        val course = tel.course
        val rot = Math.toRadians(-(course ?: 0.0))
        val k = cos(Math.toRadians(tel.lat))
        // Punkty w metrach względem bieżącej pozycji, obrócone kierunkiem ruchu w górę.
        val local = input.trail.map { p ->
            val dx = (p.lon - tel.lon) * 111_320.0 * k
            val dy = (p.lat - tel.lat) * 110_540.0
            Offset((dx * cos(rot) - dy * sin(rot)).toFloat(), (dx * sin(rot) + dy * cos(rot)).toFloat())
        }
        val ahead = tel.speed * 30
        val far = max(local.maxOfOrNull { kotlin.math.hypot(it.x.toDouble(), it.y.toDouble()) } ?: 0.0, ahead)
        val range = listOf(50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0)
            .firstOrNull { it >= far * 1.05 } ?: 50_000.0
        val scale = (radius / range).toFloat()
        fun pt(o: Offset) = Offset(me.x + o.x * scale, me.y - o.y * scale)

        clipRect(r.left, r.top, r.right, r.bottom) {
            // Pierścienie zasięgu (pełny i połowa) – łuki przed pozycją.
            for ((f, dash) in listOf(1f to false, 0.5f to true)) {
                val rr = radius * f
                val arc = Path().apply {
                    addArc(Rect(me.x - rr, me.y - rr, me.x + rr, me.y + rr), 200f, 140f)
                }
                if (dash) {
                    drawPath(arc, c.copy(alpha = 0.45f), style = Stroke(1.2f.dp(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp(), 5.dp()))))
                } else {
                    path(arc, 1.4f.dp(), color = c.copy(alpha = 0.7f))
                }
            }
            text(input.units.fmtDist(range), me.x + radius * 0.94f * cos(Math.toRadians(-20.0)).toFloat() - 4.dp(), me.y - radius * 0.94f * sin(Math.toRadians(20.0)).toFloat() - 10.dp(), 11.sp, Align.RIGHT, color = c.copy(alpha = 0.75f))
            text(if (course != null) "TRK UP" else "N UP", r.left + 4.dp(), r.top + 10.dp(), 10.sp, Align.LEFT, color = c.copy(alpha = 0.6f))

            // Ślad – im starszy, tym bledszy.
            if (local.size > 1) {
                val n = local.size
                for (i in 1 until n) {
                    val alpha = 0.25f + 0.75f * i / n
                    line(pt(local[i - 1]), pt(local[i]), 2.4f.dp(), color = c.copy(alpha = alpha))
                }
            }
            // Wektor 30 s przed sobą.
            if (course != null && ahead > 1) {
                val tip = Offset(me.x, me.y - (ahead * scale).toFloat())
                line(me, tip, 2.dp(), effect = PathEffect.dashPathEffect(floatArrayOf(7.dp(), 5.dp())))
                drawCircle(c, 3.dp(), tip)
                text("30 s", tip.x + 8.dp(), tip.y, 10.sp, Align.LEFT, color = c.copy(alpha = 0.75f))
            }
        }
        // Znacznik pozycji – grot skierowany w stronę ruchu.
        val s = 11.dp()
        val chevron = Path().apply {
            moveTo(me.x, me.y - s * 1.3f)
            lineTo(me.x + s, me.y + s * 0.9f)
            lineTo(me.x, me.y + s * 0.35f)
            lineTo(me.x - s, me.y + s * 0.9f)
            close()
        }
        path(chevron, 2.dp(), fill = Color.Black.copy(alpha = 0.6f))
        if (course == null && !tel.hasFix) text("…", me.x, me.y + 26.dp(), 12.sp, color = c.copy(alpha = 0.6f))
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
