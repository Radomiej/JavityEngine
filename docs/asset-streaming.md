# Scene-owned local asset leases

`javity-assets` adds `AssetStreams`, an optional `GameSystem` over libGDX 1.14.2
`AssetManager`. Add `api project(':javity-assets')` in a consuming module. It uses
only the portable core/libGDX dependency graph, with no desktop natives, LWJGL,
Graal or FFM dependency.

Configure loaders/resolver first, then transfer an **empty** manager to the service.
A populated manager, unfinished queue, abandoned dependency metadata or pending
ModelLoader ModelData is rejected without taking ownership or clearing caller state.
After successful construction the manager and every stateful loader instance must
be exclusive to this service. Do not also give that manager to `SceneAssets` or
call its load/update/unload/dispose methods. The service owns its error listener.

```java
AssetStreams assets = game.addSystem(new AssetStreams(
    new AssetManager(), new StreamBudget(64, 1, 128L * 1024 * 1024, 64, 2)));
AssetLease<Model> model = assets.request(new AssetKey<>("models/actor.g3dj", Model.class),
    10, 8L * 1024 * 1024);
// Game.step pumps onUiUpdate, including while WORLD is paused.
if (model.state() == AssetLease.State.READY) {
  Model borrowed = model.get();
  // A ModelInstance/animator using borrowed must retain model's lease.
}
// After detaching all consumers:
model.close();
// After rendering, outside every batch:
assets.finishFrame();
```

Register the provider before consumer systems so reverse-order scene disposal
releases consumers first. Construct, request, inspect leases, close leases, pump,
finish frames and dispose on the owner/render thread. `get()` is valid only in
READY. Closing one lease does not affect another lease of the same key; the
manager owns one root reference regardless of the number of leases. Types and
estimates for the same queued/resident path must agree. Paths normalize backslashes
like libGDX descriptors; they do not canonicalize aliases or filesystem links.

The estimate includes dependencies and is supplied by the application; it is not
a measurement of GPU memory. Negative estimates or estimates above the configured
byte capacity are rejected. Zero is valid. `StreamBudget` limits the number of
unique queued roots, resident root entries, and aggregate resident estimates.
Dependencies are covered by each root's estimate, so shared dependencies may be
counted twice. Active transactions reserve their full estimate and one entry
before admission. Waiting roots do not consume resident capacity. A new unique
root beyond `maxQueued` throws `IllegalStateException`; deduplicated leases and
cache hits need no queue slot. Closing the final queued lease frees its slot.

The first request fixes a generation's priority. Waiting roots run by descending
priority and FIFO for ties; the queue head is never bypassed when it cannot fit.
No active loader is preempted. The update time allowance is soft (zero still
advances one manager step).

Only one root is physically admitted, even when `maxInflight` exceeds one. Other
roots stay in the wrapper queue. One manager update/upload can exceed the soft
time allowance; uploads run on the owner thread, while a correctly implemented
async loader prepares CPU data on its worker. Custom loaders must obey that split.

Logical close is immediate. Closing the last READY lease leaves an unpinned cache
entry. A later request gets that same object immediately, including before the
next frame boundary. Only `finishFrame`, outside every render batch, evicts cache
entries: it removes the least recently released/acquired ready roots until the
priority/FIFO queue head can fit. A monotonic owner-thread event order resolves
recency, with original queue-entry order as the tie breaker. If pinned roots alone
prevent the head from fitting, the cache stays intact and admission waits. With
no pressure, unpinned cache remains resident. Prefetch uses an ordinary lease held
by the host; closing it leaves the same cache candidate as any other consumer.

`stats()` returns an immutable `StreamStats` snapshot on the owner thread (zero
after disposal). `queued` counts live waiting roots, `inflight` reports actual
physically admitted transactions (0/1), `ready` includes unpinned cache, and `pinned`
counts READY roots with live leases. `estimatedResidentBytes` includes resident
roots and the active reservation, retained through failure until rollback.
`overBudget` reports demand pressure: at least one live queued root cannot fit
current byte/entry reservations and residents, including reclaimable cache. It can
be true even while admitted sums stay within limits; a busy loader alone does not
set it. This is no measurement of actual GPU allocation.

Closing the last lease of an active load lets that load finish but suppresses
publication to closed leases. A same-path fresh request waits for the obsolete generation to be
drained/released unconditionally at `finishFrame`, then loads separately. No active
lease is physically released.
During a transaction even unrelated root releases wait, preserving its rollback
baseline. Scene disposal closes publications, drains active work on the owner
thread, and then releases resources. It may block on I/O/preparation and may perform
owner-thread uploads just to complete cleanup. It is not interruptible I/O.

Failure is attributed to the sole root and records the failing dependency when
libGDX supplies it. Queued unrelated requests remain queued. An explicit retry is
a fresh generation; old FAILED/CLOSED leases retain their states. Recovery at
`finishFrame` restores every baseline reference count and preserves baseline
objects/dependency arrays. Newly registered assets release in parent-before-child
order; abandoned dependency keys and pending default ModelLoader ModelData records
are removed exactly. A single-worker barrier is awaited **outside** the manager
monitor before abandoning any tasks left by a secondary libGDX cleanup exception.
If libGDX itself throws during error cleanup before notifying its listener, the
root and secondary exception remain available but the original leaf descriptor
may no longer be available.

Cleanup continues after a registered object's `dispose()` throws, reports the
exception (suppressed on the load failure where applicable), and does not call that
object's disposer twice. An object whose disposer throws can retain its own native
resources; the service cannot promise that object's internal cleanup succeeded.
Arbitrary custom-loader allocations not registered in AssetManager remain the
loader's responsibility on exceptions. The default ModelLoader hook clears its
known pending CPU ModelData; it does not discover arbitrary private allocations.

Two small same-package bridges access pinned libGDX 1.14.2 internals. They preserve
configured loaders and the ordinary loading algorithms, without reflection or
replacing the manager. `Version.VERSION` is checked at service construction. These
bridges intentionally create split packages: future JPMS modularization, different
class loaders, and libGDX upgrades need a new audit and tests. Android packaging,
GLES uploads and context recovery have **not** been validated for this module.
CPU loader tests establish ownership; they do not establish graphics readiness.
See `THIRD_PARTY.md` for provenance.

`GameSystem.validateDispose()` is a side-effect-free precondition called before any
lifecycle mutation, even on repeated disposal. AssetStreams uses it only for owner
thread validation. A rejected direct call leaves the system/resources intact for
later owner-thread disposal. This does not make `Game.dispose()` thread-safe: the
whole game must still be closed by its owner because it mutates other lifecycle
state before visiting individual systems.
