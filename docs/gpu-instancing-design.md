# GPU instancing i batching animacji — zaakceptowany projekt

2026-10-16. Użytkownik zaakceptował wariant A→B→C. Nowe wymaganie użytkownika: instancing modeli i batching animowanych postaci, z wykorzystaniem jego projektu referencyjnego. **To projekt, nie wdrożona funkcja.** Kod wskazanego projektu referencyjnego nie jest jeszcze dostępny do porównania; nie deklarujemy portu ani równoważności jego algorytmu.

## Co mamy

`GdxRenderer` używa ModelBatch, ale jego flush wywołuje shader.render dla każdego Renderable. Wspólna siatka albo jeden ModelBatch nie oznaczają automatycznie jednego draw call. `ModelPoseAnimator` ma osobny sampler i kanoniczny ModelInstance dla właściciela; renderer nie przesuwa zegara animacji. AvatarView wybiera części i sockety z tej samej pozy. Nowy batching musi zachować te kontrakty.

libGDX1.14.2 udostępnia Mesh.enableInstancedRendering, instance attributes i instanced draw. Wywołanie tej metody zmienia Mesh, więc nie wolno bezwarunkowo włączyć instancingu na pożyczonej siatce współdzielonej ze zwykłym rendererem. Stan instance bufferów i VAO musi być odtwarzany po utracie kontekstu.

## Cel i założenia

Jedno API GameObject/Component, opcjonalna optymalizacja renderowania. Gra nadal steruje osobnymi obiektami, animatorami, zdarzeniami i czasem. Instancing grupuje wyłącznie zgodne renderables, bez zmiany logiki gry lub kolejności przezroczystości. Początkowy cel: powtarzalne statyczne obiekty oraz tłum Foxów z istniejącego licencjonowanego fixture.

Zakładamy desktop OpenGL3.3+ albo odpowiednie rozszerzenia oraz Android GLES3.0+ po osobnym teście urządzenia. Sama obecność Gdx.gl30 na desktopie nie dowodzi wszystkich wymaganych funkcji. Sprawdzać rzeczywiste capabilities i limity shaderów/tekstur/atrybutów. GL20 oraz niekwalifikujące się obiekty zachowują zwykłą ścieżkę i powód odmowy w diagnostyce.

## Podejścia

1. Tylko instancing statyczny: mała zmiana i dobry pierwszy etap; nie rozwiązuje kosztu kości tłumu.
2. **Rekomendowane:** statyczny instancing → współdzielenie identycznych wyników pozy CPU → instanced skinning z paletami kości w teksturze. Zachowuje aktualne animacje, warstwy i zdarzenia; daje osobne, mierzalne korzyści każdego etapu.
3. Bake klipów/VAT i pełny GPU animation player: dobry kandydat dla dalekich tłumów, ale zmienia pipeline assetów oraz możliwości crossfade/masek/socketów. Osobny późniejszy zakres, nie automatyczny zamiennik ModelPoseAnimator.

## A. Statyczne GPU instancing

Grupowanie po dokładnej zgodności geometrii (MeshPart i zakres indeksów), layoutu vertex, materiału/tekstur, programu oraz stanu renderowania i otoczenia danego passu. Sama nazwa materiału nie wystarcza. Kopie materiałów muszą być porównywane po wspieranych wartościach albo używać jawnego wspólnego immutable material key; osobny tint może być per-instance tylko jeśli dany shader go obsługuje.

Per-instance dane: macierz świata i opcjonalny tint. Początkowo opaque i jawny alpha-test, bez mieszania dowolnej alpha transparency. Viewmodel, własne nieobsługiwane shadery, nieobsługiwane atrybuty i efekty zostają na fallback. Ujemne/niejednorodne skale i shear wymagają poprawnej macierzy normalnej i winding; pierwszy etap może je jawnie odrzucać do fallback zamiast błędnie oświetlać model.

Renderer posiada przygotowaną geometrię lub niezależne bindingi instancji — bez mutowania borrowed Mesh i bez per-frame kopiowania stałych wierzchołków. Dane instancji mają ograniczoną pojemność, stabilne sloty i aktualizację zmienionych zakresów. Zniszczenie obiektu usuwa slot bez pozostawiania ghost instance. Przy przepełnieniu grupę dzieli się na kilka draw calls, a nie gubi obiektów.

Culling jest liczony per-instance przed pakowaniem; bounds uwzględniają hierarchię i prezentację. Stats raportują submitted/visible/culled/instanced/fallback, grupy, draw calls i upload bytes. Jeden draw call oznacza jeden zgodny MeshPart/material/pass, nie cały dowolny model z wieloma materiałami.

## B. Animation batching na CPU

Niemutowalne dane rigu i klipów można współdzielić między instancjami. Wynik samplingu/kompozycji ma scene/frame-local, bounded cache dla **dokładnie tej samej pozy**. Klucz uwzględnia tożsamość i rewizję danych rigu/klipów, base clip lub mikser, czasy próbek, prędkość przez wynikowy czas, parametry miksera, aktywne maski/warstwy/wagi i stan crossfade z zamrożonym outgoing pose. Hash bez weryfikacji tożsamości/treści nie wystarcza.

Nie kwantyzować czasu po cichu. Kwantyzowany animation LOD może być osobnym opt-in z podaną tolerancją. Pauza, seek, warstwa zmieniona po kroku oraz usunięcie/rebind renderera muszą unieważniać odpowiedni wpis.

Każda postać nadal ma własny playback, zegar i callbacki: marker, loop i completion wywołują się osobno. Współdzielony wynik jest niemutowalny; lokalne Node/ModelInstance i sockety otrzymują własny stan, nie współdzielone mutable Matrix4. Widoczność AvatarView nie zmienia pozy. Pierwszy etap ma wykazać mniejszą liczbę rzeczywistych wywołań samplera przy takiej samej liczbie zdarzeń i identycznych pozach.

## C. Instanced skinning GPU

Grupa zawiera zgodne: rig/układ palety, skinned MeshPart, materiały i wariant programu. Każda instancja przesyła własną transformację świata oraz indeks wiersza palety. Różne klipy/czasy mogą pozostać w tej samej grupie, jeśli korzystają z tego samego rigu/geometrii i programu.

Rekomendowany layout: float texture z czterema texelami RGBA na macierz kości, width=4×boneCount, jeden wiersz na unikalną pozę. Vertex shader pobiera macierze przez texelFetch i miesza je wagami źródłowych wierzchołków. Format, wielkość tekstury, liczba vertex texture units i liczba atrybutów są sprawdzane przed kwalifikacją. Nie opierać limitu tłumu na uniform array DefaultShader. Macierze i normalne muszą odtworzyć konwencję bieżącego skinningu i bind pose.

Paleta jest przesyłana raz na unikalną pozę, a nie raz na draw ani per obiekt przy identycznym wyniku. Instancje o różnych pozach dostają różne wiersze. Limit palet/rozmiaru tekstury dzieli grupy lub kieruje nadmiar do fallback; stara generacja slotu nie może zostać użyta po zamknięciu sceny.

Nie zastępować per-bone cullingu/regionów fikcyjnym jednolitym modelem: części i prepared avatar regions tworzą zgodne osobne grupy. Sockety nadal wynikają z kanonicznej pozy CPU. Shader batching musi obsługiwać aktywne otoczenie/światła albo jawnie oddać obiekt zwykłemu rendererowi; nie wolno tracić fog, tekstur, tint czy materiałów dla oszczędności draw call.

## Własność i lifecycle

Zasoby źródłowe Model/Mesh/Texture pozostają pożyczone. Renderer batchingu posiada wyłącznie swoje instance buffers, przygotowaną geometrię, programy i palette texture. Wyłączyć rysowanie i zakończyć batch przed zwolnieniem zasobów lub lease. Częściowa alokacja/kompilacja ma cleanup; wymiana sceny kasuje cache i sloty.

Odtworzenie EGL wymaga ponownego uploadu instancji/palet oraz konfiguracji VAO/divisorów i lokalizacji shaderów. Używać przechowywanych danych CPU, nie starych nazw GL. Dotychczasowe naprawy libGDX1.14.2 nie są automatycznym dowodem poprawności nowego pipeline. Przeplatanie instanced i zwykłego renderu ma przywracać stan, również po błędzie.

## Weryfikacja i demo

- Statyczny: porównanie zwykłego i instanced renderu tego samego układu, transformacji, tekstur i światła; np.256 zgodnych widocznych instancji jednego MeshPart daje jeden instanced draw, a nie256.
- Culling i capacity: widoczna liczba obiektów identyczna, usuwanie/reuse slotów bez artefaktów, brakujące capabilities prowadzą do poprawnego fallback.
- Animacja: Fox z trzema klipami, crossfade, maską ogona i mikserem. Porównać skinned vertex/pozę oraz piksele z już zweryfikowanym zwykłym rendererem.
- Wspólna poza: wiele postaci, jeden wynik samplingu/paleta, ale osobne markery i completion każdego właściciela.
- Różne pozy: co najmniej dwa czasy/klipy tej samej geometrii w jednym GPU batchu, bez przenikania animacji między instancjami.
- Pauza WORLD/playback, speed, seek, warstwy, zmiana sceny, usunięcie renderera i dwa widoki w jednej klatce nie psują zegarów ani cache.
- Pomiary dla64/256/1024 postaci: draw calls, unikalne pozy, sampler calls, palette uploads/bytes, CPU i GPU timings jeśli dostępne; warmup, stała kamera i ten sam seed. Liczby FPS z Mesa software nie są obietnicą wydajności telefonu.
- GL20 fallback, desktop GL3.3+ i osobno Android GLES3 z prawdziwą utratą kontekstu. Brak testu danej platformy oznacza unverified.

Demo powinno pokazywać zwykłą/instanced ścieżkę i stats side-by-side, zachować licencję Foxa oraz powtarzalne zrzuty i raport. Kryterium sukcesu: taka sama animacja i obraz w określonej tolerancji oraz udowodnione ograniczenie draw calls/samplingu, nie samo glDrawElementsInstanced w kodzie.

## Plany wykonania

Wariant A→B→C zaakceptowany przez użytkownika 2026-10-16. Plany wykonania przygotowane do przeglądu; implementacja nie rozpoczęła się:

1. [A — statyczny instancing](superpowers/plans/2026-10-16-static-gpu-instancing.md).
2. [B — dokładny cache póz](superpowers/plans/2026-10-16-exact-pose-batching.md).
3. [C — instanced skinning](superpowers/plans/2026-10-16-instanced-skinning.md).

Kod projektu referencyjnego pozostaje dodatkowym źródłem do porównania, gdy będzie dostępny. Nie blokuje własnej implementacji. Ten zakres nie oznacza ukończenia wcześniejszej kolejki FPS/narzędzi/streamingu/otoczenia.
