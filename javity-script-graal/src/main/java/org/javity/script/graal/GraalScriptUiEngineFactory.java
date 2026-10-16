package org.javity.script.graal;

import org.javity.script.ScriptUiEngine;
import org.javity.script.ScriptUiEngineFactory;

/** Desktop JDK provider retaining the restricted GraalJS configuration. */
public final class GraalScriptUiEngineFactory implements ScriptUiEngineFactory {
  @Override public ScriptUiEngine create() { return new GraalScriptUiEngine(); }
}
