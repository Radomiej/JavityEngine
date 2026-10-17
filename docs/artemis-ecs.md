# Optional Artemis ECS

`javity-ecs-artemis` adds an optional Artemis-ODB 2.3.0 owner to the new Javity core. Core and regular GameObjects do not depend on Artemis. Add the module only to applications that use this adapter; it exports both core and the native Artemis API.

```java
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import org.javity.core.Game;
import org.javity.ecs.artemis.ArtemisSystem;
import org.javity.ecs.artemis.EcsPhase;

World nativeWorld = new World(new WorldConfigurationBuilder()
    .with(new MovementSystem())
    .build());

Game game = new Game();
ArtemisSystem ecs = game.addSystem(new ArtemisSystem(nativeWorld, EcsPhase.BEFORE_WORLD));
game.start();
// game.step(...) schedules nativeWorld.process() in the selected phase.
// Use ecs.world() for native mappers, subscriptions, archetypes, and other Artemis APIs.
```

Construct and fully configure the native `World` before passing it to `ArtemisSystem`; any configuration failure remains the caller's responsibility. Passing the World transfers exclusive ownership to the adapter. Do not call `process()` or `dispose()` on it yourself, and do not share it with another adapter. The adapter closes the World once with the owning Game, including when closed before `Game.start()` or when a callback throws. `world()` is available before start and before registration, on the creating thread, until close. Register the adapter before `Game.start()` before using operations associated with that Game.

The default phase is `BEFORE_WORLD`. `AFTER_WORLD` runs in the after-update hook and `UI` runs in the unscaled UI hook. World phases honor Game pause and time scale; UI keeps running with unscaled delta. The adapter processes the World at most once per selected hook and rejects recursive processing. The World and adapter are confined to the thread that constructed the adapter. The native Artemis API remains fully available, so direct calls that violate the ownership contract cannot be prevented by this wrapper.

This module has no renderer, GL, JNI, or desktop-only dependency. The legacy engine/backend may still expose Artemis through its existing dependency; that does not make the optional module a dependency of core or the normal desktop runtime.

## Explicit GameObject links

An adapter can bind an existing object to an entity from its owned World, or create the pair with `createLinked`. Binding checks Game and World identity, rejects destroyed objects and duplicate links, and is allowed before `Game.start()` once the adapter has been registered. Each pair has a monotonically increasing generation. The native `GameObjectRef` is reserved for the bridge; its `objectOrNull()` is read-only to callers. Use `EntityLink` for checked access:

```java
EntityLink link = ecs.createLinked("player",
    object -> object.addComponent(new Velocity()),
    edit -> edit.create(VelocityData.class));
link.addProjection(new VelocityProjection());
link.setSynchronizationEnabled(false); // suspend projected data copies
link.setSynchronizationEnabled(true);  // reconcile current state at the next boundary
link.close();                           // unbind only
```

Destroying a linked GameObject deletes its native entity. Deleting the native entity or removing its reserved reference invalidates the handle and leaves the GameObject alive. Closing a link unbinds it and releases only projection slots declared `OWNED`; native components owned by application code remain. Closing the adapter clears borrowed GameObject references before disposing the World. Lifetime housekeeping also runs in the UI hook, so a destroyed object is cleaned up while WORLD is paused and when synchronization is disabled. Artemis structural changes still follow its deferred processing rules.

## Typed projections and prefab creation

`ComponentProjection<C,E>` declares one direction and explicit ownership. `GO_TO_ECS` copies core data immediately before the selected native process; `ECS_TO_GO` copies native data immediately after it. `OWNED` is valid for `GO_TO_ECS`: the bridge creates and removes that native component as its selected initialized/enabled core source appears or disappears. `BORROWED` requires the application to provide the target component (and, for `ECS_TO_GO`, the core target); missing targets pause copying. Default selection rejects multiple core components of the requested type. Override `select(GameObject)` when the application wants a specific instance. A link cannot register two writers for the same core or native type.

The adapter does not reflect public fields or write transforms automatically. Projection callbacks should use the owning controller or physics API for controlled state; ordinary data components are a safe starting point.

`LinkedPrefabFactory.instantiate(ecs, prefab, schema, nativeConfigurator)` creates one fresh native entity and link for each GameObject read from an existing `Prefab`. It returns immutable links. If reading or native configuration fails, it destroys only the GameObjects and entities created by that invocation; objects already in the Game are preserved. Native structural cleanup remains deferred to Artemis' normal flush.
