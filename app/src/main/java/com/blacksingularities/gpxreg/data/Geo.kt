package com.blacksingularities.gpxreg.data

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val R = 6_371_008.8

    /** Odległość po kole wielkim (m). */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
        return 2 * R * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Kurs początkowy z punktu 1 do 2 (°, 0–360). */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Uproszczenie linii (Douglas–Peucker w przybliżeniu płaskim) do co najwyżej ~[maxPoints]. */
    fun simplify(points: List<LatLon>, maxPoints: Int): List<LatLon> {
        if (points.size <= maxPoints) return points
        var eps = 1e-5
        var out = points
        repeat(12) {
            out = douglasPeucker(points, eps)
            if (out.size <= maxPoints) return out
            eps *= 2
        }
        val step = points.size.toDouble() / maxPoints
        return List(maxPoints) { points[(it * step).toInt()] } + points.last()
    }

    private fun douglasPeucker(pts: List<LatLon>, eps: Double): List<LatLon> {
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to pts.size - 1)
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var maxD = 0.0
            var idx = -1
            for (i in s + 1 until e) {
                val d = perp(pts[i], pts[s], pts[e])
                if (d > maxD) {
                    maxD = d
                    idx = i
                }
            }
            if (idx >= 0 && maxD > eps) {
                keep[idx] = true
                stack.add(s to idx)
                stack.add(idx to e)
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    private fun perp(p: LatLon, a: LatLon, b: LatLon): Double {
        val dx = b.lon - a.lon
        val dy = b.lat - a.lat
        if (dx == 0.0 && dy == 0.0) return sqrt((p.lon - a.lon).let { it * it } + (p.lat - a.lat).let { it * it })
        return abs(dy * p.lon - dx * p.lat + b.lon * a.lat - b.lat * a.lon) / sqrt(dx * dx + dy * dy)
    }
}

/**
 * Liczy podsumowanie z kolejnych punktów – tak samo na żywo w trakcie nagrywania, jak i przy
 * przeliczaniu po imporcie lub edycji fragmentów. Przewyższenia z histerezą, żeby szum GPS nie
 * dodawał setek metrów podejść na płaskim terenie.
 */
class StatsAccumulator(
    private val climbThreshold: Double = 4.0,
    private val movingSpeed: Double = 0.6,
) {
    var distance = 0.0
        private set
    var duration = 0L
        private set
    var movingTime = 0L
        private set
    var maxSpeed = 0.0
        private set
    var ascent = 0.0
        private set
    var descent = 0.0
        private set
    var minEle: Double? = null
        private set
    var maxEle: Double? = null
        private set
    var count = 0
        private set

    private var last: TrackPoint? = null
    private var anchorEle: Double? = null

    /** Dystans dodany przez ostatni punkt (m) – do liczników na żywo. */
    var lastStep = 0.0
        private set

    fun add(p: TrackPoint) {
        count++
        lastStep = 0.0
        val prev = last
        if (prev != null && !p.gap) {
            val dt = (p.time - prev.time).coerceAtLeast(0)
            val d = Geo.distance(prev.lat, prev.lon, p.lat, p.lon)
            distance += d
            lastStep = d
            duration += dt
            val v = p.speed ?: if (dt > 0) d / (dt / 1000.0) else 0.0
            if (v >= movingSpeed && dt < 5 * 60_000) movingTime += dt
        }
        p.speed?.let { if (it > maxSpeed && it < 350) maxSpeed = it }
        p.ele?.let { e ->
            minEle = min(minEle ?: e, e)
            maxEle = max(maxEle ?: e, e)
            val a = anchorEle
            if (a == null) {
                anchorEle = e
            } else if (e - a >= climbThreshold) {
                ascent += e - a
                anchorEle = e
            } else if (a - e >= climbThreshold) {
                descent += a - e
                anchorEle = e
            }
        }
        last = p
    }

    fun snapshot() = Stats(distance, duration, movingTime, maxSpeed, ascent, descent, minEle, maxEle, count)

    companion object {
        fun of(points: Iterable<TrackPoint>): Stats = StatsAccumulator().apply { points.forEach { add(it) } }.snapshot()
    }
}
