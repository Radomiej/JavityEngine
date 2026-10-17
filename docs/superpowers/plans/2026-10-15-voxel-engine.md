# Voxel Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reusable bounded chunked voxel data, visible-face rendering and a playable textured native demo.

**Architecture:** Optional javity-voxel module; CPU-only world/mesher/raycast and a GL-thread adapter reusing existing ModelRenderer. Data agent and original atlas author work independently; GPU and demo consume the verified data contract. No mandatory voxel dependency in javity-core.

**Tech Stack:** JDK25, Gradle9.8.1, libGDX1.14.2, JUnit4, desktop LWJGL3.4.3.

**Spec:** docs/superpowers/specs/2026-10-15-voxel-engine-design.md (user accepted 2026-10-15).

## Global Constraints

- Fixed 16³ chunks, air0, opaque full cubes, immutable registry after world creation.
- World block range [-1048576,1048575]; chunk range [-65536,65535].
- Explicit loaded chunk cap, no implicit load on edit, absent reads air, unchanged writes no dirty work.
- Only face-neighbor invalidation; deduplicated bounded synchronous rebuild queue.
- CPU code needs no GL; atlas borrowed, GPU models owned, replacement outside active ModelBatch.
- No per-block GameObjects; fixed first-version scope, no streaming/physics/multiplayer claims.
- No commit/push/clean. Preserve all existing uncommitted modernization; snapshot before edits, retain ledger/reviews.
- Work in current feature checkout; user approved execution by supervised Luna agents and said to proceed. Do not add permission loops for reversible implementation choices.

## Review Focus

1. Load/unload on a seam must reveal/hide neighbor faces even without a new block edit (Task1 tests).
2. Failed GPU candidate construction must leave old renderable valid and retryable; borrowed atlas survives all mesh disposal (Task2 native probe).
3. Dirty retries must not starve other chunks, leak queue entries or rebuild stationary worlds (Tasks1/2 budget tests).
4. DDA starting inside a block and axis ties must not invent a placement face (Task1 tests, Task3 input).
5. Installed launcher must load real atlas resources outside checkout and cleanup after resize/replacement (Task3 native installed proof).

## Locked interfaces

All public classes package org.javity.voxel unless explicitly gpu subpackage. Add helpers only as needed, notify controller if a signature must change.

- `record ChunkPos(int x,int y,int z)` validated chunk range; `originX/Y/Z()` int; `static fromBlock(int x,int y,int z)`.
- `record BlockPos(int x,int y,int z)` validated block range.
- `enum BlockFace { NEG_X,POS_X,NEG_Y,POS_Y,NEG_Z,POS_Z }` with `dx(),dy(),dz()`.
- `BlockDefinition(String name,int tile)` plus `BlockDefinition(String name,int negX,int posX,int negY,int posY,int negZ,int posZ)`; `name()`, `tile(BlockFace)`.
- `BlockRegistry.register(BlockDefinition)->int`, `get(int)->BlockDefinition`, `size()`, `isFrozen()`; world freezes registry, get(0) handled explicitly by air contract, invalid registered IDs rejected.
- `VoxelWorld(BlockRegistry,int maxLoadedChunks)`; `void loadChunk(ChunkPos)`, `boolean unloadChunk(ChunkPos)`, `boolean isLoaded(ChunkPos)`, `List<ChunkPos> loadedChunks()`, `int getBlock(int,int,int)`, `boolean setBlock(int,int,int,int)`, `int loadedChunkCount()`, `int dirtyChunkCount()`, `ChunkSnapshot snapshot(ChunkPos)`, `List<ChunkPos> dirtyChunks()`, `void markDirty(ChunkPos)`, `void clearDirty(ChunkPos)`. dirtyChunks returns FIFO snapshot, markDirty only loaded chunks. Registry accessor `registry()` for mesher. No mutable backing arrays exposed.
- `ChunkSnapshot.position()`, `block(int localX,int localY,int localZ)` supports [-1,16] neighbor halo, snapshot immutable and standalone.
- `AtlasLayout(int textureWidth,int textureHeight,int tileSize,int gutter,int columns,int rows)`; tile cell stride tileSize+2*gutter, texture must fit all cells; `float u0/v0/u1/v1(int tile)`, `int tileCount()`. Origin convention explicitly image-top; UV maps top/bottom consistently, nearest no mipmap. Validate inset half-texel interior and metadata.
- `VoxelMesher.build(ChunkSnapshot,BlockRegistry,AtlasLayout)->VoxelMeshData`; mesh exposes defensive-copy `vertices()` packed xyz/normalxyz/uv, `indices()` short unsigned, `faceCount()`, `vertexCount()`, `indexCount()`, `position()`; empty arrays for air. At most49152 vertices with fixed opaque16³ chunk.
- `record VoxelHit(BlockPos block,BlockFace enteredFace,BlockPos placementCell,double distance)`; face/placement null when start inside solid.
- `VoxelRaycaster.cast(VoxelWorld,Vector3 origin,Vector3 direction,double maxDistance,int maxSteps)->Optional<VoxelHit>` plus double-primitives overload if needed; normalize without mutating caller vectors.
- `org.javity.voxel.gpu.VoxelChunkRenderer(Game,VoxelWorld,Texture borrowedAtlas,AtlasLayout)` AutoCloseable; `void update(PerspectiveCamera,int maxRebuilds)`; `setCullingEnabled(boolean)`, `int renderedChunkCount()`, `int visibleChunkCount()`, `int faceCount()`, `int totalRebuilds()`, `int lastRebuilds()`, `int rebuildCount(ChunkPos)` (current loaded lifetime; reset on unload). Methods after close fail except close idempotent and counters may remain available for proof. Default culling enabled. Constructor does not consume dirty work; update builds at most budget chunks (zero allowed) and updates visibility/removes unloaded renderables.

### Task 1: CPU data, mesher and picking

**Files:** new javity-voxel/build.gradle; src/main/java/org/javity/voxel/{ChunkPos,BlockPos,BlockFace,BlockDefinition,BlockRegistry,VoxelWorld,ChunkSnapshot,AtlasLayout,VoxelMeshData,VoxelMesher,VoxelHit,VoxelRaycaster}.java; src/test/java/org/javity/voxel/*Test.java. Controller edits settings.gradle only.

**Consumes:** libGDX Vector3 math; no initialized Gdx/GL.
**Produces:** locked interfaces above, executable CPU tests.

- [x] Write meaningful tests before implementation; record RED with absent behavior, then GREEN.
- [x] Test floorDiv/local conversion at -17,-16,-1,0,15,16,17; bounds, frozen registry, explicit load cap, unloaded mutation and unchanged write.
- [x] Implement bounded short[4096] chunks, immutable snapshots including six neighbor faces, dirty FIFO. Test owner+3 face neighbors at corner, no diagonals, load/unload dirty invalidation, dedup and cancellation of unloaded dirty entries.
- [x] Test and implement mesher:0/6/10 faces, full1536, two contiguous full2560, actual outward normals and triangle winding, six UV mappings, checkerboard12288faces49152vertices73728indices and unsigned index validity.
- [x] Test and implement DDA hits/misses, limits, normalization, invalid inputs, inside-solid null face, negative axes, exact boundary and deterministic tie X/Y/Z.
- [x] Format only owned Java files. Run `:javity-voxel:test` on JDK25, record logs/report. No commits.

### Task 2: GPU chunk adapter

**Files:** new javity-voxel/src/main/java/org/javity/voxel/gpu/VoxelChunkRenderer.java and focused upload helper if needed; test probes belong in desktop demo smoke rather than production fault injection API.

**Consumes:** exact Task1 API, Game/ModelRenderer/Transform3D, borrowed Texture.
**Produces:** locked VoxelChunkRenderer contract, owned Models, testable counters and lifecycle.

- [x] Read Task1 code/test results and existing core lifecycle before implementation.
- [x] Define real GL regression probe requirements before GPU implementation; controller/demo agent runs red and green native probe. Write CPU validations where useful.
- [x] Upload Mesh directly or checked ModelBuilder path with vertices beyond32767 and index count73728; no unchecked index narrowing. Model owns mesh only, material borrows atlas.
- [x] Deduplicated FIFO update budget; remove unloaded renderables first; successful commit clears dirty; failure keeps previous model valid and failed chunk retryable without starving others.
- [x] Candidate construction/attachment cleanup and safe component swap outside render; retained renderer initialized correctly by Game lifecycle before drawing. Disabled/culling state limited to renderer component; no unrelated GameObject deactivation.
- [x] Conservative full-chunk world AABB frustum culling after camera update; only translation exposed for generated chunks. Idempotent close and cleanup-all with suppressed failures.
- [x] Native probe in Task3 proves pixels, replacement, GL buffer release, borrowed atlas survival, unload and stationary zero rebuilds. Record implementation report, no builds concurrently with other agents.

### Task 3: Textured playable demo and smoke

**Files:** new javity-desktop/src/main/java/org/javity/demo/{VoxelDemo,VoxelSmokeProbe,VoxelTerrain}.java as necessary; resources/org/javity/demo/voxels/atlas.png and metadata; tools/generate-voxel-atlas.py; docs/voxel-atlas.png. Controller owns DesktopLauncher/build.gradle/CI.

**Consumes:** Tasks1/2 API and generated atlas. Independent atlas author may prepare assets in parallel with Task1.
**Produces:** `VoxelDemo(boolean smoke)`, --voxels behavior and deterministic native proof.

- [x] Generate original8-tile atlas: grass top/side,dirt,stone,wood side/top,sand,opaque leaves; tile16,gutter2,columns4,rows2 =>80×40 PNG. Replicated gutters, nearest,no mipmap. Python stdlib deterministic generator, check actual PNG bytes reproducible, create enlarged preview with Pillow only if available (generator stays stdlib).
- [x] CPU-test deterministic finite seeded generation (same seed same summary, changed seed differing summary). Load a3×3×2 patch of16³ chunks including negative X/Z; bounded54chunks. Populate once, then rebuild only dirty.
- [x] Implement freefly WASD/Space/Ctrl/Shift/mouse, Esc cursor toggle, focus-loss reset, selectable block and outline, reach8, remove/place via same shared gameplay methods exercised by smoke. No collision claim.
- [x] HUD with OpenSans Polish glyphs, chunk/visible/dirty/rebuild/face counters; sky/lighting and original atlas visibly readable.
- [x] Write native smoke probe before adapter integration to record expected red failure. Render controlled fixtures to framebuffer and assert at least distinct atlas regions, depth/occlusion, normals/winding, and selection. Use real GL tracking test utility for released buffers/atlas without adding fault hooks to production.
- [x] Fixed frame stepping, scripted camera movement/look and cross-chunk remove/place; confirm neighbor rebuilds, budget, stationary no rebuilds, resize, GL_NO_ERROR, owned resource close and borrowed atlas lifetime.
- [x] Write artifacts/voxels/{gallery.png,proof.json} only after assertions; fail process visibly on error. Cleanup after partial initialization and after idempotent close.
- [x] Run actual graphical smoke and inspect screenshot; then installed distribution from temp cwd outside checkout.

### Task 4: Integration, documentation and review

**Files:** controller-owned settings.gradle,javity-desktop/build.gradle,DesktopLauncher.java,.github/workflows/*,README.md,AGENTS.md,docs/{README,TODO,modernization-status,voxels}.md,THIRD_PARTY.md.

- [x] Snapshot preexisting files, include optional module and desktop dependency; add --voxels without altering existing modes; add voxelSmoke task and CI artifacts.
- [x] Independent review Task1 before Task2 depends on it; review combined GPU/demo before final broad scoped feature review. Exact before/after artifacts, never compare new feature against old Git HEAD baseline.
- [x] Full `clean build :javity-desktop:installDist :javity-desktop:distZip`, actual voxelSmoke plus impacted modern/materials/shapes/map modes. Installed voxel proof outside checkout and ZIP resource/license check.
- [x] Review actual screenshots and publish demo/atlas previews in docs. Document usage/API/ownership/controls, CPU and GL evidence separately, limits and deferred work. Mark only verified TODO items complete.
- [x] Report feature boundaries and outstanding MapLibre/dashed work without claiming their implementation. No commit/push; retain recovery artifacts.

## Plan self-review

All spec sections map to Tasks1–4. Shared API above resolves data/GPU/demo dependencies; GPU starts after data review, demo assets are independent. Task4 owns all shared build/launcher/docs files. Five review-focus cases have explicit Task tests/probes. Test count never substitutes for actual GL pixels or cleanup. User has selected supervised agents and explicitly instructed execution; controller proceeds under that authorization.
