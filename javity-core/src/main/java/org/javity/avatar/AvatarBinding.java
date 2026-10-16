package org.javity.avatar;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ArrayMap;
import java.util.*;
import org.javity.render.ModelRenderer;

/** Per-instance resolution of an immutable avatar definition. Borrows its renderer and model. */
public final class AvatarBinding implements AutoCloseable {
  static final class Entry {
    final PartKey key;
    final Node node;
    final NodePart nodePart;
    final AvatarPart semantic;
    final String slot;
    final boolean preparedTarget;

    Entry(
        PartKey key,
        Node node,
        NodePart nodePart,
        AvatarPart semantic,
        String slot,
        boolean preparedTarget) {
      this.key = key;
      this.node = node;
      this.nodePart = nodePart;
      this.semantic = semantic;
      this.slot = slot;
      this.preparedTarget = preparedTarget;
    }
  }

  private final ModelRenderer renderer;
  private final ModelInstance instance;
  private final List<Entry> entries;
  private final Map<String, Node> sockets;
  private final Set<String> slotNames;
  private final PreparedAvatarGeometry.GeometryLease geometryLease;
  private final Map<String, AvatarPart> regionSemantics;
  private final Map<String, com.badlogic.gdx.graphics.g3d.model.MeshPart> preparedParts;
  private boolean closed;

  AvatarBinding(AvatarDefinition definition, ModelRenderer renderer) {
    this(definition, renderer, null);
  }

  AvatarBinding(
      AvatarDefinition definition, ModelRenderer renderer, PreparedAvatarGeometry geometry) {
    if (renderer.getGameObject() == null || !renderer.getGameObject().containsComponent(renderer))
      throw new IllegalArgumentException("Avatar binding requires an attached ModelRenderer");
    this.renderer = renderer;
    this.instance = renderer.instance;
    Set<String> geometryRegions = geometry == null ? Set.of() : geometry.regionNames();
    if (geometry == null && !definition.regions().isEmpty())
      throw new IllegalArgumentException(
          "AvatarDefinition region mappings require PreparedAvatarGeometry");
    if (geometry != null && !definition.regions().keySet().equals(geometryRegions))
      throw new IllegalArgumentException(
          "Avatar region mappings must match prepared regions exactly; expected "
              + geometryRegions
              + ", got "
              + definition.regions().keySet());

    Map<String, Node> nodesByPath = new LinkedHashMap<>();
    Set<String> ambiguousPaths = new HashSet<>();
    Map<String, Integer> idCounts = new HashMap<>();
    List<Entry> resolved = new ArrayList<>();
    for (Node root : instance.nodes)
      collect(
          root,
          root.id,
          nodesByPath,
          ambiguousPaths,
          idCounts,
          definition,
          resolved,
          geometry == null ? null : geometry.target());
    validateRemappedReferences(idCounts);

    Set<PartKey> found = new HashSet<>();
    for (Entry entry : resolved) found.add(entry.key);
    Set<PartKey> requested = new LinkedHashSet<>(definition.parts().keySet());
    requested.addAll(definition.slots().keySet());
    for (PartKey key : requested) {
      if (ambiguousPaths.contains(key.nodePath()))
        throw new IllegalArgumentException(
            "Ambiguous avatar node path '"
                + key.nodePath()
                + "'. Available paths: "
                + nodesByPath.keySet());
      if (!found.contains(key))
        throw new IllegalArgumentException(
            "Unknown avatar part " + key + ". Available parts: " + describeParts(resolved));
    }
    Map<String, Node> resolvedSockets = new LinkedHashMap<>();
    for (var socket : definition.sockets().entrySet()) {
      Node node =
          ambiguousPaths.contains(socket.getValue().nodePath)
              ? null
              : nodesByPath.get(socket.getValue().nodePath);
      if (node == null)
        throw new IllegalArgumentException(
            "Unknown or ambiguous socket path '"
                + socket.getValue().nodePath
                + "'. Available paths: "
                + nodesByPath.keySet());
      resolvedSockets.put(socket.getKey(), node);
    }
    this.entries = List.copyOf(resolved);
    this.sockets = Collections.unmodifiableMap(resolvedSockets);
    Set<String> namedSlots = new LinkedHashSet<>(definition.slots().values());
    this.slotNames = Collections.unmodifiableSet(namedSlots);
    this.regionSemantics = definition.regions();
    this.geometryLease = geometry == null ? null : geometry.acquire(instance);
    if (geometryLease == null) this.preparedParts = Map.of();
    else {
      Map<String, com.badlogic.gdx.graphics.g3d.model.MeshPart> parts = new LinkedHashMap<>();
      for (String region : geometryLease.regions()) parts.put(region, geometryLease.part(region));
      this.preparedParts = Collections.unmodifiableMap(parts);
    }
    definition
        .sockets()
        .forEach(
            (name, socket) ->
                offsets.put(
                    name,
                    new AvatarDefinition.Socket(socket.nodePath, new Matrix4(socket.localOffset))));
    renderer.registerAvatarBinding(this);
  }

  private static void collect(
      Node node,
      String path,
      Map<String, Node> nodesByPath,
      Set<String> ambiguousPaths,
      Map<String, Integer> idCounts,
      AvatarDefinition definition,
      List<Entry> entries,
      PartKey preparedTarget) {
    if (node.id == null || node.id.isBlank())
      throw new IllegalArgumentException("Avatar model node ids must be non-empty");
    idCounts.merge(node.id, 1, Integer::sum);
    if (nodesByPath.containsKey(path)) ambiguousPaths.add(path);
    else nodesByPath.put(path, node);
    for (int i = 0; i < node.parts.size; i++) {
      PartKey key = new PartKey(path, i);
      entries.add(
          new Entry(
              key,
              node,
              node.parts.get(i),
              definition.parts().get(key),
              definition.slots().get(key),
              preparedTarget != null && preparedTarget.equals(key)));
    }
    for (Node child : node.getChildren())
      collect(
          child,
          path + "/" + child.id,
          nodesByPath,
          ambiguousPaths,
          idCounts,
          definition,
          entries,
          preparedTarget);
  }

  private void validateRemappedReferences(Map<String, Integer> idCounts) {
    for (Animation animation : instance.animations) {
      for (NodeAnimation track : animation.nodeAnimations) {
        if (idCounts.getOrDefault(track.node.id, 0) > 1)
          throw new IllegalArgumentException(
              "Avatar binding cannot use duplicate node id '"
                  + track.node.id
                  + "' referenced by animation '"
                  + animation.id
                  + "'; ModelInstance remaps animation tracks by id");
      }
    }
    for (Node root : instance.nodes) validateBoneIds(root, idCounts);
  }

  private static void validateBoneIds(Node node, Map<String, Integer> idCounts) {
    for (NodePart part : node.parts) {
      ArrayMap<Node, Matrix4> binds = part.invBoneBindTransforms;
      if (binds != null)
        for (int i = 0; i < binds.size; i++) {
          Node bone = binds.getKeyAt(i);
          if (idCounts.getOrDefault(bone.id, 0) > 1)
            throw new IllegalArgumentException(
                "Avatar binding cannot use duplicate node id '"
                    + bone.id
                    + "' referenced by a bone bind map; ModelInstance remaps binds by id");
        }
    }
    for (Node child : node.getChildren()) validateBoneIds(child, idCounts);
  }

  private static String describeParts(List<Entry> entries) {
    List<PartKey> keys = new ArrayList<>(entries.size());
    for (Entry entry : entries) keys.add(entry.key);
    return keys.toString();
  }

  public static void validateCapacity(
      NodePart part, String selector, int maxBones, int maxBoneWeights) {
    int boneCount = part.bones == null ? 0 : part.bones.length;
    int weightCount = 0;
    if (part.meshPart != null && part.meshPart.mesh != null)
      weightCount = part.meshPart.mesh.getVertexAttributes().getBoneWeights();
    if (boneCount > maxBones || weightCount > maxBoneWeights)
      throw new IllegalArgumentException(
          "Renderable "
              + selector
              + " requires "
              + boneCount
              + " bones and "
              + weightCount
              + " bone-weight attributes; GdxRenderer is configured for "
              + maxBones
              + " bones and "
              + maxBoneWeights
              + " weights. Supply a larger shader capacity.");
  }

  public AvatarView view() {
    ensureOpen();
    return new AvatarView(this);
  }

  /**
   * Computes base × nodeGlobal × localOffset. For a GdxRenderer world view, pass `new
   * Matrix4(objectTransform.worldMatrix()).mul(view.baseTransform())`; for a direct provider, pass
   * `view.baseTransform()`.
   */
  public Matrix4 socketTransform(String name, Matrix4 base, Matrix4 out) {
    ensureOpen();
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(out, "out");
    Node node = sockets.get(name);
    if (node == null)
      throw new IllegalArgumentException(
          "Unknown avatar socket: " + name + ". Available sockets: " + sockets.keySet());
    AvatarDefinition.Socket socket = offsets.get(name);
    return out.set(base).mul(node.globalTransform).mul(socket.localOffset);
  }

  private final Map<String, AvatarDefinition.Socket> offsets = new HashMap<>();

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("Avatar binding is closed");
    if (!rendererPresent())
      throw new IllegalStateException("Avatar binding's ModelRenderer has been removed");
  }

  boolean rendererPresent() {
    return !closed
        && renderer.getGameObject() != null
        && renderer.getGameObject().containsComponent(renderer);
  }

  boolean rendererLive() {
    return rendererPresent()
        && renderer.isInitialized()
        && renderer.isEnabled()
        && renderer.getGameObject().isActive()
        && !renderer.getGameObject().isDestroyed();
  }

  void ensureForView() {
    ensureOpen();
  }

  boolean hasSlot(String name) {
    return name != null && slotNames.contains(name);
  }

  Map<String, AvatarPart> regionSemantics() {
    return regionSemantics;
  }

  Map<String, com.badlogic.gdx.graphics.g3d.model.MeshPart> preparedParts() {
    return preparedParts;
  }

  ModelRenderer renderer() {
    return renderer;
  }

  ModelInstance instance() {
    return instance;
  }

  List<Entry> entries() {
    return entries;
  }

  @Override
  public void close() {
    if (closed) return;
    if (geometryLease != null) geometryLease.requireOwnerThread();
    closed = true;
    if (geometryLease != null) geometryLease.close();
    renderer.unregisterAvatarBinding(this);
  }

  /** Internal lifecycle callback from the owning ModelRenderer. */
  public void rendererReleased() {
    if (closed) return;
    if (geometryLease != null) geometryLease.requireOwnerThread();
    closed = true;
    if (geometryLease != null) geometryLease.close();
  }
}
