# Haptics

The `org.javity.haptics` API separates declared backend capability from a playback handle. The current default is `UnsupportedHaptics`; it reports all capabilities as false and returns an inactive handle with `UNSUPPORTED` status for valid requests. It never reports a successful vibration. This is the only backend currently shipped by the engine.

| Capability or behavior | Current support |
| --- | --- |
| Capability query | Yes; all haptic features report unavailable |
| Valid pulse or pattern request | Validated, then returns `UNSUPPORTED` |
| Amplitude control | No |
| Patterns | No |
| Android, gamepad, or device vibration | No implementation or device evidence |

`HapticPulse` uses normalized intensity in `[0, 1]` and duration in seconds. A request duration must be finite, positive, and at most 10 seconds. `HapticPattern` copies a non-empty list of at most 32 pulses and rejects a total duration above 10 seconds. Construction performs the same validation regardless of backend support, so invalid input does not become silently accepted by the fallback. Zero intensity is allowed as an explicit zero-output pulse value.

`HapticFeedback.pulse` and `HapticFeedback.pattern` adapt a request to the existing `Feedback.resource` lifecycle. Each eligible playback acquires one handle and closes that handle on natural completion, cancellation, owner removal, or callback failure. The helper does not own or close the supplied `Haptics` service; register that service with the appropriate scene or host lifetime and close it there. The API has no global singleton.

`ExclusiveHaptics` is a future adapter boundary for a native API with one global cancel call. It permits one current native request. Starting another request invalidates the old handle. Only the current token can invoke native cancel, so cancelling or closing a stale handle cannot stop its successor. A backend that cannot start a request returns an inactive `FAILED` handle and is cleaned up. Backends must report only capabilities they can invoke. A backend without amplitude control may implement basic pulses as on/off, but must retain `amplitudeControl=false`.

The exclusive adapter is single-thread confined: call it and its handles on one owner thread. Synchronous backend callbacks may close the adapter, but a nested `play` during backend `play` or global `cancel` throws `IllegalStateException`. Closing during a start marks the service closed immediately and defers global cancellation until that start returns, so a late native start cannot remain active after close. A failed start also attempts global cleanup; cleanup failures propagate (or are suppressed on a start exception) after the manager invalidates ownership. Asynchronous adapters must marshal operations to their owner thread and must not treat CPU mocks as native completion evidence.

The exclusive adapter is a CPU-testable contract, not a device implementation. No Android module, OS capability check, permission declaration, focus/lifecycle integration, emulator invocation, or felt hardware output is included. Those require a separate Android runtime slice and device validation.
