package com.blacksingularities.gpxreg.ui.map

import android.content.Context
import com.blacksingularities.gpxreg.data.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Stan pobierania obszaru mapy do pracy bez internetu. */
sealed interface OfflineState {
    data object Idle : OfflineState
    data class Downloading(val name: String, val progress: Float, val bytes: Long) : OfflineState
    data class Done(val name: String, val bytes: Long) : OfflineState
    data class Failed(val message: String) : OfflineState
}

data class OfflineArea(val id: Long, val name: String)

/**
 * Mapy offline: obszar wokół trasy pobierany do bazy MapLibre (kafelki wektorowe, styl, czcionki)
 * oraz większa pamięć podręczna kafelków już oglądanych – w terenie bez zasięgu mapa dalej działa.
 */
object OfflineMaps {
    private const val TILE_LIMIT = 60_000L
    private const val AMBIENT_CACHE = 256L * 1024 * 1024

    private val _state = MutableStateFlow<OfflineState>(OfflineState.Idle)
    val state: StateFlow<OfflineState> = _state.asStateFlow()

    private val _areas = MutableStateFlow<List<OfflineArea>>(emptyList())
    val areas: StateFlow<List<OfflineArea>> = _areas.asStateFlow()

    fun init(context: Context) {
        runCatching {
            val m = OfflineManager.getInstance(context)
            m.setOfflineMapboxTileCountLimit(TILE_LIMIT)
            m.setMaximumAmbientCacheSize(AMBIENT_CACHE, object : OfflineManager.FileSourceCallback {
                override fun onSuccess() = Unit
                override fun onError(message: String) = Unit
            })
        }
    }

    fun refresh(context: Context) {
        runCatching {
            OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    _areas.value = offlineRegions.orEmpty().map { OfflineArea(it.id, String(it.metadata, Charsets.UTF_8)) }
                }

                override fun onError(error: String) = Unit
            })
        }
    }

    /** Pobiera obszar obejmujący [points] (z marginesem) w stylu [styleUrl]. */
    fun download(context: Context, name: String, styleUrl: String, points: List<LatLon>) {
        if (points.isEmpty() || _state.value is OfflineState.Downloading) return
        var south = points.minOf { it.lat }
        var north = points.maxOf { it.lat }
        var west = points.minOf { it.lon }
        var east = points.maxOf { it.lon }
        val padLat = maxOf((north - south) * 0.15, 0.01)
        val padLon = maxOf((east - west) * 0.15, 0.01)
        south = (south - padLat).coerceAtLeast(-85.0)
        north = (north + padLat).coerceAtMost(85.0)
        west = (west - padLon).coerceAtLeast(-180.0)
        east = (east + padLon).coerceAtMost(180.0)
        val bounds = LatLngBounds.from(north, east, south, west)
        val maxZoom = maxZoomFor(south, north, west, east)
        val ratio = context.resources.displayMetrics.density
        val def = OfflineTilePyramidRegionDefinition(styleUrl, bounds, 0.0, maxZoom, ratio)
        _state.value = OfflineState.Downloading(name, 0f, 0)
        runCatching {
            OfflineManager.getInstance(context).createOfflineRegion(def, name.toByteArray(Charsets.UTF_8), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                _state.value = OfflineState.Done(name, status.completedResourceSize)
                                refresh(context)
                            } else {
                                val req = status.requiredResourceCount.coerceAtLeast(1)
                                _state.value = OfflineState.Downloading(name, (status.completedResourceCount.toFloat() / req).coerceIn(0f, 1f), status.completedResourceSize)
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            // Pojedyncze błędy kafelków są ponawiane przez MapLibre – nie przerywamy.
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            _state.value = OfflineState.Failed("tiles > $limit")
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    _state.value = OfflineState.Failed(error)
                }
            })
        }.onFailure { _state.value = OfflineState.Failed(it.message ?: "") }
    }

    fun dismiss() {
        if (_state.value !is OfflineState.Downloading) _state.value = OfflineState.Idle
    }

    fun deleteAll(context: Context) {
        runCatching {
            OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val list = offlineRegions.orEmpty()
                    if (list.isEmpty()) _areas.value = emptyList()
                    list.forEach { r ->
                        r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() = refresh(context)
                            override fun onError(error: String) = refresh(context)
                        })
                    }
                    _state.value = OfflineState.Idle
                }

                override fun onError(error: String) = Unit
            })
        }
    }

    /** Największe przybliżenie, przy którym obszar mieści się w limicie kafelków (od 12 do 16). */
    private fun maxZoomFor(south: Double, north: Double, west: Double, east: Double): Double {
        for (z in 16 downTo 12) {
            var total = 0L
            for (k in 0..z) total += tiles(k, south, north, west, east)
            if (total <= TILE_LIMIT * 0.8) return z.toDouble()
        }
        return 11.0
    }

    private fun tiles(z: Int, south: Double, north: Double, west: Double, east: Double): Long {
        val n = 1 shl z
        fun x(lon: Double) = floor((lon + 180.0) / 360.0 * n).toLong()
        fun y(lat: Double): Long {
            val r = lat * PI / 180.0
            return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n).toLong()
        }
        return (x(east) - x(west) + 1) * (y(south) - y(north) + 1)
    }
}
