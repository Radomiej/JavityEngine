package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.animation.AnimationTransition;
import org.javity.animation.LinearModelMixer;
import org.javity.animation.ModelAnimationLayer;
import org.javity.animation.ModelAnimator;
import org.javity.animation.ModelPlayback;
import org.javity.animation.ModelPoseAnimator;
import org.javity.animation.ModelPosePlayback;
import org.javity.animation.NodeMask;
import org.javity.animation.PlayMode;
import org.javity.animation.SpriteAnimationPlayer;
import org.javity.animation.SpriteClip;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.effects.CancelMode;
import org.javity.effects.Clock;
import org.javity.effects.Ease;
import org.javity.effects.EffectSystem;
import org.javity.effects.Feedback;
import org.javity.effects.FeedbackPlayer;
import org.javity.effects.FloatTarget;
import org.javity.effects.Targets;
import org.javity.effects.TweenHandle;
import org.javity.effects.TweenPlayer;
import org.javity.render.Camera2D;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;
import org.javity.render.ShapeRenderer2D;
import org.javity.render.SpriteRenderer2D;
import org.javity.script.ScriptUiException;
import org.javity.script.ScriptUiRuntime;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.declarative.UiMarkup;
import org.javity.ui.declarative.UiNode;
import org.javity.ui.effects.UiTargets;
import org.javity.ui.flex.UiTheme;

/** Native integration gallery for UI markup, React hooks, effects, clips, and skin animation. */
public final class PluginsDemo extends ApplicationAdapter {
  private static final String MARKUP =
      """
      <View id="gallery" class="flex-row gap-[12px] p-[12px]">
        <View id="native-panel" class="flex-col gap-[8px] p-[12px] w-[320px] bg-slate-800 rounded-lg">
          <Text id="native-title" class="text-xl text-cyan-400">Native XML UI</Text>
          <Text id="instructions" class="text-sm text-slate-400">SPACE switches clip · P pauses world</Text>
          <View id="fade-panel" class="w-[40px] h-[24px] bg-[#ffffff]" />
          <Text id="java-score" class="text-base text-white">Java score: ${score}</Text>
          <Button id="native-add" class="bg-blue-600 text-white rounded-md" text="Java feedback +1" onClick="nativeAdd" />
          <Progress id="progress" class="w-[260px] h-[18px]" value="${progress}" />
          <TextField id="name-field" class="w-[260px] h-[36px] bg-white text-black" value="Type here" />
          <Button id="disabled" class="bg-slate-700 text-white" text="Disabled control" disabled="true" />
          <ScrollView id="scroll" class="w-[280px] h-[148px]">
            <View id="scroll-content" class="flex-col gap-[8px] p-[8px] bg-slate-700">
              <Text id="scroll-one" class="text-base text-white">Scroll content one</Text>
              <Text id="scroll-two" class="text-base text-white">Scroll content two</Text>
              <Text id="scroll-three" class="text-base text-white">Scroll content three</Text>
              <Text id="scroll-four" class="text-base text-white">Scroll content four</Text>
              <Text id="scroll-five" class="text-base text-white">Scroll content five</Text>
              <Text id="scroll-six" class="text-base text-white">Scroll content six</Text>
            </View>
          </ScrollView>
        </View>
      </View>
      """;

  private final boolean smoke;
  private final AtomicInteger score = new AtomicInteger();
  private Game game;
  private GdxRenderer renderer;
  private Stage stage;
  private UiTheme theme;
  private UiDocument nativeDocument, reactDocument;
  private UiDocument controlsDocument;
  private ScriptUiRuntime runtime;
  private ScriptUiRuntime controlsRuntime;
  private Texture atlas;
  private SpriteRenderer2D sprite;
  private SpriteAnimationPlayer clips;
  private TextField field;
  private ScrollPane scroll;
  private TextButton reactButton, nativeButton;
  private GameObject fxOwner, fadedPanel, cameraObject;
  private ShapeRenderer2D pulseShape;
  private TweenHandle alphaTween;
  private TweenPlayer alphaPlayer;
  private FloatTarget alphaTarget;
  private Model skin;
  private ModelAnimator animatorB;
  private ModelPoseAnimator poseAnimatorA;
  private ModelPosePlayback posePlaybackA;
  private ModelAnimationLayer upperLayerA;
  private ModelRenderer skinRendererA, skinRendererB;
  private com.badlogic.gdx.math.Matrix4 rootBoneBefore, tipBoneBefore, classicSeekBefore;
  private Pixmap poseBaseEndpoint0,
      poseBaseMidpoint,
      poseBaseEndpoint1,
      poseMaskHalf,
      classicBeforePixels;
  private int mixerMidpointPixelDelta,
      mixerEndpointPixelDelta,
      maskedHalfPixelDelta,
      maskedUpperPixelDelta;
  private int maskedLowerPixelDelta, classicMarkerCalls, classicSeekPixelDelta;
  private int frames, worldFrames, uiFrames, clickCount;
  private Color alphaPauseBefore, alphaPauseAfter, feedbackEndpointPixel;
  private float alphaPauseX, alphaPauseY;
  private float actorAlphaBeforePause, actorAlphaAfterPause;
  private boolean alphaPausedPixelChanged;
  private TweenHandle restoreTween, completeTween;
  private org.javity.effects.PlaybackHandle galleryFeedback, reactFeedback;
  private int effectCompletions, feedbackCompletions, staleTimerCalls;
  private float recordedUiVolume = -1;
  private boolean recordedUiMuted;
  private int volumeActionCalls, muteActionCalls;
  private float fxStartX;

  public PluginsDemo(boolean smoke) {
    this.smoke = smoke;
  }

  @Override
  public void create() {
    renderer = new GdxRenderer();
    game = new Game();
    game.addSystem(new EffectSystem());
    GameObject clockObject = game.createObject("phase counters");
    clockObject.addComponent(
        new Component() {
          @Override
          public void onUpdate(float delta) {
            worldFrames++;
          }
        });
    Component uiClock =
        clockObject.addComponent(
            new Component() {
              @Override
              public void onUpdate(float delta) {
                uiFrames++;
              }
            });
    uiClock.updateMode = Component.UpdateMode.UI;
    cameraObject = game.createObject("gallery camera");
    Camera2D camera = cameraObject.addComponent(new Camera2D());
    camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    cameraObject.setPosition(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f);
    Camera3D camera3 = cameraObject.addComponent(new Camera3D(50));
    camera3.camera.position.set(0, .15f, 8);
    camera3.camera.lookAt(0, 0, 0);
    camera3.camera.near = .1f;
    camera3.camera.far = 50;

    theme = new UiTheme();
    stage = new Stage(new ScreenViewport());
    nativeDocument = new UiDocument(theme);
    nativeDocument.render(
        UiMarkup.parse(
            MARKUP,
            Map.of("nativeAdd", this::nativeAction),
            Map.of("score", score::get, "progress", () -> Math.min(1, score.get() / 8f))));
    nativeDocument.root().setFillParent(true);
    stage.addActor(nativeDocument.root());
    nativeButton = (TextButton) nativeDocument.actor("native-add");
    field = (TextField) nativeDocument.actor("name-field");
    scroll = (ScrollPane) nativeDocument.actor("scroll");
    ((TextButton) nativeDocument.actor("disabled")).setDisabled(true);

    reactDocument = new UiDocument(theme);
    stage.addActor(reactDocument.root());
    runtime = new ScriptUiRuntime(reactDocument);
    runtime.bindAction("claim", this::reactAction);
    runtime.bindAction("stale", () -> staleTimerCalls++);
    runtime.bindValue("score", score::get);
    runtime.eval(readResource("/org/javity/script/javity-sample.js"));
    runtime.eval(reactSampleMount());
    try (UiDocument isolatedDocument = new UiDocument(null);
        ScriptUiRuntime isolatedRuntime = new ScriptUiRuntime(isolatedDocument)) {
      isolatedRuntime.eval("globalThis.privateRuntimeMarker = 'isolated'");
      runtime.eval(
          "if (globalThis.privateRuntimeMarker !== undefined) throw new Error('runtime contexts"
              + " are shared')");
    }
    reactDocument.root().setSize(330, 190);
    reactDocument.root().setPosition(Gdx.graphics.getWidth() - 345, Gdx.graphics.getHeight() - 198);
    reactButton = (TextButton) reactDocument.actor("increment");

    controlsDocument = new UiDocument(theme);
    controlsDocument.mount(stage);
    controlsDocument.root().setSize(290, 220);
    controlsDocument.root().setPosition(24, 24);
    controlsRuntime = new ScriptUiRuntime(controlsDocument);
    controlsRuntime.bindNumberAction(
        "setVolume",
        value -> {
          recordedUiVolume = (float) value;
          volumeActionCalls++;
        });
    controlsRuntime.bindBooleanAction(
        "setMuted",
        value -> {
          recordedUiMuted = value;
          muteActionCalls++;
        });
    controlsRuntime.eval(readResource("/org/javity/script/javity-sample.js"));
    controlsRuntime.eval("mountSettingsControls()");

    createSprite();
    createEffects();
    createSkin();
    game.start();
    posePlaybackA.seekNormalized(.5);
    resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    Gdx.gl.glClearColor(.035f, .047f, .067f, 1);
    Gdx.input.setInputProcessor(
        new InputMultiplexer(
            stage,
            new InputAdapter() {
              @Override
              public boolean keyDown(int keycode) {
                if (stage.getKeyboardFocus() != null) return false;
                if (keycode == Input.Keys.SPACE) switchClip();
                if (keycode == Input.Keys.P) {
                  if (game.isPaused()) game.resume();
                  else game.pause();
                }
                return keycode == Input.Keys.SPACE || keycode == Input.Keys.P;
              }
            }));
  }

  private void createSprite() {
    Pixmap pixels = new Pixmap(96, 32, Pixmap.Format.RGBA8888);
    pixels.setColor(Color.valueOf("#fb7185"));
    pixels.fillRectangle(0, 0, 32, 32);
    pixels.setColor(Color.valueOf("#38bdf8"));
    pixels.fillRectangle(32, 0, 32, 32);
    pixels.setColor(Color.valueOf("#a3e635"));
    pixels.fillRectangle(64, 0, 32, 32);
    atlas = new Texture(pixels);
    pixels.dispose();
    List<TextureRegion> frames =
        List.of(
            new TextureRegion(atlas, 0, 0, 32, 32),
            new TextureRegion(atlas, 32, 0, 32, 32),
            new TextureRegion(atlas, 64, 0, 32, 32));
    GameObject spriteObject = game.createObject("atlas clip").setPosition(690, 160);
    sprite = spriteObject.addComponent(new SpriteRenderer2D(frames.getFirst()));
    sprite.width = 56;
    sprite.height = 56;
    clips =
        spriteObject.addComponent(
            new SpriteAnimationPlayer().register(new SpriteClip("pulse", frames, .12f)));
    clips.play("pulse", PlayMode.LOOP).setSpeed(.8);
  }

  private void createEffects() {
    fxOwner = game.createObject("feedback player");
    FeedbackPlayer player = fxOwner.addComponent(new FeedbackPlayer(Clock.UI));
    fxOwner.setPosition(555, 165);
    pulseShape = fxOwner.addComponent(ShapeRenderer2D.rectangle(86, 48));
    pulseShape.cornerRadius = 12;
    pulseShape.color.set(Color.valueOf("#8b5cf6"));
    galleryFeedback =
        player
            .play(
                Feedback.sequence(
                    Feedback.parallel(
                        Feedback.tween(Targets.positionX(fxOwner), 600, .55, Ease.OUT_QUAD),
                        Feedback.tween(Targets.scaleX(fxOwner), 1.2f, .55, Ease.OUT_QUAD),
                        Feedback.tween(Targets.shapeBlue(pulseShape), .95f, .55, Ease.OUT_QUAD)),
                    Feedback.parallel(
                        Feedback.tween(Targets.positionX(fxOwner), 555, .55, Ease.IN_OUT_QUAD),
                        Feedback.tween(Targets.scaleX(fxOwner), 1f, .55, Ease.IN_OUT_QUAD),
                        Feedback.tween(
                            Targets.shapeBlue(pulseShape), .55f, .55, Ease.IN_OUT_QUAD))))
            .onComplete(() -> feedbackCompletions++);

    fadedPanel = game.createObject("UI alpha panel");
    nativeDocument.actor("fade-panel").setColor(1, 1, 1, 1);
    alphaPlayer = fadedPanel.addComponent(new TweenPlayer(Clock.UI));
    alphaTarget = UiTargets.alpha(fadedPanel, nativeDocument, "fade-panel");
    alphaTween = alphaPlayer.to(alphaTarget, .15f, .8, Ease.OUT_QUAD);
  }

  private void createSkin() {
    skin = GeneratedSkinModel.create();
    for (int i = 0; i < 2; i++) {
      GameObject modelObject = game.createObject("two-bone skin " + i);
      Transform3D transform = modelObject.addComponent(new Transform3D());
      transform.position.set(i == 0 ? -.85f : .85f, -.2f, 0);
      transform.scale.set(.95f, .95f, .95f);
      ModelRenderer modelRenderer = modelObject.addComponent(new ModelRenderer(skin));
      if (i == 0) {
        skinRendererA = modelRenderer;
        poseAnimatorA = modelObject.addComponent(new ModelPoseAnimator());
        LinearModelMixer mixer =
            LinearModelMixer.builder(1.2).add("bend-left", 0).add("bend-right", 1).build();
        posePlaybackA = poseAnimatorA.play(mixer, PlayMode.LOOP, 0).setSpeed(0);
        upperLayerA = poseAnimatorA.addLayer("upper-action", NodeMask.paths("root/tip"));
        upperLayerA.play(AnimationTransition.clip("upper-reach").loop().startAt(.5).speed(0));
      } else {
        ModelAnimator animator = modelObject.addComponent(new ModelAnimator());
        animatorB = animator;
        skinRendererB = modelRenderer;
        animator.play("bend-right", PlayMode.LOOP);
      }
    }
  }

  private void nativeAction() {
    score.incrementAndGet();
    clickCount++;
    FeedbackPlayer player = fxOwner.getComponent(FeedbackPlayer.class);
    player.play(Feedback.tween(Targets.shapeGreen(pulseShape), .9f, .18, Ease.OUT_QUAD));
  }

  private void reactAction() {
    score.incrementAndGet();
    clickCount++;
    FeedbackPlayer player = fxOwner.getComponent(FeedbackPlayer.class);
    reactFeedback =
        player.play(Feedback.tween(Targets.shapeRed(pulseShape), .95f, .2, Ease.OUT_QUAD));
  }

  private void switchClip() {
    if (clips.current().mode() == PlayMode.LOOP) clips.play("pulse", PlayMode.ONCE);
    else clips.play("pulse", PlayMode.LOOP);
  }

  @Override
  public void render() {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    float delta = smoke ? 1f / 60f : Gdx.graphics.getDeltaTime();
    game.step(delta);
    if (runtime != null) runtime.tick(delta);
    renderer.render(game);
    nativeDocument.refresh();
    stage.act(delta);
    stage.draw();
    frames++;
    if (smoke) {
      try {
        verifySmoke();
      } catch (RuntimeException | Error failure) {
        Pixmap diagnostic = capture();
        try {
          PixmapIO.writePNG(Gdx.files.local("plugins-failure.png"), diagnostic, -1, true);
        } finally {
          diagnostic.dispose();
        }
        throw failure;
      }
    }
  }

  private void click(Actor actor) {
    Vector2 screen =
        actor.localToScreenCoordinates(new Vector2(actor.getWidth() / 2, actor.getHeight() / 2));
    int sx = Math.round(screen.x), sy = Math.round(screen.y);
    InputProcessor processor = Gdx.input.getInputProcessor();
    if (processor == null) throw new IllegalStateException("No installed input processor");
    processor.touchDown(sx, sy, 0, Input.Buttons.LEFT);
    processor.touchUp(sx, sy, 0, Input.Buttons.LEFT);
  }

  private void verifySmoke() {
    if (frames == 5) {
      Actor retainedButton = nativeDocument.actor("native-add");
      try {
        nativeDocument.render(UiNode.element("Mystery", "invalid", "", Map.of(), List.of()));
        throw new IllegalStateException("Invalid native UI tree was accepted");
      } catch (IllegalArgumentException expected) {
        if (nativeDocument.actor("native-add") != retainedButton)
          throw new IllegalStateException("Invalid native render replaced the previous keyed tree");
      }
      click(nativeButton);
      nativeDocument.refresh();
      if (score.get() != 1
          || !((Label) nativeDocument.actor("java-score"))
              .getText()
              .toString()
              .equals("Java score: 1"))
        throw new IllegalStateException(
            "Native UI action or Java value binding failed: "
                + ((Label) nativeDocument.actor("java-score")).getText());
      if (!((TextButton) nativeDocument.actor("disabled")).isDisabled())
        throw new IllegalStateException("Disabled UI button became interactive");
      scroll.setScrollY(scroll.getMaxY());
      if (scroll.getScrollY() <= 0) throw new IllegalStateException("ScrollView did not scroll");
      Vector2 fadeCenter =
          nativeDocument.actor("fade-panel").localToStageCoordinates(new Vector2(20, 12));
      alphaPauseX = fadeCenter.x;
      alphaPauseY = Gdx.graphics.getHeight() - fadeCenter.y;
      stage.setKeyboardFocus(field);
      field.selectAll();
      field.setText("");
      InputProcessor processor = Gdx.input.getInputProcessor();
      processor.keyTyped('n');
      processor.keyTyped('a');
      processor.keyTyped('t');
      processor.keyTyped('i');
      processor.keyTyped('v');
      processor.keyTyped('e');
      if (!field.getText().equals("native") || field != nativeDocument.actor("name-field"))
        throw new IllegalStateException(
            "Focused native TextField did not receive key input or changed keyed identity: "
                + field.getText());
      com.badlogic.gdx.scenes.scene2d.ui.TextField controlledField =
          (com.badlogic.gdx.scenes.scene2d.ui.TextField)
              reactDocument.actor("react-controlled-field");
      Actor controlledProgress = reactDocument.actor("react-controlled-progress");
      stage.setKeyboardFocus(controlledField);
      controlledField.selectAll();
      controlledField.setText("");
      for (char typed : "abc".toCharArray()) processor.keyTyped(typed);
      if (!controlledField.getText().equals("abc")
          || controlledField != reactDocument.actor("react-controlled-field")
          || controlledProgress != reactDocument.actor("react-controlled-progress")
          || !controlledProgress.getClass().getSimpleName().equals("ProgressBar"))
        throw new IllegalStateException(
            "React-controlled TextField or Progress did not retain its native actor after input");
      runtime.eval(
          "if(controlledChangeCalls!==3)throw new Error('programmatic React value updates recursed"
              + " into TextField onChange')");
      stage.act(0);
      stage.draw();
      Pixmap controlledPixels = capture();
      try {
        Vector2 fillPoint =
            controlledProgress.localToStageCoordinates(
                new Vector2(
                    controlledProgress.getWidth() * .15f, controlledProgress.getHeight() / 2));
        Vector2 trackPoint =
            controlledProgress.localToStageCoordinates(
                new Vector2(
                    controlledProgress.getWidth() * .4f, controlledProgress.getHeight() / 2));
        Color fillPixel =
            pixel(controlledPixels, fillPoint.x, Gdx.graphics.getHeight() - fillPoint.y);
        Color trackPixel =
            pixel(controlledPixels, trackPoint.x, Gdx.graphics.getHeight() - trackPoint.y);
        if (fillPixel.equals(trackPixel))
          throw new IllegalStateException(
              "React state did not change the rendered native Progress fill");
      } finally {
        controlledPixels.dispose();
      }
      verifySettingsControls(processor);
    }
    if (frames == 15) {
      click(reactButton);
      if (clickCount < 2 || score.get() != 2)
        throw new IllegalStateException("React button action did not reach Java gameplay");
      runtime.eval(
          "if (bridgeValue !== 2 || JavityGame.get('score') !== 2) throw new Error('React bridge"
              + " primitive mismatch')");
      if (reactDocument.actor("label") == null
          || !((Label) reactDocument.actor("label")).getText().toString().equals("Count: 1"))
        throw new IllegalStateException(
            "React counter did not reconcile keyed label: "
                + ((Label) reactDocument.actor("label")).getText());
      Actor keyed = reactDocument.actor("label");
      runtime.eval(reactSampleMount());
      if (keyed != reactDocument.actor("label"))
        throw new IllegalStateException("React keyed actor identity changed");
      runtime.eval("setTimeout(() => JavityGame.call('stale'), 500)");
      try {
        runtime.reload("function broken( {");
        throw new IllegalStateException("Invalid reload source was accepted");
      } catch (ScriptUiException expected) {
        if (reactDocument.actor("label") != keyed)
          throw new IllegalStateException("Syntax reload failure replaced the active React tree");
      }
    }
    if (frames == 30) {
      if (posePlaybackA == null
          || animatorB.current() == null
          || skinRendererA.instance == skinRendererB.instance)
        throw new IllegalStateException(
            "Model animation instances do not own independent controllers");
      poseBaseEndpoint0 = capture();
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-model-endpoint-0.png"), poseBaseEndpoint0, -1, true);
      classicBeforePixels = capture();
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-model-classic-before.png"), classicBeforePixels, -1, true);
      rootBoneBefore = skinRendererA.instance.nodes.first().parts.first().bones[0].cpy();
      tipBoneBefore = skinRendererA.instance.nodes.first().parts.first().bones[1].cpy();
      posePlaybackA.setParameter(.5);
      ModelPlayback classicSeek =
          animatorB.play(AnimationTransition.clip("bend-right").loop().startAt(.1));
      classicSeekBefore = skinRendererB.instance.nodes.first().parts.first().bones[1].cpy();
      classicSeek.seekNormalized(.65);
      if (matrixDelta(
              classicSeekBefore, skinRendererB.instance.nodes.first().parts.first().bones[1])
          < .02f)
        throw new IllegalStateException(
            "Classic ModelAnimator normalized seek did not change the skin bone matrix");
      classicSeek.onMarker("native-tip", .7, () -> classicMarkerCalls++);
    }
    if (frames == 31) {
      poseBaseMidpoint = capture();
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-model-midpoint.png"), poseBaseMidpoint, -1, true);
      mixerMidpointPixelDelta =
          differingPixels(poseBaseEndpoint0, poseBaseMidpoint, 350, 575, 220, 465);
      if (matrixDelta(tipBoneBefore, skinRendererA.instance.nodes.first().parts.first().bones[1])
          < .02f)
        throw new IllegalStateException(
            "1D model mixer midpoint did not change the skin bone matrix");
      posePlaybackA.setParameter(1);
      com.badlogic.gdx.math.Matrix4 classicSeekAfter =
          skinRendererB.instance.nodes.first().parts.first().bones[1];
      if (matrixDelta(classicSeekBefore, classicSeekAfter) < .02f)
        throw new IllegalStateException(
            "Classic ModelAnimator transition seek did not change the skin bone matrix");
    }
    if (frames == 32) {
      poseBaseEndpoint1 = capture();
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-model-endpoint-1.png"), poseBaseEndpoint1, -1, true);
      mixerEndpointPixelDelta =
          differingPixels(poseBaseEndpoint0, poseBaseEndpoint1, 350, 575, 220, 465);
      if (matrixDelta(rootBoneBefore, skinRendererA.instance.nodes.first().parts.first().bones[0])
          > .0001f) throw new IllegalStateException("Mixer changed the unanimated root bone");
      upperLayerA.setWeight(.5);
    }
    if (frames == 33) {
      poseMaskHalf = capture();
      PixmapIO.writePNG(Gdx.files.local("artifacts/j-model-mask-half.png"), poseMaskHalf, -1, true);
      maskedHalfPixelDelta = differingPixels(poseBaseEndpoint1, poseMaskHalf, 350, 575, 220, 375);
      upperLayerA.setWeight(1);
    }
    if (frames == 34) {
      Pixmap maskedFull = capture();
      PixmapIO.writePNG(Gdx.files.local("artifacts/j-model-mask-full.png"), maskedFull, -1, true);
      try {
        maskedUpperPixelDelta = differingPixels(poseBaseEndpoint1, maskedFull, 350, 575, 220, 375);
        maskedLowerPixelDelta = differingPixels(poseBaseEndpoint1, maskedFull, 350, 575, 380, 465);
        if (matrixDelta(rootBoneBefore, skinRendererA.instance.nodes.first().parts.first().bones[0])
            > .0001f)
          throw new IllegalStateException("Masked upper action changed the unmasked root bone");
        if (matrixDelta(tipBoneBefore, skinRendererA.instance.nodes.first().parts.first().bones[1])
            < .02f)
          throw new IllegalStateException("Masked upper action did not change the tip bone matrix");
      } finally {
        maskedFull.dispose();
      }
      upperLayerA.setWeight(0);
    }
    if (frames == 36) {
      Pixmap classicAfterPixels = capture();
      try {
        PixmapIO.writePNG(
            Gdx.files.local("artifacts/j-model-classic-after.png"), classicAfterPixels, -1, true);
        classicSeekPixelDelta =
            differingPixels(classicBeforePixels, classicAfterPixels, 550, 790, 220, 465);
      } finally {
        classicAfterPixels.dispose();
      }
      if (classicMarkerCalls != 1)
        throw new IllegalStateException(
            "Classic ModelAnimator native marker did not fire exactly once");
    }
    if (frames == 37) verifyPoseCrossfadeSmoke();
    if (frames == 25) {
      runtime.reload(
          readResource("/org/javity/script/javity-sample.js") + "\n" + reactSampleMount());
      reactButton = (TextButton) reactDocument.actor("increment");
      runtime.tick(1);
      if (staleTimerCalls != 0)
        throw new IllegalStateException("Old runtime timer survived successful reload");
    }
    if (frames == 30) {
      click(reactButton);
      if (score.get() != 3
          || !((Label) reactDocument.actor("label")).getText().toString().equals("Count: 1"))
        throw new IllegalStateException(
            "Reloaded React button did not run TSX callback/state update");
    }
    if (frames == 75) {
      if (galleryFeedback == null
          || !galleryFeedback.isCompleted()
          || feedbackCompletions != 1
          || Math.abs(fxOwner.transform.position.x - 555) > .02f
          || Math.abs(fxOwner.transform.scale.x - 1) > .02f
          || Math.abs(pulseShape.color.b - .55f) > .02f)
        throw new IllegalStateException(
            "Sequence/parallel feedback endpoint or completion callback failed");
      fxStartX = fxOwner.transform.position.x;
      TweenPlayer tweenPlayer = fxOwner.addComponent(new TweenPlayer(Clock.UI));
      restoreTween = tweenPlayer.to(Targets.positionX(fxOwner), fxStartX + 70, 2, Ease.LINEAR);
      completeTween =
          tweenPlayer
              .to(Targets.scaleY(fxOwner), 1.25f, .2, Ease.OUT_QUAD)
              .onComplete(() -> effectCompletions++);
    }
    if (frames == 10) {
      var playback = clips.current();
      double before = playback.time();
      game.step(.05f);
      double advanced = playback.time() - before;
      if (Math.abs(playback.speed() - .8) > .001 || Math.abs(advanced - .04) > .006)
        throw new IllegalStateException(
            "Sprite playback speed was not applied: speed="
                + playback.speed()
                + ", elapsed="
                + advanced);
    }
    if (frames == 40) switchClip();
    if (frames == 70) {
      if (!clips.current().isCompleted())
        throw new IllegalStateException("ONCE sprite clip did not complete");
      clips.play("pulse", PlayMode.LOOP).setSpeed(.8);
    }
    if (frames == 60) {
      actorAlphaBeforePause = nativeDocument.actor("fade-panel").getColor().a;
      Pixmap beforePause = capture();
      alphaPauseBefore = pixel(beforePause, alphaPauseX, alphaPauseY);
      beforePause.dispose();
      game.pause();
      int before = worldFrames, beforeUi = uiFrames;
      alphaTween = alphaPlayer.to(alphaTarget, .5f, .2f, Ease.OUT_QUAD);
      for (int i = 0; i < 5; i++) game.step(.1f);
      actorAlphaAfterPause = nativeDocument.actor("fade-panel").getColor().a;
      if (worldFrames != before) throw new IllegalStateException("WORLD updated while paused");
      if (uiFrames < beforeUi + 5)
        throw new IllegalStateException("UI clock stopped with paused WORLD");
      if (Math.abs(actorAlphaBeforePause - actorAlphaAfterPause) < .2f)
        throw new IllegalStateException(
            "UiTargets alpha did not advance during the paused UI step: before="
                + actorAlphaBeforePause
                + ", after="
                + actorAlphaAfterPause
                + ", tween="
                + alphaTween);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      renderer.render(game);
      stage.act(0);
      stage.draw();
      Pixmap afterPause = capture();
      alphaPauseAfter = pixel(afterPause, alphaPauseX, alphaPauseY);
      afterPause.dispose();
      alphaPausedPixelChanged =
          Math.max(
                  Math.abs(alphaPauseBefore.r - alphaPauseAfter.r),
                  Math.max(
                      Math.abs(alphaPauseBefore.g - alphaPauseAfter.g),
                      Math.abs(alphaPauseBefore.b - alphaPauseAfter.b)))
              >= .1f;
      if (!alphaPausedPixelChanged)
        throw new IllegalStateException(
            "UI alpha did not change the same framebuffer pixel while WORLD was paused: before="
                + alphaPauseBefore
                + ", after="
                + alphaPauseAfter);
      game.resume();
    }
    if (frames == 80) {
      if (Math.abs(fxOwner.transform.position.x - fxStartX) < 1)
        throw new IllegalStateException("Restore tween did not advance before cancellation");
      restoreTween.cancel(CancelMode.RESTORE_START);
      if (!restoreTween.isCancelled() || Math.abs(fxOwner.transform.position.x - fxStartX) > .001f)
        throw new IllegalStateException("RESTORE_START did not restore progressed effect target");
    }
    if (frames == 83) {
      var playback = clips.current();
      playback.pause();
      double pausedTime = playback.time();
      int pausedRegion = sprite.region.getRegionX();
      advancePoseSmoke(.2f);
      if (Math.abs(playback.time() - pausedTime) > .001
          || sprite.region.getRegionX() != pausedRegion)
        throw new IllegalStateException("Locally paused sprite playback advanced or changed frame");
      playback.resume();
      game.step(.1f);
      if (playback.time() - pausedTime < .07 || sprite.region.getRegionX() == pausedRegion)
        throw new IllegalStateException("Sprite playback did not resume time and frame selection");
    }
    if (frames == 90) Gdx.graphics.setWindowedMode(1000, 680);
    if (frames == 119) {
      // Keep the exact same rendered scene at capture; snapshot remains owned until dispose.
      if (runtime != null) runtime.tick(0);
    }
    if (frames == 120) {
      Pixmap pixels =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        verifyPixels(pixels);
        PixmapIO.writePNG(Gdx.files.local("plugins-smoke.png"), pixels, -1, true);
      } catch (RuntimeException | Error failure) {
        PixmapIO.writePNG(Gdx.files.local("plugins-failure.png"), pixels, -1, true);
        throw failure;
      } finally {
        pixels.dispose();
      }
      int gl = Gdx.gl.glGetError();
      if (gl != GL20.GL_NO_ERROR)
        throw new IllegalStateException("Plugins smoke OpenGL error 0x" + Integer.toHexString(gl));
      ProgressBarSmokeProbe.run(theme);
      Gdx.app.log(
          "JavityPluginsSmoke",
          "PASS 120 frames; nativeClicks="
              + clickCount
              + ", score="
              + score.get()
              + ", UI="
              + uiFrames
              + ", WORLD="
              + worldFrames
              + ", scrollY="
              + scroll.getScrollY()
              + ", mixerPixels="
              + mixerMidpointPixelDelta
              + "/"
              + mixerEndpointPixelDelta
              + ", maskedPixels="
              + maskedHalfPixelDelta
              + "/"
              + maskedUpperPixelDelta
              + ", classicSeekPixels="
              + classicSeekPixelDelta);
      verifyReactCloseReentrancy();
      Gdx.app.exit();
    }
  }

  private void verifyPoseCrossfadeSmoke() {
    Gdx.files.local("artifacts").mkdirs();
    if (poseAnimatorA == null
        || upperLayerA == null
        || skinRendererA == null
        || skinRendererB == null)
      throw new IllegalStateException("Pose crossfade fixture was not initialized");
    List<Pixmap> captures = new java.util.ArrayList<>();
    try {
      animatorB.current().setSpeed(0);
      var secondBone = skinRendererB.instance.nodes.first().parts.first().bones[1].cpy();
      Transform3D canonical = skinRendererA.getGameObject().getComponent(Transform3D.class);
      var canonicalPosition = canonical.position.cpy();
      var canonicalRotation = canonical.rotation.cpy();
      var canonicalScale = canonical.scale.cpy();

      skinRendererB.setEnabled(false);
      Pixmap secondInstanceDisabled = renderPoseCapture(captures);
      Pixmap secondInstanceDisabledRepeat = renderPoseCapture(captures);
      int disabledNegativeControlPixels =
          differingPixels(secondInstanceDisabled, secondInstanceDisabledRepeat, 575, 800, 220, 465);
      if (disabledNegativeControlPixels != 0)
        throw new IllegalStateException(
            "Disabled second skin renderer changed its same-state ROI: "
                + disabledNegativeControlPixels);
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-pose-second-instance-disabled.png"),
          secondInstanceDisabled,
          -1,
          true);
      skinRendererB.setEnabled(true);
      Pixmap baseBefore = renderPoseCapture(captures);
      int untouchedSecondPixels =
          differingPixels(secondInstanceDisabled, baseBefore, 575, 800, 220, 465);
      if (untouchedSecondPixels < 1000)
        throw new IllegalStateException(
            "Second skin instance did not contribute visible foreground over its disabled capture: "
                + untouchedSecondPixels);
      var startBone = skinRendererA.instance.nodes.first().parts.first().bones[1].cpy();
      ModelPosePlayback firstFade =
          poseAnimatorA.play(
              AnimationTransition.clip("bend-left").loop().startAt(.5).speed(0).fade(.4));
      Pixmap baseStart = renderPoseCapture(captures);
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-pose-base-fade-start.png"), baseStart, -1, true);
      if (matrixDelta(startBone, skinRendererA.instance.nodes.first().parts.first().bones[1])
              > .0001f
          || differingPixels(baseBefore, baseStart, 350, 575, 220, 375) != 0)
        throw new IllegalStateException("Base clip fade changed the rendered pose at alpha zero");

      advancePoseSmoke(.2f);
      Pixmap baseMid = renderPoseCapture(captures);
      var baseMidBone = skinRendererA.instance.nodes.first().parts.first().bones[1].cpy();
      int baseMidPixels = differingPixels(baseBefore, baseMid, 350, 575, 220, 375);
      var baseMidLocal = skinRendererA.instance.getNode("tip", true).translation;
      if (Math.abs(firstFade.fadeProgress() - .5) > .001
          || matrixDelta(startBone, baseMidBone) < .02f
          || Math.abs(baseMidLocal.x) > .015f
          || Math.abs(baseMidLocal.y - .32f) > .015f
          || baseMidPixels < 4
          || differingPixels(baseBefore, baseMid, 575, 800, 220, 465) != 0)
        throw new IllegalStateException(
            "Base clip midpoint lacked matching bone/pixel change: progress="
                + firstFade.fadeProgress()
                + ", pixels="
                + baseMidPixels
                + ", local="
                + baseMidLocal);
      PixmapIO.writePNG(Gdx.files.local("artifacts/j-pose-base-fade-mid.png"), baseMid, -1, true);

      ModelPosePlayback interrupted =
          poseAnimatorA.play(
              AnimationTransition.clip("bend-right").loop().startAt(.25).speed(0).fade(.4));
      Pixmap interruptedStart = renderPoseCapture(captures);
      if (matrixDelta(baseMidBone, skinRendererA.instance.nodes.first().parts.first().bones[1])
              > .0001f
          || differingPixels(baseMid, interruptedStart, 350, 575, 220, 375) != 0
          || firstFade.isActive())
        throw new IllegalStateException(
            "Interrupted base fade popped or retained the outgoing clock");
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-pose-base-fade-interrupt-start.png"),
          interruptedStart,
          -1,
          true);
      advancePoseSmoke(.2f);
      Pixmap interruptedMid = renderPoseCapture(captures);
      if (matrixDelta(baseMidBone, skinRendererA.instance.nodes.first().parts.first().bones[1])
              < .01f
          || differingPixels(interruptedStart, interruptedMid, 350, 575, 220, 375) < 3)
        throw new IllegalStateException("Interrupted base fade did not continue from its snapshot");
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-pose-base-fade-interrupt-mid.png"),
          interruptedMid,
          -1,
          true);
      advancePoseSmoke(.2f);
      Pixmap baseEnd = renderPoseCapture(captures);
      PixmapIO.writePNG(Gdx.files.local("artifacts/j-pose-base-fade-end.png"), baseEnd, -1, true);
      if (interrupted.isFading())
        throw new IllegalStateException("Base fade did not reach its endpoint");
      var baseEndBone = skinRendererA.instance.nodes.first().parts.first().bones[1].cpy();
      ModelPosePlayback synchronousTarget =
          poseAnimatorA.play(AnimationTransition.clip("bend-right").loop().startAt(.25).speed(0));
      Pixmap synchronousTargetPixels = renderPoseCapture(captures);
      if (matrixDelta(baseEndBone, skinRendererA.instance.nodes.first().parts.first().bones[1])
              > .0001f
          || differingPixels(baseEnd, synchronousTargetPixels, 350, 575, 220, 375) != 0
          || synchronousTarget.isFading()
          || differingPixels(baseBefore, synchronousTargetPixels, 575, 800, 220, 465) != 0)
        throw new IllegalStateException(
            "Base fade endpoint differs from the exact synchronous target pose");

      poseAnimatorA.play(AnimationTransition.clip("bend-right").loop().startAt(.25).speed(0));
      upperLayerA.current().seekNormalized(0);
      upperLayerA.setWeight(1);
      Pixmap layerBefore = renderPoseCapture(captures);
      var layerRootBefore = skinRendererA.instance.nodes.first().parts.first().bones[0].cpy();
      var layerTipBefore = skinRendererA.instance.nodes.first().parts.first().bones[1].cpy();
      ModelPosePlayback layerFade =
          upperLayerA.play(
              AnimationTransition.clip("upper-fold").loop().startAt(.5).speed(0).fade(.4));
      Pixmap layerStart = renderPoseCapture(captures);
      if (matrixDelta(layerTipBefore, skinRendererA.instance.nodes.first().parts.first().bones[1])
              > .0001f
          || differingPixels(layerBefore, layerStart, 350, 575, 220, 375) != 0)
        throw new IllegalStateException("Masked layer fade changed pixels at alpha zero");
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-pose-layer-fade-start.png"), layerStart, -1, true);

      advancePoseSmoke(.2f);
      Pixmap layerMid = renderPoseCapture(captures);
      int upperMidPixels = differingPixels(layerStart, layerMid, 350, 575, 220, 375);
      int lowerMidPixels = differingPixels(layerStart, layerMid, 350, 575, 380, 465);
      var layerMidLocal = skinRendererA.instance.getNode("tip", true).translation;
      var pausedTip = skinRendererA.instance.nodes.first().parts.first().bones[1].cpy();
      var pausedRoot = skinRendererA.instance.nodes.first().parts.first().bones[0].cpy();
      double pausedProgress = layerFade.fadeProgress();
      if (upperMidPixels < 3
          || lowerMidPixels != 0
          || Math.abs(layerMidLocal.x - -.325f) > .02f
          || Math.abs(layerMidLocal.y) > .02f
          || matrixDelta(layerTipBefore, pausedTip) < .02f)
        throw new IllegalStateException(
            "Masked layer midpoint changed the wrong region: upper="
                + upperMidPixels
                + ", lower="
                + lowerMidPixels);
      PixmapIO.writePNG(Gdx.files.local("artifacts/j-pose-layer-fade-mid.png"), layerMid, -1, true);

      game.pause();
      Pixmap pausedA = renderPoseCapture(captures);
      double pausedTime = layerFade.time();
      advancePoseSmoke(.3);
      Pixmap pausedB = renderPoseCapture(captures);
      if (Math.abs(layerFade.fadeProgress() - pausedProgress) > .0001
          || Math.abs(layerFade.time() - pausedTime) > .0001
          || matrixDelta(pausedTip, skinRendererA.instance.nodes.first().parts.first().bones[1])
              > .0001f
          || matrixDelta(pausedRoot, skinRendererA.instance.nodes.first().parts.first().bones[0])
              > .0001f
          || differingPixels(pausedA, pausedB, 350, 575, 220, 465) != 0
          || differingPixels(baseBefore, pausedB, 575, 800, 220, 465) != 0)
        throw new IllegalStateException("WORLD pause changed pose fade matrices or pixels");
      PixmapIO.writePNG(
          Gdx.files.local("artifacts/j-pose-layer-fade-paused.png"), pausedB, -1, true);
      game.resume();

      advancePoseSmoke(.2f);
      Pixmap layerEnd = renderPoseCapture(captures);
      int upperEndPixels = differingPixels(layerMid, layerEnd, 350, 575, 220, 375);
      int lowerEndPixels = differingPixels(layerMid, layerEnd, 350, 575, 380, 465);
      if (layerFade.isFading()
          || upperEndPixels < 3
          || lowerEndPixels != 0
          || matrixDelta(
                  layerTipBefore, skinRendererA.instance.nodes.first().parts.first().bones[1])
              < .02f
          || matrixDelta(
                  layerRootBefore, skinRendererA.instance.nodes.first().parts.first().bones[0])
              > .0001f
          || Math.abs(skinRendererA.instance.getNode("tip", true).translation.x - -.65f) > .02f
          || Math.abs(skinRendererA.instance.getNode("tip", true).translation.y) > .02f
          || differingPixels(baseBefore, layerMid, 575, 800, 220, 465) != 0
          || differingPixels(baseBefore, layerEnd, 575, 800, 220, 465) != 0)
        throw new IllegalStateException(
            "Masked layer endpoint failed matrix/pixel isolation: upper="
                + upperEndPixels
                + ", lower="
                + lowerEndPixels);
      PixmapIO.writePNG(Gdx.files.local("artifacts/j-pose-layer-fade-end.png"), layerEnd, -1, true);

      if (matrixDelta(secondBone, skinRendererB.instance.nodes.first().parts.first().bones[1])
              > .0001f
          || !canonicalPosition.epsilonEquals(canonical.position, .0001f)
          || !canonicalRotation.equals(canonical.rotation)
          || !canonicalScale.epsilonEquals(canonical.scale, .0001f))
        throw new IllegalStateException(
            "Pose fade changed another instance or canonical object TRS");

      ModelPosePlayback removedDuringFade =
          upperLayerA.play(
              AnimationTransition.clip("upper-reach").loop().startAt(.5).speed(0).fade(.4));
      skinRendererA.getGameObject().removeComponent(poseAnimatorA);
      skinRendererA.prepareForRender();
      if (removedDuringFade.isActive()
          || Math.abs(skinRendererA.instance.getNode("tip", true).translation.y) > .0001f
          || skinRendererA.instance.getNode("tip", true).isAnimated)
        throw new IllegalStateException("Removed pose owner wrote stale skin state");
      Gdx.files
          .local("artifacts/j-pose-crossfade-proof.json")
          .writeString(
              "{\"baseMidPixels\": "
                  + baseMidPixels
                  + ", \"maskedMidUpperPixels\": "
                  + upperMidPixels
                  + ", \"maskedMidLowerPixels\": "
                  + lowerMidPixels
                  + ", \"maskedEndUpperPixels\": "
                  + upperEndPixels
                  + ", \"maskedEndLowerPixels\": "
                  + lowerEndPixels
                  + ", \"secondInstanceForegroundPixels\": "
                  + untouchedSecondPixels
                  + ", \"secondInstanceDisabledNegativeControlPixels\": "
                  + disabledNegativeControlPixels
                  + ", \"secondInstanceStablePixels\": true, \"endpointMatchesImmediateTarget\":"
                  + " true, \"pausedStepSeconds\": 0.3, \"instances\": 2, \"worldPauseStable\":"
                  + " true, \"canonicalTransformStable\": true, \"removedOwnerRestoredBind\":"
                  + " true}\n",
              false);
      Gdx.app.log(
          "JavityPoseCrossfadeSmoke",
          "PASS base pixels="
              + baseMidPixels
              + ", masked upper="
              + upperMidPixels
              + "/"
              + upperEndPixels
              + ", lower="
              + lowerMidPixels
              + "/"
              + lowerEndPixels);
    } finally {
      for (Pixmap capture : captures) capture.dispose();
    }
  }

  private Pixmap renderPoseCapture(List<Pixmap> captures) {
    Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.render(game);
    Pixmap image = capture();
    captures.add(image);
    return image;
  }

  private void advancePoseSmoke(double seconds) {
    while (seconds > 0) {
      float delta = (float) Math.min(.1, seconds);
      game.step(delta);
      seconds -= delta;
    }
  }

  private void verifyPixels(Pixmap pixels) {
    if (clickCount < 3 || score.get() != 3 || uiFrames < 115 || worldFrames < 70)
      throw new IllegalStateException("UI/runtime clocks did not advance through pause");
    if (sprite.region.getRegionX() % 32 != 0)
      throw new IllegalStateException("Atlas clip did not select a region");
    if (completeTween == null || !completeTween.isCompleted() || effectCompletions != 1)
      throw new IllegalStateException("Tween completion callback did not run exactly once");
    if (galleryFeedback == null || !galleryFeedback.isCompleted() || feedbackCompletions != 1)
      throw new IllegalStateException("Base feedback sequence did not complete exactly once");
    if (reactFeedback == null
        || !reactFeedback.isCompleted()
        || Math.abs(pulseShape.color.r - .95f) > .02f)
      throw new IllegalStateException("React-triggered Java feedback did not reach its endpoint");
    if (alphaTween != null && alphaTween.isRunning())
      throw new IllegalStateException("UI alpha tween did not complete");
    if (!alphaPausedPixelChanged
        || alphaPauseBefore == null
        || alphaPauseAfter == null
        || Math.abs(actorAlphaBeforePause - actorAlphaAfterPause) < .2f)
      throw new IllegalStateException("Paused UI alpha pixel evidence is missing");
    Camera2D camera = cameraObject.getComponent(Camera2D.class);
    Vector2 spriteScreen = camera.worldToScreen(690, 160);
    Color spritePixel = pixel(pixels, spriteScreen.x, spriteScreen.y);
    Color[] colors = {Color.valueOf("#fb7185"), Color.valueOf("#38bdf8"), Color.valueOf("#a3e635")};
    Color expectedSprite = colors[(sprite.region.getRegionX() / 32) % 3];
    if (Math.abs(spritePixel.r - expectedSprite.r) > .16f
        || Math.abs(spritePixel.g - expectedSprite.g) > .16f
        || Math.abs(spritePixel.b - expectedSprite.b) > .16f)
      throw new IllegalStateException(
          "Animated atlas frame pixel mismatch: actual="
              + spritePixel
              + ", expected="
              + expectedSprite);
    if (mixerMidpointPixelDelta < 12 || mixerEndpointPixelDelta < 12)
      throw new IllegalStateException(
          "1D mixer endpoint/midpoint did not change same-viewport skin pixels: midpoint="
              + mixerMidpointPixelDelta
              + ", endpoint="
              + mixerEndpointPixelDelta);
    if (maskedHalfPixelDelta < 4 || maskedUpperPixelDelta < 12 || maskedLowerPixelDelta != 0)
      throw new IllegalStateException(
          "Masked skin action changed the wrong pixels: half="
              + maskedHalfPixelDelta
              + ", upper="
              + maskedUpperPixelDelta
              + ", lower="
              + maskedLowerPixelDelta);
    if (classicSeekPixelDelta < 12 || classicMarkerCalls < 1)
      throw new IllegalStateException(
          "Classic ModelAnimator seek/marker native proof failed: pixels="
              + classicSeekPixelDelta
              + ", markers="
              + classicMarkerCalls);
    if (pixels.getWidth() < 900)
      throw new IllegalStateException("Resize did not reach framebuffer");
  }

  private float matrixDelta(
      com.badlogic.gdx.math.Matrix4 before, com.badlogic.gdx.math.Matrix4 after) {
    float max = 0;
    for (int i = 0; i < 16; i++) max = Math.max(max, Math.abs(before.val[i] - after.val[i]));
    return max;
  }

  private int differingPixels(
      Pixmap before, Pixmap after, int left, int right, int top, int bottom) {
    int different = 0;
    int imageBottom = Math.max(0, after.getHeight() - bottom);
    int imageTop = Math.min(after.getHeight(), after.getHeight() - top);
    for (int y = imageBottom; y < imageTop; y++)
      for (int x = Math.max(0, left); x < Math.min(after.getWidth(), right); x++)
        if (before.getPixel(x, y) != after.getPixel(x, y)) different++;
    return different;
  }

  private Pixmap capture() {
    return Pixmap.createFromFrameBuffer(
        0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
  }

  private static Color pixel(Pixmap image, float x, float yTop) {
    int px = Math.max(0, Math.min(image.getWidth() - 1, Math.round(x)));
    int py = image.getHeight() - 1 - Math.max(0, Math.min(image.getHeight() - 1, Math.round(yTop)));
    return new Color(image.getPixel(px, py));
  }

  @Override
  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) return;
    if (renderer != null) renderer.resize(width, height);
    if (cameraObject != null) {
      Camera2D camera = cameraObject.getComponent(Camera2D.class);
      if (camera != null) {
        camera.resize(width, height);
        cameraObject.setPosition(width / 2f, height / 2f);
      }
      Camera3D camera3 = cameraObject.getComponent(Camera3D.class);
      if (camera3 != null) {
        camera3.camera.viewportWidth = width;
        camera3.camera.viewportHeight = height;
      }
    }
    if (stage != null) {
      stage.getViewport().update(width, height, true);
      if (reactDocument != null) reactDocument.root().setPosition(width - 345, height - 198);
    }
  }

  @Override
  public void dispose() {
    Throwable[] cleanupFailure = {null};
    cleanup(
        () -> {
          if (runtime != null) runtime.close();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (reactDocument != null) reactDocument.close();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (nativeDocument != null) nativeDocument.close();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (stage != null) stage.dispose();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (game != null) game.dispose();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (poseBaseEndpoint0 != null) poseBaseEndpoint0.dispose();
          if (poseBaseMidpoint != null) poseBaseMidpoint.dispose();
          if (poseBaseEndpoint1 != null) poseBaseEndpoint1.dispose();
          if (poseMaskHalf != null) poseMaskHalf.dispose();
          if (classicBeforePixels != null) classicBeforePixels.dispose();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (skin != null) skin.dispose();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (atlas != null) atlas.dispose();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (theme != null) theme.dispose();
        },
        cleanupFailure);
    cleanup(
        () -> {
          if (renderer != null) renderer.dispose();
        },
        cleanupFailure);
    if (Gdx.gl != null) {
      int gl = Gdx.gl.glGetError();
      if (gl != GL20.GL_NO_ERROR) {
        IllegalStateException glFailure =
            new IllegalStateException(
                "Plugins smoke post-disposal OpenGL error 0x" + Integer.toHexString(gl));
        if (cleanupFailure[0] == null) cleanupFailure[0] = glFailure;
        else cleanupFailure[0].addSuppressed(glFailure);
      } else if (smoke) {
        Gdx.app.log("JavityPluginsSmoke", "DISPOSE PASS GL_NO_ERROR after GPU resource disposal");
      }
    }
    if (cleanupFailure[0] != null)
      throw new IllegalStateException("Plugins demo cleanup failed", cleanupFailure[0]);
  }

  private static void cleanup(Runnable action, Throwable[] failure) {
    try {
      action.run();
    } catch (Throwable thrown) {
      if (failure[0] == null) failure[0] = thrown;
      else failure[0].addSuppressed(thrown);
    }
  }

  private String readResource(String path) {
    try (var stream = PluginsDemo.class.getResourceAsStream(path)) {
      if (stream == null) throw new IllegalStateException("Missing demo resource " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }

  private String reactSampleMount() {
    return "mountPluginsGallery(() => { JavityGame.call('claim'); globalThis.bridgeValue ="
        + " JavityGame.get('score'); })";
  }

  private void verifySettingsControls(InputProcessor processor) {
    verifyK1VisualComparison();
    Actor toggle = controlsDocument.actor("settings-muted");
    Actor slider = controlsDocument.actor("settings-volume");
    Actor disabledToggle = controlsDocument.actor("settings-disabled");
    if (toggle == null
        || slider == null
        || disabledToggle == null
        || !toggle.getClass().getSimpleName().equals("Toggle")
        || !slider.getClass().getSimpleName().equals("Slider"))
      throw new IllegalStateException("React settings did not create native Toggle/Slider actors");
    stage.setKeyboardFocus(null);
    stage.act(0);
    stage.draw();
    if (!processor.keyDown(Input.Keys.TAB) || stage.getKeyboardFocus() != toggle)
      throw new IllegalStateException("Tab did not focus the first enabled control");
    if (!processor.keyDown(Input.Keys.SPACE))
      throw new IllegalStateException("Space did not activate the focused Toggle");
    processor.keyDown(Input.Keys.SPACE); // held-key repeat must not activate twice
    processor.keyUp(Input.Keys.SPACE);
    if (!recordedUiMuted
        || muteActionCalls != 1
        || controlsDocument.actor("settings-muted") != toggle)
      throw new IllegalStateException("Controlled Toggle/action did not commit exactly once");
    if (!processor.keyDown(Input.Keys.TAB) || stage.getKeyboardFocus() != slider)
      throw new IllegalStateException("Tab did not move to the Slider in tree order");
    if (!processor.keyDown(Input.Keys.RIGHT)
        || volumeActionCalls != 1
        || Math.abs(recordedUiVolume - .6f) > .001f
        || controlsDocument.actor("settings-volume") != slider)
      throw new IllegalStateException("ArrowRight did not step the controlled Slider/action");
    processor.keyUp(Input.Keys.RIGHT);
    if (!processor.keyDown(Input.Keys.LEFT) || volumeActionCalls != 1)
      throw new IllegalStateException("A consumed onKeyDown did not prevent Slider's default step");
    processor.keyUp(Input.Keys.LEFT);
    controlsRuntime.eval(
        "if(!Object.isFrozen(lastControlKey)||Object.getPrototypeOf(lastControlKey)!==Object.prototype||lastControlKey.key!=='ArrowLeft')throw"
            + " new Error('key event was not a frozen plain JS object')");
    processor.keyUp(Input.Keys.TAB);
    processor.keyDown(Input.Keys.SHIFT_LEFT);
    if (!processor.keyDown(Input.Keys.TAB) || stage.getKeyboardFocus() != toggle)
      throw new IllegalStateException("Shift+Tab did not cycle backward");
    controlsRuntime.eval(
        "if(lastControlKey.key!=='Tab'||lastControlKey.shift!==true)throw new Error('Shift modifier"
            + " was not tracked from Stage events')");
    processor.keyUp(Input.Keys.TAB);
    processor.keyUp(Input.Keys.SHIFT_LEFT);
    stage.setKeyboardFocus(toggle);
    processor.keyDown(Input.Keys.SHIFT_LEFT);
    processor.keyDown(Input.Keys.SHIFT_RIGHT);
    processor.keyUp(Input.Keys.SHIFT_LEFT);
    controlsRuntime.eval(
        "if(!lastControlKeyUp||lastControlKeyUp.shift!==true)throw new Error('releasing one Shift"
            + " lost the other held modifier')");
    processor.keyUp(Input.Keys.SHIFT_RIGHT);
    controlsRuntime.eval(
        "if(lastControlKeyUp.shift!==false)throw new Error('Shift keyup retained a released"
            + " modifier')");
    int beforeDisabled = muteActionCalls;
    stage.setKeyboardFocus(disabledToggle);
    processor.keyDown(Input.Keys.SPACE);
    processor.keyUp(Input.Keys.SPACE);
    if (muteActionCalls != beforeDisabled)
      throw new IllegalStateException("Disabled Toggle activated from keyboard");
    stage.setKeyboardFocus(null);
    controlsRuntime.eval(
        "const React=JavityReact.React;function RejectedControlProbe(){return"
            + " React.createElement(React.Fragment,null,React.createElement(JavityReact.Toggle,{id:'rejected-toggle',checked:false,onChange:next=>{globalThis.rejectedToggle=next;globalThis.rejectedToggleCalls=(globalThis.rejectedToggleCalls||0)+1}}),React.createElement(JavityReact.Slider,{id:'rejected-slider',min:0,max:1,value:.5,step:.1,onChange:next=>{globalThis.rejectedSlider=next;globalThis.rejectedSliderCalls=(globalThis.rejectedSliderCalls||0)+1}}))};JavityReact.render(React.createElement(RejectedControlProbe))");
    Actor rejectedToggle = controlsDocument.actor("rejected-toggle");
    Actor rejectedSlider = controlsDocument.actor("rejected-slider");
    stage.setKeyboardFocus(rejectedToggle);
    processor.keyDown(Input.Keys.SPACE);
    processor.keyUp(Input.Keys.SPACE);
    if (controlsDocument.actor("rejected-toggle") != rejectedToggle
        || ((org.javity.ui.controls.Toggle) rejectedToggle).isChecked())
      throw new IllegalStateException("Rejected controlled Toggle change did not restore its prop");
    controlsRuntime.eval(
        "if(rejectedToggle!==true)throw new Error('rejected Toggle did not receive its primitive"
            + " event')");
    stage.setKeyboardFocus(rejectedSlider);
    processor.keyDown(Input.Keys.RIGHT);
    processor.keyUp(Input.Keys.RIGHT);
    if (controlsDocument.actor("rejected-slider") != rejectedSlider
        || Math.abs(((org.javity.ui.controls.Slider) rejectedSlider).getValue() - .5f) > .001f)
      throw new IllegalStateException("Rejected controlled Slider change did not restore its prop");
    controlsRuntime.eval(
        "if(Math.abs(rejectedSlider-.6)>.001)throw new Error('rejected Slider did not receive its"
            + " primitive event')");
    controlsRuntime.eval("mountSettingsControls()");
    if (stage.getKeyboardFocus() != null)
      throw new IllegalStateException("Removing focused controls left stale Stage keyboard focus");
    ((org.javity.ui.controls.Toggle) rejectedToggle).activate();
    ((org.javity.ui.controls.Slider) rejectedSlider).stepBy(1);
    controlsRuntime.eval(
        "if(rejectedToggleCalls!==1||rejectedSliderCalls!==1)throw new Error('removed native"
            + " control retained its callback')");
    toggle = controlsDocument.actor("settings-muted");
    slider = controlsDocument.actor("settings-volume");
    Actor controlledField = reactDocument.actor("react-controlled-field");
    if (controlledField instanceof TextField textField) {
      stage.setKeyboardFocus(textField);
      textField.setCursorPosition(textField.getText().length());
      processor.keyDown(Input.Keys.LEFT);
      if (textField.getCursorPosition() != textField.getText().length() - 1)
        throw new IllegalStateException("TextField cursor arrow was intercepted by UI navigation");
      processor.keyUp(Input.Keys.LEFT);
      var focusedClip = clips.current();
      double focusedClipSpeed = focusedClip.speed();
      processor.keyDown(Input.Keys.SPACE);
      processor.keyUp(Input.Keys.SPACE);
      if (clips.current() != focusedClip || Math.abs(focusedClip.speed() - focusedClipSpeed) > .001)
        throw new IllegalStateException("Focused TextField leaked SPACE to the gameplay hotkey");
    }
    stage.setKeyboardFocus(null);
    verifyBlurCallbackClose();
    stage.setKeyboardFocus(slider);
    stage.act(0);
    stage.draw();
    Pixmap pixels = capture();
    try {
      PixmapIO.writePNG(Gdx.files.local("artifacts/k1-controls-native.png"), pixels, -1, true);
      if (toggle.getWidth() < 40 || slider.getWidth() < 180)
        throw new IllegalStateException("Styled Toggle/Slider Yoga geometry was not applied");
    } finally {
      pixels.dispose();
    }
  }

  private void verifyK1VisualComparison() {
    JsonValue fixture =
        new JsonReader()
            .parse(Gdx.files.classpath("org/javity/demo/ui-reference/k1-controls-fixture.json"));
    JsonValue panel = fixture.get("panel");
    JsonValue rect = panel.get("rect");
    int width = fixture.get("viewport").getInt("width");
    int height = fixture.get("viewport").getInt("height");
    if (Gdx.graphics.getWidth() != width
        || Gdx.graphics.getHeight() != height
        || Gdx.graphics.getBackBufferWidth() != width
        || Gdx.graphics.getBackBufferHeight() != height)
      throw new IllegalStateException(
          "K1 visual proof requires the authored 1100x720 DPR1 surface");
    UiDocument visualDocument = new UiDocument(theme);
    Stage visualStage = new Stage(new ScreenViewport());
    try {
      visualStage.getViewport().update(width, height, true);
      visualDocument.render(k1Node(panel));
      visualDocument.mount(visualStage);
      visualDocument.root().setSize(rect.getFloat("width"), rect.getFloat("height"));
      visualDocument
          .root()
          .setPosition(rect.getFloat("x"), height - rect.getFloat("y") - rect.getFloat("height"));
      String[] states = {"idle", "slider-focus", "toggle-hover", "disabled"};
      for (String state : states) {
        visualStage.setKeyboardFocus(null);
        visualStage.mouseMoved(width - 20, height - 20);
        if (state.equals("slider-focus"))
          visualStage.setKeyboardFocus(visualDocument.actor("k1-slider"));
        if (state.equals("toggle-hover")) {
          Actor toggle = visualDocument.actor("k1-toggle");
          Vector2 point =
              toggle.localToStageCoordinates(
                  new Vector2(toggle.getWidth() / 2, toggle.getHeight() / 2));
          visualStage.mouseMoved(Math.round(point.x), height - Math.round(point.y));
        }
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glClearColor(15f / 255f, 23f / 255f, 42f / 255f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        visualStage.act(0);
        visualStage.draw();
        Pixmap image = capture();
        try {
          PixmapIO.writePNG(
              Gdx.files.local("artifacts/k1-fix1-native-" + state + ".png"), image, -1, true);
        } finally {
          image.dispose();
        }
      }
      StringBuilder metrics =
          new StringBuilder("{\"viewport\":{\"width\":")
              .append(width)
              .append(",\"height\":")
              .append(height)
              .append(",\"dpr\":1},\"font\":\"Open Sans\",\"states\":{}");
      metrics.setLength(metrics.length() - 1);
      for (int stateIndex = 0; stateIndex < states.length; stateIndex++) {
        String state = states[stateIndex];
        visualStage.setKeyboardFocus(null);
        visualStage.mouseMoved(width - 20, height - 20);
        if (state.equals("slider-focus"))
          visualStage.setKeyboardFocus(visualDocument.actor("k1-slider"));
        if (state.equals("toggle-hover")) {
          Actor toggle = visualDocument.actor("k1-toggle");
          Vector2 point =
              toggle.localToStageCoordinates(
                  new Vector2(toggle.getWidth() / 2, toggle.getHeight() / 2));
          visualStage.mouseMoved(Math.round(point.x), height - Math.round(point.y));
        }
        visualStage.act(0);
        visualStage.draw();
        if (stateIndex > 0) metrics.append(',');
        metrics.append('"').append(state).append("\":{");
        String[] ids = {
          "k1-panel",
          "k1-title",
          "k1-toggle",
          "k1-volume-label",
          "k1-slider",
          "k1-disabled-toggle",
          "k1-hint"
        };
        for (int i = 0; i < ids.length; i++) {
          if (i > 0) metrics.append(',');
          Actor actor = visualDocument.actor(ids[i]);
          Vector2 actorOrigin = actor.localToStageCoordinates(new Vector2());
          metrics
              .append('"')
              .append(ids[i])
              .append("\":{\"x\":")
              .append(round2(actorOrigin.x))
              .append(",\"y\":")
              .append(round2(height - actorOrigin.y - actor.getHeight()))
              .append(",\"width\":")
              .append(round2(actor.getWidth()))
              .append(",\"height\":")
              .append(round2(actor.getHeight()))
              .append('}');
        }
        Actor toggleShape =
            visualDocument.actor(state.equals("disabled") ? "k1-disabled-toggle" : "k1-toggle");
        Vector2 toggleOrigin = toggleShape.localToStageCoordinates(new Vector2());
        float toggleX = toggleOrigin.x;
        float toggleY = height - toggleOrigin.y - toggleShape.getHeight();
        float toggleTrackHeight = Math.min(toggleShape.getHeight() * .62f, 24f);
        float toggleTrackWidth = Math.min(toggleShape.getWidth(), toggleTrackHeight * 1.75f);
        float toggleTrackX = toggleX + (toggleShape.getWidth() - toggleTrackWidth) / 2f;
        float toggleTrackY = toggleY + (toggleShape.getHeight() - toggleTrackHeight) / 2f;
        float toggleKnobSize = Math.max(2f, toggleTrackHeight - 4f);
        boolean toggleChecked = ((org.javity.ui.controls.Toggle) toggleShape).isChecked();
        float toggleKnobX =
            toggleChecked
                ? toggleTrackX + toggleTrackWidth - toggleKnobSize - 2f
                : toggleTrackX + 2f;
        Actor sliderShape = visualDocument.actor("k1-slider");
        Vector2 sliderOrigin = sliderShape.localToStageCoordinates(new Vector2());
        float sliderX = sliderOrigin.x;
        float sliderY = height - sliderOrigin.y - sliderShape.getHeight();
        float sliderKnobSize = Math.min(18f, Math.max(10f, sliderShape.getHeight() * .72f));
        float sliderUsable = Math.max(0, sliderShape.getWidth() - sliderKnobSize);
        float sliderRatio = .6f;
        metrics
            .append(",\"controlShapes\":{")
            .append("\"toggleTrack\":")
            .append(rectJson(toggleTrackX, toggleTrackY, toggleTrackWidth, toggleTrackHeight))
            .append(",\"toggleKnob\":")
            .append(rectJson(toggleKnobX, toggleTrackY + 2f, toggleKnobSize, toggleKnobSize))
            .append(",\"sliderRail\":")
            .append(
                rectJson(
                    sliderX + sliderKnobSize / 2f,
                    sliderY + (sliderShape.getHeight() - 6f) / 2f,
                    Math.max(0, sliderShape.getWidth() - sliderKnobSize),
                    6f))
            .append(",\"sliderFill\":")
            .append(
                rectJson(
                    sliderX + sliderKnobSize / 2f,
                    sliderY + (sliderShape.getHeight() - 6f) / 2f,
                    sliderUsable * sliderRatio,
                    6f))
            .append(",\"sliderKnob\":")
            .append(
                rectJson(
                    sliderX + sliderUsable * sliderRatio,
                    sliderY + (sliderShape.getHeight() - sliderKnobSize) / 2f,
                    sliderKnobSize,
                    sliderKnobSize))
            .append('}');
        metrics.append('}');
      }
      metrics.append("}}\n");
      Gdx.files
          .local("artifacts/k1-fix1-native-metrics.json")
          .writeString(metrics.toString(), false);
      org.javity.ui.controls.Slider opacityProbe =
          new org.javity.ui.controls.Slider(theme, "opacity-50");
      opacityProbe.setRange(0, 1);
      opacityProbe.setStep(.1f);
      opacityProbe.setValue(.6f);
      opacityProbe.setSize(220, 32);
      opacityProbe.setPosition(400, 600);
      org.javity.ui.controls.Toggle opacityToggle =
          new org.javity.ui.controls.Toggle(theme, "opacity-50");
      opacityToggle.setChecked(true);
      opacityToggle.setSize(52, 32);
      opacityToggle.setPosition(700, 600);
      org.javity.ui.controls.Toggle opaqueToggle = new org.javity.ui.controls.Toggle(theme, "");
      opaqueToggle.setChecked(true);
      opaqueToggle.setSize(52, 32);
      opaqueToggle.setPosition(700, 540);
      org.javity.ui.controls.Slider opaqueProbe = new org.javity.ui.controls.Slider(theme, "");
      opaqueProbe.setRange(0, 1);
      opaqueProbe.setStep(.1f);
      opaqueProbe.setValue(.6f);
      opaqueProbe.setSize(220, 32);
      opaqueProbe.setPosition(400, 540);
      visualStage.addActor(opacityProbe);
      visualStage.addActor(opacityToggle);
      visualStage.addActor(opaqueProbe);
      visualStage.addActor(opaqueToggle);
      Gdx.gl.glClearColor(15f / 255f, 23f / 255f, 42f / 255f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      visualStage.act(0);
      visualStage.draw();
      if (Math.abs(Color.WHITE.a - 1f) > .0001f)
        throw new IllegalStateException("Opacity styling mutated the shared Color.WHITE constant");
      Pixmap opacityImage = capture();
      try {
        Color opaqueThumb = pixel(opacityImage, 530, height - 556);
        if (opaqueThumb.r < .98f || opaqueThumb.g < .98f || opaqueThumb.b < .98f)
          throw new IllegalStateException(
              "An opacity-styled Slider dimmed the next default Slider thumb: " + opaqueThumb);
        int[] background = {15, 23, 42};
        Map<String, int[][]> samples =
            Map.of(
                "sliderRail",
                    new int[][] {
                      rgb(pixel(opacityImage, 590, height - 616)),
                      rgb(pixel(opacityImage, 590, height - 556))
                    },
                "sliderFill",
                    new int[][] {
                      rgb(pixel(opacityImage, 470, height - 616)),
                      rgb(pixel(opacityImage, 470, height - 556))
                    },
                "sliderKnob",
                    new int[][] {
                      rgb(pixel(opacityImage, 530, height - 616)),
                      rgb(pixel(opacityImage, 530, height - 556))
                    },
                "toggleTrack",
                    new int[][] {
                      rgb(pixel(opacityImage, 712, height - 616)),
                      rgb(pixel(opacityImage, 712, height - 556))
                    },
                "toggleKnob",
                    new int[][] {
                      rgb(pixel(opacityImage, 733, height - 616)),
                      rgb(pixel(opacityImage, 733, height - 556))
                    });
        StringBuilder opacityMetrics =
            new StringBuilder("{\"alpha\":0.5,\"clearRgb\":[15,23,42],\"samples\":{");
        int sampleIndex = 0;
        for (var sample : samples.entrySet()) {
          int[] opacityRgb = sample.getValue()[0];
          int[] opaqueRgb = sample.getValue()[1];
          int maxChannelDelta = 0;
          int opaqueBackgroundDistance = 0;
          int opacityBackgroundDistance = 0;
          for (int channel = 0; channel < 3; channel++) {
            maxChannelDelta =
                Math.max(maxChannelDelta, Math.abs(opacityRgb[channel] - opaqueRgb[channel]));
            opaqueBackgroundDistance =
                Math.max(
                    opaqueBackgroundDistance, Math.abs(opaqueRgb[channel] - background[channel]));
            opacityBackgroundDistance =
                Math.max(
                    opacityBackgroundDistance, Math.abs(opacityRgb[channel] - background[channel]));
          }
          if (maxChannelDelta < 10 || opacityBackgroundDistance >= opaqueBackgroundDistance)
            throw new IllegalStateException(
                "opacity-50 did not visibly fade the native "
                    + sample.getKey()
                    + ": opacity="
                    + java.util.Arrays.toString(opacityRgb)
                    + ", opaque="
                    + java.util.Arrays.toString(opaqueRgb)
                    + ", clear="
                    + java.util.Arrays.toString(background));
          if (sampleIndex++ > 0) opacityMetrics.append(',');
          opacityMetrics
              .append('"')
              .append(sample.getKey())
              .append("\":{\"opacityRgb\":")
              .append(java.util.Arrays.toString(opacityRgb))
              .append(",\"opaqueRgb\":")
              .append(java.util.Arrays.toString(opaqueRgb))
              .append(",\"maxChannelDelta\":")
              .append(maxChannelDelta)
              .append(",\"opacityBackgroundDistance\":")
              .append(opacityBackgroundDistance)
              .append(",\"opaqueBackgroundDistance\":")
              .append(opaqueBackgroundDistance)
              .append('}');
        }
        opacityMetrics.append("}}\n");
        Gdx.files
            .local("artifacts/k1-fix1-opacity-comparison.json")
            .writeString(opacityMetrics.toString(), false);
        PixmapIO.writePNG(
            Gdx.files.local("artifacts/k1-fix1-native-opacity-probe.png"), opacityImage, -1, true);
      } finally {
        opacityImage.dispose();
      }
    } finally {
      visualDocument.close();
      visualStage.dispose();
    }
    Gdx.gl.glClearColor(.025f, .035f, .06f, 1);
  }

  private UiNode k1Node(JsonValue source) {
    Map<String, Object> props = new java.util.LinkedHashMap<>();
    JsonValue text = source.get("text");
    if (text != null) props.put("text", text.asString());
    JsonValue checked = source.get("checked");
    if (checked != null) props.put("checked", checked.asBoolean());
    JsonValue disabled = source.get("disabled");
    if (disabled != null) props.put("disabled", disabled.asBoolean());
    for (String name : List.of("min", "max", "value", "step")) {
      JsonValue value = source.get(name);
      if (value != null) props.put(name, value.asDouble());
    }
    List<UiNode> children = new java.util.ArrayList<>();
    JsonValue child = source.get("children");
    if (child != null) for (JsonValue item : child) children.add(k1Node(item));
    return UiNode.element(
        source.getString("type"),
        source.getString("id"),
        source.getString("classes", ""),
        props,
        children);
  }

  private static float round2(float value) {
    return Math.round(value * 100f) / 100f;
  }

  private static int[] rgb(Color color) {
    return new int[] {
      Math.round(color.r * 255), Math.round(color.g * 255), Math.round(color.b * 255)
    };
  }

  private static String rectJson(float x, float y, float width, float height) {
    return "{\"x\":"
        + round2(x)
        + ",\"y\":"
        + round2(y)
        + ",\"width\":"
        + round2(width)
        + ",\"height\":"
        + round2(height)
        + "}";
  }

  private void verifyBlurCallbackClose() {
    UiDocument document = new UiDocument(theme);
    document.render(
        UiNode.element(
            "Button",
            "blur-close",
            "",
            Map.of("text", "Close on blur", "onBlur", (Runnable) document::close),
            List.of()));
    document.mount(stage);
    Actor button = document.actor("blur-close");
    stage.setKeyboardFocus(button);
    stage.setKeyboardFocus(null);
    if (!document.isClosed() || button.getParent() != null || stage.getKeyboardFocus() != null)
      throw new IllegalStateException("onBlur could not close and unmount its document safely");
    document.close();
  }

  private void verifyReactCloseReentrancy() {
    runtime.bindAction("closeRuntime", runtime::close);
    runtime.eval(
        "const React=JavityReact.React;"
            + "function CloseProbe(){const [count,setCount]=React.useState(0);"
            + "return React.createElement(JavityReact.Button,{id:'close-probe',onClick:()=>{"
            + "JavityGame.call('closeRuntime');setCount(count+1)}},'Close '+count)};"
            + "JavityReact.render(React.createElement(CloseProbe))");
    Actor closeButton = reactDocument.actor("close-probe");
    if (!(closeButton instanceof TextButton))
      throw new IllegalStateException("React close proof did not create a native Button");
    stage.act(0);
    stage.draw();
    click(closeButton);
    if (!runtime.isClosed()
        || reactDocument.actor("close-probe") != null
        || reactDocument.actor("javity-script-empty") == null)
      throw new IllegalStateException(
          "React close probe failed: closed="
              + runtime.isClosed()
              + ", closeActor="
              + reactDocument.actor("close-probe")
              + ", emptyActor="
              + reactDocument.actor("javity-script-empty"));
  }
}
