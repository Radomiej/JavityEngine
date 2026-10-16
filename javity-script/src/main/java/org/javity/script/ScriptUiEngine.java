package org.javity.script;

/** Minimal synchronous engine operations used by the shared script host policy. */
public interface ScriptUiEngine extends AutoCloseable {
  @FunctionalInterface
  interface HostFunction {
    Object invoke(Object[] primitiveArguments);
  }

  void defineFunction(String name, HostFunction function);
  Object evaluate(String source);
  /** Executes a complete source unit when its completion value is intentionally ignored. */
  default void evaluateDiscardingResult(String source) { evaluate(source); }
  void checkSyntax(String source);
  @Override void close();
}
