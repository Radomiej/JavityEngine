# Praca nad JavityEngine

## Cel i ustalenia

Upraszczamy i rozbudowujemy silnik 2D/3D na libGDX. Użytkownik dopuszcza migrację starych gier i API. Galaxy Rapid jest własnym backendem silnika; jego źródła mają być budowane lokalnie w tym repozytorium, bez zależności od historycznych snapshotów Sonatype.

## Struktura

- `javity-backend`: kod Galaxy Rapid, obecnie rendering 2D, Box2D, zasoby, kamera, wejście i Artemis ODB.
- `javity-core`: nowe API komponentów, indeksy sceny i rendering 2D/3D; bez Artemis.
- `javity-physics2d`: opcjonalny Box2D dla nowego rdzenia, bez Artemis; ciała, collidery i kontakty.
- `javity-engine`: historyczne API Javity, utrzymane do migracji gier.
- `javity-net`: HTTP i pobieranie zasobów.
- `javity-assets`: opcjonalny lokalny streaming Texture/Model, typed lease, priorytety i budżetowany cache; docs/asset-streaming.md.
- `javity-maplibre`: opcjonalny MapLibre Native FFI, lokalne MBTiles/MVT, style JSON, Linux x64 system EGL/GLES.
- `javity-map-core`: nowe mapy rastrowe XYZ bez legacy; provider, cache, kamera, markery/trasy.
- `javity-map`: opcjonalne mapy geograficzne OSM, markery i trasy; to nie mapy poziomów Tiled.
- `javity-desktop`: demo nowego rdzenia i opcjonalne demo legacy.
- `javity-ui`: przenośne Scene2D/deklaratywne UI i Yoga SPI, ograniczony katalog klas stylów.
- `javity-ui-yoga-lwjgl`: desktopowy provider Yoga JNI; aplikacje desktop dodają go jawnie.
- `javity-ui-yoga-android`: provider oficjalnej Yoga 3.2.1/SoLoader; AGP i 4 testy JNI/ART API35 x86_64 sprawdzone, pełna aplikacja ma osobne bramki.
- `javity-script`: przenośny host React i SPI silnika JS; `javity-script-graal` wybiera GraalJS na desktopie, `javity-script-quickjs-android` dostarcza jawnie wstrzykiwany QuickJS.
- Historyczny `javity-android`, XML UI i generator projektów pozostają poza głównym reactorem. Nowe moduły Android są opt-in przez `-PjavityAndroid=true`.

## Build i uruchamianie

Wymagania: pełne JDK 25 LTS (JAVA_HOME wskazuje JDK). Gradle 9.8.1 dostarcza wrapper. Z głównego katalogu:

```sh
./gradlew clean build
./gradlew :javity-desktop:run
./gradlew :javity-desktop:smoke
./gradlew :javity-desktop:run --args='--mm'
./gradlew :javity-desktop:mmSmoke
./gradlew :javity-desktop:run --args='--legacy --smoke'
```

Smoke wymaga prawdziwego kontekstu OpenGL; na serwerze użyj Xvfb i programowego renderowania Mesa. Sam headless backend nie potwierdza poprawności renderowania. W środowisku z proxy skonfiguruj je w prywatnym pliku ustawień Gradle użytkownika. Nie zapisuj proxy, credentiali ani ścieżek środowiska chmurowego w kodzie projektu.

## Architektura komponentów

Wzorcem nowego rdzenia jest `Radomiej/java-lab`, zwłaszcza `shared/lab-game-v2/API.md` i `src/data/gameCoreRuntime.js`. Przenosimy kontrakt zachowania, nie browserowy transport Canvas ani generowane źródła Java w stringach JavaScript.

- Jeden model `GameObject + Component`; wiele komponentów tego samego typu.
- `hasComponent(Type)` sprawdza obecność, `getComponent(Type)` zwraca komponent lub null, `getComponents(Type)` wszystkie zgodne komponenty, również z klas pochodnych.
- `requireComponent(Type)` może pozostać opcjonalnym sprawdzeniem konfiguracji podczas inicjalizacji; nie używaj wyjątków jako normalnego sterowania gameplay.
- Gdy potrzebujesz komponentu, pobierz go raz i sprawdź null zamiast `hasComponent` + `getComponent`.
- Kolejność aktualizacji i moment dodawania/usuwania komponentów muszą być deterministyczne. Rejestry i indeksy należą do sceny.
- Aktualny core GameObject/Component pozostaje niezależny od Artemis. Opcjonalny `javity-ecs-artemis` udostępnia natywne Artemis API i jawny most do GameObjectów; natywne encje/World należą do tego adaptera, nie do API core. Nie dodawaj alternatywnego ECS backendu ani nie deklaruj przewagi wydajności bez konkretnych pomiarów. Nie usuwaj Artemisa na podstawie poprzedniej propozycji pełnego wycofania legacy. Przykłady i granice wsparcia: `docs/artemis-ecs.md`.

## 3D i mapy

- Oddziel transformacje 2D od pełnych transformacji 3D. Obrót 3D przechowuj jako quaternion; kolejność warstw 2D nie jest współrzędną Z świata.
- Model jest współdzielonym zasobem; każdy obiekt ma własną instancję i stan animacji.
- Rendering modeli i fizyka 3D są osobnymi etapami.
- Mapy mają pozostać opcjonalne. Docelowo oddziel projekcję, dostawcę kafelków, cache i renderer; unikaj osobnego GameObject dla każdego kafelka.
- Testy nie powinny wymagać serwerów OSM. Provider i zasoby powinny być wymienne.

## Zasady zmian i weryfikacji

Działający build, nowy rdzeń, Box2D i przykłady 2D/3D/UI już istnieją. Adapter map, czcionki FreeType i Camera2D/follow są dostępne. Bieżący backlog: docs/TODO.md; audyt braków: docs/migration-api.md. Bieżącą kolejność prac wyznacza `docs/work-queue.md`: narzędzia runtime, avatar, desktopowy Bullet3D oraz desktopowy zakres Feel/animacji są ukończone. Android ma zaliczone ograniczone integracje AGP, Yoga/QuickJS JNI/ART, smoke density 160/320 i rzeczywistą utratę/odzyskanie EGL. ActivityFIX3 i responsiveFIX1 są zintegrowane; wszystkie pięć przypadków instrumentacyjnych finalnego APK density 320 przeszło osobno (w tym Activity recreate, landscape, EGL recovery i touch/model/React); pełny nieprzefiltrowany runner39046532187 (source5d2a206) zaliczył5/5 bez błędów i pominięć na API35 x86_64 KVM po poprawkach przygotowania emulatora/fokusu. Historyczna przyczyna ANR TCG pozostaje nieustalona. Debug/test i R8 release build oraz APK audits są zaliczone, ograniczony runtime R8 release przeszedł na lokalnie podpisanej kopii; fizyczny ARM64, wydajność urządzenia i Android MapLibre/Bullet pozostają poza potwierdzonym zakresem. MapLibre Native oraz ograniczony katalog Shapes/materialów już istnieją. Bounded lokalny gateway Colyseus + Java authority ma własny real-process proof; szerszy netcode, migracja konkretnych gier i browser/Wasm target pozostają osobnymi zadaniami. Nie łącz mechanicznego przenoszenia kodu ze zmianą jego zachowania bez potrzeby. Zachowuj informacje o pochodzeniu i licencjach w `THIRD_PARTY.md`; Spine ma własne warunki licencji.

Diagnozuj przyczynę błędów przed poprawką. Dodawaj testy regresji dla zmian zachowania i uruchamiaj cały reactor przed deklarowaniem sukcesu. Raportuj osobno: kompilację, testy i rzeczywiste uruchomienie graficzne. Nie deklaruj działania 3D, Androida, współczesnego Spine ani map online na podstawie samej kompilacji.

Nie wysyłaj commitów ani nie publikuj wydań bez polecenia użytkownika. Nie deleguj pracy do innych agentów bez wyraźnej instrukcji użytkownika.

## UI Yoga

UI używa opaque YogaNode przez wybrany YogaBackend; LWJGL i Android JNI są osobnymi providerami. Dodaj dokładnie jeden provider do runtime albo wstrzyknij backend jawnie. Nie przywracaj LWJGL do przenośnego UI. Android wymaga inicjalizacji SoLoader z Context przed pierwszym node; nie deklaruj gotowości Android/web bez osobnych buildów i testów JNI/runtime. Klasy stylów mają jawny katalog w UtilityStyles; nie ignoruj nieznanych klas i nie deklaruj pełnego CSS/Tailwind. Zamykaj Flex i UiTheme; Stage.dispose() nie zwalnia węzłów Yoga. Odłączony kontener pozostaje własnością wywołującego.

Nie nadpisuj wersji gdx-jnigen-loader, gdx-ai ani innych niezależnie wersjonowanych rozszerzeń wersją głównego libGDX.

## Fizyka i fazy sceny

`GameSystem` rejestruj przed startem. Fazy: snapshot skali czasu → systemy `onUiUpdate` → komponenty UI → systemy before → WORLD → systemy after (fizyka) → late update → sprzątanie. `onUiUpdate` używa nieskalowanego czasu i działa podczas pauzy/freeze. Systemy należą do jednej sceny i są zamykane wraz z nią, także po błędzie callbacku. Rejestruj dostawcę zasobów przed konsumentem: po cleanup komponentów systemy zwalniają się w odwrotnej kolejności rejestracji, np. SoundManager przed SceneAssets. Nie iteruj bezpośrednio po kolekcji systemów w fazach, w których callback może zamknąć grę.

Box2D ma stały krok 1/60 s i jawny przelicznik jednostek świata na metr. Jedna bryła i wiele colliderów na obiekt; geometria kolizji jest niezależna od grafiki. Nie udostępniaj natywnych Body/Fixture w gameplay API. Zdarzenia kontaktu kolejkuj i dostarczaj dopiero po odblokowaniu World; sprawdzaj usunięcie i enabled/active przed każdym odbiorcą. Testy fizyki muszą używać rzeczywistego Box2D, bez wymagania OpenGL.

Opcjonalny `javity-physics3d` używa Bullet tylko w swoim module. Slice A+B dostarczają typed box/sphere/Y-up total-height capsule i compound bodies, fixed 1/60 krok z limitem 8 ticków na klatkę, scene-local native ownership, copied force/impulse commands, jawne teleport/kinematic ruch, world-local contacts, osobne trigger collision objects, mutual 16-bit filtry oraz copied closest-hit ray/sphere/capsule queries. Direct `Transform3D` writes po zbindowaniu są odrzucane. C1 dodaje opcjonalny `javity-voxel-physics3d` z wersjonowanymi snapshotami i ograniczonym statycznym compoundem/chunk; nie deklaruj fizycznej bariery dla dowolnych ciał dynamicznych. C2 dodaje osobny kontroler kapsułowy. Opt-in desktop C3 uruchamia `Physics3DDemo` (`--physics3d`) z lazy, jawnie posiadanym debug drawerem; CPU inicjalizacja `Physics3D` pozostaje bez GL. Android native packaging/runtime nadal wymaga osobnej walidacji. Szczegóły i limity w `docs/physics3d.md` oraz `docs/voxels.md`.

UI ma `Rectangle`, `rounded` i `border`; wspólny shader należy do UiTheme. Nie twórz nowego shadera w każdej klatce ani nie zostawiaj zmienionego shadera Batch po rysowaniu widgetu. Zaokrąglenie jest wizualne, nie przycina dzieci i nie zmienia hitboxa.

## Mapy, czcionki i kamery

MapRenderer posiada cache GPU, zwalnia Pixmap ze źródła i wykonuje Batch.flush przed możliwą eksmisją tekstury. Nie zwalnia caller-owned Batch ani TileSource. Provider FileTileSource jest lokalny i synchroniczny; nie deklaruj map online/MapLibre na podstawie raster demo. Uwzględniaj odmienne konwencje Y: MapView bottom-left, Camera2D/Input top-left.

Domyślny font jest bundlowany z licencją; UiTheme zarządza FreeType atlasem albo pożyczonym BitmapFont zgodnie z ownsFont. Zachowuj polskie glyphy. Camera2D bierze pozycję/obrót z Transform2D; FollowTarget2D działa po fizyce i nie może wskazywać innej sceny. Nie dodawaj globalnej main camera do nowego API.

Dokumentuj nowe ustalenia użytkownika w docs/TODO.md i powiązanych notatkach. Lokalny vertical slice Colyseus/Java ma implementację i real-process testy; nie rozszerzaj tej deklaracji na dowolną replikację, hosting produkcyjny lub transport klienta Java bez osobnych dowodów.

## Runtime po aktualizacji bazy

AudioSource/SceneAssets mają jawną własność, SceneTimers/SceneManager czas i lifetimes sceny. onRelease zawsze sprząta constructor-owned resources, niezależnie od onCreate; cleanup kontynuuje po błędach. InputActions.finishFrame dopiero po Game.step; po focus loss releaseAll/cancelAll. Flex overlay używa childrenOnly.

Render2D ma wspólne layer/order dla sprite/text/shapes. Nie kompiluj shaderów i nie trianguluj stałych figur każdą klatkę. Serializacja to SceneJson v1 + jawne codeki; nie wprowadzaj arbitralnej refleksyjnej instancjacji z nazw klas w pliku.

Mapy online używają bounded TileByteCache/AsyncTileSource/HttpTileFetcher. URL/User-Agent/atrybucja jawne, testy na lokalnym HTTP. Audio smoke może użyć ALSOFT_DRIVERS=null (sprawdza native API bez odsłuchu). Nie deklaruj konwersji historycznego Spine 2.1.27 do współczesnego runtime. Szczegóły w docs/finish-update.md.

## Figury SDF

SmoothShapeRenderer posiada współdzielone zasoby GPU. Pochodne GLSL i fizyczny framebuffer sterują AA; SCREEN używa logicznych pikseli i następuje po WORLD. Polilinia ma maksymalnie 64 efektywne punkty i ROUND joins; transparentne łączenia wymagają pojedynczej sumy SDF. Polygon pozostaje bez AA/obrysu. Zachowuj testy pikseli shapesSmoke po zmianach renderera.

## Porty UI/animacji/feedback

`javity-script` jest opcjonalnym przenośnym hostem React; rdzeń nie zależy od JS. Graal Context/Value należą wyłącznie do `javity-script-graal`. QuickJS Android wybieraj przez `QuickJsScriptUiEngineFactory`; oba providery używają tej samej polityki hosta i bundla. Nie dodawaj Graal/Truffle/LWJGL do APK. Normalizuj również checked wyjątki natywnego QuickJS na granicy SPI; nie uruchamiaj rekurencyjnego evaluate w bindingach JNI. TSX buduj w web/javity-ui, aplikacje importują shim src/runtime.js do już istniejącego hosta. Nie bundluj drugiej kopii React. Jawne JavityGame actions/primitive values zastępują refleksyjny dostęp do Java. Runtime close przed UiDocument close; oba na wątku UI. Schedulery hosta mają limit callbacków, który nie ogranicza dowolnego JS ani Graal Promise jobs.

EffectSystem należy do sceny, rejestruj przed startem. Dzierżawy identyfikują właściciela właściwości; stary handle nie może nadpisywać nowego efektu. ModelAnimator i legacy ModelRenderer muszą używać pojedynczego AnimationController; SpriteAnimationPlayer nie współistnieje z legacy SpriteAnimator na tym samym obiekcie. Model/Sprite zasoby dostarczone przez caller nie przechodzą na własność animatora. Testy animacji mają obejmować pozę i usunięcie właściciela, nie tylko czas.

Natywny markup/React obsługuje jawne typy/props UtilityStyles. Nie deklaruj pełnego HTML/CSS/Tailwind ani Unity API. UiDocument posiada własne węzły Yoga, pożycza UiTheme/Stage. Layout Yoga nie powinien być nadpisywany tweenem pozycji/szerokości Actor; animuj alpha albo jawne transformacje poza layoutem.

`Radio`/`Signal` i enum `StateMachine` są scene-local; nie są transportem multiplayer. Opcjonalne `javity-rooms` jest headless JVM authority dla bounded lokalnego Colyseus gateway w `web/local-multiplayer`; zachowuj owner-thread `Game`, bounded worker, protokół JSONL v1, loopback i kontrolowany cleanup. Test wymaga JDK25, Node22+ i dostępu do loopback; szczegóły oraz niezweryfikowane granice w `docs/local-multiplayer.md`. To nie jest pełny netcode ani target TeaVM/Wasm. `TimeManager` posiada niezależne dzierżawy, WORLD używa snapshotu skali danej klatki, UI jest nieskalowane. `ProgressBar` animuje własny model/fill, bez zapisywania geometrii Yoga; keyed reparent jest odłączeniem od parenta, nie terminalnym `remove`/`close`.

`SoundManager` pożycza Sound/Music, posiada konkretne native voice IDs i własne duck handles. Distinct Music streams mogą współistnieć; pojedynczy Music jest exclusive wobec własnej dzierżawy. Nigdy globalne Sound.stop() do anulowania jednego efektu. `isActive` oznacza dzierżawę, nie wykrycie końca natywnego Sound. FloatingTextPool pożycza font i resetuje sloty/generacje; render i native OpenAL muszą mieć osobny desktop smoke od recording mocks.

ModelPoseAnimator ma własny per-instance sampler TRS, rzeczywisty 1D mixer, crossfade klipów base i maksymalnie 8 warstw masked override z crossfade nazwanych klipów. Crossfade zamraża aktualny wkład slotu; stary zegar i jego zdarzenia kończą się przy podmianie. `layer.fadeTo` pozostaje osobną zmianą wagi. Wiąże pierwszy ModelRenderer; pozostałe renderery mają własny legacy controller. Nie współistnieje z ModelAnimator ani drugim pose ownerem. GameObject.containsComponent(Component) sprawdza tożsamość bez kopiowania listy; używaj go w sprawdzaniu lifetimes konkretnej instancji. ModelAnimator pozostaje oddzielnym legacy API.

Przy wymianie sceny wykryj nowy SceneManager.current natychmiast po step, podmień scene-local źródła sygnałów i dopiero emituj host sceneGeneration/status oraz tick React. Callback zakończenia LoadHandle zapisuje flagę Java; nie emituje sygnału do gościa, który mógłby odczytać już zamknięte źródło poprzedniej sceny. Key poddrzewa HUD zależny od generacji odtwarza subskrypcje; akcje persistent hosta pobierają aktualny controller/audio, nie przechowują starego.

Toggle/Slider są kontrolowane: onChange proponuje Boolean/Float w Javie lub boolean/number w JS; aktualizację zatwierdza nowy prop. Text.wrap jest opt-in; zmiana tekstu/fontu przy tej samej szerokości musi unieważniać cache pomiaru Yoga. Callback pomiaru nie może wypuszczać wyjątku przez natywne ABI. Nie mutuj pożyczonego BitmapFontData podczas dziedziczenia rozmiaru.

UiTheme.registerImage pożycza TextureRegion/Texture. Image/img jest liściem i przyjmuje tylko zarejestrowaną nazwę source oraz contain/cover/stretch. Sprawdzaj źródła przed mutacją drzewa; unregister nie unieważnia regionu trzymanego przez istniejącego aktora. Maskę rounded licz niezależnie od dopasowanej treści i UV atlasu. Komponuj obraz/tło/border przed alpha aktora, zachowując caller flip i przywracając shader/color Batch. Pożyczona tekstura musi przeżyć wszystkie używające jej obrazy.

ScriptUiReloader służy do developerskiego odczytu lokalnego bundla na wątku UI; nie przekazuj File/Java IO do gościa. Błąd składni pozostawia dotychczasowe UI; błąd wykonania po rozpoczęciu reload zamyka runtime i nie cofa akcji Java. useAction jest bez wyniku (undefined); bezpośredni JavityGame.call zwraca null, więc efekt React z taką akcją zapisuj jako block-body zwracający undefined.

Produkcyjne TSX: `npm run build` w web/javity-ui generuje trzy zasoby (host, sample, mm); sprawdzaj deterministyczność wszystkich trzech. `npm run dev` zapisuje ignorowany bundle; `--mm --ui-dev=<absolute bundle>` jest lokalnym workflow developerskim. Powtarzalny test gotowej paczki: web/javity-ui-reference/packaged-dev-proof.py. HTML fixture i native muszą używać tej samej treści/fontu/viewportu, a tolerancje tekstu, płaskich kolorów i krawędzi raportuj osobno.

## Materiały Shapes

ShapeMaterial2D jest CPU-only; ShapeRenderer2D pożycza materiał. SmoothShapeRenderer posiada do 32 programów definicji GLSL/schematu i zwalnia cache przy dispose. Nie zmieniaj ShaderProgram.pedantic globalnie. Nieaktywne uniformy pomijaj po lokalnym lookup; wszystkie aktywne wartości instancji przesyłaj przed draw. Przed utworzeniem ShaderProgram dla nowego kodu waliduj raw GL compile/link z pełnym cleanup — libGDX gubi uchwyt nieudanego shadera i próbuje usunąć -1. Cache omija powtórną walidację/kompilację. Gradient polilinii uwzględnia środek i grubość, również przy jednym punkcie ROUND. Material nie działa na legacy polygon; standardowy SceneJson ma jawnie odrzucać zapis materiału, dopóki aplikacja nie dostarczy własnego codec/rejestru. materialsSmoke sprawdza rzeczywiste piksele, compile/link recovery, limit cache i glIsShader/glIsProgram po zamknięciu.

## Voxele (opcjonalny moduł)

`javity-voxel` ma CPU-only świat/mesher/DDA oraz adapter GPU, który pożycza atlas i posiada modele chunków. Chunk 16³, air 0, opaque-only; nie twórz GameObject na blok. Świat jawnie ładuje chunki i ma limit; floorDiv/floorMod dla ujemnych współrzędnych. Edycje brzegowe oraz load/unload unieważniają tylko sąsiadów ścianowych. Nie modyfikuj tablic danych z pominięciem dirty tracking.

Kolejność: aktualna kamera/frustum → adapter.update(camera,budget) poza ModelBatch → Game.step (inicjalizacja podmienionych komponentów) → GdxRenderer.render. Poprzedni model musi pozostać ważny po nieudanej przebudowie; nie zwalniaj pożyczonego atlasu przez Model.manageDisposable. Zamknij adapter przed atlasem i kontekstem GL.

Indeksy Mesh to unsigned short: checkerboard 16³ ma 12288 ścian, 49152 wierzchołki i 73728 indeksów, więc nie myl liczby indeksów z zakresem indeksu wierzchołka. UV obrazu top-left używa niskiego V, górna krawędź ściany próbkuje v0. Zachowaj testy CPU winding/UV/seams/DDA i rzeczywiste native pixel/resource smoke; nie deklaruj streamingu, fizyki, przezroczystości ani multiplayer na podstawie dema.

Każde załadowanie chunku ma niezerowy `chunkGeneration`; poza załadowaniem wartość wynosi 0. Adapter porównuje generację, aby unload/reload tej samej pozycji przed kolejnym update nie pozostawiał starej geometrii ani historii. Nowy adapter zasiewa dirty work wszystkich istniejących chunków. Jeden aktywny konsument kolejki dirty na świat; nie deklaruj jednoczesnych wielu adapterów. Tryb --voxels włącza pauseWhenLostFocus; native smoke sprawdza rzeczywisty focus okna GLFW (helper trzeba jawnie pokazać).

`contentRevision` i `collisionSnapshot` są odrębne od renderowej kolejki dirty. Opcjonalny bridge Bullet nie czyści jej i nie posiada zasobów renderera. Gotowość oznacza dokładnie opublikowaną generację/rewizję wszystkich chunków dotkniętych sprawdzanym AABB; brakujące lub przestarzałe dane blokują tę politykę. To nie tworzy natywnej granicy: zwykłe dynamiczne ciała mogą wyjść poza załadowany teren. Patrz `docs/voxels.md`.

## MapLibre Native

`javity-maplibre` jest opcjonalny i korzysta z prawdziwego Native FFI 0.202610.0; binding/runtime aktualizuj razem. Ordinary JDK25, mały wewnętrzny most Kotlin, publiczne API Java. `MapLibreDesktop.configure` jawnie wybiera Linux x64 system EGL/GLES. `glesAdapterJar` zachowuje wyłącznie oficjalną klasę GLES z libGDX, bez ANGLELoader/sterowników ANGLE. Nie przełączaj pozostałych demonstracji na GLES; GLSL1.20 Shapes wymaga osobnej adaptacji.

Pakiet offline jest relokowalny: manifest, standardowe style v8, MBTiles/MVT, sprites i glyphy. Nie interpretuj samodzielnie warstw/expressions; nie dodawaj GameObject dla każdego tile/feature. Globalne Native network status jest OFFLINE; nie deklaruj per-map izolacji sieci. Zachowuj licencje danych/fontów i notices natywnego runtime.

MapLibreMap posiada runtime/map/session, pożycza host i Batch. Tworzenie/render/resize/close na wątku GL przed zamknięciem okna. Native frame utrzymuj do Batch.flush; fence konsumenta przed ponownym zapisem/resize/close jest wymagany (release frame nie czeka na GPU libGDX). Nie resetuj samplera Native Texture przez zwykły upload Texture. Przywracaj tint/blending i prawidłowe UV. Native MapOptions.scaleFactor jest immutable; zmiana DPI odtwarza mapę/session, zachowując kamerę, imperative layer edits i feature-state. Native loadedStyleJson nie zastępuje rejestru tych edits. Query dotyczy ostatniego renderu; setter wymaga następnego update zanim jego stan pojawi się w query.

Po zmianach uruchom maplibreSmoke i porównaj prawdziwe Native/GLJS screenshoty tego samego pakietu. TMS/gzip/PBF/winding i deterministyczność sprawdzają generatory CPU. Nie deklaruj Android/Windows/macOS, custom GLJS layers/shaders ani pełnej GL JS parity z testu Linux. Szczegóły docs/maplibre-native.md.

## Rzeczywiste dane Gorzowa

Demo `--gorzow` korzysta z oddzielnego pakietu OSM/OpenMapTiles/OpenFreeMap, nie z autorskiej geometrii miasteczka. Zachowuj ODbL, widoczną atrybucję i snapshot/hash źródeł w fixture-source.json. Nie oznaczaj tych danych jako autorskich Apache; tylko style/importer są kodem projektu. Importer jest jawnie uruchamianym narzędziem developerskim, nigdy częścią startu/builda gry.

`BundledOfflineMap` posiada rozpakowany katalog i sprawdza SHA/limity; zamknij Native przed katalogiem i oknem GL. Rzeczywiste etykiety wymagają także zakresu SDF8192–8447 (typograficzna interpunkcja), poza polskimi 0–511. Po zmianach uruchom gorzowSmoke i sprawdź oryginalną bazę/cleanup oraz faktyczne zapytania o wodę/drogi/budynki. Zgodność z java-lab opisuje docs/java-lab-component-parity.md; NativeMap wrapper nie jest jeszcze gotowym komponentem sceny.

## Controllery postaci

CharacterController2D ma trwały zamiar ruchu w jednostkach świata/s i lokalne opcjonalne granice; nie steruje Box2D. VoxelFpsController wymaga Transform3D (pozycja stóp), ma CPU AABB, grawitację i jump buffer; niezładowane komórki są barierą. Nie deklaruj ogólnej fizyki 3D ani streamingu. W demie FPS kamera jest synchronizowana po Game.step, przed cullingiem; swobodny lot jest osobnym trybem debugowania.

## Narzędzia runtime

RuntimeInspector w javity-ui pożycza Stage/UiTheme i Supplier<Game>; posiada własne Yoga tree. Aktualizuj go na UI thread z nieskalowanym czasem. Host ustawia kolejność inputProcessor() → Stage → gizmoInputProcessor() → gra; przy zamknięciu usuwa oba procesory inspektora z multiplexer i zamyka inspector przed theme. To inspektor sceny gameplay z typowaną edycją transformacji i undo/redo, nie drzewo React/UiDocument ani pełny edytor. Gizmos używają typowanych sesji transformacji. Eksport runtime wymaga jawnych SceneJson codec i docelowego Path; prepare koduje jeden niezmienny kandydat i raportuje wszystkie problemy, save zapisuje go atomowo. Nie pomijaj nieobsługiwanych komponentów ani nie wywołuj writerów ponownie podczas save. Writery muszą być czyste; zapis runtime nie zastępuje źródłowej sceny projektu. Zachowuj izolację zaznaczenia przy wymianie Game z powtarzającymi się ID. Kolejność aktualnych zadań: docs/work-queue.md.

## Własność avataru

`AvatarBinding`/`AvatarView` pożyczają kanoniczną instancję, kości, materiały i tekstury; zbieranie renderables nie zmienia zegarów ani source enabled. `PreparedAvatarGeometry` posiada tylko wygenerowaną siatkę: owner close blokuje nowe bindingi, ostatni binding zwalnia bufory na wątku GL po końcu batcha. Źródłowy Model/tekstury muszą przeżyć wszystkie bindingi. Podział wymaga jawnych autorskich regionów; proceduralne demo FPS/TPP nie potwierdza automatycznej klasyfikacji ani importowanych rigów produkcyjnych.

## GLES i zasoby kontekstu

SmoothShapeRenderer zachowuje desktop GLSL120 i wybiera lokalnie GLES300 dla GLES3; nie zmieniaj globalnych prefixów ShaderProgram. Własne materiały przechodzą tę samą konwersję tokenów. Białe tekstury SmoothShapeRenderer i UiTheme są managed i zachowują posiadany Pixmap do końca życia tekstury. Zwalniaj teksturę przed Pixmap; pause/resume nie jest dowodem odtworzenia rzeczywiście utraconego kontekstu. Ograniczone buildy Android APK, testy JNI/ART Yoga/QuickJS, smoke modelu/dotyku przy density 160/320 i rzeczywista utrata/odzyskanie EGL są potwierdzone; nie oznacza to pełnej gotowości Androida. ActivityFIX3 i responsiveFIX1 są zintegrowane; wszystkie pięć przypadków instrumentacyjnych finalnego APK density 320 przeszło osobno (w tym Activity recreate, landscape, EGL recovery i touch/model/React), pełny łączny runner39046532187 to5/5 PASS na API35 x86_64 KVM, patrz docs/android-status.md. Debug/test i R8 release build oraz audyty APK przeszły; ograniczony R8 release runtime przeszedł na lokalnie podpisanej kopii; fizyczny ARM, wydajność urządzenia i Android MapLibre/Bullet pozostają poza potwierdzonym zakresem. Pełny desktop build po integracji FBO potwierdził 646 testów w 75 suite oraz presentation3dSmoke, postprocessSmoke (GL30/GL20), avatarGl30Smoke i physics3dSmoke.

## Lokalny streaming i światła sceny

`AssetStreams` przejmuje wyłącznie skonfigurowany, pusty AssetManager. Żądania, publikacja i `finishFrame` należą do owner thread; ostatni zamknięty lease pozostawia cache, eksmisja następuje dopiero przy presji po renderze. Najpierw odłącz konsumentów modelu/tekstury, potem zamknij lease. Jedna fizyczna transakcja loadera naraz zapewnia dokładne rozliczenie zależności; statystyki pamięci są szacunkami z rezerwacjami. Pinned adaptery wewnętrzne libGDX wymagają audytu przy zmianie jego wersji. Szczegóły: `docs/asset-streaming.md`.

`RenderEnvironment3D` jest jawnym wyborem oświetlenia świata; bez niego pozostaje historyczne `renderer.environment`. Nowe światła/fog wymuszają zwykły rendering zamiast niezgodnego shadera instancingu. Viewmodel zachowuje osobne historyczne światło bez mgły/nieba. `SkySettings` pożycza cubemap; shader i mesh posiada renderer. Zachowuj opisany w `docs/scene-lighting.md` kontrakt stanu GL20 oraz hook odzyskiwania rzeczywistych lokalizacji uniformów i atrybutów. Desktop GL20/GL30 nie zastępuje osobnej walidacji nowych funkcji na Androidzie.


## Workspace multiplayer development

`./gradlew dev` bootstrapuje lokalną paczkę npm i installDist workera; Java LocalDevServer nadzoruje Node Colyseus z readiness/control pipe i bounded cleanup. `devPort` wybiera port. Development snapshot endpoints są opt-in i wyłącznie loopback; `--container` wybiera jawne 0.0.0.0 bez devtools. Liczba JVM na pokój nie jest kontraktem publicznego API — aktualny worker jest polityką izolacji hosta. Server view czyta authority, nie symuluje i nie dodaje sesji. Nie deklaruj pełnego netcode, dowolnej gry, zdalnego deploy ani Wasm backendu na podstawie tego przykładu. Zmiany procesów sprawdzaj realnym npm integration/process testem po zbudowaniu installDist.


## Eksperymentalne Javity/libGDX Wasm

`experiments/javity-wasm` jest osobnym buildem gdx-teavm1.6.1/libGDX1.14.2/TeaVM0.15.0. `gdx_teavm_web_wasm_build` najpierw odświeża core przez rootwrapper, potem generuje prawdziwy Wasm GC i webapp. Actual GdxRenderer/ModelRenderer/Game/Component mają Chromium/SwiftShader pixel/input/resize/dispose proof; nie oznacza to pełnego portu UI/Yoga/physics/MapLibre ani wydajności GPU. Zachowaj jawne reflection metadata cache shaderów. TeaVM0.15 nie ma Arrays.compare(float[]); przenośna pętla w SceneLightingAttribute zachowuje kolejność Float.compare. Instrukcja: docs/javity-wasm-rendering-spike.md.

## Maintenance, public documentation and Pages

- This `AGENTS.md` is the canonical agent instruction file; do not create a second `agent.md` with divergent rules. Update `docs/Bugs/` for confirmed defects, `docs/Spec/` for contracts and `docs/TODO.md` for unfinished work. Record reproduction, source version, exact gate and limitations before closing a bug.
- Keep README and `CHANGELOG.md` user-facing. Use Unreleased until an actual release; distinguish API/platform changes from verified runtime support. Never claim full OneJS/Tailwind/Feel/Animancer compatibility.
- `site/` is the static GitHub Pages showcase. Use local credited screenshots and dependency-free HTML/CSS/JS. Verify at `/JavityEngine/`, mobile and desktop, before publication; link examples to the current default branch. A pushed workflow is not a verified deployment.
- Keep `.gitignore` focused on generated output/local configuration. Curated evidence under docs and experiments/evidence, lockfiles, sample configuration and the Gradle wrapper JAR remain versioned. Existing tracked logs stay tracked; add newly reviewed evidence logs explicitly with `git add -f` because disposable `.log` output is ignored. Never commit SDKs, AVDs, credentials or environment-specific Gradle/proxy settings.
- Default branch was verified as `master` on 2026-10-17. Fetch before merge, preserve remote changes, run the integrated build and never force-push to resolve divergence. Publication and merging require user authorization; the current maintenance task explicitly grants it.
- BUG-ANDROID-001 is closed for the hosted API35 x86_64 KVM full-suite gate by run39046532187, source5d2a206 (5/5, no skips). Preserve its emulator display/onboarding/focus preconditions and all gameplay assertions. This does not prove the historical TCG ANR cause, physical ARM64 support or performance. Local environments without SDK/adb cannot replace hosted runtime proof.
