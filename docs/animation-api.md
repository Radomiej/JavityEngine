# Sterowanie animacjami

Nowe API w `org.javity.animation` oddziela stan od renderowania. Klipy modelu mają pojedynczy kanał libGDX; `ModelAnimator` używa dokładnie kontrolera sibling ModelRenderer, który nie aktualizuje go ponownie. Shared Model nadal należy do loadera/AssetManager, a ModelInstance i stan są osobne dla obiektu.

```java
var renderer = object.addComponent(new ModelRenderer(model));
var animator = object.addComponent(new ModelAnimator());
// Po inicjalizacji obiektu:
var walk = animator.play("walk", PlayMode.LOOP);
walk.setSpeed(1.2).onLoop(() -> footstep());
walk.pause();
walk.resume();
var run = animator.crossFade("run", .15, PlayMode.LOOP);
```

Playback podaje time/duration/normalizedTime, speed, isPlaying/isPaused/isCompleted/isActive oraz restart i callbacki start/loop/complete. Speed jest skończone i nieujemne; 0 zatrzymuje zegar. WORLD pause/disable/inactive nadal obowiązują. Once kończy się dokładnie na końcu klipu. Zastąpiony stan staje się nieaktywny, jego późniejsza zmiana nie steruje nowym klipem.

Ze względów na koszt libGDX, update przekraczający 1024 pętle klipu lub niemieszczący się w native float jest odrzucany przed zmianą pozy. To błąd konfiguracji szybkości/długości klipu, nie ciche pominięcie callbacków. Callbacki podczas aktualizacji mogą zastąpić stan lub usunąć obiekt/renderera; dalsze zapisy są blokowane.

```java
var sprite = object.addComponent(new SpriteRenderer2D(region));
sprite.width = sprite.height = 32;
var player = object.addComponent(new SpriteAnimationPlayer());
player.register(new SpriteClip("idle", idleRegions, .1f));
player.register(new SpriteClip("attack", attackRegions, .08f));
// Po inicjalizacji obiektu:
player.play("attack", PlayMode.ONCE).onComplete(
    () -> player.play("idle", PlayMode.LOOP));
```

SpriteClip kopiuje listę, pożycza TextureRegion; nie alokuje ani nie zwalnia tekstur. Sprite ma jedną region/frame, więc crossfade sprite nie jest wdrożone. Stary SpriteAnimator pozostaje dostępny; jednoczesne stare/nowe animatory na jednym sprite zgłaszają konflikt. ModelRenderer.play(name,loop) pozostaje adapterem (działa także przed podpięciem komponentu).

Crossfade modelu nie oznacza layers/masks/mixer/IK/root motion/retargetingu. Nie importujemy gameplay event track z G3DJ/G3DB. Obsługiwane formaty loadera nadal OBJ (bez klipów), G3DJ/G3DB; GLB/PBR i współczesny Spine są osobnymi zadaniami. Syntetyczne testy animacji sprawdzają rzeczywistą pozycję node; test skinningu w GL trzeba raportować osobno.

`--plugins` pokazuje wygenerowaną siatkę z dwiema kośćmi i dwoma klipami. Native smoke sprawdza niezależne instancje, animowane macierze kości i zmianę pikseli geometrii przy tym samym viewport. To dowód skinningu libGDX, nie walidacja importu produkcyjnego rigu. [Podgląd](demo-plugins.png).

`play(...).onStart(callback)` uruchamia callback raz w pierwszej aktywnej, niepauzowanej aktualizacji komponentu, także przy delta=0 albo speed=0. Pozwala dopiąć callback po otrzymaniu handle. `restart()` kolejkuje nowe start; stop lub zastąpienie przed aktualizacją usuwa stare zdarzenie. Rejestracja po rozpoczęciu nie odtwarza zdarzenia wstecz. Callback może zastąpić stan albo usunąć obiekt, a animator przed dalszym zapisem ponownie sprawdza właściciela.

## Szablony przejść, seek i markery

```java
var attack = AnimationTransition.clip("attack").speed(1.2)
    .markers(new AnimationMarker("impact", .45));
var playing = animator.play(attack).onMarker("impact", () -> applyHit());
playing.seekNormalized(.25); // bez callbacków; rzeczywista poza aktualizuje się od razu
```

Szablon jest niemodyfikowalny i każde `play` tworzy osobny stan. `startAt(0..1)`, `loop()` i `fade(seconds)` konfigurują odtwarzanie; sprite odrzuca nonzero fade przed zmianą aktualnego klipu. `onMarker(name, normalizedTime, callback)` może też dodać marker do konkretnego playbacku.

`seek(seconds)` i `seekNormalized(0..1)` są synchroniczne, zachowują pause/speed i nie emitują start/marker/loop/complete. ONCE na końcu jest completed, LOOP na końcu wraca do 0. Wcześniejszy seek reaktywuje ukończony bieżący stan; stary handle nie zmienia zastępczego playbacku. Seek modelu w trakcie fade ustawia czas docelowego klipu i zachowuje postęp fade.

Marker 0 jest po `onStart` przy pierwszym kwalifikującym update, także dla delta=0; speed=0 wstrzymuje markery. Przekroczenia używają `(before, after]`. Na granicy pętli kolejność to marker 1 → onLoop → marker 0. Dodane/przypisane ponownie callbacki korzystają z następnego snapshotu. Pause, seek, restart, stop lub usunięcie właściciela blokują dalsze zdarzenia starego kroku.

Preflight odrzuca przekroczenie 1024 markers/cycles/callbacks przed zmianą pozy, zegara lub callbackiem start. Model ma też sprawdzenie reprezentowalności kroku kontrolera float przed callbackami. Jest to limit liczby wywołań, nie czasu dowolnego kodu callbacku. Testy obejmują rzeczywiste klatki i pozy nodes; macierze skinningu i piksele OpenGL są sprawdzane osobno przez native pluginsSmoke oraz Relay Ward.

## Mixery 1D i maskowane warstwy

Implementacja `ModelPoseAnimator` przeszła niezależny przegląd, 18 testów poz, pełne 210 testów rdzenia i rzeczywisty smoke OpenGL. Używa własnego samplera TRS na ModelInstance; nie uruchamia AnimationController dla każdej warstwy. API ma jeden bazowy klip/mixer i maksymalnie 8 uporządkowanych warstw override:

```java
var pose = object.addComponent(new ModelPoseAnimator());
var locomotion = LinearModelMixer.builder(1)
    .add("idle", 0).add("walk", 1).add("run", 2).build();
var base = pose.play(locomotion, PlayMode.LOOP);
base.setParameter(1.5);
var upper = pose.addLayer("upper", NodeMask.paths("root/spine/arm"));
upper.setWeight(0);
upper.play(AnimationTransition.clip("attack").markers(new AnimationMarker("hit", .45)))
    .onMarker("hit", () -> applyHit());
upper.fadeTo(1, .1);
```

Nazwy klipów i ścieżki muszą istnieć w załadowanym modelu. Przykład uruchamiaj po inicjalizacji albo na poprawnie podpiętym obiekcie przed startem. ModelPoseAnimator nie współistnieje z ModelAnimator na tym samym obiekcie. ModelAnimator pozostaje API rzeczywistego crossfade między klipami; pose/layer odrzuca niezerowe fade w AnimationTransition, zamiast udawać crossfade. `layer.fadeTo` zmienia wagę override, a nie miesza starego i nowego klipu.

Mixer interpoluje dwa sąsiednie progi i synchronizuje znormalizowaną fazę także dla klipów o różnych długościach. Parametry spoza zakresu trafiają na endpoint. Warstwy mają niezależne zegary. Brakujący kanał overlay zachowuje dolną pozę; brakujący kanał bazowy bierze bind pose. NodeMask.paths korzysta z pełnych ścieżek, NodeMask.ids dopuszcza tylko jednoznaczne identyfikatory. Nie ma additive/2D blend, IK ani root motion.

`ModelPoseAnimator` wiąże się z pierwszym ModelRenderer obiektu; dodatkowe renderery zachowują własne sterowanie legacy. Usunięcie pierwszego unieważnia jego stare playbacki i przywraca bind pose; kolejny renderer można potem wybrać jako pierwszy. Sprawdzenie `GameObject.containsComponent(component)` ma semantykę tożsamości instancji i nie kopiuje listy.

Zapisane próby `artifacts/j-model-*.png` pochodzą z rzeczywistego OpenGL: stała kamera, niezależne fragmenty siatki, parametry 0/.5/1, wagi maski 0/.5/1 i niezmieniony obszar poza maską. Pomiary potwierdzają 7340/14820 zmienionych pikseli dla midpoint/endpoint, 6054/10668 dla half/full maski i 0 w obszarze poza maską. Klasyczny seek daje 5883 zmienionych pikseli. Wspólne demo Relay Ward łączy mixer, upper masked layer, markers, gameplay, feedback i HUD; końcowe próby native/pakietu przeszły. [Wyniki](plugin-feature-coverage.md).
