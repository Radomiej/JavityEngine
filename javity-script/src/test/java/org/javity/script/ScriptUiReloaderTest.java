package org.javity.script;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.javity.signals.Radio;
import org.javity.ui.declarative.UiDocument;
import org.junit.Test;

public class ScriptUiReloaderTest {
  @Test
  public void syntaxFailureKeepsLiveTreeActionsAndSignalUntilChangedBundleReloads() throws Exception {
    Path bundle = Files.createTempFile("javity-ui-dev", ".js");
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    ScriptUiReloader reloader = new ScriptUiReloader(runtime, bundle);
    Radio radio = new Radio();
    var score = radio.channel("score", Integer.class, 0);
    AtomicInteger actions = new AtomicInteger();
    AtomicInteger cleanups = new AtomicInteger();
    runtime.bindAction("fire", actions::incrementAndGet);
    runtime.bindAction("cleanup", cleanups::incrementAndGet);
    runtime.bindAction("oldSignal", () -> actions.addAndGet(10));
    runtime.bindAction("newSignal", () -> actions.addAndGet(100));
    runtime.bindSignal("score", score);
    try {
      runtime.eval(
          "function OldProbe(){JavityReact.React.useEffect(()=>()=>JavityGame.call('cleanup'),[]);"
              + "return JavityReact.React.createElement(JavityReact.View,{id:'old-view'})};"
              + "JavityReact.render(JavityReact.React.createElement(OldProbe));"
              + "JavityGame.subscribe('score',()=>JavityGame.call('oldSignal'));"
      );
      assertNotNull(document.actor("old-view"));

      FileTime fixedTime = FileTime.fromMillis(1_600_000_000_000L);
      Files.writeString(bundle, "function {");
      Files.setLastModifiedTime(bundle, fixedTime);
      assertEquals(ScriptUiReloader.PollResult.ERROR, reloader.poll());
      assertTrue(reloader.lastError().orElseThrow().contains("Reload rejected"));
      assertNotNull(document.actor("old-view"));
      runtime.eval("JavityGame.call('fire')");
      score.set(1);
      assertEquals(11, actions.get());
      assertEquals(ScriptUiReloader.PollResult.UNCHANGED, reloader.poll());

      Files.writeString(bundle, "function }");
      Files.setLastModifiedTime(bundle, fixedTime);
      assertEquals(ScriptUiReloader.PollResult.ERROR, reloader.poll());
      assertNotNull(document.actor("old-view"));

      String valid =
          "globalThis.newSignals=0;"
              + "JavityGame.subscribe('score',()=>JavityGame.call('newSignal'));"
              + "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'new-view'}));";
      Files.writeString(bundle, valid);
      Files.setLastModifiedTime(bundle, fixedTime);
      assertEquals(ScriptUiReloader.PollResult.RELOADED, reloader.poll());
      assertTrue(reloader.lastError().isEmpty());
      assertEquals(1, cleanups.get());
      assertNull(document.actor("old-view"));
      assertNotNull(document.actor("new-view"));
      runtime.eval("JavityGame.call('fire')");
      score.set(2);
      assertEquals("new signal listener replaces old listener", 112, actions.get());
      assertEquals(ScriptUiReloader.PollResult.UNCHANGED, reloader.poll());

      String sameLengthEdit = valid.replace("newSignal", "oldSignal");
      assertEquals(valid.length(), sameLengthEdit.length());
      Files.writeString(bundle, sameLengthEdit);
      Files.setLastModifiedTime(bundle, fixedTime);
      assertEquals(ScriptUiReloader.PollResult.RELOADED, reloader.poll());
      score.set(3);
      assertEquals("same-size, same-time edit is detected by content", 122, actions.get());
    } finally {
      reloader.close();
      runtime.close();
      document.close();
      radio.dispose();
      Files.deleteIfExists(bundle);
    }
  }

  @Test
  public void boundedStrictUtf8ReadsAndCloseDoNotMutateBorrowedRuntime() throws Exception {
    Path bundle = Files.createTempFile("javity-ui-boundary", ".js");
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    ScriptUiReloader reloader = new ScriptUiReloader(runtime, bundle);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'kept'}))");
      byte[] exactLimit = new byte[ScriptUiReloader.MAX_SOURCE_BYTES];
      java.util.Arrays.fill(exactLimit, (byte) ' ');
      Files.write(bundle, exactLimit);
      assertEquals(ScriptUiReloader.PollResult.RELOADED, reloader.poll());

      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'kept'}))");
      Files.delete(bundle);
      assertEquals(ScriptUiReloader.PollResult.ERROR, reloader.poll());
      assertNotNull(document.actor("kept"));
      assertFalse(runtime.isClosed());

      byte[] tooLarge = new byte[ScriptUiReloader.MAX_SOURCE_BYTES + 1];
      java.util.Arrays.fill(tooLarge, (byte) ' ');
      Files.write(bundle, tooLarge);
      assertEquals(ScriptUiReloader.PollResult.ERROR, reloader.poll());
      assertNotNull(document.actor("kept"));
      assertFalse(runtime.isClosed());

      Files.write(bundle, new byte[] {(byte) 0xc3, (byte) 0x28});
      assertEquals(ScriptUiReloader.PollResult.ERROR, reloader.poll());
      assertTrue(reloader.lastError().orElseThrow().contains("valid UTF-8"));
      assertNotNull(document.actor("kept"));

      AtomicReference<Throwable> threadFailure = new AtomicReference<>();
      Thread other = new Thread(() -> {
        try {
          reloader.poll();
        } catch (Throwable failure) {
          threadFailure.set(failure);
        }
      });
      other.start();
      other.join();
      assertTrue(threadFailure.get() instanceof IllegalStateException);

      reloader.close();
      assertEquals(ScriptUiReloader.PollResult.CLOSED, reloader.poll());
      assertFalse(runtime.isClosed());
      assertNotNull(document.actor("kept"));
    } finally {
      reloader.close();
      runtime.close();
      document.close();
      Files.deleteIfExists(bundle);
    }
  }

  @Test
  public void executionFailureReportsFatalRuntimeOutcomeWithoutRollingBackAction() throws Exception {
    Path bundle = Files.createTempFile("javity-ui-fatal", ".js");
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    ScriptUiReloader reloader = new ScriptUiReloader(runtime, bundle);
    AtomicInteger sideEffects = new AtomicInteger();
    runtime.bindAction("sideEffect", sideEffects::incrementAndGet);
    try {
      runtime.eval(
          "JavityReact.render(JavityReact.React.createElement(JavityReact.View,{id:'old'}))");
      Files.writeString(bundle, "JavityGame.call('sideEffect');throw new Error('boom')");
      assertEquals(ScriptUiReloader.PollResult.ERROR, reloader.poll());
      assertTrue(reloader.lastError().orElseThrow().contains("runtime was closed"));
      assertEquals(1, sideEffects.get());
      assertTrue(runtime.isClosed());
    } finally {
      reloader.close();
      runtime.close();
      document.close();
      Files.deleteIfExists(bundle);
    }
  }

  @Test
  public void reloaderCloseAndLastErrorAreThreadConfined() throws Exception {
    Path bundle = Files.createTempFile("javity-ui-thread", ".js");
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    ScriptUiReloader reloader = new ScriptUiReloader(runtime, bundle);
    try {
      AtomicReference<Throwable> constructionFailure = new AtomicReference<>();
      Thread constructorThread =
          new Thread(
              () -> {
                try {
                  new ScriptUiReloader(runtime, bundle);
                } catch (Throwable failure) {
                  constructionFailure.set(failure);
                }
              });
      constructorThread.start();
      constructorThread.join();
      assertTrue(constructionFailure.get() instanceof IllegalStateException);

      java.util.List<Runnable> operations =
          java.util.List.of(reloader::close, () -> reloader.lastError());
      for (Runnable operation : operations) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
          try {
            operation.run();
          } catch (Throwable caught) {
            failure.set(caught);
          }
        });
        other.start();
        other.join();
        assertTrue(failure.get() instanceof IllegalStateException);
      }
      assertFalse(document.isClosed());
      assertFalse(runtime.isClosed());
      runtime.close();
      assertThrows(IllegalStateException.class, () -> new ScriptUiReloader(runtime, bundle));
    } finally {
      reloader.close();
      runtime.close();
      document.close();
      Files.deleteIfExists(bundle);
    }
  }
}
