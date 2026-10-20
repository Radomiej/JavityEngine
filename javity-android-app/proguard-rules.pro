# QuickJS-KT 1.0.15 declares these consumer rules in its AAR proguard.txt; keep them
# visible here as the app's shrinker contract if its dependency metadata is repackaged.
-keep,allowoptimization class com.dokar.quickjs.QuickJs { *; }
-keep,allowoptimization class com.dokar.quickjs.QuickJsException { *; }
-keep,allowoptimization class com.dokar.quickjs.binding.JsProperty { *; }
-keep,allowoptimization class com.dokar.quickjs.binding.JsFunction { *; }
-keep,allowoptimization class com.dokar.quickjs.binding.JsObject { *; }
-keep,allowoptimization class kotlin.UByteArray

# Application callbacks/providers used by JNI-facing adapter and provider discovery.
-keep class org.javity.script.quickjs.** { *; }
-keep class org.javity.script.ScriptUiEngineFactory { *; }
-keep class org.javity.ui.yoga.YogaBackendProvider { *; }
-keep class org.javity.ui.yoga.android.AndroidYogaBackendProvider { *; }
-keep class com.facebook.yoga.** { *; }
-keep class com.facebook.soloader.** { *; }

# Pinned libGDX 1.14.2 backend context-recovery implementation members.
-keepclassmembers class com.badlogic.gdx.graphics.Mesh {
    static java.util.Map meshes;
    protected com.badlogic.gdx.graphics.glutils.VertexData vertices;
}
-keepclassmembers class com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO {
    com.badlogic.gdx.utils.IntArray cachedLocations;
    boolean isBound;
}
-keepclassmembers class com.badlogic.gdx.graphics.glutils.ShaderProgram {
    private static com.badlogic.gdx.utils.ObjectMap shaders;
    private com.badlogic.gdx.utils.ObjectIntMap uniforms;
    private com.badlogic.gdx.utils.ObjectIntMap uniformTypes;
    private com.badlogic.gdx.utils.ObjectIntMap uniformSizes;
    private com.badlogic.gdx.utils.ObjectIntMap attributes;
    private com.badlogic.gdx.utils.ObjectIntMap attributeTypes;
    private com.badlogic.gdx.utils.ObjectIntMap attributeSizes;
    private void fetchAttributes();
    private void fetchUniforms();
}

# Engine-owned retained DefaultShader location recovery, pinned to libGDX 1.14.2.
-keepclassmembers class com.badlogic.gdx.Version {
    public static final java.lang.String VERSION;
}
-keepclassmembers class com.badlogic.gdx.graphics.g3d.shaders.BaseShader {
    private int[] locations;
    private com.badlogic.gdx.utils.IntIntMap attributes;
    private com.badlogic.gdx.utils.IntIntMap instancedAttributes;
}
-keepclassmembers class com.badlogic.gdx.graphics.g3d.shaders.DefaultShader {
    private int[] boneWeightsLocations;
}
