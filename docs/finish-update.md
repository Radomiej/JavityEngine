# Aktualizacja bazy desktop

Zakres wykonany: Java 25, Gradle 9.8.1, libGDX 1.14.2; lokalny Galaxy Rapid i pluginy; nowy rdzeń bez Artemis; 2D/3D/UI/Box2D/mapy oraz podstawowe funkcje dotychczasowych gier. Legacy pozostaje do zgodności API i danych.

## Nowe API usług

- `AudioSource(Sound|Music, ownsAsset)` — play/stop/pause/resume, volume/pan/loop, sound pitch i limit voice IDs. Pożyczony Sound: zatrzymuje tylko własne głosy; Music powinien być wyłącznym strumieniem. Pauza audio jest jawna, niezależna od pause Game; patrz przyciski RuntimeDemo.
- `SceneAssets` — GameSystem zarządzający AssetManager i zwalnianiem zasobów sceny. Load jest synchroniczny, przeznaczony na inicjalizację. Asset-backed komponenty pożyczają zasoby; SceneAssets zamyka się po komponentach.
- `SceneTimers` — komponent z fazą UI, `after(delay, realtime, callback)` i `every(interval, realtime, callback)`, anulowalne Handle. WORLD używa deltaTime i pauzy, realtime unscaled. Repeating coalesces zaległe ticki do jednego callbacku na klatkę. Callback dodany przez callback rusza dopiero w kolejnej fazie timerów.
- `SceneManager` — request fabryki świeżego Game, step i current; zmiana na granicy Game.step. Zamyka poprzednią scenę i sprząta nową po błędzie startu.
- `Component.onRelease()` i `GameSystem.onRelease()` — zwalnianie zasobów przejętych przez konstruktor, także bez inicjalizacji. onDestroy nadal dotyczy zainicjalizowanych instancji. Cleanup kontynuuje po błędzie i agreguje wyjątki.

## Rendering i wejście

Render2D ma wspólne layer/order dla SpriteRenderer2D, TextRenderer2D oraz ShapeRenderer2D. Tekst bierze caller-owned BitmapFont; transform 2D wpływa na tekst. Figury: rectangle/circle z fill lub stroke, polyline i prosty polygon. Circle/rectangle/polyline mają teraz SDF AA, rounded/caps i WORLD/SCREEN; szczegóły i ograniczenia w shapes-and-shaders.md. Dostępne są publiczne `ShapeMaterial2D` z typowanymi uniformami oraz `ShapeShaderAnimator` z czasem WORLD/UI; gotowe gradient/pulse/stripes zachowują SDF AA i obrys. Materiały nie obejmują legacy polygon i wymagają jawnego codec/rejestru przy serializacji; [zakres i ograniczenia](shapes-and-shaders.md). Polygon triangulowany raz przy budowie. SpriteAnimator podmienia region atlasu; WORLD pause zatrzymuje czas animacji.

InputActions jest InputAdapter: bind/isDown/justPressed/justReleased/axis. W multiplexer umieść Stage przed światem, po Game.step wywołaj finishFrame. Przy utracie focus/zmianie focus UI wywołaj releaseAll i PointerPicker.cancelAll, aby nie zostawiać wciśniętych klawiszy. PointerPicker używa Camera2D i hit area Pickable2D, uwzględnia transform i layer/order, sprawdza żywotność capture przy zwolnieniu.

Flex domyślnie ma childrenOnly: pusta część overlay nie blokuje świata. Panel, który ma blokować świat także w pustych miejscach, ustaw `setTouchable(Touchable.enabled)`. Przyciski nadal przechwytują własne zdarzenia.

## Fizyka

PolygonCollider2D: 3–8 wierzchołków, wypukły bez przecinania/kolinearnych boków; kopiuje punkty, minimalny bok 0.01 m po przeliczeniu. Raycast zwraca niemodyfikowalną listę kopii RayHit2D najbliższy→najdalszy, mask/triggery; Box2D nie raportuje kształtu obejmującego punkt początkowy. Siły i impulsy są kolejkowane do najbliższego fixed step, także z callbacków kontaktu; siła działa przez jeden fixed step, nie przez dowolnie długą klatkę. Jednostki: kg×world-unit/s² i kg×world-unit/s. Ciągłe siły powinny być zlecane zgodnie z krokiem symulacji.

## Sceny i prefaby

SceneJson ma wersję 1, jawny registry ComponentCodec, transformacje 2D/tagi/active i settings komponentów. Standard obejmuje Transform3D (odtwarza parent refs), Camera2D i figury. PhysicsSceneCodecs/MapSceneCodecs dodają swoje typy. Asset-backed komponenty i skrypty rejestruj z własnym codec/resolverem. Nie serializuje native handles, shaderów, fontów, transportu ani credentials. Nie instancjonuje klasy podanej arbitralnie w pliku. Nieznany typ/wersja kończy się błędem; rollback niszczy nowo dodane obiekty, cleanup następuje w zwykłej fazie Game.

Prefab przechowuje immutable JSON i tworzy świeże instancje/IDs. Format nie jest automatycznym czytnikiem starych JSON/Kryo. Historyczne loader/API zostały zachowane; mapowanie konkretnej gry/assetów wymaga odpowiednich codeców.

## Mapy online

HttpTileFetcher: jawny URL z `{z}/{x}/{y}`, User-Agent i atrybucja. TileByteCache: osobny katalog per provider/style, ograniczona liczba workers/pending/disk tiles, dane do 1 MiB, retry/negative state i cancellation. AsyncTileSource dekoduje Pixmap dopiero na wątku GL, MapRenderer posiada GPU LRU. Source należy zamknąć osobno; wymagane honorowanie interruption/timeouts przez własny fetcher. Pokazuj attribution źródła w UI. Cache dyskowy ma limit liczby wpisów, nie TTL; przy zmianie wersji stylu użyj nowego katalogu lub invalidate.

HTTP sprawdzono na lokalnym serwerze, worker/cache na syntetycznych danych. Nie deklarujemy pobierania z konkretnego publicznego serwera ani pełnego MapLibre.

## Przykład i CI

`./gradlew :javity-desktop:run --args='--runtime'` i `:javity-desktop:runtimeSmoke` pokazują przeniesione wzorce TestApp: input/move, click, delayed audio, lifecycle, scene chooser, atlas/tekst/figury, prefab i polygon/raycast. Native OpenAL testuje pliki WAV wygenerowane dla projektu. Na serwerze użyto `ALSOFT_DRIVERS=null`; sprawdzono dekodowanie/odtwarzanie API, nie odsłuch przez głośniki.

`.github/workflows/desktop.yml` obejmuje Java25 build/test/dystrybucję, build/test hosta React, porównania HTML, fixture MapLibre oraz zestaw native smoke i próby zainstalowanej paczki; dokładne bieżące komendy są w workflow. Workflow nie został wysłany ani uruchomiony w GitHub; równoważne komendy sprawdzono lokalnie.

## Oddzielne dalsze zadania

Wektorowe style JSON v8/MVT/MBTiles są dostępne w osobnym `javity-maplibre` na Linux x64 EGL; [zakres zgodności](maplibre-native.md). Dalszymi zadaniami pozostają rozszerzenia Shapes (kreskowanie, AA polygon, bevel/miter, clipping, render targets i postprocessing), multiplayer, GLB/glTF/PBR oraz platformy mobile/web. Całe gry/ich XML UI wymagają migracji. Spine TestApp jest eksportem 2.1.27: wymagany właściwy projekt/narzędzie do ponownego eksportu przed migracją do nowoczesnego runtime. Nie usuwamy legacy ani nie deklarujemy pełnej konwersji tych assetów.

## Weryfikacja zamknięcia bazy — 2026-10-13 (wynik historyczny)

Na etapie zamknięcia bazy: 78 testów bez błędów, czysty build/dystrybucja oraz cztery Gradle smoke po 120 klatek (modern/map/runtime/legacy). Dokładny zapis historyczny: [modernization-status.md](modernization-status.md#zamknięcie-aktualizacji-bazy-desktop--2026-10-13). Bieżące dowody kolejnych etapów są raportowane osobno.

## Uzupełnienie Shapes AA — 2026-10-14

Końcowa baza obejmuje również SDF AA dla circle/rectangle/polyline, rounded/caps, przestrzeń WORLD/SCREEN i serializację stylów. Weryfikacja po tym batchu: 99 testów i pięć native smoke (w tym shapes), każdy po 120 klatek. Dokładny kontrakt i pozostałe rozszerzenia: [Shapes i shadery](shapes-and-shaders.md).

## Porty inspirowane Unity — 2026-10-14

Do obecnego modelu komponentów dodano Feedback/Tween, nazwane klipy sprite/modelu z ModelAnimator crossFade, deklaratywne natywne UI oraz opcjonalny host GraalJS Community/React/TSX. Bridge Java–JS udostępnia jawne akcje i wartości prymitywne. Publiczne OneJS/onejs-react MIT posłużyły jako wzorzec; komercyjne źródła Feel/Animancer nie są włączone. [Źródła i zakres](unity-inspired-api.md), [OneJS](onejs-port.md), [animacje](animation-api.md), [feedback](feedback-api.md), [UI](declarative-ui.md). Native demo `--plugins` pokazuje proceduralny skinning dwóch kości. Dostępne są `ModelPoseAnimator` z rzeczywistym 1D mixerem, masked override layers i zmianą wag oraz istniejące 2D punch/shake. Clip crossfade w pose/layer API jest zatwierdzony po niezależnym przeglądzie, 548 testach oraz rzeczywistym pluginsSmoke. Prezentacyjne efekty kamery/modelu 3D pozostają kolejnym zadaniem Task6 — [aktualny zakres](plugin-feature-coverage.md), [tworzenie gry](game-authoring.md). Produkcyjne rigi, glTF/PBR, IK i rozbudowane particles pozostają osobnym tematem.
