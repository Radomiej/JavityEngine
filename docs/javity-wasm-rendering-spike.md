# JavityEngine rendering on WebAssembly

This isolated experiment builds and runs **actual Javity rendering**, rather than drawing replacement JavaScript geometry. The path is:

`Javity Game / Component / Transform3D / ModelRenderer / Camera3D → GdxRenderer → libGDX → gdx-teavm → Wasm GC + WebGL2`.

The launcher's regular libGDX `ApplicationAdapter` lifecycle is driven by `WebApplication`. Java creates a ground model, a movable cube, six columns and a sphere animated by a real Javity component. `GdxRenderer.render(game)` draws them with the engine's model pass and shader provider. Keyboard input goes through `Gdx.input`; movement is calculated in a Javity component. JavaScript in the launcher only publishes diagnostic state for browser QA; it does not build or render the scene. The HTML label is an experiment overlay, not the engine UI.

## Build and run

Requires JDK 25 and a browser supporting Wasm GC and WebGL2. From the repository root:

```sh
./gradlew -p experiments/javity-wasm gdx_teavm_web_wasm_build
python3 -m http.server 8765 --bind 127.0.0.1 --directory experiments/javity-wasm/build/dist/wasm
```

Open **http://127.0.0.1:8765/webapp/index.html**. Click the canvas, then use WASD or arrow keys to move the cube. The orange sphere is animated by a Java component; it is not Bullet physics in this experiment.

The build is deliberately separate from the default reactor. It first builds/refreshes the real core jar through its `buildCore` prerequisite and uses the release-matched **gdx-teavm 1.6.1 / libGDX 1.14.2 / TeaVM 0.15.0**. It does not load desktop LWJGL, Bullet JNI, GraalJS or native MapLibre modules. Generated output lives under ignored `build/dist/wasm` and includes an actual `app.wasm`, TeaVM runtime, backend glue and preloaded shader/font resources.

The backend's `useGL30` option is experimental and can fall back to WebGL1. This launcher explicitly rejects missing GL30, so a successful probe establishes the WebGL2 path. The engine's shader-recovery reflection requires exact metadata for `Version`, `BaseShader` and `DefaultShader`; the leaf build declares it without changing the default desktop build.

## Actual browser verification

After building, run:

```sh
python3 experiments/javity-wasm/tools/chromium-proof.py
```

The script owns a temporary HTTP server and Chromium instance. It requires Python Playwright, Pillow and `/usr/bin/chromium`. It checks Java-rendered frame progression, real GDX keyboard movement and release, resize, nonuniform framebuffer pixels, GL errors accumulated from the first frame, and explicit scene disposal. It saves [the screenshot](../experiments/javity-wasm/evidence/javity-wasm.png) and [runtime evidence](../experiments/javity-wasm/evidence/runtime.json). Artifact/source hashes and build results are recorded in the adjacent `verification.json` after verification.

The verified browser path uses **ANGLE/SwiftShader software WebGL2 in headless Chromium**. This proves the API/renderer path and framebuffer output; it is not a hardware-GPU performance measurement or support matrix for other browsers.

## Portability change found by the build

The first real engine compilation failed because TeaVM 0.15 does not provide `Arrays.compare(float[], float[])`, reached by `SceneLightingAttribute.compareTo`. That call now uses equivalent lexicographic `Float.compare` iteration. Tests compare the ordering against the JDK, including empty arrays, prefix lengths, infinities, NaN and signed zero, and retain point/spot/fog comparison priority. No scene-lighting behavior change is intended.

## Current boundaries

This is an executable rendered scene using real engine classes, not the entire engine feature set certified for the web. The earlier [Yoga/Ammo bridge spike](teavm-wasm-spike.md) remains separate. Production Yoga/UI, browser physics, assets beyond this generated scene, MapLibre GL JS, networking integration, context loss, mobile/Firefox/Safari and performance still need their own integrations and gates. Compiling the reachable renderer code does not prove every public API or every optional module.

Upstream source: [gdx-teavm 1.6.1](https://github.com/xpenatan/gdx-teavm/tree/1.6.1), tag commit `e098ddf3b9c3dcb9dfd43356e66be05db91237a0`; [example launcher](https://github.com/xpenatan/gdx-teavm/blob/1.6.1/examples/basic/platforms/web/plugin/src/main/java/TestWebLauncher.java). gdx-teavm and libGDX use Apache-2.0; dependencies and generated backend resources retain their upstream licensing.
