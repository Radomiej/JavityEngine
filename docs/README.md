# Dokumentacja JavityEngine

- [Guide silnika krok po kroku](engine-guide.md) — FPS z rękami, tree viewer, gizma, ładowanie assetów, Feel i granice aktualnego API.
- [Następny zakres: FPS, streaming, światła i skybox](next-runtime-batch.md) — zaakceptowany projekt; stan wdrożenia poszczególnych etapów w TODO.
- [Streaming lokalnych assetów](asset-streaming.md) — typed lease, priorytety/cache i demo `--asset-streaming`; [dowody desktop](artifacts/asset-streaming/README.md), Android UNVERIFIED.

- [Zakończenie aktualizacji bazy i API usług](finish-update.md).
- [TODO i kolejność prac](TODO.md) — bieżący backlog; kolejność wykonania w [work-queue](work-queue.md).
- [Status i wyniki weryfikacji](modernization-status.md).
- [Inwentaryzacja starego API](migration-api.md) — co przeniesiono, co nadal działa w legacy i czego brakuje.
- [Fizyka 3D Bullet](physics3d.md) — opcjonalne bryły, kontakty, zapytania i adapter voxelowy; status kontrolera/demo opisany osobno.
- [Avatary FPS/TPP](avatars.md) — maski, regiony siatki, sloty/sockety, jeden właściciel pozy i widoki.
- [Efekty odczuć 3D](feel3d.md) — presety prezentacyjne, kamery świata/viewmodelu i demo GL30; [zrzuty i pomiary](artifacts/feel3d/README.md).
- [Voxele i chunki](voxels.md) — dane świata, meshing, DDA, adapter GPU i kontrola zasobów.
- [Demo voxelowe](demo-voxels.png) i [autorski atlas tekstur](voxel-atlas.png).
- [Mapy i MapLibre](maps-and-maplibre.md) — raster XYZ i oddzielny backend wektorowy.
- [Gorzów Wielkopolski offline](gorzow-offline-map.md) i [rzeczywisty widok miasta](demo-gorzow.png).
- [Komponenty względem java-lab](java-lab-component-parity.md) — sprawdzony kontrakt i konkretne braki.
- [MapLibre Native](maplibre-native.md) — własne MBTiles/MVT, style, offline glyphy/sprites i rzeczywista tekstura GPU libGDX.
- [Demo MapLibre](demo-maplibre.png), [referencja GL JS](demo-maplibre-reference.png) i [porównanie](maplibre-comparison.png).
- [Kamery](camera-api.md) — 2D, śledzenie, kierunek celowania i istniejące 3D.
- [Multiplayer](multiplayer-design-notes.md) — wstępny model serwer/klient, bez wdrożonej replikacji.
- [Lokalne pokoje Colyseus](local-multiplayer.md) — ograniczony realny bridge Java worker + Node/Colyseus + SDK preview, komendy i zakres dowodów.
- [Galeria materiałów Shapes](demo-shape-materials.png) — gradient, UI pulse, WORLD stripes i własne GLSL.
- [Shapes i shadery](shapes-and-shaders.md) — działające figury SDF/AA i zakres dalszych rozszerzeń.
- [Demo modern](demo-modern.png), [demo map offline](demo-map.png), [demo runtime](demo-runtime.png), [demo Shapes AA](demo-shapes.png), [demo pluginów](demo-plugins.png).
- [Inspiracje Unity: OneJS, Animancer, Feel](unity-inspired-api.md) — audyt, porównanie podejść i kolejność batchy.
- [Tworzenie gry](game-authoring.md) — praktyczne przykłady i właściciele zasobów.
- [Relay Ward — natywny HUD](demo-mm.png) i [referencja HTML](demo-mm-html.png); [metoda porównania](ui-html-parity.md#hud-relay-ward).
- [Pokrycie Feel/Animancer/OneJS](plugin-feature-coverage.md) — wdrożone zachowania, oddzielne dowody native i aktualny etap.
- [Feedback/tween API](feedback-api.md), [animacje 2D/3D](animation-api.md), [deklaratywne UI](declarative-ui.md), [port OneJS/React](onejs-port.md).
- [Provider QuickJS dla Androida](../javity-script-quickjs-android/README.md) — wspólny host React i aktualny status adaptera Android.
- [Audyt systemów More Mountains](mm-systems-audit.md) — przeczytane źródła, zakres wdrożenia i brakujące kontrakty.
- [Ustalenia portów](plugin-port-decisions.md) — własność, czas i granice bridge.
- `superpowers/specs/` i `superpowers/plans/` — projekt i checklisty poszczególnych etapów.

Instrukcje builda i krótkie przykłady: [README repozytorium](../README.md). Zasady pracy: [AGENTS.md](../AGENTS.md).

- [Android: audyt, probe APK i wymagany port](android-status.md)

- [Kolejka prac](work-queue.md)
- [Inspektor runtime](runtime-tools.md)
- [Pokrycie OneJS — audyt agenta](onejs-coverage-audit.md)

- [Projekt backendów Android Yoga/QuickJS i zakres testów](superpowers/specs/2026-10-15-android-port-architecture.md) — zweryfikowane artefakty; projekt, nie deklaracja wdrożenia.
- [Szczegóły skinningu i warstw avatarów](superpowers/specs/2026-10-15-avatar-api-architecture.md) — własność geometrii i ograniczenia libGDX.

- [Darmowy Fox: animacje na prawdziwym rigu](artifacts/imported-animation/README.md) — GIF, wyniki GL20/GL30 i interaktywny test naszego ModelPoseAnimator.

- [GPU instancing i animation batching](gpu-instancing-design.md) — implementacja A/B/C, benchmark i ograniczenia platform; [guide włączania](gpu-instancing-guide.md).

- [Opcjonalny Artemis ECS](artemis-ecs.md) — natywny adapter, przykłady użycia, CPU smoke/benchmark i granice zależności; [projekt](superpowers/specs/2026-10-17-optional-artemis-design.md) oraz [plan](superpowers/plans/2026-10-17-optional-artemis.md).
- [Colyseus Rooms i targety JS/Wasm](rooms-and-browser-roadmap.md) — audyt i rekomendacja, bez wdrożonej replikacji/builda browser.

- [Eksperyment TeaVM Wasm GC, Yoga i Bullet/Ammo](teavm-wasm-spike.md).
