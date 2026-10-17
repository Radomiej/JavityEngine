# 3D Presentation (Slice 1)

`Presentation3D` adds bounded, render-only local pose contributions to a `GameObject` that also has `Transform3D`. It binds and borrows that exact transform when initialized; removing or replacing it invalidates the provider's channels. The physics pose, avatar binding, shared model pose, and serialized transform remain canonical.

The renderer submits each model from:

```text
canonical object world × local presentation TRS × per-view avatar base
```

The presentation translation is in object-local world units. Scale values are additive deltas around multiplier 1. Rotation is the summed local rotation vector in radians; the provider converts the sum once to a normalized quaternion. This is intended for small additive motion, not arbitrary orientation blending. The identity contribution leaves the existing avatar composition unchanged. Plain models and avatar renderables use the same presented base. No node or bone transform is temporarily edited.

Attach and initialize a provider before opening channels or transient leases:

```java
Presentation3D modelPresentation = object.addComponent(new Presentation3D());
game.start();
game.step(0);

Presentation3D.Channel recoil = modelPresentation.openChannel();
Targets.presentationRotationX(recoil).set(0.08f);
Presentation3D.Lease shake = modelPresentation.contribute();
shake.set(0.02f, 0, 0, 0, 0, 0, 0, 0, 0);
```

Persistent channels remain until `close()`. Their `FloatTarget` identity is the channel and property key, so feedback tweens retain exclusive newest-writer behavior on each channel, including the existing KEEP_CURRENT and current-token RESTORE_START rules. A caller owns each channel and closes it to remove its held pose. Transient leases are additive to all other slots; pause/resume affects one lease and close removes only that lease. Each provider has 64 total slots shared by persistent channels and transient leases. Capacity exhaustion throws without evicting another owner's contribution.

All vector values are copied scalars. `Presentation3D.snapshot(Pose)` writes to caller-owned storage; `Pose.localMatrix(out)` writes a new matrix into caller storage. It does not expose provider-owned vectors, quaternions, or matrices. Writes validate the whole new contribution and complete aggregate before committing. Each scalar contribution is bounded to `Float.MAX_VALUE / 128`, so every subset of the 64 slots remains representable even after arbitrary cancellation. A stale channel or lease throws on writes; its `isValid`/`isActive` becomes false after provider removal, owner disable/inactivation, or object destruction, including a disable/enable cycle with no render or read during the inactive interval. Provider removal also invalidates slots created before initialization during normal component cleanup.

## Per-view cameras

Camera feedback uses a separate `CameraPresentation3D` attached to the host object for that view. Create one provider with `Space.WORLD` and another with `Space.VIEWMODEL`; the rig rejects them if passed in the opposite role. Persistent camera channels and transient leases carry camera-local translation, rotation-vector radians, and FOV delta. FOV values are degrees. The rig seeds its world and viewmodel bases from the supplied cameras, then `setWorldProjection` and `setViewmodelProjection` author later base changes. It adds the delta to the latest base and clamps the presented projection to 2°–178°. Cancelling a channel reveals the current authored base. `Camera3D.camera` and `rig.viewmodelCamera()` expose the finalized presented cameras for rendering and picking; author base changes through the rig setters.

```java
CameraPresentation3D worldFx = view.addComponent(
    new CameraPresentation3D(CameraPresentation3D.Space.WORLD));
CameraPresentation3D handsFx = view.addComponent(
    new CameraPresentation3D(CameraPresentation3D.Space.VIEWMODEL));
game.start();
game.step(0);
CameraPresentation3D.Lease kick = handsFx.contribute();
kick.set(0, 0, 0, 0.04f, 0, 0, -3f);

rig.update(eye, lookDirection, avatarObject, worldFx, handsFx);
renderer.renderWorld(game, worldCamera, avatarView);
renderer.renderViewmodel(rig.viewmodelCamera(), handsView);
```

`CameraRig3D.update` snapshots the two providers. World translation, including the rig's existing copied `setPresentationOffset`, is swept from the canonical eye using the presented FOV's near-plane corner radius; third-person boom is then swept from that translated pivot. It also runs a stationary clearance query at the final camera point, so FOV-only and rotation-only changes cannot publish a near plane overlapping an obstacle. If that check fails, the rig discards the world contribution and falls back to the authored eye, aim, and base FOV; `worldPresentationConstrained()` reports this. If even that authored camera point is obstructed, update throws instead of publishing an unsafe pose. Obstruction adapters must implement zero-length queries as stationary sphere-clearance checks. Rotation and projection are finalized before the camera's matrices are updated. The final `Camera3D.camera` is the camera to pass to model rendering, frustum checks, and `getPickRay`; the final `rig.viewmodelCamera()` is the camera for the depth-isolated viewmodel pass. A viewmodel lease does not change world aim, eye, or obstruction.

## Scope and limits

This slice supplies typed pose channels and additive leases, per-view camera channels, model/camera composition, and the feedback scalar targets. It does not include spring, punch, shake, recoil, bob or FOV presets; those can be built on these interfaces in Slice 2. Providers are per object/view and require explicit rig input; there is no global camera. The camera obstruction callback remains host-owned and must return a finite fraction in `[0,1]`. This API does not claim arbitrary world-space offsets, physical movement, root motion, IK, particle rendering, or postprocessing.

The standalone `Presentation3DGpuSmokeLauncher` fixture exercises actual rendered model and viewmodel pixels, final camera rays/frustum, and the obstruction sweep. CPU lifetime, aggregation, and projection contracts are covered by `Presentation3DTest` and `CameraRig3DTest`.

Provider lifetime revisions are checked before a snapshot is aggregated, so an inactive/disabled interval invalidates old handles even when no presentation read happened during that interval. A presented model matrix is validated after composing the canonical object matrix with its local pose. Before an ordinary `ModelInstance` receives a new base, the renderer preflights each enabled rigid node global using libGDX's renderable composition. For skinned parts, libGDX's final renderable world matrix is the presented base; the renderer does not reject unused bone-palette entries. Avatar renderables receive the same finite-matrix check after their per-view base is applied. Camera updates preflight both final cameras, including their projection, view, combined and inverse matrices, frustum planes, and center/corner pick rays before either borrowed camera or rig diagnostics are committed. An unsupported composition throws while the last valid output remains intact.

The desktop fixture also exercises `VoxelCameraObstruction` against a loaded wall. At the selected stationary eye, the authored 50° near-plane sphere clears the wall while the presented 68° sphere overlaps it; the rig reports the constrained fallback. Removing the wall admits the FOV contribution, and moving the authored eye into the wall rejects the update without changing the last valid camera output.
