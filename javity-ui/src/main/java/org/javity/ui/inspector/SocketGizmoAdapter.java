package org.javity.ui.inspector;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import org.javity.avatar.AvatarBinding;

/** Borrowed LINE batch/binding/base. Reads the canonical socket pose, never changes bones. */
public final class SocketGizmoAdapter {
  private SocketGizmoAdapter() {}

  public static void draw(
      ShapeRenderer borrowed,
      Camera camera,
      AvatarBinding binding,
      String socketName,
      Matrix4 base) {
    Matrix4 socket = binding.socketTransform(socketName, base, new Matrix4());
    Vector3 origin = socket.getTranslation(new Vector3());
    for (int i = 0; i < 3; i++) {
      borrowed.setColor(i == 0 ? Color.RED : i == 1 ? Color.GREEN : Color.CYAN);
      Vector3 axis = new Vector3(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0).rot(socket).nor();
      borrowed.line(origin, origin.cpy().mulAdd(axis, .45f));
    }
  }
}
