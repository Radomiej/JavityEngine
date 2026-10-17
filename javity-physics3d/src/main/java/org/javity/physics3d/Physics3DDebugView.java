package org.javity.physics3d;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw.DebugDrawModes;
import java.util.Objects;

/** Explicit GL-backed debug rendering lease for one Physics3D world. The camera is borrowed. */
public final class Physics3DDebugView implements AutoCloseable {
  private final Physics3D physics;
  private final DebugDrawer drawer;
  private boolean closed;

  Physics3DDebugView(Physics3D physics) {
    this.physics = Objects.requireNonNull(physics);
    DebugDrawer created = new DebugDrawer();
    try {
      created.setDebugMode(DebugDrawModes.DBG_DrawWireframe | DebugDrawModes.DBG_DrawContactPoints);
      drawer = created;
    } catch (RuntimeException | Error failure) {
      created.dispose();
      throw failure;
    }
  }

  void attach() {
    physics.attachDebugDrawer(this, drawer);
  }

  /** Draws the current world using a borrowed camera and this view's owned GL resources. */
  public void render(Camera camera) {
    Objects.requireNonNull(camera, "camera");
    physics.requireDebugViewOwner(this);
    if (closed) throw new IllegalStateException("Physics3D debug view is closed");
    drawer.begin(camera);
    try {
      physics.renderDebugWorld(this);
    } finally {
      drawer.end();
    }
  }

  @Override
  public void close() {
    if (closed) return;
    physics.requireDebugViewOwner(this);
    physics.detachDebugView(this);
    closed = true;
    drawer.dispose();
  }
}
