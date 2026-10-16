package org.javity.animation;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.*;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import java.util.*;

/** Immutable primitive snapshot. Capture only clips actually consumed by a composition. */
public final class SharedPoseData {
  public record LayoutNode(String path, int parent) {}

  public record Track(Channel translation, Channel rotation, Channel scaling) {}

  public record Clip(String name, float duration, List<Track> tracks) {
    public Clip {
      tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    public Track track(int node) {
      return tracks.get(node);
    }
  }

  /** Raw source quaternion components are preserved; normalization belongs to the sampler. */
  public record Channel(int width, List<Integer> bits) {
    public Channel {
      bits = List.copyOf(bits);
    }

    public int size() {
      return bits.size() / (width + 1);
    }

    public float time(int key) {
      return Float.intBitsToFloat(bits.get(key * (width + 1)));
    }

    public float component(int key, int component) {
      return Float.intBitsToFloat(bits.get(key * (width + 1) + 1 + component));
    }
  }

  private final int hash;
  private final Object source;
  private final long revision;
  private final List<LayoutNode> layout;
  private final List<Integer> bindBits;
  private final List<Clip> clips;

  private SharedPoseData(
      Object source, long revision, List<LayoutNode> layout, float[] bind, List<Clip> clips) {
    this.source = Objects.requireNonNull(source);
    this.revision = revision;
    this.layout = List.copyOf(layout);
    List<Integer> bits = new ArrayList<>(bind.length);
    for (float value : bind) bits.add(Float.floatToRawIntBits(value));
    this.bindBits = List.copyOf(bits);
    this.clips = List.copyOf(clips);
    hash =
        Objects.hash(System.identityHashCode(source), revision, this.layout, bindBits, this.clips);
  }

  /** bindLocalTrs is the owner's captured preorder bind T(3), R(4), S(3), not live output. */
  public static SharedPoseData capture(
      ModelInstance instance,
      Object source,
      long revision,
      float[] bindLocalTrs,
      String... clipNames) {
    List<Animation> selected = new ArrayList<>();
    for (String name : clipNames) {
      Animation found = null;
      for (Animation candidate : instance.animations)
        if (Objects.equals(name, candidate.id)) {
          found = candidate;
          break;
        }
      if (found == null) throw new IllegalArgumentException("Unknown model animation: " + name);
      selected.add(found);
    }
    return captureAnimations(instance, source, revision, bindLocalTrs, selected);
  }

  /** Also accepts selected, previously resolved animations; no unused animation is inspected. */
  public static SharedPoseData captureAnimations(
      ModelInstance instance,
      Object source,
      long revision,
      float[] bindLocalTrs,
      List<Animation> selected) {
    List<LayoutNode> layout = new ArrayList<>();
    IdentityHashMap<Node, Integer> indices = new IdentityHashMap<>();
    for (Node node : instance.nodes) index(node, node.id, -1, layout, indices);
    if (bindLocalTrs.length != layout.size() * 10)
      throw new IllegalArgumentException("Bind TRS length differs from rig layout");
    List<Clip> clips = new ArrayList<>();
    for (Animation animation : selected) {
      if (!Float.isFinite(animation.duration) || animation.duration <= 0)
        throw new IllegalArgumentException("Model animation duration must be finite and positive");
      List<Track> tracks = new ArrayList<>(Collections.nCopies(layout.size(), null));
      for (NodeAnimation track : animation.nodeAnimations) {
        Integer node = indices.get(track.node);
        if (node == null)
          throw new IllegalArgumentException(
              "Animation references a node outside this model instance");
        if (tracks.get(node) != null) throw new IllegalArgumentException("Duplicate node track");
        tracks.set(
            node,
            new Track(
                channel(track.translation, 3, animation.duration),
                channel(track.rotation, 4, animation.duration),
                channel(track.scaling, 3, animation.duration)));
      }
      clips.add(new Clip(animation.id, animation.duration, tracks));
    }
    return new SharedPoseData(source, revision, layout, bindLocalTrs, clips);
  }

  /** Already identity-bound sources, in preorder, retained by the owner's sampler. */
  static SharedPoseData captureResolved(
      Object source,
      List<LayoutNode> layout,
      float[] bind,
      List<Animation> selected,
      IdentityHashMap<Node, Integer> indices) {
    List<Clip> clips = new ArrayList<>();
    for (Animation animation : selected) {
      List<Track> tracks = new ArrayList<>(Collections.nCopies(layout.size(), null));
      for (NodeAnimation track : animation.nodeAnimations) {
        int node = indices.get(track.node);
        tracks.set(
            node,
            new Track(
                resolvedChannel(track.translation, 3),
                resolvedChannel(track.rotation, 4),
                resolvedChannel(track.scaling, 3)));
      }
      clips.add(new Clip(animation.id, animation.duration, tracks));
    }
    return new SharedPoseData(source, 0, layout, bind, clips);
  }

  /** A retained source edit that an immutable primitive snapshot cannot represent. */
  static final class UnrepresentableSourceException extends RuntimeException {
    private UnrepresentableSourceException() {
      super(null, null, false, false);
    }
  }

  // ClipData validated these arrays when first resolved. Later public source edits retain the
  // original sampler behavior; snapshotting must not introduce new eager validation failures.
  private static Channel resolvedChannel(Array<?> source, int width) {
    if (source == null) return null;
    List<Integer> bits = new ArrayList<>(source.size * (width + 1));
    for (int i = 0; i < source.size; i++) {
      if (!(source.get(i) instanceof NodeKeyframe<?> key)
          || (width == 3 ? !(key.value instanceof Vector3) : !(key.value instanceof Quaternion)))
        throw new UnrepresentableSourceException();
      bits.add(Float.floatToRawIntBits(key.keytime));
      if (width == 3) {
        Vector3 value = (Vector3) key.value;
        bits.add(Float.floatToRawIntBits(value.x));
        bits.add(Float.floatToRawIntBits(value.y));
        bits.add(Float.floatToRawIntBits(value.z));
      } else {
        Quaternion value = (Quaternion) key.value;
        bits.add(Float.floatToRawIntBits(value.x));
        bits.add(Float.floatToRawIntBits(value.y));
        bits.add(Float.floatToRawIntBits(value.z));
        bits.add(Float.floatToRawIntBits(value.w));
      }
    }
    return new Channel(width, bits);
  }

  /** Compares primitive live inputs without allocating/copying a clip or boxing its values. */
  boolean matchesResolved(
      Object source,
      List<LayoutNode> layout,
      float[] bind,
      List<Animation> selected,
      IdentityHashMap<Node, Integer> indices) {
    if (this.source != source
        || revision != 0
        || !this.layout.equals(layout)
        || bind.length != bindBits.size()
        || selected.size() != clips.size()) return false;
    for (int i = 0; i < bind.length; i++)
      if (Float.floatToRawIntBits(bind[i]) != bindBits.get(i)) return false;
    for (int i = 0; i < selected.size(); i++) {
      Animation live = selected.get(i);
      Clip frozen = clips.get(i);
      if (!Objects.equals(live.id, frozen.name())
          || Float.floatToRawIntBits(live.duration) != Float.floatToRawIntBits(frozen.duration()))
        return false;
      int trackCount = 0;
      for (int n = 0; n < frozen.tracks().size(); n++) if (frozen.track(n) != null) trackCount++;
      if (live.nodeAnimations.size != trackCount) return false;
      for (int t = 0; t < live.nodeAnimations.size; t++) {
        NodeAnimation track = live.nodeAnimations.get(t);
        Integer index = indices.get(track.node);
        if (index == null) return false;
        Track snapshot = frozen.track(index);
        if (snapshot == null
            || !matchesChannel(snapshot.translation(), track.translation)
            || !matchesChannel(snapshot.rotation(), track.rotation)
            || !matchesChannel(snapshot.scaling(), track.scaling)) return false;
      }
    }
    return true;
  }

  private static boolean matchesChannel(Channel frozen, Array<?> live) {
    if (frozen == null || live == null) return frozen == null && live == null;
    if (frozen.size() != live.size) return false;
    int cursor = 0;
    for (int k = 0; k < live.size; k++) {
      if (!(live.get(k) instanceof NodeKeyframe<?> key)
          || frozen.bits().get(cursor++) != Float.floatToRawIntBits(key.keytime)) return false;
      if (key.value instanceof Vector3 v && frozen.width() == 3) {
        if (frozen.bits().get(cursor++) != Float.floatToRawIntBits(v.x)
            || frozen.bits().get(cursor++) != Float.floatToRawIntBits(v.y)
            || frozen.bits().get(cursor++) != Float.floatToRawIntBits(v.z)) return false;
      } else if (key.value instanceof Quaternion q && frozen.width() == 4) {
        if (frozen.bits().get(cursor++) != Float.floatToRawIntBits(q.x)
            || frozen.bits().get(cursor++) != Float.floatToRawIntBits(q.y)
            || frozen.bits().get(cursor++) != Float.floatToRawIntBits(q.z)
            || frozen.bits().get(cursor++) != Float.floatToRawIntBits(q.w)) return false;
      } else return false;
    }
    return true;
  }

  private static void index(
      Node node,
      String path,
      int parent,
      List<LayoutNode> layout,
      IdentityHashMap<Node, Integer> indices) {
    if (node.id == null || node.id.isBlank())
      throw new IllegalArgumentException("Model nodes need non-empty ids");
    int index = layout.size();
    if (indices.put(node, index) != null)
      throw new IllegalArgumentException("Repeated node identity");
    layout.add(new LayoutNode(path, parent));
    for (Node child : node.getChildren())
      index(child, path + "/" + child.id, index, layout, indices);
  }

  private static Channel channel(Array<?> source, int width, float duration) {
    if (source == null) return null;
    List<Integer> bits = new ArrayList<>();
    float before = -1;
    for (Object item : source) {
      if (!(item instanceof NodeKeyframe<?> key)
          || !Float.isFinite(key.keytime)
          || key.keytime < 0
          || key.keytime > duration
          || key.keytime <= before) throw new IllegalArgumentException("Invalid keyframe time");
      before = key.keytime;
      float[] values;
      if (width == 3 && key.value instanceof Vector3 v) values = new float[] {v.x, v.y, v.z};
      else if (width == 4 && key.value instanceof Quaternion q) {
        values = new float[] {q.x, q.y, q.z, q.w};
        double length =
            (double) q.x * q.x + (double) q.y * q.y + (double) q.z * q.z + (double) q.w * q.w;
        if (!Double.isFinite(length) || length == 0)
          throw new IllegalArgumentException("Invalid quaternion");
      } else throw new IllegalArgumentException("Invalid keyframe value type");
      bits.add(Float.floatToRawIntBits(key.keytime));
      for (float value : values) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite keyframe value");
        bits.add(Float.floatToRawIntBits(value));
      }
    }
    return new Channel(width, bits);
  }

  public List<LayoutNode> layout() {
    return layout;
  }

  public Clip clip(int index) {
    return clips.get(index);
  }

  public int clipCount() {
    return clips.size();
  }

  public float bindComponent(int node, int component) {
    return Float.intBitsToFloat(bindBits.get(node * 10 + component));
  }

  public long revision() {
    return revision;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SharedPoseData data
        && source == data.source
        && revision == data.revision
        && layout.equals(data.layout)
        && bindBits.equals(data.bindBits)
        && clips.equals(data.clips);
  }

  @Override
  public int hashCode() {
    return hash;
  }
}
