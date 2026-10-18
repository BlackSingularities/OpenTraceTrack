package com.blacksingularities.gpxreg.data

import com.blacksingularities.gpxreg.i18n.tr

/**
 * Środek transportu fragmentu trasy. [gpxType] trafia do znacznika `<type>` w GPX – używamy nazw
 * rozpoznawanych przez Stravę, Garmin Connect, Komoot i OsmAnd. [group] decyduje, kiedy wykrywanie
 * proponuje zmianę (autobus i samochód to ta sama grupa – nie ma sensu ich rozróżniać po prędkości).
 */
enum class TransportMode(
    val key: String,
    val gpxType: String,
    val color: Long,
    val group: ModeGroup,
) {
    WALK("walk", "walking", 0xFF7EE081, ModeGroup.FOOT),
    HIKE("hike", "hiking", 0xFFB5E48C, ModeGroup.FOOT),
    RUN("run", "running", 0xFFD4F542, ModeGroup.RUN),
    BIKE("bike", "cycling", 0xFF2EC4F1, ModeGroup.WHEELS),
    SCOOTER("scooter", "e_scooter", 0xFF5BE7C4, ModeGroup.WHEELS),
    MOTORCYCLE("motorcycle", "motorcycling", 0xFFFF8A3D, ModeGroup.VEHICLE),
    CAR("car", "driving", 0xFFFFD23F, ModeGroup.VEHICLE),
    BUS("bus", "bus", 0xFFFF9F1C, ModeGroup.VEHICLE),
    TRAM("tram", "tram", 0xFFF25F9C, ModeGroup.VEHICLE),
    TRAIN("train", "train", 0xFFB388FF, ModeGroup.RAIL),
    PLANE("plane", "flying", 0xFFFFFFFF, ModeGroup.AIR),
    BOAT("boat", "boating", 0xFF4D9DFF, ModeGroup.WATER),
    SKI("ski", "skiing", 0xFF9AD1FF, ModeGroup.WHEELS),
    OTHER("other", "other", 0xFFBDBDBD, ModeGroup.OTHER);

    val label: String
        get() = when (this) {
            WALK -> tr("Pieszo")
            HIKE -> tr("Wędrówka")
            RUN -> tr("Bieg")
            BIKE -> tr("Rower")
            SCOOTER -> tr("Hulajnoga")
            MOTORCYCLE -> tr("Motocykl")
            CAR -> tr("Samochód")
            BUS -> tr("Autobus")
            TRAM -> tr("Tramwaj")
            TRAIN -> tr("Pociąg")
            PLANE -> tr("Samolot")
            BOAT -> tr("Łódź")
            SKI -> tr("Narty")
            OTHER -> tr("Inne")
        }

    companion object {
        fun fromKey(key: String?): TransportMode = entries.firstOrNull { it.key == key } ?: OTHER

        /**
         * Rozpoznaje typ aktywności z cudzych plików GPX (Strava, Garmin, Komoot, OsmAnd, Locus…).
         * Nieznane wartości – [OTHER].
         */
        fun fromGpxType(type: String?): TransportMode {
            val t = type?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_') ?: return OTHER
            entries.firstOrNull { it.gpxType == t || it.key == t }?.let { return it }
            return when {
                t.contains("walk") || t == "9" -> WALK
                t.contains("hik") || t.contains("trek") || t.contains("mountaineer") -> HIKE
                t.contains("run") || t.contains("jog") || t == "1" -> RUN
                t.contains("cycl") || t.contains("bik") || t.contains("ride") || t == "2" -> BIKE
                t.contains("scoot") -> SCOOTER
                t.contains("motor") -> MOTORCYCLE
                t.contains("driv") || t.contains("car") || t.contains("auto") -> CAR
                t.contains("bus") -> BUS
                t.contains("tram") || t.contains("streetcar") -> TRAM
                t.contains("train") || t.contains("rail") || t.contains("metro") || t.contains("subway") -> TRAIN
                t.contains("fly") || t.contains("flight") || t.contains("plane") || t.contains("aviat") -> PLANE
                t.contains("boat") || t.contains("sail") || t.contains("kayak") || t.contains("row") || t.contains("ferry") -> BOAT
                t.contains("ski") || t.contains("snowboard") -> SKI
                else -> OTHER
            }
        }
    }
}

enum class ModeGroup { FOOT, RUN, WHEELS, VEHICLE, RAIL, AIR, WATER, OTHER }

/** Zapisana trasa (jedno nagranie) z podsumowaniem. */
data class Track(
    val id: Long,
    val uuid: String,
    val name: String,
    val notes: String,
    val startTime: Long,
    val endTime: Long,
    val stats: Stats,
    val favorite: Boolean,
    val recording: Boolean,
    /** Uproszczony przebieg trasy do miniatury na liście (kilkadziesiąt punktów). */
    val preview: List<LatLon> = emptyList(),
    val segments: List<Segment> = emptyList(),
)

/** Fragment trasy pokonany jednym środkiem transportu. */
data class Segment(
    val id: Long,
    val trackId: Long,
    val index: Int,
    val mode: TransportMode,
    val startTime: Long,
    val endTime: Long,
    val stats: Stats,
)

data class LatLon(val lat: Double, val lon: Double)

/** Punkt trasy – wszystko, co daje odbiornik GNSS i czujniki telefonu. */
data class TrackPoint(
    val time: Long,
    val lat: Double,
    val lon: Double,
    /** Wysokość nad poziomem morza (m). */
    val ele: Double? = null,
    /** Prędkość (m/s). */
    val speed: Double? = null,
    /** Kurs względem północy geograficznej (°). */
    val course: Double? = null,
    /** Dokładność pozioma (m, 68%). */
    val accuracy: Double? = null,
    /** Dokładność pionowa (m). */
    val vAccuracy: Double? = null,
    /** Satelity użyte do wyznaczenia pozycji. */
    val sats: Int? = null,
    /** Ciśnienie (hPa) z barometru. */
    val pressure: Double? = null,
    /** Pierwszy punkt po pauzie – od poprzedniego nie liczy się dystansu (nowy `<trkseg>`). */
    val gap: Boolean = false,
    val segmentId: Long = 0,
    val id: Long = 0,
)

data class Waypoint(
    val id: Long,
    val trackId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val ele: Double?,
    val name: String,
    val note: String,
)

/** Podsumowanie trasy lub fragmentu. */
data class Stats(
    /** Dystans (m). */
    val distance: Double = 0.0,
    /** Czas od pierwszego do ostatniego punktu bez pauz (ms). */
    val duration: Long = 0,
    /** Czas w ruchu (ms). */
    val movingTime: Long = 0,
    /** Prędkość maksymalna (m/s). */
    val maxSpeed: Double = 0.0,
    val ascent: Double = 0.0,
    val descent: Double = 0.0,
    val minEle: Double? = null,
    val maxEle: Double? = null,
    val points: Int = 0,
) {
    /** Średnia w ruchu (m/s). */
    val avgMovingSpeed: Double get() = if (movingTime > 0) distance / (movingTime / 1000.0) else 0.0

    /** Średnia z całego czasu (m/s). */
    val avgSpeed: Double get() = if (duration > 0) distance / (duration / 1000.0) else 0.0
}
