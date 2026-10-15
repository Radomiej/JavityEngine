# Praca nad JavityEngine

## Cel i ustalenia

Upraszczamy i rozbudowujemy silnik 2D/3D na libGDX. Użytkownik dopuszcza migrację starych gier i API. Galaxy Rapid jest własnym backendem silnika; jego źródła mają być budowane lokalnie w tym repozytorium, bez zależności od historycznych snapshotów Sonatype.

## Struktura

- `javity-backend`: kod Galaxy Rapid, obecnie rendering 2D, Box2D, zasoby, kamera, wejście i Artemis ODB.
- `javity-core`: nowe API komponentów, indeksy sceny i rendering 2D/3D; bez Artemis.
- `javity-physics2d`: opcjonalny Box2D dla nowego rdzenia, bez Artemis; ciała, collidery i kontakty.
- `javity-engine`: historyczne API Javity, utrzymane do migracji gier.
- `javity-net`: HTTP i pobieranie zasobów.
- `javity-maplibre`: opcjonalny MapLibre Native FFI, lokalne MBTiles/MVT, style JSON, Linux x64 system EGL/GLES.
- `javity-map-core`: nowe mapy rastrowe XYZ bez legacy; provider, cache, kamera, markery/trasy.
- `javity-map`: opcjonalne mapy geograficzne OSM, markery i trasy; to nie mapy poziomów Tiled.
- `javity-desktop`: demo nowego rdzenia i opcjonalne demo legacy.
- `javity-ui`: rzeczywisty Yoga/Flexbox dla Scene2D i ograniczony katalog klas stylów.
- Android, XML UI i generator projektów pozostają historycznymi modułami poza głównym reactorem do czasu osobnej migracji.

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
- Artemis jest przejściowym szczegółem backendu legacy. Nowe funkcje gameplay dodawaj do javity-core. Nie zastępuj go innym ECS bez konkretnej potrzeby i pomiarów; nie udostępniaj encji Artemis w nowym API gameplay.

## 3D i mapy

- Oddziel transformacje 2D od pełnych transformacji 3D. Obrót 3D przechowuj jako quaternion; kolejność warstw 2D nie jest współrzędną Z świata.
- Model jest współdzielonym zasobem; każdy obiekt ma własną instancję i stan animacji.
- Rendering modeli i fizyka 3D są osobnymi etapami.
- Mapy mają pozostać opcjonalne. Docelowo oddziel projekcję, dostawcę kafelków, cache i renderer; unikaj osobnego GameObject dla każdego kafelka.
- Testy nie powinny wymagać serwerów OSM. Provider i zasoby powinny być wymienne.

## Zasady zmian i weryfikacji

Działający build, nowy rdzeń, Box2D i przykłady 2D/3D/UI już istnieją. Adapter map, czcionki FreeType i Camera2D/follow są dostępne. Bieżący backlog: docs/TODO.md; audyt braków: docs/migration-api.md. Priorytetem jest migracja pozostałych funkcji legacy przed MapLibre/Shapes/multiplayer. Nie łącz mechanicznego przenoszenia kodu ze zmianą jego zachowania bez potrzeby. Zachowuj informacje o pochodzeniu i licencjach w `THIRD_PARTY.md`; Spine ma własne warunki licencji.

Diagnozuj przyczynę błędów przed poprawką. Dodawaj testy regresji dla zmian zachowania i uruchamiaj cały reactor przed deklarowaniem sukcesu. Raportuj osobno: kompilację, testy i rzeczywiste uruchomienie graficzne. Nie deklaruj działania 3D, Androida, współczesnego Spine ani map online na podstawie samej kompilacji.

Nie wysyłaj commitów ani nie publikuj wydań bez polecenia użytkownika. Nie deleguj pracy do innych agentów bez wyraźnej instrukcji użytkownika.

## UI Yoga

UI używa natywnej Yoga przez LWJGL; obecnie moduł jest desktopowy. Nie deklaruj Android/web bez bindingów i osobnych testów. Klasy stylów mają jawny katalog w UtilityStyles; nie ignoruj nieznanych klas i nie deklaruj pełnego CSS/Tailwind. Zamykaj Flex i UiTheme; Stage.dispose() nie zwalnia węzłów Yoga. Odłączony kontener pozostaje własnością wywołującego.

Nie nadpisuj wersji gdx-jnigen-loader, gdx-ai ani innych niezależnie wersjonowanych rozszerzeń wersją głównego libGDX.

## Fizyka i fazy sceny

`GameSystem` rejestruj przed startem. Fazy: snapshot skali czasu → systemy `onUiUpdate` → komponenty UI → systemy before → WORLD → systemy after (fizyka) → late update → sprzątanie. `onUiUpdate` używa nieskalowanego czasu i działa podczas pauzy/freeze. Systemy należą do jednej sceny i są zamykane wraz z nią, także po błędzie callbacku. Rejestruj dostawcę zasobów przed konsumentem: po cleanup komponentów systemy zwalniają się w odwrotnej kolejności rejestracji, np. SoundManager przed SceneAssets. Nie iteruj bezpośrednio po kolekcji systemów w fazach, w których callback może zamknąć grę.

Box2D ma stały krok 1/60 s i jawny przelicznik jednostek świata na metr. Jedna bryła i wiele colliderów na obiekt; geometria kolizji jest niezależna od grafiki. Nie udostępniaj natywnych Body/Fixture w gameplay API. Zdarzenia kontaktu kolejkuj i dostarczaj dopiero po odblokowaniu World; sprawdzaj usunięcie i enabled/active przed każdym odbiorcą. Testy fizyki muszą używać rzeczywistego Box2D, bez wymagania OpenGL.

UI ma `Rectangle`, `rounded` i `border`; wspólny shader należy do UiTheme. Nie twórz nowego shadera w każdej klatce ani nie zostawiaj zmienionego shadera Batch po rysowaniu widgetu. Zaokrąglenie jest wizualne, nie przycina dzieci i nie zmienia hitboxa.

## Mapy, czcionki i kamery

MapRenderer posiada cache GPU, zwalnia Pixmap ze źródła i wykonuje Batch.flush przed możliwą eksmisją tekstury. Nie zwalnia caller-owned Batch ani TileSource. Provider FileTileSource jest lokalny i synchroniczny; nie deklaruj map online/MapLibre na podstawie raster demo. Uwzględniaj odmienne konwencje Y: MapView bottom-left, Camera2D/Input top-left.

Domyślny font jest bundlowany z licencją; UiTheme zarządza FreeType atlasem albo pożyczonym BitmapFont zgodnie z ownsFont. Zachowuj polskie glyphy. Camera2D bierze pozycję/obrót z Transform2D; FollowTarget2D działa po fizyce i nie może wskazywać innej sceny. Nie dodawaj globalnej main camera do nowego API.

Dokumentuj nowe ustalenia użytkownika w docs/TODO.md i powiązanych notatkach. Multiplayer jest na razie projektem: nie deklaruj transportu/replikacji bez implementacji i testów.

## Runtime po aktualizacji bazy

AudioSource/SceneAssets mają jawną własność, SceneTimers/SceneManager czas i lifetimes sceny. onRelease zawsze sprząta constructor-owned resources, niezależnie od onCreate; cleanup kontynuuje po błędach. InputActions.finishFrame dopiero po Game.step; po focus loss releaseAll/cancelAll. Flex overlay używa childrenOnly.

Render2D ma wspólne layer/order dla sprite/text/shapes. Nie kompiluj shaderów i nie trianguluj stałych figur każdą klatkę. Serializacja to SceneJson v1 + jawne codeki; nie wprowadzaj arbitralnej refleksyjnej instancjacji z nazw klas w pliku.

Mapy online używają bounded TileByteCache/AsyncTileSource/HttpTileFetcher. URL/User-Agent/atrybucja jawne, testy na lokalnym HTTP. Audio smoke może użyć ALSOFT_DRIVERS=null (sprawdza native API bez odsłuchu). Nie deklaruj konwersji historycznego Spine 2.1.27 do współczesnego runtime. Szczegóły w docs/finish-update.md.

## Figury SDF

SmoothShapeRenderer posiada współdzielone zasoby GPU. Pochodne GLSL i fizyczny framebuffer sterują AA; SCREEN używa logicznych pikseli i następuje po WORLD. Polilinia ma maksymalnie 64 efektywne punkty i ROUND joins; transparentne łączenia wymagają pojedynczej sumy SDF. Polygon pozostaje bez AA/obrysu. Zachowuj testy pikseli shapesSmoke po zmianach renderera.

## Porty UI/animacji/feedback

`javity-script` jest opcjonalnym hostem GraalJS Community i React; rdzeń nie zależy od JS. TSX buduj w web/javity-ui, aplikacje importują shim src/runtime.js do już istniejącego hosta. Nie bundluj drugiej kopii React. Jawne JavityGame actions/primitive values zastępują refleksyjny dostęp do Java. Runtime close przed UiDocument close; oba na wątku UI. Schedulery hosta mają limit callbacków, który nie ogranicza dowolnego JS ani Graal Promise jobs.

EffectSystem należy do sceny, rejestruj przed startem. Dzierżawy identyfikują właściciela właściwości; stary handle nie może nadpisywać nowego efektu. ModelAnimator i legacy ModelRenderer muszą używać pojedynczego AnimationController; SpriteAnimationPlayer nie współistnieje z legacy SpriteAnimator na tym samym obiekcie. Model/Sprite zasoby dostarczone przez caller nie przechodzą na własność animatora. Testy animacji mają obejmować pozę i usunięcie właściciela, nie tylko czas.

Natywny markup/React obsługuje jawne typy/props UtilityStyles. Nie deklaruj pełnego HTML/CSS/Tailwind ani Unity API. UiDocument posiada własne węzły Yoga, pożycza UiTheme/Stage. Layout Yoga nie powinien być nadpisywany tweenem pozycji/szerokości Actor; animuj alpha albo jawne transformacje poza layoutem.

`Radio`/`Signal` i enum `StateMachine` są scene-local; to nie transport multiplayer. `TimeManager` posiada niezależne dzierżawy, WORLD używa snapshotu skali danej klatki, UI jest nieskalowane. `ProgressBar` animuje własny model/fill, bez zapisywania geometrii Yoga; keyed reparent jest odłączeniem od parenta, nie terminalnym `remove`/`close`.

`SoundManager` pożycza Sound/Music, posiada konkretne native voice IDs i własne duck handles. Distinct Music streams mogą współistnieć; pojedynczy Music jest exclusive wobec własnej dzierżawy. Nigdy globalne Sound.stop() do anulowania jednego efektu. `isActive` oznacza dzierżawę, nie wykrycie końca natywnego Sound. FloatingTextPool pożycza font i resetuje sloty/generacje; render i native OpenAL muszą mieć osobny desktop smoke od recording mocks.

ModelPoseAnimator ma własny per-instance sampler TRS, rzeczywisty 1D mixer i maksymalnie 8 warstw masked override. Wiąże pierwszy ModelRenderer; pozostałe renderery mają własny legacy controller. Nie współistnieje z ModelAnimator ani drugim pose ownerem. GameObject.containsComponent(Component) sprawdza tożsamość bez kopiowania listy; używaj go w sprawdzaniu lifetimes konkretnej instancji. Clip fade w pose/layer API jest jawnie odrzucane; layer.fadeTo zmienia wagę, a ModelAnimator pozostaje API clip crossfade.

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
