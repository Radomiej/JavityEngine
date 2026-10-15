# Moduł voxelowy Javity

Status: projekt zaakceptowany przez użytkownika; implementacja i weryfikacja opisane w docs/voxels.md oraz docs/voxel-verification.json.

## Cel

Użytkownik chce użyć agentów Luna do budowy renderowania voxeli i chunkowania, aby można było tworzyć gry podobne do Minecrafta. Wcześniejszy cel obejmuje demo oraz własne tekstury. Rezultatem pierwszego etapu ma być opcjonalna biblioteka, nie kod ograniczony do jednego przykładu. Demo ma pokazywać użycie tej biblioteki.

Podstawowy kontrakt: dane świata nie są obiektami sceny, renderer jest adapterem danych do istniejącego przebiegu 3D. Jeden GameObject na renderowany chunk, nigdy jeden na blok. Nie deklarujemy pełnej gry Minecraft, fizyki 3D, multiplayer ani nieskończonego świata.

## Struktura i zakres

Nowy opcjonalny moduł javity-voxel zależy od javity-core i libGDX. Pakiety danych/meshingu/pickingu działają bez kontekstu GL; adapter GPU jest tworzony i zamykany na wątku renderującym. Rdzeń silnika nie zależy od voxeli. Desktop dodaje flagę --voxels i zadanie voxelSmoke.

Pierwszy etap obejmuje:

- Rejestr nieprzezroczystych bloków i mapowanie tekstur dla sześciu ścian.
- Świat z ograniczoną liczbą jawnie załadowanych chunków 16×16×16, również na ujemnych współrzędnych.
- CPU mesher emitujący wyłącznie widoczne ściany, z uwzględnieniem załadowanych sąsiadów.
- Zmiany bloków, unieważnianie chunków i ograniczoną liczbę przebudów w klatce.
- Teksturowane modele chunków, jawna własność zasobów, widoczność w kamerze i bezpieczna wymiana siatki.
- Ograniczone raycast/DDA z informacją o trafieniu i ścianie do stawiania bloków.
- Skończone demo wielochunkowe, swobodną kamerę, usuwanie/stawianie oraz własny atlas PNG.

Greedy meshing, asynchroniczni workerzy, automatyczne streamowanie, LOD, zapis świata, przezroczysta woda, dynamiczne oświetlenie, kolizje, sieć i survival są dalszymi etapami. Projekt nie uzależnia od nich uruchomienia podstawowego modułu.

## Dane i współrzędne

Oś Y jest wysokością, blok ma rozmiar 1×1×1 jednostki. Pozycja bloku oznacza jego dolny narożnik. Współrzędne bloków są całkowite, chunkowane floorDiv/floorMod; x=-1 należy do chunku -1 i lokalnego x=15. Pierwszy renderer przyjmuje współrzędne bloków od -1048576 do 1048575 w każdej osi (chunk od -65536 do 65535). Ogranicza to utratę precyzji float i chroni obliczenia; granice są walidowane przed mutacją. Rebazowanie dużych światów to osobne rozszerzenie.

Air ma identyfikator 0. Nieznany identyfikator bloku jest błędem konfiguracji. Rejestr zostaje zamrożony przed użyciem w świecie; początkowo wszystkie niezerowe bloki są sześcianami opaque. API nie udostępnia mutowalnych tablic świata pozwalających ominąć dirty tracking. Zapis tej samej wartości nie dodaje pracy. Brak chunku przy odczycie oznacza air, przy edycji zwracany jest błąd bez niejawnego tworzenia chunku. Dane bloków to short[4096], publiczne ID to unsigned int w zakresie 0..65535; rejestr zwraca ID podczas konfiguracji, a utworzenie świata zamraża jego definicje.

Ładowanie chunku i jego usunięcie unieważnia siatki istniejących sąsiadów, ponieważ zmienia widoczność ich brzegów. Edycja brzegowa unieważnia właściciela i odpowiednich sąsiadów ścianowych; narożnik nie wymaga przebudowy diagonalnych chunków. Liczba chunków ma jawny limit. Żądanie przekroczenia limitu zwraca błąd bez częściowej mutacji.

## Minimalne API Java

Nazwy klas są kontraktem projektu; implementacja może dodawać walidowane konstruktory i pomocnicze overloady bez rozszerzania zakresu.

```java
record ChunkPos(int x, int y, int z) {}
record BlockPos(int x, int y, int z) {}
enum BlockFace { NEG_X, POS_X, NEG_Y, POS_Y, NEG_Z, POS_Z }
// BlockDefinition: nazwa i sześć tile IDs. Każdy blok poza air jest opaque.
// BlockRegistry.register(definition) -> int ID; później świat zamraża rejestr.

// VoxelWorld(registry, maxLoadedChunks)
// loadChunk(ChunkPos): tworzy pusty chunk, ponowne wywołanie jest no-op.
// unloadChunk(ChunkPos): usuwa dane i oznacza istniejących sąsiadów dirty.
// getBlock(x,y,z) -> int
// setBlock(x,y,z,id) -> boolean changed
// loadedChunkCount(), dirtyChunkCount(), snapshot(ChunkPos)

// VoxelMesher.build(snapshot, atlasLayout) -> VoxelMeshData
// Snapshot jest niezmiennym CPU obrazem wnętrza i sześciu sąsiednich brzegów.
// VoxelMeshData: pozycje/normalne/UV, indeksy unsigned short, faceCount.
// Publiczne dane wynikowe nie pozwalają zmieniać świata/snapshotu.

// VoxelRaycaster.cast(world, origin, direction, maxDistance, maxSteps)
//   -> Optional<VoxelHit>
// VoxelHit: block, enteredFace, placementCell, distance.

// VoxelChunkRenderer(game, world, borrowedAtlas, atlasLayout)
// update(camera, maxRebuilds): poza renderowaniem, na GL thread.
// close(): odłącza obiekty i zwalnia własne modele.
```

DDA normalizuje skończony niezerowy kierunek i mierzy dystans w jednostkach świata. Start wewnątrz zajętego bloku zwraca dystans 0, enteredFace i placementCell nieobecne — nie wolno wtedy stawiać przez zgadywaną ścianę. Start na granicy testuje floor(origin), remisy kroków rozwiązuje deterministycznie X, Y, Z. Ujemny/nieskończony dystans, niepoprawne wektory i maxSteps≤0 są odrzucane; przekroczenie poprawnego limitu kroków lub dystansu daje brak trafienia. PlacementCell oznacza poprzednią komórkę sąsiadującą ścianą, nie dowolne zaokrąglenie punktu trafienia.

## Meshing i budżet pracy

Mesher produkuje CPU dane pozycji, normalnych, UV i indeksów w lokalnych współrzędnych chunku. Nie tworzy Mesh ani Texture. Każda widoczna ściana to cztery wierzchołki i dwa trójkąty z prawidłowym windingiem; ukryte styki nie istnieją w geometrii.

Przy 16³ chunkach checkerboard daje maksymalnie 12288 ścian i 49152 wierzchołki; liczba indeksów może przekroczyć 65535, ale każdy indeks wierzchołka musi mieścić się w unsigned short. Wybrany upload musi obsługiwać tę liczbę indeksów albo dzielić geometrię na bezpieczne części. Nie zakładamy ograniczeń ModelBuilder bez ich sprawdzenia.

Dirty queue jest deduplikowana, ma deterministyczną kolejność i nie rośnie ponad liczbę załadowanych chunków. Pierwsza wersja wykonuje meshing i upload synchronicznie z limitem maxRebuildsPerFrame; limit ogranicza liczbę chunków, a nie gwarantowany czas w milisekundach. Nie przebudowujemy stałego świata w każdej klatce. Odczyt i mutacje początkowo ograniczone do jednego wątku. Brak workerów eliminuje problem publikowania starych snapshotów, nie stanowi deklaracji wydajności dużych światów.

## Renderer i zasoby

Adapter używa istniejącego ModelRenderer/Transform3D i standardowego GdxRenderer bez specjalnego renderera dla każdego bloku. Położenie chunku reprezentuje Transform3D; vertices pozostają lokalne. Frustum culling korzysta z world-space AABB chunku i nie usuwa go z danych świata.

Adapter posiada utworzone modele/meshe, pożycza atlas. ModelRenderer pożycza Model, więc adapter jest odpowiedzialny za jego zwolnienie. Modele nie mogą przejąć własności wspólnej tekstury przez manageDisposable. Nowy model powstaje przed podmianą; przy błędzie tworzenia poprzedni pozostaje ważny, nowe części są zwalniane. Stary renderer zostaje odłączony lub wyłączony przed dispose starego modelu; wymiana odbywa się poza aktywnym ModelBatch. Pusty chunk nie tworzy pustego ModelRenderer.

Usunięcie chunku zwalnia model i obiekt adaptera oraz unieważnia sąsiadów. Zamykanie jest idempotentne, działa po częściowej inicjalizacji i próbuje zwolnić wszystkie własne zasoby po błędzie jednej operacji. Dane CPU i zasoby GPU mają oddzielny lifetime. Atlas zamyka jego właściciel po zamknięciu wszystkich konsumentów.

## Demo i tekstury

Oryginalny, edytowalny atlas PNG z trawą (góra/bok), ziemią, kamieniem, drewnem (bok/przekrój), piaskiem i nieprzezroczystymi liśćmi. Nearest filtering, brak mipmap, padding/gutters i UV wewnątrz komórek. Dołączony deterministyczny generator oraz podgląd atlasu. Żadnych tekstur lub znaków Minecraft/Mojang.

Demo pokazuje mały deterministyczny teren obejmujący dodatnie i ujemne chunki, wzgórze, drzewo i granicę chunków. Kamera WASD/mouse look w trybie swobodnego lotu, Esc zwalnia kursor. Trafiony blok jest wskazany, klik usuwa/stawia wybrany blok po sąsiedniej stronie. Zasięg interakcji ograniczony; wejście początkowo w powietrzu nie może generować fantomowego trafienia. Kamera nie ma obietnicy kolizji ani grawitacji.

HUD pokazuje załadowane chunki, widoczne chunki, kolejkę dirty, liczbę przebudów i ścian. Liczniki służą diagnozie, nie są benchmarkiem. Screenshot ma pokazywać rozpoznawalny teksturowany teren i UI, a nie sam licznik sukcesu.

## Testy i kryteria odbioru

CPU:

- Jeden blok: 6 ścian; dwa sąsiednie: 10; pełny chunk: 1536; dwa stykające się pełne chunki: 2560 łącznie.
- Brak wewnętrznych ścian, poprawne normalne/winding i UV każdego typu ściany.
- Air/empty, ujemne współrzędne, granice zakresu i limit chunków.
- Load/unload i edycje brzegowe poprawnie unieważniają sąsiadów; zapis tej samej wartości nie dodaje pracy.
- Kolejka deduplikowana, budżet przebudów respektowany, checkerboard nie przepełnia indeksów.
- DDA: trafienie, miss, start wewnątrz bloku, ujemne współrzędne, zerowe osie, limit dystansu, krawędzie i narożniki z deterministyczną polityką tie.

Rzeczywisty OpenGL:

- Uruchomienie --voxels --smoke w kontekście Mesa/Xorg, nie tylko headless backend.
- Render tekstur i różnych ścian, depth/camera, pan/fly/resize i celowanie.
- Skrypt usuwa/stawia na granicy chunków; dane i liczniki potwierdzają przebudowę obu stron.
- Screenshot, kontrolowane testy pikseli UV/occlusion i brak błędów GL.
- Weryfikacja zwalniania siatek/modeli po podmianie i close, bez zwalniania pożyczonego atlasu.
- Pełny reactor i istniejące smoke adekwatne do zmienionych integracji; uruchomienie z installDist poza checkoutem z rzeczywistym zasobem PNG.

## Oddzielenie od MapLibre

MapLibre/MBTiles i voxele są dwoma opcjonalnymi podsystemami. Ten projekt nie blokuje dalszego przygotowania integracji map. MBTiles nie staje się magazynem bloków świata. Wcześniejsze zadanie dashed Shapes pozostaje osobne; zmiana priorytetu na voxele go nie oznacza jako zakończonego.

## Przegląd projektu

Trzy niezależne raporty Luna (dane, GPU, demo) zostały porównane z istniejącymi ModelRenderer, GdxRenderer, GameObject i Camera3D. Ujednolicono jeden moduł, stały rozmiar 16³, jawne ładowanie chunków, opaque-only i wspólny lifetime atlasu. Poprawiono propozycję przebudowy diagonalnych sąsiadów oraz błędne liczby worst-case w raporcie danych. Reguły tego dokumentu mają pierwszeństwo przed roboczymi raportami agentów.

Powyższy przegląd projektu poprzedzał implementację. Później wykonano plan, implementację przez agentów, niezależne przeglądy oraz testy CPU/native i dystrybucji. Aktualne dowody i granice znajdują się w docs/voxels.md i docs/voxel-verification.json; robocze raporty projektowe same nie są dowodami runtime.
