# Android diagnostic probe

This is a developer probe, **not** the production Android launcher or a port of the desktop demo. It packages the existing Java 25 `javity-core` and `javity-voxel` jars without changing their source. Modes: `basic` (components, 2D sprite, 3D box), `shapes` (the existing SDF shader), `voxels` (chunk rendering and CPU FPS ground/jump/landing).

Prerequisites: ordinary JDK25, Android SDK platform 35/build-tools 36.0.0/platform-tools, and official R8 **9.5.26**. The D8 8.10.9 bundled with build-tools 36.0.0 rejects Java 25 class version 69. The diagnostic deliberately requires API35; it does not establish compatibility with older Android versions.

Build the normal engine artifacts first: `./gradlew build :javity-desktop:installDist`. Resolve AndroidX dependencies separately with `./gradlew -p tools/android-probe/resolve copyDeps`. Download the official libGDX 1.14.2 `gdx-backend-android` AAR and `gdx-platform` `natives-x86_64` jar from Maven Central. R8 is published in Google's Maven repository as `com.android.tools:r8:9.5.26`. Do not commit downloaded SDK files, debug keystores or APKs.

```sh
python3 tools/android-probe/build-probe.py \
  --sdk /path/to/android-sdk \
  --r8 /path/to/r8-9.5.26.jar \
  --java-home /path/to/jdk25 \
  --deps tools/android-probe/resolve/deps \
  --backend /path/to/gdx-backend-android-1.14.2.aar \
  --natives /path/to/gdx-platform-1.14.2-natives-x86_64.jar \
  --output /tmp/javity-android-apk
adb -s emulator-5554 shell svc power stayon true
adb -s emulator-5554 shell settings put system screen_off_timeout 2147483647
adb -s emulator-5554 shell input keyevent 224
adb -s emulator-5554 shell settings put secure immersive_mode_confirmations confirmed
adb -s emulator-5554 install --no-incremental -r /tmp/javity-android-apk/javity-android-probe.apk
adb -s emulator-5554 shell am start -n org.javity.androidprobe/.ProbeActivity --es mode basic
adb -s emulator-5554 logcat -d -s JavityAndroidProbe
adb -s emulator-5554 exec-out screencap -p > /tmp/android-basic.png
```

Force-stop before switching modes. For lifecycle checks use Home and resume the activity. A successful APK build is **not** a GPU/runtime result; read `GPU_PASS`, the screenshot and logcat. The probe disables audio and does not include Yoga, GraalJS, MapLibre Native FFI, networking, Box2D or skeletal animation assets. Do not use it to claim those modules are Android-ready.

On hosts without `/dev/kvm`, the official emulator 37.3.4 requires its explicit `ANDROID_I_WANT_MY_TCG=yes` software-emulation opt-in alongside `-accel off`. Boot can take several minutes. Its default API35 userdata partition needs at least 6 GB on the AVD filesystem. Keep AVD/SDK outside the checkout and distinguish emulator boot failure from app failure.

Before interpreting a blank/timeout result, check `adb shell dumpsys power`: `mWakefulness=Awake` and `mStayOn=true`. An activity launched while the emulator is asleep can stay paused without reaching `ApplicationListener.create`. System UI/phone ANRs were observed under TCG; they are environment failures, and screenshots with modal system dialogs do not prove visible game output.
