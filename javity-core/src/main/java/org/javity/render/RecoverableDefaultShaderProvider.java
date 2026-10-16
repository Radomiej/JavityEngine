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

import com.badlogic.gdx.Version;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.IntIntMap;
import java.lang.reflect.Field;

/** Retains ModelBatch-owned shader objects while repairing their post-link location caches. */
final class RecoverableDefaultShaderProvider extends DefaultShaderProvider {
  private final Access access;

  RecoverableDefaultShaderProvider(DefaultShader.Config config) {
    super(config);
    access = new Access();
  }

  @Override
  protected Shader createShader(Renderable renderable) {
    // Copy descriptor values before the DefaultShader constructor allocates a ShaderProgram.
    AttributeAlias[] vertices = aliases(renderable.meshPart.mesh.getVertexAttributes());
    AttributeAlias[] instances = aliases(renderable.meshPart.mesh.getInstancedAttributes());
    return new RecoverableDefaultShader(renderable, config, access, vertices, instances);
  }

  /**
   * GL owner thread only, after managed ShaderProgram relink AND alias-map refresh, before draws.
   */
  void contextRestored() {
    for (Shader shader : shaders) ((RecoverableDefaultShader) shader).contextRestored();
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

    RecoverableDefaultShader(
        Renderable renderable,
        Config config,
        Access access,
        AttributeAlias[] vertices,
        AttributeAlias[] instances) {
      super(renderable, config);
      this.access = access;
      vertexAliases = vertices;
      instanceAliases = instances;
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
