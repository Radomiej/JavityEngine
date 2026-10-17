package org.javity.script.quickjs;

import android.test.AndroidTestCase;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.script.ScriptUiException;
import org.javity.script.ScriptUiRuntime;
import org.javity.signals.Radio;
import org.javity.ui.declarative.UiDocument;
import org.javity.ui.yoga.android.AndroidYogaBackend;

/** Exercises the production QuickJS JNI provider with the portable React host on Android. */
public final class QuickJsScriptUiInstrumentationTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidYogaBackend.initialize(getContext());
  }

  public void testPersistentStrictLexicalsAndPrimitiveBridgeValidation() {
    try (UiDocument document = new UiDocument(new AndroidYogaBackend(), null)) {
      ScriptUiRuntime runtime = runtime(document);
      try {
        runtime.eval(
            "'use strict'; const persistentAnswer=42; let persistentLabel='ready';"
                + "class PersistentProbe {}");
        runtime.eval(
            "'use strict'; if(persistentAnswer!==42||persistentLabel!=='ready'||"
                + "new PersistentProbe().constructor!==PersistentProbe)"
                + "throw new Error('top-level lexical bindings did not persist')");

        AtomicInteger calls = new AtomicInteger();
        runtime.bindStringAction("name", value -> calls.incrementAndGet());
        expectScriptFailure(runtime, "JavityGame.call('name', {})", "arguments must be null");
        expectScriptFailure(
            runtime, "JavityGame.call('name', new String('boxed'))", "arguments must be null");
        expectScriptFailure(
            runtime,
            "JavityGame.call('name', {toJSON(){return 'coerced'}})",
            "arguments must be null");
        expectScriptFailure(runtime, "JavityGame.call('name', [])", "arguments must be null");
        assertEquals("invalid values must not invoke Java", 0, calls.get());
      } finally {
        runtime.close();
      }
    }
  }

  public void testActionSignalReactCommitRunsAtTheGuestBoundary() {
    try (UiDocument document = new UiDocument(new AndroidYogaBackend(), null)) {
      ScriptUiRuntime runtime = runtime(document);
      Radio radio = new Radio();
      var pulse = radio.channel("pulse", Integer.class, 0);
      AtomicInteger signalActions = new AtomicInteger();
      try {
        runtime.bindSignal("pulse", pulse);
        runtime.bindAction("publish", () -> pulse.set(1));
        runtime.bindAction("signalSeen", signalActions::incrementAndGet);
        runtime.eval(
            "JavityGame.subscribe('pulse',value=>{"
                + "JavityGame.call('signalSeen');"
                + "JavityReact.render(JavityReact.React.createElement("
                + "JavityReact.View,{id:'signal-committed'}));});");

        runtime.eval("JavityGame.call('publish')");

        assertEquals("signal listener runs once after the action returns", 1, signalActions.get());
        assertNotNull("React commit is drained before eval returns", document.actor("signal-committed"));
      } finally {
        runtime.close();
        radio.dispose();
      }
    }
  }

  public void testTimersMicrotasksAndCancellationUseOwnerThreadTick() {
    try (UiDocument document = new UiDocument(new AndroidYogaBackend(), null)) {
      ScriptUiRuntime runtime = runtime(document);
      try {
        runtime.eval(
            "globalThis.quickJsTicks=[];"
                + "const cancelled=setTimeout(()=>quickJsTicks.push('cancelled'),0);"
                + "clearTimeout(cancelled);"
                + "setTimeout(()=>{quickJsTicks.push('timer');"
                + "queueMicrotask(()=>quickJsTicks.push('microtask'))},0);");
        runtime.tick(0);
        runtime.eval(
            "if(quickJsTicks.join(',')!=='timer,microtask')"
                + "throw new Error('timer, microtask, or cancellation order failed: '+quickJsTicks)");
      } finally {
        runtime.close();
      }
    }
  }

  public void testSyntaxFailureKeepsTreeAndSubscriptionsThenReloadReleasesStaleCallbacks() {
    try (UiDocument document = new UiDocument(new AndroidYogaBackend(), null)) {
      ScriptUiRuntime runtime = runtime(document);
      Radio radio = new Radio();
      var score = radio.channel("score", Integer.class, 0);
      AtomicInteger oldSignalCalls = new AtomicInteger();
      AtomicInteger newSignalCalls = new AtomicInteger();
      AtomicInteger effectCleanups = new AtomicInteger();
      try {
        runtime.bindSignal("score", score);
        runtime.bindAction("oldSignal", oldSignalCalls::incrementAndGet);
        runtime.bindAction("newSignal", newSignalCalls::incrementAndGet);
        runtime.bindAction("cleanup", effectCleanups::incrementAndGet);
        runtime.eval(
            "function OldProbe(){JavityReact.React.useEffect(()=>()=>JavityGame.call('cleanup'),[]);"
                + "return JavityReact.React.createElement(JavityReact.View,{id:'old-quick-view'})};"
                + "JavityReact.render(JavityReact.React.createElement(OldProbe));"
                + "JavityGame.subscribe('score',()=>JavityGame.call('oldSignal'));");
        assertNotNull(document.actor("old-quick-view"));

        try {
          runtime.reload("function {");
          fail("invalid syntax must reject reload");
        } catch (ScriptUiException expected) {
          assertTrue(expected.getMessage(), expected.getMessage().contains("Reload rejected"));
        }
        assertNotNull("syntax check must preserve the live tree", document.actor("old-quick-view"));
        score.set(1);
        assertEquals("old subscription remains live after rejected syntax", 1, oldSignalCalls.get());

        runtime.reload(
            "JavityGame.subscribe('score',()=>JavityGame.call('newSignal'));"
                + "JavityReact.render(JavityReact.React.createElement("
                + "JavityReact.View,{id:'new-quick-view'}));");
        assertEquals("old React effect cleanup runs during successful reload", 1, effectCleanups.get());
        assertNull(document.actor("old-quick-view"));
        assertNotNull(document.actor("new-quick-view"));
        score.set(2);
        assertEquals("old signal callback was released", 1, oldSignalCalls.get());
        assertEquals("fresh signal callback is active", 1, newSignalCalls.get());
      } finally {
        runtime.close();
        radio.dispose();
      }
    }
  }

  public void testReloadFromGuestActionIsRejectedWithoutReplacingTheLiveTree() {
    try (UiDocument document = new UiDocument(new AndroidYogaBackend(), null)) {
      ScriptUiRuntime runtime = runtime(document);
      AtomicInteger rejected = new AtomicInteger();
      try {
        runtime.eval(
            "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'reload-live'}))");
        runtime.bindAction(
            "reload",
            () -> {
              try {
                runtime.reload("JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'wrong'}))");
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
        runtime.eval("JavityGame.call('reload')");
        assertEquals("reentrant reload is rejected inside the host callback", 1, rejected.get());
        assertNotNull(document.actor("reload-live"));
        assertNull(document.actor("wrong"));
      } finally {
        runtime.close();
      }
    }
  }

  public void testCloseFromGuestActionDefersNativeCloseUntilEvaluationReturns() {
    try (UiDocument document = new UiDocument(new AndroidYogaBackend(), null)) {
      ScriptUiRuntime runtime = runtime(document);
      try {
        runtime.eval(
            "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'before-close'}))");
        runtime.bindAction("close", runtime::close);
        runtime.eval(
            "globalThis.afterCloseAction=false;JavityGame.call('close');afterCloseAction=true");
        assertTrue("close requested from Java binding completes after the guest unwinds", runtime.isClosed());
        assertNull(document.actor("before-close"));
        assertNotNull(document.actor("javity-script-empty"));
      } finally {
        runtime.close();
      }
    }
  }

  public void testNativeEvaluationTimeoutReturnsAndCloseUnmountsTheDocument() {
    UiDocument document = new UiDocument(new AndroidYogaBackend(), null);
    ScriptUiRuntime runtime = runtime(document);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'timeout-view'}))");
      long startNanos = System.nanoTime();
      expectScriptFailure(runtime, "while(true){}", "QuickJS");
      long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
      assertTrue("native evaluation timeout should interrupt near the configured 2 seconds: " + elapsedMillis,
          elapsedMillis >= 1_000 && elapsedMillis < 8_000);
    } finally {
      try {
        runtime.close();
        assertTrue(runtime.isClosed());
        assertNull("close unmounts the prior React tree", document.actor("timeout-view"));
        assertNotNull("close leaves the portable empty root", document.actor("javity-script-empty"));
      } finally {
        document.close();
      }
    }
  }

  private static ScriptUiRuntime runtime(UiDocument document) {
    return new ScriptUiRuntime(document, new QuickJsScriptUiEngineFactory());
  }

  private static void expectScriptFailure(ScriptUiRuntime runtime, String source, String message) {
    try {
      runtime.eval(source);
      fail("Expected JavaScript failure containing: " + message);
    } catch (ScriptUiException expected) {
      assertTrue("Expected '" + message + "' in: " + expected.getMessage(),
          expected.getMessage().contains(message));
    }
  }
}
