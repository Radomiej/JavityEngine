package org.javity.demo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Smoke-only GL buffer event tracker; records generation, binding, and deletion intervals. */
final class Physics3DGlBufferTracker {
  private final GL20 delegate;
  private final List<Integer> generated = new ArrayList<>();
  private final List<Integer> bound = new ArrayList<>();
  private final List<Integer> deleted = new ArrayList<>();
  private final List<Integer> programsCreated = new ArrayList<>();
  private final List<Integer> programsUsed = new ArrayList<>();
  private final List<Integer> programsDeleted = new ArrayList<>();
  private final List<Integer> shadersCreated = new ArrayList<>();
  private final List<Integer> shadersDeleted = new ArrayList<>();
  private GL20 proxy;

  Physics3DGlBufferTracker(GL20 delegate) {
    this.delegate = delegate;
  }

  void install() {
    proxy =
        (GL20)
            Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[] {GL20.class},
                (p, method, args) -> {
                  try {
                    Object result = method.invoke(delegate, args);
                    String name = method.getName();
                    if (name.equals("glGenBuffer") && result instanceof Integer id && id != 0)
                      generated.add(id);
                    if (name.equals("glGenBuffers")
                        && args != null
                        && args.length > 1
                        && args[1] instanceof IntBuffer ids) {
                      IntBuffer copy = ids.duplicate();
                      for (int i = copy.position(); i < copy.limit(); i++)
                        if (copy.get(i) != 0) generated.add(copy.get(i));
                    }
                    if (name.equals("glBindBuffer")
                        && args != null
                        && args.length > 1
                        && (Integer) args[1] != 0) bound.add((Integer) args[1]);
                    if (name.equals("glDeleteBuffer") && args != null && args.length > 0)
                      deleted.add((Integer) args[0]);
                    if (name.equals("glDeleteBuffers")
                        && args != null
                        && args.length > 1
                        && args[1] instanceof IntBuffer ids) {
                      IntBuffer copy = ids.duplicate();
                      for (int i = copy.position(); i < copy.limit(); i++) deleted.add(copy.get(i));
                    }
                    if (name.equals("glCreateProgram") && result instanceof Integer id && id != 0)
                      programsCreated.add(id);
                    if (name.equals("glUseProgram")
                        && args != null
                        && args.length > 0
                        && (Integer) args[0] != 0) programsUsed.add((Integer) args[0]);
                    if (name.equals("glDeleteProgram") && args != null && args.length > 0)
                      programsDeleted.add((Integer) args[0]);
                    if (name.equals("glCreateShader") && result instanceof Integer id && id != 0)
                      shadersCreated.add(id);
                    if (name.equals("glDeleteShader") && args != null && args.length > 0)
                      shadersDeleted.add((Integer) args[0]);
                    return result;
                  } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                  }
                });
    Gdx.gl = proxy;
    Gdx.gl20 = proxy;
  }

  int generationCount() {
    return generated.size();
  }

  int deletionCount() {
    return deleted.size();
  }

  int programGenerationCount() {
    return programsCreated.size();
  }

  int programDeletionCount() {
    return programsDeleted.size();
  }

  int shaderGenerationCount() {
    return shadersCreated.size();
  }

  int shaderDeletionCount() {
    return shadersDeleted.size();
  }

  Set<Integer> livePrograms(int start, int end) {
    Set<Integer> ids = new HashSet<>();
    for (int i = Math.max(0, start); i < Math.min(end, programsCreated.size()); i++) {
      int id = programsCreated.get(i);
      if (programsUsed.contains(id) && delegate.glIsProgram(id)) ids.add(id);
    }
    return Set.copyOf(ids);
  }

  Set<Integer> createdShaders(int start, int end) {
    Set<Integer> ids = new HashSet<>();
    for (int i = Math.max(0, start); i < Math.min(end, shadersCreated.size()); i++)
      ids.add(shadersCreated.get(i));
    return Set.copyOf(ids);
  }

  void requireProgramsDeleted(Set<Integer> ids, int start, int end, String phase) {
    requireDeletedObjects(
        ids, programsDeleted, start, end, delegate::glIsProgram, "program", phase);
  }

  void requireShadersDeleted(Set<Integer> ids, int start, int end, String phase) {
    requireDeletedObjects(ids, shadersDeleted, start, end, delegate::glIsShader, "shader", phase);
  }

  private static void requireDeletedObjects(
      Set<Integer> ids,
      List<Integer> deleted,
      int start,
      int end,
      java.util.function.IntPredicate isObject,
      String kind,
      String phase) {
    for (int id : ids) {
      int count = 0;
      for (int i = Math.max(0, start); i < Math.min(end, deleted.size()); i++)
        if (deleted.get(i) == id) count++;
      if (count != 1 || isObject.test(id))
        throw new IllegalStateException(
            "Physics3D GPU smoke: "
                + kind
                + " "
                + id
                + " deletion evidence failed in "
                + phase
                + " (deletes="
                + count
                + ")");
    }
  }

  Set<Integer> liveGenerated(int start, int end) {
    Set<Integer> ids = new HashSet<>();
    for (int i = Math.max(0, start); i < Math.min(end, generated.size()); i++) {
      int id = generated.get(i);
      if (bound.contains(id) && delegate.glIsBuffer(id)) ids.add(id);
    }
    return Set.copyOf(ids);
  }

  Set<Integer> allGenerated(int start, int end) {
    Set<Integer> ids = new HashSet<>();
    for (int i = Math.max(0, start); i < Math.min(end, generated.size()); i++)
      ids.add(generated.get(i));
    return Set.copyOf(ids);
  }

  void requireDeleted(Set<Integer> ids, int start, int end, String phase) {
    for (int id : ids) {
      int count = 0;
      for (int i = Math.max(0, start); i < Math.min(end, deleted.size()); i++)
        if (deleted.get(i) == id) count++;
      if (count != 1 || delegate.glIsBuffer(id))
        throw new IllegalStateException(
            "Physics3D GPU smoke: buffer "
                + id
                + " deletion evidence failed in "
                + phase
                + " (deletes="
                + count
                + ")");
    }
  }

  void restore(GL20 original) {
    Gdx.gl = original;
    Gdx.gl20 = original;
  }
}
