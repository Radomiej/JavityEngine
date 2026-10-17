# Opcjonalny Artemis ECS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dodać natywny Artemis obok GameObject z jawnym powiązaniem i przełączaną synchronizacją, bez uzależnienia core od ECS.

**Architecture:** Opcjonalny moduł posiada przekazany World i obsługuje go w jednej wybranej fazie GameSystem. Generacyjne powiązania i prywatny token w natywnym komponencie chronią przed użyciem starego ID. Typowane projekcje mają jeden kierunek zapisu i jawną własność komponentu docelowego.

**Tech Stack:** Java25/Gradle9.8.1/libGDX1.14.2/Artemis-ODB2.3.0/JUnit4; JDK ThreadMXBean wyłącznie w desktopowym benchmarku testowym.

**Spec:** [Zaakceptowany projekt](../specs/2026-10-17-optional-artemis-design.md). Użytkownik: „wygląda dobrze, dodaj ten system”. Metoda zachowana: implementerzy Luna, nadzór i niezależne przeglądy. Plan do przeglądu przed wykonaniem.

## Global Constraints

- `javity-core` i normalne GameObjecty nie zależą od Artemis. Nowy moduł zależy od core i eksportuje `net.onedaybeard.artemis:artemis-odb:2.3.0` konsumentowi, który go wybiera.
- Nie usuwamy teraz starych modułów ani nie dodajemy nowego ECS. Ich cleanup jest późniejszym zadaniem; obecność Artemisa przez historyczny backend nie dowodzi integracji nowego modułu.
- Jedna konkretna instancja `ArtemisSystem` na Game zgodnie z istniejącym zakazem duplicate GameSystem class. Nie zmieniamy Game.addSystem dla wielu Worldów.
- World procesowany najwyżej raz w kroku, wyłącznie wybrany hook; UI i WORLD zachowują aktualne reguły Game.
- Przekazany World przechodzi na wyłączną własność adaptera. Konsument nie woła sam process/dispose i nie dzieli World między adapterami. To kontrakt native API, bez globalnego registry ani udawanej kontroli wszystkich bezpośrednich wywołań.
- Cały adapter i linki należą do owner thread. Brak GL, JNI i nowego renderera w module.
- GO/encja są opcjonalnie powiązane 1:1. Własność pola jest jednokierunkowa; brak refleksyjnego autosync i brakuje gwarancji automatycznego obserwowania publicznych pól.
- Pełny API Artemis zostaje dostępny: World/systems/mappers/aspects/subscriptions/Bag/ArchetypeBuilder.
- Reference i dane nie obchodzą TransformEditPolicy/Bullet/controller; przykłady ruchu używają zwykłego komponentu danych, nie bezpośredniego pisania do sterowanej fizyką transformacji.
- Produkcyjny moduł nie zależy od desktopu, JMX, Graal, platform natives ani drugiego schedulera. Platformy są weryfikowane oddzielnie.

## Review Focus

- Native delete/recreate ID przed następnym bridge reconciliation nie może odtworzyć starego linku — Task2.
- Zniszczenie GO przy pause/sync-off nadal unieważnia link, bez uruchamiania systemów WORLD — Task2.
- Reentrant callback przy unlink/delete/adapter close nie używa zwolnionych linków ani nie procesuje World drugi raz — Task1/2.
- Core ma wiele komponentów danego typu; projekcja domyślnie odrzuca niejednoznaczność, nie wybiera losowo — Task2.
- Błąd konfiguratora fabryki sprząta tylko utworzone przez nią obiekty/encje, nie cudze komponenty lub wcześniejsze obiekty — Task2/3.

## File map

Nowa paczka `org.javity.ecs.artemis` w module `javity-ecs-artemis`:
- `ArtemisSystem` — World owner, fazy, link registry i publiczna fasada.
- `EcsPhase` — BEFORE_WORLD, AFTER_WORLD, UI.
- `EntityLink`, `GameObjectRef`, `InactiveGameObject` — handle/token, natywna referencja i marker stanu.
- `LinkRegistry` package-private — struktura i lifetime powiązań, listener subscriptions i structural queue.
- `ComponentProjection`, `ProjectionDirection`, `ProjectionOwnership` — typowany kontrakt danych.
- `ProjectionSession` package-private — ownership, selekcja, dirty/reconcile.
- `LinkedPrefabFactory` — użycie istniejącego Prefab/SceneJson bez zmian ich codeców.

Każdy plik odpowiada za jedną rzecz; nie przenosimy logiki schedulera Game ani kodu rendererów.

---

### Task 1: Moduł i właściciel natywnego World

**Files:**
- Create: `javity-ecs-artemis/build.gradle`, `src/main/java/org/javity/ecs/artemis/{ArtemisSystem,EcsPhase}.java`, `src/test/java/org/javity/ecs/artemis/ArtemisSystemTest.java`.
- Modify: `settings.gradle`; root `build.gradle` tylko jeśli potrzeba wspólnej wartości wersji, bez zmiany aktualnej wersji backendu.
- Create: `docs/artemis-ecs.md` (zakres początkowy, native przykład i kontrakt ownership).

**Interfaces:**
- `public enum EcsPhase { BEFORE_WORLD, AFTER_WORLD, UI }`.
- `public class ArtemisSystem extends GameSystem`: konstruktory `(World world)` i `(World world, EcsPhase phase)`; default BEFORE_WORLD. World jest już skonstruowany i zainicjalizowany przez natywne API, więc konfiguracja/ewentualny błąd jego konstruktora należy do wywołującego przed transferem.
- `public World world()` — owner-thread i live-check, działa przed Game.start, także przed rejestracją systemu. Adapter musi być zarejestrowany w Game do operacji dotyczących GO.
- Native process jest wyłącznie w wybranym hooku, z world.setDelta(delta); guard przeciw reentrant process i wywołaniu hooku rekurencyjnie. `validateDispose` sprawdza thread przed zmianą stanu; `onRelease` zwalnia World raz także bez startu i po błędzie callbacku.
- Przy cleanup zachowujemy natywne World.dispose i jego ArtemisMultiException dla Exception; nie obiecujemy naprawienia wewnętrznego cleanup po Error w samym Artemis.

- [ ] **Step 1: Write failing tests.** Native BaseSystem liczy wywołania/delta; każda faza procesuje raz i widzi właściwy czas/porządek. WORLD zatrzymuje pause/zero scale, UI działa nieskalowane. Native mapper + Aspect subscription/IntBag/Archetype tworzy/modyfikuje i usuwa encję. Wrong-thread world/dispose odrzucane przed zmianą stanu; owner może potem zamknąć. Close before start/repeated close/process exception/native dispose exception nie omijają owned cleanup; duplicate wrapper w Game odrzucany przez istniejący core.
- [ ] **Step 2: Verify RED.** `./gradlew :javity-ecs-artemis:test` — brak modułu/API. Błąd JDK/Gradle socket nie jest RED funkcjonalności. Sprawdzić dostępność JDK25 (odtworzony tymczasowo JDK25 z checksum), nie zakładać obecności dawnych X/SDK.
- [ ] **Step 3: Implement.** `api project(':javity-core')` i `api 'net.onedaybeard.artemis:artemis-odb:2.3.0'`; moduł nie dodawany do desktop runtime. Używać natywnego World, nie wrappera encji udającego wszystkie API. Default root reactor może kompilować nowy moduł bez eksportowania go core/UI.
- [ ] **Step 4: Verify GREEN.** `:javity-ecs-artemis:test :javity-core:test`, dependency report core i desktop pokazuje brak zależności na nowy moduł. Raport odróżnia istniejący legacy Artemis od nowej integracji. Brak GL wymagany. Publiczne snippet tworzy WorldConfiguration/World, przekazuje ownership adapterowi, rejestruje go przed game.start.
- [ ] **Step 5: Commit.** `feat: add optional scene-owned Artemis subsystem`; zakresowe pliki, raport z RED/GREEN i review przed Task2.

### Task 2: Linki, fabryki i typowane projekcje

**Files:**
- Modify: `ArtemisSystem.java`; create remaining production files from file map under `javity-ecs-artemis/src/main/java/org/javity/ecs/artemis/`.
- Create: `src/test/java/org/javity/ecs/artemis/{EntityLinkTest,ProjectionTest,LinkedPrefabFactoryTest}.java`.
- Update: `docs/artemis-ecs.md` o fabrykę, własność i synchronizację.

**Interfaces:**
- `ArtemisSystem.bind(GameObject, Entity): EntityLink`; rejects foreign Game/World, destroyed objects, entity already bound and duplicate object. Requires adapter attached to a live Game, allows pre-start links.
- `createLinked(String, Consumer<GameObject>, Consumer<EntityEdit>): EntityLink` — creates both; configurators supplied for core and native components. Native `GameObjectRef` is reserved/owned by bridge, public no-arg native component with private object/token fields and read-only `objectOrNull()`. Its token is not user-editable API.
- `EntityLink implements AutoCloseable`: `isValid()`, checked `object()`, `entity()`, `generation()`, `setSynchronizationEnabled(boolean)`, `isSynchronizationEnabled()`, `addProjection(ComponentProjection<?,?>)`, `close()`. Close only unbinds; GO destruction deletes entity; native entity deletion unbinds without destroying GO. Adapter close invalidates all and clears borrowed refs before native dispose. No symmetric-delete policy in first version.
- `InactiveGameObject extends com.artemis.Component` marker reflects inactive GO; sample native Aspect explicitly excludes it. Markers do not silently disable a user's whole entity or unrelated systems.
- Registry verifies matching private token/ref at public handle access and pre/post processing, not just isActive(id); uses actual all-entity subscription deletion events and handles deletion before first subscription membership. Test pinned delete/recycle behavior. Each handle has World identity plus monotonic positive generation. Core destroyed state immediately makes handle invalid even before queued native delete completes.
- Housekeeping runs in onUiUpdate even for WORLD phase/sync-off, and again pre/post selected process. It never calls process to flush deletions during pause. Structural native notifications retain Artemis deferred semantics.
- `ProjectionDirection { GO_TO_ECS, ECS_TO_GO }`; `ProjectionOwnership { OWNED, BORROWED }`.
- `abstract ComponentProjection<C extends org.javity.core.Component,E extends com.artemis.Component>` ctor `(Class<C>,Class<E>,ProjectionDirection,ProjectionOwnership)`; `protected C select(GameObject)` default requires at most one matching component, caller may override for exact-instance selection. `protected void copyToEntity(C,E)` / `copyToObject(E,C)` defaults throw UnsupportedOperationException; selected direction invokes only its callback.
- GO_TO_ECS OWNED claims exclusive native component type for projection, requires that type absent on registration, creates/removes/recreates it when selected initialized/enabled core source appears/disappears/GO becomes inactive. Owned slot lifetime remains projection-owned even if user directly changes its value; native reference projection uses this mode.
- GO_TO_ECS BORROWED requires existing target on registration, never creates/removes it; missing source/target pauses copies. ECS_TO_GO permits only BORROWED: native component owned by game, core target borrowed, absence pauses copies. Borrowed callbacks must not retain core references requiring auto cleanup; use OWNED for those.
- No two projections on one link claim the same native type or same core target type; reject conflicting writer configuration before any copy. OWNED/BORROWED policy is explicit, not inferred. Failures inside user copy callbacks are propagated, not claimed fully transactional.
- GO_TO_ECS copies immediately before process; ECS_TO_GO immediately after process. Initialization/presence/enabled/active and selectors reconciled each boundary. Structural listeners queue changes; enabled/active revisions observed only for linked objects. Sync-off suspends projection copies and structural projection changes; lifetime/ref validity remains active, no unbounded event history; re-enable performs full reconciliation at next boundary with selected writer.
- `LinkedPrefabFactory.instantiate(ArtemisSystem, Prefab, SceneJson, Consumer<EntityEdit>): List<EntityLink>` creates native pair per returned prefab GO. Native archetypes remain usable by world.createEntity(archetype) + bind; no generic reflection serializer. Prefab failure or later native config failure invalidates/deletes only factory-created links/entities and destroys only its newly instantiated GOs; existing objects remain intact. Native deletion/component releases may be deferred to normal flush, no extra simulation tick.

- [ ] **Step 1: Write failing tests.** Native IDs recycled through real World; old token cannot access or delete replacement. Direct World.delete including pre-first-process, foreign worlds/scenes, duplicate links, unlink idempotence, recursive unlink/close, native removal of reserved ref. GO destroyed during pause/sync-off cleans link without WORLD tick. Factory configure/read failure preserves unrelated objects and no surviving borrowed reference/listener.
- [ ] **Step 2: Verify RED.** Run four module test classes (Task1 plus new link/projection/prefab classes); missing APIs/regressions are RED. Preserve IDs/generations in assertions, not assumptions that IDs always differ.
- [ ] **Step 3: Implement.** Registry/sessions use existing `onComponentChange` unsubscribe Runnable, activeRevision/enabledRevision and phase reconciliation. No core dependency or reflection hook added. Validate duplicate-source selection and writer conflicts before altering claimed slots. User-provided callbacks handle controlled movement; never blanket-write Transform3D.
- [ ] **Step 4: Verify GREEN.** Full module tests include add/remove/disable/active, duplicates, OWNED vs BORROWED existing/replaced/missing targets, direction ordering, exact selectors, sync off/on, native subscriptions, failure and close. Assert adapter leaves user-owned data untouched on unlink and clears all owned core references, even when native dispose raises. Test core prefab hierarchy/tag/component fresh identity. Run retained core/UI/physics tests if any shared source touched (shared source should normally be unchanged).
- [ ] **Step 5: Commit.** `feat: bridge GameObjects with native Artemis entities`; write report and independently review before Task3.

### Task 3: Real usage, benchmark i granice zależności

**Files:**
- Create: `javity-ecs-artemis/src/test/java/org/javity/ecs/artemis/{EcsUsageSmoke,ArtemisBridgeBenchmark}.java`; tests/examples stay outside production JAR.
- Modify: `javity-ecs-artemis/build.gradle`, `.github/workflows/desktop.yml`, `docs/artemis-ecs.md`, `docs/engine-guide.md`, `docs/README.md`, `AGENTS.md`, `docs/TODO.md`, `docs/work-queue.md`.
- Create: `docs/artifacts/artemis-ecs/{README.md,verification.json,benchmark.json}`.

**Interfaces:**
- JavaExec task `:javity-ecs-artemis:usageSmoke` uses test runtime and runs native World + core Game with pure entity and linked data mover, prefab and dynamic sync. Produces JSON with exact source commit, process/delta/query/lifecycle counts. No GL screenshot claim.
- `:javity-ecs-artemis:bridgeBenchmark` uses test-only ThreadMXBean; fixed same seed and dt1/60, 10000 entities, 100 warm-up steps, 300 measured steps, 3 repetitions. Cases: native ECS, GameObject data mover, mixed 10% linked with sync on/off. Report actual updates, linked count, wall time and owner-thread allocated bytes; explain remaining code overhead, no universal speed ranking or fabricated target improvement.
- Task `:javity-ecs-artemis:verifyEcsBoundary` is wired into module check, depends on its jar, writes `build/reports/ecs-boundary.json` with production direct/transitive components and core/UI/desktop graph. It verifies API direct dependencies are only core+Artemis, core/UI/default desktop have no dependency path to adapter, and module JAR has no test examples/benchmark/JMX references. Existing legacy Artemis is separately identified, not forbidden globally. A temporary init script adds adapter to desktop runtimeOnly (no dependency cycle); this actual guard must reject it, then clean configuration passes. No nested Gradle execution inside a JUnit test.

- [ ] **Step 1: Write failing usage/boundary checks.** Assert same intended movement updates and authority, native mappers/subscriptions/Bag, prefab per-instance state and sync resume. Boundary fixture rejects a deliberately introduced reverse dependency while restored final graph passes; tests aren't only README text searches.
- [ ] **Step 2: Verify RED.** Missing usageSmoke/boundary/report task fails for missing feature, not unavailable JDK. Preserve bounded controls.
- [ ] **Step 3: Implement examples/tasks/docs.** Show full native WorldConfiguration and native Archetype + bound-object factory, manual Aspect/InactiveGameObject filter and projection ownership. Explain one World per current wrapper class/Game, typed core duplicates, pause/disable scopes, borrowed resources and unsupported browser/Android claims. CI adds CPU ECS test/usage gate without GL requirements.
- [ ] **Step 4: Verify GREEN.** Full reactor, usageSmoke and benchmark. Existing desktop smoke/voxel/inspector/environment/asset-streaming/avatar/instancing gates verify no shared-runtime regressions after dependency graph edit, only once per final source. Separate Android compile/runtime gate before claiming module support; no browser claim without TeaVM build. Root final whole-goal review covers full range and module boundaries; benchmark source/input hashes preserved.
- [ ] **Step 5: Commit and handoff.** `test: demonstrate and measure optional Artemis integration`; all statuses reflect tested scope. Independent task review, final whole-goal review/fix wave, root commit/push on current branch. Memory/shader refactor and Colyseus/Wasm remain separate projects.

## Self-review and handoff

Spec coverage: native access/phase/ownership in Task1; factory/link/token/hooks/projections in Task2; usage/benchmark/dependency/platform proof in Task3. All Review Focus cases belong to tests above. No production ECS is in core, no automatic reflection/renderer added. Existing Game duplicate-system restriction is preserved. World supplied before transfer avoids promising cleanup inside a failing native constructor. Changes are sequential with Luna implementers and independent review, as already selected. The concrete plan is presented for user review before implementation per writing-plans; no execution-method question needed.
