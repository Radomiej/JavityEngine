# TeaVM WasmGC browser spike

**Status:** isolated experiment, not a production browser backend or adapter. Checked 2026-10-17.

The spike demonstrates a real TeaVM 0.15.0 WasmGC binary built from a small Java facade linked against the repository's built `javity-core` jar. The facade creates and steps actual `org.javity.core.Game` and `GameObject` instances, then exports `create`, `tick`, `input`, `frame`, `resize`, and `dispose`. `@JSBody` callbacks from those Java exports initiate a JavaScript host that uses Yoga and Ammo. This preserves the JavaLab-style lifecycle shape while making the Java/JS boundary explicit.

Chromium loaded the WasmGC binary and its TeaVM JavaScript runtime. One run returned `frame = 60`, `state = 15`, and 60 Java-to-host tick callbacks. The host laid out a Yoga child as `(left=0, top=0, width=80, height=40)`. It created a Bullet sphere at Y=5, stepped its world 60 times at 1/60 second, queried its transform, and observed Y≈0.0833343. The exported Java `dispose()` then removed the rigid body and freed 13 Ammo objects: the gravity vector immediately after `setGravity` copied it, then 12 world/body objects during cleanup, plus the Yoga subtree. Chromium reported no page or console errors. Runtime details are in [runtime.json](../experiments/teavm-wasm/evidence/runtime.json), with the matching [Chromium screenshot](../experiments/teavm-wasm/evidence/chromium-proof.png).

## Reproduce

Requirements: JDK 25, Gradle wrapper 9.8.1, Node/npm, Python Playwright, and `/usr/bin/chromium`. Build the core jar first from the repository root; the checked artifact used for this evidence has SHA-256 `a10e1a6009ea9fa131179435cf0ae91cb6466d7dfdc890fa7b32949c0e7f469b`.

```sh
./gradlew :javity-core:jar
npm --prefix experiments/teavm-wasm ci
./gradlew -p experiments/teavm-wasm buildWasmGC
cp experiments/teavm-wasm/build/web/wasm-gc/app.wasm* experiments/teavm-wasm/web/wasm-gc/
python3 experiments/teavm-wasm/tools/chromium-proof.py
```

The Gradle experiment reads `javity-core/build/libs/javity-core-0.7.0-SNAPSHOT.jar` directly and does not configure or modify the repository's root build. The proof script starts its own temporary server on an available loopback port and shuts it down after Chromium exits. It asserts the exports' lifecycle results, Yoga geometry, falling body, all 13 Ammo frees, and absence of browser errors. `runtime.json` stores the exact observed values. The browser page uses the checked-in TeaVM output and upstream Ammo pair; rebuild TeaVM and recopy its two output files before repeating after Java changes.

## Pinned inputs and provenance

- TeaVM Gradle plugin `org.teavm` 0.15.0 and `org.teavm:teavm-jso:0.15.0`.
- `com.badlogicgames.gdx:gdx:1.14.2`, matching the repository's core build.
- `yoga-layout@3.2.1`, npm integrity from `package-lock.json`, MIT, Meta Open Source; source is `facebook/yoga` tag `v3.2.1`. License text: [`YOGA-LICENSE.txt`](../experiments/teavm-wasm/YOGA-LICENSE.txt). The package bundles its own Yoga Wasm payload in ESM glue.
- Ammo.js glue and Wasm binary from `kripken/ammo.js` commit `c8779e60159d428f11ff1eb553dc9b6b6e2d317a`, pinned by the earlier browser Wasm audit; upstream uses the zlib license (same license as Bullet). License text: [`AMMO-LICENSE.txt`](../experiments/teavm-wasm/AMMO-LICENSE.txt).

SHA-256 values for checked browser artifacts:

| Artifact | SHA-256 |
| --- | --- |
| TeaVM `app.wasm` | `7f09817e3d8251e352632305a3e7ef0c25ff62f91c13fdbb05c84933bbd0106e` |
| TeaVM `app.wasm-runtime.js` | `4fa1e0e0d686cc6e0a0d111e606cb876f7168bd695d9e3d639e7d5684453089b` |
| Yoga `yoga-wasm-base64-esm.js` | `2cfeda49ae87f57bcd4a5fe433940edefc531a69bd0f83e32759fb5837df205c` |
| Ammo `ammo.wasm.js` | `8481c5250507d3f2c0cecff7cc55a34727f52f96590a2304556a43ca4cf4e0b3` |
| Ammo `ammo.wasm.wasm` | `de36b300b25d244a79ae56ef0e7fee5efe2a3281f346a134945eef0b2fbaa5d8` |
| core jar used for build | `a10e1a6009ea9fa131179435cf0ae91cb6466d7dfdc890fa7b32949c0e7f469b` |

## Limits shown by this proof

This proves WasmGC compilation, TeaVM runtime loading in one Chromium build, calls across the Java-to-JS boundary, and narrow Yoga/Ammo lifecycle operations. The Java facade reaches real core objects, but this is not a compilation or runtime test of the full `javity-core` API surface. It does not provide a `YogaBackend` implementation, Javity physics provider, GL renderer, input device adapter, real frame rendering, contacts, query variants, or the rest of `javity-physics3d` semantics. The host-side Yoga and Bullet code is throwaway browser proof code. The Ammo step/query use the upstream binding directly and do not establish equivalence with the engine's Bullet module. No JS-only mode, full engine build, mobile browser, Safari, Firefox, performance, or production dependency graph was validated. The `app.wasm-runtime.js` and HTML/JS host remain necessary browser assets; the Wasm binary alone is not an application.


The next experiment now exercises the actual Javity/libGDX renderer on Wasm GC: [rendering spike](javity-wasm-rendering-spike.md). The original Yoga/Ammo lifecycle proof remains separate.

## Review lifecycle — 2026-10-17

Host wydzielono do `web/browser-host.js`: własność per generacja, anulowanie spóźnionego startu, rollback częściowej konstrukcji i idempotentny cleanup. `python experiments/teavm-wasm/tools/bridge-lifecycle-proof.py` uruchamia siedem scenariuszy w rzeczywistym Chromium/Yoga/Ammo. [Checklista błędów](Bugs/2026-10-17-wasm-integration-review.md), [specyfikacja](Spec/2026-10-17-runtime-wasm-quality.md). Wyniki liczą tożsamości wrapperów, nie odzyskaną liniową pamięć Wasm. Kod Java i jego wygenerowany Wasm nie zmieniły się w tym refactoringu; nowe hashe hosta i dowody zapisano w `experiments/teavm-wasm/evidence/quality-verification.json`.
