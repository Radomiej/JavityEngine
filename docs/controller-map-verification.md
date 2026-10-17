# Weryfikacja controllerów i map — 2026-10-15

Pełny reactor `./gradlew build`: **400 testów Java**, zero failures/errors/skips. Generatory i styl mapy: **17 testów Python**, bez sieci. Weryfikacja na Linux x64, zwykły JDK25 i Mesa software OpenGL/EGL.

Rzeczywiste uruchomienia: smoke nowego rdzenia, mapy rastrowej, runtime, Shapes, materiałów, portów UI/animacji, UI parity, MM systems, MapLibre, Gorzowa i voxeli; dodatkowo legacy smoke. Testy dotykają rzeczywistych buforów/tekstur i cleanup, a nie wyłącznie mocków. Finalne `installDist` uruchomiono dla Gorzowa i voxelowego FPS poza checkoutem, ze ścieżki zawierającej spacje.

Przegląd agentów wykrył i doprowadził do poprawek: wygasanie bufora skoku przed lądowaniem, kierunek myszy FPS oraz odrzucanie etykiet mających wyłącznie `name:pl`. Testy regresji obejmują te przypadki. FPS blokuje stawianie bloku w bryle gracza i sprawdza bezpieczny powrót z free-fly.

Dowody: [Gorzów Native/GL JS](gorzow-comparison.png), [pomiary porównania](gorzow-render-comparison.json), [Gorzów GPU/query](gorzow-verification.json), [voxele](voxel-verification.json), [galeria voxelowa](demo-voxels.png). Pomiary porównania są opisowe — renderery nadal różnią się placementem tekstu i antyaliasingiem. Test kolejności dróg sprawdza styl CPU i rzeczywisty widok miasta; nie jest osobnym GPU pixel fixture dla każdego możliwego skrzyżowania.

Controller 2D nie implementuje odpowiedzi na kolizje/Box2D. FPS obsługuje bryłę AABB w skończonym świecie voxelowym, bez step-up, schylania i ogólnej fizyki 3D. Pipeline GitHub Actions jest skonfigurowany; wynik jego zdalnego uruchomienia jest oddzielny od powyższej lokalnej weryfikacji.
