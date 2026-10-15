# TODO

Priorytet użytkownika: zachować i migrować dotychczasowe funkcje, rozpakować lokalnie Galaxy Rapid i pluginy; nowe zaawansowane funkcje po stabilizacji bazy. Zmiany lokalne, bez publikacji.

## Zrobione

- [x] Galaxy Rapid z Gita w lokalnym module, Gradle/JDK 25/libGDX 1.14.2.
- [x] Rdzeń Game/GameObject/Component, indeksy, has/get/require, lifecycle i pauza.
- [x] Sprite 2D i modele OBJ/G3DJ/G3DB; hierarchia transformacji 3D.
- [x] Box2D: rigidbody, box/circle, compound, sensory, filtry i callbacki.
- [x] Yoga UI, utility classes, Rectangle, rounded, border.
- [x] Czcionka Open Sans z polskimi znakami, FreeType TTF/OTF i wstrzyknięcie BitmapFont.
- [x] Mapy nowego rdzenia: raster XYZ, źródło lokalne, LRU tekstur, markery/trasy, pan/zoom i demo offline.
- [x] Camera2D: konwersje, zoom, obrót, gęstość jednostek, podążanie i kierunek do kursora.
- [x] Dokumentacja migracji i wstępny plan multiplayer/MapLibre/Shapes.

## Aktualizacja bazowego silnika desktop i zgodność legacy

- [x] Audio Sound/Music, własność zasobów i komponent odtwarzania.
- [x] TextRenderer w świecie, linie/figury dotychczasowego renderer'a; UI font już działa.
- [x] Input actions i picking/touch dla świata z uwzględnieniem UI.
- [x] Box2D: polygon, raycast, siły/impulsy i bezpieczne wyniki bez natywnych handles.
- [x] Zarządzanie scenami i zasobami, przejścia, task/timer oparty o czas sceny.
- [x] Prefaby i serializacja jawnego schematu, w tym opcjonalne codeki fizyki/map.
- [ ] Automatyczny konwerter dowolnych historycznych JSON/Kryo gier — stary loader zachowany w legacy; nowe sceny używają wersji 1 i jawnych codeców.
- [x] Mapy online: asynchroniczny provider HTTP(S), bounded pending/workers/disk cache, retry/missing, cancellation, limit danych i konfigurowalna atrybucja. Weryfikacja na lokalnym HTTP, bez publicznego OSM.
- [x] Atlas/animacje sprite w nowym API i smoke rzeczywistego atlasu.
- [x] Audyt Spine w TestApp: eksport 2.1.27, zachowany runtime 3.5.51.1; poprawka konfiguracji path#skin i regresje.
- [ ] Reeksport assetów Spine do wybranego współczesnego runtime i osobny adapter. Stare eksporty wymagają odpowiedniego narzędzia/projektu Spine; nie zastępuj tego deklaracją zgodności z nowymi eksportami.
- [x] Demo migracji wzorców TestApp: move/input, click, opóźnione audio, lifecycle, scene chooser, atlas/text/shapes, prefab i fizyka.
- [ ] Migracja całych konkretnych gier użytkownika oraz XML widoków — legacy zachowane, nie usuwamy go przy aktualizacji bazowego silnika desktop.

## Kolejne rozszerzenia

- [x] MapLibre Native FFI → współdzielona tekstura libGDX; style JSON/MVT/MBTiles, polskie etykiety, glyphy/sprites i picking. Linux x64 EGL; [zakres](maplibre-native.md).
- [ ] Potwierdzić repo użytkownika z MapLibre — wskazano simulator, ale bez jednoznacznego linku. Publiczne drzewo java-lab nie wskazało takiego projektu po nazwie.
- [x] Podstawowe line/polyline/circle/rectangle/polygon i fill/stroke w świecie.
- [x] SDF AA circle/rectangle/rounded/polyline, ROUND joins, BUTT/ROUND/SQUARE caps, WORLD/SCREEN i test pikseli GPU.
- [x] Shapes: własne materiały GLSL z typowanymi uniformami, gradient/pulse/stripes, ShaderAnimator WORLD/UI, cache i testy pikseli/zasobów GPU.
- [ ] Rozszerzenia Shapes: AA polygon, bevel/miter, clipping, render targets i postprocessing.
- [ ] GLB/glTF/PBR i testy eksportowanych modeli animowanych.
- [ ] Camera3D: dedykowane follow/orbit, wspólne helpery ray/picking i widoki ekranowe.
- [x] UI ScrollView i TextField w deklaratywnym hoście.
- [x] Porównanie bazowego UI z rzeczywistym HTML/Tailwind 4.3.3: wspólne fixture/font, metryki DOM, piksele stanów i obrys liter, natywny smoke. [Wyniki i ograniczenia](ui-html-parity.md).
- [x] UI: opt-in Text.wrap, jawne nowe linie i aktualizacja pomiarów Yoga po zmianie tekstu/fontu/szerokości; porównanie akapitów z Chromium.
- [x] UI: kontrolowane Toggle/Slider, klawiatura/focus i typowane akcje React.
- [x] OneJS workflow: npm dev, przeładowanie bundla na wątku UI, zachowanie widoku po błędzie składni; natywna próba gotowej paczki z edycją, błędem składni i odzyskaniem UI przeszła.
- [x] UI: obrazy contain/cover/stretch, zaokrąglone maski i regiony atlasu — porównanie natywne/HTML oraz osobne GPU próby atlasu/flip; przegląd zakończony.
- [ ] UI: ogólne clipping kontenerów, breakpointy i dalsze utility classes.
- [ ] Multiplayer: opis schematu synchronizacji i authority, headless serwer, snapshots, spawn/despawn, interpolacja i walidowane komendy.
- [ ] Android/web/iOS, generator i XML UI: build, bindingi Yoga/FreeType i uruchomienie na każdej platformie.

Wymaganie zamknięcia etapu: czysty build całego reactora, testy zachowania i faktyczne smoke GL, aktualizacja statusu. Licznik testów nie zastępuje pokrycia.

## Kryterium zakończenia aktualizacji bazy

Gradle/JDK/libGDX zaktualizowane, lokalne źródła Galaxy/pluginów w buildzie, nowy rdzeń i podstawowe odpowiedniki funkcji dostępne, kompatybilne legacy zachowane. Czysty build, testy i cztery rzeczywiste dema wymagane. Zakończenie tego zakresu nie oznacza automatycznej konwersji każdej gry, współczesnych eksportów Spine ani gotowych platform mobile/web. Szczegóły: finish-update.md.

## Inspiracje UI/animacje/efekty — wymagania 2026-10-14

- [x] Przegląd OneJS (opis użytkownika „OneUI”), Animancer i Feel oraz audyt dopasowania do obecnego rdzenia. [Kierunek i źródła](unity-inspired-api.md).
- [x] TweenPlayer + FeedbackPlayer: WORLD/UI, sequence/parallel/wait/callback, dzierżawy właściwości i cancel/reset. [Projekt pierwszego batcha](superpowers/specs/2026-10-14-feedback-design.md). [API](feedback-api.md).
- [x] ModelAnimator/SpriteAnimationPlayer: named clips, czas/speed/pause, model crossFade, start/loop/complete; testy rzeczywistej pozy. [API](animation-api.md).
- [x] Proceduralny model z dwiema kośćmi: skinning GPU, crossFade, bone matrix i zmiana pikseli przy stałym viewport.
- [ ] Import i walidacja produkcyjnego modelu animowanego — osobno od demonstracji proceduralnej.
- [x] Deklaratywne natywne UI: View/Text/Button/Progress/TextField/ScrollView, bindingi, keyed reuse i cleanup. [API](declarative-ui.md).
- [x] Opcjonalny GraalJS/React/TSX bridge do Scene2D/Yoga; hooki, schedulery, reload i jawne JavityGame actions/values. [Port OneJS](onejs-port.md). Ograniczony katalog props/stylów.
- [x] Additive 2D punch/shake z własnością offsetów, screen flash oraz per-voice audio/buses/ducking; testy kontraktów i niezależny przegląd. Współpraca na GPU/native audio została sprawdzona w Relay Ward, również przy zamknięciu z aktywnymi zasobami.
- [x] Animacje: transition/seek/markers, rzeczywisty 1D mixer, masked override layers i weight fades; testy kości i pikseli modeli. Bez additive layers, root motion i IK.

## Feel / MMTools — audyt zakresu

[Otwarta dokumentacja i mapowanie wszystkich 12 Other tools](mm-systems-audit.md).

- [x] Publiczny SPI custom/resource feedbacków: świeże instancje, start/update/complete/close, anulowanie i własność.
- [x] Springs/punch/shake, istniejące kanały/intensity/cooldown/probability oraz typed events/Radio/Signal i enum FSM.
- [x] Nakładane hit-stop/slow motion, audio buses/voice handles/ducking i pooling.
- [x] Delayed gain/loss progress bars oraz floating text pool.
- [x] Sequencer rytmiczny i asset-backed scene loading/fade/cancel/failure; przegląd kontraktów zakończony.
- [x] Relay Ward: grywalna integracja, porównanie wspólnego HUD z HTML, natywne próby audio/efektów/zmian scen oraz cały reactor i pakiet poza checkoutem.
- [ ] FFT/audio analyzer i haptics — wymagają danych PCM/backendu; nie są deklarowane jako gotowe.

## MapLibre — aktualizacja wymagań 2026-10-15

- [x] Maksymalna praktyczna zgodność: preferować rzeczywisty MapLibre Native zamiast własnego podzbioru stylów. Projekt: [backend MapLibre](superpowers/specs/2026-10-15-maplibre-backend-design.md).
- [x] Potwierdzić MapLibre Native FFI 0.202610.0: zgodne bindingi/runtime, MBTiles i renderowanie w kontekście libGDX (EGL kontra aktualny desktop GL).
- [x] Lokalny pakiet MVT/MBTiles + style JSON + sprites/glyphs, bez sieci; autorskie miasteczko i dwa style.
- [x] Interakcja features/state, porównanie screenshotów z GL JS i macierz zgodności; testowane Native + libGDX, dystrybucja poza checkoutem.
- [ ] Dalsze backendy MapLibre (Windows/macOS/Android), tryb sieciowy oraz advanced custom GL JS layers/shaders.
- [ ] Kreskowanie Shapes pozostaje osobnym zadaniem; MapLibre dasharray nie zastępuje tej implementacji.

## Moduł voxelowy — wymagania 2026-10-15

- [x] Opcjonalny javity-voxel do gier blokowych: [projekt modułu](superpowers/specs/2026-10-15-voxel-engine-design.md).
- [x] Dane chunków 16³, rejestr bloków, ujemne współrzędne, jawne load/unload i limity.
- [x] Meshing widocznych ścian, dirty queue z budżetem, edycje między chunkami i DDA.
- [x] Adapter GPU i własność modeli/atlasu, culling, demo --voxels oraz autorskie tekstury PNG.
- [x] Testy CPU, rzeczywiste piksele/cleanup GL i smoke instalowanej dystrybucji.
- [ ] Dalsze etapy po bazie: streaming, greedy meshing, zapis, oświetlenie i fizyka; brak deklaracji ich wdrożenia.

## Gorzów i porównanie z java-lab — 2026-10-15

- [x] Prawdziwe MVT OpenFreeMap/OSM Gorzowa, 38 kafelków z12–14, relokowalny offline MBTiles, lokalne glyphy z typograficzną interpunkcją i pełna atrybucja/ODbL.
- [x] Demo `--gorzow`, dzień/noc, pan/zoom, query geometrii/nazw, screenshoty i próba natywnego renderowania.
- [x] Audyt GameObject/Component względem przypiętego java-lab: [porównanie](java-lab-component-parity.md).
- [ ] Uzupełnić wygodne delegacje Component.getComponents/requireComponent/removeComponents; odpowiedniki już istnieją na GameObject.
- [ ] Rozważyć adapter MapLibre do cyklu życia GameObject, bez tworzenia obiektu na każdy kafelek/feature.
- [x] CharacterController2D: normalizowany ruch, lokalne world bounds i skróty Component (bez integracji Box2D).
- [x] Voxel FPS controller: grawitacja, skok, sprint, kolizje, ślizganie przy ścianie i granice załadowanych chunków.
- [ ] Wspólne space WORLD/SCREEN i GameObject UI wymagają osobnego projektu zachowania.
- [x] Gorzów: warstwy tunnel/ground/bridge, priorytet klas dróg, kolizje i polskie etykiety; porównanie Native/GL JS.
