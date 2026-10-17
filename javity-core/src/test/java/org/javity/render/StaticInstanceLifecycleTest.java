package org.javity.render;

import static org.junit.Assert.*;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.javity.core.Game;
import org.junit.Test;

/** CPU lifecycle regressions only; no actual GL/EGL recovery is claimed here. */
public class StaticInstanceLifecycleTest {
  @Test
  public void twoRecoveryNotificationsWithoutPreparationAlwaysInvalidateRawNames() {
    int[] disposals = {0}, rawNames = {19}, resets = {0};
    Disposable shader =
        (Disposable)
            Proxy.newProxyInstance(
                Disposable.class.getClassLoader(),
                new Class<?>[] {Disposable.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("dispose")) disposals[0]++;
                  return null;
                });
    Runnable invalidate =
        () -> {
          rawNames[0] = 0;
          resets[0]++;
        };
    StaticInstanceBatch.discardRecoveredShader(shader, invalidate);
    rawNames[0] = 29; // New namespace notification before any prepare/shader creation.
    StaticInstanceBatch.discardRecoveredShader(null, invalidate);
    assertEquals(1, disposals[0]);
    assertEquals(2, resets[0]);
    assertEquals(0, rawNames[0]);
  }

  @Test
  public void failedShaderReleaseStillInvalidatesRawNames() {
    int[] rawName = {37};
    Disposable shader =
        () -> {
          throw new IllegalStateException("injected shader cleanup");
        };
    try {
      StaticInstanceBatch.discardRecoveredShader(shader, () -> rawName[0] = 0);
      fail("Expected injected cleanup failure");
    } catch (IllegalStateException expected) {
      assertEquals("injected shader cleanup", expected.getMessage());
    }
    assertEquals(0, rawName[0]);
  }

  @Test
  public void replacingPartsAndNodesOfLiveRendererPrunesDetachedIdsAndKeepsDisabledSurvivor() {
    GdxNativesLoader.load();
    Model model = new Model();
    Game game = new Game();
    ModelRenderer renderer =
        game.createObject("live renderer").addComponent(new ModelRenderer(model));
    Node root = new Node();
    NodePart survivor = new NodePart();
    root.parts.add(survivor);
    renderer.instance.nodes.add(root);
    IdentityHashMap<NodePart, Long> ids = new IdentityHashMap<>();
    AtomicLong sequence = new AtomicLong();
    game.start();
    game.step(0);
    try {
      GdxRenderer.refreshInstancePartIds(renderer.instance, ids, sequence::incrementAndGet);
      long survivorId = ids.get(survivor);
      survivor.enabled = false;
      NodePart previousPart = null;
      NodePart previousChildPart = null;
      for (int i = 0; i < 100; i++) {
        root.parts.clear();
        root.parts.add(survivor);
        NodePart replacement = new NodePart();
        root.parts.add(replacement);
        renderer.instance.nodes.clear();
        renderer.instance.nodes.add(root);
        Node branch = new Node();
        Node child = new Node();
        NodePart childPart = new NodePart();
        child.parts.add(childPart);
        branch.addChild(child);
        renderer.instance.nodes.add(branch);
        GdxRenderer.refreshInstancePartIds(renderer.instance, ids, sequence::incrementAndGet);
        assertEquals("Only attached parts retained at iteration " + i, 3, ids.size());
        assertEquals(survivorId, ids.get(survivor).longValue());
        if (previousPart != null) assertFalse(ids.containsKey(previousPart));
        if (previousChildPart != null) assertFalse(ids.containsKey(previousChildPart));
        previousPart = replacement;
        previousChildPart = childPart;
      }
      survivor.enabled = true;
      GdxRenderer.refreshInstancePartIds(renderer.instance, ids, sequence::incrementAndGet);
      assertEquals(survivorId, ids.get(survivor).longValue());
      renderer.instance.nodes.clear();
      GdxRenderer.refreshInstancePartIds(renderer.instance, ids, sequence::incrementAndGet);
      assertTrue(ids.isEmpty());
    } finally {
      game.close();
      model.dispose();
    }
  }
}
