# OpenTraceTrack

<p align="center">
  <img src="artwork/open-trace-track-mark.png" alt="OpenTraceTrack" width="176" />
</p>

**Prędkościomierz z HUD-em jak w samolocie i rejestrator tras GPS dla Androida** – nagrywa przejazd z podziałem na środki transportu i zapisuje go w uniwersalnym GPX, który otworzysz w Stravie, Garmin Connect, Komoot, OsmAnd czy QGIS.

[![Build & Release](../../actions/workflows/android.yml/badge.svg)](../../actions/workflows/android.yml)

*[English below](#english)*

## Pobieranie

Najnowsza wersja jest zawsze w **[Releases → Latest](../../releases/latest)**. Każdy merge do `main` buduje i publikuje nowe wydanie, a aplikacja sama proponuje aktualizację i instaluje ją bez przeglądarki.

1. Pobierz `OpenTraceTrack-x.y.z.apk` na telefon.
2. Otwórz plik i zezwól na instalację z tego źródła.
3. Wymagany Android 10 (API 29) lub nowszy.

## Funkcje

### HUD
| | |
|---|---|
| **Styl lotniczy** | Taśma prędkości z wektorem trendu i znacznikiem maksimum, taśma wysokości z prędkością pionową, taśma kursu (z GPS w ruchu, z kompasu w miejscu), ślad ruchu z ostatnich minut (kierunek jazdy w górę) z wektorem 30 s i pierścieniami zasięgu, przeciążenie, stan GPS. |
| **Styl prosty** | Duże cyfry na łuku prędkości, wysokość, prędkość pionowa i kurs. |
| **Odbicie na szybie** | Tryb lustrzany – połóż telefon pod szybą samochodu, a HUD odbije się czytelnie. |
| **Kolory i poświata** | Biel, zielony fosfor, bursztyn, cyjan; miękki blask jak na prawdziwym wyświetlaczu. |
| **Jednostki** | Metryczne, imperialne i lotnicze/morskie (węzły, stopy, mile morskie). |

### Nagrywanie
- Usługa w tle z powiadomieniem (pauza, znacznik), ekran zawsze włączony, blokada przed przypadkowym dotykiem.
- **Środki transportu w jednym nagraniu**: pieszo, wędrówka, bieg, rower, hulajnoga, motocykl, samochód, autobus, tramwaj, pociąg, samolot, łódź, narty, inne. Zmiana zaczyna nowy fragment.
- **Wykrywanie przesiadek** po prędkości: podpowiedź albo automatyczny podział, cofnięty do chwili, gdy zmiana faktycznie nastąpiła.
- Automatyczna pauza na postojach, filtr dokładności, odstęp punktów 1–10 s, barometr, ostrzeżenie o przekroczeniu prędkości.
- Nagranie przerwane przez system można wznowić – punkty zapisują się do bazy na bieżąco.

### Trasy
- Mapa (MapLibre; podkład ciemny, jasny lub kolorowy) z trasą w kolorach środków transportu.
- Statystyki, profil prędkości i wysokości – przesuwasz palcem po wykresie, punkt pokazuje się na mapie.
- Edycja fragmentów: zmiana środka transportu, podział w wybranym miejscu, łączenie, usuwanie.
- Znaczniki z nazwą i opisem, notatki, ulubione, wyszukiwanie, filtry, sortowanie, zaznaczanie wielu tras.
- Statystyki ogólne: okresy, środki transportu, ostatnie 12 miesięcy, rekordy.

### Eksport i dane
| Format | Zawartość |
|---|---|
| **GPX 1.1** | Fragment = `<trk>` z `<type>` (walking, cycling, driving, train…), pauzy = osobne `<trkseg>`, prędkość i kurs w standardowym rozszerzeniu Garmin TrackPointExtension v2, liczba satelitów w `<sat>`. Dokładność, ciśnienie i podsumowania w przestrzeni nazw `gpxreg` – inne programy je pomijają. |
| **GeoJSON** | Feature na fragment (MultiLineString), czasy w `coordTimes`, prędkości, kolor i statystyki we właściwościach. |
| **KML** | Dla Google Earth – `gx:Track` z czasem każdego punktu. |
| **CSV** | Wiersz na punkt: czas, współrzędne, wysokość, prędkość (m/s i km/h), kurs, dokładność, satelity, ciśnienie. |

Podział przy eksporcie: **jeden plik**, **plik na fragment** (każda przesiadka osobno) albo **plik na środek transportu** (np. cały rower razem).

**Import GPX** z dowolnej aplikacji – także przez „Otwórz w” / „Udostępnij”. Typ aktywności jest rozpoznawany, brakujące prędkości wyliczane. Plik wyeksportowany z OpenTraceTrack wraca 1:1 i nie dubluje się.

**Baza** to zwykły plik SQLite z czytelnym schematem (`tracks`, `segments`, `points`, `waypoints`, `meta`; czas w ms UTC, wartości w jednostkach SI). Kopia zapasowa to jeden plik, który otworzysz też w DB Browser for SQLite. Wczytanie kopii scala trasy po UUID – istniejące są pomijane.

## Architektura

```
app/src/main/java/com/blacksingularities/gpxreg/
  data/     modele, baza SQLite (TrackDb), statystyki (StatsAccumulator), ustawienia, jednostki
  record/   LocationHub (GNSS bez Usług Google), SensorHub (horyzont, barometr), Telemetry,
            Recorder (filtr, autopauza, fragmenty), ModeDetector, RecordingService
  io/       GPX (zapis/odczyt), GeoJSON, KML, CSV, DataPort (pliki, udostępnianie, kopia bazy)
  update/   aktualizacje z GitHub Releases (PackageInstaller)
  ui/       Compose: HUD (Canvas), mapa (MapLibre), ekrany, motyw
  i18n/     teksty po polsku jako klucze + słownik angielski
```

Kotlin · Jetpack Compose · Material 3 · MapLibre Native · bez Usług Google Play.

## Budowanie

Wymagany JDK 17+ i Android SDK (API 36):

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

Wydania podpisuje CI kluczem z sekretów repozytorium (`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`). Aktualizacje w aplikacji działają tylko między wersjami podpisanymi tym samym kluczem – bez sekretów CI podpisuje kluczem testowym, który zmienia się przy każdym budowaniu.

---

## English

**An aircraft-style HUD speedometer and GPS track recorder for Android.** It records trips split by transport mode and saves them as universal GPX that opens in Strava, Garmin Connect, Komoot, OsmAnd or QGIS.

- **HUD**: aviation style (speed and altitude tapes, heading tape, motion trail (track-up) with a 30 s vector and range rings, vertical speed, G) or simple big digits. Four symbol colours, glow, and a mirror mode for windshield reflection.
- **Recording** in the background with a notification, auto-pause, accuracy filter, barometer, speed warning, and recovery after the system kills the app.
- **Multiple transport modes in one recording** (14 modes). Switching starts a new section. Speed-based detection can suggest a switch or split automatically, back-dated to the moment the change actually happened.
- **Track details**: map coloured by mode, statistics, speed/elevation profile linked to the map, section editing (change mode, split, merge, delete), markers, notes.
- **Export** to GPX 1.1, GeoJSON, KML or CSV – as one file, one file per section or one file per transport mode. **Import** GPX from any app.
- **Database**: a plain, documented SQLite file. Backup is a single file; restoring merges tracks by UUID without duplicates.
- **In-app updates** from GitHub Releases, with the change notes of every version you skipped.
- Polish and English UI (follows the system language or set it in Settings).

Download the APK from **[Releases → Latest](../../releases/latest)** (Android 10+).

## Licencja / License

MIT. Mapy: © OpenStreetMap contributors, CARTO, OpenFreeMap. Silnik map: [MapLibre Native](https://github.com/maplibre/maplibre-native) (BSD-2).
