package org.javity.effects;

import java.util.*;
import java.util.function.Supplier;
import org.javity.core.GameObject;

/** Immutable, reusable description of a timeline. Each play creates fresh execution state. */
public final class Feedback {
  private final NodeFactory factory;
  private final Set<Property> properties;

  private Feedback(NodeFactory factory, Set<Property> properties) {
    this.factory = factory;
    this.properties = Set.copyOf(properties);
  }

  public static Feedback tween(FloatTarget target, float to, double duration, Ease ease) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(ease, "ease");
    finite(to, "destination");
    duration(duration);
    return new Feedback(
        () -> TimelineRunner.tween(target, to, duration, ease),
        Set.of(new Property(target.identity(), target.propertyKey(), target.gameObject())));
  }

  public static Feedback waitFor(double duration) {
    duration(duration);
    return new Feedback(() -> new WaitNode(duration), Set.of());
  }

  public static Feedback callback(Runnable callback) {
    Objects.requireNonNull(callback, "callback");
    return new Feedback(() -> new CallbackNode(callback), Set.of());
  }

  /** Creates a fresh custom effect only when its node first becomes eligible to run. */
  public static Feedback custom(double duration, Supplier<? extends FeedbackEffect> factory) {
    duration(duration);
    Objects.requireNonNull(factory, "factory");
    return new Feedback(() -> new CustomNode(duration, factory), Set.of());
  }

  /**
   * Acquires one independently-owned resource per eligible playback and closes it on every exit.
   */
  public static Feedback resource(double duration, Supplier<? extends AutoCloseable> factory) {
    Objects.requireNonNull(factory, "factory");
    return custom(
        duration,
        () -> {
          AutoCloseable resource = Objects.requireNonNull(factory.get(), "resource factory result");
          return new FeedbackEffect() {
            @Override
            public void close() {
              try {
                resource.close();
              } catch (RuntimeException | Error failure) {
                throw failure;
              } catch (Exception failure) {
                throw new IllegalStateException("Could not close feedback resource", failure);
              }
            }
          };
        });
  }

  public static Feedback sequence(Feedback... children) {
    List<Feedback> copy = children(children);
    Set<Property> properties = new HashSet<>();
    copy.forEach(child -> properties.addAll(child.properties));
    return new Feedback(() -> new SequenceNode(copy), properties);
  }

  public static Feedback parallel(Feedback... children) {
    List<Feedback> copy = children(children);
    Set<Property> properties = new HashSet<>();
    for (Feedback child : copy) {
      for (Property property : child.properties)
        if (!properties.add(property))
          throw new IllegalArgumentException(
              "Parallel feedback writes the same property more than once");
    }
    return new Feedback(() -> new ParallelNode(copy), properties);
  }

  TimelineRunner.Node newPlayback() {
    return factory.create();
  }

  boolean targetsOnly(GameObject owner) {
    return properties.stream().allMatch(property -> property.owner == owner);
  }

  @FunctionalInterface
  private interface NodeFactory {
    TimelineRunner.Node create();
  }

  private static List<Feedback> children(Feedback[] children) {
    Objects.requireNonNull(children, "children");
    List<Feedback> copy = new ArrayList<>(children.length);
    for (Feedback child : children) copy.add(Objects.requireNonNull(child, "child"));
    return List.copyOf(copy);
  }

  private static void duration(double duration) {
    if (!Double.isFinite(duration) || duration < 0)
      throw new IllegalArgumentException("Duration must be finite and non-negative");
  }

  private static void finite(float value, String what) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException(what + " must be finite");
  }

  private static final class Property {
    final Object identity;
    final String key;
    final GameObject owner;

    Property(Object identity, String key, GameObject owner) {
      this.identity = identity;
      this.key = key;
      this.owner = owner;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Property p && identity == p.identity && key.equals(p.key);
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(identity) + key.hashCode();
    }
  }

  private static final class WaitNode implements TimelineRunner.Node {
    final double duration;
    double elapsed;

    WaitNode(double duration) {
      this.duration = duration;
    }

    @Override
    public TimelineRunner.Result advance(double delta, TimelineRunner.Execution execution) {
      double consumed = Math.min(delta, duration - elapsed);
      elapsed += consumed;
      return new TimelineRunner.Result(consumed, elapsed >= duration);
    }
  }

  private static final class CallbackNode implements TimelineRunner.Node {
    final Runnable callback;
    boolean ran;

    CallbackNode(Runnable callback) {
      this.callback = callback;
    }

    @Override
    public TimelineRunner.Result advance(double delta, TimelineRunner.Execution execution) {
      if (!ran) {
        ran = true;
        execution.callback(callback);
      }
      return new TimelineRunner.Result(0, true);
    }
  }

  private static final class CustomNode implements TimelineRunner.Node {
    final double duration;
    final Supplier<? extends FeedbackEffect> factory;
    FeedbackEffect effect;
    double elapsed;
    boolean started;
    boolean closed;

    CustomNode(double duration, Supplier<? extends FeedbackEffect> factory) {
      this.duration = duration;
      this.factory = factory;
    }

    @Override
    public TimelineRunner.Result advance(double delta, TimelineRunner.Execution execution) {
      if (!started) {
        started = true;
        execution.invoke(
            () -> {
              FeedbackEffect acquired =
                  Objects.requireNonNull(factory.get(), "custom factory result");
              if (closed || !execution.isRunning()) execution.cleanup(acquired::close);
              else effect = acquired;
            });
        if (!execution.isRunning() || effect == null) return new TimelineRunner.Result(0, true);
        execution.invoke(effect::start);
        if (!execution.isRunning()) return new TimelineRunner.Result(0, true);
      }
      double consumed = duration == 0 ? 0 : Math.min(delta, duration - elapsed);
      elapsed += consumed;
      execution.invoke(() -> effect.update(elapsed, consumed));
      if (!execution.isRunning()) return new TimelineRunner.Result(consumed, true);
      if (elapsed >= duration) {
        execution.invoke(effect::complete);
        return new TimelineRunner.Result(consumed, true);
      }
      return new TimelineRunner.Result(consumed, false);
    }

    @Override
    public void close(TimelineRunner.Execution execution) {
      if (closed) return;
      closed = true;
      FeedbackEffect owned = effect;
      effect = null;
      if (owned != null) execution.cleanup(owned::close);
    }
  }

  private static final class SequenceNode implements TimelineRunner.Node {
    final List<Feedback> children;
    int index;
    TimelineRunner.Node current;

    SequenceNode(List<Feedback> children) {
      this.children = children;
    }

    @Override
    public TimelineRunner.Result advance(double delta, TimelineRunner.Execution execution) {
      double remaining = delta, consumedTotal = 0;
      while (index < children.size() && executionHandleRunning(execution)) {
        if (current == null) current = children.get(index).newPlayback();
        TimelineRunner.Result result = current.advance(remaining, execution);
        consumedTotal += result.consumed();
        remaining = Math.max(0, remaining - result.consumed());
        if (!execution.isRunning() || current == null)
          return new TimelineRunner.Result(consumedTotal, false);
        if (!result.done()) return new TimelineRunner.Result(consumedTotal, false);
        execution.closeNode(current);
        current = null;
        index++;
      }
      return new TimelineRunner.Result(consumedTotal, index >= children.size());
    }

    private static boolean executionHandleRunning(TimelineRunner.Execution execution) {
      return execution.isRunning();
    }

    @Override
    public void close(TimelineRunner.Execution execution) {
      TimelineRunner.Node started = current;
      current = null;
      if (started != null) execution.closeNode(started);
    }
  }

  private static final class ParallelNode implements TimelineRunner.Node {
    final List<Feedback> children;
    final TimelineRunner.Node[] nodes;
    final boolean[] done;

    ParallelNode(List<Feedback> children) {
      this.children = children;
      nodes = new TimelineRunner.Node[children.size()];
      done = new boolean[children.size()];
    }

    @Override
    public TimelineRunner.Result advance(double delta, TimelineRunner.Execution execution) {
      double consumed = 0;
      for (int i = 0; i < nodes.length && execution.isRunning(); i++)
        if (!done[i]) {
          if (nodes[i] == null) nodes[i] = children.get(i).newPlayback();
          TimelineRunner.Result result = nodes[i].advance(delta, execution);
          consumed = Math.max(consumed, result.consumed());
          if (result.done()) {
            execution.closeNode(nodes[i]);
            nodes[i] = null;
            done[i] = true;
          }
        }
      for (boolean value : done) if (!value) return new TimelineRunner.Result(consumed, false);
      return new TimelineRunner.Result(consumed, true);
    }

    @Override
    public void close(TimelineRunner.Execution execution) {
      Throwable failure = null;
      for (int i = 0; i < nodes.length; i++) {
        TimelineRunner.Node node = nodes[i];
        nodes[i] = null;
        if (node == null) continue;
        try {
          execution.closeNode(node);
        } catch (RuntimeException | Error next) {
          failure = TimelineRunner.appendFailure(failure, next);
        }
      }
      TimelineRunner.rethrow(failure);
    }
  }
}
