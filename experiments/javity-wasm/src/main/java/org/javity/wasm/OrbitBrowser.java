package org.javity.wasm;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/** Optional browser-only asynchronous capability bridge. No account token enters WASM. */
public final class OrbitBrowser {
  private OrbitBrowser() {}

  @JSFunctor
  public interface Reply extends JSObject {
    void accept(String json);
  }

  public interface Restore extends JSObject {
    @JSProperty
    float getX();

    @JSProperty
    float getZ();
  }

  @JSBody(script = "return !!globalThis.JavityOrbit && globalThis.JavityOrbit.enabled;")
  public static native boolean available();

  @JSBody(
      params = {"method", "params", "success", "failure"},
      script =
          """
          Promise.resolve().then(function(){
            if(!globalThis.JavityOrbit)throw Object.assign(Error('ORBIT adapter unavailable'),{code:'UNAVAILABLE'});
            return globalThis.JavityOrbit.request(method,JSON.parse(params));
          }).then(function(value){success(JSON.stringify(value));},function(error){failure(JSON.stringify({code:error.code||'REQUEST_FAILED',message:error.message}));});
          """)
  public static native void request(String method, String params, Reply success, Reply failure);

  @JSBody(script = "return globalThis.JavityOrbit ? globalThis.JavityOrbit.takeRestore() : null;")
  public static native Restore takeRestore();

  @JSBody(
      script = "return globalThis.JavityOrbit ? globalThis.JavityOrbit.takeSaveRevision() : -1;")
  public static native double takeSaveRevision();

  @JSBody(
      params = {"revision"},
      script = "if(globalThis.JavityOrbit)globalThis.JavityOrbit.syncSaveRevision(revision);")
  public static native void syncSaveRevision(double revision);

  @JSBody(
      params = {"json"},
      script = "if(globalThis.JavityOrbit)globalThis.JavityOrbit.reportJavaPlayer(json);")
  public static native void reportJavaPlayer(String json);

  @JSBody(
      params = {"id", "nickname"},
      script =
          "if(globalThis.JavityOrbit)globalThis.JavityOrbit.reportJavaPlayer(JSON.stringify({id:id,nickname:nickname}));")
  public static native void reportJavaProfile(String id, String nickname);

  @JSBody(script = "if(globalThis.JavityOrbit)globalThis.JavityOrbit.dispose();")
  public static native void dispose();
}
