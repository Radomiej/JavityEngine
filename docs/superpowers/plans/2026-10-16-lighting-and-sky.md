# Światła i niebo sceny Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Tech Stack:** Java25, Gradle9.8.1, libGDX1.14.2, JUnit4; opcjonalne moduły silnika.

**Spec:** [Zaakceptowany projekt](../../next-runtime-batch.md), akceptacja użytkownika 2026-10-16. Plan do przeglądu przed implementacją.

**Goal:** Sceny GameObject mają directional/point/spot, ambient, skybox, mgłę i prosty dzień/noc z czytelną diagnostyką.

**Architecture:** Komponenty światła i scene-local RenderEnvironment3D dostarczają snapshot do istniejącego GdxRenderer. SkyboxRenderer jest osobnym właścicielem programu/mesh, pożycza cubemap i zachowuje stan GL; widok rąk i UI pozostają osobnymi passami.

## Global Constraints

- Java25/libGDX1.14.2; pinned cache recovery nie może zgubić nowych lokalizacji świateł.
- Stare sceny bez RenderEnvironment3D zachowują renderer.environment i domyślne światło.
- Scene environment zastępuje domyślne światła w swoim renderze; bez sumowania dwóch słońc.
- Forward DefaultShader, bez PBR/IBL, cieni dynamicznych, HDR/bloom i wolumetryki.
- Cubemap pożyczony od hosta/lease; program i mesh owned; wszystko GL na owner thread, poza batchem.
- Skybox nie rysuje się w viewmodel/UI; kolejność tło→świat→world effects→viewmodel→SCREEN/HUD.
- Desktop GL20/GL30 i Android GLES3 wymagają oddzielnych rzeczywistych bramek; brak shader-prefix changes globalnie.

## Review Focus

- Scena bez nowych komponentów wygląda jak przed zmianą — Task1.
- Wyłączone/usunięte światło i scena z powtarzonymi ID nie zostawia starego słońca — Task1.
- Kierunek światła pod obróconym rodzicem jest world-space i znormalizowany — Task1.
- Translacja kamery nie daje paralaksy nieba; depth/blend/cull/viewport po pass są zachowane — Task2.
- Utrata EGL i nieudana kompilacja nie zostawiają starych uchwytów/programów — Task2/3.

---

### Task 1: Scene-local światła i konfiguracja shaderów

**Files:**
- Create: `javity-core/src/main/resources/org/javity/render/scene-lighting.fragment.glsl`, `scene-lighting.vertex.glsl`
- Create: `javity-core/src/main/java/org/javity/render/lighting/SceneLightingAttribute.java`
- Create: `javity-core/src/main/java/org/javity/render/lighting/Light3D.java`, `DirectionalLight3D.java`, `PointLight3D.java`, `SpotLight3D.java`, `RenderEnvironment3D.java`
- Modify: `javity-core/src/main/java/org/javity/render/GdxRenderer.java`, `RecoverableDefaultShaderProvider.java`
- Test: `javity-core/src/test/java/org/javity/render/lighting/LightSnapshotTest.java`

**Interfaces:**
- `Light3D extends Component`: `setColor(float r,float g,float b)`, `setIntensity(float)`, skopiowane wartości nieujemne/skończone. Directional kierunek local -Z; point/spot pozycja Transform3D world.
- Point: `setRange(float)` dodatni; spot: `setCutoffDegrees(float)` 0<angle<90, `setExponent(float)` >=0.
- `RenderEnvironment3D extends GameSystem`: `setAmbient(float r,float g,float b)`, `setFog(float r,float g,float b,float near,float far)`, `clearFog()`. Właściciel jednego Environment snapshot.
- `GdxRenderer(int maxBones,int maxBoneWeights,int directionalCapacity,int pointCapacity,int spotCapacity)`; stare ctory zachowują pojemności libGDX. Nowe demo wybiera 2/4/2; overflow pierwsze aktywne według GameObject.id i kolejności komponentów.

- [ ] **Step 1: Write the failing tests.** Testy legacyFallbackUnchanged, sceneOverrideDoesNotDoubleSun, removalDisablesLight, parentRotationDefinesDirection, invalidValuesRefused, deterministicOverflow i sceneSwapDoesNotRetainLights. Point/spot mają attenuation `max(0, 1-distance/range)^2`, więc contribution=0 przy distance>=range. Spot mnoży to przez `pow(max(dot(normalizedLightDirection, normalizedLightToFragment),0), exponent)` wewnątrz stożka cutoff i 0 poza nim; intensywność jest osobnym mnożnikiem.
- [ ] **Step 2: Verify RED.** `./gradlew :javity-core:test --tests org.javity.render.lighting.LightSnapshotTest` — brak nowego API lub wskazane niezachowane zachowanie; zachować log.
- [ ] **Step 3: Implement.** Użyć jawnego snapshotu aktywnych komponentów przed begin. Directional/spot normalizują world kierunek; singular/zero direction odrzucają konfigurację. Pojemności przekazać DefaultShader.Config. Dla scene environment użyć lokalnego fragment wariantu DefaultShader o podanej formule point/spot i liniowej mgle: factor=clamp((eyeDistance-near)/(far-near),0,1), final=mix(lit,fogColor,factor), 0<=near<far. Zachować stock fragment dla legacy Environment. SceneLightingAttribute odróżnia scene variant; shader.canRender musi odrzucić inny tryb nawet przy tych samych materiałach. Dopasowany vertex shader podaje eyeDistance po skinning/world transform, a fragment zachowuje zgodne varyings. Wariant utrzymuje skinning, tekstury i alpha test libGDX; nowe uniformy rejestrować w obecnym RecoverableDefaultShader przez BaseShader.register, aby naprawa lokalizacji objęła je po relinku. Wszystkie pliki upstream-derived zachowują Apache notices. Diagnostyka overflow jawna.
- [ ] **Step 4: Verify GREEN.** Core testy + presentation3dSmoke/postprocessSmoke/avatarGl30Smoke. Dodać native dowód spot/point/directional i fog do Task3; nie uznać samych obiektów Environment za dowód obrazu.
- [ ] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: add scene-owned 3D lighting`; wymagany przegląd i wymagane dowody przed zamknięciem zadania.

### Task 2: Cubemap i proceduralne tło bez zanieczyszczenia GL

**Files:**
- Create: `javity-core/src/main/java/org/javity/render/sky/SkyboxRenderer.java`, `SkySettings.java`
- Modify: `javity-core/src/main/java/org/javity/render/lighting/RenderEnvironment3D.java`, `javity-core/src/main/java/org/javity/render/GdxRenderer.java`
- Test: `javity-core/src/test/java/org/javity/render/sky/SkySettingsTest.java`

**Interfaces:**
- `SkySettings.cubemap(Cubemap borrowed): SkySettings`, `gradient(Color zenith,Color horizon,Color nadir): SkySettings` — kolory kopiowane, tryby rozłączne.
- `RenderEnvironment3D.setSky(SkySettings)`, `clearSky()`.
- SkyboxRenderer: `render(Camera,SkySettings)`, `dispose()`; caller musi zakończyć inne batche. Renderer posiada pass, settings nie zamyka pożyczonego cubemap.

- [ ] **Step 1: Write the failing tests.** CPU testy copiedColors/invalidValues/modesExclusive. Native probe najpierw RED dla translacji/no-parallax, obrotu kamery, world-depth occlusion i zachowania depthMask/depthFunc/blending/cull/viewport oraz braku wpływu na UI/viewmodel.
- [ ] **Step 2: Verify RED.** `./gradlew :javity-core:test --tests org.javity.render.sky.SkySettingsTest` — brak nowego API lub wskazane niezachowane zachowanie; zachować log.
- [ ] **Step 3: Implement.** Dodać skybox cube lub fullscreen procedural gradient zależny od kierunku promienia kamery. Wyłączyć zapis depth i narysować przed geometrią; bez translacji kamery. Program ma lokalny desktop/GLES dialect i pełny partial-failure cleanup. Cubemap z sześciu autorskich assetów utrzymuje ich loader/lease, nie pass. Przywrócić faktyczny wcześniejszy stan GL również po wyjątku.
- [ ] **Step 4: Verify GREEN.** Native testy Task3 na GL20/GL30 i istniejące postprocessSmoke/shapesSmoke. Sprawdzić więcej niż wizualny sky color: użyć sentinel geometrii/depth i stanu GL po pass.
- [ ] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: render managed sky backgrounds`; wymagany przegląd i wymagane dowody przed zamknięciem zadania.

### Task 3: Dzień/noc, gizma i wspólny przykład

**Files:**
- Create: `javity-core/src/main/java/org/javity/render/lighting/DayNightCycle3D.java`
- Create: `javity-desktop/src/main/java/org/javity/demo/EnvironmentGpuSmokeProbe.java`, `EnvironmentDemo.java`
- Modify: `javity-desktop/build.gradle`, `javity-desktop/src/main/java/org/javity/demo/DesktopLauncher.java`, `VoxelDemo.java`
- Modify: `javity-ui/src/main/java/org/javity/ui/inspector/RuntimeToolsOverlay.java`
- Create: `javity-ui/src/main/java/org/javity/ui/inspector/LightGizmoAdapter.java`, `SocketGizmoAdapter.java`
- Modify: `javity-ui/src/main/java/org/javity/ui/inspector/SceneInspection.java`
- Modify: `docs/engine-guide.md`, `docs/TODO.md`
- Create: `docs/lighting-and-sky.md`, `docs/artifacts/environment/README.md`

**Interfaces:**
- `DayNightCycle3D extends Component`, ctor `(DirectionalLight3D sun, RenderEnvironment3D environment, double daySeconds)`; `setPhase(double)` 0..1 i `phase(): double`, WORLD clock. Właściciele w tej samej Game, daySeconds>0 finite.
- `LightGizmoAdapter.draw(ShapeRenderer borrowed, Camera camera, GameObject target)` pokazuje kierunek, zakres i stożek; adaptery UI setterów są typowane i walidowane. SocketGizmoAdapter.draw(ShapeRenderer borrowed, Camera camera, AvatarBinding binding, String socketName, Matrix4 base) pożycza binding i renderer, nie przesuwa kości.
- `SceneInspection.beginLightEdit(Light3D): LightEditSession`, `commitLightEdit(LightEditSession, LightState)`: `LightState(float red,float green,float blue,float intensity,float range,float cutoffDegrees,float exponent)` to niemutowalny record; nieużywane dla danego typu wartości są 0, walidacja stosuje się tylko do pól tego typu; sesja zapisuje Game/obiekt/komponent i expected state. Commit tworzy pojedynczy undo; konflikt sceny/komponentu/stanu odrzucany tak samo jak TRS.
- Launcher `--environment`, zadanie `:javity-desktop:environmentSmoke`; voxel demo wybiera nowe otoczenie jawnie.

- [ ] **Step 1: Write the failing tests.** Native testy `directionalChangesPixels`, `pointAttenuates`, `spotActuallyLightsCone`, `disabledLightGone`, `fogAffectsWorldOnly`, `newUniformLocationsRefreshAfterRelink`, `skyHasNoParallax`, `stateSentinelsSurvive`, `viewmodelAndHudRemainVisible`, `dayNightPauseClock`, repeatedClose i failedShaderCleanup. CPU cycle testy phase=0/.25/.5/.75, pause i odrzucenie obcych właścicieli; light editor testy apply/undo/redo i odmowa po zmianie komponentu/sceny lub externally changed expected state.
- [ ] **Step 2: Verify RED.** `./gradlew :javity-desktop:environmentSmoke` — brak nowego API lub wskazane niezachowane zachowanie; zachować log.
- [ ] **Step 3: Implement.** Dodać panel i kontrolowane kadry dzień/noc/mgła/typ światła, funkcje tylko dla nowych jawnych komponentów. Gizma zmieniają transform przez istniejące sesje; typowane parametry nie obchodzą undo lub polityki ruchu. Cycle ustawia sun/ambient i gradient, nie nadpisuje cubemap; przy cubemap sky phase pozostaje bez zmiany tła i UI to wyjaśnia. Opisać limity świateł i nieobsługiwane efekty.
- [ ] **Step 4: Verify GREEN.** Pełny reactor; environmentSmoke, voxelSmoke, inspectorSmoke, avatar GL20/GL30, feel3dSmoke, postprocessSmoke. Android: build APK i realna utrata EGL z nowymi sky/cubemap/spot przed claimem Android support. Zachować screeny/metryki/cleanup, uaktualnić guide i scope.
- [ ] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: demonstrate environment authoring and day night`; wymagany przegląd i wymagane dowody przed zamknięciem zadania.
