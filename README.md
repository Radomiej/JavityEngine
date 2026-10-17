# JavityEngine

Silnik Java do gier 2D i 3D, modernizowany z historycznej wersji Javity. Nowy rdzeń ma prosty model `Game → GameObject → Component`, inspirowany API Java-lab. Oryginalny Galaxy Rapid jest teraz lokalnym modułem repozytorium.

## Uruchomienie

Wymagane pełne **JDK 25 LTS**; ustaw `JAVA_HOME`. Wrapper pobiera **Gradle 9.8.1** i weryfikuje checksum dystrybucji. Silnik używa **libGDX 1.14.2**, **LWJGL 3.4.3** i natywnej Yoga.

```sh
./gradlew clean build
./gradlew :javity-desktop:run
./gradlew :javity-desktop:smoke
./gradlew :javity-desktop:run --args='--map'
./gradlew :javity-desktop:mapSmoke
./gradlew :javity-desktop:run --args='--runtime'
./gradlew :javity-desktop:runtimeSmoke
./gradlew :javity-desktop:run --args='--plugins'
./gradlew :javity-desktop:pluginsSmoke
./gradlew :javity-desktop:run --args='--materials'
./gradlew :javity-desktop:materialsSmoke
./gradlew :javity-desktop:run --args='--voxels'
./gradlew :javity-desktop:voxelSmoke
./gradlew :javity-desktop:run --args='--mm'
./gradlew :javity-desktop:mmSmoke
./gradlew :javity-desktop:run --args='--legacy --smoke'
./gradlew :javity-desktop:installDist
```

Na Windows użyj `gradlew.bat`. Demo pokazuje model OBJ, fizyczny sprite 2D, podłoże i trigger oraz panel Yoga z obramowaniami i zaokrągleniami. Pauza zatrzymuje świat i Box2D; UI działa dalej. Zmiana rozmiaru okna przelicza layout. Smoke sprawdza 120 klatek, kontakty Box2D, kliknięcia pauzy/wznowienia, resize i piksele prostokąta, zapisuje `smoke.png` i zamyka aplikację.

Uruchomienie wymaga ekranu i OpenGL; na Linux bez monitora użyj Xvfb. Dystrybucja jest w `javity-desktop/build/install/javity-desktop`. Natywna Yoga jest dobierana do systemu budowania; zweryfikowano Linux x64.

## Moduły

| Moduł | Rola |
| --- | --- |
| `javity-core` | Nowy rdzeń i rendering 2D/3D; bez Artemis |
| `javity-physics2d` | Opcjonalny Box2D dla nowego rdzenia, collidery i kontakty |
| `javity-physics3d` / `javity-voxel-physics3d` | Opcjonalny desktopowy Bullet i most fizyki chunków |
| `javity-voxel` | Opcjonalny świat blokowy: chunki 16³, meshing, DDA i adapter GPU |
| `javity-ui` | Yoga/Flexbox, deklaratywne Scene2D i klasy stylów |
| `javity-script` | Przenośny host React/TSX; `javity-script-graal` na desktopie, `javity-script-quickjs-android` na Androidzie |
| `javity-desktop` | Demo nowego rdzenia i demo legacy |
| `javity-backend` | Galaxy Rapid z Gita, budowany lokalnie |
| `javity-engine` | Historyczne API Javity do migracji |
| `javity-net` | Historyczne HTTP, dostosowane do libGDX |
| `javity-maplibre` | Opcjonalny MapLibre Native: własne MBTiles/MVT, style, etykiety i query; Linux x64 EGL |
| `javity-map-core` | Nowe mapy XYZ, lokalny provider, cache, markery i trasy; bez legacy |
| `javity-map` | Historyczny plugin geograficzny OSM do migracji |

Nowe `javity-android-app`, `javity-ui-yoga-android` i `javity-script-quickjs-android` włącza `-PjavityAndroid=true`: JDK25, compileSdk36, API35+, GLES3, APK ARM64/x86_64. [Build i uruchomienie](javity-android-app/README.md), [wyniki testów i ograniczenia](docs/android-status.md). Zwykły desktop build nie wymaga Android SDK. Historyczne `javity-android`, XML UI i generator projektów pozostają poza reactorem. Desktop wybiera osobny provider `javity-ui-yoga-lwjgl`.

## Komponenty

```java
Game game = new Game();
GameObject player = game.createObject("player").setPosition(120, 80);
player.addComponent(new PlayerController()); // Twoja klasa extends Component.
PlayerController controller = player.getComponent(PlayerController.class);
if (controller != null) {
    // Użyj pobranego komponentu.
}
game.start();
game.step(1f / 60f);
```

Własne klasy dziedziczą po `org.javity.core.Component`. Hooki: `onCreate`, `onUpdate(float delta)`, `onLateUpdate(float delta)` i `onDestroy`. Dostęp do właściciela przez `getGameObject()`, sceny przez `getGame()`, transformacji 2D przez `getTransform()`.

Wiele komponentów jednego typu jest dozwolone. Zapytania uwzględniają dziedziczenie. `hasComponent` sprawdza obecność; `getComponent` zwraca pierwszy zgodny komponent lub null, `getComponents` wszystkie. `requireComponent` jest opcjonalną kontrolą konfiguracji i rzuca wyjątek przy braku. Gdy potrzebujesz referencji, pobierz ją raz zamiast `hasComponent` + `getComponent`.

`getObjectsWith(Type...)` używa indeksów sceny, a tagi osobnego indeksu. Wyniki są niemodyfikowalnymi snapshotami w stabilnej kolejności. Obejmują także nieaktywne obiekty i wyłączone komponenty; renderer i aktualizacja filtrują je oddzielnie.

Komponent dodany podczas aktualizacji zaczyna pracę w kolejnej klatce; usunięty natychmiast przestaje być aktualizowany. `onDestroy` wykonuje się raz dla zainicjalizowanego komponentu. `game.pause()` zatrzymuje WORLD; komponenty z `updateMode = Component.UpdateMode.UI` pracują dalej. Delta jest w sekundach, ograniczona do 0.1 s. Rdzeń jest jednowątkowy.

## Modele 3D

```java
GdxRenderer renderer = new GdxRenderer(); // Po utworzeniu kontekstu OpenGL.
Model model = renderer.loadModel("models/ship.g3db");
GameObject ship = game.createObject("ship");
Transform3D transform = ship.addComponent(new Transform3D());
transform.position.set(0, 0, 0);
ship.addComponent(new ModelRenderer(model));
Camera3D camera = game.createObject("camera").addComponent(new Camera3D(60));
camera.camera.position.set(0, 1, 5);
camera.camera.lookAt(0, 0, 0);
```

`Transform3D` ma Vector3, quaternion i hierarchię rodziców; `Transform2D` ma kąty w radianach i Y skierowane w górę. Każdy ModelRenderer ma osobny ModelInstance i AnimationController, model pozostaje współdzielony. AssetManager renderera obsługuje OBJ/G3DJ/G3DB. Zasoby ładowane przez renderer zwalnia `renderer.dispose()`; nie zwalniaj ich ponownie w komponentach. Modele dostarczone z zewnątrz pozostają własnością wywołującego.

Passy: modele z głębią, sprite'y, opcjonalny debug fizyki, następnie UI. GLB/glTF/PBR i fizyka 3D nie są jeszcze włączone.

## Fizyka 2D

Dodaj zależność od `javity-physics2d`; na desktopie również natywne biblioteki `gdx-platform` i `gdx-box2d-platform` z classifierem `natives-desktop`.

```java
Game game = new Game();
Physics2D physics = game.addSystem(new Physics2D(new Vector2(0, -980), 100));
GameObject floor = game.createObject("floor").setPosition(200, 30);
floor.addComponent(new BoxCollider2D(400, 20)); // Bez rigidbody: ciało statyczne.
GameObject player = game.createObject("player").setPosition(200, 180);
RigidBody2D body = player.addComponent(new RigidBody2D().setFixedRotation(true));
player.addComponent(new CircleCollider2D(16));
game.start();
game.step(1f / 60f);
```

Fizyka pracuje po aktualizacji WORLD i przed `onLateUpdate`, na stałym kroku 1/60 s. Ograniczenie delty Game do 0.1 s nadal obowiązuje. W przykładzie 100 jednostek świata to metr; pozycje, rozmiary colliderów, grawitacja i prędkość API są w jednostkach świata, obrót w radianach. Masa jest w kg, density w kg/m². Skalowanie grafiki nie zmienia collidera.

Jeden `RigidBody2D` na obiekt; wiele colliderów tworzy compound body. Obsługiwane typy: STATIC, DYNAMIC i KINEMATIC. Prostokąty/koła mają offset, density, friction, restitution, sensory oraz 16-bitowe filtry category/mask. `setLinearVelocity(x,y)`, `setAngularVelocity(...)`, `setGravityScale(...)`, `setBullet(...)` i damping konfigurują ciało bez ujawniania natywnych uchwytów.

Systemy rejestruj przed `game.start()`. Native bodies są synchronizowane w kroku gry; po starcie `game.step(0)` pozwala utworzyć je bez przesuwania czasu. `physics.refresh()` jawnie synchronizuje konfigurację bez symulacji. Zmiana transformacji teleportuje ciało, a zmiana konfiguracji budzi je. Wyłączenie obiektu lub rigidbody zatrzymuje jego ciało; wyłączenie collidera usuwa fixture. Komponent dodany w aktualizacji jest wiązany w kolejnej klatce.

Komponent może implementować `CollisionListener2D`: `onCollisionEnter/Exit(Contact2D)` i `onTriggerEnter/Exit(Contact2D)`. Zdarzenia są zgłaszane dla par colliderów, nie agregowane dla całych compound bodies. Callbacki są wykonywane po odblokowaniu World i mogą usuwać obiekty albo zamknąć scenę.

```java
Runnable unsubscribe = collider.onContactEnter(PlayerController.class, other -> {
    PlayerController player = other.getComponent(PlayerController.class);
    // Reakcja na obiekt z tym komponentem.
});
unsubscribe.run(); // Odpięcie callbacku.
```

`game.dispose()` zamyka systemy i natywny World. `physics.drawDebug(renderer.camera2D)` rysuje collidery po sprite'ach, przed UI. Wymaga OpenGL; same testy symulacji go nie potrzebują.

## UI w stylu Tailwind

```java
UiTheme theme = new UiTheme();
Flex panel = new Flex(theme, "flex-col p-6 gap-4 bg-slate-900 rounded-xl border-2 border-slate-700");
panel.add(theme.label("Ekwipunek", "text-xl text-cyan-400"), "");
panel.add(theme.rectangle("bg-slate-800 rounded-lg border-2 border-cyan-400"), "w-full h-12");
panel.add(theme.button("Zamknij", "bg-blue-600 rounded-lg border border-cyan-400", panel::remove), "w-full h-12");
stage.addActor(panel);
panel.setSize(320, 240);
```

Layout liczy prawdziwa Yoga przez LWJGL. Scene2D dostarcza widgety i wejście. Klasy kontenera określają layout/tło, klasy `add` rozmiar elementu, klasy `theme.label/button` wygląd widgetu. Jednostka odstępów to 4 jednostki świata UI. Zamykaj Flex i UiTheme: Stage.dispose() nie zwalnia węzłów Yoga. Zamknięcie kontenera zwalnia dołączone podkontenery; odłączony kontener trzeba zamknąć jawnie.

Przykłady obsługiwanych klas (pełniejszy opis: [deklaratywne UI](docs/declarative-ui.md)):

- `flex-row`, `flex-col`, `flex-wrap`, `flex-nowrap`, `flex-1`, `grow`, `shrink-0`;
- `items-start/center/end/stretch`, `justify-start/center/end/between`;
- `p-N`, `px-N`, `py-N`, `m-N`, `gap-N`, `w-N`, `h-N`, `w-full`, `h-full`, `w-auto`, `h-auto`;
- `text-sm/base/lg/xl/2xl`, `text-COLOR`, `bg-COLOR`;
- `rounded`, `rounded-none/sm/md/lg/xl/2xl/3xl/full`, `rounded-N`;
- `border`, `border-N`, `border-COLOR`;
- kolory: white, black, slate-900/800/700/400/200, cyan-400, emerald-500, blue-600/700, red-500, amber-400.

`rounded-N` oznacza promień N×4; `border-N` grubość N jednostek (bez mnożnika). Yoga uwzględnia border wewnątrz kontenera. Promień i obramowanie renderera są ograniczane do połowy krótszego boku. Wspólny shader signed-distance rysuje tło i border, zachowując promień przy resize; `rounded-full` tworzy kształt kapsuły lub koła. Zaokrąglenie tła nie przycina dzieci ani nie zmienia prostokątnego hitboxa Scene2D.

`theme.rectangle(classes)` tworzy widget `org.javity.ui.flex.Rectangle`; rozmiar określa rodzic Flex albo `setSize`. Klasy `rounded` i `border` działają też na kontenerach i przyciskach. Nieznane klasy zgłaszają błąd. To ograniczony katalog inspirowany Tailwindem, nie pełny CSS. Text ma opt-in wrap oraz ponowny pomiar po zmianie treści, fontu i szerokości. Różnice metryk fontu blisko progu zawijania są opisane w [porównaniu HTML](docs/ui-html-parity.md). Breakpointy i pełne arkusze CSS pozostają poza zakresem. Yoga UI jest obecnie desktopowe.

## Status migracji

Artemis ODB 2.3.0 pozostaje w legacy. Nowy rdzeń nie potrzebuje zewnętrznego ECS. Numer 2.4.0 z upstream README nie odpowiada opublikowanej wersji Maven Central podczas tej modernizacji; ostatni commit domyślnej gałęzi upstream pochodzi z 2021 r.

Historyczny plugin map jest budowany lokalnie i ma poprawione konwersje współrzędnych. Nowy adapter rastrowy XYZ, cache i renderer są w javity-map-core; asynchroniczny HTTP provider jest dostępny i testowany na lokalnym serwerze; GeoJSON pozostaje do migracji. Spine pozostaje na runtime 3.5.51.1; zgodność ze współczesnymi eksportami nie jest deklarowana.

Zobacz [AGENTS.md](AGENTS.md), [wyniki i status](docs/modernization-status.md) oraz [pochodzenie kodu](THIRD_PARTY.md).

## Kamera 2D

```java
GameObject cameraObject = game.createObject("camera").setPosition(400, 300);
Camera2D camera = cameraObject.addComponent(new Camera2D());
camera.resize(800, 600);
camera.setZoom(1);
FollowTarget2D follow = cameraObject.addComponent(new FollowTarget2D(player));
follow.setSmoothing(5);
Vector2 aim = camera.directionFromScreen(player.transform.position.x,
    player.transform.position.y, mouseX, mouseY);
```

GdxRenderer używa pierwszej aktywnej Camera2D; konwersje ekranu mają Y w dół, zgodnie z Input. Pozycja/obrót pochodzą z Transform2D. [Pełny kontrakt kamer](docs/camera-api.md).

## Czcionki

Domyślny UiTheme używa dołączonego Open Sans 16 px (Apache-2.0), wygenerowanego raz przez FreeType, z polskimi znakami. Własny TTF/OTF: `UiTheme.fromTrueType(file, 18)`; dodatkowe języki: overload z jawnym zestawem znaków. Bitmap font: `new UiTheme(font, ownsFont)`. Theme zwalnia własne fonty, nie zwalnia pożyczonych. Tworzenie/zamykanie odbywa się na wątku GL. To atlas rasteryzowanych glyphów, bez automatycznego shaping/RTL czy emoji fallback.

## Mapy nowego rdzenia

```java
MapView view = game.createObject("map").addComponent(
    new MapView(new GeoCoordinate(51.4027, 21.1471), 12));
view.resize(width, height);
MapRenderer mapRenderer = new MapRenderer(new FileTileSource(tileDirectory), 64);
game.createObject("marker").addComponent(new MapMarker(view.getCenter()));
// Po game.start(), wewnątrz batch.begin/end z pikselową kamerą:
mapRenderer.render(game, view, batch);
// Przy zamykaniu, poza aktywnym Batch: mapRenderer.dispose();
```

Kafelki lokalne: `z/x/y.png`, XYZ 256×256. MapRenderer nie tworzy obiektu dla każdego kafelka i posiada ograniczony cache tekstur. `MapRoute` rysuje listę GeoCoordinate. Demo `--map` używa syntetycznych kafelków offline i Open Sans z polskimi znakami; mapSmoke zapisuje `map-smoke.png`. MapView ma screen Y w górę; dla input przelicz `height - mouseY`.

Powyższy moduł obsługuje raster. Dla wektorów jest osobny **`javity-maplibre`**, oparty na rzeczywistym MapLibre Native FFI 0.202610.0: własne MBTiles/MVT, style JSON v8, sprites/glyphy, polskie etykiety, pan/zoom i feature query/state. Linux x64, jawny tryb EGL/GLES; wymaga systemowych EGL/GLES i zwykłego JDK 25. [API, format pakietu i ograniczenia](docs/maplibre-native.md).

```sh
./gradlew :javity-desktop:run --args='--maplibre'
./gradlew :javity-desktop:maplibreSmoke
```

## Dokumentacja i dalsza migracja

[Indeks docs](docs/README.md), [TODO](docs/TODO.md), [inwentaryzacja API](docs/migration-api.md), [lokalne pokoje Colyseus](docs/local-multiplayer.md), [roadmap multiplayer/browser](docs/rooms-and-browser-roadmap.md) oraz [Shapes/shadery](docs/shapes-and-shaders.md). Bieżącą kolejność prac wyznacza [work-queue](docs/work-queue.md); bounded local rooms są wdrożone, migracja konkretnych gier i szerszy netcode pozostają osobnym zakresem.

## Uzupełnione funkcje runtime

AudioSource (Sound/Music), SceneAssets, SceneTimers, SceneManager, InputActions/PointerPicker, TextRenderer2D, ShapeRenderer2D, SpriteAnimator i Prefab/SceneJson dostępne w javity-core. Physics2D ma polygon, raycast i kolejkę sił/impulsów; javity-map-core ma asynchroniczny HTTP provider i ograniczony cache dyskowy.

```java
SceneManager scenes = new SceneManager();
scenes.request(() -> {
    Game game = new Game();
    SceneTimers timers = game.createObject("timers").addComponent(new SceneTimers());
    timers.after(1, false, () -> System.out.println("Sekunda czasu świata"));
    return game;
});
scenes.step(delta); // current() to aktualna scena do renderowania.
// Przy zamykaniu: scenes.close();

SceneJson schema = PhysicsSceneCodecs.install(SceneJson.standard());
Prefab prefab = new Prefab(schema.write(selectedObjects));
List<GameObject> copies = prefab.instantiate(game, schema);
```

[Kontrakty i ograniczenia usług](docs/finish-update.md), [demo runtime](docs/demo-runtime.png). TestApp wzorce są w `--runtime`; nie jest to automatyczny import całych historycznych gier. GitHub Actions workflow jest przygotowany lokalnie do build/test/smoke po przyszłym push.

### Figury z wygładzaniem

Koła, prostokąty/rounded border i polilinie mają SDF antialiasing, końcówki BUTT/ROUND/SQUARE oraz przestrzeń WORLD/SCREEN. Zobacz [API i ograniczenia](docs/shapes-and-shaders.md) oraz [podgląd](docs/demo-shapes.png). Demo: `./gradlew :javity-desktop:run --args='--shapes'`; test GPU: `./gradlew :javity-desktop:shapesSmoke`.

Własne materiały GLSL: `ShapeMaterial2D` i komponent `ShapeShaderAnimator` z czasem WORLD/UI. Gotowe gradient/pulse/stripes zachowują SDF AA i obrys. [API i przykład](docs/shapes-and-shaders.md#materiały-glsl-jako-komponenty-shapes), [galeria](docs/demo-shape-materials.png).

### UI, animacje i feedback inspirowane Unity

Nowe API obejmuje `TweenPlayer`/`FeedbackPlayer`, `ModelAnimator` i `SpriteAnimationPlayer`, natywne `UiDocument`/markup oraz opcjonalny host React/JSX w `javity-script`. Są to odpowiedniki dopasowane do Javity; publiczne OneJS MIT posłużyło jako wzorzec bridge. Pełne API Unity, Animancer i Feel nie jest deklarowane.

[Feedback](docs/feedback-api.md), [animacje](docs/animation-api.md), [deklaratywne UI](docs/declarative-ui.md), [OneJS/TSX](docs/onejs-port.md). Budowanie aplikacji TSX: `cd web/javity-ui && npm ci && npm run build`. Bundlowany Java runtime działa bez Node.

Demo portów: `./gradlew :javity-desktop:run --args='--plugins'`. Przycisk JSX zmienia stan React i wywołuje Java feedback; obok są natywne kontrolki, animowany atlas i proceduralna siatka z dwiema kośćmi. [Podgląd](docs/demo-plugins.png). Test GPU: `:javity-desktop:pluginsSmoke`.

Wspólne demo Relay Ward (`--mm`) łączy strzelanie/leczenie, 3D mixer i masked layers, effects/hit-stop/audio, transakcyjne sceny oraz HUD React/Yoga porównany z prawdziwym HTML/Tailwind. Zrzuty i pomiary generowane przez smoke: `artifacts/mm-systems`.

Praktyczne przykłady: [tworzenie gry](docs/game-authoring.md). Rozbudowę i wyniki testów śledzi [pokrycie Feel/Animancer/OneJS](docs/plugin-feature-coverage.md); końcowy zestaw przeszedł testy native, HTML i gotowej paczki, w tym dev reload. [Porównanie screenshotów](docs/ui-html-parity.md#hud-relay-ward).

## Świat voxelowy

`--voxels` pokazuje teksturowany, skończony teren z 18 chunków i edycję bloków. Domyślny FPS używa `VoxelFpsController`: WASD i mysz, Space skok, Shift sprint oraz grawitacja i kolizje AABB z blokami; `F` przełącza swobodny lot debugowy. F5 przełącza FPS/TPP z autorską animowaną postacią i osobnym widokiem rąk/narzędzia. Esc zwalnia kursor, pierwszy klik go przechwytuje. Numerami 1–5 lub kółkiem wybierzesz blok; LMB usuwa, RMB stawia. To nie jest ogólny solver fizyki 3D ani streaming świata; [avatar i ograniczenia rigu](docs/avatars.md).

[API, własność i ograniczenia](docs/voxels.md), [screenshot](docs/demo-voxels.png), [oryginalny atlas](docs/voxel-atlas.png). Generator tekstur działa offline: `python3 tools/generate-voxel-atlas.py`.

## Rzeczywista mapa Gorzowa Wielkopolskiego

```sh
./gradlew :javity-desktop:run --args='--gorzow'
./gradlew :javity-desktop:gorzowSmoke
```

Lokalny pakiet 38 kafelków OpenFreeMap/OpenMapTiles z prawdziwymi danymi OSM; ulice, budynki, Warta i polskie etykiety. Uruchomienie nie pobiera danych z sieci. Linux x64 EGL, jak pozostałe demo Native. [Widok i pochodzenie danych](docs/gorzow-offline-map.md), [porównanie komponentów z java-lab](docs/java-lab-component-parity.md).

### Sterowanie postaciami

`CharacterController2D` zapewnia normalizowany ruch w jednostkach świata i opcjonalne granice — [API](docs/character-controller-2d.md). `Component` udostępnia również skróty wyszukiwania i usuwania komponentów właściciela.

Voxelowe demo startuje w trybie FPS: WASD, mysz, Space skok, Shift sprint; F przełącza swobodny lot. `VoxelFpsController` ma grawitację, kolizje AABB i blokowanie krawędzi załadowanego świata — [API i ograniczenia](docs/voxel-fps-controller.md).

Opt-in demo Bullet 3D uruchamiane jest przez `./gradlew :javity-desktop:run --args='--physics3d'`
lub `./gradlew :javity-desktop:physics3d`; dodaje natywny kontroler kapsułowy, bryły dynamiczne,
kinematic platform, trigger, edytowalne voxele oraz F5 FPS/TPP i F3 lazy debug draw. Nie zmienia
ustalonego trybu AABB `--voxels`. [API, granice i dowód GPU](docs/physics3d.md), [artefakty](docs/artifacts/physics3d/).

### Inspektor sceny w runtime

F1 w podstawowym demie otwiera panel na naszym UI Yoga: GameObjecty, komponenty i transformacje. Start od razu z panelem: `./gradlew :javity-desktop:run --args='--inspector'`. Panel obsługuje typowaną edycję Transform2D/Transform3D, undo/redo, gizmos local/world ze snappingiem oraz jawnie konfigurowany eksport runtime SceneJson (`--scene-export=/absolute/path/runtime-scene.json`). Właściciele ruchu blokują bezpośrednią edycję, a assety wymagają jawnych codeców/resolverów. Drzewo UiDocument/React i diagnostyka reload pozostają osobnym etapem. [Instrukcja](docs/runtime-tools.md), [zrzut](docs/demo-runtime-inspector.png), [audyt OneJS](docs/onejs-coverage-audit.md), [kolejność kolejnych prac](docs/work-queue.md).

Przewodnik od pierwszego uruchomienia: [guide silnika](docs/engine-guide.md). Demo blokowego FPS z rękami/narzędziem: `./gradlew :javity-desktop:run --args='--voxels'`; drzewo GameObjectów i gizma: `--inspector`, F1. [Rzeczywisty obecny widok FPS](docs/artifacts/avatar/voxel-fps.png).
