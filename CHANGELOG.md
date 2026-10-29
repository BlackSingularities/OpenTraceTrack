## 1.0.3

## 1.0.5

- Dopasowane kolorystycznie tła ikon aplikacji i spójne zasoby graficzne.

- Nowa strona aplikacji z opisem funkcji.
- Jednolita informacja o autorze (Adam Rędzikowski · redzikowski.com) i przycisk „Postaw kawę”.
- Usunięte odnośniki do repozytorium z interfejsu.

## 1.0.2 — Transparent application icon

- Use the transparent application mark in launcher, splash and existing branding surfaces.
- Preserve alpha in legacy and adaptive Android icons; refresh themed icon resources.

# Zmiany

## 1.0.1
- Nowa nazwa: OpenTraceTrack (dawniej GPXreg). Trasy, ustawienia i kopie bazy zostają bez zmian.
- Nowa ikona aplikacji, ikona w powiadomieniach i ekran startowy.
- Ekran nagrywania: logo w górnym pasku, na tabletach HUD i mapa obok siebie.
- HUD: zamiast sztucznego horyzontu (zależnego od pochylenia telefonu) ślad ruchu z ostatnich minut – kierunek jazdy w górę, wektor 30 s i pierścienie zasięgu.
- Taśma kursu nie ucina cyfr przy krawędziach.

## 1.0.0
- Pierwsze wydanie OpenTraceTrack.
- HUD w stylu lotniczym: taśmy prędkości i wysokości, kurs, sztuczny horyzont z drabinką pochylenia i skalą przechylenia, prędkość pionowa, przeciążenie wzdłużne. Wariant prosty z dużymi cyframi, cztery kolory symboli, poświata i tryb lustrzany do odbicia na szybie.
- Nagrywanie trasy w tle z powiadomieniem (pauza, znacznik), automatyczna pauza na postojach, filtr dokładności i wznawianie nagrania przerwanego przez system.
- Zmiana środka transportu w trakcie nagrania (14 rodzajów) tworzy nowy fragment. Podpowiedź albo automatyczny podział po zmianie prędkości – cofnięty do chwili faktycznej przesiadki.
- Mapa z trasą w kolorach środków transportu (ciemna, jasna, kolorowa), znaczniki.
- Szczegóły trasy: statystyki, profil prędkości i wysokości z kursorem na mapie, edycja fragmentów (zmiana środka transportu, podział, łączenie, usuwanie), notatki.
- Eksport do GPX 1.1, GeoJSON, KML i CSV – jeden plik, plik na fragment albo plik na środek transportu. Import GPX z dowolnej aplikacji.
- Baza SQLite o czytelnym schemacie: kopia jednym plikiem i scalanie kopii bez dublowania tras.
- Statystyki: suma za okres, podział na środki transportu, ostatnie 12 miesięcy, rekordy.
- Aktualizacje z GitHub Releases bezpośrednio w aplikacji – z listą zmian wszystkich pominiętych wersji.
- Interfejs po polsku i angielsku.
