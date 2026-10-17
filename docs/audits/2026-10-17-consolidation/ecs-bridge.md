# Javity optional Artemis bridge: architecture audit

## Recommendation

Add an opt-in `javity-ecs-artemis` module that depends on `javity-core` and Artemis ODB 2.3.0. Keep Artemis absent from `javity-core`, `javity-physics2d`, and the default modern runtime classpath. Do not retain Galaxy Rapid as an ECS host: Artemis can be owned directly by a scene `GameSystem`. This follows the user's revised direction and supersedes the older AGENTS.md statement that Artemis is merely transitional and should not be exposed to the modern API.

The module should expose Artemis as Artemis: callers can register native Artemis `BaseSystem`s in a `WorldConfiguration`, use `World`, `Entity`, native `ComponentMapper`, `Aspect`, `EntitySubscription`, and `Bag` APIs, and create/delete/edit entities. Core GameObjects remain a separate model. An adapter is explicit and optional; pure Artemis entities do not need GameObjects.

## Minimal API shape

- `ArtemisSystem extends GameSystem`: owns one `World`, built from caller-supplied `WorldConfiguration`/systems; exposes `world()` while alive; closes the World during `onRelease`.
- `ArtemisPhase { UI, BEFORE_WORLD, AFTER_WORLD }`, selected once per World. Default `BEFORE_WORLD` runs in `GameSystem.onBeforeUpdate`, after core UI work and before core WORLD components. `AFTER_WORLD` runs in `onAfterUpdate` after WORLD components (suitable for consumers of their output); `UI` runs in `onUiUpdate` and receives unscaled delta even during pause/freeze. A World is processed exactly once per `Game.step`; do not process one World in multiple hooks, which would run all Artemis systems multiple times. If systems need different phases, make the phase explicit in Artemis system logic or give them separate Worlds.
- Processing is single-threaded on the owning Game/render thread. `Game` currently has deterministic snapshots and lifecycle cleanup but no thread-affinity checks, so the module should document/enforce its own owner thread at `onCreate`, `process`, bridge mutation, and `close` boundaries. No background Artemis processing or concurrent mutation.
- `ArtemisBinding` (or `ArtemisBindings`) explicitly binds a core `GameObject` to an Artemis `Entity`; it is not created for ordinary objects. `createLinked(name)` may create both and return a typed pair/handle. `createEntity()` remains available for pure ECS.
- A factory/prefab overload can accept a binding policy/registry and atomically instantiate core prefab objects plus matching entities. Existing `Prefab.instantiate(game, schema)` remains independent. SceneJson's registered codecs serialize core components, so Artemis state needs explicit module-specific codecs if it is to be prefab data; never infer/load arbitrary ECS class names from JSON.

A compact usage outline:

```java
var ecs = game.addSystem(new ArtemisSystem(config, ArtemisPhase.BEFORE_WORLD));
Entity e = ecs.world().createEntity(); // pure ECS path
var linked = ecs.createLinked("player", bindings); // explicit GO + entity path
```

## Ownership and linked lifetime

- Each `ArtemisSystem` owns exactly one World and all systems/managers registered in its configuration. `Game` already disposes systems in reverse registration order and continues cleanup after failures; the Artemis wrapper should close its World in `onRelease`, including when initialization or a callback failed. Do not separately close caller-owned systems unless Artemis World ownership requires it; document World ownership as transferred at registration.
- A link record owns the association, not either underlying object. Recommended default: destruction/removal of the GameObject deletes its linked entity; entity deletion detaches the link and leaves the GameObject alive. Offer an explicit `DESTROY_BOTH` policy only if a caller needs symmetric lifetime. Disposing the `ArtemisSystem` detaches all links and destroys the World; it should not destroy linked GameObjects. These defaults avoid surprising destruction across the independent models.
- Subscribe to the GO's existing `onComponentChange` listener for structural add/remove. Dispose that listener on unlink/close. `GameObject.destroy()` has no global destruction callback, so the narrowest no-core-change implementation checks bound GO `isDestroyed()` at each phase and deletes linked entities before the next phase. Alternatively, a small core destruction listener would make teardown immediate; avoid adding it solely for first slice if phase-boundary cleanup is acceptable.
- Core structural changes are immediate: `addComponent`/`removeComponent` update indexes and notify listeners synchronously; removed core components are released at `Game.flush()`. On linking, bridge applies an initial structural snapshot, then listener changes. Any mirrored ECS structure should be reconciled before the selected Artemis processing phase so subscriptions observe it in that step.

## Identity, entity ID reuse, and validity

Do not treat Artemis integer entity IDs as durable handles. Artemis reuses IDs; all handles must be scoped to a specific World instance and a monotonically increasing bridge generation/link token. Store `(world identity, entity id, link generation)` and verify that the record is still current before acting. Invalidate/remove the record before calling World deletion so reentrant Artemis callbacks cannot revive it. Never resolve a stale handle by looking up its old integer ID alone. A linked pair object should expose checked accessors and become invalid after either unlink or World close; callers that directly retain native `Entity` values still follow Artemis's own lifetime rules.

## Typed adapters and change observation

No generic reflection or automatic property synchronization. Core transform fields and component fields are public/mutable; mutations like `transform.position.x = ...` have no revision or setter callback. Structural observation is not field-change observation.

Use a typed adapter registry, e.g. `ComponentAdapter<C extends core.Component, E extends artemis.Component>`, with explicit methods for create/remove and any named field projection. Define per-field direction/authority (`CORE_TO_ECS`, `ECS_TO_CORE`, or no sync); never allow two writers for the same field. If an adapter copies a value every tick, that still needs an explicit declared owner and phase. For bidirectional values, require a deliberate command/reconcile API with conflict policy rather than guessing based on reflection or comparing object snapshots.

Available core observation hooks:

- `GameObject.onComponentChange` reports only structural `ADDED` / `REMOVED` events and returns an unsubscribe handle.
- `Component.enabledRevision()` increments on actual enabled transitions; `GameObject.activeRevision()` does the same for active state. These are monotonic revisions, not callbacks. The bridge can poll these revisions at phase boundaries for explicit policy such as enabled -> disable linked entity processing, or leave entities independent. Artemis has no built-in per-entity disabled flag, so changing subscription membership by removing components is often wrong; default to merely reflecting state through a typed adapter/tag component or documenting no state propagation.
- Destruction is observable as `isDestroyed()` at phase boundaries (no listener currently).

Raw mutable field changes remain invisible. An adapter may expose setter methods, command methods, or caller-supplied dirty/revision tracking; it must not claim automatic sync.

Core GameObject allows multiple components of the same class, while Artemis allows at most one component per component type per entity. The adapter registry must define a policy for repeated core component instances (reject ambiguous binding, choose a designated instance, or aggregate into one ECS component). For a smallest first slice, reject duplicate matching core component types on bind and require explicit aggregate adapters for later cases.

## Pause, failure, and close semantics

- `Game.step` snapshots time scale; WORLD system callbacks are skipped when paused/frozen, while `onUiUpdate` runs unscaled. The selected Artemis phase should inherit those exact conditions through its chosen GameSystem hook. Document that an `AFTER_WORLD` Artemis world does not run while WORLD is paused; UI is the explicit exception.
- If Artemis `World.process()` throws, let the exception reach `Game.step`; its `finally` still flushes removals and clears stepping. The Game itself is not automatically closed by a callback exception. System disposal later must still dispose World, and `GameSystem.dispose` already invokes `onRelease` even if `onDestroy` throws.
- `Game.close()` flushes GO/component cleanup before reverse-order system disposal. Bridge cleanup must tolerate links whose GO has already been cleaned and should not access stale core components during its own release.
- Avoid mutating the current `GameSystem` list from Artemis callbacks: systems are registered before `Game.start`, and phase iteration uses a snapshot.

## Main tradeoffs / smallest implementation boundary

1. Ship native Artemis access and a `GameSystem` owner first. That already makes new projects able to use full ECS without Galaxy Rapid.
2. Add explicit GO/entity linked creation and identity-safe lifecycle handling, but keep auto data projection opt-in via typed adapters.
3. Initially do not modify core Game/GameObject APIs. The existing structural listener plus active/enabled revisions support a useful bridge; if immediate destroy notification later proves necessary, add a focused `onDestroy` subscription with disposable registration.
4. Keep Artemis in a separately included Gradle module and dependency path. `javity-backend` can keep its existing Artemis dependency/legacy behavior without being the transitively required ECS host for modern games.
