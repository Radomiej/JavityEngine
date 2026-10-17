# Optional Bullet physics 3D

`javity-physics3d` is an optional, scene-owned CPU physics module. It depends on `javity-core` and libGDX Bullet 1.14.2. Core, voxel CPU code, renderers, and the default games do not initialize or depend on Bullet. The Android graph does not include this module.

Register one `Physics3D` system before `Game.start()`. Its constructor takes copied gravity in world units per second squared, with one world unit equal to one metre. Attach exactly one initialized `Transform3D` and at least one enabled solid collider to a physics object. A body is optional: collider-only objects are implicit static bodies. `RigidBody3D` selects STATIC, DYNAMIC, or KINEMATIC movement; only one may be attached. Other components implementing `TransformEditPolicy` are treated as competing movement owners and rejected. Parent transforms and nonunit scale are unsupported.

`BoxCollider3D` dimensions are full edge lengths. `SphereCollider3D` uses a radius. `CapsuleCollider3D` is Y-up and names its end-to-end total height; its cylinder length is total height minus twice the radius. `CompoundCollider3D` contains up to 64 immutable primitive descriptions. Multiple collider components on one object also form one compound body, with a limit of 64 components and 4096 total native primitive children, including compound children and trigger colliders. Collider dimensions and local poses are independent of rendered model scale. No body is created until at least one usable collider is initialized and enabled.

The system owns all Bullet worlds and native body, motion-state, compound, and child-shape wrappers. Components retain typed configuration and copied commands only. Dynamic transforms are published from simulation. Once a body is bound, direct writes to its `Transform3D` are rejected; use `RigidBody3D.teleport` for discontinuous movement or `moveKinematic` for a kinematic target. Kinematic targets are last-write-wins before a fixed step. Teleport updates the rigid-body center-of-mass transform, motion/interpolation state, broadphase AABB, and optionally clears velocities and forces. Configuration replacement is built as a private candidate; a failed replacement leaves the previous body active.

Physics additions made during a Game update remain pending until the next normal component-initialization boundary. Physics3D skips pending candidates and preserves an existing native body while its replacement is pending; it does not initialize components recursively.

Before publishing native geometry, Physics3D rejects composed collider poses or extents whose world-space envelope exceeds one quarter of the largest finite float. Dynamic mass, inverse mass, calculated inertia, and inverse inertia must all be representable as finite positive floats. This derived-value check keeps invalid numeric state out of Bullet while still allowing ordinary large configurations (for example, a radius-1000 sphere with mass 1,000,000).

Commands are batched by category for each fixed tick, independent of the order in which their methods were called: teleport first, then the kinematic target, then requested linear/angular velocities, then queued forces and impulses in their queue order. A teleport with `clearVelocity=true` clears current native velocities and forces, but a velocity request or force/impulse already queued for that same tick is applied afterward. A trigger-only static or kinematic binding has no solver body: teleport and kinematic targets update its copied Transform3D pose and trigger AABBs at that fixed boundary; queued velocities and forces/impulses are consumed without physical response and its reported velocities remain zero. Pause and inactive bindings preserve these pose commands until an active fixed tick.

Javity owns a bounded double-precision accumulator and advances Bullet once per 1/60 second tick through `stepSimulation(fixedStep, 0)`. The input delta already includes the Game time-scale snapshot. At most eight ticks are run per frame; time above that accumulator cap is discarded and reported by `getDiscardedTimeSeconds()`. Pause and zero time scale do not advance ticks or consume queued force and impulse commands. This is a stable fixed-step schedule, not a guarantee of identical results across platforms or a network rollback contract.

CCD is accepted only for one centered box, sphere, or capsule. Other CCD configurations, including compounds and offset primitives, fail before native body allocation because Bullet's motion-clamping CCD needs a convex root shape. The desktop CPU test exercises a fast sphere against a thin wall. No GL context or debug drawer is allocated by `Physics3D`.

`Collider3D.setTrigger(true)` creates a separately owned no-response collision object for that collider; it never changes the response of solid siblings. Trigger-only static/kinematic sensor objects are supported without an empty rigid body. A dynamic body still requires at least one solid collider. Trigger local offset and rotation live in the trigger's compound shape and follow the body's world pose. Trigger overlap checks use Bullet `contactTest` narrowphase contacts, with a zero world-unit touch threshold, rather than broadphase bounds. Same-owner pairs are excluded. Trigger group/mask/material are independent; each trigger is one native collision object. All solid colliders on one body must share the same group, mask, friction, and restitution or synchronization rejects the configuration. Trigger admission resolves the actual peer native object, including its committed per-body solid filter.

`Collider3D.onContact(Consumer<Contact3D>)` returns an idempotent `AutoCloseable`; component release clears its listeners. Contact snapshots are world-local and body-level for solid compound bodies: they report stable body-lifetime IDs and leave child collider fields null because Bullet manifold points do not identify a tested public child identity. Trigger snapshots identify the exact trigger collider. Each receiver sees itself as object A and gets the normal oriented from B toward A. Manifold contacts are retained when distance is `<= 0` world units. After each native fixed substep, Physics3D copies the representative contact for each body pair and delivers ENTER, STAY, or EXIT after transform publication and after JNI returns. Multiple manifolds/points for one solid body pair produce one event per substep. EXIT caused by removal/deactivation is delivered at the next active WORLD substep to surviving receivers; scene close suppresses pending delivery. Callback recipients are checked for liveness before each call. Callback exceptions are captured outside JNI and rethrown after native contact scanning and event dispatch; callback close/removal/disable is reconciled before another catch-up substep. Queries from a contact callback read the current published world and do not recursively dispatch contacts.

`PhysicsFilter3D` validates unsigned 16-bit query group/mask fields. Native membership and query filters use mutual Bullet mask admission: `(groupA & maskB) != 0 && (groupB & maskA) != 0`. Solid settings are per body; conflicting solid-child settings fail before replacement. Trigger settings remain per trigger object.

`raycast(from, to, filter)`, `sweepSphere(radius, from, to, filter)`, and `sweepCapsule(radius, totalHeight, fromPose, toPose, filter)` return only the closest `PhysicsHit3D`. Results copy their point, normal, fraction, and distance; vector accessors return fresh copies. A hit on a solid compound body has a null collider field because child identity is not promised; trigger hits include their collider. Ignored owners and excluded triggers are removed in the native callback's `needsCollision` path before closest-hit selection. A zero-length ray returns empty after checking that its Physics3D world is live. Sphere/capsule start overlap uses `contactTest` narrowphase and reports a blocked hit at fraction/distance zero. Sweep matrices must be finite affine unit rigid transforms with no scale, shear, or reflection. Queries and refresh are owner-thread operations; they fail during the native step. A top-level query reconciles lifecycle/configuration before reading the world, but does not consume queued force, impulse, velocity, or teleport commands; those retain their documented next-fixed-tick order. Calls inside contact delivery skip recursive synchronization and see the current published world, including the native filters committed at its last synchronization boundary.

Run the real native CPU suite with:

```sh
./gradlew :javity-physics3d:test
```

`beforeFixedStep(Runnable)` registers an ordered, owner-thread callback after WORLD updates and before synchronization and commands for each actual solver tick. It does not run for paused/frozen/zero-tick frames. Callbacks execute outside JNI and must not initialize pending Game components or recursively step the Game. `publishStaticCollision(owner, descriptor)` is restricted to this callback boundary; ordinary UI synchronization, `refresh()`, and queries cannot publish staged terrain. `isCollisionPublished` checks the exact active component/transform configuration and committed native participation without exposing Bullet handles.

The optional `javity-voxel-physics3d` module depends on this module and `javity-voxel`; neither core physics nor voxel CPU code depends on the bridge. It creates at most one static compound body per tracked chunk, with a validated 4096 total child-box budget per body. Failed candidate publication retains the previous body while readiness remains false until the exact current descriptor commits. See [voxel collision documentation](voxels.md#bullet-chunk-collision-c1).

## Bullet character controller (Task 5 C2)

`BulletCharacterController3D` is an optional native Bullet `btKinematicCharacterController` backed by a `btPairCachingGhostObject` and a Y-up `btCapsuleShape`. The component exposes copied values only; native ghosts, shapes, actions, callback, and world remain owned by `Physics3D`. Each Game owns one fixed-step accumulator and the character receives one `speed × 1/60` horizontal displacement per actual Bullet tick.

Attach one controller and one identity-rotated `Transform3D` to an active character object. `Transform3D.position` and `State.feet()` are the capsule feet; the native ghost origin is the capsule center, offset by half the authored total height. `setMoveAxes(right, forward)` clamps diagonal input to unit length. `setLook(yaw, pitch)` copies angles and clamps pitch; yaw steers planar movement, while the returned state reports measured native feet velocity, grounded/jumped/landed flags, and whether the controller is currently bound. The state vectors are copied on publication and access. `setJumpHeld` queues only a rising edge, the jump buffer is measured in active fixed ticks, and a zero-tick buffer still permits an immediate grounded edge jump. `clearInput()` clears movement, sprint, held jump, and queued jump after focus loss. `warp(feet)` queues an explicit feet-position teleport at an unlocked physics boundary.

The controller uses Bullet for gravity, jump motion, step and slope response, ceiling and wall collisions, and penetration recovery. Character native settings (filter, jump speed, maximum fall speed, jump buffer, and maximum slope) become immutable when the component initializes. The native KCC in Bullet 1.14.2 does not enforce its `maxJumpHeight` field, so this API does not offer an apex-height setting. KCC recovery is iterative and the native `maxPenetrationDepth` value is a contact threshold rather than a strict maximum correction distance; spawn and warp reject deep overlaps, and no fixed recovery-distance guarantee is claimed.

Before native allocation or pose/input mutation, character dimensions, positions, effective speed, jump/fall values, and derived capsule/sweep bounds are checked against `sqrt(Float.MAX_VALUE) / 4` in world units. This supported numeric domain leaves headroom for Bullet's float-vector squared-length operations. Invalid runtime speed products and warps fail before changing the native pose or consuming queued movement commands.

When a `CharacterReadiness3D` policy is configured, spawn and warp wait for or require its closed-AABB result over the complete capsule envelope. Each tick checks the radius-expanded horizontal sweep plus authored height, step height, fixed-tick jump/fall travel, and an additional recovery allowance. The displacement admitted for a fixed tick is the exact displacement submitted to Bullet; input changes made by policy code take effect on the next tick. A denied tick suspends the native action at its previous feet pose; after native movement, the actual pose is checked again and rolled back if it left ready space. A new character remains unbound while its initial envelope is not ready; `isBoundToPhysics()` and `State.boundToPhysics()` report this state without implying that an unbound character is grounded. `VoxelCollisionAdapter.characterReadiness()` supplies this policy using exact loaded chunk generation and collision revision, including negative coordinates and chunk seams. With the default maximum fall speed of 55 units/s the admission envelope can span more than one chunk vertically. These checks govern this kinematic character only: ordinary dynamic bodies can still enter unloaded terrain.

Character ghosts use the same private native registry as bodies for query masks, self-ignore, camera sphere sweeps, and trigger contact snapshots. `Physics3DCameraObstructionQuery` adapts copied `raycast`/`sweepSphere` results to `CameraRig3D`; a starting overlap returns zero clearance. State and trigger callbacks run only after native readback. They may query, remove components, or close the scene; recipients are rechecked before each delivery, and teardown removes actions and ghosts before disposing their shapes and the world-owned ghost-pair callback. To preserve correct upright capsule sweeps and jumps, the world configures Bullet's allowed CCD sweep penetration to zero; this is a world-level sweep setting.

The native CPU regression suite checks feet/center geometry, rest/jump/landing, wall stop and slide, low/high steps, below/above slope limits, ceiling response, zero-buffer launch, fixed-frame partitioning and diagonal speed, readiness across a negative chunk seam and source edits/unload/reload, queries/triggers, pause and lifecycle, callback closure/removal, deep overlap rejection, two worlds, and injected native allocation failures. It uses desktop Bullet without OpenGL.

The tests cover native falling/resting, capsule height, compound pose, impulse/force response, damping, angular velocity, rotated teleport, kinematic platform response, CCD, paused/scaled/bounded stepping, delta repartitioning, transform policy, two independent Games, contact transitions/deduplication, mixed and bodyless triggers, mutual masks, rotated narrowphase overlap, nearest filtered ray/sphere/capsule queries, callbacks that query/close/disable, injected native allocation/insertion failures, replacement failures, pre-step hook semantics, and wrapper cleanup. The voxel bridge CPU tests exercise terrain without a renderer, revision changes, stale snapshots, empty edits, unload/reload, bounded retries, replacement failure retention, and native cleanup. Tests use desktop Bullet natives without an OpenGL context.

## Opt-in desktop scene and debug rendering (Task 5 C3)

The desktop project adds a separate Bullet showcase. It does not change the default launcher or
the established `--voxels` AABB movement backend. Run it with:

```sh
./gradlew :javity-desktop:run --args='--physics3d'
./gradlew :javity-desktop:physics3d
./gradlew :javity-desktop:physics3dSmoke
```

Controls: WASD and mouse look, Space jump, Shift sprint, F5 FPS/TPP, F3 Bullet wire/contact debug,
E starts the avatar use pose, left/right mouse edits the voxel patch, and Escape releases or captures
the cursor. Focus loss pauses WORLD and clears character input. The capsule is the sole movement
authority in this scene; one existing `GeneratedVoxelAvatar`/`ModelPoseAnimator` supplies both cached
world views and the isolated arms/tool viewmodel. The upright capsule transform remains identity;
the body yaw and camera presentation are view transforms. Camera obstruction is queried after the
native step and before chunk culling. Dynamic primitives, a compound crate, a moving kinematic
platform, the action trigger, and editable loaded voxel chunks share this scene.

Bullet `DebugDrawer` allocation is explicitly requested by F3 or `Physics3D.createDebugView()`.
The drawer owns its GL resources, borrows its camera, attaches privately to one world, and detaches
the native world pointer before disposal. Render and close check the owner thread and current lease
before GL operations or state changes. Creating a debug view checks that libGDX graphics/GL objects
are available before constructing the native drawer. The caller must keep an active context current
through rendering and GL resource disposal. Physics3D native CPU construction and tests remain GL-free.

`physics3dSmoke` runs against the actual desktop Bullet natives and a live software OpenGL context.
It renders the actual sphere ModelRenderer alone under one fixed proof camera at two copied native
poses, checks orange pixels and centroid travel at both projected centers, and temporarily disables
that ModelRenderer as a negative control that must produce zero sphere pixels. It drives the capsule,
compares the same camera/pose with Bullet debug on and off, exercises an actual F5 camera sweep
against and away from a native wall, asserts the trigger's material action and voxel collision
revision, and compares the rendered world-space avatar socket to its attached tool. It closes and
recreates the scene with GL live and verifies bound live buffers, the debug drawer program/shaders,
and deletion events. Images and metrics are at [`artifacts/physics3d`](artifacts/physics3d/):
FPS composite, clear and obstructed TPP, isolated sphere poses, red-trigger composite, debug on/off,
voxel edit, and recreated-scene captures plus
`verification.json`. A passing desktop smoke does not establish Android packaging or runtime.

The voxel bridge's readiness policy applies to the kinematic capsule only. Ordinary dynamic Bullet
bodies can move beyond loaded terrain into unknown space. At the default fall speed of 55 units/s,
the capsule readiness envelope can also require chunks above/below the current feet position.
