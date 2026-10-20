# Translation root motion

`ModelPoseAnimator.setRootMotion(nodePath, sink)` extracts translation from one exact top-level
node path on the animator's actual `ModelRenderer.instance`. The renderer may wrap an imported
`ModelInstance` or an ordinary shared `Model`. The feature borrows the instance and its animation
tracks; assets must outlive the renderer. `AvatarBinding`/`AvatarView` observe the same stripped
canonical pose and bone matrices without an additional animation clock.

```java
ModelRenderer renderer = character.addComponent(new ModelRenderer(modelInstance));
ModelPoseAnimator animator = character.addComponent(new ModelPoseAnimator());
animator.setRootMotion("Root", delta -> {
  // Immutable model-space displacement. Route it to the game's movement authority.
  movement.acceptModelDisplacement(delta.x(), delta.y(), delta.z());
});
animator.play("Walk", PlayMode.LOOP);
```

The example's `movement` is application code, not an engine-provided controller adapter. The sink
does not automatically change `Transform3D`, `ModelInstance.transform`, a rigid body or a character
controller. Convert model-space displacement into the movement authority's coordinates, including
the model's orientation and unit scale, and handle blocked movement in that authority. Do not
write `Transform3D` directly for a bound Bullet body; use that body's explicit movement API.

The selected node's published translation stays at its captured bind translation. Its rotation
and scale, the remaining nodes, and skin matrices keep their ordinary pose behavior. Extracted
translation is all three axes; this API does not extract yaw, choose a horizontal plane, retarget
a rig or compensate animation after collision. The shared source model and tracks are unchanged.

## Supported composition

- A single named base clip, in `ONCE` or `LOOP` mode, on a WORLD animator.
- Layers and layer fades whose resolved masks exclude the selected root. A mask selecting the
  root is rejected even with zero weight or a clip without a root track.
- Exact top-level paths only. Unknown, ambiguous and descendant paths are rejected.
- Base mixers and base clip fades are rejected while extraction is enabled. Enabling extraction
  while a base mixer/fade or root-selecting layer exists is also rejected. A rejected selection,
  playback or layer addition preserves the preceding valid configuration and playback.

`clearRootMotion()` restores the ordinary current animated translation immediately. It emits no
delta. Call it before switching to a base mixer/fade or a root-selecting layer. Extraction is
disabled by default; ordinary animations retain their previous translation behavior.

## Time, loops and ownership

Each successful animator WORLD update emits at most one nonzero immutable `RootMotionDelta`.
The sink runs after the stripped pose and bone matrices are published, at the animator's component
position in WORLD order, before after-WORLD systems such as physics and before late update.
Later WORLD components can observe the result. The API requires WORLD update mode; changing the
animator to UI while extraction is configured causes the next update to reject before advancing.

The delta uses the same scaled scene delta and playback speed as the animation clock. Scene pause,
freeze, paused playback, speed zero, zero delta, a completed one-shot clip and an absent/static
translation track emit nothing. One-shot completion emits only the remaining displacement.

Loop motion is computed from sampled clip endpoints, including every crossed cycle:

`T(after) - T(before) + crossedCycles * (T(duration) - T(0))`.

This is authored translation displacement, not a guessed continuous path. Nonmatching endpoint
rotation/scale and arbitrary loop discontinuities are not repaired. Sampling uses the existing
linear translation sampler. A step crossing more than the clock's 1024-loop limit, or producing
nonfinite displacement, rejects before clock advancement, lifecycle callbacks or sink delivery.
The initial authored translation offset is never emitted as movement.

Play, normalized start, seek and restart select a new sampling origin without emitting a jump.
An immediate seek still publishes a stripped pose. A late-update seek affects the next WORLD
update and emits nothing during late update. There is no retained movement queue or baseline to
replay. A throwing sink propagates its exception after the pose and clock have committed; the
next tick extracts only its own newly advanced interval.

Animation callbacks run before sink delivery. If a callback replaces/stops/seeks/restarts playback,
changes its clock revision (including pause/speed), clears/replaces extraction, disables or
deactivates the owner, toggles renderer enablement, or edits the retained root translation curve,
the planned delta for that entire tick is discarded. Curve comparison checks every key's exact
time/XYZ float bits and the channel size, without a hash. Live retained-array/key edits remain
visible in pose composition and subsequent ticks; the changed interval is never replayed.
Replacing the public `NodeAnimation.translation` field after the animator retained its channel
does not rebind that channel, following the existing sampler's behavior. This conservative rule
also applies to changes in a completion callback; it avoids
delivering stale movement after gameplay has changed playback intent. Existing marker/start/loop/
completion callback semantics remain unchanged.

Each extraction configuration reuses its exact-comparison key buffer at the largest channel size
it has observed. Ordinary ticks do not allocate another key-sized snapshot after warmup. The
snapshot lease is released in `finally` on normal completion, early returns and callback/sink
exceptions. Nested captures use independent temporary storage so they cannot overwrite an active
outer snapshot. Replacing/clearing extraction or invalidating the renderer drops the configuration
and its retained buffer. Extraction still creates its immutable delta and sampling vectors;
this is not a zero-allocation claim.

Disabled/inactive owners do not emit motion. Existing pose ownership reconciliation may stop
their playback and restore bind pose; a retained configuration does not queue missed motion.
Renderer invalidation, replacement or removal clears extraction along with its node index.
Removal/destruction of the animator releases its pose ownership and emits no final delta.

Optional `PoseBatchSystem` caches retain ordinary composed local poses. Stripping happens after
cache lookup and does not poison another instance's cached animated translation.

## Evidence and bounds

`ModelRootMotionTest` exercises actual `Game`, `GameObject`, borrowed `ModelInstance`, node tracks,
hierarchy and CPU skin matrices, with and without pose caching. It covers sink/physics/late order,
multi-loop displacement, one-shot completion, seek/restart, pause/freeze/scale, callback changes,
throwing sinks, strict configuration, overflow and ownership cleanup. These are CPU behavior
tests; they do not establish imported production-rig coverage, GPU skinning output or a built-in
Bullet root-motion controller.
