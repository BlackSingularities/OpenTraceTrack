package com.blacksingularities.gpxreg.data

import android.content.Context
import com.blacksingularities.gpxreg.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UnitSystem { METRIC, IMPERIAL, NAUTICAL }

/** Kolor symboli HUD: biel jak reszta interfejsu albo klasyczna zieleń / bursztyn wyświetlaczy. */
enum class HudColor(val argb: Long) { WHITE(0xFFFFFFFF), GREEN(0xFF6CFF8E), AMBER(0xFFFFC14D), CYAN(0xFF5CE1FF) }

/** Lotniczy – taśmy prędkości i wysokości, horyzont; prosty – wielkie cyfry jak w samochodzie. */
enum class HudStyle { AVIATION, SIMPLE }

/** Wykrywanie zmiany środka transportu na podstawie prędkości. */
enum class ModeDetection { OFF, SUGGEST, AUTO }

enum class MapStyle(val url: String) {
    DARK("https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"),
    LIGHT("https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"),
    STREETS("https://tiles.openfreemap.org/styles/liberty"),
}

enum class RecordView { HUD, SPLIT, MAP }

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val units: UnitSystem = UnitSystem.METRIC,
    val hudColor: HudColor = HudColor.WHITE,
    val hudStyle: HudStyle = HudStyle.AVIATION,
    val hudHorizon: Boolean = true,
    val hudGlow: Boolean = true,
    val mirror: Boolean = false,
    val recordView: RecordView = RecordView.SPLIT,
    val mapStyle: MapStyle = MapStyle.DARK,
    /** Odstęp między punktami (s). */
    val interval: Int = 1,
    /** Odrzucaj pozycje o dokładności gorszej niż (m). */
    val maxAccuracy: Int = 35,
    val autoPause: Boolean = true,
    /** Ile sekund bez ruchu, zanim nagrywanie się wstrzyma. */
    val autoPauseDelay: Int = 20,
    val modeDetection: ModeDetection = ModeDetection.SUGGEST,
    val defaultMode: TransportMode = TransportMode.WALK,
    /** Ostrzeżenie o przekroczeniu prędkości (m/s, 0 = wyłączone). */
    val speedAlert: Double = 0.0,
    val keepScreenOn: Boolean = true,
    val useBarometer: Boolean = true,
    val haptics: Boolean = true,
    val autoUpdate: Boolean = true,
    /** Wersja, której komunikat „Co nowego” już pokazano. */
    val seenVersion: String = "",
    /** Wersja pominięta przez użytkownika („Pomiń tę wersję”). */
    val skippedVersion: String = "",
)

/** Ustawienia w SharedPreferences, udostępniane jako StateFlow dla interfejsu i usługi. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("gpxreg_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    fun update(t: (AppSettings) -> AppSettings) {
        val n = t(_state.value)
        _state.value = n
        save(n)
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, def: E): E =
        prefs.getString(key, null)?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: def

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            language = enumOf("language", d.language),
            units = enumOf("units", d.units),
            hudColor = enumOf("hudColor", d.hudColor),
            hudStyle = enumOf("hudStyle", d.hudStyle),
            hudHorizon = prefs.getBoolean("hudHorizon", d.hudHorizon),
            hudGlow = prefs.getBoolean("hudGlow", d.hudGlow),
            mirror = prefs.getBoolean("mirror", d.mirror),
            recordView = enumOf("recordView", d.recordView),
            mapStyle = enumOf("mapStyle", d.mapStyle),
            interval = prefs.getInt("interval", d.interval),
            maxAccuracy = prefs.getInt("maxAccuracy", d.maxAccuracy),
            autoPause = prefs.getBoolean("autoPause", d.autoPause),
            autoPauseDelay = prefs.getInt("autoPauseDelay", d.autoPauseDelay),
            modeDetection = enumOf("modeDetection", d.modeDetection),
            defaultMode = TransportMode.fromKey(prefs.getString("defaultMode", d.defaultMode.key)),
            speedAlert = prefs.getFloat("speedAlert", d.speedAlert.toFloat()).toDouble(),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            useBarometer = prefs.getBoolean("useBarometer", d.useBarometer),
            haptics = prefs.getBoolean("haptics", d.haptics),
            autoUpdate = prefs.getBoolean("autoUpdate", d.autoUpdate),
            seenVersion = prefs.getString("seenVersion", d.seenVersion) ?: "",
            skippedVersion = prefs.getString("skippedVersion", d.skippedVersion) ?: "",
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("language", s.language.name)
            .putString("units", s.units.name)
            .putString("hudColor", s.hudColor.name)
            .putString("hudStyle", s.hudStyle.name)
            .putBoolean("hudHorizon", s.hudHorizon)
            .putBoolean("hudGlow", s.hudGlow)
            .putBoolean("mirror", s.mirror)
            .putString("recordView", s.recordView.name)
            .putString("mapStyle", s.mapStyle.name)
            .putInt("interval", s.interval)
            .putInt("maxAccuracy", s.maxAccuracy)
            .putBoolean("autoPause", s.autoPause)
            .putInt("autoPauseDelay", s.autoPauseDelay)
            .putString("modeDetection", s.modeDetection.name)
            .putString("defaultMode", s.defaultMode.key)
            .putFloat("speedAlert", s.speedAlert.toFloat())
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("useBarometer", s.useBarometer)
            .putBoolean("haptics", s.haptics)
            .putBoolean("autoUpdate", s.autoUpdate)
            .putString("seenVersion", s.seenVersion)
            .putString("skippedVersion", s.skippedVersion)
            .apply()
    }
}
