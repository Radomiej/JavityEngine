package org.javity.avatar;

import com.badlogic.gdx.math.Matrix4;
import java.util.*;
import org.javity.render.ModelRenderer;

/** Immutable semantic selectors and socket offsets shared by avatar bindings. */
public final class AvatarDefinition {
  private final Map<PartKey, AvatarPart> parts;
  private final Map<PartKey, String> slots;
  private final Map<String, Socket> sockets;
  private final Map<String, AvatarPart> regions;

  private AvatarDefinition(Builder builder) {
    parts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parts));
    slots = Collections.unmodifiableMap(new LinkedHashMap<>(builder.slots));
    Map<String, Socket> copiedSockets = new LinkedHashMap<>();
    builder.sockets.forEach(
        (name, socket) ->
            copiedSockets.put(name, new Socket(socket.nodePath, new Matrix4(socket.localOffset))));
    sockets = Collections.unmodifiableMap(copiedSockets);
    regions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.regions));
  }

  public static Builder builder() {
    return new Builder();
  }

  public AvatarBinding bind(ModelRenderer renderer) {
    return new AvatarBinding(this, Objects.requireNonNull(renderer));
  }

  /** Binds authored mesh regions to semantic visibility regions on one selected NodePart. */
  public AvatarBinding bind(ModelRenderer renderer, PreparedAvatarGeometry geometry) {
    return new AvatarBinding(
        this, Objects.requireNonNull(renderer), Objects.requireNonNull(geometry));
  }

  Map<PartKey, AvatarPart> parts() {
    return parts;
  }

  Map<PartKey, String> slots() {
    return slots;
  }

  Map<String, Socket> sockets() {
    return sockets;
  }

  Map<String, AvatarPart> regions() {
    return regions;
  }

  static final class Socket {
    final String nodePath;
    final Matrix4 localOffset;

    Socket(String nodePath, Matrix4 localOffset) {
      this.nodePath = nodePath;
      this.localOffset = localOffset;
    }
  }

  public static final class Builder {
    private final Map<PartKey, AvatarPart> parts = new LinkedHashMap<>();
    private final Map<PartKey, String> slots = new LinkedHashMap<>();
    private final Map<String, Socket> sockets = new LinkedHashMap<>();
    private final Map<String, AvatarPart> regions = new LinkedHashMap<>();

    private Builder() {}

    public Builder part(AvatarPart semantic, PartKey... selectors) {
      Objects.requireNonNull(semantic, "semantic");
      for (PartKey key : checked(selectors)) {
        AvatarPart prior = parts.putIfAbsent(key, semantic);
        if (prior != null && prior != semantic)
          throw new IllegalArgumentException(
              "Conflicting semantic ownership for " + key + ": " + prior + " and " + semantic);
      }
      return this;
    }

    /** Assigns accessory/clothing geometry to a named visibility slot. */
    public Builder slot(String name, PartKey... selectors) {
      if (name == null || name.isBlank())
        throw new IllegalArgumentException("Slot name is required");
      String normalized = name.trim();
      for (PartKey key : checked(selectors)) {
        String prior = slots.putIfAbsent(key, normalized);
        if (prior != null && !prior.equals(normalized))
          throw new IllegalArgumentException(
              "Conflicting slot ownership for " + key + ": " + prior + " and " + normalized);
      }
      return this;
    }

    public Builder socket(String name, String nodePath, Matrix4 localOffset) {
      if (name == null || name.isBlank())
        throw new IllegalArgumentException("Socket name is required");
      PartKey pathCheck = new PartKey(nodePath, 0);
      Objects.requireNonNull(localOffset, "localOffset");
      for (float value : localOffset.val)
        if (!Float.isFinite(value))
          throw new IllegalArgumentException("Socket matrix must be finite: " + name);
      if (sockets.putIfAbsent(
              name.trim(), new Socket(pathCheck.nodePath(), new Matrix4(localOffset)))
          != null) throw new IllegalArgumentException("Duplicate socket: " + name);
      return this;
    }

    /** Maps a prepared authored geometry region to the semantic visibility selector. */
    public Builder region(AvatarPart semantic, String region) {
      Objects.requireNonNull(semantic, "semantic");
      if (region == null || region.isBlank())
        throw new IllegalArgumentException("Region name is required");
      String normalized = region.trim();
      AvatarPart previous = regions.putIfAbsent(normalized, semantic);
      if (previous != null && previous != semantic)
        throw new IllegalArgumentException(
            "Conflicting semantic ownership for region " + normalized);
      return this;
    }

    public AvatarDefinition build() {
      return new AvatarDefinition(this);
    }

    private static List<PartKey> checked(PartKey[] selectors) {
      Objects.requireNonNull(selectors, "selectors");
      if (selectors.length == 0)
        throw new IllegalArgumentException("At least one part selector is required");
      List<PartKey> copy = new ArrayList<>(selectors.length);
      Set<PartKey> unique = new HashSet<>();
      for (PartKey selector : selectors) {
        Objects.requireNonNull(selector, "part selector");
        if (!unique.add(selector))
          throw new IllegalArgumentException("Duplicate part selector: " + selector);
        copy.add(selector);
      }
      return copy;
    }
  }
}
