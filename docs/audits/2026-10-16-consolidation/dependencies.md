# JavityEngine libGDX / build dependency audit

Audit date: 2026-10-16 UTC. Repository: /workspace/JavityEngine, requested base 48814ae. Read-only source/config/docs review and official metadata lookup only; no build, dependency resolution, code edit, or install.

## Release status and versions

libGDX 1.14.2 is the latest stable published release as of the audit date. Maven Central metadata for com.badlogicgames.gdx:gdx reports latest and release as 1.14.2, lastUpdated 20260605085116. The official GitHub latest release endpoint reports tag 1.14.2, published 2026-06-05T08:18:26Z. The configured gdxVersion in build.gradle:14 is current.

Sources:
- https://repo.maven.apache.org/maven2/com/badlogicgames/gdx/gdx/maven-metadata.xml
- https://github.com/libgdx/libgdx/releases/latest
- https://github.com/libgdx/libgdx/releases/tag/1.14.2

The central pin and actual runtime payload have multiple version axes:

- Root build.gradle:38–49 forces the named libGDX artifacts (core, box2d, freetype, lwjgl3/headless backends, platform classifiers, bullet) to gdxVersion 1.14.2. javity-android-app/build.gradle:15–21 and javity-maplibre/build.gradle:25–32 also explicitly use 1.14.2. The extracted official Lwjgl3GLES20 adapter comes from gdx-lwjgl3-angle:1.14.2.
- Official gdx:1.14.2 POM declares gdx-jnigen-loader:2.5.2. Its own metadata reports 2.5.2 as latest. This independent version is already correctly excluded from the root force rule.
- gdx-ai:1.8.2 in javity-backend/build.gradle:5 is independently versioned (Maven metadata latest is 1.8.2). Do not force it to 1.14.2.
- LWJGL is separately forced to 3.4.3 by build.gradle:17, while libGDX's 1.14.2 LWJGL3 and ANGLE POMs declare LWJGL 3.3.3. Therefore LWJGL Java/native artifacts are a 3.4.3 payload. javity-ui-yoga-lwjgl/build.gradle:11–15 also uses 3.4.3.
- Android uses ABI classifiers natives-arm64-v8a and natives-x86_64 for core and FreeType (javity-android-app/build.gradle:18–21), not desktop natives. That packaging does not prove physical ARM64 execution.

Other pinned values are independent projects: AGP 9.4.1, Kotlin 2.4.20, MapLibre FFI 0.202610.0, Yoga 3.2.1, QuickJS adapter 1.0.15, etc. Upgrading libGDX means moving the libGDX coordinate family together after adapter/API review; it does not mean changing jnigen-loader, gdx-ai, or other extension versions to the main version.

## Platform and Java 25 portability

The root Gradle convention targets Java 25 for non-Android projects (build.gradle:24–36). Android is opt-in via -PjavityAndroid=true (settings.gradle:30–35); app and Android libraries separately compile for Java 25. The evidence in docs/android-status.md:3–15 records AGP 9.4.1/Kotlin 2.4.20 builds and API 35 x86_64 emulator runs, including individual lifecycle and EGL recovery checks. It also records combined instrumentation runner ANRs, unverified physical ARM64/performance, and MapLibre/Bullet outside the APK's confirmed scope. Treat Java 25 as an evidenced configuration, not proof that all APIs or optional modules work on Android.

Portability boundaries:
- javity-core uses libGDX graphics APIs directly, so it is a portable libGDX runtime layer rather than pure Java/headless/web code.
- javity-maplibre imports LWJGL3/GLFW/EGL and explicitly limits native runtime support to Linux x64 (javity-maplibre/build.gradle:19–35; docs/maplibre-native.md:93). Keep it an optional desktop adapter.
- javity-physics3d is an optional Bullet module; Android native packaging/runtime is explicitly unverified (docs/physics3d.md:3; docs/android-status.md:29).
- Desktop Yoga is a LWJGL native provider; Android Yoga and QuickJS are dedicated modules. settings.gradle keeps Android SDK setup out of default desktop configuration.

For consolidation, preserve a portable gameplay/CPU layer and explicit platform adapters. Avoid moving MapLibre, Graal, LWJGL, or unverified Bullet natives into common artifacts merely to reduce module count. Recheck Android JDK API availability if a module gains new Java 25 APIs; language/bytecode target alone does not establish minSdk compatibility.

## Test coverage priorities

There are broad current regression suites: source-file counts include 48 tests under javity-core, 16 under javity-ui, 6 under javity-voxel, 5 under javity-android-app, and focused tests in physics, assets, maps, script, and backend modules. docs/android-status.md records a historical 646-test/75-suite run. The latest runtime batch records 805 tests/91 suites in docs/runtime-batch-verification.md; this audit did not rerun them.

Recommended order for consolidation gates:
1. Keep core behavior tests unchanged while moving packages/ownership: Game/Object/component ordering, lifetimes, cleanup on faults, resource ownership, and serialization. Check legacy javity-engine public API compatibility too.
2. Keep native CPU tests for Box2D and Bullet using matching desktop classifiers; these must remain independent of a GL context.
3. Preserve actual GPU/pixel and resource-cleanup smokes for shader dialects, GL20/GLES3, context loss, instancing, and renderer lifecycle. Compile and unit tests do not establish rendering correctness.
4. Run Android configuration/build, APK ABI audit, ART/JNI checks, and lifecycle/context recovery separately. Report the existing combined-runner ANR and physical-device limitations separately.
5. Check desktop and Android resolved classpaths and installDist contents after changes, especially the copied MapLibre adapter/license and selected natives.

This follows AGENTS.md:7–12 and existing work-queue/status evidence. A larger module or test count is not itself a behavior guarantee.

## Potentially obsolete items

- gdx-ai:1.8.2 is the only plausible unused main-source dependency found in the legacy backend scan: no com.badlogic.gdx.ai/GdxAI/steering/behavior-tree/pathfinding imports or symbols appeared in javity-backend, javity-engine, or javity-desktop Java source. Because the dependency is declared as api, downstream consumers may rely on its transitive export. Confirm API/consumer expectations before removal.
- Artemis is used broadly in backend/engine; Spine 3.5.51.1 is used in legacy Spine loading/rendering; Guava EventBus is used in routing; Zip4j is used by the unzip helper; Kryo is used by legacy scene serialization. These are not safe removals without replacing those paths.
- Historical javity-android, javity-xml-ui, and javity-project-generator directories are outside the main Gradle reactor. AGENTS.md and docs/TODO.md explicitly keep them around for migration/legacy work; do not delete them because settings.gradle omits them.
- Ignored .gradle/, **/build/, and **/target/ outputs are generated caches/build products and can be recreated. The observed javity-backend/target/maven-status directory is ignored. No cleanup was performed. Keep the Gradle wrapper JAR, docs/reference fixtures, and screenshots.

## Migration risks and verification

Main risks are flattening platform-specific native payloads; forcing independently versioned extensions; changing legacy public APIs during module moves; and losing native/render behavior while a Java build still passes.

Before accepting consolidation:
- Use dependency insight/reports for desktop runtimeClasspath and Android runtime configurations. Assert one 1.14.2 value across used core/Box2D/FreeType/Bullet/backend/ANGLE coordinates, 2.5.2 for jnigen-loader, expected LWJGL 3.4.3, and correct native classifiers.
- Build desktop and the -PjavityAndroid=true graph separately; default desktop should not require Android SDK setup.
- Run CPU native physics tests, rendering pixel/resource smokes, and Android emulator checks separately, reporting compile, unit/native tests, GL runtime and emulator status as separate evidence.
- Verify public compile/runtime API, installDist contents, native libraries, and MapLibre adapter NOTICE/license.
