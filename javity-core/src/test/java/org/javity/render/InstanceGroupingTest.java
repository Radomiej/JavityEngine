package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.util.*;
import org.junit.Test;

public class InstanceGroupingTest {
  private Renderable renderable() {
    GdxNativesLoader.load();
    Renderable r = new Renderable();
    r.meshPart.mesh =
        new Mesh(
            Mesh.VertexDataType.VertexArray,
            true,
            9,
            0,
            VertexAttribute.Position(),
            VertexAttribute.Normal());
    r.meshPart.mesh.setVertices(new float[9 * r.meshPart.mesh.getVertexSize() / 4]);
    r.meshPart.size = 3;
    r.meshPart.primitiveType = GL20.GL_TRIANGLES;
    r.material = new Material("same", ColorAttribute.createDiffuse(Color.RED));
    return r;
  }

  @Test
  public void sameNameDifferentMaterialDoesNotMerge() {
    Renderable r = renderable();
    try {
      InstanceGroupKey red = InstanceGroupKey.from(r, null);
      r.material = new Material(r.material);
      assertEquals(red, InstanceGroupKey.from(r, null));
      r.material.set(ColorAttribute.createDiffuse(Color.BLUE));
      assertNotEquals(red, InstanceGroupKey.from(r, null));
      r.material.set(new BlendingAttribute());
      assertNotNull(InstanceGroupKey.rejectionReason(r, null));
      r.material.remove(BlendingAttribute.Type);
      r.shader = null;
      Environment e = new Environment();
      e.set(ColorAttribute.createAmbientLight(0.2f, 0.3f, 0.4f, 1));
      InstanceGroupKey before = InstanceGroupKey.from(r, e);
      ((ColorAttribute) e.get(ColorAttribute.AmbientLight)).color.r = .25f;
      assertNotEquals(before, InstanceGroupKey.from(r, e));
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void orderDependentDepthUsesFallback() {
    Renderable r = renderable();
    try {
      r.material.set(new DepthTestAttribute(GL20.GL_LEQUAL, 0, 1, false));
      assertEquals("ORDER_DEPENDENT_DEPTH_STATE", InstanceGroupKey.rejectionReason(r, null));
      r.material.set(new DepthTestAttribute(GL20.GL_ALWAYS, 0, 1, true));
      assertEquals("ORDER_DEPENDENT_DEPTH_STATE", InstanceGroupKey.rejectionReason(r, null));
      r.material.set(new DepthTestAttribute(GL20.GL_LEQUAL, 0, 1, true));
      assertNull(InstanceGroupKey.rejectionReason(r, null));
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void slotReuseAndCapacity() {
    InstanceSlots slots = new InstanceSlots(256);
    for (long i = 0; i < 257; i++) slots.put(i, new Matrix4().setToTranslation(i, 0, 0));
    assertEquals(2, slots.pack(id -> true).size());
    int stable = slots.slot(42);
    slots.remove(42);
    slots.put(999, new Matrix4());
    assertEquals(stable, slots.slot(999));
    var chunks = slots.pack(id -> true);
    assertEquals(257, chunks.stream().mapToInt(c -> c.ownerIds().length).sum());
    assertFalse(
        chunks.stream().flatMapToLong(c -> Arrays.stream(c.ownerIds())).anyMatch(id -> id == 42));
    assertTrue(slots.dirtyEndExclusive() > slots.dirtyStart());
    slots.clearDirty();
    assertEquals(-1, slots.dirtyStart());
  }

  @Test
  public void unsupportedTransformFallsBack() {
    assertNull(InstanceGroupKey.transformRejection(new Matrix4().setToScaling(2, 2, 2)));
    assertNotNull(InstanceGroupKey.transformRejection(new Matrix4().setToScaling(-1, 1, 1)));
    assertNotNull(InstanceGroupKey.transformRejection(new Matrix4().setToScaling(1, 2, 1)));
    Matrix4 shear = new Matrix4();
    shear.val[Matrix4.M01] = .2f;
    assertNotNull(InstanceGroupKey.transformRejection(shear));
  }

  @Test
  public void extremeUniformScaleKeepsDeterminantSign() {
    for (float scale : new float[] {1e-30f, 1e30f}) {
      assertNull(
          InstanceGroupKey.transformRejection(new Matrix4().setToScaling(scale, scale, scale)));
      assertEquals(
          "MIRRORED_TRANSFORM",
          InstanceGroupKey.transformRejection(new Matrix4().setToScaling(-scale, scale, scale)));
    }
  }

  @Test
  public void presentedHierarchyBounds() {
    PerspectiveCamera camera = new PerspectiveCamera(60, 100, 100);
    camera.position.set(0, 0, 10);
    camera.lookAt(0, 0, 0);
    camera.near = .1f;
    camera.far = 100;
    camera.update();
    InstanceSlots slots = new InstanceSlots(2);
    BoundingBox local = new BoundingBox(new Vector3(-1, -1, -1), new Vector3(1, 1, 1));
    Map<Long, Boolean> ordinary = new HashMap<>();
    for (long id = 0; id < 4; id++) {
      Matrix4 parent = new Matrix4().setToTranslation(id * 20, 0, 0);
      Matrix4 presented = parent.mul(new Matrix4().setToTranslation(1, 0, 0));
      slots.put(id, presented);
      ordinary.put(id, camera.frustum.boundsInFrustum(new BoundingBox(local).mul(presented)));
    }
    Set<Long> actual = new HashSet<>();
    for (var c : slots.pack(id -> slots.visible(id, local, camera.frustum)))
      for (long id : c.ownerIds()) actual.add(id);
    for (long id : ordinary.keySet()) assertEquals(ordinary.get(id), actual.contains(id));
  }

  @Test
  public void missingContextFallsBack() {
    assertFalse(InstanceCapabilities.detect().supported());
  }

  private static final class CpuTexture extends Texture {
    CpuTexture() {
      super();
    }
  }

  @Test
  public void textureIdentityAndExactValues() {
    Renderable r = renderable();
    Mesh old = r.meshPart.mesh;
    r.meshPart.mesh =
        new Mesh(
            Mesh.VertexDataType.VertexArray,
            true,
            9,
            0,
            VertexAttribute.Position(),
            VertexAttribute.Normal(),
            VertexAttribute.TexCoords(0));
    r.meshPart.mesh.setVertices(new float[9 * r.meshPart.mesh.getVertexSize() / 4]);
    old.dispose();
    try {
      Texture first = new CpuTexture(), second = new CpuTexture();
      r.material.set(TextureAttribute.createDiffuse(first));
      InstanceGroupKey original = InstanceGroupKey.from(r, null);
      r.material = new Material(r.material);
      assertEquals(original, InstanceGroupKey.from(r, null));
      ((TextureAttribute) r.material.get(TextureAttribute.Diffuse)).offsetU = Float.MIN_VALUE;
      assertNotEquals(original, InstanceGroupKey.from(r, null));
      r.material.set(TextureAttribute.createDiffuse(second));
      assertNotEquals(original, InstanceGroupKey.from(r, null));
      r.material.set(TextureAttribute.createDiffuse(first));
      ((ColorAttribute) r.material.get(ColorAttribute.Diffuse)).color.g = Float.MIN_VALUE;
      assertNotEquals(original, InstanceGroupKey.from(r, null));
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void geometryStateAndLightsAreSnapshots() {
    Renderable r = renderable();
    try {
      InstanceGroupKey original = InstanceGroupKey.from(r, null);
      r.meshPart.offset = 1;
      assertNotEquals(original, InstanceGroupKey.from(r, null));
      r.meshPart.offset = 0;
      r.meshPart.size = 6;
      assertNotEquals(original, InstanceGroupKey.from(r, null));
      r.meshPart.size = 3;
      r.material.set(IntAttribute.createCullFace(GL20.GL_FRONT));
      assertNotEquals(original, InstanceGroupKey.from(r, null));
      Environment e = new Environment();
      var light =
          new com.badlogic.gdx.graphics.g3d.environment.DirectionalLight()
              .set(Color.WHITE, 1, -1, 0);
      e.add(light);
      InstanceGroupKey key = InstanceGroupKey.from(r, e);
      light.direction.x += Float.MIN_NORMAL;
      light.color.r = .99f;
      assertNotEquals(key, InstanceGroupKey.from(r, e));
      e.set(ColorAttribute.createFog(Color.RED));
      assertNotNull(InstanceGroupKey.rejectionReason(r, e));
    } finally {
      r.meshPart.mesh.dispose();
    }
  }

  @Test
  public void viewPackingDoesNotMutateOwnerMatrices() {
    InstanceSlots slots = new InstanceSlots(2);
    Matrix4 source = new Matrix4().setToTranslation(1, 2, 3);
    slots.put(1, source);
    source.idt();
    assertEquals(1, slots.pack(id -> true).get(0).transforms()[Matrix4.M03], 0);
    assertTrue(slots.pack(id -> false).isEmpty());
    assertEquals(1, slots.pack(id -> true).get(0).ownerIds().length);
    slots.clearDirty();
    slots.put(1, new Matrix4().setToTranslation(1, 2, 3));
    assertEquals(-1, slots.dirtyStart());
    assertFalse(slots.remove(7));
    slots.clear();
    assertEquals(0, slots.size());
  }

  @Test
  public void capabilitiesCheckActualVersionAndLimits() {
    var oldGraphics = com.badlogic.gdx.Gdx.graphics;
    var oldGl = com.badlogic.gdx.Gdx.gl;
    var oldGl30 = com.badlogic.gdx.Gdx.gl30;
    try {
      int[] major = {3}, minor = {2}, attribs = {16};
      com.badlogic.gdx.Gdx.graphics =
          (com.badlogic.gdx.Graphics)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {com.badlogic.gdx.Graphics.class},
                  (p, m, a) -> {
                    if (m.getName().equals("getGLVersion"))
                      return new com.badlogic.gdx.graphics.glutils.GLVersion(
                          com.badlogic.gdx.Application.ApplicationType.Desktop,
                          major[0] + "." + minor[0],
                          "test",
                          "test");
                    throw new AssertionError(m.getName());
                  });
      com.badlogic.gdx.Gdx.gl30 =
          (GL30)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {GL30.class},
                  (p, m, a) -> {
                    if (m.getName().equals("glGetIntegerv")) {
                      ((java.nio.IntBuffer) a[1])
                          .put(0, (int) a[0] == GL20.GL_MAX_VERTEX_ATTRIBS ? attribs[0] : 16);
                      return null;
                    }
                    throw new AssertionError(m.getName());
                  });
      com.badlogic.gdx.Gdx.gl = com.badlogic.gdx.Gdx.gl30;
      assertFalse(InstanceCapabilities.detect().supported());
      minor[0] = 3;
      assertTrue(InstanceCapabilities.detect().supported());
      attribs[0] = 6;
      assertFalse(InstanceCapabilities.detect().supported());
    } finally {
      com.badlogic.gdx.Gdx.graphics = oldGraphics;
      com.badlogic.gdx.Gdx.gl = oldGl;
      com.badlogic.gdx.Gdx.gl30 = oldGl30;
    }
  }
}
