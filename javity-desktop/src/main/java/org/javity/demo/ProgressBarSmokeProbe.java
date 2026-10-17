package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.core.Game;
import org.javity.script.ScriptUiRuntime;
import org.javity.signals.Radio;
import org.javity.ui.controls.ProgressBar;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.flex.UiTheme;
import org.javity.ui.flex.UtilityStyles;

/** Real framebuffer and unscaled-Stage proof for the C3 progress component. */
final class ProgressBarSmokeProbe {
  private static final String STYLE = "rounded-full border-2 border-slate-400";

  private ProgressBarSmokeProbe() {}

  static void run(UiTheme borrowedTheme) {
    ScreenViewport viewport = new ScreenViewport();
    viewport.setUnitsPerPixel(1);
    Stage uiStage = new Stage(viewport);
    Game world = new Game();
    ProgressBar gain = new ProgressBar(borrowedTheme, STYLE);
    ProgressBar loss = new ProgressBar(borrowedTheme, STYLE);
    ProgressBar instant = new ProgressBar(borrowedTheme, STYLE);
    AtomicInteger gainCompletions = new AtomicInteger();
    AtomicInteger lossCompletions = new AtomicInteger();
    try {
      world.start();
      world.pause();
      int width = Gdx.graphics.getWidth();
      float barWidth = Math.min(260, width - 80);
      float x = Math.max(20, (width - barWidth) / 2);
      gain.setBounds(x, 90, barWidth, 28);
      loss.setBounds(x, 50, barWidth, 28);
      instant.setBounds(x, 10, barWidth, 28);
      gain.setRange(0, 100)
          .setDuration(1)
          .setDelay(.25f)
          .setOnComplete(gainCompletions::incrementAndGet);
      loss.setRange(0, 100)
          .setValue(100)
          .setDuration(1)
          .setDelay(.25f)
          .setOnComplete(lossCompletions::incrementAndGet);
      instant.setRange(0, 100).setValue(50);
      gain.setValue(100);
      loss.setValue(0);
      uiStage.addActor(gain);
      uiStage.addActor(loss);
      uiStage.addActor(instant);
      viewport.update(width, Gdx.graphics.getHeight(), true);

      float beforeGain = gain.getDisplayedValue();
      double worldElapsed = world.elapsed;
      double uiElapsed = world.unscaledElapsed;
      for (int i = 0; i < 5; i++) {
        world.step(.05f);
        uiStage.act(world.unscaledDeltaTime);
      }
      if (!world.isPaused()
          || world.elapsed != worldElapsed
          || !(world.unscaledElapsed > uiElapsed)
          || !(gain.getDelayedIncrease() > beforeGain)
          || !(loss.getDisplayedValue() < loss.getDelayedDecrease()))
        throw new IllegalStateException(
            "Progress animation did not follow the unscaled UI clock while WORLD was paused");

      Gdx.gl.glViewport(
          0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      Gdx.gl.glClearColor(.02f, .03f, .04f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      uiStage.draw();
      Pixmap pixels =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        var output = Gdx.files.local("artifacts/progress-bars-native.png");
        output.parent().mkdirs();
        PixmapIO.writePNG(output, pixels, -1, true);
        Color cyan = UtilityStyles.color("cyan-400");
        Color gainTrail = UtilityStyles.color("emerald-500");
        Color amber = UtilityStyles.color("amber-400");
        Color gainLeader = pixelAtStage(pixels, x + barWidth * .12f, 104);
        Color gainTrack = pixelAtStage(pixels, x + barWidth * .8f, 104);
        Color lossForeground = pixelAtStage(pixels, x + barWidth * .5f, 64);
        Color lossTrail = pixelAtStage(pixels, x + barWidth * .9f, 64);
        Color instantFill = pixelAtStage(pixels, x + barWidth * .25f, 24);
        Color instantTrack = pixelAtStage(pixels, x + barWidth * .75f, 24);
        requireColor("gain leader", gainLeader, gainTrail);
        requireColor("gain track", gainTrack, new Color(.15f, .18f, .22f, 1));
        requireColor("loss foreground", lossForeground, cyan);
        requireColor("loss delayed trail", lossTrail, amber);
        requireColor("instant fill", instantFill, cyan);
        requireColor("instant track", instantTrack, new Color(.15f, .18f, .22f, 1));
      } finally {
        pixels.dispose();
      }

      // Once the delayed lead has moved past the foreground, the primary fill must remain cyan.
      // Probe both regions in the real framebuffer so the earlier lead-color check cannot mask
      // a foreground tint regression.
      for (int i = 0; i < 8; i++) {
        world.step(.05f);
        uiStage.act(world.unscaledDeltaTime);
      }
      Gdx.gl.glViewport(
          0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      Gdx.gl.glClearColor(.02f, .03f, .04f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      uiStage.draw();
      Pixmap laterGain =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        requireColor(
            "stable gain foreground",
            pixelAtStage(laterGain, x + barWidth * .12f, 104),
            UtilityStyles.color("cyan-400"));
        requireColor(
            "delayed gain tint",
            pixelAtStage(laterGain, x + barWidth * .52f, 104),
            UtilityStyles.color("emerald-500"));
      } finally {
        laterGain.dispose();
      }

      loss.close();
      gain.setValue(100);
      for (int i = 0; i < 20; i++) {
        world.step(.05f);
        uiStage.act(world.unscaledDeltaTime);
      }
      if (gainCompletions.get() != 1 || gain.isAnimating())
        throw new IllegalStateException("Gain completion did not run exactly once");
      for (int i = 0; i < 20; i++) {
        world.step(.05f);
        uiStage.act(world.unscaledDeltaTime);
      }
      if (lossCompletions.get() != 0)
        throw new IllegalStateException(
            "Closing an owned progress bar did not suppress its callback");
    } finally {
      gain.close();
      loss.close();
      instant.close();
      uiStage.dispose();
      world.close();
    }
    runReactHost(borrowedTheme);
  }

  private static void runReactHost(UiTheme borrowedTheme) {
    ScreenViewport viewport = new ScreenViewport();
    viewport.setUnitsPerPixel(1);
    Stage uiStage = new Stage(viewport);
    Game world = new Game();
    UiDocument document = new UiDocument(borrowedTheme);
    Radio radio = new Radio();
    var gainSignal = radio.channel("gain", Integer.class, 0);
    var lossSignal = radio.channel("loss", Integer.class, 100);
    var pulse = radio.channel("pulse", Integer.class, 0);
    AtomicInteger oldPeerCalls = new AtomicInteger();
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.bindSignal("gain", gainSignal);
      runtime.bindSignal("loss", lossSignal);
      runtime.bindSignal("pulse", pulse);
      runtime.bindAction("publish", () -> pulse.set(1));
      runtime.bindAction("peer", oldPeerCalls::incrementAndGet);
      runtime.bindAction(
          "reload",
          () ->
              runtime.reload(
                  "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'fresh-view',className:'flex-col"
                      + " w-[200px] h-[100px]'}))"));
      int width = Gdx.graphics.getWidth();
      viewport.update(width, Gdx.graphics.getHeight(), true);
      document.root().setFillParent(true);
      uiStage.addActor(document.root());
      world.start();
      world.pause();

      runtime.eval(
          "const React=JavityReact.React;function Bars(){const"
              + " [gain,setGain]=React.useState(()=>JavityGame.get('gain'));const"
              + " [loss,setLoss]=React.useState(()=>JavityGame.get('loss'));const"
              + " [moved,setMoved]=React.useState(false);globalThis.moveGain=()=>setMoved(true);React.useEffect(()=>{const"
              + " a=JavityGame.subscribe('gain',setGain);const"
              + " b=JavityGame.subscribe('loss',setLoss);"
              + "return()=>{JavityGame.unsubscribe(a);JavityGame.unsubscribe(b)}},[]);const"
              + " gainBar=React.createElement(JavityReact.Progress,{id:'react-gain',key:'react-gain',className:'w-[260px]"
              + " h-[28px] rounded-full border-2 border-slate-400"
              + " text-[#ff00ff]',min:0,max:100,value:gain,duration:1,delay:.25});return"
              + " React.createElement(JavityReact.View,{id:'react-bars',className:'flex-col"
              + " gap-[12px]"
              + " p-3'},React.createElement(JavityReact.View,{id:'gain-slot-a',className:'flex-col'},moved?null:gainBar),React.createElement(JavityReact.View,{id:'gain-slot-b',className:'flex-col'},moved?gainBar:null),React.createElement(JavityReact.Progress,{id:'react-loss',className:'w-[260px]"
              + " h-[28px] rounded-full border-2"
              + " border-slate-400',min:0,max:100,value:loss,duration:1,delay:.25}),React.createElement(JavityReact.Progress,{id:'react-instant',className:'w-[260px]"
              + " h-[28px] rounded-full border-2 border-slate-400"
              + " text-[#ff00ff]',min:0,max:100,value:50,duration:0,delay:0}),"
              + "React.createElement(JavityReact.Button,{id:'publish-button',text:'Publish',onClick:()=>JavityGame.call('publish')}))}"
              + "JavityReact.render(React.createElement(Bars));"
              + "JavityGame.subscribe('pulse',()=>JavityGame.call('reload'));"
              + "JavityGame.subscribe('pulse',()=>JavityGame.call('peer'));");
      document.root().validate();

      ProgressBar gain = (ProgressBar) document.actor("react-gain");
      ProgressBar loss = (ProgressBar) document.actor("react-loss");
      ProgressBar instant = (ProgressBar) document.actor("react-instant");
      for (int i = 0; i < 26; i++) {
        world.step(.05f);
        uiStage.act(world.unscaledDeltaTime);
      }
      gainSignal.set(100);
      lossSignal.set(0);
      if (document.actor("react-gain") != gain || document.actor("react-loss") != loss)
        throw new IllegalStateException("React Progress actor identity changed on signal update");

      for (int i = 0; i < 10; i++) {
        world.step(.05f);
        uiStage.act(world.unscaledDeltaTime);
      }
      if (!world.isPaused()
          || !(gain.getDelayedIncrease() > gain.getDisplayedValue())
          || !(loss.getDisplayedValue() < loss.getDelayedDecrease())
          || !gain.isAnimating()
          || !loss.isAnimating())
        throw new IllegalStateException(
            "React Progress timing props did not advance on the unscaled UI clock: gain="
                + gain.getDisplayedValue()
                + "/"
                + gain.getDelayedIncrease()
                + "/"
                + gain.isAnimating()
                + " loss="
                + loss.getDisplayedValue()
                + "/"
                + loss.getDelayedDecrease()
                + "/"
                + loss.isAnimating()
                + " values="
                + gain.getValue()
                + "/"
                + loss.getValue());

      float beforeMove = gain.getDisplayedValue();
      runtime.eval("moveGain()");
      if (document.actor("react-gain") != gain)
        throw new IllegalStateException(
            "Keyed React Progress reparent disposed or replaced its actor");
      world.step(.05f);
      uiStage.act(world.unscaledDeltaTime);
      if (!(gain.getDisplayedValue() > beforeMove) || !gain.isAnimating())
        throw new IllegalStateException(
            "Keyed React Progress reparent stopped its active animation");

      Gdx.gl.glViewport(
          0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      Gdx.gl.glClearColor(.02f, .03f, .04f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      uiStage.draw();
      Pixmap pixels =
          Pixmap.createFromFrameBuffer(
              0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      try {
        var output = Gdx.files.local("artifacts/react-progress-bars-native.png");
        output.parent().mkdirs();
        PixmapIO.writePNG(output, pixels, -1, true);
        Color magenta = UtilityStyles.color("#ff00ff");
        requireColor("React custom hex fill", pixelOnActor(pixels, instant, .25f), magenta);
      } finally {
        pixels.dispose();
      }

      Button publish = (Button) document.actor("publish-button");
      fireClick(uiStage, publish);
      if (document.actor("fresh-view") == null || oldPeerCalls.get() != 0)
        throw new IllegalStateException(
            "Native React button did not drain deferred signal reload and suppress old peers");
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
      uiStage.dispose();
      world.close();
    }
  }

  private static Color pixelOnActor(Pixmap image, Actor actor, float ratio) {
    Vector2 origin = actor.localToStageCoordinates(new Vector2());
    return pixelAtStage(
        image, origin.x + actor.getWidth() * ratio, origin.y + actor.getHeight() / 2f);
  }

  private static void fireClick(Stage stage, Actor actor) {
    Vector2 point =
        actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
    Vector2 screen = stage.stageToScreenCoordinates(point);
    int x = Math.round(screen.x);
    int y = Math.round(screen.y);
    stage.touchDown(x, y, 0, Input.Buttons.LEFT);
    stage.touchUp(x, y, 0, Input.Buttons.LEFT);
  }

  private static Color pixelAtStage(Pixmap image, float x, float yFromBottom) {
    int px = Math.max(0, Math.min(image.getWidth() - 1, Math.round(x)));
    int py = Math.max(0, Math.min(image.getHeight() - 1, Math.round(yFromBottom)));
    return new Color(image.getPixel(px, py));
  }

  private static void requireColor(String name, Color actual, Color expected) {
    if (Math.abs(actual.r - expected.r) > .035f
        || Math.abs(actual.g - expected.g) > .035f
        || Math.abs(actual.b - expected.b) > .035f)
      throw new IllegalStateException(
          name + " pixel mismatch: expected " + expected + ", got " + actual);
  }
}
