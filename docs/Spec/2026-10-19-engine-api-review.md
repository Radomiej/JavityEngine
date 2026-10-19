# Przegląd API i jakości rdzenia — 2026-10-19

Zakres: bieżący master po integracji ORBIT (`2ec7684`), Game/Component/GameObject/GameSystem, publiczne wejście, sceny/prefaby/zasoby oraz granice API launchera. Jest to przegląd tych kontraktów, nie certyfikacja całego renderera ani wszystkich modułów/platform.

## Utrzymany model

GameObject + Component pozostaje podstawą gameplayu, a Artemis opcjonalnym podsystemem. Nie potrzeba drugiego ECS ani nowego schedulera. Istnieją SceneManager, SceneTimers, SceneJson/Prefab, lokalny streaming AssetStreams, animator, avatar, Bullet i renderer instancingu; poniższy backlog dotyczy rozszerzenia ich kontraktów, a nie ponownej implementacji.

Lookup jest polimorficzny: `getComponent(Type)` zwraca pierwszą zgodną instancję lub null; `hasComponent(Type)` jest predykatem. Kiedy potrzebujesz komponentu, pobierz go raz i sprawdź null. `requireComponent(Type)` służy walidacji konfiguracji, nie normalnemu sterowaniu ruchem ani odpytywaniu w każdej klatce. Dopuszczone są wielokrotne komponenty tego samego typu.

## Doprecyzowane kontrakty wdrożone w tym przeglądzie

- `removeComponent(instance)` operuje na tożsamości (`==`), zgodnie z `containsComponent`. Nadpisanie equals/hashCode nie pozwala usunąć równego sąsiada ani cudzej instancji. Lookup/usuwanie po typie zachowują dotychczasową kolejność.
- Usunięcie natychmiast aktualizuje membership, indeksy, flagę removed i powiadomienie. Zwalnianie pozostaje odroczone do flush. Nie wprowadzono ponownego przypinania instancji komponentów.
- Przy sprzątaniu komponentów, obiektów i systemów kolejne RuntimeException/Error nie zatrzymują zwalniania pozostałych elementów. Pierwszy błąd pozostaje pierwotny, różne kolejne trafiają do suppressed; identyczny obiekt wyjątku nie jest dodawany do samego siebie.
- `Game.step` zawsze próbuje wykonać flush i resetuje straż rekurencji. Wyjątek aktualizacji pozostaje pierwotny, a błąd flush jest dołączany. Gdy aktualizacja nie rzuciła błędu, błąd flush nadal jest zgłaszany. To nie jest rollback symulacji ani automatyczne odtworzenie sceny po błędzie.
- Game jest jednowątkowy. Snapshoty nie oznaczają thread safety. Obecny kontrakt start/onCreate i zamykania częściowo zbudowanej sceny nie został rozszerzony o automatyczną transakcję startu.

Regresje i zakres dowodu: [Bugs](../Bugs/2026-10-19-core-api-review.md).

## Kolejność dalszego rozwoju

| Priorytet | Brak lub ograniczenie | Konkretne miejsce / następny etap |
| --- | --- | --- |
| P1 | Otwarta bramka zgodności fontów native/HTML w CI | BUG-UI-001; najpierw pomiar advance/ink edges według istniejącej specyfikacji, bez podnoszenia tolerancji. |
| P1 | Typowane, przenośne API usług platformy | OrbitBrowser to browser-only request(method, JSON, callbacks). Zaprojektować opcjonalny SPI profilu/zapisów/pokoi, z capabilities, błędami, anulowaniem i owner-thread dispatch; provider ORBIT i jawny lokalny provider. Nie dodawać TeaVM/JSO do core. |
| P1 | Komponent wiążący panel UI z obiektem gameplayu | UiDocument i inspector istnieją, ale brak ogólnego komponentu montującego/odmontowującego panel i zamykającego subskrypcje. Ustalić ownership Stage/theme/document oraz zachowanie po zmianie sceny. Nie tworzyć GameObjecta dla każdego diva. |
| P2 | Wejście przenośne i konteksty akcji | InputActions jest klawiaturowy i wymaga finishFrame/releaseAll od hosta. Dodać jawne konteksty gameplay/menu, gamepad/analog i touch; zdefiniować rebind podczas wciśnięcia oraz utratę fokusu. |
| P2 | Ujednolicony lifecycle subskrypcji/uchwytów | Radio/Signal mają Subscription, GameObject.onComponentChange zwraca Runnable. Zaprojektować wspólny closeable scope dla subskrypcji i innych zasobów komponentu; zapewnić zamknięcie także przed onCreate. Zachować zgodność istniejących wywołań. |
| P2 | Prefaby jako pipeline assetów | Prefab jest serializowanym szablonem SceneJson, codeki są jawne. Brakuje ogólnego katalogu asset IDs/resolverów i wersjonowanych overrides/migracji prefabów. Nie zastępować tego arbitralną refleksyjną instancjacją klas. |
| P2 | Multiplayer dowolnej gry | LocalRoomWorker tworzy konkretny LocalRoomSimulation; pokoje i SDK nie stanowią generycznej replikacji GameObjectów. Potrzebny schemat authority/komend, stable network IDs, spawn/despawn, interpolacja/reconciliation i test reconnect. |
| P2 | Spójna ścieżka oświetlenia i pomiary pamięci | Istniejący instancing ma ograniczenia kompatybilności z otoczeniem. Najpierw wspólne dane świateł i parity/recovery, baseline alokacji i retencji; potem reuse snapshotów/buforów. Bez deklaracji oszczędności bez pomiaru. |
| P3 | Import modeli i zgodność platform | Test Fox korzysta z konwersji glTF do G3DJ; to nie ogólny runtime loader glTF/GLB/PBR. Dalsze etapy wymagają eksportowanych rigów, loadera i walidacji materiałów. Pełne Yoga/Bullet/MapLibre na web oraz fizyczny Android ARM64 nadal wymagają osobnych dowodów. |

Te pozycje są propozycją kolejności. Nie oznaczają pełnej zgodności z OneJS/Unity, produkcyjnego netcode ani wdrożenia prywatnego API. UI clipping/breakpointy, roślinność, dalszy streaming voxelów i izolacja legacy pozostają w [TODO](../TODO.md); nie są implementowane tym patchem.

## Wyniki końcowe tego batcha

- Pierwotne7 regresji RED, po poprawce GREEN; pełny reactor objął8 przypadków LifecycleSafetyTest, zero błędów/pominięć.
- `./gradlew build :javity-ecs-artemis:usageSmoke --no-daemon --max-workers=2`: PASS38s,113 zadań (27 wykonanych,86 up-to-date). Inwentarz XML:879 przypadków/100 zestawów, zero failures/errors/skipped; zawiera cache niezmienionych wyników, nie879 świeżych wykonań.
- `./gradlew -p experiments/javity-wasm orbitPackage test -PorbitLauncherOrigins=http://localhost:5173 --no-daemon --max-workers=2`: PASS43s, pakiet Wasm z przebudowanym core i4 testy eksperymentu. Lokalny origin jest konfiguracją testową, nie produkcyjną.
- `python3 experiments/javity-wasm/tools/chromium-proof.py --evidence-dir artifacts/api-review-wasm`: PASS, rzeczywisty GdxRenderer/Chromium154/SwiftShader, canvas pixels bez nakładki, input/resize/dispose; zero błędów strony i nieudanych żądań. Nie dowodzi to pełnego portu silnika na web, GPU performance ani świeżej integracji z prywatnym API ORBIT.
- Android nie był w tym batchu ponownie uruchamiany. Native font gate BUG-UI-001 pozostaje otwarty.
