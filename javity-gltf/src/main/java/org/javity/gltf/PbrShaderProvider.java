package org.javity.gltf;

import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute;
import com.badlogic.gdx.utils.Array;
import java.util.IdentityHashMap;
import net.mgsx.gltf.scene3d.attributes.*;
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import org.javity.render.GdxRenderer;
import org.javity.render.RecoverableShaderProvider;

/**
 * Opt-in upstream gdx-gltf PBR shaders in the engine's ordinary world/viewmodel batches. Scene
 * camera, transforms and direct lights come from GdxRenderer. No implicit IBL. Borrowed IBL
 * attributes/resources in extras must outlive every draw.
 */
public final class PbrShaderProvider implements RecoverableShaderProvider {
  /** SRGB is the legacy display path (upstream gamma2.2); LINEAR feeds an HDR/tone-map pass. */
  public enum OutputColorSpace {
    SRGB,
    LINEAR
  }

  private final Thread owner = Thread.currentThread();
  private final PBRShaderConfig config;
  private static final int MAX_ENVIRONMENTS_PER_VIEW = 16;
  private final IdentityHashMap<Environment, Adapted> environments = new IdentityHashMap<>();
  private final Array<Adapted> environmentPool = new Array<>();
  private final Attributes extras = new Attributes();
  private net.mgsx.gltf.scene3d.shaders.PBRShaderProvider delegate;
  private boolean disposed, transferred;

  private static final class Adapted {
    final Environment combined = new Environment();
  }

  @Override
  public void beginView() {
    requireOpen();
    environments.clear();
  }

  public PbrShaderProvider(int maxBones, int directional, int point, int spot) {
    this(maxBones, directional, point, spot, OutputColorSpace.SRGB);
  }

  public PbrShaderProvider(
      int maxBones, int directional, int point, int spot, OutputColorSpace output) {
    java.util.Objects.requireNonNull(output, "output");
    if (maxBones < 1 || directional < 0 || point < 0 || spot < 0)
      throw new IllegalArgumentException("Invalid PBR capacities");
    config = new PBRShaderConfig();
    // Input texture decoding remains accurate sRGB-to-linear in BOTH output modes.
    config.manualSRGB = PBRShaderConfig.SRGB.ACCURATE;
    config.manualGammaCorrection = output == OutputColorSpace.SRGB;
    config.numBones = maxBones;
    config.numBoneWeights = 4;
    config.numDirectionalLights = directional;
    config.numPointLights = point;
    config.numSpotLights = spot;
  }

  public OutputColorSpace outputColorSpace() {
    requireOpen();
    return config.manualGammaCorrection ? OutputColorSpace.SRGB : OutputColorSpace.LINEAR;
  }

  /**
   * Borrowed PBR environment additions, e.g. diffuse/specular IBL cubemaps and BRDF LUT. Mutate
   * outside ModelBatch.begin/end. Direct scene-light attributes should not be overridden.
   */
  public Attributes environmentExtras() {
    requireOpen();
    return extras;
  }

  /** Transfers provider ownership to the returned engine renderer. */
  public GdxRenderer createRenderer() {
    requireOpen();
    if (transferred)
      throw new IllegalStateException("PBR provider ownership was already transferred");
    transferred = true;
    return new GdxRenderer(
        config.numBones,
        config.numBoneWeights,
        config.numDirectionalLights,
        config.numPointLights,
        config.numSpotLights,
        this);
  }

  @Override
  public Shader getShader(Renderable renderable) {
    requireOpen();
    validateMaterial(renderable.material);
    renderable.environment = adaptEnvironment(renderable.environment);
    if (delegate == null) {
      config.fragmentShader =
          RangePbrShader.fragmentSource(
              net.mgsx.gltf.scene3d.shaders.PBRShaderProvider.getDefaultFragmentShader());
      delegate =
          new net.mgsx.gltf.scene3d.shaders.PBRShaderProvider(config) {
            @Override
            protected net.mgsx.gltf.scene3d.shaders.PBRShader createShader(
                Renderable source, PBRShaderConfig shaderConfig, String prefix) {
              return new RangePbrShader(source, shaderConfig, prefix);
            }
          };
    }
    return delegate.getShader(renderable);
  }

  static void validateMaterial(Material material) {
    java.util.Objects.requireNonNull(material, "material");
    long textures =
        PBRTextureAttribute.BaseColorTexture
            | PBRTextureAttribute.EmissiveTexture
            | PBRTextureAttribute.NormalTexture
            | PBRTextureAttribute.MetallicRoughnessTexture
            | PBRTextureAttribute.OcclusionTexture;
    long allowed =
        textures
            | PBRColorAttribute.BaseColorFactor
            | ColorAttribute.Emissive
            | PBRFloatAttribute.Metallic
            | PBRFloatAttribute.Roughness
            | PBRFloatAttribute.NormalScale
            | PBRFloatAttribute.OcclusionStrength
            | PBRFloatAttribute.EmissiveIntensity
            | PBRFloatAttribute.ShadowBias
            | PBRFlagAttribute.Unlit
            | FloatAttribute.AlphaTest
            | IntAttribute.CullFace
            | DepthTestAttribute.Type
            | BlendingAttribute.Type;
    for (var attribute : material) {
      if ((attribute.type & allowed) == 0)
        throw new IllegalArgumentException(
            "PBR pass does not support material attribute "
                + attribute
                + "; author PBR attributes explicitly instead of implicit material conversion");
      if ((attribute.type & textures) != 0 && !(attribute instanceof PBRTextureAttribute))
        throw new IllegalArgumentException(
            "PBR texture channels require PBRTextureAttribute with explicit UV transform");
    }
  }

  Environment adaptEnvironment(Environment scene) {
    requireOpen();
    if (scene != null) {
      // Javity's fog equation is custom DefaultShader code, not the upstream PBR equation.
      if (scene.has(ColorAttribute.Fog))
        throw new IllegalArgumentException(
            "Javity linear scene fog is not supported by the PBR pass");
      SpotLightsAttribute spots = scene.get(SpotLightsAttribute.class, SpotLightsAttribute.Type);
      if (spots != null && spots.lights.size != 0)
        throw new IllegalArgumentException(
            "Javity cosine/exponent spot cones are not supported by the PBR pass");
      Adapted adapted = environments.get(scene);
      if (adapted == null) {
        int index = environments.size();
        if (index == MAX_ENVIRONMENTS_PER_VIEW)
          throw new IllegalStateException("PBR pass exceeds 16 source environments in one view");
        if (environmentPool.size <= index) environmentPool.add(new Adapted());
        adapted = environmentPool.get(index);
        environments.put(scene, adapted);
      }
      Environment combined = adapted.combined;
      combined.clear();
      combined.set(scene);
      combined.set(extras);
      combined.shadowMap = scene.shadowMap;
      return combined;
    }
    return null;
  }

  @Override
  public void contextRestored() {
    requireOpen();
    // BaseShader caches integer uniform locations. Recreate shaders after managed relink.
    if (delegate != null) delegate.dispose();
    delegate = null;
  }

  @Override
  public void dispose() {
    checkThread();
    if (disposed) return;
    disposed = true;
    try {
      if (delegate != null) delegate.dispose();
    } finally {
      delegate = null;
      environments.clear();
      for (Adapted adapted : environmentPool) {
        adapted.combined.clear();
        adapted.combined.shadowMap = null;
      }
      environmentPool.clear();
      extras.clear();
    }
  }

  private void requireOpen() {
    checkThread();
    if (disposed) throw new IllegalStateException("PBR provider is disposed");
  }

  private void checkThread() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("PBR provider requires its owner thread");
  }
}
