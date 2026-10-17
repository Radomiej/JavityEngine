package org.javity.demo;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import org.javity.components.SpriteRenderer;
import org.javity.engine.*;
import org.javity.ui.flex.*;

final class DemoScene implements SceneBuilder {
  Flex root;
  private UiTheme theme;
  private Texture texture;
  int updates;
  private boolean moving = true;

  @Override
  public void buildScene(JScene scene) {
    scene.getSettings().setClearColor(Color.valueOf("0b1220"));
    Pixmap image = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
    image.setColor(Color.valueOf("22d3ee"));
    image.fillCircle(32, 32, 28);
    image.setColor(Color.valueOf("e2e8f0"));
    image.fillCircle(25, 40, 5);
    texture = new Texture(image);
    image.dispose();
    JGameObject player = scene.instantiateGameObject(new Vector2(600, 360));
    player.addComponent(new SpriteRenderer(JResources.addMemorySprite("demo-player", texture)));
    player.addComponent(
        new JComponent() {
          private float elapsed;

          @Override
          public void update() {
            updates++;
            if (moving) elapsed += JTime.INSTANCE.getDelta();
            getTransform()
                .setPosition(
                    660 + (float) Math.sin(elapsed) * 120, 300 + (float) Math.cos(elapsed) * 70);
          }
        });
    JGameObject ui = scene.instantiateGameObject(new Vector2());
    ui.addComponent(
        new JComponent() {
          @Override
          public void start() {
            createUi();
          }
        });
  }

  private void createUi() {
    theme = new UiTheme();
    root = new Flex(theme, "flex-col p-8 gap-6");
    root.setFillParent(true);
    JGUI.INSTANCE.getStage().addActor(root);
    Flex header = root.add(new Flex(theme, "flex-row items-center justify-between"), "h-16");
    header.add(theme.label("JAVITY", "text-2xl text-cyan-400"), "");
    header.add(theme.label("Java 25  /  libGDX 1.14.2", "text-slate-400"), "");
    Flex body = root.add(new Flex(theme, "flex-row gap-6"), "flex-1");
    Flex panel = body.add(new Flex(theme, "flex-col p-6 gap-4 bg-slate-800"), "w-80");
    panel.add(theme.label("One engine, more possibilities", "text-lg"), "");
    panel.add(theme.label("Local Galaxy Rapid backend", "text-slate-400"), "");
    panel.add(theme.label("Real Yoga / Flexbox layout", "text-cyan-400"), "");
    panel.add(theme.label("Utility classes in Java", "text-slate-400"), "");
    Label state = panel.add(theme.label("Component: running", "text-emerald-500"), "");
    panel.add(
        theme.button(
            "Pause / resume",
            "bg-blue-600",
            () -> {
              moving = !moving;
              state.setText(moving ? "Component: running" : "Component: paused");
            }),
        "h-12 w-full");
    body.add(new Flex("flex-col"), "flex-1");
    root.add(
        theme.label("Resize the window: Yoga recalculates the layout.", "text-sm text-slate-400"),
        "h-8");
  }

  void dispose() {
    if (root != null) root.dispose();
    if (theme != null) theme.dispose();
    if (texture != null) texture.dispose();
  }
}
