package org.javity.script;

/** Engine failure translated at the platform boundary. */
public final class ScriptEngineException extends RuntimeException {
  public ScriptEngineException(String message, Throwable cause) { super(message, cause); }
}
