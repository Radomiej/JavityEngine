# Android — bieżący status

## Aktualizacja 2026-10-17 — pełny runner CI

**PASS: pełny nieprzefiltrowany zestaw5/5**, zero błędów i pominięć: [run39046532187](https://github.com/Radomiej/JavityEngine/actions/runs/39046532187), source `5d2a206`, API35 x86_64, KVM, SwiftShader, ekran640x1120/density320. Instrumentacja trwała2m32s; była to jedna komenda pełnego zestawu, nie suma pojedynczych prób. Przechodzą dotyk/model/React i przebudowa zasobów, odzyskanie EGL, kompaktowy widok/obrót oraz odtworzenie aktywnej i zapauzowanej Activity.

Runner poprawia nieosiągalny wcześniej kompaktowy viewport, potwierdza systemową podpowiedź immersive mode przed startem oraz czeka na rzeczywisty fokus przed wstrzyknięciem dotyku. Asercje pozostają; limit startu nadal60s. **BUG-ANDROID-001 zamknięty w tym zakresie**, przyczyna historycznego ANR TCG nadal nieudowodniona. Fizyczny ARM64, wydajność oraz Android MapLibre/Bullet pozostają niezweryfikowane. [Historia i zakres dowodu](Bugs/2026-10-17-android-followup.md). Poniższe wyniki z2026-10-16 są historyczne.

Aktualizacja 2026-10-16: przenośne UI i osobne backendy Yoga są zintegrowane. Pełny build desktopu po poprawkach odzyskiwania grafiki: 646 testów w 75 zestawach, bez błędów; natywne UI i porównania HTML przeszły. Android Yoga AAR i APK testów zbudowano rzeczywistym AGP 9.4.1 / Kotlin 2.4.20 przy Java 25 (class major 69), compileSdk 36 i minSdk 35. Na emulatorze API 35 x86_64 przeszły **4 rzeczywiste testy JNI/ART**, obejmujące layout, UiDocument, obsługę błędów oraz jawne zwalnianie node/config. [Wynik i zakres](artifacts/android/yoga-verification.json), [log instrumentacji](artifacts/android/yoga-instrumentation.txt).

Adapter QuickJS i wspólny host React przeszły także **7 testów JNI/ART** na tym emulatorze: akcje/sygnały, timery, reload/close oraz natywny deadline nieskończonego skryptu. [Wynik i zakres](artifacts/android/quickjs-verification.json), [log](artifacts/android/quickjs-instrumentation.txt). Testy webowego hosta: 12/12, trzy bundle odtworzone bajtowo.

Aktualna aplikacja Android z QuickJS, voxelami i GLES przechodzi testy na emulatorze API 35 x86_64. Zwykły desktop build nie wymaga SDK; moduł Android włącza `-PjavityAndroid=true`. AGP dostarcza działający D8 9.4.24 — historyczny zewnętrzny override R8 opisany niżej nie jest częścią bieżącego buildu.

## Aplikacja Android — integracja 2026-10-16

Nowy opt-in moduł `javity-android-app` przechodzi rzeczywisty build AGP: APK debug, APK testów oraz optymalizowany release R8. Testy aplikacji CPU: 13/13, w tym regresje błędów sprzątania. Końcowe audyty APK [debug](artifacts/android/app-debug-apk-audit.json) i [release](artifacts/android/app-release-apk-audit.json) potwierdziły komplet libGDX/FreeType/Yoga/QuickJS dla ARM64 i x86_64, ELF i ZIP zgodne z wyrównaniem 16 KiB oraz 29 plików notices zgodnych z manifestem. Audyt strukturalny nie jest testem wykonania na urządzeniu.

Na emulatorze API 35 x86_64 przeszły testy aplikacji przy density 160 (**58.527 s**) i density 320 (**58.899 s**): kliknięcie React → Java action → Signal → React, sterowanie wielopalcowe w obu kolejnościach, kasowanie wejść po utracie fokusu i przebudowa zasobów. [Log density 160](artifacts/android/app-density160-instrumentation.txt), [log density 320](artifacts/android/app-density320-instrumentation.txt). Zrzut sprawdzony wizualnie pokazuje HUD, teren voxelowy i figury GLES SDF. Pierwsze uruchomienia wykryły i pozwoliły poprawić dwa błędy dema: nieobsługiwany nazwany kolor oraz pomylenie kluczy `UiDocument` z nazwami aktorów.

Test rzeczywistej utraty EGL **przeszedł** (**55.856 s**): po `GLSurfaceView.onPause/onResume` z wyłączonym zachowaniem EGL ten sam helper, SmoothShapeRenderer, UiTheme, tekstura kontrolna i fixture przetrwały bez przebudowy zwykłych zasobów; wzrósł licznik uploadu managed Pixmap, a piksele materiałowego i kreskowanego SDF, zaokrąglonego UI, glifów oraz modelu wróciły. [Log instrumentacji](artifacts/android/app-managed-context-instrumentation.txt). To potwierdza tę ścieżkę utraty kontekstu na emulatorze SwiftShader/API 35 x86_64. Izolowane testy odtworzenia Activity przeszły dla [aktywnej instancji](artifacts/android/app-activity-foreground-instrumentation.txt) (62.812 s) i [już zapauzowanej](artifacts/android/app-activity-paused-instrumentation.txt) (56.209 s), wymagając terminalnego zamknięcia starej sceny i uruchomienia nowej. [Test obrotu ekranu](artifacts/android/app-landscape-instrumentation.txt) przeszedł przy density 320 (53.573 s): ta sama Activity i fixture, kompaktowy HUD, zmierzone odstępy i powrót do pionu. Końcowe powtórzenia na finalnym APK: [dotyk/model/React/przebudowa zasobów](artifacts/android/app-final-touch-instrumentation.txt) PASS (63.666 s) i [utrata EGL](artifacts/android/app-final-egl-instrumentation.txt) PASS (54.787 s), oba przy density 320. Pełna przebudowa ma oddzielny limit 60 s z uwagi na zimną kompilację shaderów w TCG; wcześniejszy limit 10 s został przekroczony. Uruchomienie pełnego zestawu miało systemowy startup ANR, a przebieg dwóch testów Activity timeout fokusu; nie są oznaczone jako PASS. Przypadki zaliczono oddzielnie. [Minifikowany release R8](artifacts/android/app-release-runtime-verification.json) uruchomiono na tym samym emulatorze: wizualnie potwierdzone model/voxele/tekst/React UI/SDF, rzeczywiste kliknięcie zmieniło licznik 0→1, a powrót z tła zachował 1 i odtworzył powierzchnię GL w tym samym procesie. Była to ręczna weryfikacja lokalnie podpisanej kopii testowej, nie instrumentacja release ani publikacja APK. [Widok po wznowieniu](artifacts/android/app-release-resume.png). Fizyczny ARM64 i wydajność pozostają niezweryfikowane; Android MapLibre i Bullet nie należą do pierwszego APK.

## Historyczna diagnostyka — 2026-10-15

Poniższe wyniki opisują starszy probe APK sprzed oddzielenia backendów, a nie aktualny zakres wdrożenia.

Nie można obecnie oznaczyć całej modernizacji jako gotowej na Androida. Główny build Gradle obejmuje desktop i biblioteki JVM; historyczny `javity-android` jest poza reactorem i zawiera helpery lokalizacji/intentów, a nie launcher nowego dema.

| Obszar | Wynik / wymagane działanie |
| --- | --- |
| Bytecode Java25 | D8 8.10.9 z build-tools36 odrzuca major69. Oficjalny R8/D8 **9.5.26** poprawnie tworzy DEX z obecnego `javity-core` i całego testowego APK. Nie trzeba z tego powodu zmieniać JDK desktopu. |
| Rdzeń / komponenty | Log emulatora potwierdził `CPU_COMPONENTS_PASS`: GameObject, lookup komponentu i ruch CharacterController2D. Zbudowano APK z istniejących jarów, bez modyfikacji źródeł silnika. |
| Yoga/UI | Obecny moduł używa LWJGL Yoga i natywnych bibliotek desktopowych. Potrzebny backend Yoga JNI na Androida i zmiana doboru FreeType natives. |
| React/GraalJS | Obecny host jest oparty na GraalJS JVM25. Nie jest zweryfikowanym hostem Android ART; potrzebny osobny dobór silnika JS/mostu. |
| MapLibre | Adapter korzysta z Native FFI/JVM FFM, GLFW/LWJGL i Linux x64 EGL. Potrzebny osobny adapter MapLibre Android. MBTiles/style/glyphy są danymi do ponownego użycia; nie oznacza to przenośności aktualnego wrappera GPU. |
| Shapes i materiały | **Potwierdzony błąd runtime**: GLSL1.20, brak precyzji float i wyłączone `GL_OES_standard_derivatives`. Prostokąt używa tego shadera także przy `antiAlias=false`. Potrzebna adaptacja GLES i natywne testy pikseli. |
| Java API i starsze Androidy | Kod używa m.in. records, `Stream.toList` i `List.getFirst`. Probe celowo wymaga API35; obsługa starszych API wymaga audytu i desugaringu/backportów. |
| Box2D, audio, fonty, animacje | Nie są potwierdzone samym testem rdzenia i voxelowego APK. Należy dodać odpowiednie ARM64 natives, zasoby i testy urządzenia/lifecycle. |
| Sterowanie | Desktopowy FPS nie jest mobilnym UI. Kontroler przyjmuje abstrakcyjne wejście, ale demo potrzebuje sterowania dotykiem, przycisków i testów utraty fokusu. |

## Narzędzia testowe

[Probe APK](../tools/android-probe/README.md) ma tryby `basic`, `shapes`, `voxels`. Jest diagnostyką, nie produkcyjnym modułem aplikacji. Zawiera natywne libGDX **x86_64**, więc nie jest APK na telefon ARM64. JDK25 buduje główne jary, launcher diagnostyczny ma `--release 17`, D8 przetwarza oba formaty. Dane i klucz debug są poza repozytorium.

Środowisko: emulator **37.3.4**, AOSP Android15/API35 x86_64, SwiftShader, bez `/dev/kvm`. Oficjalne archiwa SDK/obrazu sprawdzono względem SHA1 z ich manifestów. Wymagany jawny opt-in emulatora `ANDROID_I_WANT_MY_TCG=yes` i `-accel off`; partycja AVD znajduje się na większym dysku workspace. Emulacja TCG jest bardzo wolna i nie pozwala wnioskować o wydajności telefonu.

## Kolejność dalszego portu

1. Osobny produkcyjny launcher Android/AGP oraz rozdzielenie przenośnych modułów od LWJGL/FFM/GraalJS. Nie dołączać całego `javity-desktop` do APK.
2. Stabilny zestaw AndroidX/D8, ABI ARM64 i polityka min/target SDK; zwykły JDK25 może pozostać toolchainem desktopowym.
3. Rdzeń, modele/animacje, voxele/FPS i Box2D: prawdziwe testy GLES, input dotykowy, pause/resume, obrót i odtworzenie Activity/kontekstu.
4. Osobno GLES Shapes, Yoga JNI, host JS i MapLibre Android. Zachować te same kontrakty gameplay/stylów tam, gdzie to możliwe.
5. CI z KVM/emulatorem oraz test fizycznego ARM64. Dopiero na tych dowodach oznaczyć konkretny moduł jako wspierany na Androidzie.

## Wynik próby runtime

- **PASS CPU**: `GameObject`, lookup komponentów i `CharacterController2D` wykonały się w Android ART z DEX wygenerowanego z obecnego jaru Java25.
- **PASS pętli renderowania**: wariant sprite2D + box3D doszedł do 60 klatek z `GL0`; log potwierdził też callbacki pause/resume. Nie jest to pełny test animacji, utraty kontekstu ani zgodności wizualnej. [Zrzut emulatora](android-emulator-dialogs.png) zasłaniają podpowiedź immersive mode oraz ANR System UI, więc nie potwierdza poprawności widocznych pikseli modelu.
- **FAIL Shapes**: rzeczywiste wywołanie `GdxRenderer.drawShape` zakończyło się błędem kompilacji `SmoothShapeRenderer`; [log](android-shader-failure.txt) zawiera brak precision i niewłączone pochodne GLES. `antiAlias=false` nie omija tego shadera.
- **NIEPOTWIERDZONE voxele/FPS**: APK i DEX powstały, lecz pierwsze kolejne uruchomienia pozostały w pauzie po uśpieniu ekranu (czarny zrzut, brak logu `CREATE`). Potwierdzono potem Awake/stay-on i ponowiono próbę; środowisko miało również ANR usług systemowych. Braku wyniku nie traktujemy jako dowodu błędu meshera ani jako zaliczenia Androida.

Nie zmieniano źródeł silnika, by dopasować wynik próby. W pierwszym wariancie diagnostycznego APK zabrakło classpath assets libGDX; pakowanie fontów/shaderów poprawiono przed testem 60 klatek. Narzędzia i aktualne wyniki: [JSON](android-verification.json). Potrzebny kolejny, powtarzalny przebieg na emulatorze z KVM i fizycznym ARM64; nie deklarujemy pełnego wsparcia Androida na tej podstawie.
