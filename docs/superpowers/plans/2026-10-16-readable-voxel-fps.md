# Czytelny voxel FPS z kilofem Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Działające --voxels pokazuje prawą rękę z rozpoznawalnym kilofem i nadal poprawne TPP.

**Architecture:** Zmodyfikować autorski proceduralny avatar i wydzielić camera-local kompozycję viewmodelu. Używać obecnego socketu, ModelPoseAnimator i passu viewmodelu, bez drugiego zegara.

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

- Przełączenie F5 podczas użycia zachowuje jedną pozę i poprawne sloty — Task1/2.
- Narzędzie przy ścianie i po resize nie znika za depth świata — Task2.
- Otwarte UI lub focus loss nie wywołuje kopania — Task2.
- Tool-hidden wyłącza trzonek i głowicę bez ukrycia dłoni — Task1/2.
- Usunięcie sceny podczas zamachu zwalnia lease prezentacji i siatkę raz — Task2.

---

### Task 1: Autorski kilof i stabilny kadr

**Files:**
- Modify: `javity-desktop/src/main/java/org/javity/demo/GeneratedVoxelAvatar.java`
- Create: `javity-desktop/src/main/java/org/javity/demo/VoxelViewmodelPresentation.java`
- Modify: `javity-desktop/src/main/java/org/javity/demo/VoxelDemo.java`
- Test: `javity-desktop/src/test/java/org/javity/demo/VoxelViewmodelPresentationTest.java`

**Interfaces:**
- Consumes: `AvatarView.parts(Set<AvatarPart>)`, `slotVisible(String, boolean)`, `setBaseTransform(Matrix4)`.
- Produces: `VoxelViewmodelPresentation.writeBase(Matrix4 target): Matrix4`; jawny stały camera-local TRS, bez aktualizacji animacji.

- [x] **Step 1: Write the failing tests.** Testy `baseIsFiniteAndStable`, `doesNotMutateCallerPose` i `rightHandAndPickaxeFitProjectedLowerRight` sprawdzają powtarzalność, brak zmian kanonicznego TRS i projekcję punktów dłoni/głowicy dla 1280×720 oraz 960×720. Oczekiwany region narzędzia x=0.55..0.95, y=0.05..0.65 w normalizowanych współrzędnych obrazu bottom-left, bez dotykania near plane .03.
- [x] **Step 2: Run the tests to verify RED.** `./gradlew :javity-desktop:test --tests org.javity.demo.VoxelViewmodelPresentationTest` — oczekiwany błąd kompilacji brakującego API lub wskazana nowa asercja; zachować log przyczyny.
- [x] **Step 3: Implement the scoped change.** Zastąpić pojedynczy tool cube jedną posiadaną siatką trzonka i kontrastowej głowicy w tym samym slocie tool/socket. Widok FPS wybiera RIGHT_ARM oraz tool, TPP wszystkie części. Wyznaczyć stały TRS z próby projekcji i utrwalić go w helperze; nie tworzyć Matrix4 co klatkę ani nowego animatora.
- [x] **Step 4: Verify GREEN.** Powtórzyć testy CPU i `:javity-desktop:avatarSmoke :javity-desktop:avatarGl30Smoke :javity-desktop:voxelSmoke`. Slot off/on ma ukrywać całą siatkę narzędzia; screenshoty idle/tool-hidden/TPP zachować w `docs/artifacts/voxel-fps/`.
- [x] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: make voxel FPS hand and pickaxe readable`. Nie oznaczać zadania ukończonym przed przeglądem i wymaganym native proof.

### Task 2: Użycie, feedback i rzeczywisty dowód obrazu

**Files:**
- Modify: `javity-desktop/src/main/java/org/javity/demo/VoxelDemo.java`
- Modify: `javity-desktop/src/main/java/org/javity/demo/VoxelSmokeProbe.java`
- Modify: `docs/engine-guide.md`, `docs/avatars.md`
- Create: `docs/artifacts/voxel-fps/README.md`

**Interfaces:**
- Consumes: istniejące `GeneratedVoxelAvatar.beginUse()`, `PresentationEffects3D` i sterowanie raycast.
- Produces: LMB rising-edge rozpoczyna usunięcie bloku i swing; E sam swing. Przytrzymanie nie dodaje nowego playbacku w każdej klatce; nowy zamach dopiero po następnym naciśnięciu.

- [x] **Step 1: Write the failing tests.** Rozszerzyć native probe o widoczność kolorów dłoni, trzonka i głowicy w osobnych ROI; action-start/mid/end, wall, F5 podczas use, T, pause/resume, focus loss i resize. Sprawdzić brak przyrostu zegara animacji przy drugim renderze i brak zmiany transformacji gameplay przez recoil.
- [x] **Step 2: Run the tests to verify RED.** `./gradlew :javity-desktop:voxelSmoke` — oczekiwany błąd kompilacji brakującego API lub wskazana nowa asercja; zachować log przyczyny.
- [x] **Step 3: Implement the scoped change.** Podpiąć swing do faktycznej akcji i viewmodel-only recoil z istniejącego API. Dodać mały HUD ze skrótami i jasną nazwą demo. Anulować posiadane feedbacki przy close; nie zmieniać semantyki raycastu lub fizyki.
- [x] **Step 4: Verify GREEN.** Pełne `./gradlew build`, voxelSmoke i avatar GL20/GL30; obejrzeć faktyczne screenshoty przed/po i zapisać metryki ROI oraz cleanup. Guide wskazuje --voxels i docelowy kadr, nie stary prosty model.
- [x] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: integrate voxel tool feedback and visual regression`. Nie oznaczać zadania ukończonym przed przeglądem i wymaganym native proof.
