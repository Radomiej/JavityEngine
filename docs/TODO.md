# TODO

Aktualna kolejność nowych prac: [work-queue.md](work-queue.md). Audyt OneJS: [onejs-coverage-audit.md](onejs-coverage-audit.md).

Priorytet użytkownika: zachować i migrować dotychczasowe funkcje, rozpakować lokalnie Galaxy Rapid i pluginy; nowe zaawansowane funkcje po stabilizacji bazy. Modernizacja jest zmergowana i wypchnięta na master; strona GitHub Pages działa. Otwarte bramki pozostają jawnie zapisane poniżej.

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
- [x] CameraRig3D: kamera FPS/TPP z F5, follow za avatar, kolizyjny boom i sweep bliskiej płaszczyzny oraz ray/picking; oddzielny viewmodel. Dowody proceduralnego avataru i Bullet w docs/avatars.md oraz docs/physics3d.md.
- [ ] Ogólny edytorski orbit i dodatkowe warianty Camera3D pozostają rozszerzeniem poza kamerą postaci.
- [x] UI ScrollView i TextField w deklaratywnym hoście.
- [x] Porównanie bazowego UI z rzeczywistym HTML/Tailwind 4.3.3: wspólne fixture/font, metryki DOM, piksele stanów i obrys liter, natywny smoke. [Wyniki i ograniczenia](ui-html-parity.md).
- [x] UI: opt-in Text.wrap, jawne nowe linie i aktualizacja pomiarów Yoga po zmianie tekstu/fontu/szerokości; porównanie akapitów z Chromium.
- [x] UI: kontrolowane Toggle/Slider, klawiatura/focus i typowane akcje React.
- [x] OneJS workflow: npm dev, przeładowanie bundla na wątku UI, zachowanie widoku po błędzie składni; natywna próba gotowej paczki z edycją, błędem składni i odzyskaniem UI przeszła.
- [x] UI: obrazy contain/cover/stretch, zaokrąglone maski i regiony atlasu — porównanie natywne/HTML oraz osobne GPU próby atlasu/flip; przegląd zakończony.
- [ ] UI: ogólne clipping kontenerów, breakpointy i dalsze utility classes.
- [ ] Multiplayer: opis schematu synchronizacji i authority, headless serwer, snapshots, spawn/despawn, interpolacja i walidowane komendy.
- [ ] Pozostałe wsparcie Androida, web/iOS, generatora i XML UI: pełne sceny, API i lifecycle na wspieranych platformach. Ograniczony launcher Androida, Yoga JNI, QuickJS/React i fixture aplikacji mają już osobne wyniki; nie oznacza to pełnej gotowości platformy.

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
- [x] Przenośny host React/TSX do Scene2D/Yoga z providerem GraalJS na desktopie i jawnie wstrzykiwanym QuickJS na Androidzie; hooki, schedulery, reload i jawne JavityGame actions/values. [Port OneJS](onejs-port.md). To ograniczony host inspirowany OneJS, nie pełna zgodność; katalog props/stylów jest jawnie ograniczony.
- [x] Additive 2D punch/shake z własnością offsetów, screen flash oraz per-voice audio/buses/ducking; testy kontraktów i niezależny przegląd. Współpraca na GPU/native audio została sprawdzona w Relay Ward, również przy zamknięciu z aktywnymi zasobami.
- [x] Animacje: transition/seek/markers, rzeczywisty 1D mixer, masked override layers, niezależne weight fades oraz crossfade klipów bazowych i warstw z zamrożonym źródłem; testy kości/pikseli, pauzy, przerwań i callbacków. Zatwierdzone przy 548 testach oraz pluginsSmoke. Bez additive layers, root motion i IK.

## Feel / MMTools — audyt zakresu

[Otwarta dokumentacja i mapowanie wszystkich 12 Other tools](mm-systems-audit.md).

- [x] Publiczny SPI custom/resource feedbacków: świeże instancje, start/update/complete/close, anulowanie i własność.
- [x] Springs/punch/shake, istniejące kanały/intensity/cooldown/probability oraz typed events/Radio/Signal i enum FSM.
- [x] Nakładane hit-stop/slow motion, audio buses/voice handles/ducking i pooling.
- [x] Delayed gain/loss progress bars oraz floating text pool.
- [x] Sequencer rytmiczny i asset-backed scene loading/fade/cancel/failure; przegląd kontraktów zakończony.
- [x] Relay Ward: grywalna integracja, porównanie wspólnego HUD z HTML, natywne próby audio/efektów/zmian scen oraz cały reactor i pakiet poza checkoutem.
- [x] Jawny analyzer PCM/FFT (okna, pasma, RMS, smoothing, stereo downmix), bez dostępu do miksu Sound/Music. [API i granice](audio-analysis.md).
- [x] Kontrakt haptics: możliwości, walidacja, dzierżawy i UNSUPPORTED; rzeczywisty backend Android pozostaje etapem platformowym. [API](haptics.md).
- [x] Prezentacyjne kanały modeli/kamer 3D, additive particles GPU, world-only tint/desaturation/vignette i dashed Shapes; presety Feel 3D oraz zintegrowane demo zatwierdzone. Historyczne checkpointy desktopu z 2026-10-16: pełne buildy 614 i 638 testów, następnie 641 przed klasą FBO. Aktualny pełny build: 646 testów w 75 suite, zero failures/errors/skipped, 92 zadania w 26 s. W tym samym przebiegu przeszły presentation3dSmoke, postprocessSmoke (GL30 i GL20), avatarGl30Smoke i physics3dSmoke.

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
- [ ] Dalsze etapy po bazie: streaming, greedy meshing, zapis i oświetlenie. Opcjonalna fizyka chunków Bullet jest wdrożona osobno i opisana poniżej.

## Gorzów i porównanie z java-lab — 2026-10-15

- [x] Prawdziwe MVT OpenFreeMap/OSM Gorzowa, 38 kafelków z12–14, relokowalny offline MBTiles, lokalne glyphy z typograficzną interpunkcją i pełna atrybucja/ODbL.
- [x] Demo `--gorzow`, dzień/noc, pan/zoom, query geometrii/nazw, screenshoty i próba natywnego renderowania.
- [x] Audyt GameObject/Component względem przypiętego java-lab: [porównanie](java-lab-component-parity.md).
- [x] Delegacje Component.getComponents/requireComponent/removeComponents są dostępne; przekazują do scenowego GameObject bez dodatkowego ECS.
- [ ] Rozważyć adapter MapLibre do cyklu życia GameObject, bez tworzenia obiektu na każdy kafelek/feature.
- [x] CharacterController2D: normalizowany ruch, lokalne world bounds i skróty Component (bez integracji Box2D).
- [x] Voxel FPS controller: grawitacja, skok, sprint, kolizje, ślizganie przy ścianie i granice załadowanych chunków.
- [ ] Wspólne space WORLD/SCREEN i GameObject UI wymagają osobnego projektu zachowania.
- [x] Gorzów: warstwy tunnel/ground/bridge, priorytet klas dróg, kolizje i polskie etykiety; porównanie Native/GL JS.

## Android — integracja ograniczona i pozostałe bramki

- [x] Audyt zależności desktopowych i próba budowy diagnostycznego APK z Java25 przez D8/R8 9.5.26.
- [x] Ograniczony launcher/aplikacja i build AGP; testy aplikacji CPU 13/13 (w tym 4 regresje cleanup/fault fixture), Yoga JNI/ART 4/4, QuickJS/React JNI/ART 7/7 oraz smoke modelu i dotyku aplikacji na density 160 i 320 PASS (58.527 s i 58.899 s).
- [x] Rzeczywista utrata i odzyskanie EGL na tym samym wątku właściciela PASS (55.856 s), z kontrolą VAO, shaderów, modelu, UI, SDF, fontu i depth FBO. Poprawka pętli FBO ma potwierdzenie CPU i rzeczywistego native resume.
- [x] Zintegrowane i zatwierdzone poprawki ActivityFIX3 oraz responsiveFIX1. Na finalnym APK przy density 320 przeszły osobno dwa testy odtwarzania Activity: na pierwszym planie (62.812 s; [log](artifacts/android/app-activity-foreground-instrumentation.txt)) oraz po pauzie (56.209 s; [log](artifacts/android/app-activity-paused-instrumentation.txt)). To ograniczone potwierdzenie bramki Activity, nie wynik całego zestawu. Osobny test układu poziomego przy density 320 także przeszedł (53.573 s; [log](artifacts/android/app-landscape-instrumentation.txt)); sprawdził tę samą Activity/fixture, kompaktowy odstęp, obszary SDF, fizyczny target co najmniej 60 oraz przywrócenie portretu przy 54 logical. Dwa kolejne odizolowane testy finalnego APK przeszły: rzeczywista utrata/odzyskanie EGL na wątku właściciela (54.787 s; [log](artifacts/android/app-final-egl-instrumentation.txt)) oraz touch/model/React, multipointer i pełna generacja zasobów (63.666 s; [log](artifacts/android/app-final-touch-instrumentation.txt)). Wszystkie pięć przypadków aplikacji density 320 przeszło osobno.
- [x] Debug/test i R8 release build PASS (38 s, 235 zadań); świeże audyty debug/release APK PASS dla ABI arm64-v8a i x86_64, po 4 biblioteki native na ABI, ELF/ZIP 16 KiB i 29 notices ([debug](artifacts/android/app-debug-apk-audit.json), [release](artifacts/android/app-release-apk-audit.json)). Audyt potwierdza pakowanie ABI, nie uruchomienie na fizycznym ARM.
- [x] Weryfikacja native/distribution PASS (54 s), cztery fazy packaged-dev PASS oraz HTML K1/K2/K4/MM PASS.
- [x] Ograniczona weryfikacja runtime R8 release PASS na lokalnie podpisanej kopii APK: model, teren, zaokrąglony React UI, font i SDF widoczne; tap zmienił Java signal 0→1, HOME/foreground zachował wartość w tym samym PID 5774, a filtered logcat nie zawierał wyjątków runtime. [Weryfikacja](artifacts/android/app-release-runtime-verification.json), [ekran początkowy](artifacts/android/app-release-initial.png), [kliknięcie](artifacts/android/app-release-click.png), [wznowienie](artifacts/android/app-release-resume.png), [log uruchomienia](artifacts/android/app-release-launch.txt), [log wznowienia](artifacts/android/app-release-resume.txt), [logcat](artifacts/android/app-release-runtime-logcat.txt). Była to lokalna kopia do walidacji, nie publikacja release; unsigned output pozostał bez zmian i nie dodano APK ani klucza do repozytorium.
- [x] Pełny runner pięciu testów instrumentacyjnych density320: [run39046532187](https://github.com/Radomiej/JavityEngine/actions/runs/39046532187)5/5 PASS, zero błędów i pominięć, API35 x86_64 KVM; [zakres i historia](Bugs/2026-10-17-android-followup.md). Fizyczny ARM64 pozostaje niezweryfikowany.
- [ ] Pełny zestaw GLES/Shapes/MapLibre Android, fizyczny ARM64, pomiary wydajności na urządzeniu oraz pozostałe sceny/zasoby; w szczególności Android MapLibre i Bullet nie są częścią potwierdzenia pierwszego APK. Status i zakres dowodów: [android-status.md](android-status.md).

## Fizyka 3D i widok postaci — wymagania użytkownika

- [x] Slice A+B opcjonalnego Bullet (`gdx-bullet`): rigid bodies, box/sphere/capsule colliders, world-local contacts, osobne triggery, mutual 16-bit maski/materiale, copied closest ray/sphere/capsule queries, fixed step i cleanup; natywne testy CPU bez GL. Szczegóły i limity: [physics3d](physics3d.md).
- [x] Slice C1: opcjonalny `javity-voxel-physics3d`, wersjonowane snapshoty collision i ograniczone natywne kolizje chunków bez zależności od GPU renderera; 8 native CPU tests bez GL.
- [x] Slice C2: osobny natywny kontroler kapsułowy Bullet, bezpieczne callbacki, warp/input/state, skok/schody/pochyłości i gotowość pełnej omiatanej objętości; pełny reactor 529 testów, 65 w module fizyki i 8 bridge.
- [x] Slice C3: opt-in desktop demo, lazy Bullet debug draw, rzeczywisty native/GPU smoke i dokumentacja; zatwierdzone po niezależnym przeglądzie i powtórnym pełnym buildzie (530 testów) oraz physics3dSmoke. Android natives wymagają osobnej walidacji platformy.
- [x] Ręce/narzędzie w osobnym passie FPS, autorski ważony avatar i kamera TPP (F5), sloty/sockety, animacje locomotion/use/jump oraz konserwatywne kolizje kamery. Proceduralny rig sprawdzony na GPU; import produkcyjny pozostaje osobnym zadaniem.
- [x] Prezentacyjne kanały Feel dla kamery/modelu 3D, bez nadpisywania fizyki; presety spring/recoil/bob/FOV i zintegrowane demo są zatwierdzone.
- [x] Osobny adapter kolizji chunków; jeden bounded compound na chunk, bez rigid body na blok. Kontrakt readiness nie stanowi fizycznej granicy dla dowolnych ciał dynamicznych.
- Projekt do przeglądu: [physics3d-player-design](superpowers/specs/2026-10-15-physics3d-player-design.md). C1/C2/C3 i komplet desktopowego Task 5 są zatwierdzone. Android jest osobnym etapem i nie wynika z testów desktop/native CPU.

- [x] Priorytet avataru: oddzielne maski animacji/widoczności/render pass, helpery poddrzew, ARMS_ONLY/FULL_BODY, preparator wspólnej skinned mesh i izolacja widoków nad kanoniczną instancją. [Projekt](superpowers/specs/2026-10-15-avatar-layers-design.md). FPS+TPP potwierdzone przez użytkownika.

## Runtime UI / narzędzia sceny

- [x] Agent: audyt OneJS na podstawie źródeł/testów, 11 obszarów bez deklarowania pełnego DOM/CSS/Tailwind.
- [x] Pierwszy inspektor runtime: F1, GameObjecty, hierarchia Transform3D, komponenty, transformacje i live refresh; native Stage/smoke.
- [x] Typowana edycja Transform2D/Transform3D i undo/redo (256 wpisów), walidacja oraz blokady właścicieli ruchu. Testy CPU i native inspectorSmoke; edytory pozostałych komponentów pozostają rozszerzeniem.
- [x] Gizmos translacji/rotacji/skali 2D/3D, local/world i snapping, jedna operacja undo na gest. Nieprzedstawialne konwersje WORLD są jawnie odrzucane; wejście UI ma pierwszeństwo. Testy obejmują zoom, viewport, owner/parent guards i anulowanie.
- [x] Eksport runtime SceneJson z jawnymi codec/asset references, zagregowanym raportem i atomową podmianą jawnego celu; asset resolver dostarcza host.
- Osobna karta inspektora UI/React wycofana przez użytkownika; priorytetem jest istniejący inspektor GameObjectów/komponentów.
- [ ] Powiązanie deklaratywnego panelu UI z GameObjectem przez komponent, z jawną własnością i lifecycle; bez GameObjecta dla każdego elementu widoku.
- Instrukcja: [runtime-tools.md](runtime-tools.md). Inspektor ma edycję transformacji, gizmos i jawnie konfigurowany eksport runtime; osobny inspektor drzewa UI nie jest już wymagany.


## Nowe wymagania użytkownika — 2026-10-16, po pierwszym porcie Androida

Projekt zaakceptowany 2026-10-16; cztery plany wykonania do przeglądu: [FPS, narzędzia, streaming, światła i skybox](next-runtime-batch.md). To otwarty zakres; poprzednie checkboxy avatarów/gizmos potwierdzają wcześniejszy etap, nie te nowe ulepszenia.

- [ ] Czytelny voxel FPS: prawa dłoń/przedramię, autorski kilof, widoczny swing i odpowiednia kompozycja kadru; zachować F5, sockety i jednego animatora.
- [ ] Udostępnić tree viewer również w VoxelDemo/Physics3DDemo, poprawić wyszukiwanie i ergonomię; gizma socketów/świateł przez jawne adaptery.
- [ ] Narzędzia Feel: wspólne scenariusze use-tool/hit/landing/sprint/scene transition i panel istniejących presetów bez nowego schedulera.
- [x] Lokalny streaming nad AssetManager: typed lease, kolejki/priorytety, limity, cache i bezpieczna eksmisja nieprzypiętych rootów; autorski teksturowany model, prefetch i failure/retry w `--asset-streaming`. [API](asset-streaming.md), [native dowody](artifacts/asset-streaming/README.md). Android streaming **UNVERIFIED**; HTTP, hot reload i streaming chunków osobno.
- [x] Światła sceny directional/point/spot, ambient, gradient/pożyczony cubemap, mgła także unlit modeli, WORLD dzień/noc, typowane edycje i wspólne undo oraz gizma świateł/socketu. `--environment` i jawne otoczenie voxelów; [API](lighting-and-sky.md), [dowody desktop GL20/GL30](artifacts/environment/README.md). Android nowych funkcji **UNVERIFIED** bez rzeczywistego GLES/EGL gate; PBR/cienie/HDR poza zakresem.
- [x] Guide istniejącego API z komendami, zrzutami i przykładem SceneAssets: [engine-guide.md](engine-guide.md). Stan kolejnych wdrożeń jest opisany w checkboxach powyżej.

- [x] Niezależna walidacja animacji na darmowym importowanym Fox: GL20/GL30, 24 kości, Survey/Walk/Run, crossfade, maska, mikser, zegary, callbacki i cleanup. [Dowody](artifacts/imported-animation/README.md). Nie zamyka zadań FPS/streamingu/oświetlenia ani importu dowolnych rigów.


- [ ] Roślinność przez gotowe komponenty: deterministyczne fragmenty trawy, krzaków i drzew, proste ustawienia i współdzielone assety; bez GameObjecta na źdźbło. Projekt API i renderowania uwzględni instancing, cutout, culling, własność oraz nowe otoczenie. Priorytet: prosty kod w stylu Java Lab.

## GPU instancing i animation batching — nowe wymaganie

- [ ] Porównać źródłowy projekt użytkownika, gdy będzie dostępny kod; nie deklarować portu na podstawie nazwy/pustego repo.
- [x] Statyczny instancing zgodnych MeshPart/material/pass, culling, limity, stats i fallback GL20 — desktop oraz niezależny przegląd; [dowody](artifacts/instancing/README.md). Android GLES3/EGL nowej ścieżki zaliczony osobno na emulatorze API35 x86_64; fizyczny ARM64 i wydajność urządzenia pozostają niezweryfikowane.
- [x] Frame-local cache dokładnie zgodnych póz; osobne zegary/zdarzenia i własne kości — [Fox, dowody i koszt CPU](artifacts/imported-animation/README.md). Opcjonalny cache zmniejsza kompozycje256→1, ale w tym fixture zwiększa całkowity czas CPU.
- [ ] Dalsza optymalizacja CPU cache po pomiarach: jawny kontrakt niemutowalnych assetów/rewizji, jeśli będzie potrzebny; obecna ścieżka zachowuje widoczne bezpośrednie edycje kanałów. Nie utożsamiać mniejszej liczby kompozycji z niższym całkowitym kosztem CPU.
- [x] GPU palette texture i instanced skinning dla zgodnego rigu z niezależnymi pozami; zachować maski/avatar views/sockety.
- [x] Rzeczywiste porównanie pikseli/poz, liczby draw calls i pełnych kompozycji, benchmark64/256/1024 oraz osobny Android/context-loss gate na emulatorze API35 x86_64. [Wyniki, koszty i ograniczenia](artifacts/instancing/README.md).

[Zaakceptowany projekt i plany wykonania](gpu-instancing-design.md). A/B/C są sprawdzone w opisanym zakresie desktopu i emulatora GLES3/EGL. Fizyczny ARM64 oraz wydajność urządzenia pozostają osobnymi bramkami.

## Konsolidacja core i renderingu — wymaganie 2026-10-16

Status: projekt do przeglądu, nie wdrożona optymalizacja. [Projekt i zakres A1/A2/A3](superpowers/specs/2026-10-16-core-consolidation-design.md). Użytkownik zlecił agentów Luna pod nadzorem.

- [x] Trzy audyty źródeł: graf core/legacy, alokacje/rendering, zależności/platformy. [Raporty](audits/2026-10-16-consolidation/).
- [x] Sprawdzenie oficjalnych wydań: libGDX 1.14.2 nadal najnowszy stabilny; bez zmiany numeru wersji. Rozwiązane classpathy i dystrybucje wymagają osobnego dowodu po refaktorze.
- [ ] A1: odłączyć engine/backend/map/net legacy od domyślnego reactora i dystrybucji; osobny opt-in launcher zgodności, test architektury/dependency graph, aktualizacja poleceń. Projekt oczekuje przeglądu.
- [ ] Decyzja o pełnym wycofaniu API legacy: stare JSON/Kryo, Spine, XML/generator i adaptery mobile mają jawne koszty migracji. Nie oznaczać izolacji jako skasowania źródeł.
- [ ] A2: baseline alokacji/retencji i bezpieczny reuse snapshotów, zapytań sceny, buforów batchy; zachować atomowość i lifecycle. Osobny projekt przed implementacją.
- [ ] A3: wspólne dane świateł dla ordinary/static/skinned oraz parity/recovery testy przed odblokowaniem scene instancingu. Osobny projekt przed implementacją.

Nie deklarujemy nowych wyników RAM/FPS ani runtime Androida na podstawie audytu źródeł.

## Opcjonalny Artemis ECS — korekta użytkownika

- [x] Audyt istniejących hooków core i porównanie Artemis/Ashley/Dominion/Fleks na podstawie oficjalnych metadanych. [Projekt i źródła](superpowers/specs/2026-10-17-optional-artemis-design.md).
- [x] Projekt opcjonalnego `javity-ecs-artemis` zaakceptowany przez użytkownika.
- [x] [Szczegółowy plan ECS](superpowers/plans/2026-10-17-optional-artemis.md) wdrożony w trzech krokach; moduł natywny, linki i projekcje mają testy CPU.
- [x] Native World/systemy/mappers/aspects/subscriptions/Bag, jeden właściciel i wybrana faza Game.
- [x] Powiązania/fabryka GameObject–Entity, bezpieczne recycled IDs, lifetime i cleanup także przy wyłączonej synchronizacji.
- [x] Typowane reference/data projekcje, single writer, struktura/enabled/active, duplikaty i sync on/off; bez automatycznego śledzenia dowolnych publicznych pól.
- [x] Benchmark pure ECS/GO/mixed oraz boundary guard i CPU usage gate; Android/ART, browser/Wasm i renderowanie pozostają niezweryfikowane. Wyniki i źródłowe hashe: [artyfakty ECS](artifacts/artemis-ecs/README.md).

Pełne wycofanie Artemisa nie jest aktualnym celem. Cleanup starego Galaxy nie może usuwać nowej opcjonalnej możliwości używania ECS. Wcześniejsze pozycje A1/legacy są historycznym kierunkiem zastąpionym tą korektą.

## Colyseus Rooms i browser/Wasm — nowy zakres

- [x] Audyt Luna i weryfikacja dokumentacji: Java authority + Colyseus gateway, headless symulacja, lokalny create/join/preview, alternatywy i koszty. [Rekomendacja](rooms-and-browser-roadmap.md).
- [x] Sprawdzenie gdx-teavm1.6.1/libGDX1.14.2/TeaVM0.15.0 i JS/Wasm GC; lista brakujących browser adapterów. To nie jest proof kompilacji Javity.
- [x] Opcjonalny `javity-rooms` headless, bounded JSONL worker oraz lokalny Colyseus gateway z dwoma oficjalnymi SDK clients. Rzeczywisty worker/process proof, room isolation, late baseline i cleanup: [przewodnik](local-multiplayer.md), [artefakt](artifacts/local-multiplayer/verification.json).
- [ ] Native SDK Java i deployment pozostają do osobnej weryfikacji.
- [ ] Późniejszy target browser JS/Wasm, szerszy netcode i realny WSS z hostem zdalnym; platformy/UI/physics/mapy osobnymi bramkami.

Kierunek dalszego portu przeglądarkowego pozostaje TeaVM/Wasm z osobnymi adapterami Yoga i Bullet Wasm, MapLibre GL JS oraz browser JavaScript zamiast GraalJS. To projektowana kolejność, nie obecna gotowość: [udokumentowany audyt i brakujące proofy](audits/2026-10-17-consolidation/browser-wasm-ports.md).

Podgląd nie jest drugim właścicielem autorytatywnego Game, Colyseus nie uruchamia automatycznie naszej fizyki JVM, a Wasm nie jest warunkiem multiplayer.


## Workspace multiplayer i eksperyment TeaVM — doprecyzowanie użytkownika

- [x] Jedna komenda `./gradlew dev`: bootstrap lokalnej paczki Colyseus i worker distribution, supervisor po stronie Java, readiness i kontrolowane zamykanie.
- [x] Lokalne przełączanie client/server view; server view czyta snapshoty Java bez dodawania gracza i bez przejmowania authority.
- [x] Liczba JVM/pokoi jest polityką hostowania, nie wymaganiem API; aktualny worker pozostaje domyślnym izolatorem.
- [ ] Integracja przyszłej platformy: konfiguracja gier/scen i replikacji, host/pule pokoi, obrazy kontenerów oraz realne auth/WSS/deploy; obecny `--container` jest jawnym entry pointem bez devtools.
- [x] Eksperyment TeaVM/Wasm GC z realnym core i mostami Yoga/Ammo: kompilacja i Chromium proof; [wynik i ograniczenia](teavm-wasm-spike.md). To nie jest wspierany browser backend.


## Rzeczywiste renderowanie Javity w WebAssembly

- [x] Oddzielny build `experiments/javity-wasm`: prawdziwe Game/Component/ModelRenderer/Camera3D/GdxRenderer przez gdx-teavm, bez zastępczego renderera JS.
- [x] Usunięcie blokera TeaVM `Arrays.compare(float[])` z zachowaniem kolejności JDK i testami.
- [ ] Kolejne browser integracje: Yoga/UI i Ammo jako providery silnika, scene asset loading, MapLibre GL JS, multiplayer, context loss i macierz przeglądarek; obecny spike nie potwierdza ich działania.
- [ ] Sprzętowy GPU i pomiary; Chromium/SwiftShader potwierdza uruchomienie i obraz, nie wydajność.

Wyniki i uruchomienie: [Javity Wasm rendering](javity-wasm-rendering-spike.md).


## Review jakości Wasm i integracji — 2026-10-17

Źródło statusów: [checklista Bugs](Bugs/2026-10-17-wasm-integration-review.md). Kontrakty i kolejność prac: [specyfikacja](Spec/2026-10-17-runtime-wasm-quality.md).

- [x] Usunąć fałszywy PASS testu grafiki z etykiety HTML; dodać kontrolę negatywną bez draw calls i sprawdzanie pikseli właściwej sceny.
- [x] Refactoring sceny: nazwane komponenty, oddzielne tworzenie i cleanup, zwolnienie pozostałych zasobów po błędzie oraz zachowanie błędu inicjalizacji.
- [x] Domknąć wyścigi i częściową konstrukcję mostu Ammo; niezależny przebieg siedmiu scenariuszy Chromium zaliczony.
- [x] Ujednoznacznić minimum Node i wersję faktycznie sprawdzoną.
- [ ] Osobna bramka platformowa: browser context loss, DPR, inne przeglądarki i pomiary sprzętowego GPU.

Starsze audyty i liczniki testów w tym pliku pozostają historycznymi checkpointami. Nie oznaczają aktualnego PASS dla modułów zmienionych później.

## Maintenance and publication — 2026-10-17

[Specification](Spec/2026-10-17-maintenance-and-pages.md).

- [x] Revisit Android runner evidence; no speculative app fix. [Diagnosis and full-suite workflow](Bugs/2026-10-17-android-followup.md).
- [x] Full hosted Android suite: run39046532187, source5d2a206,5/5 PASS after emulator display/onboarding/focus fixes. Historical TCG ANR cause remains unproven; physical-device checks stay open.
- [x] Update canonical AGENTS.md, local ignore rules, README and Unreleased CHANGELOG.
- [x] Validate and publish the static engine showcase, merge the reviewed branch into master, and record deployment evidence. [Live site](https://radomiej.github.io/JavityEngine/) · [verification](artifacts/site/README.md).

Feature gaps in browser providers, remote netcode and physical-device validation remain separate backlog items; this maintenance batch does not mark them implemented.

- [ ] BUG-UI-001: capture font advances and ink-edge coverage on hosted CI; keep the failing glyph gate until a diagnosis-backed fix. [Specification](Spec/2026-10-17-ui-font-parity.md).
