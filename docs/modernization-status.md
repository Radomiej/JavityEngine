# Status modernizacji

## Wykonane zmiany

- Gradle 9.8.1 z wrapperem i checksum, pełne JDK 25 LTS, libGDX 1.14.2, LWJGL 3.4.3.
- Galaxy Rapid i mapy budowane ze źródeł w tym repozytorium; bez snapshotów Sonatype.
- Nowy rdzeń bez Artemis: wiele komponentów typu, dziedziczenie, indeksy, tagi, pauza i stabilny cykl życia.
- Rendering sprite'ów oraz modeli OBJ/G3DJ/G3DB, odrębne transformacje 2D/3D, instancje modeli i kontroler animacji.
- Natywna Yoga dla Scene2D; utility classes, padding, gap, grow, wrap i zagnieżdżanie.
- UI: `Rectangle`, `rounded-*`, `border-*`, wspólny shader signed-distance, border uwzględniany przez Yoga i kontrola pikseli w smoke.
- `GameSystem` oraz opcjonalny Box2D nowego rdzenia: stały krok, dynamic/static/kinematic, koła/prostokąty, compound collidery, materiały, filtry i triggery.
- Bezpieczne callbacki kontaktu poza blokadą World, zmiany konfiguracji, budzenie ciał oraz zamykanie sceny z callbacku.
- Poprawki starego API libGDX, refleksji, projekcji map i zwalniania zasobów renderowania legacy.
- Nowe demo 2D/3D/UI oraz osobne demo legacy; AGENTS.md i dokumentacja.
- Opcjonalny javity-map-core bez legacy: XYZ offline, projekcja, cache GPU, markery/trasy i demo.
- Camera2D/FollowTarget2D i konwersje/kierunek do kursora; Open Sans FreeType z polskimi znakami.

## Zakres sprawdzenia

Testy nowego rdzenia obejmują wiele komponentów, dziedziczenie i indeksy, dodawanie podczas aktualizacji, niszczenie, pauzę i czas, izolację scen, tagi i blokadę ponownego podłączenia komponentu. Transformacje: rodzic z obrotem/skalą i blokada cyklu; modele: współdzielony zasób oraz niezależne instancje.

Yoga: padding/gap/grow, zagnieżdżanie i resize, przenoszenie/usuwanie elementów, wrap, nieznane klasy, border/radius i zamknięcie kontenera. Mapy: round-trip projekcji bez zmiany argumentu i kolejność osi. Refleksja: proxy/unproxy dziedziczonego pola publicznego.

Box2D: rzeczywiste biblioteki natywne bez OpenGL; grawitacja i lądowanie, stały krok przy różnym dzieleniu klatek, pauza, sensory, filtry, compound bodies i masa, usuwanie w kontakcie, teleport/active, dodanie w kolejnej klatce, izolacja scen, walidacja konfiguracji, budzenie uśpionego ciała, odpięcie callbacku i zamknięcie sceny podczas kontaktu.

Smoke graficzny sprawdza nowy rdzeń, 120 klatek, model z pliku, fizyczny sprite, kontakty z podłożem/triggerem, Yoga, kliknięcia pauzy/wznowienia i resize. Sprawdza niezmienność fizycznej pozycji przez pauzę oraz piksele narożnika, border i wypełnienia Rectangle. Osobny tryb legacy sprawdza pipeline Javity/Galaxy. Nie używa serwera map.

## Granice obecnego wyniku

- Nowy rdzeń i legacy współistnieją. Gry legacy wymagają migracji; raster map ma adapter nowego rdzenia; plugin online/GeoJSON pozostaje w legacy. Nowy Box2D jest już osobnym modułem.
- GLB/glTF/PBR i fizyka 3D pozostają do integracji. Nie sprawdzano eksportowanego modelu z animacją szkieletową.
- Yoga UI sprawdzono na Linux x64. Android/web/iOS oraz XML UI i generator nie są objęte nowym buildem.
- Katalog klas UI jest ograniczony; brak pełnego CSS/Tailwind i breakpointów. Opt-in pomiar zawijanego tekstu jest już dostępny; różnice metryk fontu opisuje docs/ui-html-parity.md. Rounded nie przycina dzieci i nie zmienia hitboxa.
- Mapy mają limitowany cache i lokalny provider; asynchroniczny HTTP provider/cache/atrybucja są wdrożone i testowane lokalnie; MapLibre Native vector styles są w opcjonalnym module Linux x64 EGL; zakres: maplibre-native.md.
- Zachowano historyczny Spine 3.5.51.1, Kryo 3.0.3 i zip4j 1.3.2 w legacy. Ich migracja wymaga osobnych testów zgodności.
- LWJGL używa legacy Unsafe memory backend na tym JDK i wypisuje ostrzeżenie o deprecacji; rzeczywisty start i rendering są sprawdzane oddzielnie od tego komunikatu.
- W starych źródłach istnieją testy demonstracyjne/puste; sam licznik testów nie jest miarą pokrycia.

Artefakty z ostatniej weryfikacji: raporty Gradle w `*/build/reports/tests/test`, dystrybucja w `javity-desktop/build/install/javity-desktop`, zrzut `smoke.png` po smoke. Szczegółowe polecenia: README.

## Wynik weryfikacji bazy — 2026-10-13, etap 1

- `./gradlew clean build :javity-desktop:installDist`: exit 0, BUILD SUCCESSFUL, 20 testów bez błędów (17 nowych testów regresji/kontraktów oraz 3 historyczne).
- Nowe demo `--smoke`: exit 0, 120 klatek, rzeczywisty OBJ, sprite, Yoga, kliknięcia pauzy/wznowienia i resize; WORLD=90, UI=120.
- Legacy `--legacy --smoke`: exit 0, 120 klatek, 120 aktualizacji komponentu i panel Yoga.
- Gradle Wrapper JAR: SHA-256 zgodny z oficjalnym checksum Gradle 9.8.1.
- Bytecode rdzenia: major version 69 (Java 25).
- Przegląd obrazu: `docs/demo-modern.png`.
- Zmiany lokalne na branchu `modernization/java25-gradle-yoga`; nie publikowano ich na GitHub.

## Wynik weryfikacji — 2026-10-13, etap 2

- Czysty build i dystrybucja desktop: BUILD SUCCESSFUL, exit 0; 40 testów, zero błędów i pominięć. W tym 13 natywnych testów Box2D i 10 testów Yoga/UI.
- `:javity-desktop:smoke`: exit 0; 120 klatek, 2D/3D, rounded/border/Rectangle, Box2D, pauza i resize. WORLD=90, UI=120, kontakty=3, triggery=1. Automatyczna kontrola pikseli narożnika, obramowania i wypełnienia przeszła.
- Legacy `--legacy --smoke`: exit 0; 120 klatek i 120 aktualizacji komponentu.
- Aktualny zrzut: `docs/demo-modern.png`. Zmiany pozostają lokalne, bez commit/push.

## Wynik weryfikacji — 2026-10-13, etap 3

- `clean build :javity-desktop:installDist`: exit 0, BUILD SUCCESSFUL; 51 testów, zero błędów i pominięć. W tym 8 nowych testów projekcji/kamery map/cache i 3 Camera2D/FollowTarget2D.
- `:javity-desktop:smoke`: exit 0, 120 klatek; 2D/3D, Camera2D zgodna z native projection, Yoga/rounded/border, Box2D i pauza/resize. WORLD=90, UI=120, kontakty=3, triggery=1.
- `:javity-desktop:mapSmoke`: exit 0, 120 klatek; synthetic XYZ offline, markery/trasy (kontrola pikseli), pan/zoom/dateline, disabled view, bounded cache/eviction, resize i polskie glyphy Open Sans. Końcowo 64 tekstury w cache, 124 uploady razem z testem cache o pojemności 1; dispose sprawdza opróżnienie cache.
- Legacy `--legacy --smoke`: exit 0, 120 klatek/aktualizacji, UI Yoga.
- Przejrzano obrazy: `docs/demo-modern.png` i `docs/demo-map.png`.
- Dokumentacja: TODO, inwentaryzacja API, kamery, MapLibre, Shapes/shadery i multiplayer. MapLibre/Shapes/multiplayer to plany, nie wdrożone backendy.
- Branch `modernization/java25-gradle-yoga`, bez commit/push.

## Funkcje uzupełnione przy zamknięciu aktualizacji bazy

Audio/Sound/Music, TextRenderer2D, figury/linie, atlas animation, input/picking, SceneManager/SceneTimers/SceneAssets, onRelease i cleanup po błędzie, prefab/SceneJson v1 (codeki physics/map), polygon/raycast/forces/impulses oraz asynchronous HTTP tiles/disk cache. Naprawiono Flex overlay input i legacy Spine path#skin. Nowe demo runtime i workflow CI są przygotowane. Szczegóły API i granice: finish-update.md.

## Zamknięcie aktualizacji bazy desktop — 2026-10-13

- Ostatni `clean build :javity-desktop:installDist`: exit 0, BUILD SUCCESSFUL; **78 testów**, zero failures/errors/skipped. Dodane regresje obejmują również ograniczenie liczby plików cache, przerwanie aktywnego downloadu i zerowy WORLD timer podczas pauzy.
- Na tych źródłach wykonano cztery Gradle smoke: modern, map, runtime i legacy. Każdy exit 0 i 120 klatek; wspólny BUILD SUCCESSFUL.
- Modern: WORLD=90/UI=120, kontakty=3/triggery=1, 2D/3D/Camera2D i piksele rounded/border.
- Map: rzeczywisty drag przechodzi przez InputMultiplexer po poprawce Flex; marker/route pixels, zoom/dateline, cache/eviction i resize; cached=64, uploads=124.
- Runtime: input/UI picking, atlas, tekst/figury, native Sound/Music, timers, prefab, polygon/raycast, async tiles, resize i dwie sceny. Test audio na OpenAL null driver, bez odsłuchu. Dispose potwierdza zamknięcie obu scen.
- Legacy: 120 aktualizacji komponentu i Yoga UI. Poprawki Spine path#skin sprawdzone oddzielnie w testach, bez deklaracji renderowania współczesnego Spine.
- Dystrybucja ZIP: `javity-desktop/build/distributions/javity-desktop-0.7.0-SNAPSHOT.zip`, integralność wpisów potwierdzona. Build/dystrybucja zweryfikowane na Linux x64, wymagane JDK25.
- Linki dokumentacji oraz YAML workflow sprawdzone lokalnie; GitHub Actions nie uruchamiano zdalnie.
- Aktualizacja bazowego silnika desktop zamknięta. Rozszerzenia i migracja konkretnych gier/platform/Spine są rozdzielone w TODO i finish-update.md. Zmiany lokalne, bez commit/push.

## Batch Shapes AA — 2026-10-14

- Własny renderer SDF: circle/rectangle/rounded/polyline, AA domyślnie, centered stroke, ROUND joins, BUTT/ROUND/SQUARE caps, WORLD/SCREEN; style w SceneJson v1.
- Agenci Luna wdrożyli backend, style i demo. Osobni reviewerzy Luna sprawdzili kontrakty i integrację; root nadzorował poprawki i uruchamiał weryfikację. Końcowy przegląd zatwierdzony po poprawkach GLSL, alpha oraz granic precyzji float.
- Końcowy clean build/installDist: BUILD SUCCESSFUL, **99 testów**, failures/errors/skipped=0.
- Pięć native smoke po 120 klatek: modern/map/runtime/shapes/legacy. Shapes sprawdza piksele AA/control, alpha bez kumulacji, caps/corners, SCREEN/WORLD, obrót/skalę/zoom/ruch kamery/resize i sentinele sprite/tekst/UI. Linux x64, Mesa/Xvfb; audio null driver.
- Podgląd: demo-shapes.png. API i ograniczenia: shapes-and-shaders.md. Polygon AA, bevel/miter i publiczne materiały nadal w TODO. To własna implementacja inspirowana Shapes, bez zależności Unity.
- Dystrybucja ZIP przebudowana i sprawdzona lokalnie. Workflow rozszerzony o shapesSmoke; zdalnego CI nie uruchamiano. Bez commit/push.

## Batch OneJS / animacje / feedback — 2026-10-14

- Agenci Luna wdrożyli i niezależnie przeglądali efekty, animacje, natywne UI, host React/GraalJS i integrację. Root koordynował poprawki oraz finalną weryfikację. Przegląd końcowy po jednej fali poprawek P1/P2: spec i jakość zatwierdzone.
- Działają `TweenPlayer`/`FeedbackPlayer`, scene-local leases, sequence/parallel/wait/callback, anulowanie i zegary WORLD/UI.
- `ModelAnimator`/`SpriteAnimationPlayer`: named clips, stan czasu/speed/pause, model crossFade, start/loop/complete. Dwie instancje modelu mają osobne kontrolery i pozy.
- `UiDocument`/markup, View/Text/Button/Progress/TextField/ScrollView, keyed reuse, bindingi, strict utility classes; UiTargets.alpha bez naruszania Yoga layout.
- Opcjonalny `javity-script`: GraalJS Community na zwykłym JDK25, React19/Reconciler0.31, TSX, hooki, schedulery, reload, named Java actions/primitive values. Publiczny OneJS MIT posłużył jako wzorzec hosta.
- Finalny `clean build :javity-desktop:installDist`: exit0, BUILD SUCCESSFUL; **160 testów JUnit**, failures/errors/skipped=0. Osobno `npm ci --offline`, build i **4 testy adaptera React**, exit0. Bundlowane JS odtworzone identycznie bajtowo ze źródeł.
- Finalne sześć smoke OpenGL: modern/map/runtime/shapes/plugins/legacy, każde exit0 i 120 klatek. Linux x64, Mesa/Xvfb, audio null driver. Plugins: Java/JS clicks=3, score=3, UI=128/WORLD=123 (dodatkowe kroki do sprawdzania zegarów), scrollY=40; GL_NO_ERROR również po disposal.
- Plugins sprawdza rzeczywiste kliknięcia przez multiplexer, controlled React state, primitive bridge, reload i cleanup starych timerów, key/focus/input/scroll, sprite speed/localpause/ONCE, progressed cancel/restore, alpha pixels podczas WORLD pause oraz same-viewport deformation pixels i macierze kości proceduralnej siatki. Dodatkowy przycisk React zamyka runtime i próbuje potem zmienić stan; native UI pozostaje puste.
- Podgląd `demo-plugins.png`; API: feedback-api.md, animation-api.md, declarative-ui.md, onejs-port.md. Zakres pozostałych systemów More Mountains: mm-systems-audit.md.
- ZIP dystrybucji przebudowany; workflow sprawdza także TSX bundling i pluginsSmoke. Nie uruchamiano zdalnego GitHub Actions. Zmiany lokalne, bez commit/push.
- Na koniec tego historycznego etapu springs/shake, timescale stack, audio buses, pooling i mixery były jeszcze w TODO. Późniejsze wdrożenia opisuje [aktualne pokrycie](plugin-feature-coverage.md); FFT i HD haptics nadal wymagają backendów. Proceduralny skinning nie jest walidacją importu produkcyjnego rigu.

## Feel / Animancer / OneJS — integracja końcowa, 2026-10-15

- Zakończono Radio/Signal/FSM, TimeManager, audio buses/ducking, floating text, sequencer, transakcyjne sceny, custom/resource feedback, presentation shake/punch/flash, animation seek/markers/transitions, rzeczywisty 1D mixer i masked override layers.
- Natywne React/Yoga: kontrolowane Toggle/Slider, klawiatura/focus, zawijanie tekstu z poprawnym pomiarem, obrazy zarejestrowane przez hosta i lokalny dev reload. GraalJS Community działa na zwykłym JDK25.
- Relay Ward `--mm` łączy te API; [przewodnik](game-authoring.md), [zakres i granice](plugin-feature-coverage.md).
- Końcowy czysty build/installDist/distZip: 335 testów Java, zero failures/errors/skipped. Node 10/10; trzy bundle deterministyczne. Po ostatnich ograniczonych poprawkach demo i fixture przebudowano pakiet oraz powtórzono dotknięte testy.
- Osiem trybów native PASS, rzeczywiste framebuffer/macierze: modern/map/runtime/shapes/plugins/ui-parity/mm/legacy. Audio OpenAL null, bez odsłuchu. MM zamyka rzeczywisty aktywny load oraz 8 żywych zasobów; 8/8 zwolnione, obie tekstury usunięte, GL0.
- Chromium/Tailwind: C1/K1/K2/K4/MM. Dla wspólnego HUD stałe actor boxes 0 px, końce każdego Progress do 1 px; amber/emerald trails zgodne. [Native](demo-mm.png), [HTML](demo-mm-html.png), [metryki i ograniczenia fontów](ui-html-parity.md).
- Installed MM/UI parity poza checkoutem PASS. Rzeczywista edycja dev.tsx bundle → syntax-error retention → recovery → nowa scena/health PASS. ZIP i zasoby/licencje sprawdzone; uruchomienie gry nie wymaga npm.
- Linux x64; inne platformy i pełne API Unity/DOM/CSS nie są deklarowane. FFT, HD haptics, root motion/IK i pełne 2D/additive model blending pozostają osobnymi tematami. CI rozszerzone, bez zdalnego uruchomienia; zmiany lokalne, bez commit/push.

## Materiały shaderowe Shapes — 2026-10-15

- ShapeMaterial2D: własna funkcja koloru GLSL1.20 z typowanymi FLOAT/VEC2/VEC4, gradient/pulse/stripes i niezależnymi wartościami instancji. ShapeShaderAnimator steruje uniformem w czasie WORLD/UI.
- SDF AA, rounded i stroke pozostają aktywne. Cache do 32 własnych definicji należy do renderera; CPU material nie posiada GPU. Nieużywane uniformy są bezpiecznie pomijane bez zmiany globalnego pedantic.
- Native probe wykrył i doprowadził do naprawy optymalizowanych uniformów, sprzątania błędnego GLSL w libGDX oraz gradientu w poprzek grubości polilinii. Testuje rzeczywiste glIsShader/glIsProgram po zamknięciu, również po błędach compile/link i przy limicie cache.
- Czysty build/installDist/distZip: 343 testy Java, 0 failures/errors/skipped. Dziewięć trybów native z nowym materials i legacy przeszło; gotowy materials/legacy działa także poza checkoutem. Audio pozostałych trybów: OpenAL null, bez odsłuchu.
- Demo `--materials`, test `:javity-desktop:materialsSmoke`. [API i ograniczenia](shapes-and-shaders.md), [screenshot](demo-shape-materials.png). Standardowy SceneJson zgłasza brak codec dla materiałów zamiast cicho je usuwać. Polygon materials, render targets i postprocessing nadal poza zakresem.
- Zmiany lokalne; bez commit/push i bez zdalnego uruchomienia CI.

## Voxele i chunki — 2026-10-15

- Opcjonalny `javity-voxel`: opaque bloki, chunki 16³, ujemne współrzędne, bounded load/unload, immutable snapshots, meshing widocznych ścian i DDA.
- Adapter GPU: Model/ModelRenderer na niepusty chunk, frustum AABB, budgeted FIFO rebuild, generacje load/unload, odtworzenie nad zachowanym światem, rollback po błędach, jawna własność modeli i pożyczony atlas.
- Demo `--voxels`, WASD/mysz, edycja bloków, oryginalny atlas/generator. [API](voxels.md), [screenshot](demo-voxels.png), [raport](voxel-verification.json).
- Finalny clean build/installDist/distZip i **371 testów Java PASS**,0 failures/errors/skipped. Native GPU/scena PASS37klatek,18loaded,8visible,0dirty,28rebuilds,10236faces; GL0 także po cleanup. Realne texele/occlusion i końcowe indeksy checkerboard potwierdzone pikselami.
- Rzeczywiste GLFW focus loss/regain oraz przypadki renderer close/recreate i unload/reload tej samej pozycji mają regresje. Poprzednie wersje wykazują oczekiwane błędy, aktualna przechodzi.
- Installed poza checkoutem PASS, screenshot identyczny; zasoby/licencje i CRC ZIP sprawdzone. Regresje modern/map/shapes/materials/legacy PASS. Linux x64/Mesa; inne platformy nie są deklarowane jako uruchomione.
- Streaming, greedy meshing, zapis, przezroczystość, oświetlenie blokowe, fizyka3D i multiplayer nadal osobno. MapLibre/MBTiles i dashed Shapes pozostają w backlogu. Zmiany lokalne, bez commit/push ani zdalnego CI.

## MapLibre Native — 2026-10-15

Opcjonalny `javity-maplibre` używa rzeczywistego Native FFI 0.202610.0, bez browsera i bez własnego parsera stylów. Zweryfikowano współdzieloną teksturę EGL/libGDX, autorskie offline MBTiles/MVT z gzip/TMS, lokalne sprites i glyphy Open Sans z polskimi etykietami. Kamera, rendered-feature query, feature-state, zmiany stylu, premultiplied alpha, resize/DPI z zachowaniem imperative edits i zwolnienie natywnej tekstury mają rzeczywisty smoke GPU. Adapter jest Linux x64; inne platformy i advanced GL JS custom layers/shaders nie są deklarowane.

Porównanie tej samej geometrii, stylów dzień/noc i kamery z MapLibre GL JS 6.14.0: ponad 99,6% pikseli w tolerancji RGB3, foreground IoU około 99%; pozostałe różnice głównie na krawędziach. To pomiar dwóch demonstracyjnych stylów, nie całej specyfikacji. [API i ograniczenia](maplibre-native.md), [porównanie](maplibre-comparison.png), [metryki/hash](maplibre-render-comparison.json), [dowód native](maplibre-verification.json).

Końcowa lokalna weryfikacja: `clean build installDist distZip` oraz 10 trybów native (MapLibre i dotychczasowe modern/map/runtime/shapes/materials/voxels/plugins/ui-parity/mm) przeszły; 380 testów JUnit, 0 błędów/skips, dodatkowo 7 testów generatorów Python. Zainstalowany launcher MapLibre i legacy uruchomiono poza checkoutem w katalogach ze spacjami; ZIP CRC poprawny, brak pełnego ANGLE/ANGLELoader w runtime. Workflow CI rozszerzono, ale zdalnego CI nie uruchamiano.
