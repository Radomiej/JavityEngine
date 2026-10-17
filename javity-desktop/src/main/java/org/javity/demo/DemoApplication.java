package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import org.javity.engine.JGUI;
import org.javity.engine.ext.JavityApplication;

final class DemoApplication extends JavityApplication {
  private final DemoScene demo;
  private final boolean smoke;
  private int frames;

  DemoApplication(boolean smoke) {
    this(new DemoScene(), smoke);
  }

  private DemoApplication(DemoScene demo, boolean smoke) {
    super(demo);
    this.demo = demo;
    this.smoke = smoke;
  }

  @Override
  public void render() {
    super.render();
    frames++;
    if (smoke && frames == 120) {
      if (demo.updates < 100 || demo.root == null || demo.root.getChildren().size == 0)
        throw new IllegalStateException("Scene or Yoga UI did not start");
      Pixmap screenshot =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      PixmapIO.writePNG(Gdx.files.local("smoke.png"), screenshot, -1, true);
      screenshot.dispose();
      Gdx.app.log(
          "JavitySmoke",
          "PASS: 120 frames, component updates=" + demo.updates + ", Yoga UI active");
      Gdx.app.exit();
    }
  }

  @Override
  public void resize(int width, int height) {
    super.resize(width, height);
    if (demo.root != null) JGUI.INSTANCE.getStage().getViewport().update(width, height, true);
  }

  @Override
  public void dispose() {
    super.dispose();
    demo.dispose();
  }
}
