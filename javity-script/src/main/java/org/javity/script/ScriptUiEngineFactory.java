package org.javity.script;

/** Platform-selected JavaScript engine provider. */
@FunctionalInterface
public interface ScriptUiEngineFactory {
  ScriptUiEngine create();
}
