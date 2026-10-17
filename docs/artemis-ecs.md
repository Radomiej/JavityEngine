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
