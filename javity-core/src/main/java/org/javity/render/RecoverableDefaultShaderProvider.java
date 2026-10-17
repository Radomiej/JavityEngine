/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

/* Retained-location recovery and light formulas are pinned to libGDX 1.14.2
 * BaseShader/DefaultShader. Bundled upstream LICENSE/AUTHORS and THIRD_PARTY.md apply. */
package org.javity.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Version;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.IntIntMap;
import java.lang.reflect.Field;
import org.javity.render.lighting.*;

/** Retains ModelBatch-owned shader objects while repairing their post-link location caches. */
final class RecoverableDefaultShaderProvider extends DefaultShaderProvider {
  private final Access access;
  final DefaultShader.Config sceneConfig;

  RecoverableDefaultShaderProvider(DefaultShader.Config config) {
    this(
        config,
        RenderEnvironment3D.DEFAULT_DIRECTIONAL_CAPACITY,
        RenderEnvironment3D.DEFAULT_POINT_CAPACITY,
        RenderEnvironment3D.DEFAULT_SPOT_CAPACITY);
  }

  RecoverableDefaultShaderProvider(
      DefaultShader.Config config, int directional, int point, int spot) {
    super(config);
    access = new Access();
    sceneConfig = new DefaultShader.Config();
    sceneConfig.numBones = config.numBones;
    sceneConfig.numBoneWeights = config.numBoneWeights;
    sceneConfig.numDirectionalLights = directional;
    sceneConfig.numPointLights = point;
    sceneConfig.numSpotLights = spot;
    sceneConfig.defaultCullFace = config.defaultCullFace;
    sceneConfig.defaultDepthFunc = config.defaultDepthFunc;
  }

  @Override
  protected Shader createShader(Renderable renderable) {
    // Copy descriptor values before the DefaultShader constructor allocates a ShaderProgram.
    AttributeAlias[] vertices = aliases(renderable.meshPart.mesh.getVertexAttributes());
    AttributeAlias[] instances = aliases(renderable.meshPart.mesh.getInstancedAttributes());
    boolean scene = isScene(renderable);
    if (scene && sceneConfig.vertexShader == null) {
      sceneConfig.vertexShader =
          Gdx.files.classpath("org/javity/render/scene-lighting.vertex.glsl").readString();
      sceneConfig.fragmentShader =
          Gdx.files.classpath("org/javity/render/scene-lighting.fragment.glsl").readString();
    }
    return new RecoverableDefaultShader(
        renderable, scene ? sceneConfig : config, access, vertices, instances, scene);
  }

  /**
   * GL owner thread only, after managed ShaderProgram relink AND alias-map refresh, before draws.
   */
  void contextRestored() {
    for (Shader shader : shaders) ((RecoverableDefaultShader) shader).contextRestored();
  }

  private static boolean isScene(Renderable renderable) {
    return renderable.environment != null
        && renderable.environment.has(SceneLightingAttribute.Type);
  }

  private record AttributeAlias(int key, String alias) {}

  private static AttributeAlias[] aliases(VertexAttributes attributes) {
    AttributeAlias[] result = new AttributeAlias[attributes == null ? 0 : attributes.size()];
    for (int i = 0; i < result.length; i++) {
      VertexAttribute attribute = attributes.get(i);
      result[i] = new AttributeAlias(attribute.getKey(), attribute.alias);
    }
    return result;
  }

  private static IntIntMap currentAttributes(ShaderProgram program, AttributeAlias[] aliases) {
    IntIntMap result = new IntIntMap();
    for (AttributeAlias alias : aliases) {
      int location = program.getAttributeLocation(alias.alias());
      if (location >= 0) result.put(alias.key(), location);
    }
    return result;
  }

  private static final class RecoverableDefaultShader extends DefaultShader {
    private final Access access;
    private final AttributeAlias[] vertexAliases, instanceAliases;
    private boolean[] originallyActiveUniforms;
    private final boolean scene;
    private final int[][] sceneDirectional, scenePoint, sceneSpot;
    private final int sceneAmbient, sceneFogRange;

    RecoverableDefaultShader(
        Renderable renderable,
        Config config,
        Access access,
        AttributeAlias[] vertices,
        AttributeAlias[] instances,
        boolean scene) {
      super(
          renderable,
          config,
          createPrefix(renderable, config)
              + (scene && renderable.environment.has(ColorAttribute.Fog)
                  ? "#define sceneFogFlag\n"
                  : ""));
      this.scene = scene;
      sceneDirectional =
          registerLights(
              "u_sceneDirectional", scene ? config.numDirectionalLights : 0, "color", "direction");
      scenePoint =
          registerLights(
              "u_scenePoint",
              scene ? config.numPointLights : 0,
              "color",
              "position",
              "intensity",
              "range");
      sceneSpot =
          registerLights(
              "u_sceneSpot",
              scene ? config.numSpotLights : 0,
              "color",
              "position",
              "direction",
              "intensity",
              "range",
              "cutoff",
              "exponent");
      sceneAmbient = scene ? register("u_sceneAmbient") : -1;
      sceneFogRange = scene ? register("u_sceneFogRange") : -1;
      this.access = access;
      vertexAliases = vertices;
      instanceAliases = instances;
    }

    private int[][] registerLights(String prefix, int count, String... fields) {
      int[][] ids = new int[count][fields.length];
      for (int i = 0; i < count; i++)
        for (int j = 0; j < fields.length; j++)
          ids[i][j] = register(prefix + "[" + i + "]." + fields[j]);
      return ids;
    }

    @Override
    public boolean canRender(Renderable renderable) {
      return scene == isScene(renderable) && super.canRender(renderable);
    }

    @Override
    public void render(Renderable renderable, Attributes attributes) {
      // DefaultShader skips bindLights for meshes without normals. Fog is surface-independent.
      if (scene) {
        SceneLightingAttribute marker =
            renderable.environment.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
        set(sceneFogRange, marker.fogNear(), marker.fogFar());
        ColorAttribute fog = renderable.environment.get(ColorAttribute.class, ColorAttribute.Fog);
        if (fog != null) set(u_fogColor, fog.color);
      }
      super.render(renderable, attributes);
    }

    @Override
    protected void bindLights(Renderable renderable, Attributes attributes) {
      if (!scene) {
        super.bindLights(renderable, attributes);
        return;
      }
      SceneLightingAttribute marker =
          renderable.environment.get(SceneLightingAttribute.class, SceneLightingAttribute.Type);
      ColorAttribute ambient =
          renderable.environment.get(ColorAttribute.class, ColorAttribute.AmbientLight);
      set(sceneAmbient, ambient.color.r, ambient.color.g, ambient.color.b);
      set(sceneFogRange, marker.fogNear(), marker.fogFar());
      ColorAttribute fog = renderable.environment.get(ColorAttribute.class, ColorAttribute.Fog);
      if (fog != null) set(u_fogColor, fog.color);
      DirectionalLightsAttribute dirs =
          renderable.environment.get(
              DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type);
      PointLightsAttribute points =
          renderable.environment.get(PointLightsAttribute.class, PointLightsAttribute.Type);
      SpotLightsAttribute spots =
          renderable.environment.get(SpotLightsAttribute.class, SpotLightsAttribute.Type);
      for (int i = 0; i < sceneDirectional.length; i++) {
        int[] ids = sceneDirectional[i];
        DirectionalLight light = dirs != null && i < dirs.lights.size ? dirs.lights.get(i) : null;
        if (light == null) {
          set(ids[0], 0f, 0f, 0f);
          set(ids[1], 0f, -1f, 0f);
        } else {
          set(ids[0], light.color.r, light.color.g, light.color.b);
          set(ids[1], light.direction);
        }
      }
      for (int i = 0; i < scenePoint.length; i++) {
        int[] ids = scenePoint[i];
        PointLight light = points != null && i < points.lights.size ? points.lights.get(i) : null;
        if (light == null) {
          set(ids[0], 0f, 0f, 0f);
          set(ids[1], 0f, 0f, 0f);
          set(ids[2], 0f);
        } else {
          set(ids[0], light.color.r, light.color.g, light.color.b);
          set(ids[1], light.position);
          set(ids[2], light.intensity);
        }
        set(ids[3], marker.pointRange(i));
      }
      for (int i = 0; i < sceneSpot.length; i++) {
        int[] ids = sceneSpot[i];
        SpotLight light = spots != null && i < spots.lights.size ? spots.lights.get(i) : null;
        if (light == null) {
          set(ids[0], 0f, 0f, 0f);
          set(ids[1], 0f, 0f, 0f);
          set(ids[2], 0f, -1f, 0f);
          set(ids[3], 0f);
          set(ids[5], 1f);
          set(ids[6], 1f);
        } else {
          set(ids[0], light.color.r, light.color.g, light.color.b);
          set(ids[1], light.position);
          set(ids[2], light.direction);
          set(ids[3], light.intensity);
          set(ids[5], (float) Math.cos(Math.toRadians(light.cutoffAngle)));
          set(ids[6], light.exponent);
        }
        set(ids[4], marker.spotRange(i));
      }
    }

    @Override
    public void init() {
      ShaderProgram ownedProgram = program;
      try {
        super.init();
        int[] locations = access.locations(this);
        originallyActiveUniforms = new boolean[locations.length];
        for (int i = 0; i < locations.length; i++) originallyActiveUniforms[i] = locations[i] >= 0;
      } catch (RuntimeException | Error failure) {
        // BaseShaderProvider has not adopted this failed shader yet; its program is ours to close.
        try {
          ownedProgram.dispose();
        } catch (RuntimeException | Error cleanup) {
          if (cleanup != failure) failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    }

    void contextRestored() {
      if (program == null || !program.isCompiled() || originallyActiveUniforms == null)
        throw new IllegalStateException(
            "Owned DefaultShader is not initialized/linked for context recovery");
      int[] locations = access.locations(this);
      if (locations.length != originallyActiveUniforms.length)
        throw new IllegalStateException("Unexpected libGDX BaseShader location cache shape");
      int[] refreshed = new int[locations.length];
      for (int i = 0; i < locations.length; i++)
        refreshed[i] =
            originallyActiveUniforms[i]
                ? program.fetchUniformLocation(getUniformAlias(i), false)
                : -1;
      IntIntMap vertices = currentAttributes(program, vertexAliases);
      IntIntMap instances = currentAttributes(program, instanceAliases);
      int[] boneLocations = access.boneWeights(this);
      int[] refreshedBones = boneLocations == null ? null : new int[boneLocations.length];
      if (refreshedBones != null)
        for (int i = 0; i < refreshedBones.length; i++)
          refreshedBones[i] = program.getAttributeLocation(ShaderProgram.BONEWEIGHT_ATTRIBUTE + i);
      // Query all candidates first. Preserve validators, setter objects and their original ID
      // lists.
      IntIntMap targetVertices = access.attributes(this);
      IntIntMap targetInstances = access.instances(this);
      System.arraycopy(refreshed, 0, locations, 0, locations.length);
      targetVertices.clear();
      targetVertices.putAll(vertices);
      targetInstances.clear();
      targetInstances.putAll(instances);
      if (refreshedBones != null)
        System.arraycopy(refreshedBones, 0, boneLocations, 0, boneLocations.length);
      refreshLightLocations();
    }

    private void refreshLightLocations() {
      dirLightsLoc = loc(u_dirLights0color);
      dirLightsColorOffset = loc(u_dirLights0color) - dirLightsLoc;
      dirLightsDirectionOffset = loc(u_dirLights0direction) - dirLightsLoc;
      dirLightsSize = Math.max(0, loc(u_dirLights1color) - dirLightsLoc);
      pointLightsLoc = loc(u_pointLights0color);
      pointLightsColorOffset = loc(u_pointLights0color) - pointLightsLoc;
      pointLightsPositionOffset = loc(u_pointLights0position) - pointLightsLoc;
      pointLightsIntensityOffset =
          has(u_pointLights0intensity) ? loc(u_pointLights0intensity) - pointLightsLoc : -1;
      pointLightsSize = Math.max(0, loc(u_pointLights1color) - pointLightsLoc);
      spotLightsLoc = loc(u_spotLights0color);
      spotLightsColorOffset = loc(u_spotLights0color) - spotLightsLoc;
      spotLightsPositionOffset = loc(u_spotLights0position) - spotLightsLoc;
      spotLightsDirectionOffset = loc(u_spotLights0direction) - spotLightsLoc;
      spotLightsIntensityOffset =
          has(u_spotLights0intensity) ? loc(u_spotLights0intensity) - spotLightsLoc : -1;
      spotLightsCutoffAngleOffset = loc(u_spotLights0cutoffAngle) - spotLightsLoc;
      spotLightsExponentOffset = loc(u_spotLights0exponent) - spotLightsLoc;
      spotLightsSize = Math.max(0, loc(u_spotLights1color) - spotLightsLoc);
    }
  }

  /** Fixed, type-checked upstream members only. Keep rules accompany this pinned adapter. */
  private static final class Access {
    private final Field locations, attributes, instances, boneWeights;

    Access() {
      try {
        // Runtime read avoids javac inlining Version.VERSION and hiding a substituted GDX jar.
        Object version = Version.class.getField("VERSION").get(null);
        if (!"1.14.2".equals(version))
          throw new IllegalStateException(
              "DefaultShader recovery requires libGDX 1.14.2; found " + version);
        locations = field(BaseShader.class, "locations", int[].class);
        attributes = field(BaseShader.class, "attributes", IntIntMap.class);
        instances = field(BaseShader.class, "instancedAttributes", IntIntMap.class);
        boneWeights = field(DefaultShader.class, "boneWeightsLocations", int[].class);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException(
            "Missing pinned libGDX 1.14.2 shader cache members/keep rules", failure);
      }
    }

    private static Field field(Class<?> owner, String name, Class<?> type)
        throws ReflectiveOperationException {
      Field field = owner.getDeclaredField(name);
      if (field.getType() != type)
        throw new NoSuchFieldException(owner.getName() + "." + name + " has changed type");
      field.setAccessible(true);
      return field;
    }

    private static Object read(Field field, Object owner) {
      try {
        return field.get(owner);
      } catch (IllegalAccessException failure) {
        throw new IllegalStateException("Pinned shader cache became inaccessible", failure);
      }
    }

    int[] locations(BaseShader owner) {
      return (int[]) read(locations, owner);
    }

    IntIntMap attributes(BaseShader owner) {
      return (IntIntMap) read(attributes, owner);
    }

    IntIntMap instances(BaseShader owner) {
      return (IntIntMap) read(instances, owner);
    }

    int[] boneWeights(DefaultShader owner) {
      return (int[]) read(boneWeights, owner);
    }
  }
}
