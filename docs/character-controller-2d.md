# CharacterController2D

`CharacterController2D` is a `javity-core` component for moving an object's `Transform2D` in the WORLD update. It normalizes movement input and treats speed as world units per second. The default speed is 120, matching java-lab's controller default; set a different default with `setSpeed`, or pass a speed to one `move` call.

```java
CharacterController2D controller = player.addComponent(new CharacterController2D());
controller.setSpeed(4).move(inputX, inputY);
```

Movement intent is represented by the public `velocity` vector and persists until the next `move` call. A zero direction or zero speed stops movement. `move(Vector2)` reads the vector without changing it. The controller uses the owning object's position and is driven by Javity's normal component update rules: it does not move while the object is inactive or destroyed, the component is disabled, or WORLD updates are paused/frozen. Its delta must be finite and non-negative; a zero delta leaves position unchanged.

Bounds are optional and local to the controller because `javity-core.Game` has no scene-wide world-bounds API. `setWorldBounds(x, y, width, height)` accepts a world-space rectangle. `setHalfExtents(halfWidth, halfHeight)` clamps the object's center so the supplied footprint stays inside that rectangle. If a footprint is wider than the rectangle along an axis, the center is placed at the rectangle midpoint on that axis. `constrainToBounds` can disable this clamp.

This is transform movement only. It does not query or resolve Box2D collisions, and it does not move a physics body; objects driven by `javity-physics2d` need a physics-aware controller in that module. `onAfterMove(delta)` runs after each WORLD update's movement step (including a zero-delta step); it does not indicate that collision processing occurred.

`Component` also exposes its owner's component helpers: `getComponents()`, `getComponents(Class)`, `requireComponent(Class)`, `removeComponents(Class)`, `removeComponent(Component)`, and `removeComponent(Class)`. Queries retain the owner's copy and inheritance behavior, and removals retain its lifecycle notifications and deferred cleanup.
