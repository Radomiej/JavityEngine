package org.javity.script;

import static org.junit.Assert.*;

import org.javity.ui.declarative.UiDocument;
import org.junit.Test;

public class ScriptUiRuntimeTest {
  @Test
  public void typedActionsValidatePrimitiveArityAndTypeBeforeJavaAndSurviveReload() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    var number = new java.util.concurrent.atomic.AtomicReference<Double>();
    var bool = new java.util.concurrent.atomic.AtomicReference<Boolean>();
    var string = new java.util.concurrent.atomic.AtomicReference<String>();
    try {
      runtime.bindNumberAction("volume", number::set);
      runtime.bindBooleanAction("mute", bool::set);
      runtime.bindStringAction("name", string::set);
      runtime.bindAction("zero", () -> number.set(-1d));
      runtime.eval(
          "JavityGame.call('volume', 0.65); JavityGame.call('mute', true); JavityGame.call('name',"
              + " 'music')");
      assertEquals(Double.valueOf(.65), number.get());
      assertEquals(Boolean.TRUE, bool.get());
      assertEquals("music", string.get());
      runtime.eval("JavityGame.call('zero')");
      assertEquals(Double.valueOf(-1), number.get());
      assertScriptFailure(runtime, "JavityGame.call('volume')", "exactly one numeric primitive");
      assertScriptFailure(
          runtime, "JavityGame.call('volume', 1, 2)", "exactly one numeric primitive");
      assertScriptFailure(
          runtime, "JavityGame.call('volume', '1')", "exactly one numeric primitive");
      assertScriptFailure(runtime, "JavityGame.call('volume', NaN)", "must be finite");
      assertScriptFailure(runtime, "JavityGame.call('mute', 1)", "exactly one boolean primitive");
      assertScriptFailure(runtime, "JavityGame.call('name', {})", "exactly one string primitive");
      assertScriptFailure(runtime, "JavityGame.call('zero', false)", "requires no arguments");
      assertEquals(Double.valueOf(-1), number.get());
      runtime.bindNumberAction("volume", value -> number.set(value * 2));
      runtime.eval("JavityGame.call('volume', .25)");
      assertEquals(Double.valueOf(.5), number.get());
      runtime.unbindAction("volume");
      assertScriptFailure(runtime, "JavityGame.call('volume', .5)", "Unknown JavityGame action");
      runtime.bindBooleanAction("mute", value -> bool.set(!value));
      runtime.reload("JavityGame.call('mute', false)");
      assertEquals(Boolean.TRUE, bool.get());
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void reactHostUsesNumericControlledSliderToggleAndKeyHandlers() {
    assertFreshScriptFailure(
        "JavityReact.render(JavityReact.React.createElement(JavityReact.Toggle,{id:'bad-toggle',checked:'yes'}))",
        "checked must be a boolean");
    assertFreshScriptFailure(
        "JavityReact.render(JavityReact.React.createElement(JavityReact.Slider,{id:'bad-slider',min:0,max:1,step:.1,value:'0.5'}))",
        "Slider value must be a finite number");
    assertFreshScriptFailure(
        "JavityReact.render(JavityReact.React.createElement(JavityReact.Slider,{id:'bad-step',min:0,max:1,value:.5,step:0}))",
        "step must be positive");
    assertFreshScriptFailure(
        "JavityReact.render(JavityReact.React.createElement(JavityReact.Slider,{id:'bad-range',min:1,max:1,value:1,step:.1}))",
        "Slider range requires min < max");
  }

  @Test
  public void reactTextWrapDecodesAsBooleanBeforeThemeCreationAndPreservesPreviousTree() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'preserved'}))");
      assertScriptFailure(
          runtime,
          "JavityReact.render(JavityReact.React.createElement(JavityReact.Text,{id:'bad-wrap',"
              + "text:'text',wrap:'true'}))",
          "wrap must be a boolean");
      assertNotNull(document.actor("preserved"));
      assertNull(document.actor("bad-wrap"));

      UiDocument validDocument = new UiDocument(null);
      ScriptUiRuntime validRuntime = new ScriptUiRuntime(validDocument);
      try {
        validRuntime.eval(
            "JavityReact.render(JavityReact.React.createElement(JavityReact.Text,{id:'wrapped',"
                + "text:'Zażółć',wrap:true}))");
        fail("Text.wrap must reach native typed decoding before a theme is required");
      } catch (ScriptUiException expected) {
        assertTrue(
            expected.getMessage(),
            expected.getMessage().contains("UiTheme is required to render Text"));
      } finally {
        validRuntime.close();
        validDocument.close();
      }
    } finally {
      runtime.close();
      document.close();
    }
  }

  private static void assertFreshScriptFailure(String source, String message) {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      assertScriptFailure(runtime, source, message);
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void nestedSignalReloadDrainsAtTheOuterEvalBoundaryAndSkipsOldPeers() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("pulse", Integer.class, 0);
    var peers = new java.util.concurrent.atomic.AtomicInteger();
    try {
      runtime.bindSignal("pulse", signal);
      runtime.bindValue("level", () -> 17);
      runtime.bindAction("publish", () -> signal.set(1));
      runtime.bindAction(
          "reload",
          () ->
              runtime.reload(
                  "globalThis.freshLevel=JavityGame.get('level');"
                      + "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'fresh-view'}))"));
      runtime.bindAction("peer", peers::incrementAndGet);
      runtime.eval(
          "JavityGame.subscribe('pulse',()=>JavityGame.call('reload'));JavityGame.subscribe('pulse',()=>JavityGame.call('peer'))");
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'old-view'}));JavityGame.call('publish')");
      assertNotNull("reload must finish before outer eval returns", document.actor("fresh-view"));
      assertNull(document.actor("old-view"));
      assertEquals(0, peers.get());
      runtime.eval("if(freshLevel!==17)throw new Error('binding missing after nested reload')");
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void nestedSignalReloadFromTimerCancelsOldDueSnapshots() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("pulse", Integer.class, 0);
    var oldTimerCalls = new java.util.concurrent.atomic.AtomicInteger();
    var oldMicrotaskCalls = new java.util.concurrent.atomic.AtomicInteger();
    var oldSignalPeers = new java.util.concurrent.atomic.AtomicInteger();
    try {
      runtime.bindSignal("pulse", signal);
      runtime.bindAction("publish", () -> signal.set(1));
      runtime.bindAction(
          "reload",
          () ->
              runtime.reload(
                  "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'timer-fresh'}))"));
      runtime.bindAction("oldTimer", oldTimerCalls::incrementAndGet);
      runtime.bindAction("oldPeer", oldSignalPeers::incrementAndGet);
      runtime.bindAction("oldMicrotask", oldMicrotaskCalls::incrementAndGet);
      runtime.eval(
          "JavityGame.subscribe('pulse',()=>JavityGame.call('reload'));"
              + "JavityGame.subscribe('pulse',()=>JavityGame.call('oldPeer'));"
              + "setTimeout(()=>{JavityGame.call('publish');queueMicrotask(()=>JavityGame.call('oldMicrotask'))},0);"
              + "setTimeout(()=>JavityGame.call('oldTimer'),0)");
      runtime.tick(0);
      assertNotNull(
          "timer-requested reload must drain before tick returns", document.actor("timer-fresh"));
      assertEquals(0, oldTimerCalls.get());
      assertEquals(0, oldMicrotaskCalls.get());
      assertEquals(0, oldSignalPeers.get());
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void deferredReloadProcessingIsBoundedToOneReloadPerOuterBoundary() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("pulse", Integer.class, 0);
    var pulse = new java.util.concurrent.atomic.AtomicInteger();
    var firstMounts = new java.util.concurrent.atomic.AtomicInteger();
    var secondMounts = new java.util.concurrent.atomic.AtomicInteger();
    try {
      runtime.bindSignal("pulse", signal);
      runtime.bindAction("publish", () -> signal.set(pulse.incrementAndGet()));
      runtime.bindAction("firstMounted", firstMounts::incrementAndGet);
      runtime.bindAction("secondMounted", secondMounts::incrementAndGet);
      runtime.bindAction(
          "firstReload",
          () ->
              runtime.reload(
                  "globalThis.generation=1;JavityGame.call('firstMounted');JavityGame.subscribe('pulse',()=>JavityGame.call('secondReload'));JavityGame.call('publish')"));
      runtime.bindAction(
          "secondReload",
          () -> runtime.reload("globalThis.generation=2;JavityGame.call('secondMounted')"));
      runtime.eval(
          "JavityGame.subscribe('pulse',()=>JavityGame.call('firstReload'));JavityGame.call('publish')");
      assertEquals(1, firstMounts.get());
      assertEquals(0, secondMounts.get());
      runtime.eval("globalThis.nextBoundary=true");
      assertEquals(1, secondMounts.get());
      runtime.eval(
          "if(generation!==2||!nextBoundary)throw new Error('pending reload missed next boundary:"
              + " '+generation+'/'+nextBoundary)");
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void invalidNestedSignalReloadRetainsSubscriptions() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("pulse", Integer.class, 0);
    try {
      runtime.bindSignal("pulse", signal);
      runtime.bindAction("invalidReload", () -> runtime.reload("function {"));
      runtime.eval(
          "globalThis.calls=0;globalThis.caught=0;JavityGame.subscribe('pulse',()=>{calls++;try{JavityGame.call('invalidReload')}catch(e){caught++}})");
      signal.set(1);
      signal.set(2);
      runtime.eval(
          "if(calls!==2||caught!==2)throw new Error('invalid deferred reload detached live listener"
              + " '+calls+'/'+caught)");
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void rapidNestedSignalReloadRequestsUseTheLastValidSource() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("pulse", Integer.class, 0);
    try {
      runtime.bindSignal("pulse", signal);
      runtime.bindAction(
          "replaceTwice",
          () -> {
            runtime.reload("globalThis.winner=1");
            runtime.reload(
                "globalThis.winner=2;JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'last-wins'}))");
          });
      runtime.eval("JavityGame.subscribe('pulse',()=>JavityGame.call('replaceTwice'))");
      signal.set(1);
      assertNotNull(document.actor("last-wins"));
      runtime.eval("if(winner!==2)throw new Error('last valid reload candidate did not win')");
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void reactProgressTimingPropsDecodeBeforeThemeValidationAndRejectInvalidHostValues() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'preserved',className:'w-[40px]'}))");
      try {
        runtime.eval(
            "JavityReact.render(JavityReact.React.createElement(JavityReact.Progress,{"
                + "id:'progress',className:'text-[#ff00ff]',min:0,max:100,value:50,duration:1,delay:.25}))");
        fail("headless document should reject Progress without a theme");
      } catch (ScriptUiException expected) {
        assertTrue(
            expected.getMessage(),
            expected.getMessage().contains("UiTheme is required to render Progress"));
      }
      assertNotNull(document.actor("preserved"));
      assertNull(document.actor("progress"));

      assertScriptFailure(
          runtime,
          "JavityNative.commit(JSON.stringify({type:'Progress',key:'bad-progress',classes:'',props:{value:1,duration:-1},children:[]}),{}"
              + " )",
          "duration must be finite and nonnegative");
      assertNotNull(document.actor("preserved"));
      assertNull(document.actor("bad-progress"));
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void realRadioEventsCommitReactStateImmediatelyAndCanPublishReentrantly() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("score", Integer.class, 1);
    try {
      runtime.bindSignal("score", signal);
      runtime.eval(
          "const {React,View}=JavityReact;function Score(){const"
              + " [value,setValue]=React.useState(JavityGame.get('score'));React.useEffect(()=>{const"
              + " id=JavityGame.subscribe('score',setValue);return"
              + " ()=>JavityGame.unsubscribe(id)},[]);return"
              + " React.createElement(View,{id:'radio-root',className:'w-['+value+'px]'})};"
              + "JavityReact.render(React.createElement(Score));"
              + "globalThis.subscriptionId=JavityGame.subscribe('score',value=>{"
              + "if(value===2)JavityGame.call('republish')});");
      assertEquals(
          "w-[1px]", ((org.javity.ui.flex.Flex) document.actor("radio-root")).getStyleClasses());
      runtime.bindAction("republish", () -> signal.set(3));
      signal.set(2);
      assertEquals(
          "w-[3px]", ((org.javity.ui.flex.Flex) document.actor("radio-root")).getStyleClasses());
      runtime.eval("JavityGame.unsubscribe(subscriptionId);JavityGame.unsubscribe(subscriptionId)");
      signal.set(4);
      assertEquals(
          "w-[4px]", ((org.javity.ui.flex.Flex) document.actor("radio-root")).getStyleClasses());
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void radioSubscriptionsDoNotSurviveReloadAndSignalBindingDoes() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("score", Integer.class, 2);
    try {
      runtime.bindSignal("score", signal);
      runtime.eval(
          "globalThis.oldCalls=0;globalThis.oldId=JavityGame.subscribe('score',()=>oldCalls++)");
      runtime.reload(
          "globalThis.newCalls=0;globalThis.newId=JavityGame.subscribe('score',()=>newCalls++)");
      signal.set(3);
      runtime.eval(
          "if(typeof oldCalls!=='undefined'||newCalls!==1)throw new Error('reload radio"
              + " lifetime')");
      runtime.unbindSignal("score");
      assertScriptFailure(
          runtime, "JavityGame.subscribe('score',()=>{})", "Unknown JavityGame signal");
      runtime.bindSignal("score", signal);
      runtime.eval("globalThis.afterRebind=0;JavityGame.subscribe('score',()=>afterRebind++)");
      var replacement = radio.channel("replacement", Integer.class, 4);
      runtime.bindSignal("score", replacement);
      signal.set(5);
      runtime.eval("if(afterRebind!==0)throw new Error('replaced source retained a subscription')");
      runtime.eval(
          "globalThis.afterReplacement=0;JavityGame.subscribe('score',()=>afterReplacement++)");
      replacement.set(6);
      runtime.eval(
          "if(afterRebind!==0)throw new Error('replaced signal callback remained subscribed')");
      runtime.eval("if(afterReplacement!==1)throw new Error('replacement signal did not publish')");
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void signalSubscriptionsValidatePrimitiveBindingsAndOwnerThread() throws Exception {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("text", String.class, "ready");
    try {
      runtime.bindValue("occupied", () -> 1);
      try {
        runtime.bindSignal("occupied", signal);
        fail("signal binding must reject a value name collision");
      } catch (IllegalArgumentException expected) {
      }
      runtime.bindSignal("text", signal);
      try {
        runtime.bindValue("text", () -> "collision");
        fail("value binding must reject an existing signal name");
      } catch (IllegalArgumentException expected) {
      }
      runtime.eval("if(JavityGame.get('text')!=='ready')throw new Error('initial radio value')");
      assertScriptFailure(
          runtime, "JavityGame.subscribe('missing',()=>{})", "Unknown JavityGame signal");
      assertScriptFailure(runtime, "JavityGame.subscribe('text',{})", "callback");
      runtime.eval("globalThis.textEvents=0;JavityGame.subscribe('text',()=>textEvents++)");
      var result = new java.util.concurrent.atomic.AtomicReference<Throwable>();
      Thread other =
          new Thread(
              () -> {
                try {
                  signal.set("wrong-thread");
                } catch (Throwable error) {
                  result.set(error);
                }
              });
      other.start();
      other.join();
      assertTrue(result.get() instanceof IllegalStateException);
      signal.set("updated");
      runtime.eval("if(textEvents!==1)throw new Error('radio event missing')");
      runtime.unbindSignal("text");
      assertScriptFailure(runtime, "JavityGame.get('text')", "Unknown JavityGame value");
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void signalCallbackCanRequestDeferredReloadAndCloseSuppressesPeerCallbacks() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("events", Integer.class, 0);
    var peerCalls = new java.util.concurrent.atomic.AtomicInteger();
    try {
      runtime.bindSignal("events", signal);
      runtime.bindValue("persistent", () -> 9);
      runtime.bindAction(
          "reload",
          () -> runtime.reload("globalThis.afterSignalReload=JavityGame.get('persistent')"));
      runtime.bindAction("oldPeer", peerCalls::incrementAndGet);
      runtime.eval(
          "globalThis.calls=0;JavityGame.subscribe('events',()=>{calls++;JavityGame.call('reload')});"
              + "JavityGame.subscribe('events',()=>JavityGame.call('oldPeer'))");
      signal.set(1);
      runtime.eval(
          "if(afterSignalReload!==9||typeof calls!=='undefined')throw new Error('deferred reload"
              + " did not replace context or preserve bindings')");
      assertEquals(0, peerCalls.get());
      runtime.bindAction("peer", peerCalls::incrementAndGet);
      runtime.bindAction("close", runtime::close);
      runtime.eval(
          "JavityGame.subscribe('events',()=>JavityGame.call('close'));JavityGame.subscribe('events',()=>JavityGame.call('peer'))");
      signal.set(2);
      assertTrue(runtime.isClosed());
      assertEquals(0, peerCalls.get());
      assertNotNull(document.actor("javity-script-empty"));
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void closeOverridesReloadRequestedInTheSameSignalCallback() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("events", Integer.class, 0);
    try {
      runtime.bindSignal("events", signal);
      runtime.bindAction(
          "reloadThenClose",
          () -> {
            runtime.reload("globalThis.mustNotRun=true");
            runtime.close();
          });
      runtime.eval("JavityGame.subscribe('events',()=>JavityGame.call('reloadThenClose'))");
      signal.set(1);
      assertTrue(runtime.isClosed());
      assertNotNull(document.actor("javity-script-empty"));
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void actualSampleSignalHookAndNestedKeyedYogaTreeUpdateInGraal() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    org.javity.signals.Radio radio = new org.javity.signals.Radio();
    var signal = radio.channel("score", Integer.class, 5);
    try {
      runtime.bindSignal("score", signal);
      runtime.eval(resource("/org/javity/script/javity-sample.js"));
      runtime.eval("mountRadioAndNestedFixture()");
      var value = (org.javity.ui.flex.Flex) document.actor("radio-fixture");
      assertEquals("flex-col gap-[5px]", value.getStyleClasses());
      signal.set(8);
      assertEquals("flex-col gap-[8px]", value.getStyleClasses());

      var root = (org.javity.ui.flex.Flex) document.actor("nested-root");
      var row = (org.javity.ui.flex.Flex) document.actor("nested-row");
      var left = (org.javity.ui.flex.Flex) document.actor("nested-left");
      var right = (org.javity.ui.flex.Flex) document.actor("nested-right");
      var leftInner = (org.javity.ui.flex.Flex) document.actor("nested-left-inner");
      assertNotNull(leftInner.getParent().getParent().getParent());
      document.root().setSize(400, 200);
      document.root().layout();
      float previousWidth = root.getWidth();
      runtime.eval("mountNestedViewFixture(true,true)");
      assertSame(left, document.actor("nested-left"));
      assertSame(right, document.actor("nested-right"));
      assertSame(leftInner, document.actor("nested-left-inner"));
      assertEquals("flex-col gap-3 w-[240px]", root.getStyleClasses());
      assertSame(right, row.getChildren().get(0));
      assertSame(left, row.getChildren().get(1));
      document.root().layout();
      assertTrue(
          "updated Yoga width should change: before="
              + previousWidth
              + ", after="
              + root.getWidth(),
          root.getWidth() > previousWidth);
      assertTrue(left.getWidth() > 0);
    } finally {
      runtime.close();
      radio.dispose();
      document.close();
    }
  }

  @Test
  public void realReactHooksCommitJsxAndEffectCleanup() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval("globalThis.originalJavityReact=JavityReact");
      runtime.eval(resource("/org/javity/script/javity-sample.js"));
      runtime.eval(
          "if(JavityReact!==originalJavityReact)throw new Error('sample replaced the runtime React"
              + " host')");
      runtime.eval("mountHeadlessCounter()");
      runtime.eval("if (!headlessEffectMounted) throw new Error('effect did not run')");
      assertEquals(
          "w-[10px]",
          ((org.javity.ui.flex.Flex) document.actor("headless-screen")).getStyleClasses());
      runtime.eval("bumpHeadlessCounter()");
      runtime.tick(0);
      assertEquals(
          "w-[20px]",
          ((org.javity.ui.flex.Flex) document.actor("headless-screen")).getStyleClasses());
      runtime.eval("JavityReact.unmount()");
      runtime.eval("if (headlessEffectMounted) throw new Error('effect cleanup did not run')");
      assertEquals(null, document.actor("headless-screen"));
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void deterministicTimersMicrotasksAndClockValidation() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "globalThis.calls=[]; setTimeout(()=>calls.push('t'), 0);"
              + " queueMicrotask(()=>calls.push('m'));");
      runtime.eval("if(calls[0]!=='m')throw new Error('microtask order')");
      runtime.eval("Promise.resolve().then(()=>calls.push('p'))");
      runtime.eval(
          "if(calls[1]!=='p')throw new Error('Graal Promise microtask did not run at eval"
              + " boundary')");
      runtime.eval("requestAnimationFrame(time=>calls.push(time))");
      runtime.tick(0);
      runtime.eval("if(calls[3]!==0)throw new Error('requestAnimationFrame timestamp')");
      runtime.eval("globalThis.calls.push('ready')");
      runtime.tick(0);
      runtime.eval("if(calls[2]!=='t')throw new Error('timer did not run')");
      runtime.eval("setTimeout(()=>setTimeout(()=>calls.push('next'),0),0)");
      runtime.tick(0);
      runtime.eval("if(calls.length!==5)throw new Error('nested zero timer ran in same tick')");
      runtime.tick(0);
      runtime.eval("if(calls[5]!=='next')throw new Error('nested timer did not run next tick')");
      runtime.eval(
          "globalThis.peer=[]; let cancelPeer; setTimeout(()=>clearTimeout(cancelPeer),0);"
              + " cancelPeer=setTimeout(()=>peer.push('bad'),0)");
      runtime.tick(0);
      runtime.eval("if(peer.length!==0)throw new Error('cleared timer in due snapshot still ran')");
      try {
        runtime.tick(Double.NaN);
        fail("non-finite time should fail");
      } catch (IllegalArgumentException expected) {
      }
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void invalidReactStyleLeavesPreviouslyCommittedNativeTree() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'kept',className:'w-[25px]'}))");
      try {
        runtime.eval(
            "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'bad',style:{position:'fixed'}}))");
        fail("unsupported inline style should fail");
      } catch (ScriptUiException expected) {
      }
      assertEquals(
          "w-[25px]", ((org.javity.ui.flex.Flex) document.actor("kept")).getStyleClasses());
      assertNull(document.actor("bad"));
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void fragmentRootCommitsBothNativeViews() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.React.Fragment,null,"
              + "JavityReact.React.createElement(JavityReact.View,{id:'fragment-a',className:'w-[11px]'}),"
              + "JavityReact.React.createElement(JavityReact.View,{id:'fragment-b',className:'w-[22px]'})))");
      assertEquals(
          "w-[11px]", ((org.javity.ui.flex.Flex) document.actor("fragment-a")).getStyleClasses());
      assertEquals(
          "w-[22px]", ((org.javity.ui.flex.Flex) document.actor("fragment-b")).getStyleClasses());
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void tickUsesOneBudgetForTimersAndTheirMicrotasks() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "for(let i=0;i<1000;i++)setTimeout(()=>{"
              + "for(let j=0;j<1000;j++)queueMicrotask(()=>{});},0)");
      try {
        runtime.tick(0);
        fail("timer and microtask callbacks must share one per-tick limit");
      } catch (IllegalStateException expected) {
        assertTrue(expected.getMessage().contains("callback"));
      }
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void callbackFailureLeavesLaterDueTimerPending() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "globalThis.laterTimerRan=false;setTimeout(()=>{throw new Error('expected')},0);"
              + "setTimeout(()=>laterTimerRan=true,0)");
      try {
        runtime.tick(0);
        fail("first timer should report its exception");
      } catch (ScriptUiException expected) {
      }
      runtime.tick(0);
      runtime.eval("if(!laterTimerRan)throw new Error('later timer was lost')");
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void explicitGameplayBridgeCallsActionsAndReadsPrimitiveValues() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      runtime.bindAction("increment", calls::incrementAndGet);
      runtime.bindValue("score", () -> 42.5d);
      runtime.bindValue("ready", () -> true);
      runtime.bindValue("label", () -> "Level 1");
      runtime.bindValue("missing", () -> null);
      runtime.eval(
          "if(JavityGame.get('score')!==42.5||JavityGame.get('ready')!==true||"
              + "JavityGame.get('label')!=='Level 1'||JavityGame.get('missing')!==null)"
              + "throw new Error('game values were not primitive JS values');"
              + "JavityGame.call('increment')");
      assertEquals(1, calls.get());
      runtime.unbindAction("increment");
      runtime.unbindValue("label");
      assertScriptFailure(runtime, "JavityGame.call('increment')", "Unknown JavityGame action");
      assertScriptFailure(runtime, "JavityGame.get('label')", "Unknown JavityGame value");
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void gameplayRegistrationsPersistReloadAndOldContextIsDiscarded() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      runtime.bindAction("advance", calls::incrementAndGet);
      runtime.bindValue("level", () -> 7);
      runtime.eval("globalThis.oldContextMarker='old'");
      runtime.reload("globalThis.levelAtReload=JavityGame.get('level')");
      runtime.eval(
          "if(typeof oldContextMarker!=='undefined'||levelAtReload!==7)"
              + "throw new Error('reload did not use the retained game bridge and fresh context');"
              + "JavityGame.call('advance')");
      assertEquals(1, calls.get());
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void gameplayBridgeRejectsUnknownNamesAndNonPrimitiveValues() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.bindValue("bad", Object::new);
      runtime.bindValue("nan", () -> Double.NaN);
      assertScriptFailure(runtime, "JavityGame.call('unknown')", "Unknown JavityGame action");
      assertScriptFailure(runtime, "JavityGame.get('unknown')", "Unknown JavityGame value");
      assertScriptFailure(runtime, "JavityGame.get('bad')", "Unsupported JavityGame value");
      assertScriptFailure(runtime, "JavityGame.get('nan')", "Unsupported JavityGame value");
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void closeFromEvaluationActionDefersTeardownUntilGuestReturns() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'before-close'}))");
      runtime.bindAction("close", runtime::close);
      runtime.eval(
          "globalThis.afterCloseAction=false; globalThis.closeTimerCalls=0;"
              + "JavityGame.call('close'); afterCloseAction=true;"
              + "setTimeout(()=>closeTimerCalls++,0);");
      assertTrue(runtime.isClosed());
      assertNull(document.actor("before-close"));
      assertNotNull(document.actor("javity-script-empty"));
      assertThrows(IllegalStateException.class, () -> runtime.eval("afterCloseAction"));
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void closeFromTimerSuppressesPeerCallbacksAndEmptiesBorrowedDocument() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'timer-view'}))");
      var actionCalls = new java.util.concurrent.atomic.AtomicInteger();
      runtime.bindAction(
          "close",
          () -> {
            actionCalls.incrementAndGet();
            runtime.close();
          });
      runtime.eval(
          "globalThis.timerTail=false; globalThis.peerTimerCalls=0;"
              + "setTimeout(()=>{JavityGame.call('close'); timerTail=true},0);"
              + "setTimeout(()=>peerTimerCalls++,0)");
      runtime.tick(0);
      assertEquals(1, actionCalls.get());
      assertTrue(runtime.isClosed());
      assertNull(document.actor("timer-view"));
      assertNotNull(document.actor("javity-script-empty"));
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void closeRunsReactCleanupWithRegisteredGameBindingsBeforeReleasingThem() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      var cleanupCalls = new java.util.concurrent.atomic.AtomicInteger();
      runtime.bindAction("cleanup", cleanupCalls::incrementAndGet);
      runtime.eval(
          "function"
              + " CleanupProbe(){JavityReact.React.useEffect(()=>()=>JavityGame.call('cleanup'),[]);return"
              + " JavityReact.React.createElement(JavityReact.View,{id:'cleanup-view'})};"
              + "JavityReact.render(JavityReact.React.createElement(CleanupProbe))");
      runtime.close();
      assertEquals(1, cleanupCalls.get());
      assertTrue(runtime.isClosed());
      assertNull(document.actor("cleanup-view"));
      assertNotNull(document.actor("javity-script-empty"));
      assertThrows(IllegalStateException.class, () -> runtime.eval("JavityGame.call('cleanup')"));
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void reloadRejectsReentrantGuestMutationBeforeTouchingDocument() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'still-here'}))");
      runtime.bindAction(
          "reload",
          () -> {
            try {
              runtime.reload("globalThis.reloaded=true");
              fail("reentrant reload should be rejected");
            } catch (IllegalStateException expected) {
              assertTrue(expected.getMessage().contains("inside a guest callback"));
            }
          });
      runtime.eval("JavityGame.call('reload')");
      assertNotNull(document.actor("still-here"));
      runtime.eval("if(globalThis.reloaded)throw new Error('reentrant reload mutated context')");
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void gameplayBindingsValidateNamesAndEnforceOwnerThreadAndLifetime() throws Exception {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      try {
        runtime.bindAction("  ", () -> {});
        fail("blank action name should be rejected");
      } catch (IllegalArgumentException expected) {
      }
      var result = new java.util.concurrent.atomic.AtomicReference<Throwable>();
      Thread other =
          new Thread(
              () -> {
                try {
                  runtime.bindValue("off-thread", () -> 1);
                } catch (Throwable error) {
                  result.set(error);
                }
              });
      other.start();
      other.join();
      assertTrue(result.get() instanceof IllegalStateException);
    } finally {
      runtime.close();
      document.close();
    }
    try {
      runtime.bindAction("closed", () -> {});
      fail("closed runtime must reject new registrations");
    } catch (IllegalStateException expected) {
    }
    try {
      runtime.unbindValue("closed");
      fail("closed runtime must reject unregistration");
    } catch (IllegalStateException expected) {
    }
  }

  private static void assertScriptFailure(ScriptUiRuntime runtime, String source, String message) {
    try {
      runtime.eval(source);
      fail("Expected JavaScript failure containing: " + message);
    } catch (ScriptUiException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains(message));
    }
  }

  @Test
  public void successfulReloadUsesFreshGlobalsAndClearsOldTimers() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval("globalThis.oldGlobal=1; globalThis.oldTimer=0; setTimeout(()=>oldTimer++,0)");
      runtime.reload(
          "globalThis.freshGlobal=2; globalThis.newTimer=0; setTimeout(()=>newTimer++,0)");
      runtime.eval(
          "if(typeof oldGlobal!=='undefined'||freshGlobal!==2)throw new Error('reload context was"
              + " not fresh')");
      runtime.tick(0);
      runtime.eval(
          "if(typeof oldTimer!=='undefined'||newTimer!==1)throw new Error('reload timer cleanup"
              + " failed')");
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void evalRejectsCallsFromWrongThread() throws Exception {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      var result = new java.util.concurrent.atomic.AtomicReference<Throwable>();
      Thread other =
          new Thread(
              () -> {
                try {
                  runtime.eval("1+1");
                } catch (Throwable error) {
                  result.set(error);
                }
              });
      other.start();
      other.join();
      assertTrue(result.get() instanceof IllegalStateException);
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void hostClassLookupAndJavaReflectionAreDenied() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      try {
        runtime.eval("Java.type('java.lang.System')");
        fail("Java.type must not be exposed");
      } catch (ScriptUiException expected) {
        assertTrue(expected.getMessage().contains("JavaScript evaluation failed"));
      }
    } finally {
      runtime.close();
      document.close();
    }
  }

  @Test
  public void reloadSyntaxFailureLeavesCurrentTreeMounted() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'kept',className:'w-[33px]'}))");
      try {
        runtime.reload("function broken( {");
        fail("syntax error should reject reload");
      } catch (ScriptUiException expected) {
      }
      assertNotNull(document.actor("kept"));
    } finally {
      runtime.close();
      document.close();
    }
  }

  private String resource(String path) {
    try (var in = getClass().getResourceAsStream(path)) {
      if (in == null) throw new AssertionError("Missing runtime sample bundle: " + path);
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }
}
