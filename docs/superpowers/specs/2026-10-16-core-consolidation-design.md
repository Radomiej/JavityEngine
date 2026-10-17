# Konsolidacja JavityEngine: jeden core i spójny rendering

> Aktualizacja zakresu: użytkownik zlecił nowy opcjonalny podsystem Artemis obok GameObject. Aktualny [projekt mostu ECS](2026-10-17-optional-artemis-design.md) zastępuje kolejność i decyzje A1 poniżej; A2/A3 pozostają roadmapą. Opis wariantów A/B i opt-in launchera jest zapisem wcześniejszej propozycji, nie instrukcją usuwania Artemisa ani aktualnym planem wykonania. Kod nie został dotąd zmieniony.

2026-10-16, Europe/Warsaw. Baza audytu: `48814ae`. **Projekt do przeglądu; opisane zmiany nie są wdrożone.** Użytkownik zlecił konsolidację i pracę agentów Luna pod nadzorem. Trzy audyty są ukończone; zmiany kodu wymagają jeszcze przeglądu tego projektu.

## Cel i kryterium prostoty

Jedynym modelem nowego gameplay pozostaje `org.javity.core.Game`, `GameObject` i `Component`, zgodnym z java-lab. Typowe zastosowanie polega na dodaniu gotowych komponentów i jawnie potrzebnych systemów. Fizyka, voxele, mapy, assety, UI i providery platform pozostają opcjonalnymi modułami zależnymi od tego core. Użytkownik nie powinien wybierać między nowym lifecycle a Artemis/Galaxy Rapid podczas tworzenia nowej gry.

Wymagania użytkownika: spójny core, aktualny libGDX, spójne światła i shadery, mniejszy koszt pamięci, usunięcie starych rzeczy i sensowne testy. Przyjęte ograniczenia: zachować działanie współczesnych przykładów i własność zasobów; mierzyć zmiany wydajności; rozdzielać desktop, build APK i faktyczny runtime Androida. Nie traktować liczby modułów jako miary prostoty API.

## Stan potwierdzony

- Nowy core i współczesne usługi nie wymagają Artemis/Galaxy Rapid. `javity-core` zawiera też rendering libGDX; nie jest modułem pozbawionym grafiki.
- `javity-desktop -> javity-engine -> javity-backend` nadal wciąga stary stos przez `DemoApplication`/`DemoScene` i `--legacy`. Osobna stara gałąź to `javity-map -> javity-engine + javity-net`.
- `javity-android`, XML UI i generator projektów są poza obecnym reactorem, ale zawierają użytkowników starych API. Ich usunięcie oznacza świadome wycofanie kompatybilności.
- libGDX **1.14.2 jest najnowszym stabilnym wydaniem** według oficjalnego Maven Central i GitHub Releases sprawdzonych 2026-10-16. Zostaje 1.14.2; nie wprowadzamy sztucznego podbicia wersji.
- `gdx-jnigen-loader` 2.5.2, `gdx-ai` 1.8.2 i LWJGL 3.4.3 mają osobne wersje. Audyt deklaracji nie zastępuje sprawdzenia rozwiązanych classpathów i dystrybucji.
- Snapshot świateł, zapytania sceny oraz przygotowanie batchy tworzą nowe obiekty w gorących ścieżkach. To dowody ze źródeł, jeszcze bez pomiaru bajtów/klatkę.
- Nie wykazano wycieku między scenami. Część cache jest już ograniczana/przycinana; nie należy zastępować jej arbitralną eksmisją.
- Ostatni checkpoint: 805 testów / 91 suite i rzeczywiste desktop GL20/GL30 PASS. W tym audycie nie uruchamiano ponownie builda ani renderowania.

Źródła: [core i legacy](../../audits/2026-10-16-consolidation/core-unification.md), [rendering/pamięć](../../audits/2026-10-16-consolidation/render-memory.md), [zależności/platformy](../../audits/2026-10-16-consolidation/dependencies.md).

## Warianty i rekomendacja

| Wariant | Efekt | Koszt |
| --- | --- | --- |
| **A — jeden domyślny core, legacy jako jawny opt-in** | Czysta współczesna aplikacja i classpath; stary kod pozostaje osobnym narzędziem migracji | W repo nadal są źródła zgodności; ich fizyczne usunięcie wymaga wycofania starych API |
| B — całkowite wycofanie legacy | Usunięcie drugiego stosu ze źródeł i builda | Utrata JGameObject/JComponent, starych JSON/Kryo, Spine, XML/generatora i historycznych adapterów mobile; brak automatycznej konwersji |
| C — scalenie wszystkiego w jeden artefakt | Jeden duży pakiet zależności | Cięższa aplikacja, wymieszane zależności natywne/platformowe i brak korzyści dla lifecycle; odrzucony |

Rekomendacja pierwszego etapu: A. Na pytanie o całkowite usunięcie zgodności użytkownik może wybrać B; do tego czasu nie zakładamy zgody na utratę historycznych scen. Stare zależności znikają z głównej aplikacji już w A. Usuwanie zbędnych współczesnych zależności wymaga dowodu braku użyć; nie usuwamy wykorzystywanego Spine/Artemis z modułu zgodności przez samo skasowanie deklaracji Gradle.

## Podział pracy

To trzy kolejne podprojekty. Ten dokument konkretyzuje **A1: jeden domyślny core i izolacja zgodności**. A2/A3 są kierunkiem dalszego refaktoru i dostają własne mierzalne projekty przed implementacją.

1. **A1 — czysty graf domyślnego runtime.** Oddzielenie starego dema i modułów legacy; automatyczna kontrola zależności, aktualizacja guide i migracji.
2. **A2 — alokacje i retencja.** Baseline, reuse snapshotów/buforów, stabilne zapytania sceny i diagnostyka pamięci.
3. **A3 — wspólne dane światła i warianty shaderów.** Zgodność zwykłego/static/skinned renderowania oraz odzyskiwanie zasobów; dopiero wtedy zdjęcie fallbacku instancingu dla obsługiwanych nowych świateł.

Metoda wybrana przez użytkownika: implementerzy Luna, nadzór głównego agenta. Zmiany wspólnych plików core/renderera następują sekwencyjnie; audyty mogą działać równolegle. Każdy podprojekt ma testy i niezależny przegląd przed integracją.

## A1: kontrakt builda i publiczne API

### Domyślny build

`./gradlew build`, `:javity-desktop:run` i desktopowa dystrybucja używają wyłącznie współczesnego core i jawnych opcjonalnych usług. Domyślne settings nie dołączają `javity-backend`, `javity-engine`, `javity-map`, `javity-net` ani nowego launchera zgodności. Desktop nie ma zależności od ich artefaktów. Android pozostaje oddzielnym opt-in `-PjavityAndroid=true`, bez konieczności SDK w domyślnym desktopowym buildzie.

Współczesne tryby --voxels, --physics3d, --environment, --asset-streaming, --inspector, --map, --runtime, Shapes, UI/JS i MapLibre zachowują funkcje oraz dotychczasowe jawne ograniczenia platform.

### Zgodność opt-in

Projektowana flaga `-PjavityLegacy=true` dołącza historyczne cztery moduły i osobny `javity-legacy-desktop`. Ten launcher przejmuje istniejące `DemoApplication`/`DemoScene`, zależy od engine oraz używanego przez nie nowego UI/Yoga, ma własny main i natywne zależności desktopowe. Nie zależy od współczesnego `javity-desktop` tylko po to, aby uzyskać launcher.

Projektowane polecenia: `./gradlew -PjavityLegacy=true :javity-legacy-desktop:run` oraz osobny `legacySmoke`. Domyślny `--legacy` kończy się czytelną informacją o nowym poleceniu przed utworzeniem okna/zasobów GL; nie uruchamia po cichu ModernDemo. Nowoczesny launcher nie ma importu ani refleksyjnego ładowania starego engine.

Zachowane klasy legacy nie zmieniają pakietów/API ani algorytmów serializerów w tym etapie. Konsument starych projektów musi jawnie włączyć zgodność lub zadeklarować jej artefakt. Jest to zmiana organizacji builda i poleceń; zostanie wyraźnie opisana w README i migration-api. Nie obiecujemy importu starych zapisów przez SceneJson v1.

### Usunięcia i granice

Usuwamy zależność legacy z nowego desktopu, starą gałąź wyboru dema z jego kodu oraz zbędne default settings/version declarations, jeśli nie są już potrzebne konfiguracji opt-in. Zachowujemy przypisanie wersji Artemis wyłącznie tam, gdzie potrzebuje go opt-in. Przeniesione pliki dema mają jednego właściciela; nie zostają duplikaty.

`javity-net` pozostaje wyłącznie zależnością starego pluginu map w opt-in. Nie przenosimy starego downloadera do nowego core. Nie usuwamy XML UI/generatora/legacy Android ani ich publicznych API w A1. Osobna lista wycofania pokaże, jakie możliwości trzeba zastąpić lub jawnie porzucić przy wariancie B. Zachowujemy licencje aktywnych źródeł i assetów; usunięcie zależności nie uprawnia do skasowania notices dla nadal używanego zasobu.

## A1: weryfikacja i akceptacja

- Domyślne settings/project graph oraz rozwiązany desktop runtimeClasspath nie zawierają czterech modułów legacy, Artemis, Spine, Kryo ani starego eksportu gdx-ai. Źródła nowej aplikacji nie importują legacy pakietów.
- Test architektury sprawdza rzeczywisty graf/artefakty; nie polega jedynie na wyszukaniu słowa „Artemis” w README. Testy zachowania Game/Component pozostają zachowane.
- `installDist` nowej aplikacji nie zawiera legacy JAR-ów. Świeży build i uruchomienie dystrybucji weryfikują, że nie korzystamy z poprzednio wygenerowanego katalogu.
- Osobny opt-in build przechodzi testy backend/engine/map/net i rzeczywisty `legacySmoke`; flaga nie przywraca legacy do classpathu nowego desktopu.
- Odrzucenie `--legacy` testowane bez GL, z właściwą wskazówką migracji. Brak flagi nie powoduje „Project not found” w domyślnych zadaniach.
- Współczesne bramki CPU i native: pełny reactor, smoke/runtime/map, voxel/inspector, środowisko/sky, avatar/Feel, statyczny i skinned instancing GL20/GL30; MapLibre/Gorzów po zmianach pakowania ich adaptera. Nie powtarzamy niezmienionych testów bez powodu po zaliczeniu danej bramki.
- Android: osobny build debug/release i audyt ABI/zależności bez legacy. Runtime/ART/EGL dopiero po osobnym uruchomieniu; brak takiego dowodu ma status UNVERIFIED.
- Zapisujemy porównanie listy i sumy bajtów JAR-ów przed/po dla tego samego wariantu dystrybucji. Mniejsza dystrybucja nie stanowi pomiaru RAM ani czasu klatki.
- README, AGENTS, engine-guide i migration-api opisują jeden rekomendowany core, właściwe polecenia i odrębną zgodność; historyczne wyniki testów pozostają historyczne.

## A2: kontrakty do zachowania przy optymalizacji pamięci

Najpierw osobny benchmark desktopowy mierzący alokacje wątku właściciela po warm-up dla stałych scen, wielu kamer i zbiorów świateł. Osobno mierzymy alokacje CPU, zatrzymaną pamięć, szacowane bajty GPU/liczbę buforów/programów i czasy klatek. Benchmark ma te same assety, liczbę obiektów i ustawienia przed/po; bez deklaracji przyspieszenia z samego spadku liczby `new` w kodzie.

Pierwszy cel to RenderEnvironment3D i scratch renderera. Reuse zachowuje deterministyczny wybór świateł, copied values i atomowość: nieudana walidacja nie zmienia poprzedniego snapshotu. Nie można po prostu mutować publikowanych świateł podczas walidacji. Obecny borrowed snapshot jest ważny do następnego wywołania; nie zmieniamy tego po cichu. Nowa iteracja sceny wymaga określenia kolejności, podtypów, destrukcji/mutacji i reentrancy; istniejące `getObjectsWith` nie staje się ukrytą mutable view.

Potem bufory transformacji/palet, scratch kolekcje batchy i zmienne materiały. Unieważnianie materiałów/geometrii ma działać także przy mutacji istniejącego obiektu — identity nie zastępuje rewizji/content key. Pruning przy zmianie sceny/widoku, właściciel GL i pożyczone modele/tekstury pozostają zachowane. Budżety retencji dopiero po pomiarze szczytów i określeniu zachowania na braku miejsca.

## A3: kierunek spójnego światła/shaderów

Jeden opis danych światła sceny i jawnych profili passu ma zasilać zwykłe, statyczne i skinned world draws. Wspólne są walidacja, limity, równania światła/mgły, diagnostyka i reguły lifetime/recovery. Sky, world, viewmodel i UI zachowują swoje role oraz kolejność. Viewmodel ma jawny profil bez world fog/sky i zachowuje czytelność rąk.

Instancing nie zaczyna obsługiwać nowych świateł przez usunięcie SceneLightingAttribute. Do czasu udowodnienia zgodności pikseli, alpha-test, tekstur, skórowania i recovery zachowuje bezpieczny fallback. Nowe warianty muszą odświeżać rzeczywiste uniformy/atrybuty; niezmienione lokalizacje po odtworzeniu VAO są osobnym przypadkiem regresji. Raw compile/link failure sprząta częściowe zasoby. Nie wprowadzamy globalnych prefixów/pedantic ani refleksyjnego dostępu do gameplay.

Shaderowe usługi mają wspólne zasady właściciela wątku, borrowed/owned, cleanup i diagnostyki. Nie zakładamy, że shader SDF/UI, sky i skinned model mają identyczny program albo identyczny stan GL. PBR, cienie, HDR i foliage nie są częścią konsolidacji.

## Stan po tym dokumencie

Audyty i projekt są gotowe. Nie zmieniono kodu produktu, zależności ani deklarowanych wersji. Nie wykonano nowych pomiarów wydajności. Po przeglądzie projektu A1 powstaje szczegółowy plan wykonania, a następnie wdrożenie z agentami Luna pod nadzorem. A2/A3 pozostają następnymi podprojektami; nie oznaczamy ich jako zrobionych na podstawie tego audytu.
