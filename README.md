# JavityEngine

JavityEngine is a Java game engine in active modernization, built on libGDX. The new core uses a small `Game → GameObject → Component` model for 2D and 3D gameplay, with optional modules for physics, UI, maps, scripting, assets, and other features. The repository also retains the historical Javity/Galaxy Rapid API to support gradual migration.

[Showcase](https://radomiej.github.io/JavityEngine/) · [Showcase source](site/) · [Documentation index](docs/README.md) · [Changelog](CHANGELOG.md) · [License](LICENSE) · [Third-party credits](THIRD_PARTY.md)

## What changed

The modernization adds a Java 25/Gradle build, a common 2D/3D component core, optional Artemis ECS and local Java-authoritative Colyseus development. Recent work adds optional glTF/GLB import, PBR, directional shadows, RGBA16F HDR, translation root motion, and more precise UI typography. Shared GPU recovery and ownership paths have dedicated regression tests. See [CHANGELOG.md](CHANGELOG.md), the [bug checklist](docs/Bugs/README.md) and the [current backlog](docs/TODO.md).

## Quick start

Requirements: a full **JDK 25** installation, with `JAVA_HOME` pointing to it. The Gradle wrapper downloads **Gradle 9.8.1**. The project uses **libGDX 1.14.2**. A desktop demo also needs a working OpenGL display; Linux headless environments can use Xvfb with Mesa.

```sh
./gradlew build
./gradlew :javity-desktop:run
```

The default desktop demo shows the new core, including a 2D object, a 3D model, Box2D, and Yoga-based UI. Other demo modes include:

```sh
./gradlew :javity-desktop:run --args='--map'       # Offline raster map
./gradlew :javity-desktop:run --args='--runtime'   # Runtime services and serialization
./gradlew :javity-desktop:run --args='--plugins'   # UI, animation, and scripting integrations
./gradlew :javity-desktop:qualityDemo             # PBR Fox Garden, shadows/HDR and runtime tools
./gradlew :javity-desktop:run --args='--voxels'    # Voxel world
./gradlew :javity-desktop:run --args='--mm'         # Relay Ward systems demo
./gradlew :javity-desktop:run --args='--legacy'     # Historical API demo
```

Each desktop mode runs in a real graphics context. For map, runtime, plugins, and other focused workflows, see the [demo and command index](docs/game-authoring.md) and [documentation index](docs/README.md).

## A small component-based game

Create a `Game`, attach components to named objects, then start and step the game. A component can read its owner and transform from its lifecycle hooks:

```java
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;

public final class Spin extends Component {
    @Override
    public void onUpdate(float delta) {
        getTransform().rotation += 1.5f * delta;
    }
}

Game game = new Game();
GameObject player = game.createObject("player").setPosition(120, 80);
player.addComponent(new Spin());
game.start();
game.step(1f / 60f);
game.close(); // The host closes its scene when it is no longer needed.
```

In an application, the render loop calls `game.step(delta)` each frame. Components are ordinary Java classes; `onCreate`, `onUpdate`, `onLateUpdate`, and `onDestroy` are the main lifecycle hooks. Multiple components of the same type are allowed. `getComponent(Type)` returns the first match or `null`; `getComponents(Type)` returns all matches, including instances of subclasses. See [creating a game](docs/game-authoring.md) and the detailed [engine guide](docs/engine-guide.md).

## Modules

The core and optional features are separate Gradle modules. Add only the modules your application needs.

| Module | Purpose |
| --- | --- |
| `javity-core` | New GameObject/component API and 2D/3D rendering integration |
| `javity-physics2d` | Optional Box2D physics for the new core |
| `javity-physics3d`, `javity-voxel-physics3d` | Optional Bullet physics and voxel collision adapter |
| `javity-ui`, `javity-ui-yoga-lwjgl` | Scene2D UI with Yoga layout; desktop Yoga provider |
| `javity-script`, `javity-script-graal` | Portable React/TSX host and desktop GraalJS provider |
| `javity-map-core`, `javity-map` | Raster map API and historical geographic map plugin |
| `javity-maplibre` | Optional MapLibre Native vector maps; Linux x64 EGL/GLES |
| `javity-assets` | Optional local asset streaming and bounded cache |
| `javity-gltf` | Optional bounded glTF/GLB importer, PBR, desktop directional shadows and HDR |
| `javity-ecs-artemis` | Optional Artemis-ODB adapter; core does not depend on Artemis |
| `javity-rooms` | Local Colyseus gateway and Java-authoritative room example |
| `javity-engine`, `javity-backend`, `javity-net` | Historical API/backend modules retained for migration |
| `javity-desktop` | New-core and legacy demos |

Android modules are opt-in and require the Android SDK:

```sh
./gradlew -PjavityAndroid=true :javity-android-app:assembleDebug
```

See the [Android build instructions](javity-android-app/README.md) and [current Android status](docs/android-status.md). The optional local Colyseus development workspace runs with `./gradlew dev`; it requires Node.js 22 or newer and npm. The documented process proof used Node.js 24. See [local multiplayer setup and limits](docs/local-multiplayer.md).

## Platform readiness

| Target | Current evidence and limits |
| --- | --- |
| Desktop JVM | Main development target. Build and desktop graphics demos use JDK 25; platform-specific optional modules have their own requirements. |
| Android | Opt-in app builds and emulator API 35 x86_64 checks cover UI, scripting, input, rendering, context recovery, and selected lifecycle cases. The complete unfiltered five-test suite passes on the API35 x86_64 KVM emulator, covering input, resource/context recovery, rotation and Activity recreation. Physical ARM64 performance and Android MapLibre/Bullet are unverified. See [Android status](docs/android-status.md). |
| Browser / WebAssembly | An isolated TeaVM/libGDX rendering spike has a real Wasm GC/WebGL2 scene and Chromium/SwiftShader proof. This is not a full engine port: production UI/Yoga, browser physics, networking integration, and broad browser support remain unverified. See the [Wasm rendering spike](docs/javity-wasm-rendering-spike.md). |

Treat a module's build, tests, and runtime proof as separate evidence. A successful compile alone does not establish support for every platform or optional feature.

## UI and integrations

The declarative UI API has a documented, deliberately limited set of utility classes. Its layout uses Yoga, with desktop and Android providers selected by the application. The class names are inspired by Tailwind; this is not full CSS or Tailwind support. Rounded corners affect drawing, not child clipping or hit testing. See [declarative UI](docs/declarative-ui.md) and [UI comparison limits](docs/ui-html-parity.md).

Selected animation, feedback, and React/TSX APIs adapt ideas from Unity ecosystem tools and OneJS to Javity's Java runtime. They are independent implementations; this repository does not include Unity, proprietary plugin code, or full Unity API parity. See [inspiration and scope](docs/unity-inspired-api.md), [animation](docs/animation-api.md), [feedback](docs/feedback-api.md), and [React/TSX integration](docs/onejs-port.md).

## Migration and project history

This modernization raises the build baseline to Java 25 and Gradle 9.8.1 and introduces the new `org.javity.core` API. Existing games built against the historical `javity-engine` or Galaxy Rapid APIs may need source and build changes to use the new modules. The legacy API and its desktop demo remain available to support migration; Artemis is also available through the separate optional adapter. See the [legacy API inventory](docs/migration-api.md) and [modernization status](docs/modernization-status.md).

Galaxy Rapid sources are built from this repository rather than a historical published snapshot. External code and assets retain their upstream licenses and notices; see [THIRD_PARTY.md](THIRD_PARTY.md). For project practices and implementation notes, see [AGENTS.md](AGENTS.md).

## ORBIT launcher

Build a versioned `.nekko` archive containing WebAssembly, its loader, assets and the ORBIT API bridge. See [packaging, publishing and cloud saves](docs/orbit-wasm.md). The launcher API must support the explicit TeaVM JSO script policy; the ordinary standalone Wasm target remains available.

## Game authoring APIs

Use [typed platform services](docs/platform-services.md) for ORBIT/local profiles and versioned saves, [GameObject UI panels](docs/ui-game-object.md) for scene-owned UI, [input contexts](docs/input-actions.md) for gameplay/menu controls, and [prefab catalogs](docs/prefab-pipeline.md) for versioned templates. The [replication core](docs/game-replication.md) provides authority, snapshots and interpolation; live ticket-backed Colyseus transport remains a separate integration. [Scope and verification](docs/Spec/2026-10-20-authoring-api-batch.md).

## Modern 3D workflow

[Fox Garden](docs/quality-showcase.md) combines imported animations, authored greenery, scene
lights, PBR/shadows/HDR, input and the existing GameObject tools. Start with
`./gradlew :javity-desktop:qualityDemo`; use F1 for the runtime hierarchy.
[glTF assets and supported profile](docs/gltf-assets.md), [shadows](docs/shadows.md),
[HDR](docs/hdr-rendering.md) and [root motion](docs/root-motion.md) describe the API
and ownership rules. These optional features have scoped desktop tests, not
blanket Android/WebAssembly or complete Unity compatibility certification.
