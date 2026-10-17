# JavityEngine — przewodnik praktyczny

Stan: 2026-10-16. Ten guide opisuje istniejące API. Stan zaakceptowanego batcha (FPS, narzędzia, streaming i otoczenie) opisują [kolejka prac](work-queue.md) i instrukcje poniżej; dokument [projektu](next-runtime-batch.md) zachowuje historyczny punkt wyjścia.

## 1. Wybierz i uruchom przykład

Wymagania desktopu: pełne JDK25 i OpenGL. Polecenia uruchamiaj z katalogu repozytorium; Gradle Wrapper pobiera właściwy Gradle.

| Cel | Polecenie |
| --- | --- |
| Voxel FPS z autorskim kilofem i przełączanym TPP | `./gradlew :javity-desktop:run --args='--voxels'` |
| Ten sam rodzaj gry z opcjonalną fizyką Bullet | `./gradlew :javity-desktop:run --args='--physics3d'` |
| Drzewo obiektów, komponenty, transformacje i gizma | `./gradlew :javity-desktop:run --args='--inspector'` |
| Feedback, zegary, scena i React HUD | `./gradlew :javity-desktop:run --args='--mm'` |
| Otoczenie: dzień/noc, mgła, directional/point/spot i typowany authoring | `./gradlew :javity-desktop:run --args='--environment'` |
| Lokalny streaming typed lease | `./gradlew :javity-desktop:run --args='--asset-streaming'` |
| Prezentacyjne efekty kamery/modelu 3D | `./gradlew :javity-desktop:feel3d` |
| Porównanie kontrolek z HTML | `./gradlew :javity-desktop:run --args='--ui-parity'` |

**Voxel FPS:** WASD — ruch, mysz — rozglądanie, Space — skok, Shift — sprint, F5 — FPS/TPP, E — zamach bez edycji świata, T — pokaż/ukryj kilof, C — ubranie, F — lot debugowy, Escape — kursor, LMB/RMB — usuń/dodaj blok, 1–5 — wybór bloku. Pierwsze kliknięcie przechwytuje kursor. Każde kolejne naciśnięcie LMB usuwa jeden blok przez istniejący raycast i uruchamia zamach; przytrzymanie LMB lub E nie powtarza akcji. Fokus UI, pauza WORLD i utrata fokusu okna blokują użycie. Po powrocie fokusu kliknij, aby ponownie przechwycić kursor.

![Voxel FPS: prawa dłoń, drewniany trzonek i cyjanowa głowica kilofa](artifacts/voxel-fps/task2-idle.png)

To rzeczywisty zrzut renderera. Kamera startuje skierowana na blok w zasięgu kopania, a mały HUD pozostawia miejsce na teren. Prawa dłoń, drewniany trzonek i stopniowana głowica pochodzą z autorskiego modelu i atlasu. Postać ma jeden animator; chód, sprint, skok oraz zamach używają tej samej pozy. Lekki recoil trafia wyłącznie do kamery viewmodelu przez `PresentationEffects3D` i jawny provider `CameraPresentation3D(VIEWMODEL)`. Nie zmienia transformacji gracza, kamery świata ani raycastu. Pass viewmodelu czyści depth, więc kilof pozostaje widoczny przy ścianie i po resize. [Zrzuty i pomiary ROI](artifacts/voxel-fps/README.md), [konfiguracja avatarów](avatars.md#fpstpp-voxel-demo-task-4c).

Android ma osobny launcher i demo dotykowe; te dowody dotyczą desktopu: [instrukcja Androida](../javity-android-app/README.md).

## 2. Scena i komponenty

Nowy gameplay opiera się na `org.javity.core.Game`, `GameObject` i `Component`. Legacy Galaxy Rapid/Artemis pozostają adapterami migracyjnymi. Nie potrzebujesz encji Artemis do nowej gry.

```java
Game game = new Game();
GameObject player = game.createObject("Player");
Transform3D transform = player.addComponent(new Transform3D());
transform.position.set(0, 2, 4);
game.start();
// Każda klatka: game.step(deltaSeconds).
// Koniec życia sceny: game.close().
```

Dodaj importy `org.javity.core.Game`, `GameObject`, `Transform3D`. Wiele komponentów tego samego typu jest dozwolone. `getComponent(Type)` zwraca pierwszy zgodny albo null, `getComponents(Type)` wszystkie zgodne, `hasComponent(Type)` obecność. Przy potrzebie pobrania użyj jednego `getComponent` i sprawdzenia null. `requireComponent` służy sprawdzeniu konfiguracji, nie sterowaniu ruchem przez wyjątki.

Systemy (`GameSystem`) dodawaj przed `start()`. Komponenty dodane później inicjalizuje następny krok gry. `Transform3D` ma pozycję, quaternion i skalę; Transform2D każdego obiektu nie zastępuje transformacji 3D. Wybierz jednego właściciela ruchu: controller albo fizyka. Inspektor respektuje `TransformEditPolicy` i nie teleportuje brył przez bezpośredni zapis transformacji.

## 3. Tree viewer i gizma

Uruchom `--inspector`, a potem użyj F1 do otwierania i zamykania panelu w ModernDemo. F1 działa również w `--voxels`, `--physics3d` i `--environment`: używa tej samej Game, zwalnia kursor i daje UI pierwszeństwo przed gameplay. W voxelach i Bullet Focus wymaga jawnego trybu lotu debugowego. Launcher Androida ma osobny, ograniczony zakres.

![Drzewo GameObjectów i inspektor sceny](demo-runtime-inspector.png)

Panel pokazuje obiekty aktywne i nieaktywne, hierarchię pierwszego Transform3D oraz wszystkie komponenty, także wyłączone i powtórzone. Hierarchia 2D pozostaje płaska. Wybierz obiekt, zmień typowane pola i kliknij Apply. Undo/redo ma 256 wpisów we wspólnej historii transformacji i typowanych parametrów światła. Wybranie światła w RuntimeToolsOverlay pokazuje osobny draft RGB/intensity/range/cutoff/exponent; Apply zapisuje jedną operację, a zmiana sceny, komponentu lub stanu odrzuca nieaktualny draft. Range i cutoff nie są polami directional. Gizma pokazują kierunek, zasięg i stożek; socket prawej dłoni w voxelach pokazuje kanoniczne osie bez zmiany kości. Przyciski MOVE/ROTATE/SCALE oraz LOCAL/WORLD i pole snapu sterują uchwytami osi; jeden drag tworzy jedną operację historii. Kontrolki UI mają pierwszeństwo przed światem. Focus loss, zmiana sceny/kamery lub resize anuluje podgląd przeciągania.

Do własnego hosta przekaż aktualną scenę przez Supplier<Game>, kamery przez `setGizmoCameras`, ustaw kolejność wejścia inspector → Stage → gizma → gameplay i rysuj gizma między światem a Stage. [Pełny przykład integracji, ograniczenia rodziców i eksport](runtime-tools.md).

Eksport wymaga jawnego `SceneJson`, codeców zasobów i ścieżki. Snapshot nie jest automatycznym zapisem projektu; nieznany komponent blokuje zapis zamiast znikać z pliku. `--scene-export=/absolute/path/runtime-scene.json` działa w ModernDemo.

## 4. Ładowanie zasobów podczas gry — działa już teraz

`SceneAssets` posiada libGDX `AssetManager`. `queueSceneAssets` kolejkuje pracę, `update(budgetMillis)` posuwa ją naprzód, a `get` pobiera wyłącznie gotowy zasób. Operacje managera wykonuj na wątku renderującym; loader może przygotowywać część danych asynchronicznie, a upload GPU kończy się na tym wątku. Budżet jest miękki: pojedynczy upload/loader może potrwać dłużej.

Poniższy kompletny przykład wymaga własnego pliku modelu pod przekazaną ścieżką internal (G3DB/G3DJ/OBJ). Nie jest on dołączonym assetem gry. Utwórz obiekt dopiero po inicjalizacji libGDX, np. w `ApplicationListener.create()`, wywołuj `frame` z render i `close` z dispose. Host odpowiada za resize oraz czyszczenie framebufferu przed renderem.

```java
import com.badlogic.gdx.graphics.g3d.Model;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.services.AssetSpec;
import org.javity.services.SceneAssets;

public final class AsyncModelExample implements AutoCloseable {
  private final Game game = new Game();
  private final SceneAssets assets = game.addSystem(new SceneAssets());
  private final GdxRenderer renderer = new GdxRenderer();
  private final String path;
  private boolean attached;

  public AsyncModelExample(String path) {
    this.path = path;
    assets.queueSceneAssets(new AssetSpec<>(path, Model.class));
    GameObject camera = game.createObject("Camera");
    camera.addComponent(new Transform3D()).position.set(0, 2, 6);
    camera.addComponent(new Camera3D(67));
    game.start();
  }

  public void frame(float deltaSeconds) {
    if (!attached && assets.update(2)) {
      GameObject model = game.createObject("Loaded model");
      model.addComponent(new Transform3D());
      model.addComponent(new ModelRenderer(assets.get(path, Model.class)));
      attached = true;
    }
    game.step(deltaSeconds);
    renderer.render(game);
  }

  public void resize(int width, int height) {
    renderer.resize(width, height);
  }

  @Override public void close() {
    try { game.close(); } finally { renderer.dispose(); }
  }
}
```

Nie wywołuj `load()` ani `finishLoading()` w głównej pętli, jeśli zależy Ci na ładowaniu etapowym: te API blokują. Zwrócony Model jest pożyczony — komponent go nie zwalnia. `SceneAssets` zamyka zasoby po komponentach przy zamknięciu Game. `unload(path)` jest bezpieczne dopiero po odłączeniu wszystkich użytkowników modelu/tekstury i zakończeniu rysowania. Nie niszcz zasobu, którego używa ModelInstance lub bieżący batch.

`progress()` jest monotoniczne dla życia SceneAssets; po zakończeniu pierwszej partii nie jest procentem nowej partii. Gotowość tej partii sprawdzaj wynikiem `update`, a poszczególny plik przez `assetManager().isLoaded`. Wskaźnik nie jest licznikiem bajtów ani zużyciem GPU.

Powyższy przykład używa istniejącego `SceneAssets`. Opcjonalny **`javity-assets`** udostępnia teraz scene-local typed lease, kolejkę priorytet/FIFO, limity oraz cache z bezpieczną eksmisją. Model/tekstura żyją przez cały okres używania lease; po ostatnim close zostają cache, a presja kolejki może je usunąć wyłącznie po rysowaniu w `finishFrame()`. Najpierw odłącz ModelRenderer, potem zamknij jego lease. Nie przekazuj jednego managera równocześnie do obu usług.

Uruchom `./gradlew :javity-desktop:run --args='--asset-streaming'`: R pobiera dwa lease autorskiego modelu z teksturą, 1 zwalnia pierwszy, X odłącza model i zwalnia oba, P/O pokazują prefetch/release, E jawne failure/retry, Space pauzę WORLD. Panel rozdziela stany requestów i liczniki ready/failed bieżącej partii od root stats; nie pokazuje procentu bajtów. [API i zasady własności](asset-streaming.md), [native dowody](artifacts/asset-streaming/README.md). Android streaming pozostaje **UNVERIFIED**.

HTTP, hot reload i automatyczny streaming terenu są osobnym zakresem. `AssetSceneLoadJob` + `SceneManager.requestLoading` obsługują przygotowanie nowej sceny, anulowanie kandydata i kontrolowaną podmianę. [Przewodnik usług](game-authoring.md), [projekt](next-runtime-batch.md).

## 5. Feedback, animacje, UI

Do tweenów i kompozycji użyj `EffectSystem`, `FeedbackPlayer` i niemutowalnych opisów `Feedback.sequence/parallel`. Zasoby dźwięku/freeze/efektu mają uchwyty; anulowanie zwalnia uchwyty tego playbacku. WORLD podlega pauzie, UI używa czasu nieskalowanego. [Przykłady](feedback-api.md).

`PresentationEffects3D` daje modelom i kamerom punch, shake, recoil, bob i FOV. Nie przesuwa fizyki ani stanu gameplay. Świat i viewmodel mają osobne powierzchnie prezentacji. [Presety i konfiguracja](feel3d.md), [particles](particles3d.md), [world postprocess](world-postprocess.md).

W overlay F1 panel Feel korzysta z dokładnie zaznaczonego obiektu: wybierz preset use-tool/hit/landing/sprint, powierzchnię i WORLD/UI, edytuj typowane parametry, następnie Play/Pause/Resume/Stop. Wymaga istniejącego managera `PresentationEffects3D` danego zegara i pasującego providera; brak konfiguracji pokazuje w statusie, bez automatycznego dodawania komponentów. Anuluje tylko własny playback przy zmianie zaznaczenia/sceny i close. [Konfiguracja i przykład API](feel3d.md#panel-podglądu-feel), [rzeczywisty panel](artifacts/feel-preview/panel-model.png). Przejście sceny demonstruje istniejący tryb `--mm` z `SceneManager`.


`ModelPoseAnimator` obsługuje klipy, warstwy, maski i crossfade. Widoczność części avataru nie usuwa kości potrzebnych socketom. [Animacje](animation-api.md), [avatary](avatars.md). Są to własne odpowiedniki wybranych zachowań Animancer/Feel, bez deklaracji pełnego API Unity.

UI jest opcjonalne: Scene2D/Yoga bez JS albo React/TSX na wspólnym hoście. Zwykły JDK25 + GraalJS działa na desktopie, QuickJS na Androidzie; nie potrzebujesz dystrybucji GraalVM. Katalog UtilityStyles jest ograniczony i jawny, nie pełny HTML/CSS/Tailwind. [TSX i hot reload UI](onejs-port.md), [porównanie HTML](ui-html-parity.md).

## 6. Światło i tło — aktualny stan

`RenderEnvironment3D` jest scene-local systemem rejestrowanym przed startem. GameObject z `Transform3D` i `DirectionalLight3D`, `PointLight3D` lub `SpotLight3D` dostarcza światło świata; kierunek to world-transformed lokalne -Z. Ambient, gradient/cubemap i mgła należą do environment. Domyślne limity sceny to 2 directional / 4 point / 2 spot; nadmiar wybierany jest deterministycznie i raportowany. Scena bez tego systemu zachowuje historyczne `GdxRenderer.environment`.

`DayNightCycle3D(sun, environment, daySeconds)` używa zegara WORLD i właścicieli tej samej Game. Faza 0 to północ, .25 wschód, .5 południe, .75 zachód. Steruje słońcem, ambient i gradientem; pożyczony cubemap pozostaje tym samym obrazem. `--environment` pokazuje kamienny dziedziniec: 1 popołudnie, 2 ostatnie światło, 3 północ, 4 mgła; przyciski włączają cykl, pauzę i lokalne światła. F1/wybór lampy otwiera typowany authoring. `--voxels` jawnie wybiera nowe otoczenie z zatrzymaną fazą poranną.

Mgła obejmuje zwykłe modele świata także bez normals; nie obejmuje Shapes, własnych shaderów ani UI. Pass viewmodelu celowo zachowuje historyczne światło bez scene fog/sky, więc dłonie mogą mieć inne oświetlenie niż świat. Nowe scene environment wymusza zwykły rendering kandydatów instancingu. To forward lighting bez PBR/IBL, dynamicznych cieni, HDR/bloom i wolumetryki. [API i własność](lighting-and-sky.md), [dokładny kontrakt shaderów i sky pass](scene-lighting.md), [zrzuty i bramki](artifacts/environment/README.md). Nowe funkcje na Androidzie pozostają **UNVERIFIED** do rzeczywistej bramki GLES/EGL.

![Dziedziniec: popołudnie](artifacts/environment/afternoon.png)
![Dziedziniec: ostatnie światło](artifacts/environment/last-light.png)

## 7. Jak sprawdzać własną zmianę

`./gradlew build` sprawdza reactor desktopowy. `environmentSmoke` (GL20/GL30 i osobny sky gate), `voxelSmoke`, `inspectorSmoke`, `avatarSmoke`, `avatarGl30Smoke` i `feel3dSmoke` wymagają rzeczywistego OpenGL. Kompilacja ani test headless nie dowodzą widoczności rąk, światła lub shaderów. [Aktualne dowody Androida](android-status.md) rozdzielają testy CPU, JNI, piksele, lifecycle i ograniczenia sprzętu. [Indeks pozostałych instrukcji](README.md).


## 8. Darmowy model z prawdziwymi animacjami

`python3 tools/animation-validation/run.py --interactive` otwiera Fox z trzema importowanymi klipami: 1 Survey, 2 Walk, 3 Run, 4 crossfade, 5 mikser50%, 6 maska ogona, Space pauza. [GIF i wyniki](artifacts/imported-animation/README.md), [instrukcja i licencja](../tools/animation-validation/README.md). `run.py` bez `--interactive` wykonuje asercje i zamyka aplikację; `--gl30` wybiera drugi testowany profil. Model jest lokalnym fixture G3DJ przekonwertowanym z glTF, więc nie oznacza to nowego loadera GLB w runtime.


## 9. Instancing modeli i animowanych tłumów

[Jak włączyć InstancedModel, skonfigurować Fox24/4 i zarządzać lifecycle](gpu-instancing-guide.md). GPU palette skinning działa z niezależnymi pozami; cache CPU jest osobnym opt-in ze zmierzonym narzutem. [Benchmark, porównania i granice Androida](artifacts/instancing/README.md).

## 10. Lokalny multiplayer

`javity-rooms` wykonuje headless symulację Java, a `web/local-multiplayer` wystawia Colyseus Room i preview z dwoma oficjalnymi klientami SDK. Uruchomienie, JDK/Node wymagania, authority, cleanup oraz precyzyjny zakres proofu są opisane w [lokalnych pokojach Colyseus](local-multiplayer.md). Preview jest klientem DTO i nie oznacza browser/Wasm builda silnika.
