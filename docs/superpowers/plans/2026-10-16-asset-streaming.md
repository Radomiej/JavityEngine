# Streaming assetów lokalnych Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Tech Stack:** Java25, Gradle9.8.1, libGDX1.14.2, JUnit4; opcjonalne moduły silnika.

**Spec:** [Zaakceptowany projekt](../../next-runtime-batch.md), akceptacja użytkownika 2026-10-16. Plan do przeglądu przed implementacją.

**Goal:** Gra ładuje lokalne modele i tekstury w czasie działania, a zasób żyje do zwolnienia ostatniego użytkownika.

**Architecture:** Opcjonalny moduł javity-assets owija istniejący AssetManager, zachowując jego zależności i loadery. Scene-local system zarządza żądaniami, lease i bezpiecznym punktem eksmisji; sceny nie wymieniają żywego ModelInstance za plecami animatora.

## Global Constraints

- Internal/local pliki przez wstrzyknięty AssetManager; bez HTTP, hot reload, streamingu chunków i audio w tym etapie.
- Manager/queue/publikacja/close wyłącznie na owner thread; async loader nie może wykonywać GL na workerze.
- Texture/Model pożyczane przez konsumenta z lease; nie eksmitować aktywnych zasobów.
- Budżet czasu miękki; rozmiary resident szacowane, jawne i konserwatywne, bez deklaracji pomiaru całego GPU.
- Bez dodawania Graal/LWJGL/FFM do przenośnego modułu; Java25 i libGDX1.14.2 pozostają bez zmian.
- Nie zmieniać istniejącego SceneAssets i jego API w sposób niezgodny wstecz.

## Review Focus

- Dwa lease tego samego klucza po zamknięciu jednego nadal mają ważny zasób — Task1.
- Zależność współdzielona przez dwa modele nie jest zwalniana za wcześnie — Task1.
- Zamknięcie ostatniego requestu podczas load i nowe żądanie tego pliku nie publikuje starej generacji — Task2.
- Pełny budżet z aktywnymi użytkownikami powoduje backpressure, nie use-after-dispose — Task2.
- Close sceny w połowie async load nie publikuje GL z workera ani callbacku do zniszczonej sceny — Task2/3.

---

### Task 1: Typed lease i współdzielony load

**Files:**
- Create: `javity-assets/build.gradle`
- Modify: `settings.gradle`
- Create: `javity-assets/src/main/java/org/javity/assets/AssetKey.java`, `AssetLease.java`, `AssetStreams.java`, `StreamBudget.java`
- Test: `javity-assets/src/test/java/org/javity/assets/AssetStreamsOwnershipTest.java`

**Interfaces:**
- `AssetKey<T>(String path, Class<T> type)` — niemutowalny, niepusta ścieżka.
- `StreamBudget(int maxQueued, int maxInflight, long estimatedResidentBytes, int maxResidentEntries, int updateMillis)` — wartości >0, updateMillis>=0.
- `AssetStreams extends GameSystem`, ctor `(AssetManager manager, StreamBudget budget)` przejmuje manager i pamięta owner thread.
- `<T> AssetLease<T> request(AssetKey<T> key, int priority, long estimatedResidentBytes)`; estimate>=0, zawiera zależności, sprzeczny typ/estimate tego samego path odrzucony.
- `AssetLease<T> extends AutoCloseable`: `state(): State`, `get(): T`, `failure(): Throwable`, `close(): void`. State=QUEUED/LOADING/READY/FAILED/CLOSED; get wyłącznie READY.

- [x] **Step 1: Write the failing tests.** Rzeczywisty AssetManager z kontrolowanymi testowymi loaderami/Disposable: `sameKeyLoadsOnce`, `closingOneLeaseKeepsSecondReady`, `sharedDependencySurvivesFirstRootUnload`, `conflictingTypeOrEstimateRefused`, `closeIdempotentAndGetAfterCloseFails`. Testy nie wymagają GL.
- [x] **Step 2: Verify RED.** `./gradlew :javity-assets:test --tests org.javity.assets.AssetStreamsOwnershipTest` — brak nowego API lub wskazane niezachowane zachowanie; zachować log.
- [x] **Step 3: Implement.** Zarejestrować moduł api javity-core; nie tworzyć loaderów od nowa. Jeden manager.load per koordynowany root, refcount lease niezależny od wewnętrznego refcount zależności AssetManager. onUiUpdate pompuje manager.update(updateMillis), również podczas WORLD pause. Żądania/odczyty/close lease z obcego wątku odrzucić.
- [x] **Step 4: Verify GREEN.** Testy na faktycznej kolejce AssetManager, CPU test wrongThreadRefused i cleanup po nieudanym load. Sprawdzić graf zależności bez desktop natives w production dependencies.
- [x] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: add scene-owned streaming asset leases`; wymagany przegląd i wymagane dowody przed zamknięciem zadania.

### Task 2: Priorytety, presja pamięci i bezpieczna eksmisja

**Files:**
- Modify: `javity-assets/src/main/java/org/javity/assets/AssetStreams.java`
- Create: `javity-assets/src/main/java/org/javity/assets/StreamStats.java`
- Test: `javity-assets/src/test/java/org/javity/assets/AssetStreamsSchedulingTest.java`

**Interfaces:**
- `AssetStreams.finishFrame(): void` jest jedynym punktem eksmisji poza ModelBatch/SpriteBatch; host wywołuje po zakończeniu rysowania.
- `AssetStreams.stats(): StreamStats` zwraca niemutowalny record `StreamStats(int queued,int inflight,int ready,int pinned,long estimatedResidentBytes,boolean overBudget)`.
- Prefetch = zwykły lease posiadany przez system hosta do chwili gotowości/wyjścia z zasięgu; po jego close zasób zostaje nieprzypiętym cache kandydatem.

- [x] **Step 1: Write the failing tests.** Testy `priorityThenFifo`, `queueAndInflightLimits`, `pinnedAssetsNeverEvicted`, `onlyFinishFrameEvicts`, `reRequestBeforeFinishFrameKeepsResource`, `lateOldGenerationNotPublished`, `sceneCloseDuringLoadCleansDependencies`, `failedLoadAllowsExplicitNewRequest`. LRU dotyczy ostatniego zwolnienia/uzyskania gotowego root; równy czas rozstrzyga kolejność wejścia.
- [x] **Step 2: Verify RED.** `./gradlew :javity-assets:test --tests org.javity.assets.AssetStreamsSchedulingTest` — brak nowego API lub wskazane niezachowane zachowanie; zachować log.
- [x] **Step 3: Implement.** Scheduler trzyma sortowaną ograniczoną kolejkę bez preemptowania aktywnego loadera. Queue-full request odrzuca jawnie, bez cichego usuwania lease. Konserwatywne sumy root estimates mogą liczyć wspólną zależność dwukrotnie — nazwać je estimates. Brak miejsca z przypiętymi rootami blokuje start kolejnych; overBudget raportowany. Cancellation ma generację i doprowadza manager do spójnego zwolnienia bez publikowania zamkniętego lease. Zamknięcie systemu wymaga zatrzymanego renderowania i drenuje własne zasoby.
- [x] **Step 4: Verify GREEN.** Całe javity-assets:test, także WORLD pause/onUiUpdate i powtórne close po błędzie. Brak wycieku testowych Disposable i brak unload pomiędzy begin/end symulowanego batcha.
- [x] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: bound asset streaming and defer eviction safely`; wymagany przegląd i wymagane dowody przed zamknięciem zadania.

### Task 3: Działające demo i granice platformy

**Files:**
- Create: `javity-desktop/src/main/java/org/javity/demo/AssetStreamingDemo.java`, `AssetStreamingGpuSmokeProbe.java`
- Modify: `javity-desktop/build.gradle`, `javity-desktop/src/main/java/org/javity/demo/DesktopLauncher.java`
- Modify: `docs/engine-guide.md`, `docs/README.md`, `docs/TODO.md`
- Create: `docs/asset-streaming.md`, `docs/artifacts/asset-streaming/README.md`

**Interfaces:**
- Launcher `--asset-streaming`, zadanie `:javity-desktop:assetStreamingSmoke`.
- Consumes: request/lease/get/finishFrame/stats z Task1/2; demo trzyma lease tak długo, jak renderujący komponent.

- [x] **Step 1: Write the failing tests.** Native probe: animowany sentinel nadal się aktualizuje podczas load; po READY wyświetlony oryginalny lokalny model z teksturą, dwa lease i jeden render zasobu po close pierwszego, ostatni close usuwa komponent przed finishFrame/unload. Zrzut i native resource counter przed/po dispose.
- [x] **Step 2: Verify RED.** `./gradlew :javity-desktop:assetStreamingSmoke` — brak nowego API lub wskazane niezachowane zachowanie; zachować log.
- [x] **Step 3: Implement.** Dodać mały autorski fixture modeli/tekstur z licencją, panel request/prefetch/release i statystyk. Nowe żądanie po error jest jawne. Nie zmieniać ModelRenderer.instance w miejscu. Nie integrować jeszcze automatycznego streamingu terenu.
- [x] **Step 4: Verify GREEN.** Pełny reactor, assetStreamingSmoke + voxelSmoke. Osobny opt-in Android build i instrumentacja realnego load/close/context-loss przed deklaracją Android streaming; bez dostępnego wyniku oznaczyć tę część jako unverified, nie PASS.
- [x] **Step 5: Commit.** Stage tylko pliki zadania, `git diff --cached --check`, commit `feat: demonstrate streaming assets with runtime proof`; wymagany przegląd i wymagane dowody przed zamknięciem zadania.
