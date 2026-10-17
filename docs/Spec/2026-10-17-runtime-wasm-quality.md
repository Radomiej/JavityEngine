# Specyfikacja jakości runtime i WebAssembly

Data: 2026-10-17. Zakres bieżący: review i refactoring na bazie `650cfa6`. To zapis kontraktów obecnej implementacji i kryteriów dalszych prac; nie deklaracja wdrożenia całego backlogu.

## Cel użytkownika

Twórca gry lub agent AI powinien uruchamiać grę i lokalne testy autorytatywnego multiplayer bez ręcznej orkiestracji procesów. Podstawą jest prosty `GameObject + Component`, opcjonalny Artemis i wspólny kod gameplay. W przeglądarce uruchamiamy rzeczywisty Javity/libGDX przez TeaVM Wasm GC, a nie drugi renderer geometryczny napisany w JavaScript. JavaScript pozostaje jawnym mostem do API przeglądarki i wybranych bibliotek.

## Aktualne granice modułów

| Obszar | Stan potwierdzony | Poza potwierdzonym zakresem |
| --- | --- | --- |
| `javity-core` | Game/Component i rzeczywisty GdxRenderer w scenie Wasm | Każde publiczne API i każdy asset/efekt na web |
| `experiments/javity-wasm` | Modele 3D, komponent ruchu/animacji, GDX input, resize i zamknięcie sceny w Chromium/SwiftShader | Kontekst loss/recovery, inne przeglądarki, pomiary sprzętowego GPU |
| `experiments/teavm-wasm` | Java lifecycle i jawny most do Yoga/Ammo Wasm | Produkcyjne providery YogaBackend/Physics3D dla Javity |
| `javity-ecs-artemis` | Opcjonalne encje/systemy i jawne, typowane linki z GameObject | Automatyczna migracja całego core do ECS |
| `javity-rooms` + workspace Colyseus | Authority Java, jedna komenda dev, widoki klient/serwer i nadzór procesów | Ogólna replikacja scen, produkcyjny hosting i pełny netcode |

## Kontrakty własności i błędów

1. Model jest współdzielonym zasobem właściciela sceny. ModelRenderer pożycza go; każdy obiekt ma własny ModelInstance. Cleanup obiektów/sceny poprzedza zamknięcie renderera i posiadanych modeli.
2. Udany lub częściowo nieudany start ma terminalne zamknięcie. Wyjątek jednej akcji nie blokuje pozostałych. Błąd inicjalizacji jest pierwotny; błędy cleanupu są suppressed. Ten sam Throwable nie może być dodany jako własny suppressed.
3. Po zamknięciu nie ma kolejnych kroków Game ani rysowania tej sceny. Zewnętrzna pętla backendu może nadal istnieć do zamknięcia aplikacji/karty; nie deklarujemy zatrzymania jej RAF ani zerowych uchwytów całego backendu.
4. Asynchroniczny start mostu ma własną generację i zasoby. Spóźniony Promise po dispose lub recreate nie może publikować stanu nowego właściciela. Każdy udany natywny przydział jest od razu objęty cleanupem; częściowa konstrukcja też jest sprzątana.
5. Referencje pożyczone z Ammo, np. wynik getOrigin, nie są niszczone jak obiekty posiadane. Dispose jest idempotentne; nie może podwójnie zwolnić native pointera.
6. Authority multiplayer pozostaje w Javie. Klient wysyła intencje; server view jest tylko odczytem. Liczba JVM na pokój jest wymienną polityką hosta. SIGKILL nie daje gwarancji reaping przez martwego rodzica; zombie należy do host-init, co test odróżnia od działającej usługi.

## Kontrakt builda

- Sprawdzone parowanie: gdx-teavm 1.6.1 / libGDX 1.14.2 / TeaVM 0.15.0, JDK 25.
- `experiments/javity-wasm` pozostaje poza domyślnym reactorem. Jego build odświeża prawdziwy JAR core przed kompilacją, także gdy wygenerowanego JAR-a brakuje.
- Zachowujemy dokładne reflection metadata dla cache shaderów i jawne pakowanie zasobów. Desktop JNI/LWJGL/Graal/MapLibre Native nie są milcząco przenoszone do przeglądarki.
- `SceneLightingAttribute` zachowuje kolejność Float.compare w przenośnej pętli, ponieważ TeaVM 0.15 nie implementuje Arrays.compare(float[]). Testy zachowują kolejność dla NaN, signed zero i prefiksów.
- Sam build `.wasm` nie jest dowodem obrazu, poprawnego UI, fizyki ani gotowości multiplayer.

## Bramy jakości

- [x] Domyślny `./gradlew build :javity-ecs-artemis:usageSmoke --no-daemon` bez błędów; liczbę testów podawać wraz z informacją o cache.
- [x] Osobne testy polityki cleanupu: kontynuacja, kolejność, częściowy start, suppressed i brak self-suppression.
- [x] Rzeczywista scena Wasm: postęp Game, realne GDX keydown/keyup, resize, brak zaobserwowanego GL/JS error i brak dalszego rysowania sceny po dispose.
- [x] Kontrola pikseli wyklucza DOM i sprawdza konkretne kolory sceny. Wyłączenie draw calls musi dać FAIL, nie PASS dzięki etykiecie.
- [x] Most Yoga/Ammo: opóźniony load + dispose, recreate podczas pending, częściowa awaria i wielokrotne dispose; sprawdzić rzeczywiste obiekty Wasm.
- [x] Dowody przypisane do sourceCommit i hashów; zmiana kodu wymaga aktualnego proofu. Historyczne wyniki nie są przepisywane na nową wersję bez uruchomienia.

Wyniki zamknięcia bieżącego przeglądu będą zapisane przy [checkliście Bugs](../Bugs/2026-10-17-wasm-integration-review.md) i w odpowiednich artefaktach, nie wywnioskowane z samych checkboxów.

## Kolejność dalszych prac

1. Zamknąć potwierdzone błędy z checklisty; utrzymać małe jawne klasy lifecycle i powtarzalne proofy.
2. Browser providery Yoga i fizyki: porównać zachowanie z istniejącymi kontraktami UI/Physics3D, zanim zostaną podpięte do gry. Dotychczasowy test layoutu/kuli nie zastępuje tych implementacji.
3. Rzeczywisty klient Wasm/JS do wspólnego pokoju Colyseus; wspólna konfiguracja gry/sceny, baseline i interpolacja. Nie duplikować authority w kliencie.
4. Host platformy, obraz kontenera, routing i auth/WSS; dopiero po realnym teście deploy. Zachować API niezależne od liczby procesów/JVM.
5. Macierz przeglądarek, DPR, context recovery, urządzenia i GPU profiling. Osobno rozwiązać pełny Android runner ANR; brak tej bramki pozostaje jawną blokadą.

## Poza tym refactoringiem

Nie zastępujemy Artemisa innym ECS, nie zmieniamy publicznego API GameObject, nie usuwamy legacy Galaxy ani nie deklarujemy zgodności pełnego Tailwind/OneJS/Unity. Nie wdrażamy brakujących platform przez zmianę oznaczeń w dokumentacji.
