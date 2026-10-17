package org.javity.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import org.javity.core.Component;

public final class Camera3D extends Component {
  public final PerspectiveCamera camera;

  public Camera3D(float fieldOfView) {
    camera = new PerspectiveCamera(fieldOfView, 1, 1);
    camera.near = .1f;
    camera.far = 1000;
  }
}
