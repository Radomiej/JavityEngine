# Review jakości Wasm i integracji — 2026-10-17

Bazą przeglądu jest `650cfa6`. Zakres: oba spike’i Wasm, ich buildy/proofy, zmiana przenośnego porównywania świateł w core oraz statyczny przegląd lokalnego Colyseus/Java i opcjonalnego Artemisa. To nie jest deklaracja pełnego audytu całego repozytorium. Dwa niezależne przeglądy były tylko do odczytu; poniżej zapisano wszystkie zgłoszenia wraz z ustaleniami kontrolera.

## Checklist napraw

- [x] **BUG-WASM-001 / P2 — test obrazu akceptował pusty renderer.**
  - Miejsce: `experiments/javity-wasm/tools/chromium-proof.py`, poprzednia analiza pełnego screenshotu strony.
  - Reprodukcja: wyłączyć `drawArrays`, `drawElements` i warianty instancing w `WebGL2RenderingContext`. Stary test nadal przechodził z 152 kolorami, pochodzącymi z etykiety HTML.
  - Naprawa: screenshot samego canvas z ukrytą etykietą; wymagane piksele zielonej geometrii i pomarańczowej kuli. Oddzielny screenshot strony jest wyłącznie prezentacją.
  - Bramka: `--disable-draw --evidence-dir <tymczasowy katalog>` musi zakończyć się błędem. Po naprawie kontrola wykazała tylko 3 kolory i została odrzucona; normalny WebGL przeszedł.
- [x] **BUG-WASM-002 / P2 — wyjątek podczas dispose blokował zwolnienie pozostałych zasobów.**
  - Miejsce: `JavityWasmDemo.dispose()`, wcześniejsze sekwencyjne `game.close()` / `renderer.dispose()` / modele.
  - Przyczyna: pierwszy wyjątek przerywał kolejne wywołania, mimo ustawienia terminalnego `disposed`.
  - Naprawa: wspólna kolejność cleanupu próbuje wszystkich akcji, zachowuje pierwszy błąd i dodaje pozostałe jako suppressed. Referencje sceny są czyszczone w `finally`.
  - Bramka: `SceneCleanupTest` sprawdza kontynuację, kolejność, pierwotny wyjątek i ochronę przed self-suppression. Dla starej semantyki 3 z 4 testów były czerwone; poprawiona wersja przechodzi 4/4.
- [x] **BUG-WASM-003 / P2 — częściowa inicjalizacja sceny nie miała rollbacku.**
  - Miejsce: `JavityWasmDemo.create()`.
  - Reprodukcja kontraktu: błąd inicjalizacji po przydzieleniu wcześniejszych zasobów nie wywoływał ich cleanupu.
  - Naprawa: `createScene()` jest otoczone rollbackiem; błąd konstrukcji pozostaje główny, wcześniej przydzielone zasoby są zamykane, a referencje czyszczone.
  - Dowód: test polityki cleanupu częściowej inicjalizacji oraz przegląd miejsca wywołania. Nie jest to fault-injection rzeczywistego braku pamięci GPU.
- [x] **BUG-BRIDGE-001 / P2 — dispose podczas ładowania Ammo pozostawiał spóźnione zasoby.**
  - Miejsce: `experiments/teavm-wasm` — eksport Java `create()` i asynchroniczny host JS.
  - Reprodukcja: zablokować Promise `Ammo()`, wywołać Java `dispose()`, następnie rozwiązać Promise. Bazowa wersja tworzyła 13 obiektów i zwalniała tylko wektor grawitacji: 12 pozostawało żywych po zamknięciu.
  - Wymagane: własność przypisana do generacji startu; anulowany start nie może tworzyć/publikować zasobów ani nadpisywać kolejnego startu. Konstrukcja częściowa musi być exception-safe; dispose idempotentne.
  - Wynik: niezależny przebieg Chromium z rzeczywistym Yoga/Ammo zaliczony; sesje mają własną generację i natychmiast rejestrują posiadane zasoby. Dowód: `experiments/teavm-wasm/evidence/lifecycle.json`.
- [x] **BUG-BRIDGE-002 / P2 — szybki recreate pozostawiał zasoby poprzedniej generacji.** Bazowy test odwróconej kolejności rozwiązywania Promise: 26 konstrukcji, 14 zwolnień. Generacja sesji blokuje spóźniony start; regresja Chromium przechodzi.
- [x] **BUG-BRIDGE-003 / P2 — błąd konstruktora Ammo pozostawiał częściowy przydział.** Bazowo 11 konstrukcji, 5 zwolnień przy awarii `btRigidBodyConstructionInfo`. Natychmiastowa rejestracja własności i cleanup w odwrotnej kolejności zamykają wcześniejsze zasoby; regresja przechodzi.
- [x] **BUG-BRIDGE-004 / P3 — tick nieaktywnego hosta dereferencjonował null.** Wykryte podczas review pierwszej wersji refactoringu, przed commitem. Dodano guard przed create i po dispose; regresja przechodzi.
- [x] **BUG-BRIDGE-005 / P3 — nieudane niszczenie grawitacji raportowało sukces.** Wykryte podczas review refactoringu. Host raportuje teraz błąd i próbuje pozostałych zwolnień. Nie ponawia niepewnego native destroy; test nie dowodzi zwolnienia wskaźnika, którego destroy rzucił wyjątek.
- [x] **BUG-BRIDGE-006 / P2 — odrzucony anulowany loader powodował unhandled rejection.** Wykryte podczas review refactoringu i odtworzone po przywróceniu starego rethrow. Błąd anulowanej generacji jest obsłużony; błąd aktywnego startu pozostaje dostępny przez `whenReady()`. Obie ścieżki sprawdzone w Chromium.
- [x] **BUG-QA-002 / P3 — po błędzie pozostawał poprzedni artefakt PASS.** Potwierdzone przeglądem ścieżki zapisu proofu. Teraz także nieudany przebieg nadpisuje `runtime.json` z `success: false`; kontrola negatywna potwierdza zapis bieżącego FAIL.
- [x] **BUG-DOC-001 / P3 — niejasne minimum Node.**
  - Miejsce: `docs/local-multiplayer.md`.
  - Naprawa: odróżniono minimum paczek Node >=22 od faktycznie sprawdzonego Node 24. Nie deklarujemy osobnego testu wersji 22/23.

## Refactoring bez zmiany kontraktu

- [x] Nazwane komponenty `KeyboardMovement` i `Hover` zamiast anonimowych klas przechwytujących całą aplikację; komponenty przechowują konkretny Transform3D.
- [x] Wyodrębnione `createScene`, `releaseScene`, polityka `SceneCleanup` i kontrola pierwszego błędu GL.
- [x] Etykieta strony w osobnym `web/label.html`; aktualizuje się także przy powtórnym buildzie, bez wielkiego literału HTML w Gradle.
- [x] Proof rozdziela uruchomienie, kontrolę pikseli i cleanup; nie wybiera przypadkowego pliku `index.html` i zapisuje bieżące niepowodzenie zamiast zostawiać poprzedni PASS jako najnowszy wynik.

## Znane otwarte blokady spoza bieżącej naprawy

- [ ] **BUG-ANDROID-001 / P2 — łączny runner instrumentacyjny APK kończy się ANR.** Historyczny, potwierdzony problem uruchomienia zestawu; pięć przypadków przeszło osobno. Nie ustalono, że przyczyna leży w silniku, zamiast w środowisku/runnerze. Bramka zamknięcia: stabilny pełny przebieg na odtworzonym środowisku oraz analiza ANR, bez zastępowania go sumą pojedynczych PASS. [Stan i logi](../android-status.md). Nie ponawiano Androida w tym przeglądzie.

## Braki i ograniczenia, które nie są potwierdzonymi błędami

- Browser YogaBackend i provider fizyki Ammo nie są jeszcze wpięte jako pełne providery Javity. Stary bridge jest osobnym eksperymentem.
- SwiftShader/Chromium przy DPR=1 nie potwierdza sprzętowego GPU, DPR=2, Firefox/Safari/mobile ani wydajności.
- Brakuje proofu odtwarzania kontekstu WebGL, liczników zerowych uchwytów GPU po zamknięciu i pełnej walidacji assetów/UI.
- Snapshot viewer multiplayer dotyczy schematu przykładowego pokoju; nie jest replikacją dowolnych komponentów. Zdalny deploy, auth/WSS, prediction i reconnect wymagają osobnych prac.
- Build/proofy wykonano na Linux. Wrapper Windows i jego ścieżki nie mają osobnego testu; nie oznaczono ich jako obsługiwanej macierzy platform.

## Wynik review integracji

W statycznym przeglądzie opcjonalnego ECS i kodu lokalnego Colyseus/Java nie potwierdzono nowego błędu runtime. To opis zakresu, nie dowód braku wszystkich usterek. Dotychczasowe hashe dowodów integracji pasowały do badanych plików; nie powtarzano niezmienionych testów sieciowych tylko dla korekty dokumentacji.

## Granica dowodów lifecycle

Siedem scenariuszy przeglądarkowych sprawdza tożsamość i własność wrapperów rzeczywistych obiektów Yoga/Ammo. Nie jest to pomiar odzyskania liniowej pamięci Wasm. Drugi agent niezależnie przejrzał końcowy kod obu eksperymentów; nie potwierdził dodatkowego błędu w tym zakresie.

## Końcowe bramki

- [x] Domyślny build i ECS usage smoke: PASS, 113 zadań (6 wykonanych, 107 up-to-date), 871 przypadków w 99 zestawach XML, bez błędów. To inventory obejmujące cache, nie 871 świeżych wykonań.
- [x] Osobny build Wasm oraz 4 testy cleanupu: PASS.
- [x] Oba happy-path proofy Chromium oraz siedem regresji bridge: PASS.
- [x] Kontrola negatywna renderera: oczekiwany FAIL, 3 kolory, zapis `success: false`.
- [x] Hashe źródeł i artefakty w `experiments/javity-wasm/evidence/verification.json` oraz `experiments/teavm-wasm/evidence/quality-verification.json`.
