# Task 1 report: Java local room simulation and worker

Status: complete. Base: `b539196a8ee76f342b7de859da485f4b953d09f9`. Head: `6a914569c5d64788a815480c063f690513ec2bcd` (`Add headless Java room worker`).

Implemented optional `javity-rooms`, with no change to core or ECS sources. `LocalRoomSimulation` creates real core `Game`/`GameObject` instances, confines all access and close to the creator thread, applies 1/60 steps, normalizes diagonal input, expires input at 250 ms, clamps positions to ±50, caps rooms at 8 players, and enforces monotonically increasing safe-integer sequences. `LocalRoomWorker` provides JSONL v1 over stdin/stdout, a bounded 64-frame inbound queue and 64-line daemon writer, while keeping diagnostics on stderr. It rejects malformed/mismatched/extra-field commands, checks the 8192-byte limit before LF (including incomplete frames), emits snapshots every three ticks, and closes Game on the owner thread after shutdown, EOF, framing failure, or output failure.

Files: `settings.gradle`; `javity-rooms/build.gradle`; `javity-rooms/src/main/java/org/javity/rooms/{LocalRoomSimulation,LocalRoomWorker,RoomPlayer}.java`; `javity-rooms/src/test/java/org/javity/rooms/{LocalRoomSimulationTest,LocalRoomWorkerProcessTest}.java`.

## Protocol and launch contract for Task 2

Run `:javity-rooms:installDist`, then launch the generated script with a Java 25 runtime:

```sh
javity-rooms/build/install/javity-rooms/bin/javity-rooms <gateway-generated-roomId>
```

The script uses `java` from `PATH` (or `JAVA_HOME`); the service must use JDK 25 because module classes target Java 25. The room ID is one positional argument. stdin is newline-delimited UTF-8 JSON; stdout contains only JSONL. Example:

```json
{"v":1,"roomId":"room-1","type":"join","requestId":"j1","sessionId":"gateway-session"}
{"v":1,"roomId":"room-1","type":"input","requestId":"i1","sessionId":"gateway-session","sequence":0,"x":1,"z":0}
{"v":1,"roomId":"room-1","type":"shutdown","requestId":"q1"}
```

Observed launch output is `{"v":1,"roomId":"launch-check","type":"ready"}` followed by the shutdown ack. Accepted join/leave/shutdown commands return `ack`; invalid commands return `error` with a code and message (and requestId when it was a valid string). Stale input is ignored without ack. Snapshots are `{"v":1,"roomId":"…","type":"snapshot","tick":N,"players":[{"sessionId":"…","x":0.0,"z":0.0,"lastSequence":-1}]}`. Commands must contain exactly their defined fields; no client-supplied position or target fields are accepted. The gateway must supply its own session IDs.

## Verification

- RED: temporarily changed movement speed from 4 to 3; `:javity-rooms:test --tests org.javity.rooms.LocalRoomSimulationTest.fixedMovementSequenceExpiryAndRoomLimit` failed at the hardcoded 4-unit/s displacement assertion. See `logs/task1-red.log`.
- GREEN: `:javity-rooms:test :javity-rooms:installDist --no-daemon` passed; seven tests passed, including actual child Java processes for join/input/movement snapshot/leave/shutdown, malformed input, wrong room, extra fields, EOF, oversized unterminated input, and undrained stdout backpressure. See `logs/task1-final.log`.
- The generated install script was launched with JDK 25 and completed a ready/shutdown exchange. See `logs/task1-installDist.log` for the distribution build and `logs/task1-launch.log` for the real ready/shutdown exchange.
- `git diff --check` passed.

Gradle initially could not start in the default sandbox because it could not determine a usable wildcard IP. Running the installed Gradle 9.8.1 distribution with network-enabled sandbox permissions resolved that environment issue. A launch using the system Java 21 failed with class-file version 69/maximum 65; the actual runtime contract therefore requires JDK 25 on the gateway host.

No browser or Colyseus integration is claimed by Task 1. No ECS files were changed.
