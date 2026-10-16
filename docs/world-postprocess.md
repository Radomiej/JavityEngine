# World postprocessing

`org.javity.postprocessing.WorldPostProcess` captures one host-selected world pass into an owned `RGBA8888` color target with a 16-bit depth renderbuffer. It composites that color to the framebuffer and viewport that were active on entry. The callback runs while the owned target is bound; end all `ModelBatch`/`SpriteBatch` work inside the callback. Call viewmodel and screen UI passes after `renderWorld` returns so they remain outside the effect.

```java
postProcess.renderWorld(() -> renderer.renderWorld(game, worldCamera, avatarView));
renderer.renderViewmodel(rig.viewmodelCamera(), viewmodelProvider);
renderHudAndStage();
```

The host owns `GdxRenderer`, cameras, game, UI and callback resources. The postprocessor owns its framebuffer, color texture, depth renderbuffer, full-screen mesh/VBO and shader. Create, resize, render and close it on its GL owner thread before disposing the context. It does not close or retain host renderers or callback resources.

The physical target is limited to 4096 pixels per dimension and 128 MiB for known attachments (6 bytes per pixel: RGBA8 plus depth16). Validation happens before allocation. During transactional resize, the old and candidate attachment sets together must fit that 128 MiB budget. Resize publishes a candidate only after construction succeeds; invalid sizes preserve the current target. The estimate excludes driver bookkeeping and any multisampling because this pass does not enable MSAA.

Controls have persistent base tint/desaturation/vignette and up to 64 independent effect leases. Tint contributions multiply in acquisition order; desaturation and vignette add and clamp to `[0,1]`. Each lease has an intensity in `[0,1]`; closing a lease removes only that contribution, and resolution uses the current base. Tint channels accept `[0,4]`. Invalid and non-finite inputs are rejected before state changes. An identity result selects a shader bypass branch so captured color is sampled without color math.

The pass supports RGB tint, luminance desaturation and a radial vignette. It does not support bloom, depth of field, HDR, user shaders, shader variants, transparency sorting, or viewmodel/HUD effects. The callback is explicit: hosts choose which world rendering belongs in the capture. GL framebuffer, viewport, caller VAO and its attribute descriptors, texture unit/bindings, shader program, array/element buffers, depth test/function/range/write state, blend state, cull enable/mode, scissor, color mask and clear values are restored in `finally`, including when the callback throws. On GL20, active or buffer-backed affected caller attributes are rejected before capture; the smoke covers disabled/unbound repeated passes, prebound rejection and element-array preservation. GL30 preserves the caller VAO directly.

Run the standalone real-context proof with:

```sh
./gradlew :javity-desktop:postprocessSmoke
```

The dedicated launcher runs GL 3.2 and GL20 contract probes. It checks numeric readbacks for bypass, tint, full desaturation, vignette center/edge, an asymmetric orientation marker, overlapping and cancelled leases, caller HUD after the pass, resize preservation, caller VAO/attribute restoration with immediate mesh reuse, resource identity/deletion and borrowed resource survival. It writes a small set of PNGs plus expected/observed pixel samples to [`docs/artifacts/world-postprocess/`](artifacts/world-postprocess/).
