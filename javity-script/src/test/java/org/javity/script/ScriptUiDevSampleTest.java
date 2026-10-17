package org.javity.script;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.javity.signals.Radio;
import org.javity.ui.declarative.UiDocument;
import org.junit.Test;

public class ScriptUiDevSampleTest {
  @Test
  public void generationKeyRemountsHealthHookAfterHostRebindsItsSignal() {
    UiDocument document = new UiDocument(null);
    ScriptUiRuntime runtime = new ScriptUiRuntime(document);
    Radio firstRadio = new Radio();
    Radio secondRadio = new Radio();
    var oldHealth = firstRadio.channel("health", Integer.class, 10);
    var nextHealth = secondRadio.channel("health", Integer.class, 20);
    var generation = firstRadio.channel("sceneGeneration", Integer.class, 0);
    AtomicInteger observedHealth = new AtomicInteger(-1);
    AtomicInteger observedGeneration = new AtomicInteger(-1);
    AtomicInteger directGeneration = new AtomicInteger(-1);
    AtomicInteger callbackGeneration = new AtomicInteger(-1);
    java.util.List<String> installedSignals = new java.util.ArrayList<>();
    runtime.bindSignal("health", oldHealth);
    runtime.bindSignal("sceneGeneration", generation);
    runtime.bindNumberAction("recordHealth", value -> observedHealth.set((int) value));
    runtime.bindNumberAction("recordGeneration", value -> observedGeneration.set((int) value));
    runtime.bindNumberAction("recordDirectGeneration", value -> directGeneration.set((int) value));
    runtime.bindNumberAction(
        "recordCallbackGeneration", value -> callbackGeneration.set((int) value));
    runtime.bindStringAction("recordSignalInstall", installedSignals::add);
    try {
      runtime.reload(
          "const React=JavityReact.React;function useSignal(name){const"
              + " [value,setValue]=React.useState(()=>JavityGame.get(name));"
              + "React.useEffect(()=>{JavityGame.call('recordSignalInstall',name);setValue(JavityGame.get(name));const"
              + " callback=name==='sceneGeneration'?"
              + "next=>{JavityGame.call('recordCallbackGeneration',next);setValue(next)}:setValue;const"
              + " id=JavityGame.subscribe(name,callback);return()=>JavityGame.unsubscribe(id)},[name]);return"
              + " value};function DevelopmentHud({generation}){const health=useSignal('health');"
              + "React.useLayoutEffect(()=>{JavityGame.call('recordHealth',health)},[health]);return"
              + " React.createElement(JavityReact.View,{id:'health-'+generation})};function"
              + " DevelopmentHudRoot(){const generation=useSignal('sceneGeneration');"
              + "JavityGame.call('recordGeneration',generation);return"
              + " React.createElement(DevelopmentHud,{key:generation,generation})};"
              + "JavityReact.render(React.createElement(DevelopmentHudRoot));"
              + "JavityGame.subscribe('sceneGeneration',value=>JavityGame.call('recordDirectGeneration',value));");
      assertEquals(10, observedHealth.get());
      assertTrue(installedSignals.contains("health"));
      assertTrue(installedSignals.contains("sceneGeneration"));
      oldHealth.set(11);
      assertEquals(11, observedHealth.get());

      runtime.bindSignal("health", nextHealth);
      nextHealth.set(19);
      assertEquals(
          "the old component subscription is gone after host rebind", 11, observedHealth.get());

      generation.set(1);
      assertEquals(1, callbackGeneration.get());
      assertEquals(1, directGeneration.get());
      assertEquals(1, observedGeneration.get());
      assertEquals(
          "the generation-keyed child reads the newly bound value", 19, observedHealth.get());
      nextHealth.set(21);
      assertEquals("the remounted health hook receives later events", 21, observedHealth.get());
    } finally {
      runtime.close();
      document.close();
      firstRadio.dispose();
      secondRadio.dispose();
    }
  }
}
