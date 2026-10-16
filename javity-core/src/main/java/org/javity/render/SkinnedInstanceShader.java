package org.javity.render;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** DefaultShader's original weighted position/normal/material formulas, with texture matrices. */
final class SkinnedInstanceShader extends DefaultShader {
  private SkinnedInstanceShader(Renderable source, Config config, ShaderProgram program) {
    super(source, config, program);
  }

  static SkinnedInstanceShader create(Renderable source) {
    Config config = new Config();
    config.numBones = source.bones.length;
    config.numBoneWeights = source.meshPart.mesh.getVertexAttributes().getBoneWeights();
    ShaderProgram program = InstanceShader.prepareProgram(createPrefix(source, config), true);
    try {
      if (!program.isCompiled()) throw new IllegalStateException(program.getLog());
      SkinnedInstanceShader result = new SkinnedInstanceShader(source, config, program);
      result.init();
      for (String attribute : new String[] {"a_i0", "a_i1", "a_i2", "a_i3", "a_paletteRow"})
        if (program.getAttributeLocation(attribute) < 0)
          throw new IllegalStateException("Missing instance attribute " + attribute);
      if (program.fetchUniformLocation("u_palette", false) < 0)
        throw new IllegalStateException("Missing palette sampler");
      return result;
    } catch (RuntimeException | Error failure) {
      program.dispose();
      throw failure;
    }
  }

  void paletteUnit(int unit) {
    program.setUniformi("u_palette", unit);
  }
}
