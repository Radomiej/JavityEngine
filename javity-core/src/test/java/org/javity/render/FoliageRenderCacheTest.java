package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Pool;
import java.util.*;
import org.javity.core.*;
import org.javity.render.foliage.*;
import org.junit.Test;

/**
 * Real CPU geometry/mesh storage; the mesh allocator is injected to avoid requiring a GL context.
 */
public class FoliageRenderCacheTest {
  static {
    GdxNativesLoader.load();
  }

  static final class BorrowedTexture extends Texture {
    int disposals;

    BorrowedTexture() {
      super();
    }

    @Override
    public void dispose() {
      disposals++;
    }

    @Override
    public int getWidth() {
      return 256;
    }

    @Override
    public int getHeight() {
      return 128;
    }
  }

  static final class CpuMesh extends Mesh {
    int disposals;
    boolean failDispose;
    int boundsReads;

    CpuMesh(int vertices, int indices) {
      super(
          VertexDataType.VertexArray,
          true,
          vertices,
          indices,
          VertexAttribute.Position(),
          VertexAttribute.Normal(),
          VertexAttribute.TexCoords(0));
    }

    @Override
    public BoundingBox calculateBoundingBox(BoundingBox bounds, int offset, int count) {
      boundsReads++;
      return super.calculateBoundingBox(bounds, offset, count);
    }

    @Override
    public void dispose() {
      disposals++;
      super.dispose();
      if (failDispose) throw new AssertionError("injected mesh cleanup");
    }
  }

  static final class Allocator implements FoliageRenderCache.MeshFactory {
    final List<CpuMesh> meshes = new ArrayList<>();
    int attempts, failAt = -1;

    @Override
    public Mesh create(int vertices, int indices) {
      if (++attempts == failAt) throw new IllegalStateException("injected allocation");
      CpuMesh mesh = new CpuMesh(vertices, indices);
      meshes.add(mesh);
      return mesh;
    }
  }

  @Test
  public void revisionOnlyReplacementAndLifecycleKeepBorrowedTextureAlive() {
    BorrowedTexture texture = new BorrowedTexture();
    Allocator allocator = new Allocator();
    FoliageRenderCache cache = new FoliageRenderCache(allocator);
    try (Game game = new Game()) {
      GameObject object = game.createObject("patch");
      Transform3D transform = object.addComponent(new Transform3D());
      FoliagePatch3D patch =
          object.addComponent(FoliagePatch3D.grass(FoliageAtlas.bundled(texture)).count(4));
      game.start();
      cache.reconcile(game);
      var first = cache.prepare(patch, 0);
      assertSame(first, cache.prepare(patch, 0));
      transform.position.x = 100;
      assertSame(first, cache.prepare(patch, 1));
      assertEquals(1, cache.stats().builds());
      patch.seed(12);
      var next = cache.prepare(patch, 1);
      assertNotSame(first, next);
      assertEquals(1, allocator.meshes.get(0).disposals);
      patch.setEnabled(false);
      cache.reconcile(game);
      assertEquals(0, cache.stats().patches());
      assertEquals(1, allocator.meshes.get(1).disposals);
      patch.setEnabled(true);
      cache.reconcile(game);
      assertNotSame(next, cache.prepare(patch, 2));
      object.removeComponent(patch);
      cache.reconcile(game);
      assertEquals(0, cache.stats().patches());
      assertEquals(0, texture.disposals);
    } finally {
      cache.dispose();
    }
  }

  @Test
  public void partialAllocationFailureRetainsOldGeometryAndRetriesWithFrameBackoff() {
    BorrowedTexture texture = new BorrowedTexture();
    Allocator allocator = new Allocator();
    FoliageRenderCache cache = new FoliageRenderCache(allocator);
    try (Game game = new Game()) {
      GameObject object = game.createObject("patch");
      object.addComponent(new Transform3D());
      FoliagePatch3D patch =
          object.addComponent(FoliagePatch3D.bushes(FoliageAtlas.bundled(texture)).count(1));
      game.start();
      cache.reconcile(game);
      var first = cache.prepare(patch, 0);
      patch.count(4096);
      allocator.failAt = allocator.attempts + 2;
      assertSame(first, cache.prepare(patch, 1));
      int afterFailure = allocator.attempts;
      assertSame(first, cache.prepare(patch, 1));
      assertEquals(afterFailure, allocator.attempts);
      assertEquals(1, allocator.meshes.get(1).disposals);
      assertEquals(0, allocator.meshes.get(0).disposals);
      assertEquals(1, cache.stats().buildFailures());
      allocator.failAt = -1;
      assertNotSame(first, cache.prepare(patch, 100));
      assertEquals(1, allocator.meshes.get(0).disposals);
    } finally {
      cache.dispose();
    }
    assertTrue(allocator.meshes.stream().allMatch(mesh -> mesh.disposals == 1));
    assertEquals(0, texture.disposals);
  }

  @Test
  public void explicitMaterialKeepsItsChannelsAndCutoutStateIsPrivate() {
    BorrowedTexture texture = new BorrowedTexture();
    Material template = new Material(ColorAttribute.createEmissive(1, .2f, .1f, 1));
    template.set(new BlendingAttribute(true, .2f));
    FoliagePatch3D patch =
        FoliagePatch3D.grass(FoliageAtlas.bundled(texture)).material(template).cutoff(.3f);
    Material material = FoliageRenderCache.material(patch.snapshot());
    assertTrue(material.has(ColorAttribute.Emissive));
    assertFalse(material.has(TextureAttribute.Diffuse));
    assertFalse(material.get(BlendingAttribute.class, BlendingAttribute.Type).blended);
    assertEquals(1, material.get(BlendingAttribute.class, BlendingAttribute.Type).opacity, 0);
    assertTrue(material.get(DepthTestAttribute.class, DepthTestAttribute.Type).depthMask);
    assertEquals(GL20.GL_NONE, material.get(IntAttribute.class, IntAttribute.CullFace).value);
    assertEquals(.3f, material.get(FloatAttribute.class, FloatAttribute.AlphaTest).value, 0);
    assertTrue(template.get(BlendingAttribute.class, BlendingAttribute.Type).blended);
    assertEquals(0, texture.disposals);
  }

  @Test
  public void wholePatchBoundsCullBeforeSubmissionAndSceneWithoutCameraReleasesMeshes() {
    Allocator allocator = new Allocator();
    FoliageRenderCache cache = new FoliageRenderCache(allocator);
    try (Game game = new Game();
        Game emptyScene = new Game()) {
      GameObject object = game.createObject("bushes");
      object.addComponent(new Transform3D());
      FoliagePatch3D patch =
          object.addComponent(
              FoliagePatch3D.bushes(FoliageAtlas.bundled(new BorrowedTexture())).count(3));
      game.start();
      cache.reconcile(game);
      var entry = cache.prepare(patch, 0);
      PerspectiveCamera camera = new PerspectiveCamera(60, 100, 100);
      camera.position.set(0, 1, 8);
      camera.lookAt(0, 1, 0);
      camera.near = .1f;
      camera.far = 100;
      camera.update();
      Array<Renderable> output = new Array<>();
      Pool<Renderable> pool =
          new Pool<>() {
            @Override
            protected Renderable newObject() {
              return new Renderable();
            }
          };
      IdentityHashMap<Renderable, Component> owners = new IdentityHashMap<>();
      cache.beginView();
      int cachedBoundsReads = allocator.meshes.getFirst().boundsReads;
      cache.collect(patch, entry, new Matrix4(), camera, output, pool, owners);
      assertEquals(
          "Draws reuse cached geometry bounds",
          cachedBoundsReads,
          allocator.meshes.getFirst().boundsReads);
      assertFalse(output.isEmpty());
      assertSame(patch, owners.get(output.first()));
      int firstDraws = output.size;
      cache.collect(
          patch, entry, new Matrix4().setToTranslation(1000, 0, 0), camera, output, pool, owners);
      assertEquals(firstDraws, output.size);
      assertEquals(1, cache.stats().culled());
      assertEquals(1, cache.stats().visible());
      assertEquals(1, cache.stats().builds());
      cache.reconcile(emptyScene);
      assertEquals(0, cache.stats().patches());
      assertTrue(allocator.meshes.stream().allMatch(mesh -> mesh.disposals == 1));
    } finally {
      cache.dispose();
    }
  }

  static final class RequiredProvider implements RecoverableShaderProvider {
    @Override
    public void contextRestored() {}

    @Override
    public Shader getShader(Renderable renderable) {
      return null;
    }

    @Override
    public void dispose() {}
  }

  @Test
  public void explicitProviderRequirementIsCheckedWithoutAllocatingGeometry() {
    FoliagePatch3D patch =
        FoliagePatch3D.grass(FoliageAtlas.bundled(new BorrowedTexture()))
            .material(new Material(), RequiredProvider.class);
    assertThrows(
        IllegalArgumentException.class,
        () -> FoliageRenderCache.validateShaderProvider(patch, null));
    FoliageRenderCache.validateShaderProvider(patch, new RequiredProvider());
  }

  @Test
  public void failedFirstBuildStaysEmptyAndCleanupContinuesAfterError() {
    Allocator allocator = new Allocator();
    FoliageRenderCache cache = new FoliageRenderCache(allocator);
    try (Game game = new Game()) {
      GameObject object = game.createObject("bushes");
      object.addComponent(new Transform3D());
      FoliagePatch3D patch =
          object.addComponent(
              FoliagePatch3D.bushes(FoliageAtlas.bundled(new BorrowedTexture())).count(4096));
      game.start();
      allocator.failAt = 1;
      assertNull(cache.prepare(patch, 0));
      assertEquals(0, cache.stats().patches());
      assertEquals(0, cache.stats().meshes());
      assertEquals(0, cache.stats().builds());
      assertEquals(1, cache.stats().buildFailures());
      allocator.failAt = -1;
      assertNotNull(cache.prepare(patch, 100));
      assertEquals(3, cache.stats().meshes());
      allocator.meshes.getFirst().failDispose = true;
      assertThrows(AssertionError.class, cache::dispose);
      assertEquals(0, cache.stats().patches());
      cache.dispose();
      assertTrue(allocator.meshes.stream().allMatch(mesh -> mesh.disposals == 1));
    } finally {
      cache.dispose();
    }
  }

  @Test
  public void offscreenFoliageRemainsAvailableToWorldPassWithoutColorSubmission() {
    Allocator allocator = new Allocator();
    FoliageRenderCache cache = new FoliageRenderCache(allocator);
    try (Game game = new Game()) {
      GameObject object = game.createObject("offscreen caster");
      object.addComponent(new Transform3D());
      FoliagePatch3D patch =
          object.addComponent(
              FoliagePatch3D.bushes(FoliageAtlas.bundled(new BorrowedTexture()))
                  .area(.1f, .1f)
                  .count(1));
      game.start();
      var entry = cache.prepare(patch, 0);
      PerspectiveCamera camera = new PerspectiveCamera(60, 100, 100);
      camera.position.set(0, 1, 8);
      camera.lookAt(0, 1, 0);
      camera.near = .1f;
      camera.far = 100;
      camera.update();
      Array<Renderable> color = new Array<>(), passOnly = new Array<>();
      Pool<Renderable> pool =
          new Pool<>() {
            @Override
            protected Renderable newObject() {
              return new Renderable();
            }
          };
      IdentityHashMap<Renderable, Component> owners = new IdentityHashMap<>();
      cache.beginView();
      Matrix4 offscreen = new Matrix4().setToTranslation(6, 0, 0);
      cache.collect(patch, entry, offscreen, camera, color, passOnly, pool, owners);
      assertEquals("Offscreen geometry stays out of color draws", 0, color.size);
      assertEquals("World pass still receives offscreen casters", 1, passOnly.size);
      assertSame(patch, owners.get(passOnly.first()));
      assertArrayEquals(offscreen.val, passOnly.first().worldTransform.val, 0);
      assertEquals(1, cache.stats().culled());
      assertEquals(0, cache.stats().draws());
      assertEquals(0, cache.stats().visible());
      cache.collect(patch, entry, new Matrix4(), camera, color, passOnly, pool, owners);
      assertEquals(1, color.size);
      assertEquals(
          "Visible geometry must not be duplicated into pass-only casters", 1, passOnly.size);
      assertEquals(1, cache.stats().draws());
      assertEquals(1, cache.stats().visible());
    } finally {
      cache.dispose();
    }
  }
}
