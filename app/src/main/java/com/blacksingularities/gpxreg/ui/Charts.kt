package com.blacksingularities.gpxreg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.gpxreg.data.Geo
import com.blacksingularities.gpxreg.data.TrackPoint
import com.blacksingularities.gpxreg.data.TransportMode
import com.blacksingularities.gpxreg.ui.theme.Palette
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Próbka profilu: dystans od startu (m), wartość, indeks punktu, środek transportu. */
data class ProfileSample(val dist: Double, val value: Double, val index: Int, val mode: TransportMode)

/** Profil wzdłuż dystansu z [points]; [value] – np. prędkość lub wysokość (w jednostkach widoku). */
fun buildProfile(points: List<TrackPoint>, modes: Map<Long, TransportMode>, maxSamples: Int = 700, value: (TrackPoint) -> Double?): List<ProfileSample> {
    if (points.size < 2) return emptyList()
    val out = ArrayList<ProfileSample>(points.size)
    var d = 0.0
    for (i in points.indices) {
        val p = points[i]
        if (i > 0 && !p.gap) d += Geo.distance(points[i - 1].lat, points[i - 1].lon, p.lat, p.lon)
        val v = value(p) ?: continue
        out += ProfileSample(d, v, i, modes[p.segmentId] ?: TransportMode.OTHER)
    }
    if (out.size <= maxSamples) return out
    // Uśrednianie w kubełkach – wykres gładszy i lżejszy.
    val step = out.size.toDouble() / maxSamples
    return List(maxSamples) { k ->
        val a = (k * step).toInt()
        val b = min(out.size, ((k + 1) * step).toInt()).coerceAtLeast(a + 1)
        val slice = out.subList(a, b)
        ProfileSample(slice.last().dist, slice.sumOf { it.value } / slice.size, slice[slice.size / 2].index, slice.first().mode)
    }
}

/**
 * Wykres profilu z linią w kolorach środków transportu. Przeciągnięcie palcem przesuwa kursor –
 * [onScrub] dostaje indeks punktu (do znacznika na mapie) albo null po puszczeniu.
 */
@Composable
fun ProfileChart(
    samples: List<ProfileSample>,
    modifier: Modifier = Modifier,
    formatY: (Double) -> String,
    formatX: (Double) -> String,
    cursor: Int?,
    onScrub: (Int?) -> Unit,
) {
    val tm = rememberTextMeasurer()
    val scrub by rememberUpdatedState(onScrub)
    val data by rememberUpdatedState(samples)
    val labelStyle = remember { TextStyle(color = Palette.TextDim, fontSize = 10.sp, fontFeatureSettings = "tnum") }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun pick(x: Float) {
                        val s = data
                        if (s.isEmpty()) return
                        val left = 40.dp.toPx()
                        val w = size.width - left
                        val maxD = s.last().dist.coerceAtLeast(1.0)
                        val d = ((x - left) / w).coerceIn(0f, 1f) * maxD
                        val i = s.binarySearchBy(d) { it.dist }.let { if (it < 0) (-it - 1).coerceAtMost(s.lastIndex) else it }
                        scrub(i)
                    }
                    pick(down.position.x)
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        pick(ch.position.x)
                        ch.consume()
                    }
                    scrub(null)
                }
            },
    ) {
        if (samples.size < 2) return@Canvas
        val left = 40.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 6.dp.toPx()
        val w = size.width - left
        val h = bottom - top
        var lo = samples.minOf { it.value }
        var hi = samples.maxOf { it.value }
        if (hi - lo < 1e-6) {
            hi += 1
            lo -= 1
        }
        val step = niceStep((hi - lo) / 4)
        lo = floor(lo / step) * step
        hi = ceil(hi / step) * step
        val maxD = samples.last().dist.coerceAtLeast(1.0)
        fun x(d: Double) = left + (d / maxD * w).toFloat()
        fun y(v: Double) = bottom - ((v - lo) / (hi - lo) * h).toFloat()

        // siatka pozioma z opisami
        var gy = lo
        while (gy <= hi + step / 2) {
            val yy = y(gy)
            drawLine(Palette.Hairline, Offset(left, yy), Offset(size.width, yy), 1f)
            val r = tm.measure(formatY(gy), labelStyle)
            drawText(r, topLeft = Offset(left - r.size.width - 6.dp.toPx(), yy - r.size.height / 2f))
            gy += step
        }
        // opisy osi X (start, środek, koniec)
        for (f in listOf(0.0, 0.5, 1.0)) {
            val r = tm.measure(formatX(maxD * f), labelStyle)
            val xx = (left + w * f.toFloat() - r.size.width * f.toFloat())
            drawText(r, topLeft = Offset(xx, bottom + 4.dp.toPx()))
        }

        // wypełnienie pod linią
        val area = Path().apply {
            moveTo(x(samples.first().dist), bottom)
            samples.forEach { lineTo(x(it.dist), y(it.value)) }
            lineTo(x(samples.last().dist), bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), top, bottom))

        // linia w kolorach fragmentów
        var i = 0
        while (i < samples.size - 1) {
            val mode = samples[i].mode
            val p = Path().apply { moveTo(x(samples[i].dist), y(samples[i].value)) }
            var j = i
            while (j < samples.size - 1 && samples[j].mode == mode) {
                j++
                p.lineTo(x(samples[j].dist), y(samples[j].value))
            }
            drawPath(p, Color(mode.color), style = Stroke(2.2f.dp.toPx(), cap = StrokeCap.Round))
            i = j
        }

        cursor?.let { ci ->
            val s = samples.getOrNull(ci) ?: return@let
            val cx = x(s.dist)
            drawLine(Color.White, Offset(cx, top), Offset(cx, bottom), 1.5f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            drawCircle(Color.White, 5.dp.toPx(), Offset(cx, y(s.value)))
            val label = formatY(s.value) + "  ·  " + formatX(s.dist)
            val r = tm.measure(label, labelStyle.copy(color = Color.White, fontSize = 12.sp))
            val bx = (cx - r.size.width / 2f).coerceIn(left, size.width - r.size.width)
            drawRoundRect(Palette.PanelHigher, Offset(bx - 6.dp.toPx(), top), androidx.compose.ui.geometry.Size(r.size.width + 12.dp.toPx(), r.size.height + 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            drawText(r, topLeft = Offset(bx, top + 3.dp.toPx()))
        }
    }
}

/** Krok siatki 1/2/5×10^n. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val p = 10.0.pow(floor(log10(raw)))
    val f = raw / p
    return p * when {
        f < 1.5 -> 1.0
        f < 3.5 -> 2.0
        f < 7.5 -> 5.0
        else -> 10.0
    }
}

/** Słupki wartości (np. dystans w miesiącach). */
@Composable
fun BarChart(values: List<Pair<String, Double>>, modifier: Modifier = Modifier, formatTop: (Double) -> String) {
    val tm = rememberTextMeasurer()
    val label = remember { TextStyle(color = Palette.TextDim, fontSize = 10.sp) }
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxV = max(values.maxOf { it.second }, 1.0)
        val bottom = size.height - 16.dp.toPx()
        val top = 16.dp.toPx()
        val gap = 6.dp.toPx()
        val bw = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, (k, v) ->
            val x = i * (bw + gap)
            val bh = ((v / maxV) * (bottom - top)).toFloat()
            drawRoundRect(
                if (i == values.lastIndex) Color.White else Color.White.copy(alpha = 0.35f),
                Offset(x, bottom - max(bh, 2f)),
                androidx.compose.ui.geometry.Size(bw, max(bh, 2f)),
                androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
            )
            val r = tm.measure(k, label)
            drawText(r, topLeft = Offset(x + bw / 2 - r.size.width / 2, bottom + 3.dp.toPx()))
            if (v > 0 && (i == values.lastIndex || v == values.maxOf { it.second })) {
                val t = tm.measure(formatTop(v), label.copy(color = Color.White))
                drawText(t, topLeft = Offset((x + bw / 2 - t.size.width / 2).coerceIn(0f, size.width - t.size.width), bottom - bh - t.size.height - 2.dp.toPx()))
            }
        }
    }
}
