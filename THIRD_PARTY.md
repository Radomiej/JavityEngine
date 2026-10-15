# Pochodzenie kodu i zależności

## Przeniesione źródła

- `javity-backend/src`: [Radomiej/galaxy-rapid-engine](https://github.com/Radomiej/galaxy-rapid-engine), commit `c79b716e0b31af8cce68e9294fb33be9f310cb33`, katalog `galaxy-rapid-engine/src`. Upstream README deklaruje Apache License 2.0. Zachowano istniejące nagłówki; poprawki lokalne dotyczą libGDX, wejścia i zwalniania zasobów.
- `javity-map/src`: [Radomiej/JavityEngine-plugins](https://github.com/Radomiej/JavityEngine-plugins), commit `160fb40d0f887580509eae490a3b4930ebc765f0`, katalog `javity-map/src`. Upstream LICENSE: Apache License 2.0. Lokalnie poprawiono projekcję.
- Bazowy JavityEngine: commit `9e210b8cdf83a49f93496d63d162e7eb31761cc0`, Apache License 2.0.
- Nowy rdzeń jest implementacją inspirowaną kontraktem [Radomiej/java-lab](https://github.com/Radomiej/java-lab), a nie kopią browserowego runtime.

## Biblioteki

- libGDX: Apache License 2.0.
- LWJGL i bindingi Yoga: BSD; sam Yoga: MIT. Obowiązują licencje upstream.
- Artemis ODB: BSD 2-Clause oraz Apache License 2.0 dla kodu refleksji pochodzącego z libGDX.
- Spine: osobna licencja Spine Runtimes. Korzystamy z opublikowanego `com.esotericsoftware.spine:spine-libgdx:3.5.51.1`, aby odtworzyć stare API. Nie zmieniamy warunków tego runtime ani nie deklarujemy zgodności ze współczesnymi eksportami.
- Pozostałe zależności oraz ich wersje są jawnie wymienione w `build.gradle`. Zachowuj nagłówki i informacje licencyjne przy dalszej migracji.

## Dołączona czcionka

Open Sans Regular TTF, digitized data copyright (c) 2010–2011 Google Corporation, Apache License 2.0. Źródło: https://www.opensans.com/, plik dostarczony w pakiecie fonts-open-sans. TTF, pełna licencja i notice znajdują się w javity-ui/src/main/resources/org/javity/ui/fonts. Font generowany w runtime przez libGDX FreeType.

Shapes pozostaje referencją: nie dołączono kodu ani assets Unity Shapes.

## MapLibre Native i referencja GL JS

- `org.maplibre.nativeffi:maplibre-native-ffi-jvm` oraz runtime OpenGL `0.202610.0`: rzeczywisty renderer Native, nie port własnego parsera. Dokładny runtime `natives-linux-x64` zawiera BSD 2-Clause FFI i notices upstream MapLibre Native/dependencies. Zachowano pełne notices w `javity-maplibre/src/main/resources/META-INF/licenses/maplibre-native-0.202610.0/` i w oryginalnym runtime JAR. README upstream FFI mówi BSD-3, ale LICENSE dokładnego artefaktu mówi BSD-2; dystrybucja zachowuje tekst z przypiętego artefaktu.
- `gdx-lwjgl3-angle:1.14.2`: Apache-2.0. Zadanie `glesAdapterJar` pakuje wyłącznie **niezmienioną** klasę `Lwjgl3GLES20` z oficjalnego JAR; pomija `ANGLELoader` i natywne sterowniki ANGLE, aby współdzielony kontekst korzystał z systemowego EGL. Pełna licencja i informacja o pochodzeniu w `META-INF/licenses/libgdx-adapter/`.
- Kotlin compiler/stdlib `2.4.20`: Apache-2.0; wewnętrzny most obsługuje JVM inline value classes FFI. Publiczne API Javity jest javowe.
- MapLibre GL JS `6.14.0`: BSD-3-Clause i notices transitive dependencies w zablokowanym pakiecie npm `web/maplibre-reference`. Wyłącznie referencja developerska, nie browser/runtime dołączony do gry.
- MBTiles/MVT, ikony i nazwy demo są autorskie i objęte licencją projektu Apache-2.0; nie pobrano danych OSM. `tools/generate-maplibre-town.py` zapisuje pochodzenie i hashe pakietu.
- Glyphy SDF generowane z powyższego Open Sans TTF; zachowano jego licencję/notice w module oraz pakiecie mapy. Generatory i narzędzia porównania to własne implementacje; nie kopiują TinySDF ani komercyjnych pluginów.

## Przykład runtime

`javity-desktop/src/main/resources/runtime`: syntetyczny avatar/atlas, kafelek i krótkie WAV wygenerowane na potrzeby projektu; nie pochodzą z pakietów TestApp. Wzorce TestApp (move/input, delayed sound, lifecycle, scene chooser) odtworzono w nowym API. Nie kopiowano assetów Spine/music/skin z TestApp.

## Port UI i runtime JavaScript

- Host UI/React jest adaptacją architektury [Singtaa/OneJS](https://github.com/Singtaa/OneJS/tree/f36ff5e41a16eeddf40e775f28fa7eee856894a0) (3.9.8, MIT, copyright 2024 Singtaa) oraz [onejs-react](https://github.com/Singtaa/onejs-react/tree/51cb714bcedaf5f6adc0bf2601db2920ae19e40d) (0.4.0, MIT deklarowane w package.json). Unity/C# bridge zastąpiono natywnym hostem Javity; nie dystrybuujemy Unity ani jego assets. OneJS MIT i atrybucja w web/javity-ui/LICENSE oraz zasobach licenses modułu javity-script.
- React 19.0.0, React Reconciler 0.31.0 i Scheduler: MIT, copyright Meta Platforms, Inc. and affiliates. Bundle zachowuje komentarze licencyjne; pełne teksty z zablokowanych pakietów npm dołączone w javity-script/src/main/resources/org/javity/script/licenses.
- Graal polyglot/JavaScript community 25.0.2: licencje UPL/MIT i pozostałe licencje transitive dependencies w ich JAR-ach. Używamy js-community; wersje jawne w javity-script/build.gradle.
- esbuild 0.25.0: MIT, wyłącznie narzędzie budowania JS/TSX; nie jest runtime silnika. package-lock.json określa zależności web/javity-ui.
- API animacji i feedbacku jest własną implementacją inspirowaną publiczną dokumentacją Animancer/Feel. Ich komercyjnego kodu, assets i Unity assemblies nie dołączono. Model/atlas demonstracyjny są generowane na potrzeby Javity.

## Fixture porównań i Relay Ward

`web/javity-ui-reference/k4-image-fixture.png`, dane wspólnych fixture oraz proceduralny dwukościowy model demonstracyjny są własnymi materiałami testowymi Javity. Relay Ward używa opisanych wyżej syntetycznych zasobów `runtime`, bez assets Feel/Animancer/Unity. Tailwind 4.3.3 jest narzędziem referencyjnego HTML (MIT); jego runtime ani przeglądarka nie są częścią natywnego HUD-u gry. Zależności referencji są zablokowane w `web/javity-ui-reference/package-lock.json`.

## Autorskie tekstury voxelowe

`javity-desktop/src/main/resources/org/javity/demo/voxels/atlas.png`, jego metadane i `tools/generate-voxel-atlas.py` to oryginalne materiały projektu. Generator jest deterministyczny i używa standardowej biblioteki Pythona; nie zawiera tekstur Minecraft/Mojang ani innych paczek assets. Warunki użycia grafiki i generatora znajdują się w dołączonym `org/javity/demo/voxels/LICENSE.txt`.

## Rzeczywiste dane Gorzowa Wielkopolskiego

`javity-desktop/.../maplibre-gorzow/gorzow.mbtiles` zawiera oryginalne payloady MVT z OpenFreeMap `planet/20261011_080001_pt`, schemat OpenMapTiles 3.16.0, pochodzące z OpenStreetMap. To dane osób trzecich pod ODbL 1.0, odrębne od autorskiego syntetycznego miasteczka. Widoczna atrybucja: © OpenStreetMap contributors · OpenMapTiles · OpenFreeMap. Pełny tekst ODbL i notice są w pakiecie mapy, a źródło tekstu licencji: `tools/licenses/ODbL-1.0.txt` (https://opendatacommons.org/licenses/odbl/odbl-10.txt).

Importer `tools/import-gorzow-map.py` ogranicza footprint do 38 kafelków z12–14 i zachowuje oryginalne MVT (jedynie deterministyczna kompresja gzip w MBTiles). `fixture-source.json` zapisuje URL konkretnej wersji, SHA-256 oryginalnych kafelków i lokalnych zasobów. Style dzień/noc są autorskie Apache-2.0; nie skopiowano stylu Liberty. Font Open Sans ma powyższą odrębną licencję, zachowaną w pakiecie.
