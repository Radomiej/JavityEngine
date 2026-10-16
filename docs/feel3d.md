# Efekty odczuć 3D

`PresentationEffects3D` dodaje ograniczone efekty prezentacyjne do modelu lub jednego z dwóch widoków kamery. Służą do wizualnej informacji zwrotnej, takiej jak uderzenie, odrzut, drgania, kołysanie i skok pola widzenia (FOV). Wkłady te wpływają wyłącznie na renderowanie: nie przesuwają transformacji rozgrywki, nie zmieniają fizyki ani nie edytują kości modelu. Interaktywny przykład desktopowy i test GL30 korzystają z tego samego publicznego API.

## Konfiguracja sceny

Dodaj kanoniczną transformację i odpowiedni provider prezentacji do każdego obiektu, który ma otrzymywać efekty. Obiekt widoku może mieć oba providery kamery. Jeśli używasz callbacku końca efektu, dodaj `EffectSystem` przed `Game.start()`; callbacki są wywoływane przez ten system sceny. Komponenty inicjalizują się podczas `start()` lub pierwszego kroku, więc przed odtwarzaniem efektów uruchom grę i wykonaj krok.

```java
Game game = new Game();
game.addSystem(new EffectSystem()); // before start when using completion callbacks

GameObject actor = game.createObject("target");
actor.addComponent(new Transform3D());
actor.addComponent(new Presentation3D());
PresentationEffects3D actorEffects = actor.addComponent(
    new PresentationEffects3D(Clock.WORLD));

GameObject view = game.createObject("view");
Camera3D worldCamera = view.addComponent(new Camera3D(60));
CameraPresentation3D worldView = view.addComponent(
    new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
CameraPresentation3D handsView = view.addComponent(
    new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
PresentationEffects3D cameraEffects = view.addComponent(
    new PresentationEffects3D(Clock.WORLD));

ParticleBurstPool3D particles = game.addSystem(new ParticleBurstPool3D());
game.start();
game.step(0);
```

Manager obiektu wymaga `Presentation3D`; manager kamery wymaga providera odpowiadającego wybranej powierzchni. Jeśli nie używasz callbacków końca efektu, możesz pominąć `EffectSystem`. `PresentationEffects3D` obsługuje zegar `Clock.WORLD` (domyślny) i `Clock.UI`. Przy użyciu `PresentationEffects3D.feedback(...)` ustaw taki sam zegar managera i `FeedbackPlayer`.

## Odtwarzanie efektów

Presety są niezmiennymi opisami i można ich używać wielokrotnie. Każde wywołanie `play` tworzy osobne odtwarzanie i własny addytywny lease providera. Na przykład:

```java
actorEffects.play(PresentationEffects3D.punchScale(
    .25, .25, .25, 3, .8, .45));

cameraEffects.play(PresentationEffects3D.recoil(
    PresentationEffects3D.Surface.VIEWMODEL_CAMERA,
    .16, .025, 0, .1, .05, 0, 4, .8, .55));
cameraEffects.play(PresentationEffects3D.shake(
    PresentationEffects3D.Surface.WORLD_CAMERA,
    .035, .025, .015, .008, .01, .006, .3, 16, seed));
cameraEffects.play(PresentationEffects3D.fovKick(
    PresentationEffects3D.Surface.WORLD_CAMERA, 2.5, 3, .8, .35));
```

`Surface.MODEL` wpływa na lokalną prezentację modelu. `WORLD_CAMERA` i `VIEWMODEL_CAMERA` wybierają niezależne providery widoków kamery. Pozycje podaje się w lokalnych jednostkach świata, obroty i fazy kołysania w radianach, czas i czas trwania w sekundach, częstotliwość w hercach, a zmiany FOV w stopniach. Wartości uderzenia i odrzutu są prędkościami początkowymi; wartości sprężyny określają jej cel.

Dostępne presety to `punchPosition`, `punchScale`, `punchAngular`, `springToOffset`, `shake`, `recoil`, `timeBob`, `distanceBob`, `fovKick` i `fovSpring`. Zmiana celu przez `Playback.setGoal(...)` lub `setFovGoal(...)` nie resetuje bieżącej pozycji ani prędkości sprężyny. Dla kołysania można w trakcie działania podawać dane przez `setTimeBobIntensity(...)` lub `setDistanceBobInput(intensity, phaseRadians)`; intensywność należy do zakresu `[0,1]`. Fazę kołysania zależnego od dystansu dostarcza kod ruchu gry.

Odtwarzanie można wstrzymać przez `pause()`, wznowić przez `resume()` lub anulować przez `cancel()`. Anulowanie usuwa tylko wkład tego odtwarzania, po czym widoczna jest aktualna bazowa transformacja lub projekcja kamery. `onComplete(...)` wywołuje się po naturalnym zakończeniu, nie po anulowaniu; wymaga `EffectSystem` dodanego przed startem. Wyłączenie komponentu managera zatrzymuje jego zegar i zachowuje ostatnią próbkę providera; po ponownym włączeniu odtwarzanie rusza dalej. Wyłączenie lub usunięcie providera bądź jego obiektu unieważnia odtwarzania i ich uchwyty.

Manager obsługuje do 64 odtwarzanych efektów naraz. Każdy provider modelu lub kamery ma też 64 sloty współdzielone przez trwałe kanały i przejściowe lease'y. Trwałe kanały i przejściowe lease'y należą do kodu, który je utworzył, i należy je zamykać, gdy nie są już potrzebne. Wkłady muszą być skończone i mieścić się w obsługiwanym zakresie; stosuj niewielkie przesunięcia, aby ich suma pozostała reprezentowalna.

## Aktualizacja kamer i kolejność renderowania

Po symulacji zaktualizuj rig, podając bieżące bazowe położenie oka, kierunek patrzenia i oba odpowiadające providery kamery. Rig kopiuje i łączy ich wartości przed aktualizacją macierzy kamery. Do renderowania świata przekaż gotową kamerę świata, a do osobnej warstwy modelu widoku gotową kamerę viewmodel. Bazową projekcję zmieniaj przez metody rig, aby efekty FOV nakładały się na jej aktualną wartość.

```java
CameraRig3D rig = new CameraRig3D(worldCamera, viewmodelCamera, obstructionQuery);
// Each frame, after simulation:
game.step(delta);
rig.update(eye, lookDirection, null, worldView, handsView);

postProcess.renderWorld(() -> {
    renderer.renderWorld(game, worldCamera, avatarView);
    particleRenderer.draw(particles, worldCamera.camera, spark,
        0, 0, viewportWidth, viewportHeight);
});
renderer.renderViewmodel(rig.viewmodelCamera(), handsModel);
drawHud();
```

W przykładzie postprocessing obejmuje geometrię świata i cząstki, a następnie poza tą warstwą renderowane są viewmodel i HUD. Dzięki temu desaturacja świata nie obejmuje nakładki. Efekty cząstek działają w przestrzeni świata, a renderer pożycza jawnie przekazaną, zaktualizowaną kamerę i teksturę. Szczegóły kolejności warstw i własności opisują strony [Cząstki 3D](particles3d.md) i [Postprocessing świata](world-postprocess.md).

## Demo i dowód działania

Uruchom interaktywne demo desktopowe poleceniem `./gradlew :javity-desktop:feel3d`. Wymaga ono rzeczywistego kontekstu GL30. WASD porusza kamerą, Spacja lub lewy przycisk myszy wystrzeliwuje efekt, B przełącza kołysanie viewmodelu, P włącza lub wyłącza postprocessing świata, a `+`/`-` zmienia bazowy FOV świata. Drugi wiersz HUD pokazuje bazowy i wynikowy FOV, liczbę cząstek oraz fazę kroku.

Uruchom test renderowania do bufora poleceniem `./gradlew :javity-desktop:feel3dSmoke`. Zapisuje on zrzuty i pomiary w [artefaktach feel3d](artifacts/feel3d/README.md). To dowód działania na desktopowym GL30; integracja Androida jest nadal sprawdzana. API udostępnia natywne dla silnika efekty prezentacyjne i nie deklaruje zgodności z komercyjnym API Unity, zgodności z produkcyjnym rigem kamery ani zweryfikowanych haptycznych wrażeń na Androidzie.
