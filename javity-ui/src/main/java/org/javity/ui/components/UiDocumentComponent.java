package org.javity.ui.components;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import java.util.Objects;
import java.util.function.Supplier;
import org.javity.core.Component;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiNode;

/** Mounts and updates a UiDocument panel on a borrowed Stage. */
public class UiDocumentComponent extends Component {
  private final Thread uiThread;
  private final Stage stage;
  private final UiDocument document;
  private final Supplier<UiNode> panel;

  /**
   * Creates a panel component. The document is owned by this component; Stage and its theme are
   * borrowed. Construct and use the component on the Stage's UI/render owner thread.
   */
  public UiDocumentComponent(Stage stage, UiDocument document, Supplier<UiNode> panel) {
    Stage checkedStage = Objects.requireNonNull(stage, "stage");
    UiDocument checkedDocument = Objects.requireNonNull(document, "document");
    Supplier<UiNode> checkedPanel = Objects.requireNonNull(panel, "panel");
    this.uiThread = Thread.currentThread();
    this.stage = checkedStage;
    this.document = checkedDocument;
    this.panel = checkedPanel;
    own(
        () -> {
          checkUiThread();
          checkedDocument.close();
        });
    updateMode = UpdateMode.UI;
  }

  public final UiDocument document() {
    checkUiThread();
    return document;
  }

  @Override
  public void onCreate() {
    checkUiThread();
    document.mount(stage);
    refreshPanel();
    syncVisibility();
  }

  @Override
  public void onUpdate(float delta) {
    checkUiThread();
    refreshPanel();
  }

  @Override
  protected void onActivationChanged() {
    syncVisibility();
  }

  /** Renders the latest panel tree supplied by the constructor callback. */
  public final void refreshPanel() {
    checkUiThread();
    document.render(Objects.requireNonNull(panel.get(), "panel supplier returned null"));
  }

  /**
   * Synchronizes panel visibility with component enabled and GameObject active state. Call this
   * when either state changes, including while this component is disabled or its object is
   * inactive.
   */
  public final void syncVisibility() {
    checkUiThread();
    if (document.isClosed()) return;
    var root = document.root();
    boolean visible = isEnabled() && getGameObject() != null && getGameObject().isActive();
    root.setVisible(visible);
    root.setTouchable(visible ? Touchable.childrenOnly : Touchable.disabled);
    if (!visible && root.getStage() == stage) stage.unfocus(root);
  }

  @Override
  protected void onRelease() {
    checkUiThread();
    document.unmount();
  }

  private void checkUiThread() {
    if (Thread.currentThread() != uiThread)
      throw new IllegalStateException("UiDocumentComponent must be used on its UI owner thread");
  }
}
