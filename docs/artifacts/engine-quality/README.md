# Engine quality evidence — 2026-10-20

Curated output from the supervised Sol implementation batch. The source revision
and SHA-256 file inventory are in `manifest.json`. This is bounded functional
evidence on Linux x64, JDK25.0.4.1 and Mesa llvmpipe; it is not a GPU performance
benchmark or certification of Android/new-feature WebAssembly support.

- `fox-garden*.png`: actual integrated PBR, directional shadows, HDR/ACES,
  independently animated Fox instances and existing GameObject tools.
- `showcase-verification.json`: input, movement, animation, exposure/light pixels
  and cleanup assertions. Installed distribution also passed outside checkout,
  loading bundled classpath Fox resources without an asset-root override.
- `fox-original-gl30.png`: direct original glTF Fox, not converted G3DJ.
  The original Fox credits/license remain in
  [NOTICE](../../../tools/animation-validation/fox/NOTICE.md) and
  [SOURCE](../../../tools/animation-validation/fox/SOURCE.md).
- `shadow-context-restored.png`: first whole frame after actual unshared context
  replacement; exact equality with the preceding identical-policy frame is
  asserted by the shadow smoke. Missing-floor darkness is not accepted as proof.
- `hdr-reinhard.png`: linear radiance1 versus4 survives RGBA16F before mapping;
  native checks distinguish clipping, exposure and transfer functions.
- `ui-native.png`, `ui-comparison.json`: unchanged browser/native tolerance gate;
  fractional FreeType metrics retain the original atlas sizes. Hosted closure is
  tracked separately in [Bugs](../../Bugs/2026-10-20-engine-quality.md).
- `light-before.json`, `light-after.json`: same CPU snapshot workload including
  query copies, warmup10000, measured10000, three repetitions. Median bytes per
  default snapshot3679.202 →1552.027. No zero-allocation or FPS claim.
- `wasm-runtime.json`, `wasm-framebuffer.png`: existing actual Javity/libGDX Wasm
  GC renderer in Chromium/SwiftShader, input/resize/dispose and real framebuffer
  colors with DOM label excluded. This does not port optional glTF/HDR/shadows.
- `typed-platform-verification.json`: packaged Java/Wasm SDK with a synthetic
  MessagePort host, not proof of a live deployed launcher/API.
- `native-regression.txt`, `integrated-build.txt`: retained root validation logs;
  tasks marked UP-TO-DATE reused unchanged Gradle results.

Reproduce with the commands in [quality showcase](../../quality-showcase.md),
[glTF](../../gltf-assets.md), [shadows](../../shadows.md), [HDR](../../hdr-rendering.md),
and [Wasm spike](../../javity-wasm-rendering-spike.md).
