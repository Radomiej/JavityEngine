package org.javity.script.graal;

import java.io.IOException;
import java.util.Objects;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.javity.script.ScriptEngineException;
import org.javity.script.ScriptUiEngine;

/** GraalJS transport implementation; host policy and callback lifetimes live in javity-script. */
public final class GraalScriptUiEngine implements ScriptUiEngine {
  private Context context = newContext();

  @Override
  public void defineFunction(String name, HostFunction function) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(function, "function");
    context.getBindings("js").putMember(name, (ProxyExecutable) args -> {
      Object[] primitives = new Object[args.length];
      for (int i = 0; i < args.length; i++) primitives[i] = readPrimitive(args[i]);
      try {
        return writePrimitive(function.invoke(primitives));
      } catch (RuntimeException ex) {
        throw new ScriptEngineException(ex.getMessage(), ex);
      }
    });
  }

  @Override
  public Object evaluate(String source) {
    try {
      return writePrimitive(context.eval("js", source));
    } catch (PolyglotException ex) {
      if (ex.isHostException()) {
        Throwable host = ex.asHostException();
        if (host instanceof ScriptEngineException wrapped && wrapped.getCause() != null)
          throw new ScriptEngineException(wrapped.getCause().getMessage(), wrapped.getCause());
        throw new ScriptEngineException(host.getMessage(), host);
      }
      throw failure("JavaScript evaluation failed", ex);
    }
  }

  @Override
  public void checkSyntax(String source) {
    try {
      context.parse(Source.newBuilder("js", source, "javity-reload.js").build());
    } catch (IOException | PolyglotException ex) {
      throw failure("JavaScript syntax check failed", ex);
    }
  }

  @Override
  public void close() {
    if (context == null) return;
    try {
      context.close();
    } catch (PolyglotException ex) {
      throw failure("GraalJS close failed", ex);
    } finally {
      context = null;
    }
  }

  private static Object readPrimitive(Value value) {
    if (value == null || value.isNull()) return null;
    if (value.isHostObject()) throw new IllegalArgumentException("Host objects are not accepted");
    if (value.isString()) return value.asString();
    if (value.isBoolean()) return value.asBoolean();
    if (value.isNumber()) {
      double number = value.asDouble();
      if (!Double.isFinite(number)) throw new IllegalArgumentException("Numbers must be finite");
      return number;
    }
    throw new IllegalArgumentException("Only primitive JSON values may cross the script host bridge");
  }

  private static Object writePrimitive(Object value) {
    if (value == null || value instanceof String || value instanceof Boolean) return value;
    if (value instanceof Number number && Double.isFinite(number.doubleValue())) return number.doubleValue();
    if (value instanceof Value guestValue) {
      try { return readPrimitive(guestValue); }
      catch (IllegalArgumentException ignored) { return null; }
    }
    throw new IllegalArgumentException("Only null, String, Boolean and finite Number values may cross the script host bridge");
  }

  private static ScriptEngineException failure(String prefix, Throwable cause) {
    String message = cause.getMessage();
    return new ScriptEngineException(prefix + (message == null ? "" : ": " + message), cause);
  }

  private static Context newContext() {
    return Context.newBuilder("js")
        .allowHostAccess(HostAccess.NONE)
        .allowHostClassLookup(name -> false)
        .allowIO(IOAccess.NONE)
        .allowNativeAccess(false)
        .option("js.foreign-object-prototype", "false")
        .build();
  }
}
