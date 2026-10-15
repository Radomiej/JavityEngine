package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.core.ComponentChange;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.voxel.AtlasLayout;
import org.javity.voxel.BlockDefinition;
import org.javity.voxel.BlockRegistry;
import org.javity.voxel.ChunkPos;
import org.javity.voxel.VoxelWorld;
import org.javity.voxel.gpu.VoxelChunkRenderer;

/** Real framebuffer, upload, culling, replacement and GL-lifetime assertions for voxel chunks. */
public final class VoxelGpuSmokeProbe {
  private static final String ATLAS_PATH = "org/javity/demo/voxels/atlas.png";
  private static final AtlasLayout ATLAS_LAYOUT = new AtlasLayout(80, 40, 16, 2, 4, 2);
  private static final ChunkPos ZERO = new ChunkPos(0, 0, 0);
  private static final ChunkPos ONE_X = new ChunkPos(1, 0, 0);
  private static final ChunkPos REMOTE = new ChunkPos(6, 0, 0);
  private static final ChunkPos LARGE = new ChunkPos(2, 0, 0);
  private static final ChunkPos EMPTY_LIFETIME = new ChunkPos(8, 2, 8);

  private VoxelGpuSmokeProbe() {}

  public static void run() {
    GL20 original = Gdx.gl;
    GL20 original20 = Gdx.gl20;
    List<String> driverErrors = new ArrayList<>();
    GlTracker tracker = new GlTracker(original20, driverErrors);
    Gdx.gl = tracker.proxy();
    Gdx.gl20 = tracker.proxy();

    Texture atlas = null;
    Pixmap atlasPixels = null;
    Game game = null;
    GdxRenderer renderer = null;
    VoxelChunkRenderer chunks = null;
    Set<Integer> preAdapterBuffers = Set.of();
    Set<Integer> oldChunkBuffers = Set.of();
    int atlasHandle = 0;
    try {
      atlas = new Texture(Gdx.files.internal(ATLAS_PATH), false);
      atlas.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
      atlasHandle = atlas.getTextureObjectHandle();
      atlasPixels = new Pixmap(Gdx.files.internal(ATLAS_PATH));

      BlockRegistry blocks = new BlockRegistry();
      int grass = blocks.register(new BlockDefinition("grass", 1, 1, 2, 0, 1, 1));
      int wood = blocks.register(new BlockDefinition("wood", 4, 4, 5, 5, 4, 4));
      int stone = blocks.register(new BlockDefinition("stone", 3));
      int sand = blocks.register(new BlockDefinition("sand", 6));
      int invalidTile = blocks.register(new BlockDefinition("invalid_tile", 8));
      VoxelWorld world = new VoxelWorld(blocks, 8);
      world.loadChunk(ZERO);
      world.loadChunk(ONE_X);
      world.loadChunk(REMOTE);

      // Four controlled framebuffer targets: two different atlas rows, a depth overlap,
      // and two blocks sharing a face across the chunk boundary.
      world.setBlock(1, 1, 1, grass);
      world.setBlock(4, 1, 1, wood);
      world.setBlock(7, 1, 0, stone);
      world.setBlock(7, 1, 1, grass);
      world.setBlock(15, 1, 4, grass);
      world.setBlock(16, 1, 4, wood);
      world.setBlock(96, 1, 1, sand);
      require(world.dirtyChunkCount() == 3, "Initial dirty requests were not deduplicated by chunk");

      game = new Game();
      GameObject cameraObject = game.createObject("voxel smoke camera");
      Transform3D cameraTransform = cameraObject.addComponent(new Transform3D());
      Camera3D cameraComponent = cameraObject.addComponent(new Camera3D(67));
      PerspectiveCamera camera = cameraComponent.camera;
      camera.near = .1f;
      camera.far = 256;
      game.start();
      renderer = new GdxRenderer();
      renderer.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      // Keep atlas assertions tied to source RGB rather than directional-light shading.
      renderer.environment.clear();
      renderer.environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1, 1, 1, 1));
      renderBlankFrame(game, renderer);
      preAdapterBuffers = tracker.generatedBuffers();

      chunks = new VoxelChunkRenderer(game, world, atlas, ATLAS_LAYOUT);
      int initialTotal = chunks.totalRebuilds();
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(chunks.lastRebuilds() <= 1, "A frame exceeded the one-chunk rebuild budget");
      require(world.dirtyChunkCount() == 2, "A budget of one did not leave two dirty chunks queued");
      drainDirty(game, renderer, chunks, camera, cameraTransform, world);
      require(chunks.totalRebuilds() - initialTotal == 3, "Initial dirty chunks were not rebuilt once each");
      require(chunks.faceCount() == 38, "Expected 38 exposed faces across fixtures, got " + chunks.faceCount());
      require(chunks.renderedChunkCount() == 3, "Each nonempty loaded chunk should own one rendered model");
      require(chunks.visibleChunkCount() < chunks.renderedChunkCount(), "A remote chunk was not frustum-culled");

      // A new adapter must reconstruct models from an unchanged, already-clean CPU world.
      Set<Integer> firstAdapterBuffers = tracker.liveAdapterBuffers(preAdapterBuffers);
      chunks.close();
      game.step(0);
      for (int handle : firstAdapterBuffers)
        require(!original20.glIsBuffer(handle), "Closing the first adapter retained buffer " + handle);
      chunks = new VoxelChunkRenderer(game, world, atlas, ATLAS_LAYOUT);
      require(world.dirtyChunkCount() == 3, "A replacement adapter did not queue every loaded chunk");
      int firstReplacementFrame = frame(
          game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(firstReplacementFrame == 1, "Replacement adapter ignored its one-chunk build budget");
      require(chunks.renderedChunkCount() == 1, "Replacement adapter did not begin rebuilding models");
      drainDirty(game, renderer, chunks, camera, cameraTransform, world);
      require(chunks.renderedChunkCount() == 3, "Replacement adapter failed to restore the clean world's models");

      // Validate actual texel sampling against the PNG, including its image-top/low-v row
      // convention. Grass's green cap lives near the top of tile 1; wood's side and cap
      // deliberately use different atlas rows and tiles.
      Vector3 grassCapPoint = new Vector3(1f + 8f / 15f, 1f + 12f / 15f, 2f);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 0);
      assertFramebufferMatchesTile(camera, grassCapPoint, atlasPixels, 1, 8, 3, "grass side cap");
      requireGreen(framebufferColor(camera, grassCapPoint), "Grass side did not sample its cap row");

      Vector3 woodSidePoint = new Vector3(4f + 8f / 15f, 1f + 7f / 15f, 2f);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(4.5f, 1.5f, 1.5f), 0);
      Color woodSide = framebufferColor(camera, woodSidePoint);
      assertFramebufferMatchesTile(camera, woodSidePoint, atlasPixels, 4, 8, 8, "wood side");
      requireDistinct(woodSide, framebufferColorForTile(atlasPixels, 1, 8, 3), "wood side atlas row");

      Vector3 overlapPoint = new Vector3(7f + 8f / 15f, 1f + 7f / 15f, 2f);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(7.5f, 1.5f, 1.5f), 0);
      Color overlap = framebufferColor(camera, overlapPoint);
      assertFramebufferMatchesTile(camera, overlapPoint, atlasPixels, 1, 8, 8, "front block in depth overlap");
      requireCloserToTile(overlap, atlasPixels, 1, 3, "front voxel must occlude the stone behind it");

      Vector3 woodTopPoint = new Vector3(4f + 8f / 15f, 2f, 1f + 7f / 15f);
      frameTopDown(game, renderer, chunks, camera, cameraTransform, new Vector3(4.5f, 1.5f, 1.5f), 0);
      Color woodTop = framebufferColor(camera, woodTopPoint);
      assertFramebufferMatchesTile(camera, woodTopPoint, atlasPixels, 5, 8, 8, "wood cap / lower atlas row");
      requireDistinct(woodTop, woodSide, "wood top and side tiles");

      int stableTotal = chunks.totalRebuilds();
      for (int i = 0; i < 3; i++) {
        frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
        require(chunks.lastRebuilds() == 0, "Stationary clean world triggered a rebuild");
      }
      require(chunks.totalRebuilds() == stableTotal, "Stationary frame changed rebuild totals");

      require(!world.setBlock(1, 1, 1, grass), "Writing the same value should report no change");
      require(world.dirtyChunkCount() == 0, "An unchanged write dirtied a chunk");
      require(world.setBlock(2, 1, 1, wood), "Expected a block edit");
      require(world.setBlock(2, 1, 1, stone), "Expected a replacement edit");
      require(world.dirtyChunkCount() == 1, "Multiple edits failed to deduplicate one chunk request");
      int zeroRebuilds = chunks.rebuildCount(ZERO);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(chunks.lastRebuilds() == 1, "Dirty chunk was not rebuilt within a budget of one");
      require(chunks.rebuildCount(ZERO) == zeroRebuilds + 1, "A deduplicated chunk was rebuilt more than once");

      // Editing one block at the seam changes visibility in both loaded chunks.
      int leftRebuilds = chunks.rebuildCount(ZERO);
      int rightRebuilds = chunks.rebuildCount(ONE_X);
      require(world.setBlock(15, 1, 4, 0), "Expected removal at chunk boundary");
      require(world.dirtyChunkCount() == 2, "A cross-chunk edit must dirty the owner and loaded face neighbor");
      drainDirty(game, renderer, chunks, camera, cameraTransform, world);
      require(chunks.rebuildCount(ZERO) == leftRebuilds + 1, "Boundary owner did not rebuild exactly once");
      require(chunks.rebuildCount(ONE_X) == rightRebuilds + 1, "Boundary neighbor did not rebuild exactly once");
      require(chunks.faceCount() == 38,
          "Removing one seam block should expose a single six-face block while retaining the edited adjacent pair");

      // Replacing the same loaded coordinate without an intermediate update must retire the
      // previous model even when the rebuild budget is zero.
      int renderedBeforeUnload = chunks.renderedChunkCount();
      GameObject oldRemoteObject = findChunkObject(game, REMOTE);
      require(oldRemoteObject != null, "Could not locate the remote chunk object before replacement");
      Vector3 remoteSandPoint = new Vector3(96f + 8f / 15f, 1f + 7f / 15f, 2f);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(96.5f, 1.5f, 1.5f), 0);
      assertFramebufferMatchesTile(
          camera, remoteSandPoint, atlasPixels, 6, 8, 8, "remote sand before same-coordinate replacement");
      Set<Integer> buffersBeforeRemoteReplacement = tracker.liveAdapterBuffers(preAdapterBuffers);
      require(world.unloadChunk(REMOTE), "Remote chunk did not unload");
      world.loadChunk(REMOTE);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 0);
      require(chunks.renderedChunkCount() == renderedBeforeUnload - 1,
          "Reloading a chunk coordinate retained its previous ModelRenderer");
      require(oldRemoteObject.isDestroyed(), "Same-coordinate reload did not destroy the old chunk object");
      require(chunks.rebuildCount(REMOTE) == 0, "Same-coordinate reload retained its previous rebuild count");
      Set<Integer> buffersAfterRemoteReplacement = tracker.liveAdapterBuffers(preAdapterBuffers);
      Set<Integer> retiredRemoteBuffers = new HashSet<>(buffersBeforeRemoteReplacement);
      retiredRemoteBuffers.removeAll(buffersAfterRemoteReplacement);
      require(retiredRemoteBuffers.size() == 2,
          "Same-coordinate reload did not retire the old chunk vertex/index buffers");
      for (int handle : retiredRemoteBuffers)
        require(!original20.glIsBuffer(handle), "Same-coordinate reload retained old buffer " + handle);
      world.setBlock(96, 1, 1, sand);
      drainDirty(game, renderer, chunks, camera, cameraTransform, world);
      require(chunks.rebuildCount(REMOTE) == 1, "Reloaded chunk did not start a fresh per-position rebuild count");

      // Empty chunks have lifetime diagnostics even without a rendered model, and those
      // diagnostics must reset when the same coordinate is loaded again.
      world.loadChunk(EMPTY_LIFETIME);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(chunks.rebuildCount(EMPTY_LIFETIME) == 1, "Empty loaded chunk was not counted as rebuilt");
      require(world.unloadChunk(EMPTY_LIFETIME), "Empty lifetime fixture did not unload");
      world.loadChunk(EMPTY_LIFETIME);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 0);
      require(chunks.rebuildCount(EMPTY_LIFETIME) == 0,
          "Same-coordinate empty reload retained its prior per-position diagnostic count");
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(chunks.rebuildCount(EMPTY_LIFETIME) == 1,
          "Empty replacement lifetime did not begin its own rebuild count");

      // 16^3 / 2 occupied checkerboard cells emit 12,288 faces, 49,152 vertices and
      // 73,728 indices. This exercises unsigned-short vertex indices with an index
      // array longer than 65,535 entries on the real driver.
      world.loadChunk(LARGE);
      for (int z = 0; z < 16; z++)
        for (int y = 0; y < 16; y++)
          for (int x = 0; x < 16; x++)
            if (((x + y + z) & 1) == 0) world.setBlock(32 + x, y, z, grass);
      int facesBeforeLarge = chunks.faceCount();
      drainDirty(game, renderer, chunks, camera, cameraTransform, world);
      require(chunks.faceCount() - facesBeforeLarge == 12_288, "Large checkerboard upload lost faces or overflowed indices");
      require(chunks.rebuildCount(LARGE) == 1, "Large checkerboard chunk did not upload once");
      chunks.setCullingEnabled(false);
      frameFromOutside(
          game,
          renderer,
          chunks,
          camera,
          cameraTransform,
          new Vector3(39.5f, 8.5f, 7.5f),
          new Vector3(39.5f, 8.5f, 38f));
      Color largeMeshPixel = framebufferColor(camera, new Vector3(39.5f, 8.5f, 16f));
      require(largeMeshPixel.r + largeMeshPixel.g + largeMeshPixel.b > .08f,
          "Checkerboard mesh with more than 65,535 indices produced no framebuffer pixels");
      // Mesher order is y,z,x then NEG_X/POS_X/NEG_Y/POS_Y/NEG_Z/POS_Z. The
      // last occupied checkerboard voxel is local (14,15,15), world (46,15,15).
      // Its POS_Y face is face 12,285, referencing index entries 73,710..73,715
      // and vertices 49,140..49,143. This top-face sample reaches the late index
      // buffer tail and the unsigned-short vertex range above 32,767.
      Vector3 lateFacePoint = new Vector3(46f + 8f / 15f, 16f, 15f + 7f / 15f);
      frameTopDown(
          game,
          renderer,
          chunks,
          camera,
          cameraTransform,
          lateFacePoint,
          0);
      assertFramebufferMatchesTile(camera, lateFacePoint, atlasPixels, 0, 8, 8,
          "late checkerboard top face from index entries 73,710+");
      require(original20.glGetError() == GL20.GL_NO_ERROR,
          "Large checkerboard draw left an OpenGL error");
      chunks.setCullingEnabled(true);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 0);

      // Verify transactional replacement and retry after a bad visible atlas tile. The
      // mesher rejects the candidate before GPU allocation, leaving the previous model valid.
      // Capture only live names: OpenGL may recycle a deleted numeric name for a
      // replacement allocation, so historical handles are not stable identities.
      oldChunkBuffers = tracker.liveAdapterBuffers(preAdapterBuffers);
      require(oldChunkBuffers.size() >= 2, "Probe did not observe owned voxel vertex/index buffers");
      int deletedBeforeReplacement = tracker.deletedLiveCount(oldChunkBuffers);
      require(world.setBlock(1, 2, 1, stone), "Expected a replaceable voxel mesh edit");
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(tracker.deletedLiveCount(oldChunkBuffers) >= deletedBeforeReplacement + 2,
          "Successful model replacement did not release the previous GL mesh buffers");

      Set<Integer> stableBuffers = tracker.liveAdapterBuffers(preAdapterBuffers);
      int failedRebuildCount = chunks.rebuildCount(ZERO);
      GameObject zeroObject = null;
      for (GameObject object : game.getObjects())
        if (object.name.equals("voxel chunk 0,0,0")) zeroObject = object;
      require(zeroObject != null, "Could not locate the chunk object for listener rollback coverage");
      ModelRenderer detachedRenderer = zeroObject.getComponent(ModelRenderer.class);
      require(detachedRenderer != null, "Chunk had no current renderer before listener rollback test");
      com.badlogic.gdx.graphics.g3d.Model retainedModel = detachedRenderer.instance.model;
      boolean[] throwOnOldRemoval = {true};
      Runnable removeListener = zeroObject.onComponentChange(change -> {
        if (throwOnOldRemoval[0]
            && change.type() == ComponentChange.Type.REMOVED
            && change.component() == detachedRenderer) {
          throwOnOldRemoval[0] = false;
          throw new IllegalStateException("probe listener rejected old renderer removal");
        }
      });
      require(world.setBlock(3, 1, 1, wood), "Expected listener rollback mesh edit");
      boolean listenerFailed = false;
      try {
        frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      } catch (IllegalStateException expected) {
        listenerFailed = expected.getMessage() != null
            && expected.getMessage().contains("probe listener");
      }
      require(listenerFailed, "Replacement did not expose the one-shot removal listener failure");
      require(chunks.rebuildCount(ZERO) == failedRebuildCount,
          "Listener-failed replacement was counted as a committed rebuild");
      require(world.dirtyChunkCount() > 0, "Listener-failed chunk was not left retryable");
      ModelRenderer restoredRenderer = zeroObject.getComponent(ModelRenderer.class);
      require(restoredRenderer != null && restoredRenderer != detachedRenderer,
          "Rollback did not attach a fresh ModelRenderer for the retained model");
      require(restoredRenderer.instance.model == retainedModel,
          "Rollback failed to preserve the previous Model instance");
      for (int handle : stableBuffers)
        require(original20.glIsBuffer(handle), "Listener failure released the retained old buffer " + handle);
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 0);
      assertFramebufferMatchesTile(camera, new Vector3(1f + 8f / 15f, 2f + 7f / 15f, 2f), atlasPixels, 3, 8, 8,
          "old model after listener rollback");
      removeListener.run();
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(chunks.rebuildCount(ZERO) == failedRebuildCount + 1,
          "Listener-failed replacement was not retryable on the next update");

      failedRebuildCount = chunks.rebuildCount(ZERO);
      stableBuffers = tracker.liveAdapterBuffers(preAdapterBuffers);
      require(world.setBlock(1, 2, 1, invalidTile), "Expected an invalid atlas candidate edit");
      int generationIndex = tracker.generationCount();
      boolean candidateFailed = false;
      try {
        frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      } catch (RuntimeException expected) {
        candidateFailed = expected.getMessage() != null
            && expected.getMessage().toLowerCase().contains("tile");
      }
      require(candidateFailed,
          "Invalid atlas candidate did not report the expected atlas tile validation failure");
      require(chunks.rebuildCount(ZERO) == failedRebuildCount,
          "A failed candidate was counted as a committed rebuild");
      for (int handle : stableBuffers)
        require(original20.glIsBuffer(handle), "Failed replacement disposed a still-renderable old buffer " + handle);
      require(tracker.generationCount() == generationIndex,
          "Invalid tile candidate allocated GPU buffers before validation");

      // Draw the retained model after the failed candidate, then retry on the next update.
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 0);
      assertFramebufferMatchesTile(camera, new Vector3(1f + 8f / 15f, 2f + 7f / 15f, 2f), atlasPixels, 3, 8, 8,
          "old model after failed replacement");
      require(world.setBlock(1, 2, 1, wood), "Expected a valid retry edit");
      drainDirty(game, renderer, chunks, camera, cameraTransform, world);
      require(chunks.rebuildCount(ZERO) == failedRebuildCount + 1, "Failed upload was not retried exactly once");

      Set<Integer> voxelBuffers = tracker.adapterBuffers(preAdapterBuffers);
      chunks.close();
      chunks.close();
      chunks = null;
      game.step(0); // flush destroyed chunk objects before any later draw
      for (int handle : voxelBuffers)
        require(!original20.glIsBuffer(handle), "Voxel adapter leaked GL buffer " + handle);
      require(original20.glIsTexture(atlasHandle), "Voxel adapter disposed its borrowed atlas texture");
      atlas.bind();
      require(original20.glGetError() == GL20.GL_NO_ERROR, "Borrowed atlas could not be rebound after close");

      game.close();
      game = null;
      renderer.dispose();
      renderer = null;
      require(driverErrors.isEmpty(), "Driver errors during voxel probe: " + driverErrors);
      require(original20.glGetError() == GL20.GL_NO_ERROR, "Voxel probe left an OpenGL error");
      Gdx.app.log("Voxels", "GPU PASS: atlas UVs, depth, budget, seams, culling, large indices and resource recovery");
    } finally {
      Throwable failure = null;
      try {
        if (chunks != null) chunks.close();
      } catch (RuntimeException | Error e) {
        failure = e;
      }
      try {
        if (game != null) game.close();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
      try {
        if (renderer != null) renderer.dispose();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
      try {
        if (atlas != null) atlas.dispose();
      } catch (RuntimeException | Error e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
      if (atlasPixels != null) atlasPixels.dispose();
      Gdx.gl = original;
      Gdx.gl20 = original20;
      if (failure instanceof RuntimeException e) throw e;
      if (failure instanceof Error e) throw e;
    }
  }

  /** Starts an isolated desktop GL context so the probe can run before VoxelDemo is integrated. */
  public static void main(String[] args) {
    List<Throwable> failures = new ArrayList<>();
    Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
    configuration.setTitle("Javity voxel GPU smoke probe");
    configuration.setWindowedMode(960, 640);
    configuration.useVsync(false);
    configuration.setForegroundFPS(60);
    new Lwjgl3Application(
        new ApplicationAdapter() {
          @Override
          public void create() {
            try {
              run();
            } catch (Throwable failure) {
              failures.add(failure);
            } finally {
              Gdx.app.exit();
            }
          }
        },
        configuration);
    if (!failures.isEmpty()) throw new IllegalStateException("Voxel GPU smoke failed", failures.get(0));
  }

  private static void drainDirty(
      Game game,
      GdxRenderer renderer,
      VoxelChunkRenderer chunks,
      PerspectiveCamera camera,
      Transform3D cameraTransform,
      VoxelWorld world) {
    int frame = 0;
    while (world.dirtyChunkCount() > 0 && frame++ < 32) {
      frame(game, renderer, chunks, camera, cameraTransform, new Vector3(1.5f, 1.5f, 1.5f), 1);
      require(chunks.lastRebuilds() <= 1, "Dirty drain exceeded its one-chunk budget");
    }
    require(world.dirtyChunkCount() == 0, "Dirty drain did not finish within its bounded frame count");
  }

  private static GameObject findChunkObject(Game game, ChunkPos position) {
    String name = "voxel chunk " + position.x() + "," + position.y() + "," + position.z();
    for (GameObject object : game.getObjects()) if (object.name.equals(name)) return object;
    return null;
  }

  private static int frame(
      Game game,
      GdxRenderer renderer,
      VoxelChunkRenderer chunks,
      PerspectiveCamera camera,
      Transform3D cameraTransform,
      Vector3 target,
      int budget) {
    cameraTransform.position.set(target.x, target.y, target.z + 5f);
    camera.position.set(cameraTransform.position);
    camera.up.set(Vector3.Y);
    camera.lookAt(target);
    camera.update();
    chunks.update(camera, budget);
    int rebuilt = chunks.lastRebuilds();
    game.step(0);
    clearFrame();
    renderer.render(game);
    Gdx.gl.glFinish();
    return rebuilt;
  }

  private static void frameTopDown(
      Game game,
      GdxRenderer renderer,
      VoxelChunkRenderer chunks,
      PerspectiveCamera camera,
      Transform3D cameraTransform,
      Vector3 target,
      int budget) {
    cameraTransform.position.set(target.x, target.y + 5f, target.z);
    camera.position.set(cameraTransform.position);
    camera.up.set(0, 0, -1);
    camera.lookAt(target);
    camera.update();
    chunks.update(camera, budget);
    game.step(0);
    clearFrame();
    renderer.render(game);
    Gdx.gl.glFinish();
  }

  private static void frameFromOutside(
      Game game,
      GdxRenderer renderer,
      VoxelChunkRenderer chunks,
      PerspectiveCamera camera,
      Transform3D cameraTransform,
      Vector3 target,
      Vector3 cameraPosition) {
    cameraTransform.position.set(cameraPosition);
    camera.position.set(cameraPosition);
    camera.up.set(Vector3.Y);
    camera.lookAt(target);
    camera.update();
    chunks.update(camera, 0);
    game.step(0);
    clearFrame();
    renderer.render(game);
    Gdx.gl.glFinish();
  }

  private static void renderBlankFrame(Game game, GdxRenderer renderer) {
    clearFrame();
    renderer.render(game);
    Gdx.gl.glFinish();
  }

  private static void clearFrame() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClearColor(0, 0, 0, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
  }

  private static Color framebufferColor(PerspectiveCamera camera, Vector3 worldPoint) {
    Vector3 pixel = worldPoint.cpy();
    camera.project(pixel);
    int x = Math.max(0, Math.min(Gdx.graphics.getBackBufferWidth() - 1, Math.round(pixel.x)));
    int y = Math.max(0, Math.min(Gdx.graphics.getBackBufferHeight() - 1, Math.round(pixel.y)));
    Pixmap frame = Pixmap.createFromFrameBuffer(x, y, 1, 1);
    try {
      return new Color(frame.getPixel(0, 0));
    } finally {
      frame.dispose();
    }
  }

  private static void assertFramebufferMatchesTile(
      PerspectiveCamera camera,
      Vector3 worldPoint,
      Pixmap atlas,
      int tile,
      int texelX,
      int texelY,
      String label) {
    Color actual = framebufferColor(camera, worldPoint);
    Color expected = framebufferColorForTile(atlas, tile, texelX, texelY);
    float distance = colorDistance(actual, expected);
    require(distance < .035f,
        label + " sampled an unexpected atlas texel: framebuffer=" + actual + ", expected=" + expected + ", RGB distance=" + distance);
  }

  private static Color framebufferColorForTile(Pixmap atlas, int tile, int texelX, int texelY) {
    int x = (tile % 4) * 20 + 2 + texelX;
    int y = (tile / 4) * 20 + 2 + texelY;
    return new Color(atlas.getPixel(x, y));
  }

  private static void requireCloserToTile(Color actual, Pixmap atlas, int expectedTile, int otherTile, String message) {
    Color expected = framebufferColorForTile(atlas, expectedTile, 8, 8);
    Color other = framebufferColorForTile(atlas, otherTile, 8, 8);
    require(colorDistance(actual, expected) + .10f < colorDistance(actual, other),
        message + ": framebuffer=" + actual + ", expected tile=" + expected + ", hidden tile=" + other);
  }

  private static void requireGreen(Color color, String message) {
    require(color.g > color.r * 1.18f && color.g > color.b * 1.08f, message + ": " + color);
  }

  private static void requireDistinct(Color first, Color second, String label) {
    require(colorDistance(first, second) > .12f, label + " rendered as the same atlas color: " + first + " / " + second);
  }

  private static float colorDistance(Color first, Color second) {
    float dr = first.r - second.r;
    float dg = first.g - second.g;
    float db = first.b - second.b;
    return (float) Math.sqrt(dr * dr + dg * dg + db * db);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Voxel GPU smoke: " + message);
  }

  private static final class GlTracker {
    private final GL20 delegate;
    private final List<String> driverErrors;
    private final Set<Integer> generated = new HashSet<>();
    private final Set<Integer> deleted = new HashSet<>();
    private final List<Integer> generationEvents = new ArrayList<>();
    private GL20 proxy;

    GlTracker(GL20 delegate, List<String> driverErrors) {
      this.delegate = delegate;
      this.driverErrors = driverErrors;
    }

    GL20 proxy() {
      if (proxy != null) return proxy;
      proxy = (GL20)
          Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[] {GL20.class}, (p, method, args) -> {
            String name = method.getName();
            try {
              Object result = method.invoke(delegate, args);
              if (name.equals("glGenBuffer") && result instanceof Integer handle && handle != 0) {
                generated.add(handle);
                generationEvents.add(handle);
              }
              if (name.equals("glGenBuffers") && args != null && args.length > 0 && args[0] instanceof IntBuffer handles) {
                IntBuffer view = handles.duplicate();
                for (int i = view.position(); i < view.limit(); i++)
                  if (view.get(i) != 0) {
                    generated.add(view.get(i));
                    generationEvents.add(view.get(i));
                  }
              }
              if (name.equals("glDeleteBuffer") && args != null && args.length > 0) deleted.add((Integer) args[0]);
              if (name.equals("glDeleteBuffers") && args != null && args.length > 0 && args[0] instanceof IntBuffer handles) {
                IntBuffer view = handles.duplicate();
                for (int i = view.position(); i < view.limit(); i++) deleted.add(view.get(i));
              }
              if (!name.equals("glGetError")) {
                int error = delegate.glGetError();
                if (error != GL20.GL_NO_ERROR)
                  driverErrors.add("GL error 0x" + Integer.toHexString(error) + " after " + name);
              }
              return result;
            } catch (InvocationTargetException failure) {
              throw failure.getCause();
            }
          });
      return proxy;
    }

    Set<Integer> generatedBuffers() { return Set.copyOf(generated); }
    int generationCount() { return generationEvents.size(); }
    Set<Integer> adapterBuffers(Set<Integer> beforeAdapter) { return difference(generated, beforeAdapter); }
    Set<Integer> liveAdapterBuffers(Set<Integer> beforeAdapter) {
      Set<Integer> live = new HashSet<>();
      for (int handle : adapterBuffers(beforeAdapter)) if (delegate.glIsBuffer(handle)) live.add(handle);
      return Set.copyOf(live);
    }
    Set<Integer> difference(Set<Integer> first, Set<Integer> second) {
      Set<Integer> result = new HashSet<>(first);
      result.removeAll(second);
      return Set.copyOf(result);
    }
    int deletedLiveCount(Set<Integer> handles) {
      int count = 0;
      for (int handle : handles) if (deleted.contains(handle) && !delegate.glIsBuffer(handle)) count++;
      return count;
    }
  }
}
