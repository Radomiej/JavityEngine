# Game replication core

`javity-rooms` now has a transport-neutral replication core alongside the bounded local Colyseus demo. It is an API seam for game-specific authority and state, not a claim that arbitrary `GameObject` graphs or physics simulations can be mirrored automatically.

Applications register a `ReplicationSchema<T>` with an explicit stable string ID, version, and `StateCodec<T>`. The codec owns validation, encoding, decoding, and interpolation. No class names or reflection are read from the wire. `NetworkId` values are allocated by `AuthoritativeReplicationServer`; they do not use `GameObject.id`, which is local to a scene. `GameObjectBinding` is an opt-in map owned by the application, and its sample `apply2D` adapter only applies the supplied position schema. Call `prune(client)` after an accepted snapshot to unbind despawned network IDs; the binding never destroys caller-owned objects.

The server owns each replicated entity and its session owner. Client `PlayerCommand`s pass the sender/target ownership check, command allowlist, payload size bound, and monotonic per-session/per-command sequence check; sequence numbers are shared across that session's targets. Each registered command requires an explicit payload predicate, which runs before the application handler can mutate game state. `removeSession` drops owned entities and sequence tracking at disconnect. The handler applies game rules; client state never becomes authoritative by being sent back as a state update.

`snapshot()` emits a monotonically revisioned delta containing first-time `Spawn`, subsequent `Update`, and `Despawn` changes. Delta revisions are contiguous; a missed revision marks the client as requiring a full snapshot. `fullSnapshot()` advances the revision and emits all live entities as spawns; the host broadcasts this reset to every peer so late join and reconnect cannot create gaps for peers already in the room. The client requires a full snapshot initially and after reconnect, decodes and validates an entire envelope against temporary state, then applies it atomically. Stale revisions return `false`; malformed envelopes throw without changing state or history. Despawn tombstones for unknown IDs are ignored because peers can have different baselines. Per-entity decoded history is bounded to the configured 2–128 samples; each envelope is limited to 32,768 changes and 16 MiB encoded state, with 1 MiB per entity. Interpolation calls the registered schema codec; snapshots do not claim prediction or reconciliation.

`ReplicationWireCodec` provides a versioned JSON v1 format for envelopes and commands. Snapshot messages have `v`, `revision`, `full`, and `changes`; change types are a fixed spawn/update/despawn whitelist. State bytes are Base64. Unknown fields and versions, duplicate keys, malformed numbers/base64, deep JSON, and inputs over the configured limits are rejected. Client commands contain `v`, `target`, `command`, `sequence`, and Base64 `payload`; they have no sender field. `decodeCommand(verifiedSessionId, wire)` injects the session identity that the host already authenticated.

`InProcessReplicationExample` demonstrates deterministic authority-to-client flow without a transport. The loopback test transfers the production codec's JSON string between two Java peers over TCP and applies the decoded envelope. It proves socket delivery and codec interoperability, not a Colyseus integration. A future Colyseus adapter sends that same string as its message body. For example, Java authority handling is:

```java
PlayerCommand command = codec.decodeCommand(verifiedSessionId, commandWire);
authority.accept(command);
String snapshotWire = codec.encodeSnapshot(authority.snapshot());
```

The host then uses `room.send("replication:command", commandWire)` and `room.onMessage("replication:snapshot", wire => ...)` on the client, and `room.onMessage("replication:command", (client, wire) => ...)` plus `room.broadcast("replication:snapshot", snapshotWire)` on the server. The host bridge must resolve the verified session identity before passing the command string to Java; the client cannot choose it. Broadcast every full-snapshot reset to all peers. A future ticket-backed host can issue a short-lived, one-use room ticket and bind its verified identity to the Colyseus session. The current local gateway does not issue tickets or connect this API to Colyseus. These snippets describe the integration seam; no live Colyseus adapter or Java client SDK is included.

Minimal example:

```java
ReplicationRegistry schemas = new ReplicationRegistry();
schemas.register(Transform2DState.schema());
AuthoritativeReplicationServer authority = new AuthoritativeReplicationServer(schemas);
NetworkId player = authority.spawn(sessionId, Transform2DState.SCHEMA_ID, 1,
    new Transform2DState(0, 0));
ReplicationEnvelope initial = authority.fullSnapshot();
// Send initial through the host-selected transport; clients register the same schema first.
```

Close authority/client/bindings with their owning room or connection. These APIs are owner-thread confined, like `Game`; they do not provide thread safety. A game still chooses which values to replicate, which commands to expose, how authority is partitioned, and how physics outcomes are validated.

The examples send codec output as an opaque string. Keep it opaque through JavaScript transport layers rather than parsing and reserializing 64-bit IDs/revisions as JavaScript numbers. Generic prediction/reconciliation, live ticket-backed Colyseus transport, and automatic ownership of spawned GameObjects are not implemented by this replication core.
