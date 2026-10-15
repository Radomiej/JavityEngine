# Voxel first-person controller

`VoxelFpsController` is a CPU-only `javity-voxel` component for walking through an explicitly loaded `VoxelWorld`. It owns no input or graphics resources. A host reads keyboard/mouse state and passes intent to the component; its `onUpdate` then advances motion with the scene's world delta. `advance(delta)` is also available for deterministic CPU callers and tests.

## Setup and pose

Attach a `Transform3D` and a controller to the same `GameObject` before `Game.start()`:

```java
GameObject player = game.createObject("player");
Transform3D feet = player.addComponent(new Transform3D());
feet.position.set(0, 2, 0); // bottom-center of the player's collision box
VoxelFpsController controller = player.addComponent(new VoxelFpsController(world));
```

The transform position is the player's feet, not the camera. Its rotation stores yaw and pitch. The host can call `controller.getEyePosition(out)` to get the camera position (feet + 1.62 Y) and `controller.getLookDirection(out)` for a unit direction; yaw zero faces world -Z and positive pitch looks upward. `getTransform3D()` returns the cached same-object transform. A missing transform is a configuration error reported during `onCreate`.

Pass movement axes with `setMoveInput(forward, right)`, sprint state with `setSprint`, jump-key state with `setJump`, and relative mouse deltas in degrees with `addLookInput`. Inputs are independent of libGDX input, so the same controller runs in tests and non-graphical hosts. Diagonal movement is normalized. Jump is triggered by a press edge and does not repeat while held; a press just before landing is retained through the landing step and consumed on the next advance.

The body is an upright axis-aligned box with radius 0.30 and height 1.80. Walking speed is 4.3 units/s, sprint speed is 7, jump speed is 8, gravity is 24, and terminal fall speed is 50. `advance` accepts finite non-negative deltas, caps simulation time per call at 0.25 seconds, and uses bounded substeps to prevent crossing thin blocks. A zero delta leaves motion and grounded state unchanged. It validates the delta before checking component state; while disabled or while its owner is inactive it leaves motion unchanged. Calling it before initialization or after removal/destruction throws `IllegalStateException`.

## Collision behavior and limits

Registered nonzero block IDs collide as full opaque cubes. AABB motion checks each axis separately, which stops at floors, ceilings, and walls while allowing the remaining horizontal axis to slide along a wall. Collision reads the world directly and treats any unloaded chunk as a solid boundary even though ordinary `VoxelWorld.getBlock` reads it as air. This prevents walking or falling out of the currently loaded area. Coordinates outside the world's documented block range are also solid; the transform must start with a finite feet position whose full body fits inside that range.

This is a simple FPS controller, not general 3D physics: it has no slopes, steps, crouching, variable jump height, swimming, moving platforms, dynamic bodies, or multiplayer prediction. It assumes axis-aligned full-cube blocks and a single-threaded world, consistent with `VoxelWorld`.
