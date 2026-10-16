# Narzędzia runtime — inspektor sceny

Pierwszy etap narzędzi działa w tej samej aplikacji, na istniejącym UiTheme/Scene2D/Yoga. Nie uruchamia osobnej przeglądarki ani edytora Unity. Jest opcjonalną nakładką deweloperską.

```sh
./gradlew :javity-desktop:run --args='--inspector'
./gradlew :javity-desktop:run --args='--scene-export=/absolute/path/runtime-scene.json'
./gradlew :javity-desktop:inspectorSmoke
```

F1 otwiera/zamyka panel w podstawowym demie, także gdy nie podano `--inspector`. W `--voxels` i `--physics3d` ten sam inspektor jest dostępny przez `RuntimeToolsOverlay`; startuje zamknięty. Panel pokazuje aktualne GameObjecty (również nieaktywne), ID, liczbę komponentów i hierarchię wynikającą z pierwszego Transform3D. Obiekty 2D pozostają płaskie, bo silnik nie ma ich ogólnej hierarchii. Kliknięcie pokazuje tagi, aktywność, Transform2D i lokalny Transform3D oraz wszystkie komponenty — także duplikaty, wyłączone i anonimowe. Quaternion jest pokazywany jawnie, bez podszywania się pod kąty Eulera.

Pole wyszukiwania dopasowuje fragment nazwy, dziesiętnego ID lub tagu bez rozróżniania wielkości liter. Dopasowane dziecko zachowuje kontekst rodziców. Przyciski `-` / `+` zwijają i rozwijają hierarchię; zwinięty rodzic nadal ukrywa potomków podczas wyszukiwania. Ukrycie wiersza przez filtr lub zwinięcie nie usuwa zaznaczenia. Zmiana instancji Game czyści wybór, historię, filtr i zwinięcia także przy powtarzających się ID. `SceneInspection.visibleRows(query, collapsed)` zwraca niemodyfikowalną projekcję i nie modyfikuje zbioru wywołującego. Obiekt chunka pozostaje jednym GameObjectem; inspektor nie tworzy obiektu na blok.

W voxelach i Bullet F1 oddaje kursor panelowi, czyści zamiary ruchu i blokuje akcje oraz polling gameplay podczas otwarcia. F1 lub X przywraca wcześniejsze przechwycenie kursora i odrzuca pierwszy delta myszy. Utrata fokusu anuluje drag i nie przechwytuje kursora przy późniejszym ukryciu lub cleanup bez fokusu. `Focus` działa tylko dla kamery zwróconej jawnie przez dostawcę debugowego: w voxelach najpierw zamknij panel i włącz `F` (free-fly), potem otwórz narzędzia. FPS/TPP i Bullet pokazują odmowę. Free-fly i focus poruszają wyłącznie kamerą, zachowując stopy i kierunek gracza; powrót do FPS synchronizuje kamerę z kontrolerem. Jeśli edycja terenu zablokowała zachowane stopy, powrót wybiera bezpieczny spawn. Jest to świadoma zmiana wcześniejszego zachowania free-fly, które przenosiło gracza do kamery.

Nakładka odświeża się na czasie UI, również podczas pauzy WORLD. Nie wymienia przycisków przy każdym ruchu obiektu; drzewo jest przebudowywane po zmianie struktury/nazwy/aktywności lub widocznych wierszy filtra/zwinięć. Rozbudowane listy i szczegóły można przewijać. Tylko panel przechwytuje pointer input, a pusta reszta nakładki nie blokuje świata.

Inspektor edytuje jawnie typowane pola Transform2D (x/y, obrót w radianach, skala x/y) i Transform3D (pozycja/skala oraz składowe quaternionu). Wartości muszą być skończone, a quaternion jest normalizowany. Apply zapisuje jedną operację undo; undo/redo ma limit 256 wpisów i sprawdza instancję sceny, obiektu, komponentu, właściciela ruchu oraz oczekiwaną bieżącą wartość. Zmiana sceny, usunięcie komponentu, dodanie komponentu przejmującego ruch lub konflikt z późniejszą zmianą tej samej właściwości unieważnia historię bez nadpisania nowej wartości.

Kolorowe uchwyty osi X/Y/Z są rysowane w świecie poza panelem. Obsługują przeciąganie pozycji, rotacji wokół osi i skali na obiektach 2D/3D. Przyciski `GIZMO: MOVE/ROTATE/SCALE` i `LOCAL/WORLD` wybierają operację oraz przestrzeń; pole kroku przyjmuje dodatni krok snapu albo `0`, aby wyłączyć snap. Snap zaokrągla przyrost przeciągnięcia na wybranej osi, zachowując istniejące wartości pozostałych osi i bazową pozycję poza siatką. Przeciągnięcie jest tylko podglądem i zapisuje pełny TRS jako jedną komendę po puszczeniu. Anulowanie dotyku, zmiana zaznaczenia/sceny lub zmiana komponentu/rodzica podczas przeciągania pozostawia oryginalny TRS bez zmian. Interakcje panelu Stage mają pierwszeństwo przed uchwytami.

Ruch WORLD w 3D konwertuje pozycję przez odwrotność macierzy rodzica; macierz osobliwa jest jawnie odrzucana, również gdy stanie się osobliwa podczas podglądu. Obrót WORLD z rodzicem oraz skala WORLD pod rodzicem lub obiektem z obrotem mogą wprowadzić shear, więc są jawnie odrzucane. W 2D skala WORLD jest obsługiwana dla obrotów będących wielokrotnością 90 stopni przez mapowanie osi; inne kąty są jawnie odrzucane, bo skala niejednorodna mogłaby wprowadzić shear. Użyj wtedy LOCAL. Nie ma ogólnego API fizycznego teleporu; `TransformEditPolicy` nadal blokuje manipulację obiektami, których ruch posiada fizyka, a ich uchwyty są tylko informacyjne.

Kamery przekazane przez `setGizmoCameras` muszą być używane do renderowania świata na całym fizycznym framebufferze okna. API libGDX `Camera` nie ujawnia GL render-viewport rectangle, a `viewportWidth/viewportHeight` opisują projekcję (światowe/logiczne jednostki), więc inspektor nie próbuje wywnioskować viewportu renderowania z tych wartości. Stage może używać własnego `Viewport`; jego prawdziwa konwersja wejścia Scene2D ma pierwszeństwo przed uchwytami dzięki kolejności processorów, bez heurystycznego testowania `Stage.hit`. Podgląd przeciągnięcia jest anulowany po zmianie macierzy kamery, wymiarów/obszaru Stage viewportu, resize okna lub utracie fokusu. Host powinien wywołać `onFocusLost()` z callbacku fokusu okna; adapter desktopowy ModernDemo jest już podłączony.

Panel zachowuje co najmniej 120 jednostek Stage na szczegóły komponentu przy rozmiarach smoke 1000×680 i 900×600, a lista obiektów i szczegóły są przewijane. Mniejszy logical viewport może przyciąć kontrolki; zapewnij panelowi co najmniej 600 jednostek logical height albo użyj większego świata Stage.

`TransformEditPolicy` w javity-core pozwala komponentom ruchu odmówić bezpośrednich zmian transformacji. Box2D `RigidBody2D`, `CharacterController2D` i `VoxelFpsController` odmawiają; poprawne przeniesienie bryły Box2D wymaga osobnego API teleportacji synchronizującego stan fizyki. Edytor obejmuje wyłącznie jawne pola transformacji, nadal pokazuje pozostałe komponenty jako informacje. Runtime snapshot export jest dostępny po podaniu hostowi jawnego schematu `SceneJson` i docelowego `Path`; desktop demo udostępnia przycisk przez `--scene-export=/absolute/path/runtime-scene.json`. Ten plik opisuje chwilowy stan runtime, nie edytowalny projekt ani ogólny round-trip dowolnych assetów.

`SceneJson.standard()` używa jawnej allowlisty codeców i nie ładuje klas z pliku. `prepare(Game)` agreguje problemy całej sceny, a `prepareSelection(List<GameObject>)` pozwala jawnie przygotować wybór. Nieznany komponent, brak zasobu/codec, niepoprawne dane, uszkodzona transformacja, zniszczony/obcy/duplikowany obiekt lub rodzic poza wyborem blokuje cały zapis i pokazuje ścieżkę oraz indeks obiektu/komponentu. `save(report, path)` zapisuje dokładnie przygotowany kandydat bez ponownego uruchamiania writerów codeców: używa UTF-8, pliku tymczasowego obok celu i atomowej podmiany. Jeśli system plików nie obsługuje atomowej podmiany, zapis kończy się błędem; przy błędzie preflight lub zapisu istniejący plik pozostaje nietknięty.

Writers codeców muszą być czyste i nie modyfikować gry. Inspektor może zachować plik przy błędzie, lecz nie może cofnąć skutków ubocznych dowolnego callbacku użytkownika. Standardowy `ModelRenderer` nie ma codec/asset resolvera, więc scena ModernDemo zgłasza go jako błąd zamiast generować fałszywe odwołanie do modelu. Host, który chce zapisywać własne assety, rejestruje codec z jawnym resolverem i zachowuje jego kontrakt zasobów.

## Wpięcie do własnej sceny

```java
RuntimeInspector inspector = new RuntimeInspector(stage, theme, () -> sceneManager.current());
inspector.setGizmoCameras(camera3D, camera2D);
InputMultiplexer input = new InputMultiplexer(
    inspector.inputProcessor(), stage, inspector.gizmoInputProcessor(), gameInput);
Gdx.input.setInputProcessor(input);
// Po kroku gry, przed stage.act/draw, na wątku UI:
inspector.update(unscaledDelta);
renderer.render(game);
inspector.drawGizmos(); // pomiędzy renderem świata i Stage
```

Eksport wymaga jawnych argumentów hosta; nie ma domyślnej ścieżki ani autozapisu:

```java
SceneJson schema = SceneJson.standard();
Path target = Path.of("/absolute/path/runtime-scene.json");
RuntimeInspector inspector =
    new RuntimeInspector(stage, theme, () -> sceneManager.current(), schema, target);
```

Nagłówek `RUNTIME SNAPSHOT` inspektora udostępnia `EXPORT` całej sceny oraz `PICK` aktualnie zaznaczonego obiektu. Raport preflightu jest widoczny w przewijanych szczegółach, a status operacji w polu edycji. Zapisany dokument jest chwilowym runtime snapshotem, nie źródłową sceną projektu. W przypadku assetów host musi zarejestrować codec dokładnego typu z jawnym resolverem przed uruchomieniem zapisu.

Supplier ma zwracać aktualną `Game`; można użyć `() -> game`. To nie jest snapshot raz pobranej sceny. Wymiana instancji sceny zeruje wybór, nawet jeśli nowa scena ma obiekty z tymi samymi ID. Zniszczenie wybranego obiektu usuwa szczegóły. Nie ma globalnej referencji do main scene.

Inspektor posiada własne węzły Yoga, pożycza Stage, UiTheme i Game. Pokazuje scenę gameplay i jawne transformacje; nie jest drzewem React/UiDocument ani pełnym edytorem. Przy zamykaniu usuń oba procesory (`inputProcessor()` i `gizmoInputProcessor()`) z multiplexer, wywołaj `inspector.close()`, następnie zamknij własne Stage/theme zgodnie z ich kontraktami. `close()` jest idempotentne; nie zamyka pożyczonej gry. Nie wywołuj odczytu/odświeżania z worker thread. UiTheme musi przeżyć inspektor.

## RuntimeToolsOverlay w hoście 3D

```java
RuntimeToolsOverlay tools = new RuntimeToolsOverlay(stage, theme, () -> game);
RuntimeInspector inspector = tools.inspector();
inspector.setGizmoCameras(worldCamera, null);
tools.setDebugCameraProvider(() -> freeFlyMode ? debugCamera : null);
tools.setInteractionChanged(() -> { clearGameplayInput(); discardMouseDelta(); });
InputMultiplexer input = new InputMultiplexer(
    inspector.inputProcessor(), stage, inspector.gizmoInputProcessor(), gameplayInput);
// Host blokuje także polling klawiszy i akcje gameplay, gdy inspector.isVisible().
tools.update(unscaledDelta);
renderer.render(game);
tools.drawGizmos();
stage.act(unscaledDelta);
stage.draw();
// Przed resize: inspector.onViewportChanged(); callbacki fokusu:
// tools.onFocusLost(); tools.onFocusGained();
// Cleanup: usuń oba procesory inspektora z input, potem tools.close(), Stage/theme.
```

Overlay pożycza Stage/theme/scenę i kamery. Posiada tylko inspektor oraz stan przekazania kursora; nie instaluje globalnego input processora ani nie zamyka zasobów hosta. `inspector.selectedObject()` zwraca wyłącznie żywy obiekt bieżącej wyświetlanej sceny albo null. Dostawca debug-camera musi zwracać null poza jawnym free-fly; overlay nie włącza tego trybu i nie pisze Transform3D. Cleanup obu procesorów pozostaje obowiązkiem hosta. Panel presetów Feel należy do osobnego etapu.

Dowody dla obu hostów: [voxele](runtime-tools-voxel.png), [voxele 900×600](runtime-tools-voxel-900x600.png), [Bullet](runtime-tools-physics3d.png), [Bullet 900×600](runtime-tools-physics3d-900x600.png). Wspólny native probe klika prawdziwy Stage przez input chain, sprawdza F1 przy focusie pola, capture/restore i brak capture bez fokusu, niezmienione rewizje voxelów i TRS kontrolera, odmowę focusu oraz rzeczywisty resize okna. Porównanie zamkniętej/otwartej nakładki wymaga >15000 zmienionych pikseli. Voxel smoke dodatkowo sprawdza focus w jawnym free-fly, krok gry, powrót do FPS bez przesunięcia stóp i awaryjny spawn po edycji blokującej zachowaną pozycję.

## Weryfikacja

CPU tests sprawdzają hierarchię, duplikaty/disabled, snapshot bez mutacji, wymianę scen z powtarzającymi się ID, usuwanie obiektu i zamknięcie sceny, walidację edycji, undo/redo, znormalizowane edycje quaternionu, limity historii, pełny, atomowy TRS, odwrotność macierzy rodzica, snapping oraz odrzucenie osobliwych rodziców, zmian własności i konfliktów. Natywny smoke klika prawdziwe kontrolki Stage i przeciąga ekranowy uchwyt lokalnej osi dziecka pod obróconym i skalowanym rodzicem, sprawdza pojedyncze undo/redo i wymianę dostawcy sceny z powtórzonym ID. Dodatkowo klika zablokowany uchwyt piłki Box2D, sprawdza zoomowaną projekcję kamery 2D, 2D WORLD scale przy 90° i odrzucenie przy 45°, anulowanie podglądu po osobliwej macierzy rodzica, zmianie kamery, resize i utracie fokusu oraz priorytet nakładającego się przycisku Stage przy przeskalowanym Viewporcie. Smoke sprawdza widoczne szczegóły przy 1000×680 i 900×600, edycję pola 2D/3D, NaN, lifecycle, F1/focus, pauzę i resize. Przed zrzutem przywraca Transform3D kostki i usuwa tymczasowego rodzica. [Zrzut](demo-runtime-inspector.png), [wynik](runtime-inspector-verification.json).

Zakres OneJS jest opisany osobno w [audycie agenta](onejs-coverage-audit.md). Ten inspektor pokazuje **scenę gameplay**, nie drzewo React/UiDocument, props/classes ani layout bounds Yoga. Nie jest to pełny edytor ani narzędzie mobilne — obecny moduł UI pozostaje desktopowy.
