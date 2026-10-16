package org.javity.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import java.util.List;
import org.javity.core.GameObject;
import org.javity.render.lighting.*;
import org.javity.ui.flex.*;

/** Typed selected-light editor. Values are a draft; Apply is one optimistic, undoable command. */
public final class LightAuthoringPanel implements AutoCloseable {
  private final Stage stage;
  private final RuntimeInspector inspector;
  private final Flex root;
  private final TextField[] fields = new TextField[7];
  private final Label status;
  private final TextButton type;
  private Light3D light;
  private SceneInspection.LightEditSession edit;
  private GameObject selected;
  private int index;
  private boolean closed;

  public LightAuthoringPanel(Stage stage, UiTheme theme, RuntimeInspector inspector) {
    this.stage = stage;
    this.inspector = inspector;
    root = new Flex(theme, "flex-col p-4 gap-1 bg-slate-900 rounded-lg border border-slate-700");
    try {
      root.setName("light-authoring-panel");
      root.add(theme.label("LIGHT / SCENE", "text-cyan-400"), "h-8");
      type =
          theme.button(
              "Select a light",
              "text-sm text-white bg-slate-700 rounded",
              () -> {
                index++;
                load();
              });
      root.add(type, "h-8");
      String[] names = {"Red", "Green", "Blue", "Intensity", "Range", "Cutoff deg", "Exponent"};
      for (int i = 0; i < 7; i++) {
        Flex row = root.add(new Flex(theme, "flex-row gap-1"), "h-8");
        row.add(theme.label(names[i], "text-sm text-slate-200"), "w-24");
        fields[i] = theme.textField("0", "text-sm text-white bg-slate-800 rounded");
        fields[i].setName("light-" + i);
        row.add(fields[i], "flex-1");
      }
      Flex buttons = root.add(new Flex(theme, "flex-row gap-1"), "h-8");
      buttons.add(
          theme.button("Reload", "text-sm text-white bg-slate-700 rounded", this::load), "flex-1");
      buttons.add(
          theme.button("Apply", "text-sm text-white bg-blue-600 rounded", this::apply), "flex-1");
      status =
          root.add(
              theme.label("Select a light in F1 tools", "text-sm text-slate-200", true), "h-20");
      root.add(
          theme.label(
              "Transform gizmos use the same Undo / Redo.\nRange is world units. No shadows / PBR.",
              "text-sm text-slate-400",
              true),
          "h-16");
      stage.addActor(root);
      root.setVisible(false);
    } catch (RuntimeException | Error failure) {
      root.remove();
      root.close();
      throw failure;
    }
  }

  public void load() {
    selected = inspector.selectedObject();
    List<Light3D> lights = selected == null ? List.of() : selected.getComponents(Light3D.class);
    light = lights.isEmpty() ? null : lights.get(Math.floorMod(index, lights.size()));
    edit = null;
    if (light == null) {
      type.setText("Select a light");
      return;
    }
    edit = inspector.beginLightEdit(light);
    var s = SceneInspection.lightState(light);
    float[] v = {
      s.red(), s.green(), s.blue(), s.intensity(), s.range(), s.cutoffDegrees(), s.exponent()
    };
    for (int i = 0; i < 7; i++) {
      fields[i].setText(Float.toString(v[i]));
      fields[i].setDisabled(
          i == 4 && !(light instanceof PointLight3D) || i >= 5 && !(light instanceof SpotLight3D));
    }
    type.setText(
        light.getClass().getSimpleName() + " / " + (Math.floorMod(index, lights.size()) + 1));
    status.setText("Draft loaded. Apply records one Undo.");
  }

  public void apply() {
    try {
      if (edit == null) throw new IllegalStateException("Select a light");
      float[] v = new float[7];
      for (int i = 0; i < 7; i++) v[i] = Float.parseFloat(fields[i].getText());
      inspector.commitLightEdit(
          edit, new SceneInspection.LightState(v[0], v[1], v[2], v[3], v[4], v[5], v[6]));
      load();
      status.setText("Applied / use inspector Undo or Redo");
    } catch (IllegalArgumentException | IllegalStateException failure) {
      status.setText("Rejected: " + failure.getMessage() + " / Reload draft");
    }
  }

  public void update(boolean visible) {
    if (closed) return;
    GameObject current = inspector.selectedObject();
    if (current != selected
        || light == null && current != null && current.getComponent(Light3D.class) != null
        || light != null && (current == null || !current.containsComponent(light))) {
      index = 0;
      load();
    }
    boolean show = visible && light != null;
    setVisible(show);
    root.setBounds(
        16, 16, Math.min(336, stage.getWidth() * .42f), Math.max(0, stage.getHeight() - 32));
    root.validate();
  }

  public void setVisible(boolean visible) {
    if (closed) return;
    if (!visible) stage.unfocus(root);
    root.setVisible(visible);
  }

  public boolean hasLight() {
    return light != null;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    stage.unfocus(root);
    root.remove();
    root.close();
  }
}
