# Imported animation validation

Wymaga JDK25, Python3 i działającego desktopowego OpenGL. Test używa istniejącego `ModelPoseAnimator` oraz `GdxRenderer`, bez zmiany kodu produkcyjnego silnika.

```sh
python3 tools/animation-validation/run.py
python3 tools/animation-validation/run.py --gl30
python3 tools/animation-validation/run.py --interactive
```

Skrypt buduje installDist; `--skip-build` jawnie wybiera już istniejącą dystrybucję. Do pracy lokalnej ustaw JAVA_HOME na JDK25. W środowisku bez monitora potrzebny jest Xorg/Xvfb i odpowiedni DISPLAY. `LIBGL_ALWAYS_SOFTWARE=1` pozwala użyć Mesa software; sam headless backend nie potwierdza obrazu.

`GdxRenderer(24, 4)` jawnie zapewnia shader capacity dla24 kości; domyślny renderer ma limit12. Ten model wymaga większej pojemności.

Viewer: 1 Survey, 2 Walk, 3 Run, 4 crossfade do Run, 5 mikser Walk/Run 50%, 6 maskowany ogon, Space pauza WORLD. Zamknięcie okna zwalnia zasoby. Viewer najpierw wykonuje te same asercje, potem otwiera sterowanie; raport interaktywny trafia do ignorowanego `.generated/interactive`, nie nadpisuje zachowanych wyników smoke.

Asset Fox z repozytorium Khronos jest przypięty do rewizji w `fox/revision.txt`; upstream autorzy i licencje są w [SOURCE.md](fox/SOURCE.md) i [NOTICE.md](fox/NOTICE.md). Jest darmowy; nie jest assetem Mojang ani częścią Unity Animancer. Model CC0, rig/animacje i konwersja glTF CC BY4.0. Wymagane uznanie autorstwa zachowano również na zrzutach.

`prepare_fox.py` to narzędzie **tylko dla tego fixture**, nie ogólny importer glTF. Wymaga NumPy/SciPy. Zapisuje G3DJ i niezależne próbki skinningu oraz lokalnych TRS z oryginalnego glTF. Zachowuje pozycje/UV/skin weights/klipy; generuje normalne trójkątów i przekłada inverse bind matrices na konwencję G3DJ. W silniku nadal ładowany jest G3DJ, nie GLB. Regeneracja:

```sh
python3 tools/animation-validation/prepare_fox.py
```

Raport sprawdza 12 próbek ×1728 wierzchołków. Tolerancja maksymalnego odchylenia wynosi 0.01 jednostki assetu: przy skali sceny .02 to 0.0002 jednostki świata. Uwzględnia aproksymację SLERP przez libGDX dla małych kątów względem matematycznego SLERP SciPy. Dla przyjętej kamery i viewportu to mniej niż piksel; raport zachowuje faktyczny błąd, nie tylko PASS. Referencja blendingu normalizuje kwaterniony, a próbki czasu idą w krokach <=50ms, ponieważ Game.step ogranicza dt do100ms.

Oddzielnie sprawdzane są zmiany obrazu każdego klipu bez HUD-u, crossfade start/mid/end, maska ogona i izolacja reszty kości, mikser synchroniczny fazowo, pauza WORLD i playback, speed2x, niezależne instancje, brak aktualizacji podczas renderu, marker i ONCE completion, zwolnienie tekstury źródła. To dowód dla jednego importowanego rigu, nie retargeting, IK, root motion ani walidacja wszystkich formatów/rigów/Androida.

GIF z rzeczywistych klatek: po teście GL20 uruchom `python3 tools/animation-validation/make_preview.py` (Pillow).

## Exact pose batching (opt-in)

```sh
python3 tools/animation-validation/run.py --pose-cache
python3 tools/animation-validation/run.py --pose-cache --gl30
python3 tools/animation-validation/run.py --output-subdir task2-uncached
python3 tools/animation-validation/run.py --output-subdir task2-uncached --gl30
```

`--pose-cache` registers a scene-owned `PoseBatchSystem` and repeats the original oracle/render checks. It also compares 256 independent Fox owners against 256 uncached owners, requires one actual composition and 255 hits per frame, 256 independent markers, bit-exact local/socket/skin matrices and skinned vertices, and one immutable clip snapshot. A sequential 20-frame measurement after 10 warmup frames records `Game.step` elapsed time and GL-thread allocated bytes; it excludes rendering and initial capture. These are advisory process measurements, not a stable microbenchmark or a claim of faster total animation.

New cache reports go to `docs/artifacts/imported-animation/pose-cache/{gl20,gl30}`. `--output-subdir` retains a separate uncached run without replacing the original evidence. Reports include the SHA256 of the actually loaded core jar, animator source, harness and fixture, alongside the checkout commit. Curated reports omit duplicate screenshots after verifying their PNG hashes against uncached/original images; see [the recorded comparison](../../docs/artifacts/imported-animation/pose-cache/pixel-equivalence.json).
