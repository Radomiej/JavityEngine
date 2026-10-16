# Fizyka 3D i postać FPS/TPP — propozycja

Status: projekt do przeglądu; poniższe API nie jest jeszcze zaimplementowane.

## Cel i obecny stan

Użytkownik chce gry 3D, w tym Minecraftowo-podobne: fizykę 3D, sterowanie FPS, widoczną postać oraz proste efekty gameplay. Zachowujemy GameObject/Component, opcjonalność modułów i możliwość przyszłego Androida. Użytkownik potwierdził FPS z ręką/narzędziem i przełączany TPP. Szczególny priorytet: warstwy avataru i widoczność części bez uszkadzania kości. [Osobny projekt avataru](2026-10-15-avatar-layers-design.md).

Obecnie działają Box2D (2D), Transform3D/ModelRenderer/animatory oraz VoxelFpsController. Ten ostatni używa AABB względem pełnych bloków; nie jest Bullet, kapsułą ani ogólną fizyką 3D. Demo ma kamerę z wysokości oczu i przełącznik debugowego lotu, ale nie ma avataru, viewmodelu rąk ani TPP.

## Wybór backendu

Rekomendacja: opcjonalny `javity-physics3d` oparty na oficjalnym `gdx-bullet`, wersjonowanym razem z libGDX. Pozwala wykorzystać istniejące bindingi, bryły, zapytania i natywne artefakty desktop/Android. Ich obecność w repozytorium Maven nie zastępuje testu konkretnego ABI/urządzenia. Rdzeń nie zależy od Bullet.

Alternatywy: JBullet eliminuje część pracy JNI, ale wymaga osobnej oceny aktualności i zgodności; Jolt JNI to kolejny backend/binding i osobna integracja Androida. Nie budujemy własnego ogólnego solvera na bazie kontrolera blokowego.

## Kontrakt modułu fizyki

Proponowane komponenty i usługi:

- `Physics3D`: scene-local GameSystem, stały krok 1/60 s, ograniczony accumulator i liczba kroków. WORLD/time scale zgodnie z aktualnym modelem sceny; pauza nie zatrzymuje UI.
- `RigidBody3D`: STATIC/DYNAMIC/KINEMATIC, masa, prędkości, siły/impulsy, damping i konfiguracja CCD dla szybkich brył.
- `BoxCollider3D`, `SphereCollider3D`, `CapsuleCollider3D`: kilka colliderów na jeden body, lokalne offsety oraz warstwy/maski kolizji.
- Triggery, raycast i capsule/sphere sweep; wyniki jako własne dane Java, bez publicznych uchwytów `bt*`.
- Kontakty enter/stay/exit dostarczane po kroku solvera; zmiany sceny/body kolejkowane, bez mutacji solvera w callbacku.

Pierwszy etap przyjmuje bryły na nieparentowanym Transform3D, z jednostkową skalą. Wymiary określają collidery. Nie obsługujemy milcząco shearu, ujemnej ani zmienianej skali bryły. Wizualne dzieci mogą mieć własną hierarchię i skalę. Dynamiczne body zapisuje wynik do Transform3D po kroku; kinematyczne przekazuje polecenie do solvera przed krokiem. Jeden właściciel ruchu na obiekt.

Świat posiada solver, broadphase i dispatcher; komponenty posiadają przydzielone im body/shape/motion state zgodnie z jedną jawną regułą cleanup. Usunięcie przed onCreate, nieudane tworzenie i zamknięcie sceny muszą zwalniać wszystko dokładnie raz. Zasoby modelu/renderera nie przechodzą do fizyki.

## Minecraft i chunki

Zachowujemy lekki VoxelFpsController jako oddzielny backend ruchu blokowego. Nie dokładamy jednego rigid body/GameObject na blok i nie podłączamy jednocześnie kontrolera AABB oraz Bullet do tego samego gracza.

Adapter statycznych kolizji chunków to oddzielny etap: CPU geometria niezależna od widoczności i tekstury; wersja/generacja chunku, brudna kolejka i ograniczony budżet. Stara kolizja pozostaje aktywna do udanej wymiany na granicy kroku fizyki. Unload/load i edycje muszą aktualizować kolizję, także gdy chunk nie jest widoczny. GPU mesher nie może być autorytatywnym źródłem kolizji — obecnie usuwa ukryte ściany i traktuje brakujące chunki inaczej niż controller.

Pierwszy działający Bullet batch sprawdza niezależny teren testowy i bryły. Współpraca dynamicznych obiektów z edytowalnym światem voxelowym wymaga własnego adaptera i testów; nie deklarujemy jej na podstawie samej obecności Bullet.

## Widok postaci

`CameraRig3D` czyta pozycję stóp oraz niezależny kierunek patrzenia. Aktualizuje się po symulacji, przed cullingiem i renderowaniem.

- FPS: oczy, osobny autorski model ręki/narzędzia, animacja ruchu/użycia i prezentacyjny bob. Viewmodel ma oddzielną kamerę/pass, aby nie przecinać ścian ani HUD-u. Ciało gracza nie zasłania własnej kamery.
- TPP (założenie do potwierdzenia): F5, widoczny blokowy avatar, obrót ciała według kierunku ruchu/aim, kamera za graczem oraz sweep skracający dystans przy ścianie. Opcjonalny widok z przodu jest dalszym rozszerzeniem.
- Avatar: własne proceduralne pudełkowe części i tekstury; idle/walk/run/jump/use. Stan ruchu zasila obecne API animacji; nie jest nową kopią animatora.
- Lot debugowy pozostaje osobny. Wejście jest abstrakcyjne, żeby dało się później dodać touch/gamepad. Ten batch nie deklaruje gotowego UI dotykowego.

Widok 3D nie wymaga dołożenia solvera do istniejącej blokowej postaci: avatar i rig współpracują z tym samym interfejsem stanu ruchu, a backend AABB lub Bullet wybierany jest jawnie.

## Feel: co wykorzystać i co dodać

Wykorzystujemy istniejące FeedbackPlayer/EffectSystem, zegary, audio, Signal/EventBus i animatory. Zdarzenia kroku, lądowania, skoku i uderzenia mogą wyzwalać istniejące sekwencje.

Pierwszy zestaw rozszerzeń 3D: prezentacyjny shake/impulse kamery, FOV tween, recoil ręki/narzędzia i punch wizualnego modelu. Ich offsety nie zapisują się do pozycji bryły/capsule ani autorytatywnego wejścia ruchu. Własność i anulowanie właściwości zachowują obecne dzierżawy EffectSystem.

Nie nazywamy tego pełnym Feel. Dalsze osobne systemy: FFT/źródło PCM, haptics z backendem urządzenia, particles, postprocessing i pozostały katalog feedbacków. Obecny audyt: ../../mm-systems-audit.md i ../../plugin-feature-coverage.md.

## Kryteria weryfikacji

1. Native CPU Bullet: grawitacja/upadek/spoczynek, impulsy, bryły statyczne/kinematyczne, wiele colliderów, triggery, maski, raycast/sweep, pauza/skala czasu i cleanup po błędach.
2. Stabilny fixed step przy różnych deltach; udokumentowane ograniczenia deterministyczności. To nie gwarancja identycznej symulacji między platformami ani synchronizacji multiplayer.
3. GPU demo: spadające bryły, widoczne debug collidery, FPS ręka, TPP avatar i kamera bez wchodzenia w ściany. Rzeczywiste piksele i lifecycle, nie tylko kompilacja.
4. Voxel adapter (osobno): ściany/seams, edycja/unload/reload, generacje, brak wejścia w niezaładowany świat i atomowa wymiana kolizji.
5. Pełny reactor i dotychczasowe smokes; osobny Android build/ABI/native test. Aktualna diagnostyka Androida nie potwierdziła całego stosu i wykryła niezależny problem GLES Shapes.

## Kolejność prac

A. Priorytet użytkownika: maski/widoczność avataru, viewmodel FPS i avatar/TPP zgodnie z osobnym projektem.
B. Bullet core i testy własności/fixed-step, proste demo fizyki.
C. Feedbacki kamery/modelu 3D podpięte do istniejących systemów.
D. Adapter kolizji edytowalnych chunków oraz osobny controller capsule/Bullet.
E. Android: launcher/adaptacje platformowe i rzeczywiste testy, bez deklaracji pełnej kompatybilności na podstawie desktopu.

Po przeglądzie tego zakresu potrzebny jest szczegółowy plan implementacji. Dokument nie oznacza, że powyższe API, model postaci lub Bullet już istnieją w kodzie.
