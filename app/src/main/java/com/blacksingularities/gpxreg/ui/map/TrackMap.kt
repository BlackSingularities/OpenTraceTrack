package com.blacksingularities.gpxreg.ui.map

import android.content.Context
import android.graphics.Color as AColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blacksingularities.gpxreg.data.LatLon
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Linia na mapie w kolorze środka transportu. */
data class MapLine(val color: Long, val points: List<LatLon>)

private const val SRC_LINES = "gpxreg-lines"
private const val SRC_ME = "gpxreg-me"
private const val SRC_WPT = "gpxreg-wpt"
private const val SRC_CURSOR = "gpxreg-cursor"
private const val SRC_ENDS = "gpxreg-ends"

/**
 * Mapa MapLibre z trasą. [fitKey] – po zmianie kamera obejmuje całą trasę; [follow] – kamera
 * podąża za pozycją [me] (przesunięcie mapy palcem wyłącza śledzenie przez [onFollowChange]).
 */
@Composable
fun TrackMap(
    styleUrl: String,
    lines: List<MapLine>,
    modifier: Modifier = Modifier,
    me: LatLon? = null,
    follow: Boolean = false,
    onFollowChange: (Boolean) -> Unit = {},
    fitKey: Any? = null,
    cursor: LatLon? = null,
    waypoints: List<LatLon> = emptyList(),
    showEnds: Boolean = false,
    bottomPadding: Int = 0,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder(context) }
    val onFollow by rememberUpdatedState(onFollowChange)

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> holder.view.onStart()
                Lifecycle.Event.ON_RESUME -> holder.view.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.view.onPause()
                Lifecycle.Event.ON_STOP -> holder.view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) holder.view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.view.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            holder.view.onPause()
            holder.view.onStop()
            holder.view.onDestroy()
        }
    }

    holder.onUserMove = { onFollow(false) }

    AndroidView(factory = { holder.view }, modifier = modifier)

    LaunchedEffect(styleUrl) { holder.setStyle(styleUrl) }
    LaunchedEffect(holder.styleVersion, lines) { holder.setLines(lines, showEnds) }
    LaunchedEffect(holder.styleVersion, me) { holder.setMe(me) }
    LaunchedEffect(holder.styleVersion, waypoints) { holder.setWaypoints(waypoints) }
    LaunchedEffect(holder.styleVersion, cursor) { holder.setCursor(cursor) }
    LaunchedEffect(holder.styleVersion, fitKey) { if (fitKey != null) holder.fit(lines, bottomPadding) }
    LaunchedEffect(holder.styleVersion, me, follow) { if (follow && me != null) holder.followTo(me) }
}

private class MapHolder(context: Context) {
    val view: MapView = MapView(context).apply { onCreate(null) }
    var map: MapLibreMap? = null
    var style: Style? = null
    var styleVersion by mutableStateOf(0)
    var onUserMove: () -> Unit = {}
    private var currentUrl: String? = null
    private var pendingUrl: String? = null
    private var fittedOnce = false

    init {
        view.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                isCompassEnabled = true
                isLogoEnabled = false
                isAttributionEnabled = true
                isTiltGesturesEnabled = false
                setCompassMargins(0, 24 * density(), 24 * density(), 0)
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(52.0, 19.0)).zoom(4.5).build()
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserMove()
            }
            pendingUrl?.let { setStyle(it) }
        }
    }

    private fun density() = view.resources.displayMetrics.density.toInt().coerceAtLeast(1)

    fun setStyle(url: String) {
        val m = map
        if (m == null) {
            pendingUrl = url
            return
        }
        if (url == currentUrl) return
        currentUrl = url
        m.setStyle(Style.Builder().fromUri(url)) { s ->
            style = s
            addLayers(s)
            styleVersion++
        }
    }

    private fun addLayers(s: Style) {
        s.addSource(org.maplibre.android.style.sources.GeoJsonSource(SRC_LINES))
        s.addSource(org.maplibre.android.style.sources.GeoJsonSource(SRC_ENDS))
        s.addSource(org.maplibre.android.style.sources.GeoJsonSource(SRC_WPT))
        s.addSource(org.maplibre.android.style.sources.GeoJsonSource(SRC_CURSOR))
        s.addSource(org.maplibre.android.style.sources.GeoJsonSource(SRC_ME))
        // Ciemna obwódka pod linią – trasa czytelna na każdym podkładzie.
        s.addLayer(
            LineLayer("gpxreg-lines-casing", SRC_LINES).withProperties(
                PropertyFactory.lineColor(AColor.BLACK),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        s.addLayer(
            LineLayer("gpxreg-lines", SRC_LINES).withProperties(
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        s.addLayer(
            CircleLayer("gpxreg-ends", SRC_ENDS).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeColor(AColor.BLACK),
                PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
        s.addLayer(
            CircleLayer("gpxreg-wpt", SRC_WPT).withProperties(
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor(AColor.WHITE),
                PropertyFactory.circleStrokeColor(AColor.parseColor("#FF3B47")),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
        s.addLayer(
            CircleLayer("gpxreg-cursor", SRC_CURSOR).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(AColor.WHITE),
                PropertyFactory.circleStrokeColor(AColor.BLACK),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
        s.addLayer(
            CircleLayer("gpxreg-me-halo", SRC_ME).withProperties(
                PropertyFactory.circleRadius(16f),
                PropertyFactory.circleColor(AColor.WHITE),
                PropertyFactory.circleOpacity(0.18f),
            ),
        )
        s.addLayer(
            CircleLayer("gpxreg-me", SRC_ME).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(AColor.WHITE),
                PropertyFactory.circleStrokeColor(AColor.BLACK),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }

    private fun source(id: String) = style?.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(id)

    private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFFL)

    fun setLines(lines: List<MapLine>, ends: Boolean) {
        val src = source(SRC_LINES) ?: return
        val features = lines.filter { it.points.size > 1 }.map { l ->
            Feature.fromGeometry(LineString.fromLngLats(l.points.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                addStringProperty("color", hex(l.color))
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
        val endSrc = source(SRC_ENDS) ?: return
        if (ends && lines.isNotEmpty()) {
            val first = lines.first { it.points.isNotEmpty() }
            val last = lines.last { it.points.isNotEmpty() }
            endSrc.setGeoJson(
                FeatureCollection.fromFeatures(
                    listOf(
                        Feature.fromGeometry(Point.fromLngLat(first.points.first().lon, first.points.first().lat)).apply { addStringProperty("color", "#FFFFFF") },
                        Feature.fromGeometry(Point.fromLngLat(last.points.last().lon, last.points.last().lat)).apply { addStringProperty("color", "#FF3B47") },
                    ),
                ),
            )
        } else {
            endSrc.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    fun setMe(me: LatLon?) {
        val src = source(SRC_ME) ?: return
        src.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(me?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })))
    }

    fun setWaypoints(w: List<LatLon>) {
        source(SRC_WPT)?.setGeoJson(FeatureCollection.fromFeatures(w.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }))
    }

    fun setCursor(c: LatLon?) {
        source(SRC_CURSOR)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(c?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })))
    }

    fun fit(lines: List<MapLine>, bottomPadding: Int) {
        val m = map ?: return
        val pts = lines.flatMap { it.points }
        if (pts.isEmpty()) return
        val pad = 56 * density()
        if (pts.size == 1 || pts.all { it == pts[0] }) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pts[0].lat, pts[0].lon), 15.0))
            return
        }
        val b = LatLngBounds.Builder()
        pts.forEach { b.include(LatLng(it.lat, it.lon)) }
        val update = CameraUpdateFactory.newLatLngBounds(b.build(), pad, pad, pad, pad + bottomPadding)
        if (fittedOnce) m.animateCamera(update, 600) else m.moveCamera(update)
        fittedOnce = true
    }

    fun followTo(me: LatLon) {
        val m = map ?: return
        val zoom = if (m.cameraPosition.zoom < 12) 16.0 else m.cameraPosition.zoom
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(me.lat, me.lon), zoom), 700)
    }
}
