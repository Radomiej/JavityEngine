package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import java.util.Arrays;
import java.util.function.BiConsumer;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.ui.inspector.RuntimeToolsOverlay;
import org.javity.voxel.VoxelWorld;

/** Native proof shared by the two host demos; uses their installed input chain and real Stage. */
final class RuntimeToolsSmokeProbe {
  static void verify(String name, RuntimeToolsOverlay tools, Stage stage, InputMultiplexer input,
      Game game, GameObject controlled, Camera camera, VoxelWorld world, Runnable render,
      BiConsumer<Integer, Integer> resize) {
    var inspector = tools.inspector();
    int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
    boolean originalCapture = Gdx.input.isCursorCatched();
    long revision = revision(world);
    float[] trs = controlled.getComponent(Transform3D.class).worldMatrix().val.clone();
    float[] cameraBefore = camera.combined.val.clone();
    render.run();
    int[] baseline = pixels();
    Gdx.input.setCursorCatched(true);
    require(input.keyDown(Input.Keys.F1), "F1 not handled");
    input.keyUp(Input.Keys.F1);
    require(inspector.isVisible() && !Gdx.input.isCursorCatched(), "F1 did not release cursor");
    require(inspector.selectObject(controlled), "Cannot select controlled object");
    require(inspector.selectedObject() == controlled, "Selection is not live object identity");
    require(!inspector.focusSelected(), "Focus moved a controller camera");
    require(Arrays.equals(cameraBefore, camera.combined.val), "Refused focus changed camera");
    inspector.setSearchQuery(controlled.name);
    inspector.setCollapsed(controlled.id, true);
    require(inspector.selectedObject() == controlled, "Hidden selection lost");
    inspector.setSearchQuery("no-match-runtime-tools");
    require(inspector.selectedObject() == controlled, "Filter cleared selection");
    inspector.setSearchQuery("");
    inspector.setCollapsed(controlled.id, false);
    render.run();
    TextField value = stage.getRoot().findActor("runtime-transform-value");
    value.setText("12345");
    click(stage, input, "runtime-transform-apply");
    require(Arrays.equals(trs, controlled.getComponent(Transform3D.class).worldMatrix().val),
        "Inspector changed controller-owned TRS");
    click(stage, input, "runtime-object-search");
    input.keyDown(Input.Keys.F);
    input.keyUp(Input.Keys.F);
    require(Arrays.equals(cameraBefore, camera.combined.val), "Panel keyboard reached gameplay");
    require(revision == revision(world), "Panel click edited a voxel");
    render.run();
    int[] open = pixels();
    int changed = 0;
    for (int i = 0; i < baseline.length; i++) if (baseline[i] != open[i]) changed++;
    require(changed > 15000, "Tools panel has no meaningful visible pixels: " + changed);
    screenshot("runtime-tools-" + name + ".png");
    Gdx.graphics.setWindowedMode(900, 600);
    resize.accept(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    render.run();
    require(inspector.detailsViewportHeight() >= 120, "Resize clipped inspector details");
    screenshot("runtime-tools-" + name + "-900x600.png");
    Gdx.graphics.setWindowedMode(width, height);
    resize.accept(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    require(input.keyDown(Input.Keys.F1), "F1 lost priority to text focus");
    input.keyUp(Input.Keys.F1);
    require(!inspector.isVisible() && Gdx.input.isCursorCatched(), "F1 did not restore capture");
    Gdx.input.setCursorCatched(false);
    inspector.setVisible(true);
    inspector.setVisible(false);
    require(!Gdx.input.isCursorCatched(), "Uncaptured cursor became captured");
    Gdx.input.setCursorCatched(true);
    inspector.setVisible(true);
    tools.onFocusLost();
    inspector.setVisible(false);
    require(!Gdx.input.isCursorCatched(), "Unfocused hide recaptured cursor");
    tools.onFocusGained();
    Gdx.input.setCursorCatched(originalCapture);
    inspector.setSearchQuery("");
    render.run();
    Gdx.app.log("RuntimeTools", name + " PASS: F1 capture/restore, input priority, filtering, controlled focus/TRS refusal, actual resize, pixels=" + changed);
  }

  private static long revision(VoxelWorld world) {
    long result = 0;
    for (var pos : world.loadedChunks()) result += world.contentRevision(pos);
    return result;
  }

  private static void click(Stage stage, InputMultiplexer input, String name) {
    Actor actor = stage.getRoot().findActor(name);
    require(actor != null, "Missing control " + name);
    Vector2 point = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2, actor.getHeight() / 2));
    stage.stageToScreenCoordinates(point);
    require(input.touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT), "Panel click not handled " + name);
    input.touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
  }

  private static int[] pixels() {
    Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try {
      int[] values = new int[pixmap.getWidth() * pixmap.getHeight()];
      for (int y = 0; y < pixmap.getHeight(); y++)
        for (int x = 0; x < pixmap.getWidth(); x++) values[y * pixmap.getWidth() + x] = pixmap.getPixel(x, y);
      return values;
    } finally { pixmap.dispose(); }
  }

  private static void screenshot(String name) {
    Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    try { PixmapIO.writePNG(Gdx.files.local("docs/" + name), pixmap, -1, true); }
    finally { pixmap.dispose(); }
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
