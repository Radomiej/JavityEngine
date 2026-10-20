# Platform services

`org.javity.platform.PlatformServices` is an optional, portable façade for account identity, profile, versioned saves, and rooms. The core package contains no TeaVM or JSO types. Applications choose a `PlatformProvider`, then call the typed operations. Current ORBIT `profile()` aliases `player.get()` and exposes only id/nickname, so its avatar URL is empty. Results carry either a value or a stable `ErrorCode`; callers should treat provider errors as expected outcomes.

Provider callbacks may arrive on any thread. The façade queues them, and `pump()` delivers callbacks on the thread that constructed `PlatformServices`. Call it from the game/application loop; each pump processes only the completions queued when it began, so callbacks that submit more work cannot create an unbounded frame. Service calls, cancellation, pumping, and closing belong to the owner thread. Cancelling an operation suppresses its callback even when the provider already completed; closing the façade cancels outstanding operations, drops queued work, and closes its provider. Cleanup continues if cancellation or provider close throws, then rethrows the first failure with later distinct failures suppressed.

Save payloads are caller-owned JSON strings. The API parses and canonicalizes exactly one JSON value before constructing the ORBIT request, rejecting malformed or trailing values so payload text cannot change the request envelope. Revision zero means no save exists; `getSave` returns success with a null value for an empty slot, matching the SDK. `putSave` succeeds only when `expectedRevision` matches, then returns the incremented revision. This is compare-and-set behavior, not merge or retry. Stale revisions return `CONFLICT`.

`LocalPlatformProvider` is an explicit offline implementation. Its identity is synthetic, data exists only for the lifetime of the provider instance, and room list/create/close state is local to that instance; joining returns `UNSUPPORTED` because it cannot issue a network ticket. Room close returns `{ok}`. `OrbitPlatformProvider` maps the same operations onto the existing ORBIT browser bridge; identity and cloud saves depend on an embedded, authorized ORBIT launch. ORBIT room operations follow the SDK's current shapes: create takes mode/private/ranked/maxPlayers, list returns room items, and join returns endpoint/externalRoomId/ticket/expiresIn. `player.get` exposes id/nickname; an empty save is null and a save otherwise has data/revision. Treat `inviteCode` and `ticket` as secret capabilities and keep them out of logs and telemetry. Field mappings were checked against the vendored SDK and `Radomiej/nekko-launcher` HEAD `956386c` (`packages/game-sdk/src/protocol.ts`, `index.ts`, and `host.ts`). Neither provider supplies generic game replication, persistence guarantees beyond the ORBIT save contract, or offline synchronization.

The Wasm demo requests a profile and loads/saves its progress through this API. Press **P** in the demo to write the current position using the last observed revision. Existing launcher panel controls remain supported by the browser adapter.

```java
try (PlatformServices services = new PlatformServices(new LocalPlatformProvider())) {
  services.player(result -> {
    if (result.isSuccess()) System.out.println(result.value().displayName());
  });
  services.putSave("progress", "{\"level\":1}", 0, result -> {
    if (!result.isSuccess()) System.out.println(result.errorCode());
  });
  services.pump(); // In a running game, call once per UI/render frame on the owner thread.
}
```

The demo's Java save path and existing panel exchange the latest observed revision. They still use
CAS: simultaneous writes may return `CONFLICT`, and neither path silently overwrites the other.
The adapter validates save coordinates and required response fields before changing scene state.
The provider/parser tests and standalone Wasm proof do not replace a fresh authorized account/API
end-to-end test against the deployed private launcher.

The repeatable browser QA helper `python tools/orbit/typed-platform-proof.py` expects a package built
with launcher origin `http://localhost:5173` and that local port free. It serves the game on a second
origin with TeaVM JSO CSP and uses a synthetic host for SDK/MessagePort responses.
`--disable-draw --evidence-dir artifacts/typed-platform-negative` is a negative control and must fail.
The ORBIT workflow builds its test package separately so production launcher origins remain intact.
