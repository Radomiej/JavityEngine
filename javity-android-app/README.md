# Javity Android

Właściwa aplikacja demonstracyjna nowego rdzenia: świat voxelowy, FPS/AABB, model 3D, Shapes GLES3 oraz deklaratywny HUD React → Java → Signal. Nie dołącza modułów legacy, LWJGL, Graal, desktopowego MapLibre ani Bullet.

Wymaga JDK 25, Android SDK z platformą 36 i build-tools 36.0.0. Urządzenie: Android API 35+, GLES3; APK zawiera ARM64 oraz x86_64. Zwykły desktop build nadal nie potrzebuje SDK.

```sh
./gradlew -PjavityAndroid=true :javity-android-app:assembleDebug :javity-android-app:testDebugUnitTest
./gradlew -PjavityAndroid=true :javity-android-app:assembleRelease
python3 tools/audit-android-apk.py javity-android-app/build/outputs/apk/debug/javity-android-app-debug.apk
python3 tools/audit-android-apk.py javity-android-app/build/outputs/apk/release/javity-android-app-release-unsigned.apk --output build/android-release-audit.json
```

Release jest optymalizowany przez R8 i pozostaje niepodpisany. Nie zapisuj kluczy podpisujących w repozytorium. Biblioteki JNI generuje zadanie podłączone przez Android Variant API; nie kopiuj desktopowych natives do APK.

Sterowanie: przyciski W/A/S/D, JUMP, przesunięcie palca po prawej stronie dla rozglądania i PAUSE / RESUME. HUD ma pierwszeństwo przed wejściem gry; ruch i rozglądanie mogą korzystać z różnych palców. Utrata fokusu kasuje trzymane wejścia. Rozmiary UI są logiczne i uwzględniają gęstość ekranu.

Testy instrumentacyjne wymagają uruchomionego urządzenia/emulatora:

```sh
./gradlew -PjavityAndroid=true :javity-android-app:connectedDebugAndroidTest
```

Osobny test EGL wyłącza zachowanie kontekstu i wznawia ten sam GLSurfaceView, zachowując właścicieli reprezentatywnych zasobów. Zwykła przebudowa zasobów dema jest osobnym testem. Wyniki i ograniczenia rzeczywistej walidacji: [status Androida](../docs/android-status.md). Budowa i statyczny audyt APK nie potwierdzają wykonania na ARM64 ani wydajności urządzenia.

Licencje i notices są pakowane w `assets/android/third-party/`; audyt APK weryfikuje hashe plików z manifestu. Font i host React zachowują również notices w zasobach odpowiednich modułów.

Bieżące dowody: pięć przypadków aplikacji przeszło oddzielnie na emulatorze API35 x86_64 przy density320 (dotyk/przebudowa, obrót, dwa warianty Activity recreate, rzeczywista utrata EGL). Łączny runner na TCG bez KVM miał timeouty ANR; nie jest oznaczony jako zaliczony. [Release R8](../docs/artifacts/android/app-release-runtime-verification.json) ma osobny ręczny test startu, kliknięcia i powrotu z tła. Fizyczny ARM64 nadal wymaga testu urządzenia.

## Full instrumentation diagnostics

The manually triggered `Android full instrumentation` workflow runs the complete five-case app suite as one unfiltered `connectedDebugAndroidTest` invocation:

```sh
gh workflow run android-runtime.yml --ref master
```

It builds once on JDK25/SDK36, boots an API35 x86_64 emulator with KVM and pins its test display to640x1120 at density320. The compact-layout test requires portrait logical height>=360 and landscape<360; arbitrary Pixel2 defaults do not satisfy both. `tools/android-full-suite.sh` is the diagnostic entry point for this disposable emulator: it changes that emulator’s display settings, retains Gradle’s exit code and prints bounded failure details in addition to uploading reports/logcat/ANR artifacts. For the current outcome and limits see [Android follow-up](../docs/Bugs/2026-10-17-android-followup.md). A workflow file or APK build alone does not establish a passing runtime suite.
