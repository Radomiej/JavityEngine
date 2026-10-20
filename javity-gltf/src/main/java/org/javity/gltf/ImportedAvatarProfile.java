package org.javity.gltf;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import java.util.*;
import org.javity.avatar.*;

/**
 * Explicit authoring over the canonical imported instance. Inspection is CPU-only and copies
 * metadata; it does not classify a rig, prune nodes, own resources or sample animations. A profile
 * can be reused for matching instances; AvatarDefinition.bind still validates each instance.
 */
public final class ImportedAvatarProfile {
  public record PartInfo(PartKey key, String materialId, List<String> bonePaths) {
    public PartInfo {
      bonePaths = List.copyOf(bonePaths);
    }
  }

  public record ClipInfo(String id, float duration, List<String> nodePaths) {
    public ClipInfo {
      nodePaths = List.copyOf(nodePaths);
    }
  }

  /**
   * Immutable snapshot of exact engine paths, NodePart ordinals, palettes and imported clip IDs.
   */
  public record Catalog(List<String> nodePaths, List<PartInfo> parts, List<ClipInfo> clips) {
    public Catalog {
      nodePaths = List.copyOf(nodePaths);
      parts = List.copyOf(parts);
      clips = List.copyOf(clips);
    }

    public Builder profile() {
      return new Builder(this);
    }
  }

  private final AvatarDefinition definition;
  private final Map<String, String> clips;

  private ImportedAvatarProfile(AvatarDefinition definition, Map<String, String> clips) {
    this.definition = definition;
    this.clips = Collections.unmodifiableMap(new LinkedHashMap<>(clips));
  }

  public AvatarDefinition definition() {
    return definition;
  }

  /** Returns the original clip ID for use with ModelPoseAnimator's existing play API. */
  public String clip(String role) {
    String id = clips.get(role);
    if (id == null) throw new IllegalArgumentException("Unknown avatar clip role: " + role);
    return id;
  }

  public Map<String, String> clips() {
    return clips;
  }

  public static Catalog inspect(ModelInstance instance) {
    Objects.requireNonNull(instance, "instance");
    LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
    IdentityHashMap<Node, String> paths = new IdentityHashMap<>();
    Set<String> ids = new HashSet<>();
    for (Node root : instance.nodes) collect(root, root.id, nodes, paths, ids);
    List<PartInfo> parts = new ArrayList<>();
    nodes.forEach(
        (path, node) -> {
          for (int i = 0; i < node.parts.size; i++) {
            var part = node.parts.get(i);
            List<String> palette = new ArrayList<>();
            if (part.invBoneBindTransforms != null)
              for (Node bone : part.invBoneBindTransforms.keys())
                palette.add(requirePath(paths, bone, "bone palette"));
            parts.add(
                new PartInfo(
                    new PartKey(path, i),
                    part.material == null ? null : part.material.id,
                    palette));
          }
        });
    List<ClipInfo> clips = new ArrayList<>();
    Set<String> clipIds = new HashSet<>();
    for (var clip : instance.animations) {
      if (clip.id == null || clip.id.isBlank() || !clipIds.add(clip.id))
        throw new IllegalArgumentException("Missing or duplicate imported clip ID: " + clip.id);
      Set<String> targets = new LinkedHashSet<>();
      for (var channel : clip.nodeAnimations)
        targets.add(requirePath(paths, channel.node, "animation " + clip.id));
      clips.add(new ClipInfo(clip.id, clip.duration, List.copyOf(targets)));
    }
    return new Catalog(List.copyOf(nodes.keySet()), parts, clips);
  }

  private static void collect(
      Node node,
      String path,
      Map<String, Node> nodes,
      IdentityHashMap<Node, String> paths,
      Set<String> ids) {
    if (node.id == null
        || node.id.isBlank()
        || !node.id.equals(node.id.trim())
        || node.id.contains("/"))
      throw new IllegalArgumentException("Avatar node ID cannot form an exact path: " + node.id);
    if (nodes.putIfAbsent(path, node) != null || paths.put(node, path) != null)
      throw new IllegalArgumentException("Ambiguous avatar node path: " + path);
    // ModelInstance remaps palettes/channels by ID; duplicate IDs cannot safely be authored.
    if (!ids.add(node.id))
      throw new IllegalArgumentException("Duplicate canonical avatar node ID: " + node.id);
    for (Node child : node.getChildren()) collect(child, path + "/" + child.id, nodes, paths, ids);
  }

  private static String requirePath(IdentityHashMap<Node, String> paths, Node node, String source) {
    String path = paths.get(node);
    if (path == null)
      throw new IllegalArgumentException("Detached canonical node referenced by " + source);
    return path;
  }

  public static final class Builder {
    private final Catalog catalog;
    private final Set<PartKey> parts = new LinkedHashSet<>();
    private final AvatarDefinition.Builder definition = AvatarDefinition.builder();
    private final Map<String, String> clips = new LinkedHashMap<>();

    private Builder(Catalog catalog) {
      this.catalog = catalog;
      catalog.parts.forEach(part -> parts.add(part.key));
    }

    public Builder part(AvatarPart semantic, PartKey... selectors) {
      checkParts(selectors);
      definition.part(semantic, selectors);
      return this;
    }

    public Builder slot(String name, PartKey... selectors) {
      checkParts(selectors);
      definition.slot(name, selectors);
      return this;
    }

    public Builder socket(String name, String nodePath, Matrix4 localOffset) {
      if (!catalog.nodePaths.contains(nodePath))
        throw new IllegalArgumentException(
            "Unknown avatar socket path: " + nodePath + "; available: " + catalog.nodePaths);
      definition.socket(name, nodePath, localOffset);
      return this;
    }

    public Builder clip(String role, String importedId) {
      if (role == null || role.isBlank()) throw new IllegalArgumentException("Clip role required");
      if (catalog.clips.stream().noneMatch(clip -> clip.id.equals(importedId)))
        throw new IllegalArgumentException(
            "Unknown imported clip: " + importedId + "; available: " + catalog.clips);
      if (clips.putIfAbsent(role, importedId) != null)
        throw new IllegalArgumentException("Duplicate avatar clip role: " + role);
      return this;
    }

    public ImportedAvatarProfile build() {
      return new ImportedAvatarProfile(definition.build(), clips);
    }

    private void checkParts(PartKey[] selectors) {
      Objects.requireNonNull(selectors, "selectors");
      for (PartKey selector : selectors)
        if (!parts.contains(selector))
          throw new IllegalArgumentException(
              "Unknown imported avatar part: " + selector + "; available: " + parts);
    }
  }
}
