package org.javity.core;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Vector3;
import org.javity.render.ModelRenderer;
import org.junit.Test;

public class Transform3DTest {
  @Test
  public void parentRotationAndScaleAffectChildPosition() {
    Transform3D parent = new Transform3D(), child = new Transform3D();
    parent.position.set(10, 0, 0);
    parent.rotation.setFromAxis(Vector3.Z, 90);
    parent.scale.set(2, 2, 2);
    child.position.set(1, 0, 0);
    child.setParent(parent);
    Vector3 world = child.worldMatrix().getTranslation(new Vector3());
    assertEquals(10, world.x, .0001);
    assertEquals(2, world.y, .0001);
    assertThrows(IllegalArgumentException.class, () -> parent.setParent(child));
  }

  @Test
  public void modelResourceIsSharedButInstanceStateIsIndependent() {
    Model model = new Model();
    ModelRenderer a = new ModelRenderer(model), b = new ModelRenderer(model);
    a.instance.transform.setToTranslation(2, 3, 4);
    assertSame(a.instance.model, b.instance.model);
    assertNotSame(a.instance, b.instance);
    assertEquals(Vector3.Zero, b.instance.transform.getTranslation(new Vector3()));
    model.dispose();
  }
}
