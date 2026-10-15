package org.javity.animation;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javity.core.Component;
import org.javity.render.ModelRenderer;

/** Per-renderer TRS pose composer for a 1D mixer and sparse override layers. */
public final class ModelPoseAnimator extends Component implements ModelPosePlayback.Host {
  private static final int MAX_LAYERS = 8;
  private final List<PoseNode> nodes = new ArrayList<>();
  private final Map<String, Integer> pathIndices = new HashMap<>();
  private final Map<String, List<Integer>> idIndices = new HashMap<>();
  private final IdentityHashMap<Node, Integer> nodeIndices = new IdentityHashMap<>();
  private final Map<String, ClipData> clips = new HashMap<>();
  private final List<ModelAnimationLayer> layers = new ArrayList<>();
  private final PlaybackClock.Step[] layerSteps = new PlaybackClock.Step[MAX_LAYERS];
  private final ModelPosePlayback[] layerPlaybacks = new ModelPosePlayback[MAX_LAYERS];
  private ModelRenderer renderer;
  private ModelPosePlayback current;

  @Override
  public void onCreate() {
    checkExclusiveOwners();
    setupRenderer();
  }

  public ModelPosePlayback play(LinearModelMixer mixer, PlayMode mode) {
    Objects.requireNonNull(mixer);
    Objects.requireNonNull(mode);
    requireAttachedAndUsable();
    setupRenderer();
    for (LinearModelMixer.Entry entry : mixer.entries()) clip(entry.animationName());
    PlaybackClock clock =
        new PlaybackClock("<mixer>", mixer.cycleDuration(), mode, 0, 1, List.of());
    ModelPosePlayback next =
        new ModelPosePlayback(clock, this, mixer, mixer.entries().get(0).threshold());
    replaceBase(next);
    composeNow();
    return next;
  }

  public ModelPosePlayback play(LinearModelMixer mixer, PlayMode mode, double parameter) {
    if (!Double.isFinite(parameter))
      throw new IllegalArgumentException("Mixer parameter must be finite");
    ModelPosePlayback playback = play(mixer, mode);
    playback.parameter = clampParameter(mixer, parameter);
    composeNow();
    return playback;
  }

  /** Starts one named base clip using shared transition timing and marker semantics. */
  public ModelPosePlayback play(AnimationTransition transition) {
    Objects.requireNonNull(transition);
    requireAttachedAndUsable();
    setupRenderer();
    if (transition.fadeSeconds() > 0)
      throw new IllegalArgumentException("Model pose clip crossfade is unsupported");
    ClipData data = clip(transition.clipName());
    PlaybackClock clock =
        new PlaybackClock(
            transition.clipName(),
            data.duration,
            transition.mode(),
            transition.normalizedStart(),
            transition.speed(),
            transition.markers());
    ModelPosePlayback next = new ModelPosePlayback(clock, this, null, 0);
    replaceBase(next);
    composeNow();
    return next;
  }

  public ModelPosePlayback play(String animationName, PlayMode mode) {
    return play(AnimationTransition.clip(animationName).mode(mode));
  }

  private void replaceBase(ModelPosePlayback next) {
    if (current != null) current.clock.stop();
    current = next;
  }

  public ModelPosePlayback current() {
    return current;
  }

  public ModelAnimationLayer addLayer(String name, NodeMask mask) {
    Objects.requireNonNull(mask);
    requireAttachedAndUsable();
    setupRenderer();
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Layer name is required");
    if (layers.size() >= MAX_LAYERS)
      throw new IllegalStateException("A model pose supports at most eight layers");
    for (ModelAnimationLayer layer : layers)
      if (layer.name.equals(name))
        throw new IllegalArgumentException("Duplicate model layer: " + name);
    boolean[] resolved = resolveMask(mask);
    ModelAnimationLayer layer = new ModelAnimationLayer(this, name, mask);
    layer.resolvedMask = resolved;
    layers.add(layer);
    return layer;
  }

  public List<ModelAnimationLayer> layers() {
    return List.copyOf(layers);
  }

  long frameIndex() {
    return getGame().getFrameIndex();
  }

  public void stop() {
    requireAttachedAndUsable();
    stopAllPlaybacks();
    resetBindPose();
  }

  @Override
  public void onUpdate(float delta) {
    if (!isOwnerRunnable()) {
      relinquishPose();
      return;
    }
    if (!isRendererLive()) {
      invalidateForRendererChange();
      return;
    }
    if (!Float.isFinite(delta) || delta < 0)
      throw new IllegalArgumentException("Pose delta must be finite and non-negative");

    PlaybackClock.Step baseStep = current == null ? null : current.clock.preflight(delta);
    java.util.Arrays.fill(layerSteps, null);
    java.util.Arrays.fill(layerPlaybacks, null);
    int frameLayerCount = layers.size();
    for (int i = 0; i < frameLayerCount; i++) {
      ModelPosePlayback playback = layers.get(i).current;
      layerPlaybacks[i] = playback;
      if (playback != null) layerSteps[i] = playback.clock.preflight(delta);
    }

    for (int i = 0; i < frameLayerCount; i++) layers.get(i).updateFade(delta);
    ModelPosePlayback base = current;
    if (base != null && baseStep != null) {
      if (!base.clock.fireStart(() -> current == base && isOwnerRunnable() && isRendererLive())) {
        if (current == base && !isOwnerRunnable()) relinquishPose();
      }
    }
    for (int i = 0; i < frameLayerCount; i++) {
      ModelAnimationLayer layer = layers.get(i);
      ModelPosePlayback playback = layerPlaybacks[i];
      PlaybackClock.Step step = layerSteps[i];
      if (playback != null && step != null && layer.current == playback) {
        playback.clock.fireStart(
            () -> layer.current == playback && isOwnerRunnable() && isRendererLive());
        if (!isOwnerRunnable() || !isRendererLive()) {
          relinquishPose();
          return;
        }
      }
    }
    if (!isOwnerRunnable() || !isRendererLive()) {
      relinquishPose();
      return;
    }
    if (base != null && baseStep != null && current == base)
      base.clock.advance(baseStep, () -> current == base && isOwnerRunnable() && isRendererLive());
    if (!isOwnerRunnable() || !isRendererLive()) {
      relinquishPose();
      return;
    }
    for (int i = 0; i < frameLayerCount; i++) {
      ModelAnimationLayer layer = layers.get(i);
      ModelPosePlayback playback = layerPlaybacks[i];
      PlaybackClock.Step step = layerSteps[i];
      if (playback != null && step != null && layer.current == playback)
        playback.clock.advance(
            step, () -> layer.current == playback && isOwnerRunnable() && isRendererLive());
      if (!isOwnerRunnable() || !isRendererLive()) {
        relinquishPose();
        return;
      }
    }
    if (isOwnerRunnable() && isRendererLive()) composeNow();
    else relinquishPose();
  }

  /** Called by ModelRenderer immediately before drawing to observe post-WORLD ownership changes. */
  public boolean prepareForRenderer(ModelRenderer candidate) {
    if (renderer != candidate) {
      if (renderer != null && isRendererPresent()) return false;
      if (isAttachedUsable() && getComponent(ModelRenderer.class) == candidate) {
        if (renderer != null) resetBindPose();
        invalidatePlaybacks();
        renderer = candidate;
        clearIndex();
      } else return false;
    }
    if (!isOwnerRunnable() || !isRendererLive()) {
      relinquishPose();
      return false;
    }
    ensureSetup();
    return true;
  }

  public boolean owns(ModelRenderer candidate) {
    return renderer == candidate && isOwnerRunnable() && isRendererPresent();
  }

  /**
   * True for this owner's stable primary renderer, whether or not the owner is currently enabled.
   */
  public boolean isAssignedRenderer(ModelRenderer candidate) {
    if (renderer == null && isAttachedUsable()) renderer = getComponent(ModelRenderer.class);
    return renderer == candidate && isRendererPresent();
  }

  /** Called by a renderer that observes this component's removal before deferred cleanup. */
  public void rendererOwnershipLost(ModelRenderer candidate) {
    if (renderer != candidate) return;
    resetBindPose();
    invalidatePlaybacks();
    renderer = null;
    clearIndex();
  }

  @Override
  public void onComponentChange(org.javity.core.ComponentChange change) {
    if (change.component() == renderer
        && change.type() == org.javity.core.ComponentChange.Type.REMOVED) {
      resetBindPose();
      invalidatePlaybacks();
      renderer = null;
      clearIndex();
    }
    if (change.type() == org.javity.core.ComponentChange.Type.ADDED) {
      if (change.component() instanceof ModelAnimator)
        throw new IllegalStateException("ModelPoseAnimator conflicts with ModelAnimator");
      if (change.component() instanceof ModelPoseAnimator && change.component() != this)
        throw new IllegalStateException("Only one ModelPoseAnimator may own a renderer");
    }
  }

  @Override
  protected void onRelease() {
    resetBindPose();
    invalidatePlaybacks();
    renderer = null;
    clearIndex();
    layers.clear();
  }

  private void setupRenderer() {
    if (renderer == null) {
      renderer = getComponent(ModelRenderer.class);
      if (renderer == null)
        throw new IllegalStateException("ModelPoseAnimator needs ModelRenderer");
    }
    if (getGameObject().getComponents(ModelPoseAnimator.class).size() > 1)
      throw new IllegalStateException("Only one ModelPoseAnimator may own a renderer");
    checkExclusiveOwners();
    renderer.observePoseOwner(this);
    ensureSetup();
  }

  private void checkExclusiveOwners() {
    if (getComponent(ModelAnimator.class) != null)
      throw new IllegalStateException("ModelPoseAnimator conflicts with ModelAnimator");
    if (getGameObject().getComponents(ModelPoseAnimator.class).size() > 1)
      throw new IllegalStateException("Only one ModelPoseAnimator may own a renderer");
  }

  private void ensureSetup() {
    if (renderer == null || !nodes.isEmpty()) return;
    ModelInstance instance = renderer.instance;
    IdentityHashMap<Node, Integer> newIndices = new IdentityHashMap<>();
    for (Node root : instance.nodes) indexNode(root, root.id, newIndices);
    nodeIndices.putAll(newIndices);
    for (ModelAnimationLayer layer : layers) layer.resolvedMask = resolveMask(layer.mask);
  }

  private void indexNode(Node node, String path, IdentityHashMap<Node, Integer> newIndices) {
    if (node.id == null || node.id.isBlank())
      throw new IllegalArgumentException("Model nodes used by pose animation need non-empty ids");
    int index = nodes.size();
    newIndices.put(node, index);
    PoseNode pose = new PoseNode(node, path, index);
    nodes.add(pose);
    Integer previous = pathIndices.putIfAbsent(path, index);
    if (previous != null) pathIndices.put(path, -1);
    idIndices.computeIfAbsent(node.id, ignored -> new ArrayList<>()).add(index);
    for (Node child : node.getChildren()) indexNode(child, path + "/" + child.id, newIndices);
  }

  private boolean[] resolveMask(NodeMask mask) {
    boolean[] result = new boolean[nodes.size()];
    for (String value : mask.values()) {
      if (mask.usesPaths()) {
        Integer index = pathIndices.get(value);
        if (index == null || index < 0)
          throw new IllegalArgumentException("Unknown or ambiguous node path: " + value);
        result[index] = true;
      } else {
        List<Integer> matches = idIndices.get(value);
        if (matches == null || matches.isEmpty())
          throw new IllegalArgumentException("Unknown node id: " + value);
        if (matches.size() != 1) throw new IllegalArgumentException("Ambiguous node id: " + value);
        result[matches.get(0)] = true;
      }
    }
    return result;
  }

  private ClipData clip(String name) {
    ClipData existing = clips.get(name);
    if (existing != null) return existing;
    Animation animation = null;
    for (Animation candidate : renderer.instance.animations)
      if (Objects.equals(candidate.id, name)) {
        animation = candidate;
        break;
      }
    if (animation == null) throw new IllegalArgumentException("Unknown model animation: " + name);
    if (!Float.isFinite(animation.duration) || animation.duration <= 0)
      throw new IllegalArgumentException(
          "Model animation duration must be finite and positive: " + name);
    NodeTrack[] tracks = new NodeTrack[nodes.size()];
    for (NodeAnimation nodeAnimation : animation.nodeAnimations) {
      Integer index = nodeIndices.get(nodeAnimation.node);
      if (index == null)
        throw new IllegalArgumentException(
            "Animation references a node outside this model instance: " + name);
      if (tracks[index] != null)
        throw new IllegalArgumentException("Duplicate node track in animation: " + name);
      validateChannel(
          nodeAnimation.translation, animation.duration, name, "translation", Vector3.class);
      validateChannel(
          nodeAnimation.rotation, animation.duration, name, "rotation", Quaternion.class);
      validateChannel(nodeAnimation.scaling, animation.duration, name, "scaling", Vector3.class);
      tracks[index] =
          new NodeTrack(nodeAnimation.translation, nodeAnimation.rotation, nodeAnimation.scaling);
    }
    ClipData result = new ClipData(name, animation.duration, tracks);
    clips.put(name, result);
    return result;
  }

  private static void validateChannel(
      Array<?> channel, float duration, String animation, String kind, Class<?> expectedValueType) {
    if (channel == null) return;
    float before = -1;
    for (int i = 0; i < channel.size; i++) {
      Object item = channel.get(i);
      if (!(item instanceof NodeKeyframe<?> key)
          || !Float.isFinite(key.keytime)
          || key.keytime < 0
          || key.keytime > duration
          || key.keytime <= before
          || key.value == null
          || !expectedValueType.isInstance(key.value))
        throw new IllegalArgumentException(
            "Invalid " + kind + " keyframes in animation: " + animation);
      before = key.keytime;
      Object value = key.value;
      if (value instanceof Vector3 vector) {
        if (!finite(vector))
          throw new IllegalArgumentException("Non-finite " + kind + " keyframe in " + animation);
      } else if (value instanceof Quaternion quaternion) {
        double length2 =
            (double) quaternion.x * quaternion.x
                + (double) quaternion.y * quaternion.y
                + (double) quaternion.z * quaternion.z
                + (double) quaternion.w * quaternion.w;
        if (!finite(quaternion) || !Double.isFinite(length2) || length2 == 0)
          throw new IllegalArgumentException("Invalid rotation keyframe in " + animation);
      }
    }
  }

  private static boolean finite(Vector3 value) {
    return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
  }

  private static boolean finite(Quaternion value) {
    return Float.isFinite(value.x)
        && Float.isFinite(value.y)
        && Float.isFinite(value.z)
        && Float.isFinite(value.w);
  }

  ModelPosePlayback createLayerPlayback(ModelAnimationLayer layer, AnimationTransition transition) {
    ClipData data = clip(transition.clipName());
    PlaybackClock clock =
        new PlaybackClock(
            transition.clipName(),
            data.duration,
            transition.mode(),
            transition.normalizedStart(),
            transition.speed(),
            transition.markers());
    return new ModelPosePlayback(clock, layer, null, 0);
  }

  private void sample(ClipData clip, double time, PoseNode node, boolean fallbackBind) {
    node.sampleT.set(fallbackBind ? node.bindT : node.outT);
    node.sampleR.set(fallbackBind ? node.bindR : node.outR);
    node.sampleS.set(fallbackBind ? node.bindS : node.outS);
    NodeTrack track = clip.tracks[node.index];
    if (track == null) return;
    if (track.translation != null && track.translation.size > 0)
      sampleVector(track.translation, time, node.sampleT);
    if (track.rotation != null && track.rotation.size > 0)
      sampleQuaternion(track.rotation, time, node.sampleR, node.keyR);
    if (track.scaling != null && track.scaling.size > 0)
      sampleVector(track.scaling, time, node.sampleS);
  }

  void composeNow() {
    if (renderer == null || nodes.isEmpty() || !isAttachedUsable() || !isRendererPresent()) return;
    if (!isOwnerRunnable()) return;
    ensureSetup();
    LinearModelMixer mixer = current == null ? null : current.mixer;
    ClipData single = current != null && mixer == null ? clip(current.animationName()) : null;
    double phase = current == null ? 0 : current.clock.normalizedTime();
    int lower = -1, upper = -1;
    double mix = 0;
    if (mixer != null) {
      lower = lowerIndex(mixer, current.parameter);
      upper = upperIndex(mixer, current.parameter, lower);
      mix = pairMix(mixer, current.parameter, lower, upper);
    }
    int layerCount = layers.size();
    int nodeCount = nodes.size();
    for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
      PoseNode node = nodes.get(nodeIndex);
      node.outT.set(node.bindT);
      node.outR.set(node.bindR);
      node.outS.set(node.bindS);
      if (single != null) {
        sample(single, phase * single.duration, node, true);
        node.outT.set(node.sampleT);
        node.outR.set(node.sampleR);
        node.outS.set(node.sampleS);
      } else if (mixer != null) {
        ClipData a = clip(mixer.entries().get(lower).animationName());
        ClipData b = clip(mixer.entries().get(upper).animationName());
        sample(a, phase * a.duration, node, true);
        node.tempT.set(node.sampleT);
        node.tempR.set(node.sampleR);
        node.tempS.set(node.sampleS);
        sample(b, phase * b.duration, node, true);
        blendVector(node.tempT, node.sampleT, mix, node.outT);
        blendVector(node.tempS, node.sampleS, mix, node.outS);
        node.outR.set(node.tempR).nor().slerp(node.sampleR.nor(), (float) mix).nor();
      }
      for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
        ModelAnimationLayer layer = layers.get(layerIndex);
        ModelPosePlayback playback = layer.current;
        if (playback == null
            || !playback.clock.isActive()
            || layer.weight <= 0
            || !layer.resolvedMask[node.index]) continue;
        ClipData overlay = clip(playback.animationName());
        NodeTrack track = overlay.tracks[node.index];
        if (track == null) continue;
        double clipTime = playback.clock.normalizedTime() * overlay.duration;
        if (track.translation != null && track.translation.size > 0) {
          sampleVector(track.translation, clipTime, node.tempT);
          blendVector(node.outT, node.tempT, layer.weight, node.outT);
        }
        if (track.rotation != null && track.rotation.size > 0) {
          sampleQuaternion(track.rotation, clipTime, node.tempR, node.keyR);
          node.outR.slerp(node.tempR, (float) layer.weight).nor();
        }
        if (track.scaling != null && track.scaling.size > 0) {
          sampleVector(track.scaling, clipTime, node.tempS);
          blendVector(node.outS, node.tempS, layer.weight, node.outS);
        }
      }
      node.node.translation.set(node.outT);
      node.node.rotation.set(node.outR);
      node.node.scale.set(node.outS);
      node.node.localTransform.set(node.outT, node.outR, node.outS);
      node.node.isAnimated = true;
    }
    renderer.instance.calculateTransforms();
  }

  private static void sampleVector(Array<NodeKeyframe<Vector3>> keys, double time, Vector3 out) {
    int last = keys.size - 1;
    if (time <= keys.get(0).keytime) {
      out.set(keys.get(0).value);
      return;
    }
    if (time >= keys.get(last).keytime) {
      out.set(keys.get(last).value);
      return;
    }
    int low = 0, high = last;
    while (low + 1 < high) {
      int mid = (low + high) >>> 1;
      if (keys.get(mid).keytime <= time) low = mid;
      else high = mid;
    }
    NodeKeyframe<Vector3> a = keys.get(low), b = keys.get(high);
    float alpha = (float) ((time - a.keytime) / (b.keytime - a.keytime));
    blendVector(a.value, b.value, alpha, out);
  }

  private static void blendVector(Vector3 from, Vector3 to, double alpha, Vector3 out) {
    double inverse = 1 - alpha;
    out.set(
        (float) (from.x * inverse + to.x * alpha),
        (float) (from.y * inverse + to.y * alpha),
        (float) (from.z * inverse + to.z * alpha));
  }

  private void sampleQuaternion(
      Array<NodeKeyframe<Quaternion>> keys, double time, Quaternion out, Quaternion scratch) {
    int last = keys.size - 1;
    if (time <= keys.get(0).keytime) {
      normalizeQuaternion(keys.get(0).value, out);
      return;
    }
    if (time >= keys.get(last).keytime) {
      normalizeQuaternion(keys.get(last).value, out);
      return;
    }
    int low = 0, high = last;
    while (low + 1 < high) {
      int mid = (low + high) >>> 1;
      if (keys.get(mid).keytime <= time) low = mid;
      else high = mid;
    }
    NodeKeyframe<Quaternion> a = keys.get(low), b = keys.get(high);
    float alpha = (float) ((time - a.keytime) / (b.keytime - a.keytime));
    normalizeQuaternion(a.value, out);
    normalizeQuaternion(b.value, scratch);
    out.slerp(scratch, alpha).nor();
  }

  private static void normalizeQuaternion(Quaternion source, Quaternion out) {
    double length2 =
        (double) source.x * source.x
            + (double) source.y * source.y
            + (double) source.z * source.z
            + (double) source.w * source.w;
    double inverseLength = 1.0 / Math.sqrt(length2);
    out.set(
        (float) (source.x * inverseLength),
        (float) (source.y * inverseLength),
        (float) (source.z * inverseLength),
        (float) (source.w * inverseLength));
  }

  private static int lowerIndex(LinearModelMixer mixer, double value) {
    int last = mixer.entries().size() - 1;
    if (value <= mixer.entries().get(0).threshold()) return 0;
    if (value >= mixer.entries().get(last).threshold()) return last;
    int low = 0, high = last;
    while (low + 1 < high) {
      int mid = (low + high) >>> 1;
      if (mixer.entries().get(mid).threshold() <= value) low = mid;
      else high = mid;
    }
    return low;
  }

  private static int upperIndex(LinearModelMixer mixer, double value, int lower) {
    int last = mixer.entries().size() - 1;
    if (lower >= last || value <= mixer.entries().get(0).threshold()) return lower;
    return lower + 1;
  }

  private static double pairMix(LinearModelMixer mixer, double value, int lower, int upper) {
    if (lower == upper) return 0;
    double low = mixer.entries().get(lower).threshold();
    double high = mixer.entries().get(upper).threshold();
    double range = high - low;
    if (Double.isFinite(range)) return (value - low) / range;
    return (value * 0.5 - low * 0.5) / (high * 0.5 - low * 0.5);
  }

  private static double clampParameter(LinearModelMixer mixer, double value) {
    return Math.max(
        mixer.entries().get(0).threshold(),
        Math.min(mixer.entries().get(mixer.entries().size() - 1).threshold(), value));
  }

  private void resetBindPose() {
    if (renderer == null || nodes.isEmpty()) return;
    for (PoseNode pose : nodes) {
      pose.node.translation.set(pose.bindT);
      pose.node.rotation.set(pose.bindR);
      pose.node.scale.set(pose.bindS);
      pose.node.isAnimated = false;
    }
    renderer.instance.calculateTransforms();
  }

  private void relinquishPose() {
    invalidatePlaybacks();
    resetBindPose();
  }

  private void stopAllPlaybacks() {
    invalidatePlaybacks();
  }

  private void invalidatePlaybacks() {
    if (current != null) current.clock.stop();
    current = null;
    for (ModelAnimationLayer layer : layers) {
      if (layer.current != null) layer.current.clock.stop();
      layer.current = null;
      layer.fading = false;
    }
  }

  private void invalidateForRendererChange() {
    resetBindPose();
    invalidatePlaybacks();
    renderer = null;
    clearIndex();
  }

  private void clearIndex() {
    nodes.clear();
    pathIndices.clear();
    idIndices.clear();
    nodeIndices.clear();
    clips.clear();
    for (ModelAnimationLayer layer : layers) layer.resolvedMask = null;
  }

  private boolean isAttachedUsable() {
    return getGameObject() != null
        && getGame() != null
        && !getGame().isDisposed()
        && !getGameObject().isDestroyed()
        && getGameObject().containsComponent(this);
  }

  private boolean isOwnerRunnable() {
    return isAttachedUsable() && isInitialized() && isEnabled() && getGameObject().isActive();
  }

  private boolean isRendererPresent() {
    return renderer != null
        && isAttachedUsable()
        && renderer.getGameObject() == getGameObject()
        && getGameObject().containsComponent(renderer);
  }

  private boolean isRendererLive() {
    return isRendererPresent() && renderer.isInitialized() && renderer.isEnabled();
  }

  private void requireAttachedAndUsable() {
    if (!isAttachedUsable())
      throw new IllegalStateException("ModelPoseAnimator must be attached to a live object");
  }

  void requireLayerUsable(ModelAnimationLayer layer) {
    requireAttachedAndUsable();
    setupRenderer();
    if (!layers.contains(layer))
      throw new IllegalStateException("Layer no longer belongs to this animator");
  }

  @Override
  public void requireCurrent(ModelPosePlayback playback) {
    requireAttachedAndUsable();
    setupRenderer();
    if (!isRendererPresent()) {
      relinquishPose();
      throw new IllegalStateException("ModelPoseAnimator needs an attached ModelRenderer");
    }
    if (current != playback || !playback.clock.isActive())
      throw new IllegalStateException("Playback is no longer current");
  }

  @Override
  public ModelPosePlayback restart(ModelPosePlayback playback) {
    requireCurrent(playback);
    playback.clock.restart();
    composeNow();
    return playback;
  }

  @Override
  public ModelPosePlayback seek(ModelPosePlayback playback, double seconds) {
    requireCurrent(playback);
    if (!Double.isFinite(seconds) || seconds < 0 || seconds > playback.duration())
      throw new IllegalArgumentException(
          "Seek time must be finite and within the playback duration");
    playback.clock.seek(seconds);
    composeNow();
    return playback;
  }

  @Override
  public void parameter(ModelPosePlayback playback, double value) {
    playback.parameter = clampParameter(playback.mixer, value);
    composeNow();
  }

  @Override
  public ModelPosePlayback stopped(ModelPosePlayback playback) {
    requireCurrent(playback);
    playback.clock.stop();
    current = null;
    composeNow();
    return playback;
  }

  private static final class PoseNode {
    final Node node;
    final String path;
    final int index;
    final Vector3 bindT, bindS, outT = new Vector3(), outS = new Vector3();
    final Vector3 sampleT = new Vector3(),
        sampleS = new Vector3(),
        tempT = new Vector3(),
        tempS = new Vector3();
    final Quaternion bindR,
        outR = new Quaternion(),
        sampleR = new Quaternion(),
        tempR = new Quaternion(),
        keyR = new Quaternion();

    PoseNode(Node node, String path, int index) {
      this.node = node;
      this.path = path;
      this.index = index;
      bindT = node.translation.cpy();
      bindS = node.scale.cpy();
      bindR = node.rotation.cpy().nor();
    }
  }

  private record NodeTrack(
      Array<NodeKeyframe<Vector3>> translation,
      Array<NodeKeyframe<Quaternion>> rotation,
      Array<NodeKeyframe<Vector3>> scaling) {}

  private record ClipData(String name, float duration, NodeTrack[] tracks) {}
}
