# Narzędzia sceny i Feel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tree viewer i gizma działają także w voxelach/Bullet, a istniejące presety Feel mają wspólny panel podglądu.

**Architecture:** Opcjonalny host overlay pożycza Stage/theme/Game i używa obecnego RuntimeInspector. Filtrowanie, focus i panel presetów są jawne, bez refleksji lub drugiego modelu sceny.

**Tech Stack:** Java25, Gradle9.8.1, libGDX1.14.2, JUnit4, istniejące Scene2D/Yoga i renderery silnika.

**Spec:** [Zaakceptowany projekt](../../next-runtime-batch.md), akceptacja użytkownika 2026-10-16. Ten plan wymaga przeglądu przed kodowaniem; nowe nazwy API nie są deklaracją wdrożenia.

## Global Constraints

- Zachować Java/GameObject/Component i jeden kanoniczny animator avataru.
- Zachować aktualne profile GL20/GL30 desktopu; Android API35+/GLES3 ma osobną bramkę runtime.
- Zasoby GPU tworzyć, publikować i zamykać na owner thread poza aktywnym batchem; nie zwalniać pożyczonych zasobów.
- Bez assetów Mojang, PBR/IBL, dynamicznych cieni i deklaracji pełnej zgodności z Unity.
- Nie obchodzić TransformEditPolicy ani dodawać globalnego singletona sceny.
- Własność, czas WORLD/UI i anulowanie zachowują istniejące kontrakty.

## Review Focus

- F1 z przechwyconą myszą oddaje input panelowi i przywraca poprzedni tryb — Task1.
- Zmiana sceny z powtórzonymi ID zeruje selection/filter-collapse state — Task1.
- Filtrowane dziecko zachowuje widoczny kontekst rodziców — Task1.
- Stop starego feedbacku nie anuluje nowego lease tej samej właściwości — Task2.
- Focus selected nie teleportuje gracza/kamery posiadającej controller — Task1.

---

### Task 1: Wspólny overlay i tree viewer

**Files:**
- Create: `javity-ui/src/main/java/org/javity/ui/inspector/RuntimeToolsOverlay.java`
- Modify: `javity-ui/src/main/java/org/javity/ui/inspector/RuntimeInspector.java`
- Modify: `javity-ui/src/main/java/org/javity/ui/inspector/SceneInspection.java`
- Modify: `javity-desktop/src/main/java/org/javity/demo/VoxelDemo.java`, `Physics3DDemo.java`
- Test: `javity-ui/src/test/java/org/javity/ui/inspector/SceneInspectionFilterTest.java`

**Interfaces:**
- Produces: `SceneInspection.visibleRows(String query, Set<Integer> collapsed): List<Row>`; query name/ID/tag case-insensitive, ancestors retained.
- Produces: `RuntimeInspector.selectedObject(): GameObject` zwraca żywy aktualny wybór lub null.
- Produces: overlay ctor `(Stage, UiTheme, Supplier<Game>)`, `inspector()`, `update(float)`, `drawGizmos()`, `onFocusLost()`, `close()`; host używa procesorów z inspector().

- [x] **Step 1: Write the failing tests.** Testy filterByNameIdTag, matchingChildKeepsAncestors, collapsePreservesSelection, sceneSwapClearsCollapseAndSelection, inactiveDuplicateComponentsRemainVisible. Native test: F1/cursor/input priorytet, resize, focus selected podczas aktywnego controller; nie wolno zmienić gameplay TRS.
- [x] **Step 2: Run the tests to verify RED.** `./gradlew :javity-ui:test --tests org.javity.ui.inspector.SceneInspectionFilterTest` — oczekiwany błąd kompilacji brakującego API lub wskazana nowa asercja; zachować log przyczyny.
- [x] **Step 3: Implement the scoped change.** Dodać search i collapse/expand, używając rzeczywistych obiektów gry. Wpiąć overlay z kolejnością inspector→Stage→gizmo→gameplay; F1 zapamiętuje cursor capture i przywraca po zamknięciu. Focus może sterować tylko kamerą debugową po jawnym wejściu w free-fly; w pozostałych trybach wyświetla odmowę. Zachować sesje/undo i cleanup dwóch inputProcessorów.
- [x] **Step 4: Verify GREEN.** CPU testy oraz inspectorSmoke, voxelSmoke i physics3dSmoke; kliknięcie panelu nie kopie bloku, control-owned ruch jest zablokowany, obiekt chunkowy nie oznacza obiektu per blok. Uzupełnić docs/runtime-tools.md.
- [x] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: expose runtime scene tools in 3D demos`. Nie oznaczać zadania ukończonym przed przeglądem i wymaganym native proof.

### Task 2: Panel odtwarzania presetów Feel

**Files:**
- Create: `javity-ui/src/main/java/org/javity/ui/inspector/FeelPreviewPanel.java`
- Modify: `javity-ui/src/main/java/org/javity/ui/inspector/RuntimeToolsOverlay.java`
- Modify: `javity-desktop/src/main/java/org/javity/demo/Feel3DGpuSmokeProbe.java`
- Test: `javity-ui/src/test/java/org/javity/ui/inspector/FeelPreviewSessionTest.java`
- Modify: `docs/feel3d.md`, `docs/engine-guide.md`

**Interfaces:**
- Produces: `FeelPreviewPanel(Stage, UiTheme, Supplier<GameObject>)`, `play(PresentationEffects3D.Preset)`, `setClock(Clock)`, `pause()`, `resume()`, `stop()`, `update(float)`, `close()`.
- Consumes: `PresentationEffects3D.play(Preset): Playback`, Playback pause/resume/cancel i jawny clock managera.

- [x] **Step 1: Write the failing tests.** Testy closeCancelsOnlyOwnedPlayback, selectionChangeStopsPreview, worldPauseDoesNotAdvanceWorldPreset, uiPresetContinuesWhileWorldPaused i wrongSurfaceRefusesClearly. Native kliknięcia play/pause/stop mają zmienić obraz kamery/modelu, nie kanoniczną transformację.
- [x] **Step 2: Run the tests to verify RED.** `./gradlew :javity-ui:test --tests org.javity.ui.inspector.FeelPreviewSessionTest` — oczekiwany błąd kompilacji brakującego API lub wskazana nowa asercja; zachować log przyczyny.
- [x] **Step 3: Implement the scoped change.** Wybór clock wyszukuje istniejący PresentationEffects3D z dokładnie tym zegarem; brak managera daje czytelny błąd, bez automatycznego dodawania komponentów. Panel trzyma wyłącznie playback utworzony przez siebie, dostarcza istniejące presety use-tool/hit/landing/sprint i wskazuje wymagany manager/clock. Parametry edytować typowanymi polami i nowym niezmiennym presetem dla kolejnego play, nie refleksją. Scene transition pozostaje obsługiwane przez SceneManager i istniejący scenariusz mm; panel dokumentuje odnośnik do tej demonstracji.
- [x] **Step 4: Verify GREEN.** feel3dSmoke, inspectorSmoke, mmSmoke i pełny build; zamknięcie overlay/zmiana scene nie pozostawia efektów ani node Yoga. Zrzut panelu i przykłady w guide.
- [x] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: add owned Feel preview tools`. Nie oznaczać zadania ukończonym przed przeglądem i wymaganym native proof.
