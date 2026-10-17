# JavityEngine browser, Wasm, and multiplayer feasibility

**Audit date:** 2026-10-17
**Scope:** Read-only review of `/workspace/JavityEngine` plus upstream documentation. No build or browser runtime was run, so this is a compatibility assessment, not a support claim.

## Finding

**A browser port is feasible, but the checked-in engine is not currently a browser/Wasm build.** The root build targets Java 25 and libGDX 1.14.2, but `settings.gradle` has no HTML/GWT/TeaVM target. The generated `html` project is a legacy GWT template (libGDX 1.9.6, GWT 2.8, Java source level 1.6), not a build path for this engine.

There are two materially different approaches:

- **libGDX GWT/HTML backend:** compiles Java source to browser JavaScript. It is not a WebAssembly compiler. Its Java subset, reflection, and platform-specific dependency constraints make it a poor fit for this Java 25 project without substantial source/dependency adaptation.
- **TeaVM with gdx-teavm:** the upstream project documents JS and WebAssembly GC targets and an exact released pairing of gdx-teavm 1.6.1 + libGDX 1.14.2 + TeaVM 0.15.0. TeaVM 0.13 announced Java 25 and Wasm GC coroutine/thread support. This means a Java 25/libGDX browser target is technically plausible, but does not establish that JavityEngine compiles or runs on it.

Wasm GC output still has a JavaScript web runtime/loader and JavaScript interop boundary. A `.wasm` artifact is real WebAssembly output, but it does not turn LWJGL/JNI libraries into browser-compatible code or remove browser API requirements.

## Repo boundaries that block an “as-is” target

- The root includes desktop `javity-maplibre`, `javity-script-graal`, `javity-ui-yoga-lwjgl`, `javity-physics2d`, and `javity-physics3d`; the desktop application pulls native Box2D, FreeType, and Bullet runtimes. Browser target dependencies must be selected explicitly rather than inheriting the desktop runtime graph.
- `javity-maplibre` is directly bound to LWJGL3, EGL/GLES, MapLibre Native FFI and a Linux-x64 native runtime. It is explicitly a desktop Linux EGL integration. A browser map renderer would be a different integration, for example MapLibre GL JS, with a JS/WebGL bridge; Native FFI does not become browser-safe through TeaVM.
- `javity-physics3d` uses `gdx-bullet` JNI/native code. No browser Bullet adapter is present. Exclude it from the first browser slice or implement and validate a separate browser physics choice.
- `javity-physics2d` depends on `gdx-box2d`, the JNI wrapper. libGDX has a distinct `gdx-box2d-gwt` emulation module based on JBox2D, but this is not a drop-in proof for Javity's wrapper: source/dependency compatibility and behavior must be tested. `gdx-teavm` also lists a separate Java Box2D binding as a project, not as a ready replacement for this module.
- Script engines are platform-specific: `javity-script-graal` uses Graal Polyglot and is declared desktop; QuickJS is Android-only; no browser JavaScript provider is wired into `javity-script`. A browser host can use the page's JS runtime or a TeaVM JSO-backed provider, but the existing providers cannot simply be assumed portable.
- Yoga is isolated behind `YogaBackend`, which is useful for a port, but the only checked-in providers are LWJGL Yoga JNI and Android Yoga JNI. `javity-ui` discovers them with `ServiceLoader`; browser provider selection/initialization needs an explicit supported implementation and likely explicit provider wiring.
- `javity-ui` depends on libGDX FreeType. gdx-teavm advertises a separate `gdx-freetype-web` module, but compatibility and the project’s direct dependency/API use need a build/runtime proof. Browser font generation, assets, and fallback behavior should be checked.
- `javity-net` is legacy HTTP/TCP-oriented. It wraps `HttpURLConnection`, Java `URL`/`File`, `AsyncExecutor`, and libGDX `Socket`/`ServerSocket`; `RapidJson` uses Jackson databind/reflection. These are concrete portability review points, especially for GWT and browser I/O. The new multiplayer transport should avoid treating this module as a browser-ready transport.
- Core rendering uses libGDX GL abstractions and custom shaders. libGDX documents WebGL 2 support in its HTML backend, but GL profile, GLSL dialect, texture/precision limits, input/audio, and screenshot parity need tests against the actual Javity scenes. A GLES/WebGL API match does not establish shader or feature parity.

The repository is already careful to isolate many of these systems into optional modules. A browser target should keep that boundary and compile only a portable core plus deliberately chosen browser adapters. Static search also shows reflection-sensitive UI/service loading; TeaVM has reflection support/configuration, but its metadata and supported Java APIs must be validated against actual reachable code. GWT's documented reflection model is especially restricted.

## Multiplayer

**Multiplayer does not require WebAssembly.** A browser client can be JavaScript output from GWT or TeaVM, or ordinary TypeScript/JavaScript, and speak the same versioned application protocol as native clients. The server remains a separate service (Java is a reasonable choice); the browser cannot host the authoritative public TCP listener.

The transport at the browser edge should be HTTPS for requests and typically WSS/WebSocket for a persistent low-latency session. libGDX's documented generic TCP sockets exclude GWT; browser code cannot open arbitrary TCP sockets. Use a browser WebSocket adapter (explicit JS API binding or a tested backend feature) and a server-side WebSocket endpoint, or use HTTP polling where suitable. The `javity-net` classes are not evidence that a WebSocket protocol, game session, state replication, prediction, or reconnect behavior exists: no such multiplayer implementation was found in the checked modules/docs. Keep the serialized message schema and game rules transport-neutral so native TCP and browser WSS can share the protocol if both are wanted.

## Recommended path and gates

1. **Start with a minimal browser client target**, not all modules: a small launcher and one scene using `javity-core`, basic assets/input/rendering, and the TeaVM JS target. Pin the upstream pairing `gdx-teavm 1.6.1` / `libGDX 1.14.2` / TeaVM `0.15.0` for the first spike. JS makes browser interop and diagnostics simplest while exercising the same Java core.
2. **Compile a Wasm GC variant from that same portable source set** once the JS target is stable. Keep generated JS loader/runtime and page assets as part of the deliverable. Test in the intended browser matrix; no browser support claim follows from compiler output alone.
3. **Add platform seams one at a time:** explicit browser Yoga provider; font path; then only the desired physics/map/script adapters. Prove real framebuffer output and scene behavior. Leave `javity-maplibre`, desktop Graal, LWJGL Yoga, and Bullet out unless replaced by browser-specific implementations. Test actual shaders and the scene features users need (including any lighting/postprocess paths) before claiming parity.
4. **Add multiplayer independently of the Wasm decision:** define/version messages and authoritative server behavior; add a browser WSS client adapter plus native client adapter if desired. Gate on a real browser↔server loop with reconnect, ordering/duplication handling, latency and protocol-version checks. The existing `javity-net` module is not that gate.

Suggested gates, in order: (a) dependency graph contains no JNI/LWJGL/native-only desktop modules; (b) compile and asset packaging under pinned TeaVM JS; (c) browser launch, input, asset load and framebuffer/shader smoke on the chosen browser(s); (d) same source builds and runs under Wasm GC; (e) each optional adapter has its own runtime proof; (f) WSS round-trip against the separate server. Record compiler output and runtime evidence separately.

## Sources

Repo files inspected: `AGENTS.md`, `settings.gradle`, root `build.gradle`, `gradle.properties`, module Gradle files for core/UI/script/network/physics/maplibre/desktop/Android, `javity-net` Java sources, and generator `DependencyBank.java` plus the legacy HTML template.

Primary upstream sources (checked 2026-10-17):

- [gdx-teavm README and compatibility table](https://github.com/xpenatan/gdx-teavm) — JS/Wasm web targets and released libGDX 1.14.2 pairing.
- [gdx-teavm setup guide](https://github.com/xpenatan/gdx-teavm/blob/master/README.md) and [plugin properties](https://github.com/xpenatan/gdx-teavm/blob/master/docs/plugin-properties.md) — `js {}` vs `wasm {}` target configuration and generated `.wasm` runtime support.
- [TeaVM 0.13 release notes](https://teavm.org/docs/release-notes/0.13.0.html) — Java 25 support and Wasm GC coroutine/thread support.
- [libGDX HTML5/GWT backend notes](https://libgdx.com/wiki/html5-backend-and-gwt-specifics) and [starter classes/reflection notes](https://libgdx.com/wiki/app/starter-classes-and-configuration) — GWT browser backend and reflection limitations.
- [libGDX Box2D docs](https://libgdx.com/wiki/extensions/physics/box2d) and [upstream GWT extension list](https://libgdx.com/wiki/articles/dependency-management-with-gradle) — native Box2D wrapper and separate GWT Box2D module.
- [libGDX networking docs](https://libgdx.com/wiki/networking) — HTTP/TCP API and TCP exclusion on GWT.
- [libGDX OpenGL ES support](https://libgdx.com/wiki/graphics/opengl-es-support) — GL/WebGL capability context; it does not guarantee Javity shader parity.
- [TeaVM Wasm GC loader API](https://www.teavm.org/docs/wasm-gc-backend/loader.html) — browser loading/runtime API for generated Wasm GC modules.
