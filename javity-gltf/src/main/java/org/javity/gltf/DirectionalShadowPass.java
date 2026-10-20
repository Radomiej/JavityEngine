package org.javity.gltf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.ShadowMap;
import com.badlogic.gdx.graphics.g3d.shaders.DepthShader;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.utils.*;
import java.util.IdentityHashMap;
import java.util.Objects;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.shaders.PBRDepthShaderProvider;
import org.javity.core.*;
import org.javity.render.*;
import org.javity.render.lighting.*;

/** One bounded packed-depth shadow for the first scene directional, in an optional PBR world. */
public final class DirectionalShadowPass implements WorldRenderPass, ShadowMap {
  public static final int MAX_RESOLUTION = 2048;
  public static final long ATTACHMENT_BUDGET_BYTES = 64L * 1024 * 1024;

  /** Fixed world-space orthographic volume; no automatic fitting, cascades or stabilization. */
  public record Settings(
      int resolution,
      float width,
      float height,
      float near,
      float far,
      float centerX,
      float centerY,
      float centerZ,
      float bias) {
    public Settings {
      if (resolution < 16 || resolution > MAX_RESOLUTION)
        throw new IllegalArgumentException("Shadow resolution must be in [16,2048]");
      for (float value : new float[] {width, height, near, far, centerX, centerY, centerZ, bias})
        if (!Float.isFinite(value))
          throw new IllegalArgumentException("Shadow settings must be finite");
      if (width <= 0 || height <= 0 || near < 0 || far <= near || bias < 0 || bias > .1f)
        throw new IllegalArgumentException("Invalid shadow volume or bias");
      if (!Float.isFinite(far - near) || !Float.isFinite(near + .5f * (far - near)))
        throw new IllegalArgumentException("Shadow depth interval is not representable");
    }

    public long attachmentBytes() {
      return 6L * resolution * resolution;
    }
  }

  private final Thread owner = Thread.currentThread();
  private final PbrShaderProvider requiredProvider;
  private final DirectionalLight3D sun;
  private final int maxBones, maxBoneWeights;
  private final OrthographicCamera lightCamera = new OrthographicCamera();
  private final Environment receivers = new Environment(), unshadowed = new Environment();
  private final PBRFloatAttribute shadowBias =
      new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, 0);
  private final TextureDescriptor<Texture> texture = new TextureDescriptor<>();
  private final Array<Renderable> depthRenderables = new Array<>();
  private final FlushablePool<Renderable> depthPool =
      new FlushablePool<>() {
        @Override
        protected Renderable newObject() {
          return new Renderable();
        }
      };
  private RecoverableFrameBuffer target;
  private ModelBatch depthBatch;
  private Settings settings;
  private boolean closed, rendering, claimed;

  public DirectionalShadowPass(
      PbrShaderProvider provider,
      DirectionalLight3D sun,
      Settings settings,
      int maxBones,
      int maxBoneWeights) {
    requiredProvider = Objects.requireNonNull(provider, "provider");
    this.sun = Objects.requireNonNull(sun, "sun");
    this.settings = Objects.requireNonNull(settings, "settings");
    if (maxBones < 1 || maxBoneWeights < 0 || maxBoneWeights > 8)
      throw new IllegalArgumentException("Invalid shadow skinning capacity");
    this.maxBones = maxBones;
    this.maxBoneWeights = maxBoneWeights;
    requireCapability();
    ShadowGlState state = new ShadowGlState();
    Throwable allocationFailure = null;
    try {
      target = makeTarget(settings);
      depthBatch = makeBatch();
    } catch (RuntimeException | Error failure) {
      allocationFailure = failure;
      closePreserving(failure, depthBatch, target);
      throw failure;
    } finally {
      try {
        state.restore();
      } catch (RuntimeException | Error restoreFailure) {
        if (allocationFailure != null) {
          if (restoreFailure != allocationFailure) allocationFailure.addSuppressed(restoreFailure);
        } else {
          closePreserving(restoreFailure, depthBatch, target);
          throw restoreFailure;
        }
      }
    }
    texture.minFilter = texture.magFilter = Texture.TextureFilter.Nearest;
    texture.uWrap = texture.vWrap = Texture.TextureWrap.ClampToEdge;
  }

  @Override
  public void validateShaderProvider(RecoverableShaderProvider provider) {
    requireIdle();
    if (provider != requiredProvider)
      throw new IllegalArgumentException("Directional shadows require their exact PBR provider");
    if (claimed) throw new IllegalStateException("Shadow pass ownership was already transferred");
    claimed = true;
  }

  @Override
  public void render(
      Camera camera,
      Array<Renderable> renderables,
      IdentityHashMap<Renderable, Component> owners,
      Environment environment) {
    requireIdle();
    requireCapability();
    Objects.requireNonNull(camera, "camera");
    Objects.requireNonNull(renderables, "renderables");
    Objects.requireNonNull(owners, "owners");
    validateSun(sun, environment);
    // Validate all borrowed input before changing GL or the published receiver environments.
    for (Renderable source : renderables) {
      Component model = owners.get(source);
      if (model == null || model.getGame() != sun.getGame() || !eligible(model))
        throw new IllegalArgumentException(
            "Shadow input must belong to the selected sun's live scene");
      ShadowPolicy3D policy = policy(model.getGameObject());
      if (policy != null && policy.cast) validateCaster(source);
    }
    var first =
        environment
            .get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type)
            .lights
            .first();
    lightCamera.viewportWidth = settings.width();
    lightCamera.viewportHeight = settings.height();
    lightCamera.near = settings.near();
    lightCamera.far = settings.far();
    lightCamera.direction.set(first.direction).nor();
    lightCamera.up.set(
        Math.abs(lightCamera.direction.y) > .99f ? 1 : 0,
        Math.abs(lightCamera.direction.y) > .99f ? 0 : 1,
        0);
    lightCamera
        .position
        .set(lightCamera.direction)
        .scl(-(settings.near() + .5f * (settings.far() - settings.near())))
        .add(settings.centerX(), settings.centerY(), settings.centerZ());
    lightCamera.normalizeUp();
    lightCamera.update();
    for (float value : lightCamera.combined.val)
      if (!Float.isFinite(value))
        throw new IllegalArgumentException("Shadow camera matrix is not representable");
    ShadowGlState state = new ShadowGlState();
    rendering = true;
    Throwable failure = null;
    try {
      for (Renderable source : renderables) {
        ShadowPolicy3D policy = policy(owners.get(source).getGameObject());
        if (policy != null && policy.cast) {
          Renderable copy = depthPool.obtain().set(source);
          copy.shader = null;
          copy.environment = null;
          depthRenderables.add(copy);
        }
      }
      state.prepareDepth();
      target.begin();
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glColorMask(true, true, true, true);
      Gdx.gl.glDepthMask(true);
      Gdx.gl.glDepthRangef(0, 1);
      Gdx.gl.glClearColor(1, 1, 1, 1);
      Gdx.gl.glClearDepthf(1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      // Keep a white border for taps reaching outside the fitted volume.
      Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glScissor(1, 1, settings.resolution() - 2, settings.resolution() - 2);
      depthBatch.begin(lightCamera);
      Throwable submissionFailure = null;
      try {
        for (Renderable copy : depthRenderables) depthBatch.render(copy);
      } catch (RuntimeException | Error problem) {
        submissionFailure = problem;
        throw problem;
      } finally {
        try {
          depthBatch.end();
        } catch (RuntimeException | Error problem) {
          if (submissionFailure == null) throw problem;
          if (problem != submissionFailure) submissionFailure.addSuppressed(problem);
        }
      }
    } catch (RuntimeException | Error problem) {
      failure = problem;
      // A failed ModelBatch.flush may leave it begun with queued renderables. Retire that batch
      // before returning its pooled copies, so the next frame cannot reuse a half-finished pass.
      ModelBatch broken = depthBatch;
      try {
        depthBatch = makeBatch();
      } catch (RuntimeException | Error replacementFailure) {
        if (replacementFailure != problem) problem.addSuppressed(replacementFailure);
      }
      closePreserving(problem, broken);
      throw problem;
    } finally {
      depthRenderables.clear();
      depthPool.flush();
      try {
        state.restore();
      } catch (RuntimeException | Error restore) {
        if (failure != null) {
          if (restore != failure) failure.addSuppressed(restore);
        } else throw restore;
      } finally {
        rendering = false;
      }
    }
    receivers.clear();
    receivers.set(environment);
    receivers.shadowMap = this;
    shadowBias.value = settings.bias();
    receivers.set(shadowBias);
    unshadowed.clear();
    unshadowed.set(environment);
    unshadowed.shadowMap = null;
    for (Renderable source : renderables) {
      ShadowPolicy3D policy = policy(owners.get(source).getGameObject());
      source.environment = policy != null && policy.receive ? receivers : unshadowed;
    }
  }

  static ShadowPolicy3D policy(GameObject object) {
    if (object == null || !object.isActive() || object.isDestroyed()) return null;
    ShadowPolicy3D policy = object.getComponent(ShadowPolicy3D.class);
    return policy != null && object.containsComponent(policy) && eligible(policy) ? policy : null;
  }

  static void validateSun(DirectionalLight3D sun, Environment environment) {
    Objects.requireNonNull(sun, "sun");
    if (!eligible(sun) || sun.getGame() == null)
      throw new IllegalArgumentException("Shadow sun must be a live scene component");
    if (environment == null || !environment.has(SceneLightingAttribute.Type))
      throw new IllegalArgumentException("Directional shadows require a scene lighting snapshot");
    var lights = environment.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type);
    if (lights == null || lights.lights.size == 0)
      throw new IllegalArgumentException("Scene directional capacity excludes the shadow sun");
    DirectionalLight3D first = null;
    for (GameObject object : sun.getGame().getObjectsWith(Light3D.class)) {
      if (!object.isActive()) continue;
      Transform3D transform = object.getComponent(Transform3D.class);
      if (transform == null || !eligible(transform)) continue;
      for (Light3D light : object.getComponents(Light3D.class))
        if (light instanceof DirectionalLight3D directional && eligible(light)) {
          first = directional;
          break;
        }
      if (first != null) break;
    }
    if (first != sun)
      throw new IllegalArgumentException(
          "Only the first published scene directional can cast PBR shadows");
    float length = lights.lights.first().direction.len2();
    if (!Float.isFinite(length) || length == 0)
      throw new IllegalArgumentException("Published shadow direction must be finite and nonzero");
  }

  private static boolean eligible(Component component) {
    return component != null
        && component.isInitialized()
        && component.isEnabled()
        && component.getGameObject() != null
        && component.getGameObject().isActive()
        && !component.getGameObject().isDestroyed()
        && component.getGameObject().containsComponent(component);
  }

  private void validateCaster(Renderable renderable) {
    if (renderable.material.has(BlendingAttribute.Type)
        && !renderable.material.has(FloatAttribute.AlphaTest))
      throw new IllegalArgumentException(
          "Transparent BLEND shadow casting is unsupported; use MASK or disable cast");
    if (renderable.material.has(FloatAttribute.AlphaTest)) {
      // Pinned PBRDepthShader samples only base-color texture alpha on UV0. It does not
      // multiply that alpha by the PBR factor, opacity or vertex color used by the color pass.
      BlendingAttribute blending =
          renderable.material.get(BlendingAttribute.class, BlendingAttribute.Type);
      if (blending == null)
        throw new IllegalArgumentException("MASK shadow casting requires BlendingAttribute");
      if (blending.opacity != 1)
        throw new IllegalArgumentException("MASK shadow casting requires unit opacity");
      PBRColorAttribute factor =
          renderable.material.get(PBRColorAttribute.class, PBRColorAttribute.BaseColorFactor);
      if (factor != null && factor.color.a != 1)
        throw new IllegalArgumentException("MASK shadow casting requires unit base-color alpha");
      PBRTextureAttribute baseColor =
          renderable.material.get(PBRTextureAttribute.class, PBRTextureAttribute.BaseColorTexture);
      float cutoff = renderable.material.get(FloatAttribute.class, FloatAttribute.AlphaTest).value;
      if (!Float.isFinite(cutoff) || cutoff < 0)
        throw new IllegalArgumentException("MASK shadow cutoff must be finite and nonnegative");
      if (baseColor == null && cutoff >= 1)
        throw new IllegalArgumentException(
            "MASK shadow casting does not support untextured cutoff at or above one");
      if (baseColor != null && baseColor.uvIndex != 0)
        throw new IllegalArgumentException("MASK shadow casting supports only base-color UV0");
      for (VertexAttribute attribute : renderable.meshPart.mesh.getVertexAttributes())
        if (attribute.usage == VertexAttributes.Usage.ColorPacked
            || attribute.usage == VertexAttributes.Usage.ColorUnpacked)
          throw new IllegalArgumentException("MASK shadow casting does not support vertex color");
    }
    if (renderable.bones != null && renderable.bones.length > maxBones)
      throw new IllegalArgumentException("Shadow skin exceeds configured bone capacity");
    for (VertexAttribute attribute : renderable.meshPart.mesh.getVertexAttributes())
      if (attribute.usage == VertexAttributes.Usage.BoneWeight && attribute.unit >= maxBoneWeights)
        throw new IllegalArgumentException("Shadow skin exceeds configured bone-weight capacity");
    for (float value : renderable.worldTransform.val)
      if (!Float.isFinite(value))
        throw new IllegalArgumentException("Shadow caster matrix must be finite");
  }

  private ModelBatch makeBatch() {
    DepthShader.Config config = PBRDepthShaderProvider.createDefaultConfig();
    // Pinned PBR color discards alpha <= cutoff; its depth source instead uses <.
    // Match the visible silhouette, including fully transparent texels with cutoff zero.
    String alphaDiscard = "texture2D(u_diffuseTexture, v_texCoords0).a < u_alphaTest";
    int seam = config.fragmentShader.indexOf(alphaDiscard);
    if (seam < 0 || config.fragmentShader.indexOf(alphaDiscard, seam + 1) >= 0)
      throw new IllegalStateException("gdx-gltf 2.3.0 depth alpha-test shader source drift");
    config.fragmentShader =
        config.fragmentShader.replace(
            alphaDiscard, "texture2D(u_diffuseTexture, v_texCoords0).a <= u_alphaTest");
    config.numBones = maxBones;
    config.numBoneWeights = maxBoneWeights;
    config.defaultCullFace = GL20.GL_BACK;
    return new ModelBatch(new DepthProvider(config));
  }

  private static final class DepthProvider extends PBRDepthShaderProvider {
    DepthProvider(DepthShader.Config config) {
      super(config);
    }

    int[] programIds() {
      int[] ids = new int[shaders.size];
      for (int i = 0; i < shaders.size; i++)
        ids[i] =
            ((com.badlogic.gdx.graphics.g3d.shaders.BaseShader) shaders.get(i)).program.getHandle();
      return ids;
    }
  }

  /** Snapshot of owned depth programs for live-context resource-lifetime diagnostics. */
  public int[] shaderProgramIds() {
    requireIdle();
    return ((DepthProvider) depthBatch.getShaderProvider()).programIds();
  }

  private static RecoverableFrameBuffer makeTarget(Settings settings) {
    return new RecoverableFrameBuffer(
        Pixmap.Format.RGBA8888, settings.resolution(), settings.resolution(), true);
  }

  /** Replacement is allocated first; failure preserves the previous target and settings. */
  public void configure(Settings next) {
    requireIdle();
    Objects.requireNonNull(next, "settings");
    if (next.equals(settings)) return;
    if (next.resolution() == settings.resolution()) {
      settings = next;
      return;
    }
    if (Math.addExact(settings.attachmentBytes(), next.attachmentBytes()) > ATTACHMENT_BUDGET_BYTES)
      throw new IllegalArgumentException("Shadow replacement peak exceeds attachment budget");
    ShadowGlState state = new ShadowGlState();
    Throwable failure = null;
    try {
      RecoverableFrameBuffer candidate = makeTarget(next);
      RecoverableFrameBuffer previous = target;
      ResourceIds oldIds = resourceIds();
      target = candidate;
      settings = next;
      ResourceIds newIds = resourceIds();
      state.replaceOwnedTarget(
          oldIds.framebuffer(),
          oldIds.colorTexture(),
          oldIds.depthRenderbuffer(),
          newIds.framebuffer(),
          newIds.colorTexture(),
          newIds.depthRenderbuffer());
      previous.dispose();
    } catch (RuntimeException | Error problem) {
      failure = problem;
      throw problem;
    } finally {
      restorePreserving(state, failure);
    }
  }

  public Settings settings() {
    requireIdle();
    return settings;
  }

  /** Owned names for real-context lifecycle checks; shader programs are depthBatch-owned. */
  public record ResourceIds(int framebuffer, int colorTexture, int depthRenderbuffer) {}

  public ResourceIds resourceIds() {
    requireIdle();
    return new ResourceIds(
        target.getFramebufferHandle(),
        target.getColorBufferTexture().getTextureObjectHandle(),
        target.getDepthBufferHandle());
  }

  @Override
  public com.badlogic.gdx.math.Matrix4 getProjViewTrans() {
    checkThread();
    if (closed) throw new IllegalStateException("Shadow pass is disposed");
    return lightCamera.combined;
  }

  @Override
  public TextureDescriptor<Texture> getDepthMap() {
    checkThread();
    if (closed) throw new IllegalStateException("Shadow pass is disposed");
    texture.texture = target.getColorBufferTexture();
    return texture;
  }

  @Override
  public void contextRestored() {
    requireIdle();
    requireCapability();
    ShadowGlState state = new ShadowGlState();
    Throwable failure = null;
    try {
      ModelBatch replacement = makeBatch();
      ModelBatch previous = depthBatch;
      depthBatch = replacement;
      previous.dispose();
      texture.texture = null;
    } catch (RuntimeException | Error problem) {
      failure = problem;
      throw problem;
    } finally {
      restorePreserving(state, failure);
    }
  }

  @Override
  public void dispose() {
    checkThread();
    if (closed) return;
    if (rendering) throw new IllegalStateException("Cannot close an active shadow pass");
    closed = true;
    receivers.shadowMap = null;
    unshadowed.shadowMap = null;
    receivers.clear();
    unshadowed.clear();
    texture.texture = null;
    Throwable failure = null;
    for (Disposable resource : new Disposable[] {depthBatch, target}) {
      try {
        if (resource != null) resource.dispose();
      } catch (RuntimeException | Error problem) {
        if (failure == null) failure = problem;
        else if (failure != problem) failure.addSuppressed(problem);
      }
    }
    if (failure instanceof RuntimeException problem) throw problem;
    if (failure instanceof Error problem) throw problem;
  }

  private void checkThread() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Shadow pass requires its GL owner thread");
  }

  private void requireIdle() {
    checkThread();
    if (closed) throw new IllegalStateException("Shadow pass is disposed");
    if (rendering) throw new IllegalStateException("Shadow pass is not reentrant");
  }

  private static void requireCapability() {
    if (Gdx.gl30 == null || Gdx.gl == null)
      throw new UnsupportedOperationException(
          "Directional shadows currently require an active GL30 context");
  }

  private static void closePreserving(Throwable failure, Disposable... resources) {
    for (Disposable resource : resources)
      if (resource != null)
        try {
          resource.dispose();
        } catch (RuntimeException | Error cleanup) {
          if (cleanup != failure) failure.addSuppressed(cleanup);
        }
  }

  private static void restorePreserving(ShadowGlState state, Throwable failure) {
    try {
      state.restore();
    } catch (RuntimeException | Error restoreFailure) {
      if (failure == null) throw restoreFailure;
      if (restoreFailure != failure) failure.addSuppressed(restoreFailure);
    }
  }
}
