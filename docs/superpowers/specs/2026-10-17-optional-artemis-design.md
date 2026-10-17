# Opcjonalny Artemis i most GameObject–Entity

**Status: wdrożone na bazie `d0b2ffb` w `javity-ecs-artemis`; desktop CPU usage, benchmark i zależności mają osobne artefakty.** Użytkownik zastąpił pełne wycofanie Artemisa wymaganiem osobnego podsystemu ECS, dostępnego obok GameObject, z opcjonalnymi powiązaniami i dynamiczną synchronizacją. Wcześniejszy kierunek „zakazać Artemis we wszystkich artefaktach” jest nieaktualny; zachowano legacy.

## Cel i decyzja biblioteki

Obecny `GameObject + Component` pozostaje prostym API gameplay. Nowy opcjonalny `javity-ecs-artemis` pozwala używać natywnego Artemis-ODB: systemów, encji, ComponentMapper, Aspect, EntitySubscription, ArchetypeBuilder i Bag. Użytkownik może korzystać z samych encji, samych GameObjectów lub jawnie je powiązać. Galaxy Rapid nie jest potrzebny jako gospodarz nowego ECS.

Rekomendacja pierwszego backendu: Artemis-ODB 2.3.0, ponieważ użytkownik chce jego pełne API, a migracja na inną bibliotekę bez pomiaru nie daje dowodu korzyści. Wersja i ograniczenia utrzymania są jawne. Core nie uzyskuje zależności od Artemis; adapter eksportuje natywne API tylko konsumentom, którzy go dodadzą. Nie obiecujemy przezroczystej wymiany bibliotek kodowi używającemu bezpośrednio ich API.

## Sprawdzone upstream i alternatywy

Metadane oficjalnych repozytoriów i Maven: [zapis odpowiedzi i adresów](../../audits/2026-10-17-consolidation/ecs-upstream-evidence.json). Daty dotyczą wydań/gałęzi, nie są benchmarkiem.

| Biblioteka | Ostatnie stabilne wydanie w GitHub Releases | Charakterystyka dla projektu |
| --- | --- | --- |
| Artemis-ODB | 2.3.0, 2019-11-10; Maven również 2.3.0; default branch commit 2021-07-20 | Pełne żądane API Java, pooling/mappers/aspects; ryzyko małej aktywności upstream |
| Ashley | 1.7.4, 2021-02-05; default branch commit 2023-04-18 | Proste Java/libGDX, families/listeners; zmiana API i brak dowodu przewagi wydajności |
| Dominion | 0.9.0, 2023-07-17; default branch commit 2023-11-28 | Java i podejście archetypowe; inne API i granice JVM/platform wymagające osobnych testów |
| Fleks | 2.15, 2026-08-29; nowsze zmiany gałęzi | Kotlin/multiplatform, nowsze wydania; koszt ergonomii dla użytkownika piszącego w Java |

Artemis README deklaruje utrzymanie; konkretne metadane są starsze, dlatego nie traktujemy tego zdania jako dowodu aktywnego rozwoju. Nie oznaczamy żadnej alternatywy jako szybszej bez tego samego benchmarku na docelowym workload/JVM.

## Rozważone podejścia

| Podejście | Korzyść | Koszt / decyzja |
| --- | --- | --- |
| **Opcjonalny native ECS + jawny most** | Zwykłe GameObjecty nie wymagają encji; użytkownik dostaje pełne API Artemis | Koszt synchronizacji tylko powiązanych danych; rekomendowane i zgodne z nowym wymaganiem |
| Artemis jako obowiązkowy magazyn każdego GameObjecta | Jeden magazyn encji od początku | Przepisanie lifecycle/duplikatów komponentów i narzucenie ECS wszystkim grom; poza zakresem |
| Inny backend ECS od razu | Możliwe inne API/storage i nowsze wydania | Migracja, pomiary i nowe granice platform; brak dowodu uzasadniającego taką zmianę teraz |

## Architektura i użycie

- `javity-core`: zachowuje obecne API, nie importuje `com.artemis`.
- `javity-ecs-artemis`: zależy od core i Artemis; posiada World i most. Moduł jest w reactorze, ale nie jest tranzytywną zależnością core, UI ani zwykłego desktop runtime.
- Konsument dodaje moduł jawnie i rejestruje scene-local `ArtemisSystem extends GameSystem` przed `Game.start()`.
- Native World/config/systems pozostają dostępne bez ograniczającego DSL. Konfiguracja i natywny stan nie są odtwarzane z refleksyjnych nazw klas w JSON.
- Proponowane udogodnienia `createLinked(name)`, `bind(gameObject, entity)` i fabryka korzystają z tego samego mechanizmu powiązań; nie tworzą drugiej sceny ani drugiego animatora/renderera.
- Czyste encje nie wymagają GameObjectów. Nie są automatycznie rysowane przez renderer wyszukujący ModelRenderer na GameObjectach; rendering wymaga powiązania lub jawnego systemu renderującego. Ten etap nie dodaje nowego renderera ECS.

To spec zachowania, nie ukończony kontrakt nazw wszystkich metod. Szczegółowy plan ustala podpisy metod i testy przed implementacją.

Na bazie `95b951c` zwykły desktop nadal dostaje Artemis przez stary engine/backend. Pierwszy krok nie dodaje mu nowej zależności na moduł ECS; całkowite oczyszczenie tego classpathu nastąpi po odcięciu starego Galaxy. Testy nie mogą błędnie uznać obecności starego tranzytywnego JAR-a za dowód działania nowego adaptera ani obiecywać jego usunięcia przed cleanupem.

## Właściciel, czas i fazy

Jedna instancja adaptera posiada jeden World. World jest procesowany najwyżej raz w kroku gry, w wybranej przy rejestracji fazie: BEFORE_WORLD (domyślna), AFTER_WORLD albo UI. WORLD dziedziczy pauzę/freeze/time scale; UI otrzymuje nieskalowany czas. Nie uruchamiamy tych samych systemów w kilku hookach na ten sam krok.

Tworzenie, mutacje, procesowanie, synchronizacja i zamknięcie należą do owner thread. Bez wielowątkowego dostępu do GameObjectów/GL. Pełny dostęp do World nie oznacza dodatkowego manualnego `process()`/`dispose()` przez konsumenta: schedulowanie i lifetime należą do adaptera. Zasada jest udokumentowana; nie twierdzimy, że można uniemożliwić naruszenie kontraktu po zwróceniu natywnego World.

Zamknięcie sceny sprząta adapter także po błędzie callbacku i przed/po inicjalizacji zgodnie z GameSystem. Cleanup kontynuuje po wyjątkach. Błąd World.process dociera do hosta; nie uruchamiamy ponownie częściowo wykonanej klatki ani nie udajemy transakcyjności całego ECS.

## Powiązania i lifetime

Powiązanie jest opcjonalne, scene-local, 1:1 w pierwszej wersji. Fabryka tworząca parę sprząta własny niedokończony obiekt/encję, jeśli konfiguracja się nie uda; nie usuwa obiektów przekazanych przez użytkownika.

Domyślnie zniszczenie GameObjecta usuwa jego encję; usunięcie encji odłącza powiązanie, pozostawiając GameObject przy życiu. Symetryczne niszczenie wymaga osobnej jawnej polityki, nie jest cichym domyślnym zachowaniem. Zamknięcie adaptera odłącza referencje, zwalnia World, nie niszczy wszystkich pożyczonych GameObjectów.

Handle sprawdza tożsamość World/sceny i generację powiązania. Sam integer ID encji jest niewystarczający: Artemis używa ID ponownie. Usunięcie encji przez pełne natywne API również musi unieważnić powiązanie, zanim stare ID może wskazać nową encję. Nie wolno polegać wyłącznie na tym, że obecnie `isActive(id)` zwraca true. Plan musi sprawdzić rzeczywiste zdarzenia/subscriptions i moment recycle w przypiętym Artemis.

Core nie ma natychmiastowego zewnętrznego callbacku destroy; istniejące `isDestroyed`, structural listener i revisions pozwalają housekeeping na granicy fazy. Zdefiniowany termin unieważnienia: przed następnym przetwarzaniem/publikacją mostu, także gdy data sync jest wyłączony. Utrzymanie lifetime musi działać podczas pauzy niezależnie od wykonywania systemów WORLD; natywne odroczone zmiany subskrypcji zachowują semantykę Artemis.

## Komponenty, zapytania i synchronizacja

`org.javity.core.Component` i `com.artemis.Component` to różne klasy bazowe. Nie zmieniamy dziedziczenia wszystkich komponentów. Są dwie jawne drogi:

1. Natywny komponent-reference daje encji kontrolowany dostęp do powiązanego GameObjecta lub wybranego core komponentu bez kopiowania pól. Natywne Aspect/mappers filtrują typy takich adapterów, nie automatycznie klasy core.
2. Typowany projektor mapuje dane konkretnego komponentu na natywny komponent ECS lub odwrotnie. Każda mapowana właściwość ma jednego autora: GO_TO_ECS lub ECS_TO_GO. Zmiana kierunku jest jawna na granicy fazy; oba modele nie nadpisują równocześnie np. Transform3D.

Dane transformacji kontrolera/fizyki zapisujemy przez właściwe API/TransformEditPolicy; ECS nie omija ograniczeń Bullet ani VoxelFpsController. Jeśli brak bezpiecznego writer API dla danej właściwości, projektor odrzuca konfigurację, zamiast pisać bezpośrednio do TRS.

Już istnieją `GameObject.onComponentChange` z odpinanym listenerem (ADDED/REMOVED), `Component.onComponentChange`, `enabledRevision` i `activeRevision`. Wykorzystujemy je w pierwszej wersji; unikamy globalnego skanowania wszystkich GameObjectów. Surowe zmiany `position.x = ...` nie emitują zdarzeń — typowany projektor porównuje/kopiuje dane w określonej fazie lub wymaga jawnej invalidacji. Brak refleksyjnej automagii każdego pola.

Core dopuszcza wiele komponentów tego samego typu, Artemis pojedynczy komponent na konkretny typ. Domyślna projekcja wymagająca jednego źródła odrzuca niejednoznaczność; użytkownik może wskazać konkretną instancję/selektor lub jawny agregat. Nie gubimy duplikatów po cichu. Aktywność/enabled mają określoną politykę projekcji/markerów, a użytkownik nadal kontroluje swoje natywne Aspect.

Data sync można wyłączyć i ponownie włączyć per powiązanie. World nadal działa, a cleanup i ważność handle pozostają obsługiwane. Ponowne włączenie robi pełną reconciliację z wybranym autorem danych; nie odgrywa nieograniczonej kolejki historycznych zmian. Zmiany strukturalne są kolejkowane i stosowane w bezpiecznej fazie; callback nie woła rekurencyjnie World.process.

## Testy i pomiary

- Native API: system, encja, mapper, Aspect/subscription/Bag, dodanie/usunięcie komponentu i native deletion na prawdziwym Artemis2.3.0.
- Fazy: dokładnie jedno process, WORLD/UI, pauza/freeze/skala, wyjątek i ponowne wejście; cleanup i wrong-thread rejection.
- Powiązania: GO/entity factory, istniejące obiekty, różne sceny, usunięcie z obu stron, reentrant callbacks, recycled IDs i generacje, start failure i wielokrotne close.
- Projekcje: initial/re-enable reconciliation, add/remove, aktywność/enabled, duplikaty, reference vs copy, single writer, controller/physics policy, brak podwójnego renderowania i brak zaległych listenerów.
- Granice: core i domyślna aplikacja bez zależności modułu; konsument adaptera dostaje native Artemis. Żaden guard nie zakazuje Artemisa globalnie, gdy użytkownik jawnie go wybierze.
- Benchmark porównuje pure ECS, GameObject i mieszankę z określonym procentem powiązań, tę samą liczbą aktualizacji oraz z sync on/off. Oddzielnie alokacje/RAM/czas; brak obietnicy, że most przyspiesza GameObjecty.
- Java25 i desktop CPU najpierw; Android ma osobny build/ART przed deklaracją działania. Brak dowodu to UNVERIFIED.

## Następne etapy konsolidacji

Najpierw moduł opcjonalnego ECS i testowany most. Następnie można odciąć/usunąć historyczny Galaxy Rapid i jego loader/UI/map/plugin, zachowując nową możliwość korzystania z Artemis. Dawne plany pełnego usunięcia Artemisa nie są wykonywane. Optymalizacja pamięci i wspólne dane shaderów pozostają kolejnymi podprojektami z pierwotnego zlecenia.

[Audyty mostu](../../audits/2026-10-17-consolidation/ecs-bridge.md) wskazują ryzyka, a nie gotową implementację. Projekt nie jest jeszcze planem wykonania.
