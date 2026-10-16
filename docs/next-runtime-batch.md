# Kolejny zakres: FPS, narzędzia sceny, streaming i otoczenie

Data: 2026-10-16. **Projekt zaakceptowany przez użytkownika 2026-10-16; nie jest opisem wdrożonego API.** W tej aktualizacji dokumentacji nie dodano nowych klas gameplay/renderera. [Guide działającego silnika](engine-guide.md) oddziela istniejące funkcje od planu. Kierunek zaakceptowano. Poniższe cztery plany rozdzielają wykonanie na małe etapy i wymagają przeglądu przed implementacją z dowodami runtime.

## Cel użytkownika

Uruchomić łatwy do znalezienia przykład gry blokowej z czytelną ręką i narzędziem w FPS, możliwością przełączenia TPP, edycją GameObjectów podczas gry i efektami feedbacku. Docelowa gra ma ładować zasoby podczas działania oraz konfigurować światło i niebo jako część sceny. API ma pozostać Java/GameObject/Component; UI może być natywne albo React. Użytkownik prosi także o dokumentację i guide.

Zaakceptowane założenie: pierwszym narzędziem jest autorski pikselowy kilof w prawej ręce. Pierwszy streaming dotyczy lokalnych/pakowanych modeli i tekstur; automatyczne pobieranie HTTP oraz generowanie nieskończonego świata nie są tym samym zadaniem. „Inne” przy light/skybox interpretujemy jako ambient, mgłę i prosty cykl dnia/nocy; nie jako obietnicę pełnego PBR i dynamicznych cieni.

## Fakty po audycie

| Obszar | Działa teraz | Brak lub problem |
| --- | --- | --- |
| Voxel FPS | `--voxels`, WASD/mysz, F5 FPS/TPP, canonical animator i depth-cleared pass rąk | Ręce są pionowymi proceduralnymi bryłami; narzędzie mało czytelne |
| Tree viewer | `--inspector`, F1, GameObjecty/komponenty/hierarchia Transform3D | Nie jest obecnie podłączony do VoxelDemo ani Physics3DDemo |
| Gizma | MOVE/ROTATE/SCALE, LOCAL/WORLD, snap, undo/redo | Wybór osi na ekranie; brak osobnego galerii/panelu scenariuszy dla voxelów, świateł i efektów |
| Feel 3D | Punch, shake, recoil, bob, FOV, particles, world postprocess | Brak wspólnego panelu authoringu presetów i spójnego przykładu tool-use w FPS |
| Assety | SceneAssets/AssetManager queue + update, zależności loaderów, scene loading | Brak typed lease, eksmisji/budżetu pamięci, kolejki priorytetów i spójnej diagnostyki streamingu |
| Światło | Renderer Environment: ambient i DirectionalLight | Brak komponentów sceny i ich edycji; brak skyboxa |

## 1. Czytelny przykład FPS

Rekomendacja: poprawić istniejące `--voxels`, żeby komenda dawała oczekiwany przykład, oraz dodać czytelną nazwę trybu w launcherze/dokumentacji. Początkowa kamera ma patrzeć na teren i obiekt do kopania, a HUD nie może dominować widoku narzędzia.

Prawe przedramię/dłoń z wyraźnym drewnianym trzonkiem i głowicą kilofa zajmują dolny prawy fragment ekranu. Geometria i atlas są autorskie. Użycie ma widoczny zamach, powrót i lekki recoil; chód daje niewielki bob, sprint/skok nie powodują przecięcia kamery. Zachowujemy jeden kanoniczny animator, semantyczne części avataru i socket ręki. Korekta pozy/kompozycji nie może niszczyć TPP ani przesuwać kontrolera fizyki. Nie obiecujemy jeszcze automatycznego importu dowolnego produkcyjnego rigu.

LMB: blok usuwany przez istniejący raycast; swing uruchamia się jako feedback akcji, z jawnie ustaloną polityką powtarzania przy przytrzymaniu. E może pozostać demonstracją samej animacji. T i F5 pozostają dostępne. Test nie uznaje dowolnych brązowych pikseli za dowód kilofa: region dłoni i głowicy ma być widoczny w oczekiwanej części obrazu przed/po akcji, także przy ścianie, zmianie proporcji i przełączeniu TPP.

## 2. Wspólne narzędzia gameplay i Feel

Wpiąć obecny RuntimeInspector również do demo voxelowego i Bullet: F1 zwalnia kursor, gameplay nie przechwytuje myszy panelu, zamknięcie przywraca poprzedni tryb kursora. Inspektor pokazuje tę samą Game, bez budowania drugiego modelu sceny. Gracz, teren/chunki i avatar są widoczne jako obiekty silnika; nie tworzymy GameObject na każdy blok.

Najpierw uzupełnić ergonomię: wyszukiwanie po nazwie/ID/tagu, czytelne zagnieżdżenia i stan komponentów, focus zaznaczenia kamery debugowej. Zachować jawne adaptery edycji, izolację scen i historię. Gizma do nowych obiektów: kierunek słońca, pozycja/zasięg punktowego światła, stożek spot oraz osie socketów. Teleportowanie obiektów sterowanych kontrolerem/fizyką wymaga ich API, nie obejścia TransformEditPolicy.

Panel Feel ma wybór istniejących presetów, play/pause/stop, WORLD/UI, podgląd parametrów i liczby aktywnych playbacków. Pierwsze scenariusze: use-tool, hit, landing, sprint i scene transition. Reużywa `PresentationEffects3D`, `FeedbackPlayer`, `TimeManager`, `SoundManager`, nie tworzy drugiego schedulera. Edycja działa tylko przez jawne typowane adaptery; nie otwieramy refleksyjnego dostępu do wszystkich pól Java. Nie jest to inspektor DOM/React.

## 3. Streaming zasobów

Rozważone podejścia:

1. Tylko instrukcja `SceneAssets.queueSceneAssets/update`: mały koszt, działa już dziś, ale lifetime i ograniczenia pamięci pozostają po stronie każdej gry.
2. **Rekomendowane:** opcjonalna warstwa streamingu nad istniejącym AssetManager — typed uchwyty, jawne posiadanie i diagnostyka, z wykorzystaniem loaderów libGDX.
3. Własny scheduler i komplet loaderów/GPU uploaderów: więcej kontroli, dużo większy koszt utrzymania, duplikacja zależności i obsługi kontekstu.

Pierwszy etap drugiego wariantu:

- Jedna scena posiada usługę streamingu; współdzielenie między scenami wymaga osobnego jawnego właściciela hosta, nie globalnego singletona.
- Typowany klucz obejmuje ścieżkę i klasę zasobu; sprzeczne typy/parametry tego samego pliku są odrzucane. Powtórne żądania współdzielą load i zależności.
- Uchwyt konsumenta ma stan QUEUED/LOADING/READY/FAILED/CLOSED, pobranie gotowego zasobu, opis błędu i idempotentne close. Nazwy i podpisy API ustala dopiero specyfikacja.
- Konsument trzyma lease tak długo, jak ModelInstance/Texture/animator korzystają z zasobu. Po close nie wolno dalej pobierać zasobu; aplikacja najpierw odłącza renderujące komponenty.
- CPU przygotowanie przez async loader; manager, publikacja READY i upload GPU na owner thread. Nie wolno wywoływać GL na workerze. Budżet czasu jest miękki, bo pojedynczy upload nie jest przerywalny.
- Limity kolejki i inflight; jawny priorytet oczekujących żądań. Prefetch może utrzymywać zasób bez komponentu. Kolejność jest deterministyczna dla równego priorytetu.
- Eksmisja LRU dotyczy tylko nieużywanych zasobów, po końcu renderowania. Aktywnego lease nie wolno wywłaszczyć, nawet gdy budżet jest przekroczony. Raportować presję zamiast usuwać używany model.
- Limity pamięci oparte na jawnych szacunkach rozmiaru; nie udawać dokładnego odczytu całej pamięci GPU. Dodatkowo limity liczby zasobów, prac i bajtów źródeł.
- Anulowanie i zamknięcie sceny ignorują spóźnione wyniki przez generację; zarządzane zależności i częściowy błąd mają jawny cleanup. Do końca trwającego load nie wydajemy już nowego lease zamkniętego żądania.
- Postęp osobno per żądanie/batch; nie używać monotonicznego SceneAssets.progress jako procentu każdego nowego batcha.

Pierwszy zakres: lokalne/internal Texture i Model z domyślnymi loaderami i zależnościami, działająca gra w czasie ładowania, diagnostyka i demonstracja load/unload/prefetch. Nie zmieniać ModelInstance/animatora automatycznie przy zmianie modelu. Hot reload plików, atomowa wymiana wersji żywego modelu, HTTP cache/download, audio streaming i chunk streaming są dalszymi rozszerzeniami, nie synonimami tego etapu.

Testy: deduplikacja, zależności, liczniki lease, zamknięcie/anulowanie/błąd, brak eksmisji aktywnego zasobu i cleanup spóźnionej pracy; realny render z równoczesnym ładowaniem, poprawna tekstura/model po READY, brak użycia po dispose. Android wymaga osobnego testu JNI/GLES i utraty kontekstu; sam desktop nie potwierdza portu.

## 4. Światło, niebo i otoczenie

Rekomendacja: komponenty światła po stronie GameObject, ustawienia otoczenia po stronie sceny, renderer wykonuje ich snapshot dla danej kamery. Nie odczytuje mutable sceny z workera. Ambient jest jednym ustawieniem sceny, a nie bezwarunkowo sumowanym światłem każdego obiektu. Zachować zachowanie starego renderer.environment dla scen bez nowej konfiguracji; jawny tryb sceny przejmuje konfigurację bez podwójnego słońca.

Pierwszy zakres: DirectionalLight3D, PointLight3D, SpotLight3D, ambient, kolor/intensywność, enabled/active, kierunek/pozycja z Transform3D i jawne limity liczby świateł. Nazwy są propozycją, nie istniejącymi klasami. Wyłączyć nieaktywne/usunięte światła; przy nadmiarze deterministyczny wybór i diagnostyka. Spot wymaga jawnej pojemności shaderów — samo dodanie obiektu do Environment nie jest dowodem, że shader go rysuje.

Skybox: sześć lokalnych tekstur/cubemap albo oryginalny proceduralny gradient w oddzielnym, jawnie wybranym trybie. Skybox nie przesuwa się przy translacji kamery, reaguje na obrót, nie zasłania geometrii i nie zapisuje depth; przywraca stany GL. Nie renderować go w pass viewmodelu ani UI, nie wyzerować wyniku postprocessingu. Kolejność: tło → świat 3D → efekty świata zgodnie z ich passami → viewmodel → SCREEN/HUD. Host scene transition obejmuje finalny widok według aktualnego kontraktu.

Mgła: kolor i parametry odległości; najpierw wariant wspierany przez wybrany shader modeli. Nie obiecywać automatycznej mgły na dowolnym własnym shaderze, Shapes, MapLibre ani UI. Prosty dzień/noc steruje kierunkiem/kolorem słońca, ambientem i kolorem proceduralnego nieba. Tekstury cubemap są pożyczone od właściciela zasobów; zamknąć GPU pass przed zwolnieniem lease. Recovery kontekstu obejmuje cubemap/program/mesh, również na Androidzie w osobnej bramce.

Światło w obecnym DefaultShader to klasyczny forward lighting, nie PBR/IBL. Dynamiczne cienie, HDR/bloom, odbicia i wolumetryka wymagają kolejnych specyfikacji. Nie dokładamy ich pod nieokreślone „inne”.

Testy: kierunek/kolor/intensywność i dezaktywacja świateł na faktycznych pikselach modeli oraz voxelów; spot rzeczywiście oświetla obiekt; skybox nie ma paralaksy, poprawnie zachowuje depth/stany, nie wpływa na HUD/viewmodel; fog i dzień/noc mają kontrolowane kadry. Powtórzyć utratę EGL dla nowo posiadanych zasobów przed deklaracją wsparcia Androida.

## Kolejność i kryterium zakończenia

1. Czytelny FPS i odnajdywalne demo, z porównaniem prawdziwych zrzutów przed/po.
2. Wpięcie istniejącego inspektora do voxelów; ergonomia gizmos i scenariusze Feel w małych etapach.
3. Usługa streamingowa z jawną własnością i lokalnym demo.
4. Światła/skybox/mgła i podgląd parametrów przez typowane adaptery narzędzi.
5. Pełny reactor, odpowiednie native smoke, oddzielne Android gates i aktualizacja guide. Commit/push według dotychczasowej autoryzacji użytkownika, bez publikowania binariów lub kluczy.

Każdy etap kończy się działającym przykładem, instrukcją uruchomienia, wynikami CPU/native i zrzutem. Niedostarczone modele produkcyjne i fizyczny ARM64 nie stają się zaliczone na podstawie testu proceduralnego/desktopu. Uzgodnienie tego kierunku nie oznacza, że wszystkie opisane klasy już istnieją.


## Plany wykonania — do przeglądu

1. [Czytelny voxel FPS z kilofem](superpowers/plans/2026-10-16-readable-voxel-fps.md).
2. [Narzędzia sceny i Feel](superpowers/plans/2026-10-16-runtime-tools-feel.md).
3. [Streaming lokalnych assetów](superpowers/plans/2026-10-16-asset-streaming.md).
4. [Światła, skybox i otoczenie](superpowers/plans/2026-10-16-lighting-and-sky.md).

Metoda wykonania zachowuje wcześniejszą preferencję użytkownika: praca agentów pod nadzorem głównego agenta i niezależne przeglądy. Integracje wspólnego renderera/VoxelDemo następują sekwencyjnie. Nie zmieniamy wyboru metody ani nie traktujemy samych planów jako działającego kodu.
