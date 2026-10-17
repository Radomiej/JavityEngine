package org.javity.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.javity.animation.AnimationMarker;
import org.javity.animation.AnimationTransition;
import org.javity.animation.ModelAnimator;
import org.javity.animation.ModelPlayback;
import org.javity.avatar.AvatarBinding;
import org.javity.avatar.AvatarDefinition;
import org.javity.avatar.AvatarMeshPreparator;
import org.javity.avatar.AvatarPart;
import org.javity.avatar.AvatarPreset;
import org.javity.avatar.AvatarView;
import org.javity.avatar.PartKey;
import org.javity.avatar.PreparedAvatarGeometry;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.core.Transform3D;
import org.javity.render.Camera3D;
import org.javity.render.GdxRenderer;
import org.javity.render.ModelRenderer;

/** Native framebuffer, depth, and canonical-resource proof for Task 4 slice B. */
final class AvatarGpuSmokeProbe extends ApplicationAdapter {
  private static final int SIZE = 256;
  private GeneratedAvatarModel fixture;
  private Model probeModel;
  private Game game;
  private GdxRenderer renderer;
  private Camera3D camera;
  private FrameBuffer framebuffer;
  private PreparedAvatarGeometry prepared;
  private AvatarBinding binding;
  private AvatarBinding secondBinding;
  private AvatarView view;
  private ModelRenderer avatarRenderer;
  private ModelRenderer secondRenderer;
  private ModelAnimator animator;
  private ModelPlayback playback;
  private final AtomicInteger markerCalls = new AtomicInteger();
  private final List<String> sampleReports = new ArrayList<>();
  private int sampleIndex;
  private GameObject avatarObject;
  private int[] generatedBuffers;
  private List<Integer> generatedVaos;
  private final String requestedGlProfile;
  private int javaFailureVaoCount;
  private int glErrorFailureVaoCount;
  private int restoreFailureVaoCount;
  private boolean generatedVaosDeleted;
  private Throwable failure;

  AvatarGpuSmokeProbe(String requestedGlProfile) {
    this.requestedGlProfile = requestedGlProfile;
  }

  @Override
  public void create() {
    try {
      runProbe();
      System.out.println(
          "avatarGpuSmoke: PASS (requested="
              + requestedGlProfile
              + ", actual="
              + actualGlProfile()
              + ", VAOs="
              + (generatedVaos == null ? 0 : generatedVaos.size())
              + ", authored skin parity, region color/depth, leases, source resources)");
    } catch (Throwable problem) {
      failure = problem;
      problem.printStackTrace();
    } finally {
      Gdx.app.exit();
    }
  }

  private void runProbe() {
    require(
        (Gdx.gl30 == null ? "GL20" : "GL30").equals(requestedGlProfile),
        "Requested " + requestedGlProfile + " but backend initialized " + actualGlProfile());
    fixture = GeneratedAvatarModel.create();
    probeModel = createDepthProbeModel();
    renderer = new GdxRenderer();
    renderer.resize(SIZE, SIZE);
    framebuffer = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, true);
    game = new Game();
    GameObject cameraObject = game.createObject("avatar smoke camera");
    camera = cameraObject.addComponent(new Camera3D(50));
    camera.camera.position.set(0, 0, 3.2f);
    camera.camera.lookAt(0, 0, 0);
    camera.camera.near = .1f;
    camera.camera.far = 20;

    avatarObject = game.createObject("local authored avatar");
    avatarObject.addComponent(new Transform3D());
    avatarRenderer = avatarObject.addComponent(new ModelRenderer(fixture.model));
    animator = avatarObject.addComponent(new ModelAnimator());
    GameObject secondAvatarObject = game.createObject("second shared avatar");
    secondAvatarObject.addComponent(new Transform3D()).position.set(1.35f, 0, 0);
    secondRenderer = secondAvatarObject.addComponent(new ModelRenderer(fixture.model));
    GameObject probeObject = game.createObject("behind hidden body depth probe");
    probeObject.addComponent(new Transform3D()).position.z = -.2f;
    probeObject.addComponent(new ModelRenderer(probeModel));

    Map<String, int[]> authored = Map.of("body", new int[] {0, 1}, "arms", new int[] {2, 3});
    verifyMalformedBindMapDoesNotAllocate(authored);
    List<Integer> failedBuffers = new ArrayList<>();
    List<Integer> failedVaos = new ArrayList<>();
    try {
      prepareWithTracking(authored, 1, failedBuffers, failedVaos);
      throw new AssertionError("Injected sidecar upload failure was not observed");
    } catch (IllegalStateException expected) {
      require(
          expected.getMessage().contains("injected avatar buffer upload failure"),
          "Unexpected upload failure: " + expected);
    }
    assertVaoTracked(failedVaos, "Java upload exception");
    javaFailureVaoCount = failedVaos.size();
    require(!failedBuffers.isEmpty(), "Injected upload did not allocate any partial GPU buffers");
    for (int handle : failedBuffers)
      require(!Gdx.gl.glIsBuffer(handle), "Failed upload leaked partial buffer " + handle);
    assertVaos(failedVaos, false, "Java upload exception");
    List<Integer> glErrorBuffers = new ArrayList<>();
    List<Integer> glErrorVaos = new ArrayList<>();
    try {
      prepareWithTracking(authored, 2, glErrorBuffers, glErrorVaos);
      throw new AssertionError("Injected driver GL_OUT_OF_MEMORY was not observed");
    } catch (IllegalStateException expected) {
      require(
          expected.getMessage().contains("GL error: " + GL20.GL_OUT_OF_MEMORY),
          "Unexpected driver upload error: " + expected);
    }
    assertVaoTracked(glErrorVaos, "GL_OUT_OF_MEMORY upload failure");
    glErrorFailureVaoCount = glErrorVaos.size();
    require(!glErrorBuffers.isEmpty(), "Driver upload failure allocated no buffers");
    for (int handle : glErrorBuffers)
      require(!Gdx.gl.glIsBuffer(handle), "Driver upload failure leaked buffer " + handle);
    assertVaos(glErrorVaos, false, "GL_OUT_OF_MEMORY upload failure");
    require(
        Gdx.gl.glGetError() == GL20.GL_NO_ERROR,
        "Injected driver upload error remained pending after cleanup");
    List<Integer> restoreBuffers = new ArrayList<>();
    List<Integer> restoreVaos = new ArrayList<>();
    try {
      prepareWithTracking(authored, 3, restoreBuffers, restoreVaos);
      throw new AssertionError("Injected one-shot GL restoration failure was not observed");
    } catch (IllegalStateException expected) {
      require(
          expected.getMessage().contains("injected avatar binding restore failure"),
          "Unexpected restoration failure: " + expected);
    }
    assertVaoTracked(restoreVaos, "state restoration failure");
    restoreFailureVaoCount = restoreVaos.size();
    require(!restoreBuffers.isEmpty(), "Restore failure allocated no candidate buffers");
    for (int handle : restoreBuffers)
      require(!Gdx.gl.glIsBuffer(handle), "Restore failure leaked buffer " + handle);
    assertVaos(restoreVaos, false, "state restoration failure");
    List<Integer> successfulBuffers = new ArrayList<>();
    generatedVaos = new ArrayList<>();
    prepared = prepareWithTracking(authored, 0, successfulBuffers, generatedVaos);
    assertVaoTracked(generatedVaos, "successful sidecar upload");
    generatedBuffers = successfulBuffers.stream().mapToInt(Integer::intValue).toArray();
    AvatarDefinition definition =
        AvatarDefinition.builder()
            .region(AvatarPart.TORSO, "body")
            .region(AvatarPart.LEFT_ARM, "arms")
            .build();
    binding = definition.bind(avatarRenderer, prepared);
    secondBinding = definition.bind(secondRenderer, prepared);
    view = binding.view();
    game.start();
    game.step(0);
    playback =
        animator.play(
            AnimationTransition.clip("arm-sweep")
                .loop()
                .markers(new AnimationMarker("midpoint", .5)));
    playback.onMarker("midpoint", markerCalls::incrementAndGet).seek(.1);

    require(
        Map.of("body", 2, "arms", 2).equals(prepared.report().triangleCounts()),
        "Unexpected region report");
    assertSourcePartUnmodified(avatarRenderer);
    compareSourceAndPartitionAtCurrentPose();
    playback.seek(.4);
    compareSourceAndPartitionAtCurrentPose();
    require(markerCalls.get() == 0, "Silent animation seeks fired a marker");
    playback.seek(.2);
    game.step(.1f);
    game.step(.1f);
    require(
        markerCalls.get() == 1,
        "Game.step did not advance the clip through its marker (time="
            + playback.time()
            + ", markers="
            + markerCalls.get()
            + ")");

    // A hidden body must submit neither color nor depth: the same background probe pixel is
    // visible with custom-empty and arm-only providers, while arm pixels differ from empty.
    view.parts(Set.of());
    Capture empty = capture(view);
    view.preset(AvatarPreset.ARMS_ONLY);
    Capture armsOnly = capture(view);
    saveArtifact("arms-only.png", armsOnly.color);
    int bodyPixel = pixel(armsOnly.color, SIZE / 2, 88);
    require(greenDominates(bodyPixel), "Hidden body failed to reveal the green depth probe");
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error after hidden-body color readback");
    require(
        Math.abs(empty.depthAt(SIZE / 2, 88) - armsOnly.depthAt(SIZE / 2, 88)) < .00001f,
        "Hidden body wrote depth over the farther probe");
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error after hidden-body depth readback");
    require(
        pixel(empty.color, SIZE / 2, 175) != pixel(armsOnly.color, SIZE / 2, 175),
        "Selected authored arm region did not change the arm color pixels");
    empty.color.dispose();
    armsOnly.color.dispose();
    verifyCapacityFailureRecovery();

    Throwable[] wrongThread = new Throwable[1];
    Thread wrongCloser =
        new Thread(
            () -> {
              try {
                binding.close();
              } catch (Throwable rejected) {
                wrongThread[0] = rejected;
              }
            },
            "avatar-wrong-thread-close-check");
    wrongCloser.start();
    try {
      wrongCloser.join();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while checking GL-thread ownership", interrupted);
    }
    require(
        wrongThread[0] instanceof IllegalStateException,
        "Wrong-thread binding close was not rejected");
    Capture stillLive = capture(view);
    require(
        fixtureColorDominates(pixel(stillLive.color, SIZE / 2, 175)),
        "Wrong-thread close invalidated the selected local arm fixture pixel");
    stillLive.color.dispose();

    require(
        generatedBuffers.length >= 2,
        "Prepared geometry did not allocate vertex and index buffers");
    prepared.close();
    assertBuffers(generatedBuffers, true, "owner close with two live bindings");
    assertVaos(generatedVaos, true, "owner close with two live bindings");
    AvatarView secondView = secondBinding.view();
    secondBinding.close();
    assertBuffers(generatedBuffers, true, "first binding close");
    assertVaos(generatedVaos, true, "first binding close");
    avatarRenderer.setEnabled(false);
    Capture closedView = capture(secondView);
    require(
        greenDominates(pixel(closedView.color, SIZE / 2, 88)),
        "Closed view hid later ordinary geometry");
    closedView.color.dispose();
    avatarRenderer.setEnabled(true);

    binding.close();
    assertBuffers(generatedBuffers, false, "last binding close");
    assertVaos(generatedVaos, false, "last binding close");
    generatedVaosDeleted = true;
    require(
        Gdx.gl.glIsTexture(fixture.texture.getTextureObjectHandle()),
        "Borrowed source texture was disposed");
    secondRenderer.setEnabled(false);
    probeObjectEnabled(false);
    Capture sourceStillUsable = capture(null);
    require(
        countFixturePixels(sourceStillUsable.color) > 500,
        "Borrowed source avatar did not render after final sidecar lease disposal");
    require(
        Gdx.gl.glGetError() == GL20.GL_NO_ERROR,
        "GL error rendering borrowed source after sidecar disposal");
    sourceStillUsable.color.dispose();
    secondRenderer.setEnabled(true);
    probeObjectEnabled(true);

    avatarRenderer.setEnabled(false);
    Capture disabled = capture(view);
    require(countPixels(disabled.color) > 500, "Disabled local view hid a later ordinary model");
    disabled.color.dispose();
    avatarRenderer.setEnabled(true);
    avatarObject.removeComponent(avatarRenderer);
    game.step(0);
    Capture removed = capture(view);
    require(countPixels(removed.color) > 500, "Removed local view hid a later ordinary model");
    removed.color.dispose();

    FileHandle artifactDir = artifactDirectory();
    artifactDir.mkdirs();
    StringBuilder report = new StringBuilder();
    report.append(
        "{\n  \"fixture\": \"procedural two-quad skinned rig; not full voxel avatar\",\n");
    report
        .append("  \"requestedGlProfile\": \"")
        .append(requestedGlProfile)
        .append("\",\n  \"actualGlProfile\": \"")
        .append(actualGlProfile())
        .append("\",\n  \"actualGlVersion\": \"")
        .append(jsonEscape(Gdx.graphics.getGLVersion().getVersionString()))
        .append("\",\n  \"gl30Available\": ")
        .append(Gdx.gl30 != null)
        .append(",\n  \"successfulCandidateVaoCount\": ")
        .append(generatedVaos.size())
        .append(",\n  \"javaUploadFailureCandidateVaoCount\": ")
        .append(javaFailureVaoCount)
        .append(",\n  \"glErrorFailureCandidateVaoCount\": ")
        .append(glErrorFailureVaoCount)
        .append(",\n  \"restoreFailureCandidateVaoCount\": ")
        .append(restoreFailureVaoCount)
        .append(",\n  \"successfulVaosDeletedAfterFinalLease\": ")
        .append(generatedVaosDeleted)
        .append(",\n");
    report.append("  \"samples\": [\n");
    for (int i = 0; i < sampleReports.size(); i++)
      report
          .append("    ")
          .append(sampleReports.get(i))
          .append(i + 1 < sampleReports.size() ? ",\n" : "\n");
    report.append("  ],\n  \"armsOnlyImage\": \"arms-only.png\"\n}\n");
    artifactDir.child("verification.json").writeString(report.toString(), false, "UTF-8");

    framebuffer.dispose();
    game.dispose();
    renderer.dispose();
    probeModel.dispose();
    fixture.close();
  }

  private void compareSourceAndPartitionAtCurrentPose() {
    double sampledTime = playback.time();
    int sampledMarkers = markerCalls.get();
    view.preset(AvatarPreset.FULL_BODY);
    Capture source = capture(null);
    Capture partitioned = capture(view);
    Capture secondInstance = capture(secondBinding.view());
    int differences = 0;
    for (int y = 0; y < SIZE; y++)
      for (int x = 0; x < SIZE; x++)
        if (source.color.getPixel(x, y) != partitioned.color.getPixel(x, y)) differences++;
    require(
        differences == 0,
        "Full partition differs from source at current pose: " + differences + " pixels");
    sampleIndex++;
    String sourceName = "sample-" + sampleIndex + "-source.png";
    String partitionName = "sample-" + sampleIndex + "-partition.png";
    saveArtifact(sourceName, source.color);
    saveArtifact(partitionName, partitioned.color);
    sampleReports.add(
        "{\"timeSeconds\": "
            + sampledTime
            + ", \"pixelDifferences\": "
            + differences
            + ", \"sourceImage\": \""
            + sourceName
            + "\", \"partitionImage\": \""
            + partitionName
            + "\"}");
    require(playback.time() == sampledTime, "Rendering advanced the sampled animation clock");
    require(markerCalls.get() == sampledMarkers, "Rendering fired an animation marker");
    require(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "GL error after source/partition readbacks");
    source.color.dispose();
    partitioned.color.dispose();
    secondInstance.color.dispose();
  }

  private Capture capture(AvatarView override) {
    framebuffer.begin();
    Gdx.gl.glViewport(0, 0, SIZE, SIZE);
    Gdx.gl.glClearColor(.05f, .08f, .12f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    renderer.renderWorld(game, camera, override);
    Gdx.gl.glFinish();
    Pixmap color = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, SIZE, SIZE);
    ByteBuffer depth =
        BufferUtils.newByteBuffer(SIZE * SIZE * Float.BYTES).order(ByteOrder.nativeOrder());
    Gdx.gl.glReadPixels(0, 0, SIZE, SIZE, GL20.GL_DEPTH_COMPONENT, GL20.GL_FLOAT, depth);
    framebuffer.end();
    return new Capture(color, depth);
  }

  private int countFixturePixels(Pixmap pixmap) {
    int count = 0;
    for (int y = 0; y < pixmap.getHeight(); y++)
      for (int x = 0; x < pixmap.getWidth(); x++) {
        int rgba = pixmap.getPixel(x, y);
        int red = rgba >>> 24 & 255;
        int green = rgba >>> 16 & 255;
        if (red > green * 1.15f) count++;
      }
    return count;
  }

  private void probeObjectEnabled(boolean enabled) {
    for (GameObject object : game.getObjects())
      if (object.name.equals("behind hidden body depth probe")) {
        ModelRenderer model = object.getComponent(ModelRenderer.class);
        if (model != null) model.setEnabled(enabled);
      }
  }

  private void saveArtifact(String name, Pixmap pixmap) {
    FileHandle directory = artifactDirectory();
    directory.mkdirs();
    PixmapIO.writePNG(directory.child(name), pixmap);
  }

  private FileHandle artifactDirectory() {
    return Gdx.files.local("docs/artifacts/avatar/" + requestedGlProfile.toLowerCase());
  }

  private static String actualGlProfile() {
    return Gdx.gl30 == null ? "GL20" : "GL30";
  }

  private static String jsonEscape(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private void assertVaoTracked(List<Integer> handles, String phase) {
    if (Gdx.gl30 != null)
      require(!handles.isEmpty(), "GL30 sidecar did not generate a candidate VAO during " + phase);
  }

  private void assertVaos(List<Integer> handles, boolean shouldExist, String phase) {
    if (Gdx.gl30 == null) {
      require(handles.isEmpty(), "GL20 path unexpectedly tracked VAOs at " + phase);
      return;
    }
    for (int handle : handles)
      require(
          Gdx.gl30.glIsVertexArray(handle) == shouldExist,
          "Generated VAO " + handle + " lifetime wrong at " + phase);
  }

  private void verifyMalformedBindMapDoesNotAllocate(Map<String, int[]> authored) {
    NodePart sourcePart = fixture.model.nodes.first().parts.first();
    Matrix4 original = sourcePart.invBoneBindTransforms.getValueAt(1);
    List<Integer> candidateBuffers = new ArrayList<>();
    sourcePart.invBoneBindTransforms.setValue(1, null);
    try {
      prepareWithTracking(authored, 0, candidateBuffers, new ArrayList<>());
      throw new AssertionError("Null inverse bind matrix passed source preflight");
    } catch (IllegalArgumentException expected) {
      require(
          expected.getMessage().contains("inverse bind transform"),
          "Unexpected malformed bind-map diagnostic: " + expected);
    } finally {
      sourcePart.invBoneBindTransforms.setValue(1, original);
    }
    require(
        candidateBuffers.isEmpty(),
        "Malformed CPU preflight unexpectedly allocated candidate buffers");
  }

  private PreparedAvatarGeometry prepareWithTracking(
      Map<String, int[]> authored,
      int failureMode,
      List<Integer> handles,
      List<Integer> vertexArrays) {
    int[] stateBefore = captureGlBindings();
    GL20 previousGl = Gdx.gl;
    GL20 previousGl20 = Gdx.gl20;
    GL30 previousGl30 = Gdx.gl30;
    Class<?> glType = previousGl30 == null ? GL20.class : GL30.class;
    Object tracked =
        Proxy.newProxyInstance(
            glType.getClassLoader(),
            new Class<?>[] {glType},
            (proxy, method, arguments) -> {
              Object delegate = previousGl30 == null ? previousGl20 : previousGl30;
              int outputPosition =
                  method.getName().equals("glGenVertexArrays")
                          && arguments[1] instanceof IntBuffer output
                      ? output.position()
                      : -1;
              try {
                Object result = method.invoke(delegate, arguments);
                if (method.getName().equals("glGenBuffer")) handles.add((int) result);
                if (method.getName().equals("glGenVertexArrays"))
                  recordGeneratedVertexArrays(arguments, outputPosition, vertexArrays);
                return result;
              } catch (InvocationTargetException failure) {
                throw failure.getCause();
              }
            });
    if (failureMode == 1) {
      // A second proxy layer fails only after a generated buffer is bound for its first data
      // upload.
      Object delegate = tracked;
      tracked =
          Proxy.newProxyInstance(
              glType.getClassLoader(),
              new Class<?>[] {glType},
              (proxy, method, arguments) -> {
                if (method.getName().equals("glBufferData"))
                  throw new IllegalStateException("injected avatar buffer upload failure");
                try {
                  return method.invoke(delegate, arguments);
                } catch (InvocationTargetException failure) {
                  throw failure.getCause();
                }
              });
    } else if (failureMode == 2) {
      Object delegate = tracked;
      boolean[] uploaded = {false};
      tracked =
          Proxy.newProxyInstance(
              glType.getClassLoader(),
              new Class<?>[] {glType},
              (proxy, method, arguments) -> {
                if (method.getName().equals("glGetError") && uploaded[0]) {
                  uploaded[0] = false;
                  return GL20.GL_OUT_OF_MEMORY;
                }
                Object result;
                try {
                  result = method.invoke(delegate, arguments);
                } catch (InvocationTargetException failure) {
                  throw failure.getCause();
                }
                if (method.getName().equals("glBufferData")) uploaded[0] = true;
                return result;
              });
    } else if (failureMode == 3) {
      Object delegate = tracked;
      boolean[] bufferUploaded = {false};
      boolean[] uploadChecked = {false};
      boolean[] restoreFaultInjected = {false};
      tracked =
          Proxy.newProxyInstance(
              glType.getClassLoader(),
              new Class<?>[] {glType},
              (proxy, method, arguments) -> {
                if (method.getName().equals("glBindBuffer")
                    && uploadChecked[0]
                    && !restoreFaultInjected[0]
                    && (int) arguments[0] == GL20.GL_ARRAY_BUFFER
                    && (int) arguments[1] == stateBefore[0]) {
                  Object result = method.invoke(delegate, arguments);
                  restoreFaultInjected[0] = true;
                  throw new IllegalStateException("injected avatar binding restore failure");
                }
                Object result;
                try {
                  result = method.invoke(delegate, arguments);
                } catch (InvocationTargetException failure) {
                  throw failure.getCause();
                }
                if (method.getName().equals("glBufferData")) bufferUploaded[0] = true;
                if (method.getName().equals("glGetError") && bufferUploaded[0])
                  uploadChecked[0] = true;
                return result;
              });
    }
    Gdx.gl = (GL20) tracked;
    Gdx.gl20 = (GL20) tracked;
    if (previousGl30 != null) Gdx.gl30 = (GL30) tracked;
    try {
      return AvatarMeshPreparator.prepare(fixture.model, new PartKey("root", 0), authored);
    } finally {
      Gdx.gl = previousGl;
      Gdx.gl20 = previousGl20;
      Gdx.gl30 = previousGl30;
      require(
          Arrays.equals(stateBefore, captureGlBindings()),
          "Avatar geometry preparation did not restore GL buffer/VAO bindings");
    }
  }

  private int[] captureGlBindings() {
    IntBuffer value = BufferUtils.newIntBuffer(1);
    Gdx.gl.glGetIntegerv(GL20.GL_ARRAY_BUFFER_BINDING, value);
    int array = value.get(0);
    value.clear();
    Gdx.gl.glGetIntegerv(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING, value);
    int element = value.get(0);
    int vao = 0;
    if (Gdx.gl30 != null) {
      value.clear();
      Gdx.gl30.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, value);
      vao = value.get(0);
    }
    return new int[] {array, element, vao};
  }

  private static void recordGeneratedVertexArrays(
      Object[] arguments, int outputPosition, List<Integer> handles) {
    int count = (int) arguments[0];
    if (arguments[1] instanceof IntBuffer output) {
      for (int i = 0; i < count; i++) handles.add(output.get(outputPosition + i));
    } else if (arguments[1] instanceof int[] output) {
      int offset = (int) arguments[2];
      for (int i = 0; i < count; i++) handles.add(output[offset + i]);
    } else {
      throw new IllegalStateException("Unexpected glGenVertexArrays output type");
    }
  }

  private void assertSourcePartUnmodified(ModelRenderer modelRenderer) {
    NodePart source = modelRenderer.instance.nodes.first().parts.first();
    require(source.meshPart.mesh == fixture.mesh, "Canonical source mesh identity changed");
    require(
        source.bones != null && source.bones.length == 2,
        "ModelInstance did not initialize source bone palette");
  }

  private void assertBuffers(int[] handles, boolean shouldExist, String phase) {
    for (int handle : handles)
      require(
          Gdx.gl.glIsBuffer(handle) == shouldExist,
          "Generated buffer " + handle + " lifetime wrong at " + phase);
  }

  private Model createDepthProbeModel() {
    Material green = new Material(ColorAttribute.createDiffuse(.05f, .95f, .2f, 1));
    ModelBuilder builder = new ModelBuilder();
    builder.begin();
    MeshPartBuilder part =
        builder.part(
            "behind-body",
            GL20.GL_TRIANGLES,
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position
                | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal,
            green);
    part.rect(-.6f, -.9f, -.05f, .6f, -.9f, -.05f, .6f, -.05f, -.05f, -.6f, -.05f, -.05f, 0, 0, 1);
    return builder.end();
  }

  private void verifyCapacityFailureRecovery() {
    GdxRenderer limited = new GdxRenderer(1, 4);
    try {
      boolean rejected = false;
      try {
        limited.renderWorld(game, camera, view);
      } catch (IllegalArgumentException expected) {
        rejected = expected.getMessage().contains("requires 2 bones");
      }
      require(rejected, "Configured shader capacity did not reject the two-bone avatar");
      boolean viewmodelRejected = false;
      try {
        limited.renderViewmodel(camera.camera, view);
      } catch (IllegalArgumentException expected) {
        viewmodelRejected = expected.getMessage().contains("requires 2 bones");
      }
      require(
          viewmodelRejected,
          "Viewmodel pass did not reject an avatar beyond its configured shader capacity");
      avatarRenderer.setEnabled(false);
      secondRenderer.setEnabled(false);
      framebuffer.begin();
      Gdx.gl.glClearColor(.05f, .08f, .12f, 1);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      limited.renderWorld(game, camera, null);
      Gdx.gl.glFinish();
      Pixmap recovered = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, SIZE, SIZE);
      framebuffer.end();
      require(
          countPixels(recovered) > 100,
          "Renderer did not recover on the next ordinary-model frame");
      recovered.dispose();
    } finally {
      avatarRenderer.setEnabled(true);
      secondRenderer.setEnabled(true);
      limited.dispose();
    }
  }

  private static int pixel(Pixmap pixmap, int x, int y) {
    return pixmap.getPixel(x, y);
  }

  private static boolean greenDominates(int rgba) {
    int r = rgba >>> 24 & 255;
    int g = rgba >>> 16 & 255;
    return g > r * 1.5f;
  }

  private static boolean fixtureColorDominates(int rgba) {
    int red = rgba >>> 24 & 255;
    int green = rgba >>> 16 & 255;
    return red > green * 1.15f;
  }

  private static int countPixels(Pixmap pixmap) {
    int count = 0;
    int background = (13 << 24) | (20 << 16) | (31 << 8) | 255;
    for (int y = 0; y < pixmap.getHeight(); y++)
      for (int x = 0; x < pixmap.getWidth(); x++) if (pixmap.getPixel(x, y) != background) count++;
    return count;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @Override
  public void dispose() {
    if (failure != null) throw new RuntimeException("avatarGpuSmoke failed", failure);
  }

  private record Capture(Pixmap color, ByteBuffer depth) {
    float depthAt(int x, int y) {
      return depth.getFloat((y * SIZE + x) * Float.BYTES);
    }
  }
}
