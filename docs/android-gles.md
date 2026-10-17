# Android GLES renderer compatibility gate

The initial Android app target is GLES 3.0. This establishes a supported API level for dynamic uniform-array indexing in `SmoothShapeRenderer`; it does not by itself establish that the generated shader or pixel output works on Android drivers.

`SmoothShapeRenderer` keeps its GLSL 1.20 sources and generates a GLSL ES 3.00 dialect only when the active libGDX context reports OpenGL ES 3 or newer. The conversion changes stage inputs/outputs, fragment output, and texture lookup syntax. It preserves the fixed 64-point path ceiling, eight dash lengths, shader loop bounds, material insertion point, and material-program cache. GLES 2 is rejected because this renderer depends on ES 3 shader behavior. Desktop contexts keep the original GLSL 1.20 source.

`RectanglePainter` uses its existing GLSL ES 1.00-compatible shader forms (`varying`, `texture2D`, and `gl_FragColor`). It is separate from the SDF renderer and does not use the SDF dialect conversion or change libGDX's global shader prefixes. The Android GLES3 fixture must still compile and exercise this shader through actual UI drawing.

`WorldPostProcess` and `ParticleBurstRenderer3D` also keep their ES 1.00-compatible shader forms. The particle renderer independently requires GL30 for VAO and element-buffer state preservation. GLES3 exposes that API, while shader compilation and state restoration on an Android context remain validation gates. The postprocess desktop GL20/GL30 contract probe does not substitute for Android GLES execution.

The 1x1 white textures owned by `SmoothShapeRenderer` and `UiTheme` retain their source Pixmaps until the owners close. libGDX marks those textures managed and needs the source Pixmap when rebuilding them after context loss. Pausing and resuming without destroying the GL context does not exercise this recovery path.

A private 16×16 EGL pbuffer using SDK SwiftShader compiled and linked the scratch default GLES3 shaders and injected material shaders with no-space, spaced, and newline/comment-separated `texture2D` calls. The context reported OpenGL ES 3.0 and 1056 fragment uniform components. This validates shader syntax and linking on that compiler; it does not validate Android drivers or pixels.

Required platform evidence before an Android support claim: render AA, dash, cap, material, rectangle/image UI, postprocess and particle fixtures; force actual EGL context recreation and confirm managed white textures reload; verify custom shader owners are recreated or restored by the application lifecycle. These checks need Android GLES/JNI execution and are not satisfied by Java compilation or a private compiler pbuffer.

## Odtwarzanie framebufferów libGDX 1.14.2

Silnik używa `RecoverableFrameBuffer` dla własnych framebufferów (`WorldPostProcess` i diagnostyka Androida). W tej wersji libGDX `GLFrameBuffer.build()` dopisuje instancję do managed registry również przy odbudowie, a `invalidateAllFrameBuffers()` iteruje po rosnącym rejestrze. Dodatkowo nowy attachment trafia za starym, podczas gdy do FBO dołączany jest pierwszy. Rzeczywisty test EGL na Androidzie wykrył brak wznowienia klatek; źródła i zrzut GLThread wskazały tę ścieżkę.

Adapter usuwa wyłącznie dodatkową rejestrację własnej instancji, zachowując indeksy innych właścicieli. Przy odbudowie po rzeczywistej utracie kontekstu porzuca stare nazwy i attachmenty bez `glDelete`, ponieważ nowy kontekst może już używać tych samych liczb. Po nieudanej odbudowie zeruje bookkeeping pozostały po cleanup libGDX, aby późniejsze dispose nie usuwało ponownie użytych nazw. Nie czyści globalnego rejestru i nie naprawia framebufferów utworzonych bezpośrednio przez inne biblioteki.

To adapter związany z zachowaniem libGDX 1.14.2, nie ogólny mechanizm ręcznej odbudowy w żywym kontekście. Przy zmianie wersji biblioteki ponownie sprawdź upstream i regresję: dwóch właścicieli, attachment/depth, błąd niekompletnego FBO oraz ponowne użycie nazw. Testy CPU korzystają z rzeczywistych klas libGDX z kontrolowanym GL; rzeczywisty test EGL i pikseli jest oddzielną bramką opisaną w statusie Androida.

## VAO i lokalizacje shaderów po utracie EGL

`JavityAndroidGraphics` wykonuje ograniczoną naprawę po standardowym `AndroidGraphics.onSurfaceCreated()`. W libGDX 1.14.2 nowy VAO zachowuje stare `cachedLocations`, a ponownie skompilowany `ShaderProgram` zachowuje mapy poprzednich lokalizacji. Adapter czyści cache VAO należących do aplikacji i odczytuje ponownie lokalizacje aktywnych shaderów. `RecoverableDefaultShaderProvider` aktualizuje dodatkowe lokalizacje `BaseShader`/`DefaultShader`, w tym światła i kości, zachowując właścicieli modeli i programów. Nie wykonuje odpytywania GL przy każdym rysowaniu.

Dostęp do prywatnych pól jest celowo przypięty do libGDX 1.14.2; reguły R8 są w `javity-android-app/proguard-rules.pro`. Aktualizacja libGDX wymaga przeglądu tego adaptera i ponownego testu zmieniających się lokalizacji. Testy CPU wymuszają inne lokalizacje po odbudowie; nie zastępują testu urządzenia.

Rzeczywisty test API35 x86_64 przeszedł w 55.856 s: ten sam widok, renderer i właściciele zasobów przeżyli utratę EGL, a sprawdzenia pikseli modelu 3D, SDF, UI i tekstu oraz odbudowy tekstury i framebuffera z depth przeszły przed i po utracie. Log: [app-managed-context-instrumentation.txt](artifacts/android/app-managed-context-instrumentation.txt). To wynik emulatora, nie potwierdzenie wszystkich sterowników urządzeń ani wszystkich efektów cząsteczkowych/postprocess na Androidzie.
