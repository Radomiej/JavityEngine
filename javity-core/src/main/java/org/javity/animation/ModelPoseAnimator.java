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
  private final TransitionState[] layerTransitions = new TransitionState[MAX_LAYERS];
  private final long[] layerTransitionRevisions = new long[MAX_LAYERS];
  private final List<SharedPoseData.LayoutNode> sharedLayout = new ArrayList<>();
  private float[] bindLocalTrs;
  private SharedPoseData sharedData;
  private ModelRenderer renderer;
  private ModelPosePlayback current;
  private TransitionState baseTransition;

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
    ClipData data = clip(transition.clipName());
    PlaybackClock clock =
        new PlaybackClock(
            transition.clipName(),
            data.duration,
            transition.mode(),
            transition.normalizedStart(),
            transition.speed(),
            transition.markers());
    clock.allowPauseAfterCompletion();
    ModelPosePlayback next = new ModelPosePlayback(clock, this, null, 0);
    PoseValue[] frozen = transition.fadeSeconds() > 0 ? captureBaseContribution() : null;
    replaceBase(next);
    if (frozen != null)
      baseTransition = new TransitionState(frozen, transition.fadeSeconds(), frameIndex() + 1);
    composeNow();
    return next;
  }

  public ModelPosePlayback play(String animationName, PlayMode mode) {
    return play(AnimationTransition.clip(animationName).mode(mode));
  }

  private void replaceBase(ModelPosePlayback next) {
    if (current != null) current.clock.stop();
    current = next;
    baseTransition = null;
  }

  void replaceLayer(ModelAnimationLayer layer, ModelPosePlayback next, double fadeSeconds) {
    PoseValue[] frozen = fadeSeconds > 0 ? captureLayerContribution(layer) : null;
    if (layer.current != null) layer.current.clock.stop();
    layer.current = next;
    layer.transition =
        frozen == null ? null : new TransitionState(frozen, fadeSeconds, frameIndex() + 1);
    composeNow();
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
    ModelPosePlayback plannedBase = current;
    TransitionState plannedBaseFade = baseTransition;
    long plannedBaseFadeRevision = plannedBaseFade == null ? -1 : plannedBaseFade.revision;
    java.util.Arrays.fill(layerSteps, null);
    java.util.Arrays.fill(layerPlaybacks, null);
    int frameLayerCount = layers.size();
    for (int i = 0; i < frameLayerCount; i++) {
      ModelPosePlayback playback = layers.get(i).current;
      layerPlaybacks[i] = playback;
      if (playback != null) layerSteps[i] = playback.clock.preflight(delta);
    }

    java.util.Arrays.fill(layerTransitions, null);
    java.util.Arrays.fill(layerTransitionRevisions, -1);
    for (int i = 0; i < frameLayerCount; i++) {
      layerTransitions[i] = layers.get(i).transition;
      if (layerTransitions[i] != null) layerTransitionRevisions[i] = layerTransitions[i].revision;
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
      if (playback != null
          && step != null
          && layer.current == playback
          && playback.clock.revision() == step.generation()) {
        playback.clock.fireStart(
            () ->
                layer.current == playback
                    && playback.clock.revision() == step.generation()
                    && isOwnerRunnable()
                    && isRendererLive());
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
    if (plannedBase != null
        && current == plannedBase
        && baseTransition == plannedBaseFade
        && plannedBaseFade != null
        && plannedBaseFade.revision == plannedBaseFadeRevision
        && baseStep != null
        && plannedBase.clock.revision() == baseStep.generation()
        && plannedBase.clock.isActive()
        && !plannedBase.clock.isPaused()
        && frameIndex() >= plannedBaseFade.eligibleFrame) {
      plannedBaseFade.advance(delta);
      if (plannedBaseFade.progress() >= 1) baseTransition = null;
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
      TransitionState fade = layerTransitions[i];
      if (playback != null
          && layer.current == playback
          && layer.transition == fade
          && fade != null
          && fade.revision == layerTransitionRevisions[i]
          && step != null
          && playback.clock.revision() == step.generation()
          && playback.clock.isActive()
          && !playback.clock.isPaused()
          && frameIndex() >= fade.eligibleFrame) {
        fade.advance(delta);
        if (fade.progress() >= 1) layer.transition = null;
      }
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
    bindLocalTrs = new float[nodes.size() * 10];
    for (PoseNode node : nodes) {
      Integer parent = nodeIndices.get(node.node.getParent());
      sharedLayout.add(new SharedPoseData.LayoutNode(node.path, parent == null ? -1 : parent));
      storeTrs(bindLocalTrs, node.index * 10, node.bindT, node.bindR, node.bindS);
    }
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
    switch (mask.kind()) {
      case EXACT, SUBTREE -> {
        for (String value : mask.values()) {
          Node anchor;
          if (mask.usesPaths()) {
            Integer index = pathIndices.get(value);
            if (index == null || index < 0)
              throw new IllegalArgumentException(
                  "Unknown or ambiguous node path '"
                      + value
                      + "'. Available paths: "
                      + availablePaths());
            anchor = nodes.get(index).node;
          } else {
            List<Integer> matches = idIndices.get(value);
            if (matches == null || matches.isEmpty())
              throw new IllegalArgumentException(
                  "Unknown node id '" + value + "'. Available ids: " + idIndices.keySet());
            if (matches.size() != 1)
              throw new IllegalArgumentException(
                  "Ambiguous node id '" + value + "'. Available paths: " + availablePaths());
            anchor = nodes.get(matches.get(0)).node;
          }
          if (mask.kind() == NodeMask.Kind.EXACT) result[nodeIndices.get(anchor)] = true;
          else markSubtree(anchor, result);
        }
      }
      case UNION -> {
        for (NodeMask operand : mask.operands()) {
          boolean[] selected = resolveMask(operand);
          for (int i = 0; i < result.length; i++) result[i] |= selected[i];
        }
      }
      case DIFFERENCE -> {
        NodeMask[] operands = mask.operands();
        boolean[] included = resolveMask(operands[0]);
        boolean[] excluded = resolveMask(operands[1]);
        for (int i = 0; i < result.length; i++) result[i] = included[i] && !excluded[i];
      }
    }
    return result;
  }

  private String availablePaths() {
    List<String> result = new ArrayList<>(nodes.size());
    for (PoseNode node : nodes) result.add(node.path);
    return result.toString();
  }

  private void markSubtree(Node root, boolean[] result) {
    Integer index = nodeIndices.get(root);
    if (index == null)
      throw new IllegalArgumentException("Mask node is outside this renderer instance");
    result[index] = true;
    for (Node child : root.getChildren()) markSubtree(child, result);
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
    Animation retained = new Animation();
    retained.id = name;
    retained.duration = animation.duration;
    for (PoseNode node : nodes) {
      NodeTrack track = tracks[node.index];
      if (track == null) continue;
      NodeAnimation bound = new NodeAnimation();
      bound.node = node.node;
      bound.translation = track.translation;
      bound.rotation = track.rotation;
      bound.scaling = track.scaling;
      retained.nodeAnimations.add(bound);
    }
    ClipData result = new ClipData(name, animation.duration, tracks, retained);
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
    clock.allowPauseAfterCompletion();
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

  private PoseValue[] captureBaseContribution() {
    PoseValue[] result = values(nodes.size());
    for (PoseNode node : nodes) {
      sampleBase(current, node, node.sampleT, node.sampleR, node.sampleS);
      if (baseTransition != null) {
        PoseValue from = baseTransition.outgoing[node.index];
        double alpha = baseTransition.progress();
        blendVector(from.t, node.sampleT, alpha, node.sampleT);
        blendVector(from.s, node.sampleS, alpha, node.sampleS);
        blendRotation(from.r, node.sampleR, alpha, node.sampleR, node.tempR);
      }
      PoseValue value = result[node.index];
      value.t.set(node.sampleT);
      value.r.set(node.sampleR);
      value.s.set(node.sampleS);
      value.tc = value.rc = value.sc = 1;
    }
    return result;
  }

  private PoseValue[] captureLayerContribution(ModelAnimationLayer layer) {
    PoseValue[] result = values(nodes.size());
    for (PoseNode node : nodes) readLayer(layer, node, result[node.index]);
    return result;
  }

  private static PoseValue[] values(int count) {
    PoseValue[] result = new PoseValue[count];
    for (int i = 0; i < count; i++) result[i] = new PoseValue();
    return result;
  }

  private void sampleBase(
      ModelPosePlayback playback, PoseNode node, Vector3 outT, Quaternion outR, Vector3 outS) {
    if (playback == null) {
      outT.set(node.bindT);
      outR.set(node.bindR);
      outS.set(node.bindS);
      return;
    }
    if (playback.mixer == null) {
      ClipData clip = clip(playback.animationName());
      sample(clip, playback.clock.normalizedTime() * clip.duration, node, true);
      outT.set(node.sampleT);
      outR.set(node.sampleR);
      outS.set(node.sampleS);
      return;
    }
    LinearModelMixer mixer = playback.mixer;
    int lower = lowerIndex(mixer, playback.parameter);
    int upper = upperIndex(mixer, playback.parameter, lower);
    double mix = pairMix(mixer, playback.parameter, lower, upper);
    ClipData a = clip(mixer.entries().get(lower).animationName());
    ClipData b = clip(mixer.entries().get(upper).animationName());
    double phase = playback.clock.normalizedTime();
    sample(a, phase * a.duration, node, true);
    node.tempT.set(node.sampleT);
    node.tempR.set(node.sampleR);
    node.tempS.set(node.sampleS);
    sample(b, phase * b.duration, node, true);
    blendVector(node.tempT, node.sampleT, mix, outT);
    blendVector(node.tempS, node.sampleS, mix, outS);
    blendRotation(node.tempR, node.sampleR, mix, outR, node.keyR);
  }

  private void readLayer(ModelAnimationLayer layer, PoseNode node, PoseValue out) {
    out.tc = out.rc = out.sc = 0;
    ModelPosePlayback playback = layer.current;
    if (playback == null || !playback.clock.isActive() || !layer.resolvedMask[node.index]) return;
    ClipData clip = clip(playback.animationName());
    NodeTrack track = clip.tracks[node.index];
    if (track != null) {
      double time = playback.clock.normalizedTime() * clip.duration;
      if (track.translation != null && track.translation.size > 0) {
        sampleVector(track.translation, time, out.t);
        out.tc = 1;
      }
      if (track.rotation != null && track.rotation.size > 0) {
        sampleQuaternion(track.rotation, time, out.r, node.keyR);
        out.rc = 1;
      }
      if (track.scaling != null && track.scaling.size > 0) {
        sampleVector(track.scaling, time, out.s);
        out.sc = 1;
      }
    }
    TransitionState fade = layer.transition;
    if (fade != null)
      blendContribution(fade.outgoing[node.index], out, fade.progress(), out, node.tempR);
  }

  private static void blendContribution(
      PoseValue from, PoseValue to, double alpha, PoseValue out, Quaternion scratch) {
    double oneMinus = 1 - alpha;
    double tc = oneMinus * from.tc + alpha * to.tc;
    double rc = oneMinus * from.rc + alpha * to.rc;
    double sc = oneMinus * from.sc + alpha * to.sc;
    if (tc > 0) blendVector(from.t, to.t, alpha * to.tc / tc, out.t);
    if (rc > 0) blendRotation(from.r, to.r, alpha * to.rc / rc, out.r, scratch);
    if (sc > 0) blendVector(from.s, to.s, alpha * to.sc / sc, out.s);
    out.tc = tc;
    out.rc = rc;
    out.sc = sc;
  }

  private static void blendRotation(
      Quaternion from, Quaternion to, double alpha, Quaternion out, Quaternion scratch) {
    if (alpha <= 0) out.set(from);
    else if (alpha >= 1) out.set(to);
    else {
      scratch.set(to).nor();
      out.set(from).nor().slerp(scratch, (float) alpha).nor();
    }
  }

  void composeNow() {
    if (renderer == null || nodes.isEmpty() || !isAttachedUsable() || !isRendererPresent()) return;
    if (!isOwnerRunnable()) return;
    ensureSetup();
    PoseBatchSystem batch = getGame().getSystem(PoseBatchSystem.class);
    if (batch == null || batch.isDisposed()) {
      composeLocal();
    } else {
      List<Animation> selected = new ArrayList<>();
      selectClips(current, selected);
      for (ModelAnimationLayer layer : layers) selectClips(layer.current, selected);
      sharedData =
          batch.source(
              sharedData,
              data ->
                  data.matchesResolved(
                      renderer.instance.model, sharedLayout, bindLocalTrs, selected, nodeIndices),
              () ->
                  SharedPoseData.captureResolved(
                      renderer.instance.model, sharedLayout, bindLocalTrs, selected, nodeIndices));
      ExactPoseKey.Builder key = ExactPoseKey.builder(sharedData);
      keyPlayback(key, current);
      keyFade(key, baseTransition);
      key.tag(layers.size());
      for (ModelAnimationLayer layer : layers) {
        key.number(layer.weight).tag(layer.resolvedMask == null ? 0 : 1);
        if (layer.resolvedMask != null) key.mask(layer.resolvedMask);
        keyPlayback(key, layer.current);
        keyFade(key, layer.transition);
      }
      ExactPoseCache.Pose pose =
          batch.resolve(
              key.build(),
              () -> {
                composeLocal();
                float[] result = new float[nodes.size() * 10];
                for (PoseNode node : nodes)
                  storeTrs(result, node.index * 10, node.outT, node.outR, node.outS);
                return result;
              });
      for (PoseNode node : nodes) {
        int i = node.index * 10;
        node.outT.set(pose.component(i), pose.component(i + 1), pose.component(i + 2));
        node.outR.set(
            pose.component(i + 3),
            pose.component(i + 4),
            pose.component(i + 5),
            pose.component(i + 6));
        node.outS.set(pose.component(i + 7), pose.component(i + 8), pose.component(i + 9));
      }
    }
    for (PoseNode node : nodes) {
      node.node.translation.set(node.outT);
      node.node.rotation.set(node.outR);
      node.node.scale.set(node.outS);
      node.node.localTransform.set(node.outT, node.outR, node.outS);
      node.node.isAnimated = true;
    }
    renderer.instance.calculateTransforms();
  }

  private void selectClips(ModelPosePlayback playback, List<Animation> selected) {
    if (playback == null) return;
    if (playback.mixer == null) selected.add(clip(playback.animationName()).retained);
    else
      for (LinearModelMixer.Entry entry : playback.mixer.entries())
        selected.add(clip(entry.animationName()).retained);
  }

  private void keyPlayback(ExactPoseKey.Builder key, ModelPosePlayback playback) {
    if (playback == null) {
      key.tag(0);
      return;
    }
    key.tag(playback.clock.isActive() ? 1 : 2);
    if (playback.mixer == null) {
      ClipData data = clip(playback.animationName());
      key.tag(0).number(playback.clock.normalizedTime() * data.duration);
    } else {
      LinearModelMixer mixer = playback.mixer;
      key.tag(1)
          .tag(mixer.entries().size())
          .number(playback.parameter)
          .number(playback.clock.normalizedTime());
      for (LinearModelMixer.Entry entry : mixer.entries()) {
        ClipData data = clip(entry.animationName());
        key.number(entry.threshold()).number(playback.clock.normalizedTime() * data.duration);
      }
      int lower = lowerIndex(mixer, playback.parameter);
      int upper = upperIndex(mixer, playback.parameter, lower);
      key.tag(lower).tag(upper).number(pairMix(mixer, playback.parameter, lower, upper));
    }
  }

  private static void keyFade(ExactPoseKey.Builder key, TransitionState fade) {
    key.tag(fade == null ? 0 : 1);
    if (fade == null) return;
    key.number(fade.progress());
    float[] frozen = new float[fade.outgoing.length * 10];
    for (int i = 0; i < fade.outgoing.length; i++) {
      PoseValue value = fade.outgoing[i];
      storeTrs(frozen, i * 10, value.t, value.r, value.s);
      key.number(value.tc).number(value.rc).number(value.sc);
    }
    key.floats(frozen);
  }

  private static void storeTrs(float[] target, int i, Vector3 t, Quaternion r, Vector3 s) {
    target[i] = t.x;
    target[i + 1] = t.y;
    target[i + 2] = t.z;
    target[i + 3] = r.x;
    target[i + 4] = r.y;
    target[i + 5] = r.z;
    target[i + 6] = r.w;
    target[i + 7] = s.x;
    target[i + 8] = s.y;
    target[i + 9] = s.z;
  }

  private void composeLocal() {
    int layerCount = layers.size();
    int nodeCount = nodes.size();
    for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
      PoseNode node = nodes.get(nodeIndex);
      sampleBase(current, node, node.outT, node.outR, node.outS);
      if (baseTransition != null) {
        PoseValue from = baseTransition.outgoing[node.index];
        double alpha = baseTransition.progress();
        blendVector(from.t, node.outT, alpha, node.outT);
        blendVector(from.s, node.outS, alpha, node.outS);
        blendRotation(from.r, node.outR, alpha, node.outR, node.tempR);
      }
      for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
        ModelAnimationLayer layer = layers.get(layerIndex);
        if (layer.weight <= 0 || layer.resolvedMask == null) continue;
        PoseValue contribution = node.layerValue;
        readLayer(layer, node, contribution);
        double tWeight = layer.weight * contribution.tc;
        double rWeight = layer.weight * contribution.rc;
        double sWeight = layer.weight * contribution.sc;
        if (tWeight > 0) blendVector(node.outT, contribution.t, tWeight, node.outT);
        if (rWeight > 0) blendRotation(node.outR, contribution.r, rWeight, node.outR, node.tempR);
        if (sWeight > 0) blendVector(node.outS, contribution.s, sWeight, node.outS);
      }
    }
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
      layer.transition = null;
    }
    baseTransition = null;
  }

  private void invalidateForRendererChange() {
    resetBindPose();
    invalidatePlaybacks();
    renderer = null;
    clearIndex();
  }

  private void clearIndex() {
    sharedData = null;
    bindLocalTrs = null;
    sharedLayout.clear();
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
    baseTransition = null;
    composeNow();
    return playback;
  }

  @Override
  public boolean isFading(ModelPosePlayback playback) {
    return current == playback && playback.clock.isActive() && baseTransition != null;
  }

  @Override
  public double fadeProgress(ModelPosePlayback playback) {
    return current == playback && baseTransition != null ? baseTransition.progress() : 1;
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
    final PoseValue layerValue = new PoseValue();

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

  private record ClipData(String name, float duration, NodeTrack[] tracks, Animation retained) {}

  private static final class PoseValue {
    final Vector3 t = new Vector3(), s = new Vector3();
    final Quaternion r = new Quaternion();
    double tc, rc, sc;
  }

  static final class TransitionState {
    final PoseValue[] outgoing;
    final double duration;
    final long eligibleFrame;
    double elapsed;
    long revision;

    TransitionState(PoseValue[] outgoing, double duration, long eligibleFrame) {
      this.outgoing = outgoing;
      this.duration = duration;
      this.eligibleFrame = eligibleFrame;
    }

    double progress() {
      return Math.min(1, elapsed / duration);
    }

    void advance(float delta) {
      double next = elapsed + delta;
      if (!Double.isFinite(next)) throw new IllegalArgumentException("Pose fade step overflow");
      elapsed = Math.min(duration, next);
      revision++;
    }
  }
}
