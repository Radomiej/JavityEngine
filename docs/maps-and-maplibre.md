# Mapy geograficzne i MapLibre

## MapLibre Native

Jest już osobny `javity-maplibre`, oparty na rzeczywistym MapLibre Native FFI 0.202610.0. Czyta własne wektorowe MBTiles/MVT i style JSON, rysuje do współdzielonej tekstury libGDX, obsługuje lokalne sprites/glyphy, polskie etykiety i picking. Pierwszy adapter wymaga Linux x64 oraz jawnego systemowego EGL/GLES. [Format pakietu, API i dowody](maplibre-native.md).

Poniższa sekcja dotyczy lżejszego `javity-map-core` (raster). Historyczne rozważania architektury zachowano jako kontekst; wybór Native jest już wdrożony.

## Dostępny raster

`javity-map-core` jest opcjonalnym modułem dla nowego rdzenia. Web Mercator, raster XYZ 256 px, zoom 0–22; double dla projekcji, float dopiero przy rysowaniu. X zawija się na dateline, Y zatrzymuje na granicach Mercatora. MapView trzyma kamerę/viewport; kafelki są danymi, bez GameObject dla każdego kafelka. MapMarker i MapRoute są komponentami.

TileSource zwraca nowy Pixmap albo null; renderer zwalnia Pixmap po uploadzie. FileTileSource czyta `root/z/x/y.png`. MapRenderer posiada ograniczony LRU Texture i przywraca kolor Batch. Flush przed miss/eviction zabezpiecza tekstury nadal używane przez SpriteBatch. Zamykać renderer na wątku GL, poza aktywnym rysowaniem Batch. Source należy do wywołującego.

Caller rozpoczyna Batch z pikselową kamerą ortograficzną i wywołuje `render(game, view, batch)`. MapView używa ekranowego Y w górę; wejście libGDX Y w dół należy przeliczyć `height - inputY`. Marker/trasa używają GeoCoordinate, nie Transform2D. Renderer rysuje najbliższą kopię markerów i krótkie segmenty przy dateline; nie obsługuje powtarzanych etykiet i złożonych wielokątów.

Brak kafelka nie trafia do GPU cache. FileTileSource jest synchroniczny i lokalny. AsyncTileSource/TileByteCache/HttpTileFetcher realizują nieblokujące poll z ograniczeniem pending/workers, cache dyskowym, stanem missing/error, retry i cancellation. Konfiguracja URL, User-Agent i atrybucji jest jawna; limity i lokalny HTTP sprawdzono w testach. Wybierz limit cache większy od liczby widocznych unikalnych kafelków, żeby uniknąć przeładowywania. Cache jest liczbą tekstur, nie budżetem bajtów; 64 RGBA256 to około 16 MiB samych pikseli.

`--map` uruchamia demo syntetycznych kafelków, nie rzeczywistych ulic; nie pobiera nic z internetu. `:javity-desktop:mapSmoke` sprawdza rendering, markery, cache, pan/zoom/dateline, wyłączenie widoku, resize i polskie glyphy.

## Czy możemy mieć style jak MapLibre?

Tak, ale pełny styl JSON MapLibre obejmuje sources, layers, expressions, filtry, glyphy/sprites, etykiety i geometrię wektorową. Kolorowanie gotowego rastrowego PNG nie zapewni tych funkcji.

Rozważane drogi:

1. **MapLibre Native** jako osobny backend map renderujący do tekstury: zachowuje ekosystem stylów i wektorów. Desktop wymaga zbadania JNI, buildów natywnych, własności kontekstu/zasobów GPU i sposobu eksportu framebuffer/texture. Spike został wykonany; działający wariant opisuje maplibre-native.md. Android ma własny SDK; nie oznacza to gotowego bindingu Java desktop/libGDX.
2. **MapLibre GL JS w WebView/CEF**: łatwiej przenieść webowy kod użytkownika, ale dodatkowy runtime browsera, powierzchnia renderowania i most wejścia/danych utrudniają wspólne sceny 2D/3D i dystrybucję.
3. **Własny parser stylów/wektorowy renderer libGDX**: pełna kontrola, najwyższy koszt; nie rekomendujemy implementowania pełnej zgodności ze specyfikacją w ramach obecnej migracji.

Dla szybkiego efektu można używać kafelków rastrowych wygenerowanych ze stylu przez zewnętrzny serwer; styl/etykiety są wtedy ustalone po stronie dostawcy, bez pełnej interaktywności MapLibre. Obecny TileSource pozwala wymienić takie źródło bez zmiany gameplay.

Źródła sprawdzone 2026-10-13:
- [MapLibre Style Spec](https://maplibre.org/maplibre-style-spec/).
- [MapLibre Native](https://github.com/maplibre/maplibre-native) — natywny renderer C++, platformy desktop/mobile, BSD-2-Clause.
- [MapLibre GL JS](https://github.com/maplibre/maplibre-gl-js) — JS/WebGL.

Repo simulatora użytkownika nie zostało jednoznacznie wskazane. Nie zakładamy, że jego kod jest dostępny w publicznym java-lab.

## Provider HTTP i cache

```java
HttpTileFetcher http = new HttpTileFetcher(urlTemplate, userAgent, attribution);
TileByteCache bytes = new TileByteCache(http, cacheDirectory, 2, 16, 512, 10_000);
AsyncTileSource source = new AsyncTileSource(bytes);
MapRenderer renderer = new MapRenderer(source, 64);
// Pokazuj source.attribution() w UI.
// Przy zamykaniu: renderer.dispose(); source.close();
```

urlTemplate ma `{z}/{x}/{y}` i HTTP(S), cacheDirectory to Path dedykowany providerowi/stylowi. Workers nie tworzą tekstur; dekodowanie Pixmap i upload odbywa się na GL thread. Limit odpowiedzi 1 MiB, połączenie 5 s, całe pobranie 10 s. Kafelek musi mieć 256×256. Błędny obraz jest odrzucany i ma backoff; close anuluje zadania i zamyka client. Disk cache ma limit liczby wpisów, bez TTL. Szczegóły: finish-update.md.
