package org.javity.ui.inspector;

import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import java.util.Objects;
import java.util.function.Supplier;
import org.javity.core.GameObject;
import org.javity.effects.Clock;
import org.javity.presentation.PresentationEffects3D;
import org.javity.presentation.PresentationEffects3D.Surface;
import org.javity.ui.flex.Flex;
import org.javity.ui.flex.UiTheme;

/** Optional typed authoring panel. Borrows Stage/theme/selection; owns its tree and one playback. */
public final class FeelPreviewPanel implements AutoCloseable {
  public enum Preview { USE_TOOL, HIT, LANDING, SPRINT }

  /** Immutable next-play parameters: local units, Hz, damping ratio and seconds. */
  public record Parameters(double x, double y, double z, double frequency, double damping,
      double duration) {
    public Parameters {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
          || !Double.isFinite(frequency) || !Double.isFinite(damping) || !Double.isFinite(duration)
          || frequency <= 0 || damping < 0 || duration <= 0)
        throw new IllegalArgumentException("Finite values required; Hz/duration > 0, damping >= 0");
    }
  }

  private final Stage stage;
  private final Flex root, content;
  private final FeelPreviewSession session;
  private final Label status, counts;
  private final TextButton presetButton, clockButton, surfaceButton;
  private final TextField[] fields = new TextField[6];
  private Preview preview = Preview.USE_TOOL;
  private Surface surface = Surface.MODEL;
  private boolean closed;
  private String parameterError;

  public FeelPreviewPanel(Stage stage, UiTheme theme, Supplier<GameObject> selection) {
    this.stage = Objects.requireNonNull(stage);
    Objects.requireNonNull(theme);
    session = new FeelPreviewSession(selection);
    root = new Flex(theme, "flex-col p-4 bg-slate-900 rounded-lg border border-slate-700");
    content = new Flex(theme, "flex-col gap-1");
    try {
      root.setName("feel-preview-panel"); root.setTouchable(Touchable.enabled);
      root.addListener(new InputListener() {
        @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; }
      });
      ScrollPane scroll = new ScrollPane(content);
      scroll.setScrollingDisabled(true, false); scroll.setFadeScrollBars(false);
      scroll.addListener(new InputListener() {
        @Override public void enter(InputEvent e, float x, float y, int pointer, Actor from) {
          stage.setScrollFocus(scroll);
        }
      });
      root.add(scroll, "flex-1");
      content.add(theme.label("FEEL PREVIEW", "text-cyan-400"), "h-8");
      content.add(theme.label("Selected object / same-owner manager", "text-sm text-slate-200", true), "h-8");
      presetButton = theme.button("USE_TOOL", "text-sm text-white bg-blue-600 rounded", () ->
          selectPreset(Preview.values()[(preview.ordinal() + 1) % Preview.values().length]));
      presetButton.setName("feel-preset"); content.add(presetButton, "h-8");
      clockButton = theme.button("WORLD", "text-sm text-white bg-slate-700 rounded", () ->
          setClock(session.clock() == Clock.WORLD ? Clock.UI : Clock.WORLD));
      clockButton.setName("feel-clock"); content.add(clockButton, "h-8");
      surfaceButton = theme.button(surface.name(), "text-sm text-white bg-slate-700 rounded", () -> {
        surface = Surface.values()[(surface.ordinal() + 1) % Surface.values().length];
        surfaceButtonText();
      });
      surfaceButton.setName("feel-surface"); content.add(surfaceButton, "h-8");
      String[] names = {"X", "Y", "Z", "Hz", "Damping", "Seconds"};
      for (int i = 0; i < fields.length; i++) {
        Flex row = content.add(new Flex(theme, "flex-row gap-1"), "h-8");
        row.add(theme.label(names[i], "text-sm text-slate-200"), "w-20");
        fields[i] = theme.textField("0", "text-sm text-slate-200 bg-slate-800 rounded");
        fields[i].setName("feel-parameter-" + names[i]); row.add(fields[i], "flex-1");
      }
      Flex controls = content.add(new Flex(theme, "flex-row gap-1"), "h-8");
      addButton(controls, theme, "Play", this::playEdited);
      addButton(controls, theme, "Pause", this::pause);
      addButton(controls, theme, "Resume", this::resume);
      addButton(controls, theme, "Stop", this::stop);
      status = content.add(theme.label("Select an object", "text-sm text-slate-200", true), "h-16");
      status.setName("feel-status");
      counts = content.add(theme.label("", "text-sm text-cyan-400"), "h-6");
      content.add(theme.label("Scene transition: --mm (SceneManager demo)", "text-sm text-slate-400", true), "h-10");
      selectPreset(preview); stage.addActor(root); update(0);
    } catch (RuntimeException | Error failure) {
      root.remove(); root.close(); content.close(); session.close(); throw failure;
    }
  }

  private void addButton(Flex row, UiTheme theme, String text, Runnable action) {
    TextButton button = theme.button(text, "text-sm text-white bg-blue-600 rounded", action);
    button.setName("feel-" + text.toLowerCase(java.util.Locale.ROOT)); row.add(button, "flex-1");
  }
  private void surfaceButtonText() { surfaceButton.setText(surface.name()); }

  public void selectPreset(Preview value) {
    if (closed) return;
    parameterError = null;
    preview = Objects.requireNonNull(value); presetButton.setText(value.name());
    surface = value == Preview.LANDING || value == Preview.SPRINT ? Surface.WORLD_CAMERA : Surface.MODEL;
    surfaceButtonText();
    setParameters(switch (value) {
      case USE_TOOL -> new Parameters(8, -2, 0, 2, .7, 1);
      case HIT -> new Parameters(3, 3, 3, 3, .6, .7);
      case LANDING -> new Parameters(0, -3, 0, 3, .7, .8);
      case SPRINT -> new Parameters(0, .06, 0, 2, .7, 3);
    });
  }
  public void setParameters(Parameters parameters) {
    Objects.requireNonNull(parameters); if (closed) return;
    double[] values = {parameters.x, parameters.y, parameters.z, parameters.frequency, parameters.damping, parameters.duration};
    for (int i = 0; i < fields.length; i++) fields[i].setText(Double.toString(values[i]));
  }
  private void playEdited() {
    if (closed) return;
    try {
      Parameters p = new Parameters(Double.parseDouble(fields[0].getText()), Double.parseDouble(fields[1].getText()),
          Double.parseDouble(fields[2].getText()), Double.parseDouble(fields[3].getText()),
          Double.parseDouble(fields[4].getText()), Double.parseDouble(fields[5].getText()));
      var preset = switch (preview) {
        case USE_TOOL, LANDING -> PresentationEffects3D.recoil(surface, p.x, p.y, p.z, 0, 0, 0, p.frequency, p.damping, p.duration);
        case HIT -> {
          if (surface != Surface.MODEL) throw new IllegalArgumentException("HIT requires MODEL / Presentation3D");
          yield PresentationEffects3D.punchScale(p.x, p.y, p.z, p.frequency, p.damping, p.duration);
        }
        case SPRINT -> PresentationEffects3D.timeBob(surface, p.x, p.y, p.z, p.frequency, 0, p.duration);
      };
      play(preset);
    } catch (IllegalArgumentException failure) {
      parameterError = "Invalid next preset: " + failure.getMessage(); refresh();
    }
  }
  public void play(PresentationEffects3D.Preset preset) { parameterError = null; session.play(preset); refresh(); }
  public void setClock(Clock value) { parameterError = null; session.setClock(value); clockButton.setText(session.clock().name()); refresh(); }
  public void pause() { session.pause(); refresh(); }
  public void resume() { session.resume(); refresh(); }
  public void stop() { parameterError = null; session.stop(); refresh(); }
  public void setVisible(boolean visible) {
    if (closed) return;
    if (!visible) stage.unfocus(root);
    root.setVisible(visible);
  }
  public boolean isPlaying() { return session.isPlaying(); }
  private void refresh() {
    if (closed) return;
    int active = session.activeCount();
    boolean playing = session.isPlaying();
    status.setText(parameterError == null ? session.status() : parameterError);
    counts.setText("Manager active: " + active + " / owned: " + (playing ? 1 : 0));
  }
  public void update(float unscaledDelta) {
    if (closed) return;
    session.update(); refresh();
    float width = Math.min(336, Math.max(0, stage.getWidth() * .42f));
    float height = Math.max(0, stage.getHeight() - 32);
    root.setBounds(16, 16, width, height); root.validate();
  }
  @Override public void close() {
    if (closed) return;
    session.close(); closed = true;
    root.remove(); root.close(); content.close();
  }
}
