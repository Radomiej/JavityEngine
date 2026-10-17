package org.javity.script;

/** Runtime failure at the JavaScript/native UI boundary. */
public final class ScriptUiException extends RuntimeException {
  public ScriptUiException(String message) {
    super(message);
  }

  public ScriptUiException(String message, Throwable cause) {
    super(message, cause);
  }
}
