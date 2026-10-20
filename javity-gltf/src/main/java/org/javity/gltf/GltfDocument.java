package org.javity.gltf;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** CPU preflight for the deliberately bounded Javity import profile; not a glTF validator. */
public final class GltfDocument {
  public static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
  private static final Set<String> EXTENSIONS =
      Set.of("KHR_materials_unlit", "KHR_texture_transform", "KHR_materials_emissive_strength");

  private GltfDocument() {}

  public static void validate(FileHandle file, int maxBones) {
    if (maxBones < 1) throw new IllegalArgumentException("maxBones must be positive");
    String extension = file.extension().toLowerCase(java.util.Locale.ROOT);
    if (!extension.equals("gltf") && !extension.equals("glb"))
      throw new IllegalArgumentException("Expected .gltf or .glb: " + file.path());
    if (file.length() > MAX_FILE_BYTES)
      throw new IllegalArgumentException("glTF file exceeds 64 MiB");
    byte[] bytes = file.readBytes();
    if (bytes.length > MAX_FILE_BYTES)
      throw new IllegalArgumentException("glTF file exceeds 64 MiB");
    String json =
        extension.equals("glb") ? jsonChunk(bytes) : new String(bytes, StandardCharsets.UTF_8);
    JsonValue root = new JsonReader().parse(json);
    JsonValue asset = root.require("asset");
    if (!"2.0".equals(asset.getString("version", ""))
        || !"2.0".equals(asset.getString("minVersion", "2.0")))
      throw new IllegalArgumentException("Only glTF 2.0 is supported");
    checkExtensions(root);
    for (String key : new String[] {"extensionsUsed", "extensionsRequired"})
      for (JsonValue value = first(root, key); value != null; value = value.next)
        if (!EXTENSIONS.contains(value.asString())) unsupported("extension " + value.asString());
    for (JsonValue material = first(root, "materials"); material != null; material = material.next)
      validateTextureTransforms(material);
    Set<String> names = new HashSet<>();
    int index = 0;
    for (JsonValue node = first(root, "nodes"); node != null; node = node.next, index++) {
      if (!names.add(node.getString("name", "glNode " + index)))
        unsupported("duplicate node names");
      if (node.has("weights")) unsupported("morph weights");
    }
    for (JsonValue mesh = first(root, "meshes"); mesh != null; mesh = mesh.next) {
      if (mesh.has("weights")) unsupported("morph weights");
      for (JsonValue primitive = first(mesh, "primitives");
          primitive != null;
          primitive = primitive.next) {
        if (primitive.has("targets")) unsupported("morph targets");
        if (primitive.getInt("mode", 4) != 4) unsupported("non-triangle primitives");
        JsonValue attributes = primitive.require("attributes");
        for (JsonValue attribute = attributes.child; attribute != null; attribute = attribute.next)
          if (!Set.of(
                  "POSITION",
                  "NORMAL",
                  "TANGENT",
                  "TEXCOORD_0",
                  "TEXCOORD_1",
                  "COLOR_0",
                  "JOINTS_0",
                  "WEIGHTS_0")
              .contains(attribute.name)) unsupported("vertex attribute " + attribute.name);
        if (attributes.has("JOINTS_1") || attributes.has("WEIGHTS_1"))
          unsupported("more than four skin weights");
      }
    }
    for (JsonValue skin = first(root, "skins"); skin != null; skin = skin.next)
      if (skin.require("joints").size > maxBones)
        unsupported("skin exceeds " + maxBones + " bones");
    for (JsonValue accessor = first(root, "accessors"); accessor != null; accessor = accessor.next)
      if (accessor.has("sparse")) unsupported("sparse accessors");
    Set<String> clips = new HashSet<>();
    int animationIndex = 0;
    for (JsonValue animation = first(root, "animations");
        animation != null;
        animation = animation.next, animationIndex++) {
      if (!clips.add(animation.getString("name", "animation" + animationIndex)))
        unsupported("duplicate effective animation names");
      for (JsonValue sampler = first(animation, "samplers");
          sampler != null;
          sampler = sampler.next)
        if (!"LINEAR".equals(sampler.getString("interpolation", "LINEAR")))
          unsupported("non-LINEAR animation interpolation");
      for (JsonValue channel = first(animation, "channels");
          channel != null;
          channel = channel.next)
        if ("weights".equals(channel.require("target").getString("path")))
          unsupported("morph animation");
    }
  }

  private record UvTransform(float u, float v, float su, float sv, float rotation) {}

  private static void validateTextureTransforms(JsonValue material) {
    Map<Integer, UvTransform> transforms = new HashMap<>();
    for (String channel : new String[] {"normalTexture", "emissiveTexture", "occlusionTexture"})
      checkTextureTransform(material.get(channel), transforms);
    JsonValue pbr = material.get("pbrMetallicRoughness");
    if (pbr != null) {
      checkTextureTransform(pbr.get("baseColorTexture"), transforms);
      checkTextureTransform(pbr.get("metallicRoughnessTexture"), transforms);
    }
  }

  private static void checkTextureTransform(
      JsonValue texture, Map<Integer, UvTransform> transforms) {
    if (texture == null) return;
    JsonValue extensions = texture.get("extensions");
    JsonValue transform = extensions == null ? null : extensions.get("KHR_texture_transform");
    int uv =
        transform == null
            ? texture.getInt("texCoord", 0)
            : transform.getInt("texCoord", texture.getInt("texCoord", 0));
    if (uv < 0 || uv > 1) unsupported("texture coordinate set " + uv);
    JsonValue offset = transform == null ? null : transform.get("offset");
    JsonValue scale = transform == null ? null : transform.get("scale");
    if ((offset != null && offset.size != 2) || (scale != null && scale.size != 2))
      throw new IllegalArgumentException("Texture transform offset/scale require two values");
    UvTransform value =
        new UvTransform(
            offset == null ? 0 : finite(offset.getFloat(0)),
            offset == null ? 0 : finite(offset.getFloat(1)),
            scale == null ? 1 : finite(scale.getFloat(0)),
            scale == null ? 1 : finite(scale.getFloat(1)),
            transform == null ? 0 : finite(transform.getFloat("rotation", 0)));
    UvTransform previous = transforms.putIfAbsent(uv, value);
    if (previous != null && !previous.equals(value))
      unsupported("different per-channel texture transforms sharing TEXCOORD_" + uv);
  }

  private static float finite(float value) {
    if (!Float.isFinite(value))
      throw new IllegalArgumentException("Texture transform must be finite");
    return value == 0 ? 0 : value;
  }

  private static JsonValue first(JsonValue root, String key) {
    JsonValue value = root.get(key);
    return value == null ? null : value.child;
  }

  private static void checkExtensions(JsonValue value) {
    if (value.isObject() && value.has("extensions"))
      for (JsonValue extension = value.get("extensions").child;
          extension != null;
          extension = extension.next)
        if (!EXTENSIONS.contains(extension.name)) unsupported("extension " + extension.name);
    for (JsonValue child = value.child; child != null; child = child.next) checkExtensions(child);
  }

  private static String jsonChunk(byte[] bytes) {
    if (bytes.length < 20) throw new IllegalArgumentException("Truncated GLB header");
    ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    if (data.getInt() != 0x46546c67 || data.getInt() != 2 || data.getInt() != bytes.length)
      throw new IllegalArgumentException("Invalid GLB header or length");
    String json = null;
    int chunks = 0;
    while (data.hasRemaining()) {
      if (data.remaining() < 8) throw new IllegalArgumentException("Truncated GLB chunk");
      int length = data.getInt(), type = data.getInt();
      if (length < 0 || length % 4 != 0 || length > data.remaining())
        throw new IllegalArgumentException("Invalid GLB chunk length");
      if (chunks == 0 && type != 0x4e4f534a)
        throw new IllegalArgumentException("GLB must start with JSON");
      if (type == 0x4e4f534a) {
        if (json != null) throw new IllegalArgumentException("Duplicate GLB JSON chunk");
        json = new String(bytes, data.position(), length, StandardCharsets.UTF_8);
      } else if (type != 0x004e4942 || chunks != 1) {
        throw new IllegalArgumentException("Unsupported GLB chunk type or order");
      }
      data.position(data.position() + length);
      chunks++;
    }
    return json;
  }

  private static void unsupported(String feature) {
    throw new IllegalArgumentException("Javity glTF profile does not support " + feature);
  }
}
