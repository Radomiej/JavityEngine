# Jakość silnika: porównanie z Unity i jMonkeyEngine

Data: 2026-10-20. Audyt źródeł: `af73022c9dafeaa14b9bfff7e597ec935e8c9cb2`.
Status: badanie i propozycja kolejności; nowe moduły opisane poniżej nie są wdrożone.

## Cel produktu

Twórca gry lub agent ma składać grę z prostych komponentów Java, uruchamiać ją
lokalnie, oglądać rzeczywisty stan sceny i testować authority bez pisania własnego
frameworka. GameObject/Component pozostaje modelem publicznym; Artemis jest
opcjonalnym podsystemem. Istniejące UI, animacje, Feel, mapy, fizyka i streaming
mają współpracować z jednym rdzeniem.

„AAA” oznacza kierunek jakości, nie aktualną certyfikację. Liczba komponentów,
licznik testów i screenshot z programowego GPU nie potwierdzają wydajności,
niezawodności długiej sesji ani jakości grafiki na urządzeniu gracza.

## Referencje sprawdzone w sieci

| Referencja | Co warto przenieść jako zasadę | Zastosowanie w Javity |
| --- | --- | --- |
| [Unity: execution order](https://docs.unity3d.com/Manual/execution-order.html) | Jawne fazy i kontrakty callbacków. | Zachować obecne fazy Game; opisać stabilne punkty integracji zamiast dodawać dowolne callbacki. |
| [Unity: managed memory](https://docs.unity3d.com/Manual/performance-optimizing-code-managed-memory.html) | Mierzyć alokacje w pętli klatki. | Pomiar bytes/frame oraz czasu faz przed i po optymalizacji; nie wnioskować o FPS z samej liczby new. |
| [jME: AppState](https://wiki.jmonkeyengine.org/docs/3.8/core/app/state/application_states.html) | Własność i lifecycle usług aplikacji/sceny. | Rozwijać istniejący GameSystem i scope; bez drugiego menedżera gameplay. |
| [jME: Blender/glTF](https://wiki.jmonkeyengine.org/docs/3.8/tutorials/how-to/modeling/blender/blender_gltf.html) | Czytelny eksport modeli i materiałów. | Import glTF/GLB z walidacją, własnością assetów i instancjami animacji. |
| [jME: render pipelines](https://wiki.jmonkeyengine.org/docs/3.8/core/renderer/render_pipeline.html) | Oddzielne etapy renderowania. | Najpierw jawne punkty dla shadow/world/postprocess/viewmodel/UI, bez implementowania od razu ogólnego render graph. |
| [jME: lights and shadows](https://wiki.jmonkeyengine.org/docs/3.8/core/light/light_and_shadow.html) | Cienie są osobnym kosztem/passem. | Budżetowana mapa kierunkowa przed rozbudowanymi CSM i cieniami wszystkich świateł. |
| [gdx-gltf](https://github.com/mgsx-dev/gdx-gltf) | Istniejący importer glTF i shadery PBR dla libGDX. | Kandydat do adaptera; nie ma potrzeby odtwarzać parsera i shaderów od zera. Wymaga oddzielnej walidacji z naszym libGDX/TeaVM i instancingiem. |

Dokumentacja jME jest referencją możliwości Javy, a nie zobowiązaniem migracji
renderera. Opis platform biblioteki gdx-gltf nie potwierdza działania naszego
backendu TeaVM/Wasm GC.

## Stan i luki

| Obszar | Już istnieje | Najważniejsza luka / następna bramka |
| --- | --- | --- |
| Core | Lifecycle, GameSystem, indeksy sceny, cleanup po błędach, ResourceScope, activation. | Własność hierarchii i odporne dostarczanie zmian komponentów. |
| Authoring | Tree viewer, inspektor GameObjectów, gizma, undo/redo, SceneJson, prefaby. | Spójna ścieżka od przykładu do zapisanej sceny; nie drugi inspektor HTML. |
| Assety | Streaming, lease, cache/budżety, identyfikatory assetów i resolver prefabów. | Import/manifest modeli glTF/GLB oraz kontrakt materiałów i zależności. |
| Rendering | Forward, światła sceny, fog, sky/day-night, static/skinned instancing. | PBR/IBL, cienie i później HDR; jawne fallbacki batchingu. |
| Animacje | Klipy, crossfade, mixer, maskowane warstwy, avatar/FPS/TPP. | Ogólny import rigów, events/root motion; potem IK/retargeting. |
| Świat | Voxele/chunki, foliage, fizyka i kontrolery. | Wspólne pomiary draw calls, cullingu i pamięci dla reprezentatywnej sceny. |
| Multiplayer | Lokalny Colyseus/Java slice i osobny ogólny rdzeń replikacji. | Połączenie ogólnego kodeka z ticket-backed transportem; prediction osobno. |
| Platformy | JVM desktop, ograniczone Android bramki, eksperyment Wasm z ORBIT. | Każda nowa funkcja potrzebuje osobnego dowodu dla docelowego backendu. |

## Potwierdzone problemy kodu

### Q-001: rodzic Transform3D może należeć do innej sceny

`javity-core/src/main/java/org/javity/core/Transform3D.java`, `setParent()`
sprawdza cykle, ale nie scenę. `worldMatrix()` używa następnie obcego rodzica.
Uruchomiona sonda JVM potwierdziła, że dziecko sceny A otrzymuje translację
rodzica sceny B. Pozycja rodzica wynosiła 42 i taka wartość trafiła do macierzy
dziecka. Nie wymagało to startu gry ani kontekstu GL.

Przed poprawką określić kontrakt pre-attachment: testy budują też hierarchie
niepodłączonych transformów. Zakaz wszystkich niepodłączonych rodziców byłby
zmianą kompatybilności. Rozwiązanie musi obsłużyć attach, zmianę rodzica i
usunięcie rodzica; nie tylko jeden warunek w setParent.

### Q-002: błąd listenera pomija pozostałych odbiorców zmiany

`javity-core/src/main/java/org/javity/core/GameObject.java`, `notifyChange()`
przerywa iterację przy pierwszym wyjątku. `addComponent()` wcześniej dołączył
komponent i zaktualizował indeks. Sonda potwierdziła jednocześnie obecność
nowego komponentu i zero dostarczeń do drugiego listenera. Grozi to rozjazdem
obserwatorów, w tym adapterów systemów, wobec faktycznej sceny.

Kierunek poprawki: jawna semantyka zdarzenia po zatwierdzeniu zmiany, dostarczenie
do wszystkich uprawnionych odbiorców, zbieranie błędów bez self-suppression.
Trzeba zachować zasady reentrancy/usunięcia i przetestować callback zmieniający
scenę. Wyjątek nie może sugerować automatycznego rollbacku addComponent.

### Q-003: alokacje przy każdym snapshotcie oświetlenia

`javity-core/src/main/java/org/javity/render/lighting/RenderEnvironment3D.java`,
`snapshot()` tworzy Environment, tablice, Color/Vector3, światła i atrybuty.
`GdxRenderer.renderWorld()` wywołuje tę ścieżkę na klatkę. To potwierdzone
alokacje ze źródeł; wpływu na opóźnienia GC jeszcze nie zmierzono.

Kierunek: pomiar, potem reużywane buforowanie. Zachować obecny kontrakt
atomowości: błędny nowy snapshot nie niszczy poprzedniego. Jedna mutowana
Environment bez walidacji przed publikacją byłaby regresją.

### Istniejący kontrakt, którego nie oznaczamy pochopnie jako błąd

SceneJson po nieudanym odczycie oznacza utworzone obiekty destroy; ich cleanup
jest odroczony do flush/close. Jest to już opisane w prefab pipeline. Zmiana na
natychmiastowy cleanup wymaga transakcji obejmującej wyłącznie tworzony fragment,
bez wymuszonego flush całej aktywnej sceny i uruchamiania cudzych callbacków.

## Warianty rozwoju

1. **Ewolucja obecnego Javity — rekomendowana.** Zachowuje API i istniejące
   dowody desktop/Android/Wasm. Kolejne funkcje trafiają do opcjonalnych modułów.
2. **Backend jMonkeyEngine.** Wartościowy osobny eksperyment tylko przy wyraźnym
   wyborze użytkownika; oznacza mapowanie modeli sceny, materiałów, lifecycle
   i utrzymanie drugiego zestawu testów. Nie zakładamy zgodności z TeaVM.
3. **Nowy renderer i API od zera.** Największy koszt i ryzyko utraty gotowych
   komponentów; obecny audyt nie dostarczył uzasadnienia takiej przebudowy.

## Proponowana kolejność i warunki zamknięcia

1. **Stabilność i pomiary:** Q-001/Q-002, diagnoza czerwonego desktop smoke,
   baseline alokacji/czasów faz, następnie Q-003. Testy błędów, reentrancy,
   pełny reactor i właściwe native smoke; żadnego luzowania golden tolerancji.
2. **Nowoczesne assety:** opcjonalny adapter gdx-gltf, statyczny i skinned model,
   manifest/lease, błędne dane i cleanup. Weryfikacja klipów oraz niezależności
   instancji; określić co zachowuje import, a co konwertuje lub odrzuca.
3. **Materiały i światło:** PBR metallic/roughness, normal/emissive/occlusion,
   przestrzeń liniowa/sRGB i IBL. Jawna integracja z oświetleniem świata i
   osobnym viewmodelem. Materiały nieobsługiwane przez instancing używają
   udokumentowanego fallbacku, zamiast tracić właściwości.
4. **Cienie i postprocess:** kierunkowa mapa cieni z budżetem i flagami
   cast/receive, potem HDR/tone mapping. Każdy pass musi przywracać stan GL
   i odzyskiwać zasoby po utracie kontekstu.
5. **Character workflow:** eventy animacji, root motion z jawnym właścicielem
   transformacji/fizyki, przykładowy avatar z FPS/TPP. IK i retargeting później.
6. **Narzędzia i demo:** jedna scena referencyjna: postać, narzędzie, teren,
   roślinność, światło, UI, streaming i zapis. Ta sama mała gra do testu
   authority; unikamy tworzenia kolejnego izolowanego demo dla każdej flagi.

Warunek wydajności dotyczy wskazanego sprzętu i sceny: mierzyć p50/p95/p99
czasu klatki, draw calls, bytes/frame, pamięć assetów oraz zachowanie podczas
streamingu. Cel 60 FPS oznacza budżet 16,67 ms, nie wynik uzyskany przez ten
audyt. Software GL służy do poprawności; nie certyfikuje szybkości GPU.

## Bieżąca weryfikacja i granice

- Dwie sondy CPU powyżej uruchomiono na JDK 25 przeciw klasom bieżącej bazy;
  obie potwierdziły opisywane zachowanie. Nie zmieniano kodu produktu.
- CI dla `af73022`: Android i ORBIT Wasm success; Desktop failure w kroku
  Native graphics and audio smoke; późniejsze screenshot gates pominięte.
  Nie przypisujemy tego automatycznie do historycznego BUG-UI-001. Pobranie
  pełnego job logu nie powiodło się; przyczyna wymaga dalszej diagnozy.
- Brak nowego pełnego builda lub certyfikacji fizycznego GPU/Androida w tym
  audycie. Poprzednie testy nie są oznaczone jako świeżo uruchomione.
- Product Design: zidentyfikowano ścieżkę tworzenia gry jako cel oceny. Narzędzie
  Browser wymagane przez plugin nie jest dostępne w tej sesji, dlatego nie
  wykonano audytu UX ze świeżymi screenshotami i nie deklarujemy jego wyniku.
- Opracowanie szczegółowego projektu nowego zakresu oczekuje na wybór kierunku
  w pytaniu do użytkownika. Powyższe to badanie i kolejka, nie zatwierdzona
  specyfikacja nowych modułów ani deklaracja gotowości AAA.
