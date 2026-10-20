# Pochodzenie kodu i zależności

## Przeniesione źródła

- `javity-backend/src`: [Radomiej/galaxy-rapid-engine](https://github.com/Radomiej/galaxy-rapid-engine), commit `c79b716e0b31af8cce68e9294fb33be9f310cb33`, katalog `galaxy-rapid-engine/src`. Upstream README deklaruje Apache License 2.0. Zachowano istniejące nagłówki; poprawki lokalne dotyczą libGDX, wejścia i zwalniania zasobów.
- `javity-map/src`: [Radomiej/JavityEngine-plugins](https://github.com/Radomiej/JavityEngine-plugins), commit `160fb40d0f887580509eae490a3b4930ebc765f0`, katalog `javity-map/src`. Upstream LICENSE: Apache License 2.0. Lokalnie poprawiono projekcję.
- Bazowy JavityEngine: commit `9e210b8cdf83a49f93496d63d162e7eb31761cc0`, Apache License 2.0.
- Nowy rdzeń jest implementacją inspirowaną kontraktem [Radomiej/java-lab](https://github.com/Radomiej/java-lab), a nie kopią browserowego runtime.

## Biblioteki

- libGDX: Apache License 2.0.
- Oficjalny libGDX `gdx-bullet:1.14.2` Java wrapper i `gdx-bullet-platform` bindings są objęte Apache License 2.0 libGDX; dołączone natywne Bullet upstream są na licencji Zlib (nie BSD). Weryfikacja exact-source korzystała z nagłówka Bullet `btKinematicCharacterController-1.14.2.cpp` w upstreamowym pakiecie źródłowym; zachowuj notice/licencję z opublikowanych JAR-ów i dystrybucji. Nie skopiowano vendored C++ ani zewnętrznych assetów do repozytorium. Avatar, voxel atlas i materiały demo są oryginalne.
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
- Graal polyglot/JavaScript community 25.0.2: licencje UPL/MIT i pozostałe licencje transitive dependencies w ich JAR-ach. Używamy js-community; wersje jawne w javity-script-graal/build.gradle. Graal nie jest zależnością providera Android.
- esbuild 0.25.0: MIT, wyłącznie narzędzie budowania JS/TSX; nie jest runtime silnika. package-lock.json określa zależności web/javity-ui.
- API animacji i feedbacku jest własną implementacją inspirowaną publiczną dokumentacją Animancer/Feel. Ich komercyjnego kodu, assets i Unity assemblies nie dołączono. Model/atlas demonstracyjny są generowane na potrzeby Javity.

## Fixture porównań i Relay Ward

`web/javity-ui-reference/k4-image-fixture.png`, dane wspólnych fixture oraz proceduralny dwukościowy model demonstracyjny są własnymi materiałami testowymi Javity. Relay Ward używa opisanych wyżej syntetycznych zasobów `runtime`, bez assets Feel/Animancer/Unity. Tailwind 4.3.3 jest narzędziem referencyjnego HTML (MIT); jego runtime ani przeglądarka nie są częścią natywnego HUD-u gry. Zależności referencji są zablokowane w `web/javity-ui-reference/package-lock.json`.

## Autorskie tekstury voxelowe

`javity-desktop/src/main/resources/org/javity/demo/voxels/atlas.png`, jego metadane i `tools/generate-voxel-atlas.py` to oryginalne materiały projektu. Generator jest deterministyczny i używa standardowej biblioteki Pythona; nie zawiera tekstur Minecraft/Mojang ani innych paczek assets. Warunki użycia grafiki i generatora znajdują się w dołączonym `org/javity/demo/voxels/LICENSE.txt`.

## Rzeczywiste dane Gorzowa Wielkopolskiego

`javity-desktop/.../maplibre-gorzow/gorzow.mbtiles` zawiera oryginalne payloady MVT z OpenFreeMap `planet/20261011_080001_pt`, schemat OpenMapTiles 3.16.0, pochodzące z OpenStreetMap. To dane osób trzecich pod ODbL 1.0, odrębne od autorskiego syntetycznego miasteczka. Widoczna atrybucja: © OpenStreetMap contributors · OpenMapTiles · OpenFreeMap. Pełny tekst ODbL i notice są w pakiecie mapy, a źródło tekstu licencji: `tools/licenses/ODbL-1.0.txt` (https://opendatacommons.org/licenses/odbl/odbl-10.txt).

Importer `tools/import-gorzow-map.py` ogranicza footprint do 38 kafelków z12–14 i zachowuje oryginalne MVT (jedynie deterministyczna kompresja gzip w MBTiles). `fixture-source.json` zapisuje URL konkretnej wersji, SHA-256 oryginalnych kafelków i lokalnych zasobów. Style dzień/noc są autorskie Apache-2.0; nie skopiowano stylu Liberty. Font Open Sans ma powyższą odrębną licencję, zachowaną w pakiecie.


## Zależności runtime Androida

- Oficjalny `com.facebook.yoga:yoga:3.2.1`: MIT, copyright Facebook/Meta i affiliates; natywne JNI Yoga jest odrębne od desktopowego LWJGL. `com.facebook.soloader:soloader`, `annotation` i `nativeloader` 0.10.5: Apache-2.0. `com.google.code.findbugs:jsr305:3.0.2` deklaruje Apache-2.0 w POM; cztery klasy `javax.annotation.concurrent` mają osobne nagłówki CC-BY-2.5, copyright 2005 Brian Goetz, zachowane w notices. Nie dołączono pełnego analizatora FindBugs. Ten graf nie dołącza fbjni.
- `io.github.dokar3:quickjs-kt-android:1.0.15`: wrapper Kotlin/JNI Apache-2.0, copyright 2024 dokar3. Dołączony silnik Bellard QuickJS `2026-06-04`, commit `04be246001599f5995fa2f2d8c91a0f198d3f34c`, jest na MIT (Fabrice Bellard i Charlie Gordon). Bridge używa również `eteran/c-vector`, commit `774773d4cb1e66dd736e90e7f482d4f22af464c6`, MIT, copyright 2015 Evan Teran. Licencja wrappera nie zastępuje obu licencji MIT.
- Kotlin stdlib 2.4.20, kotlinx.coroutines 1.11.0 oraz JetBrains annotations 23.0.0: Apache-2.0. Zachowano osobny upstream NOTICE kotlinx.coroutines. Kompilator Kotlin jest narzędziem budowania, nie biblioteką runtime APK.
- Pełne teksty tej grupy zależności, atrybucje oraz manifest źródeł/SHA-256 dołączono w `javity-android-app/src/main/assets/android/third-party/` (w APK `assets/android/third-party/`). Istniejące notices React/OneJS i fontu pozostają w zasobach swoich modułów. Informacje licencyjne i obecność bibliotek ARM w artefaktach nie są deklaracją zweryfikowanego działania na ARM.

Natywne libGDX 1.14.2 zawiera także stb_image (wybrana licencja MIT, Sean Barrett) i ETC1 utilities (Apache-2.0, Google). `gdx-freetype` 1.14.2 używa przypiętego FreeType 2.13.3, commit `42608f77f20749dd6ddc9e0536788eaad70ea4b5`; wybrano FreeType License (FTL). Portions of this software are copyright © 2024 The FreeType Project (www.freetype.org). All rights reserved. Zachowano też notices kompilowanych driverów BDF/PCF/hash i bundled zlib 1.3. Prebuilt Yoga zawiera runtime libc++/libc++abi/libunwind z NDK r27b; QuickJS zawiera helper compiler-rt z r28c. Pełne teksty z odpowiednich rewizji LLVM, wraz z LLVM exceptions, oraz atrybucje znajdują się w `assets/android/third-party/`. Systemowego Android libstdc++.so nie dystrybuujemy jako osobnego pliku APK.


## Fox — external animation validation fixture

`tools/animation-validation/fox/` contains the pinned Khronos glTF Sample Assets Fox and a derived offline G3DJ fixture. Model by PixelMannen (2014, CC0 1.0); rig/animations by tomkranis (2014, CC BY4.0); glTF conversion by AsoboStudio/scurest (2017, CC BY4.0). See [original legal notice](tools/animation-validation/fox/SOURCE.md), [attribution, license links and modifications](tools/animation-validation/fox/NOTICE.md), and revision/hash files there. The project code license does not replace these asset licenses. Captures retain visible attribution. Conversion adds generated normals and rearranges bind/vertex attributes without claiming general glTF import support.

## AssetManager failure cleanup bridge (libGDX 1.14.2)

`javity-assets/.../com/badlogic/gdx/assets/JavityAssetInternals142.java` and
`.../assets/loaders/JavityModelPreparation142.java` are original Javity adapters
based on a source audit of libGDX 1.14.2 `AssetManager`, `AssetLoadingTask`,
`AsyncExecutor`, and `ModelLoader`. libGDX is Apache-2.0, copyright 2011 libGDX
contributors (upstream AUTHORS); source: https://github.com/libgdx/libgdx/tree/1.14.2/gdx/src/com/badlogic/gdx.
The existing libGDX license attribution applies; https://www.apache.org/licenses/LICENSE-2.0.

The adapters access package/protected internals to recover registered assets,
remove abandoned dependency entries, and discard default ModelLoader's pending
ModelData, preserving the configured loaders and ordinary loading algorithms.
They do not use reflection or include a fork of those loaders. This is an
intentional version-pinned split-package dependency, unsupported under a future
JPMS modularization without further work; Android/class-loader packaging and
runtime require separate validation. Ownership and custom-loader limitations are
in [docs/asset-streaming.md](docs/asset-streaming.md).

## Authored local asset-streaming fixture

`javity-desktop/src/main/resources/org/javity/demo/asset-streaming/` contains
original JavityEngine signal-crate geometry and 64×64 signal/checker PNG textures,
authored 2026-10-16, copyright JavityEngine contributors, Apache-2.0 under the
repository LICENSE. No external model/texture was copied.
[Source and license notice](javity-desktop/src/main/resources/org/javity/demo/asset-streaming/NOTICE.md).
The standard libGDX model/texture loaders retain their existing attribution above.

## Local Colyseus multiplayer gateway

`web/local-multiplayer` depends on the official Colyseus npm packages `@colyseus/core@0.18.19`, `@colyseus/ws-transport@0.18.4`, `@colyseus/schema@5.0.40`, and `@colyseus/sdk@0.18.5`. Their published metadata and upstream Colyseus package notices identify MIT licensing. Express `5.2.1` is MIT. The SDK browser bundle is served from the installed package; no Colyseus source or compiled SDK copy is vendored into this repository. The exact dependency tree and registry integrity values are pinned by `web/local-multiplayer/package-lock.json`; license identifiers for all 98 lockfile packages are recorded in [the local multiplayer license inventory](docs/artifacts/local-multiplayer/npm-license-inventory.json). Preserve upstream notices when distributing npm dependencies.

## Scene lighting shader variant (libGDX 1.14.2)

`javity-core/src/main/resources/org/javity/render/scene-lighting.vertex.glsl`
and `scene-lighting.fragment.glsl` derive from libGDX 1.14.2
`graphics/g3d/shaders/default.vertex.glsl` and `default.fragment.glsl`.
The Apache-2.0 notice and existing bundled libGDX LICENSE/AUTHORS apply.
Local changes move scene lights to fragment evaluation, add finite point/spot
ranges and linear distance fog, retaining upstream skinning/material paths.
Legacy environments continue using the unmodified libGDX default sources.


## Experimental TeaVM Wasm GC spike

`experiments/teavm-wasm` uses TeaVM plugin/JSO 0.15.0 (Apache-2.0, https://github.com/konsoletyper/teavm), libGDX 1.14.2 (Apache-2.0), and `yoga-layout@3.2.1` (MIT, Meta/Facebook). Yoga is pinned in its own npm lockfile. The vendored Ammo.js Wasm/glue pair is from https://github.com/kripken/ammo.js at commit `c8779e60159d428f11ff1eb553dc9b6b6e2d317a` (zlib/Bullet). Preserve the experiment's `AMMO-LICENSE.txt`, `YOGA-LICENSE.txt`, `TEAVM-LICENSE.txt`, pinned upstream files and provenance hashes when distributing it. Generated TeaVM output is experimental; it is isolated from the default reactor and is not a supported browser backend. [Evidence and exact hashes](docs/teavm-wasm-spike.md).

`experiments/javity-wasm` uses `com.github.xpenatan.gdx-teavm`/`backend-web` 1.6.1 (Apache-2.0, https://github.com/xpenatan/gdx-teavm/tree/1.6.1), paired with libGDX 1.14.2 and TeaVM 0.15.0. Backend-generated output is not vendored; preserve the notices of gdx-teavm, libGDX, TeaVM and generated runtime dependencies when distributing a built app. [Build/runtime proof](docs/javity-wasm-rendering-spike.md).

## SDK platformy ORBIT

`web/orbit/vendor/orbit-sdk.global.js` jest przypiętym artefaktem własnego projektu użytkownika [Radomiej/nekko-launcher](https://github.com/Radomiej/nekko-launcher), commit `d4bb30d28ce8731c5974de3441b63c5bea08b46c`, źródła `packages/game-sdk/src`. SHA256 i ścieżka artefaktu: `web/orbit/vendor/provenance.json`; packer weryfikuje checksum przed użyciem. Repozytorium źródłowe jest prywatne i nie deklaruje odrębnej otwartej licencji SDK; integracja na zlecenie właściciela nie nadaje mu automatycznie licencji Apache-2.0 całego silnika. Przed publiczną redystrybucją należy określić warunki dystrybucji SDK z właścicielem. Adapter Javity i narzędzia pakowania powstały dla tej integracji.

## Opcjonalny importer glTF i shadery PBR

`javity-gltf` używa `com.github.mgsx-dev.gdx-gltf:gltf:2.3.0` z projektu
[mgsx-dev/gdx-gltf](https://github.com/mgsx-dev/gdx-gltf/tree/2.3.0), Apache-2.0.
Zależność pozostaje opcjonalna względem core, z przypiętym libGDX1.14.2.
Autorskie minimalne fixture glTF/GLB powstają przez
`javity-gltf/tools/generate-fixtures.py` i są udostępnione jako CC0;
`javity-gltf/src/main/resources/org/javity/gltf/fixtures/LICENSE.txt` zawiera
warunki. Nie zastępują licencji modeli zewnętrznych: rzeczywisty Fox zachowuje
istniejące `tools/animation-validation/fox/NOTICE.md` i źródłowe pochodzenie.

`ManagedMeshBindingsRecovery` is a version-checked libGDX1.14.2 compatibility
adapter for `Mesh` / `VertexBufferObjectWithVAO` CPU bookkeeping. After the host
rebuilds managed meshes, it clears retained VAO attribute locations and stale
bound flags for the current application, preserving borrowed mesh ownership and
geometry. The upstream Apache-2.0 license and existing notices apply. Android R8
keep rules retain only these pinned members. `DirectionalShadowPass` uses the
upstream optional gdx-gltf PBR depth shaders and the same license; no upstream
SceneManager or gameplay scene model is embedded.

`javity-gltf/.../RangePbrShader.java` rozszerza upstreamowy `PBRShader`2.3.0
i w runtime modyfikuje rozwinięte oficjalne źródło fragment shadera przez dwa
ściśle sprawdzane, jednokrotne punkty podmiany. Upstream przechowuje
`PointLightEx.range`, ale nie używa go w tym shaderze; lokalny adapter dodaje
uniform zasięgu i skończone wygaszanie Javity, zachowując upstreamowy falloff
dla nieograniczonych świateł legacy. Nie dołączono skopiowanego pełnego shadera
ani importera. Obowiązują Apache-2.0 i notices przypiętego gdx-gltf; zmiana
wersji wymaga ponownego audytu punktów podmiany i testów pikselowych.

The optional directional-shadow pass also version-checks and adjusts the pinned
upstream depth shader alpha-test comparison (`<` to `<=`) at runtime to agree
with the PBR color silhouette. Original upstream copyright/license continues to
apply; the local adjustment and its profile restrictions are in
`DirectionalShadowPass` and `docs/shadows.md`.

### Original foliage atlas and demo tree (2026-10-20)

`javity-core/src/main/resources/foliage/leaves-and-grass.png` is original authored
RGBA artwork, reproducible using `tools/generate-foliage-atlas.py` (Python/Pillow).
The atlas and generated demo tree geometry are dedicated under CC0 1.0 Universal;
no third-party source images or geometry were used. See the adjacent
`foliage/NOTICE.md` for layout, provenance and license URL.

`javity-gltf/src/main/resources/org/javity/gltf/fixtures/avatar.glb` is an original
CC0 1.0 Universal block humanoid fixture, generated by
`javity-gltf/tools/generate-avatar-fixture.py`. Its geometry, eight-joint skeleton,
rigid hand tool and `tool-salute` animation were authored for this repository;
no third-party character artwork or motion data is included. The adjacent
`fixtures/LICENSE.txt` states the dedication.
