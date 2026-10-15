# MapLibre w Javity — projekt backendu

Status: backend Linux x64 EGL wdrożony; rzeczywisty Native, MBTiles i porównanie GL JS opisuje [maplibre-native.md](../../maplibre-native.md).
Data: 2026-10-15.

## Cel zatwierdzony przez użytkownika

Własne mapy do gry i aplikacji, offline MBTiles z wektorami i oddzielnym stylem. Maksymalna praktyczna zgodność z MapLibre: możliwość użycia rzeczywistych danych, stylów i narzędzi tego ekosystemu. Nie zastępować tego kolorowaniem PNG ani własnym parserem udającym pełny MapLibre.

Nowe ustalenie zmienia wcześniejszą propozycję własnego podzbioru stylów: preferujemy rzeczywisty renderer MapLibre Native. Kompatybilność z GL JS jest ograniczona możliwościami wybranej wersji Native i bindingu; identyczność wszystkich funkcji i pikseli nie jest obietnicą.

## Rozpoznanie i wybór

1. Rekomendacja: opcjonalny backend MapLibre Native przez oficjalny projekt maplibre-native-ffi. Wykorzystuje silnik stylów, geometrię i etykiety upstream. Pozostawia nam integrację zasobów, wejścia, GPU i API Javity.
2. GL JS w CEF/WebView daje zgodność z silnikiem przeglądarkowym i jego rozszerzeniami, ale wymaga dystrybucji przeglądarki i osobnej integracji powierzchni. Nie jest automatycznym fallbackiem.
3. Własny renderer MVT/libGDX daje kontrolę, ale utrudnia osiągnięcie celu maksymalnej zgodności. Nie wybieramy tej drogi do pełnych stylów.

Odczytano publiczne README, instalację, kod przykładu LWJGL, wydania GitHub oraz metadane Maven Central. W momencie sprawdzenia najnowszy core release to core/v0.202610.0, a artefakt Maven również ma wersję 0.202610.0. To kandydat do przypięcia, nie potwierdzona integracja. Nie mieszamy przykładów z main z ABI wydania: implementacja musi korzystać z API i źródeł pasujących do przypiętej wersji.

FFI jest eksperymentalne, pre-1.0 i ma niestabilne ABI. README wskazuje brak advanced custom layers i shader registry. Gotowy przykład LWJGL zawiera owned/borrowed texture i native surface, EGL na Linuksie oraz WGL na Windows. Dokumentacja Native zawiera MBTilesFileSource i protokół mbtiles:// z absolutną ścieżką. Ich obecność i zachowanie trzeba potwierdzić w wybranym binarnym runtime.

## Granice modułów

- Istniejący javity-map-core pozostaje lekkim raster XYZ, bez wymuszonej zależności natywnej MapLibre.
- Nowy opcjonalny javity-maplibre ma publiczne API Java i ukrywa szczegóły FFI. Dopuszczalny mały wewnętrzny adapter Kotlin tylko jeśli binding JVM tego wymaga; gameplay pozostaje Java. Zwykłe JDK 25, bez GraalVM jako wymogu map.
- Backend desktopowy zaczyna od Linux x64. Windows/macOS/Android mają osobne kryteria uruchomienia, nie dziedziczą statusu PASS z Linuksa.
- Wersje bindingu i biblioteki natywnej są przypięte i zgodne. Żadnych zależności od pływających snapshotów. Licencje runtime i transitives trafiają do THIRD_PARTY oraz dystrybucji.

## Pakiet mapy offline

Katalog aplikacji:

```text
maps/town/
  map.json
  style.json
  town.mbtiles
  sprites/...
  glyphs/...
```

map.json jest małym manifestem Javity: wersja formatu, ścieżka stylu, jawne powiązania źródeł z lokalnymi MBTiles, początkowa kamera i atrybucja. Nie zastępuje stylu MapLibre. style.json pozostaje dokumentem specyfikacji MapLibre; jawnie wybrane odnośniki źródeł/zasobów są rozwiązywane dla lokalnego pakietu. Przy przeniesieniu katalogu nie pozostają zapisane absolutne ścieżki dewelopera.

MBTiles zawiera MVT i właściwości obiektów. PNG/JPEG może pozostać źródłem rastrowym, ale nie uzyska stylowania pojedynczych ulic. Uwzględnić tile_row TMS względem XYZ, kompresję kafelków i vector_layers w metadanych. Duże mapy są kafelkowane; nie tworzymy GameObject per feature/tile.

Offline oznacza brak żądań sieciowych także dla glyphów i sprite'ów. Pakiet otwierany read-only; brakujące pliki, niepoprawny styl i nieobsługiwane źródła zwracają konkretne błędy. Walidacja ścieżek zatrzymuje wyjście poza katalog pakietu. Sieciowe źródła wymagają jawnego trybu online; testy nie korzystają z OSM ani zewnętrznych tile servers.

## Renderowanie i własność

Mapa jest osobnym przebiegiem renderera, poza aktywnym SpriteBatch. UI i obiekty gry mogą być komponowane nad jej obrazem. Adapter posiada runtime, map handle, session i własne zasoby; kontekst okna i zasoby przekazane przez aplikację pozostają pożyczone. Pożyczonych uchwytów FFI nie zwalniamy jak zasobów Javity.

Najpierw test EGL/GLES kontra aktualny desktopowy kontekst libGDX. Wspólny kompatybilny kontekst lub poprawne współdzielenie tekstury jest preferowane. Przypięty runtime musi potwierdzić wymagania API, a test musi pokazać rzeczywiste piksele i UI libGDX po mapie. Nie zakładamy, że tekstura z osobnego kontekstu jest widoczna w libGDX. CPU readback może służyć wyłącznie diagnostyce i nie spełnia celu wydajnego renderowania interaktywnego.

Jeśli konteksty są niekompatybilne, zatrzymujemy integrację GPU z raportem pomiarów i wyborem EGL-compatible bootstrap albo osobnego backendu. Bez cichego przełączenia całego silnika na GLES, bez zastępowania MapLibre własnym parserem i bez deklarowania sukcesu na podstawie osobnego okna upstream.

Wątek renderujący wykonuje operacje GL; callbacki danych dostarczają wyniki przez kontrolowaną kolejkę. Resize uwzględnia logiczny viewport i framebuffer/DPI. Przywracamy wymagany stan GL przed libGDX, w tym framebuffer, viewport, program, VAO/buffery, tekstury, blend/depth/stencil/scissor. Zamknięcie jest idempotentne, także po błędzie inicjalizacji; session zamyka się przed teksturą i runtime.

## Publiczne możliwości pierwszej integracji

Otwórz lokalny pakiet, ustaw kamerę, pan/zoom, przełącz cały styl, zmień właściwość warstwy przez kontrolowany adapter. Kliknięcie odpytuje renderowane features i zwraca source/source-layer/id/properties. Jawne ograniczenia typów identyfikatorów i rozmiaru odpowiedzi. Współrzędne ekranowe oraz lon/lat konwertowane przez renderer, z udokumentowanym początkiem osi Y i DPI.

Źródła, warstwy, filtry, wyrażenia, dasharray, glyphy, sprite'y, feature-state, ekstrudowane budynki i zapytania są obsługiwane przez Native w zakresie udostępnionym przez przypięty runtime/binding. Każda funkcja ma oddzielny status testu. Nie implementujemy częściowego interpretera stylów w Javie jako zastępstwa braków bindingu.

Pierwszy pakiet używa geograficznego układu MapLibre. Fikcyjne miasteczko może mieć umowne położenie geograficzne; konwersja lokalnego XY wymaga jawnej skali i odwzorowania. Nie nazywamy dowolnego XY standardowym GeoJSON. Scena, komponenty, kolizje i stan rozgrywki pozostają poza MBTiles. Natywne obiekty 3D nad mapą i wspólna kamera perspektywiczna wymagają dalszej integracji, a nie samego renderowania mapy do tekstury.

## Demo i mierzalny odbiór

Autorskie dane małego miasteczka: woda, park, drogi z atrybutami, budynki, POI. MVT/MBTiles i dwa prawdziwe style JSON tej samej geometrii. Brak danych i tekstur Minecraft/OSM w fixture. Dołączony deterministyczny generator danych i informacje o pochodzeniu zasobów.

1. Uruchomienie Native i odczyt prawdziwych MBTiles z wybranego wydania.
2. Rzeczywista mapa w oknie libGDX z nakładką UI, pan/zoom, resize i cleanup bez błędów GL.
3. Styl dzienny/nocny, filtrowanie cech, wyrażenie zależne od właściwości/zoomu, droga z obrysem, linia przerywana, polygon z otworem, ikona i etykieta z polskimi znakami. Obsługa poza tym zestawem ma status upstream/unverified.
4. Wskazanie feature po identyfikatorze, zmiana stanu/stylu i zgodność geometrii obu stylów.
5. Testy bez sieci: wszystkie źródła/ikony/fonty lokalne; błędny pakiet i uszkodzony kafelek dają kontrolowany błąd bez wycieku.
6. Porównanie z GL JS: ten sam dataset, styl, viewport, kamera i zasoby. Screenshoty obu rendererów, osobny raport geometrii, kolorów, tekstu i AA; różnice wynikające z Native opisane. Brak arbitralnego stwierdzenia „100% kompatybilności”.
7. CPU tests manifestu/ścieżek/metadanych i pełny reactor; native smoke oraz instalowana dystrybucja poza checkoutem. Żaden nowy backend nie psuje istniejących map i Shapes.

## Etapy

A. Potwierdzić dopasowane wersje API/native i integrację kontekstu — twardy punkt decyzyjny.
B. Pakiet offline i fixture MBTiles, realny renderer, kamera i cleanup.
C. Adapter stylów/interakcji oraz porównanie z GL JS.
D. Dystrybucja, dokumentacja i jawna macierz zgodności.

Demo voxelowe i ogólne kreskowanie Shapes pozostają odrębnymi pozycjami poprzedniego celu. Dasharray MapLibre nie oznacza automatycznego wdrożenia dashed w Shapes.

## Źródła

- https://github.com/maplibre/maplibre-native-ffi — README i przykład examples/lwjgl-map.
- https://github.com/maplibre/maplibre-native-ffi/releases/tag/core/v0.202610.0
- https://repo.maven.apache.org/maven2/org/maplibre/nativeffi/maplibre-native-ffi/maven-metadata.xml
- https://maplibre.org/maplibre-native-ffi/install/
- https://maplibre.org/maplibre-style-spec/
- https://github.com/maplibre/maplibre-native — MBTilesFileSource.

Dokument jest specyfikacją, nie raportem testów. Dotąd wykonano jedynie rozpoznanie publicznych źródeł i wcześniejszy smoke obecnego renderera PNG. Nie zainstalowano zależności MapLibre ani nie wykonano testu jego renderowania.
