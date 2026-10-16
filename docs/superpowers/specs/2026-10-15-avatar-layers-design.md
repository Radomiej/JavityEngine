# Avatar: animacja, widoczność części i FPS/TPP

Status: projekt do przeglądu, nie deklaracja wdrożonego API. Priorytet użytkownika: łatwo użyć jednego modelu jako samych rąk FPS i pełnej postaci TPP, zachować animacje i kontrolować widoczność części przypisanych do kości. **Użytkownik potwierdził FPS + przełączany TPP.**

## Stan obecny i granice

ModelPoseAnimator ma bazowy klip/1D mixer oraz do ośmiu masked override layers z wagami/fade. NodeMask wybiera obecnie dokładne ścieżki lub unikalne ID; nie rozwija automatycznie poddrzewa. ModelRenderer ma osobną ModelInstance na obiekt. To własne API inspirowane Animancerem, nie import ani pełne odwzorowanie jego funkcji. Warstwa animacji nie jest filtrem renderowania. Obecnie nie ma AvatarDefinition, maski geometrii per-view ani kamery/viewmodelu FPS/TPP.

## Trzy rozłączne pojęcia

1. **Maska animacji** określa, które kości/węzły dostają pozę danej warstwy: nogi chodzą, górna część ciała wykonuje użycie narzędzia.
2. **Maska widoczności** określa, które części geometrii wysłać do GPU: ręce, głowa, tułów, nogi, ubranie i dodatki.
3. **Warstwa/pas renderowania** wybiera świat lub viewmodel lokalnego gracza. Widok FPS/TPP należy do konkretnego camera rig, nie globalnego singletona ani globalnej flagi ukrywającej ciało wszystkim kamerom.

Nie realizujemy ukrywania przez zerowanie skali kości, usuwanie węzłów, zatrzymanie animatora ani alpha=0. Pełny szkielet i macierze skinningu pozostają prawidłowe; wyłączana jest geometria, a nie rodzic dłoni/attachmentu. Zniknięcie głowy nie może deformować rąk ani usuwać socketów.

## Definicja avataru i wygodne presety

Proponowane AvatarDefinition to niemutowalne dane współdzielone przez instancje. Wiążą semantyczne części HEAD/TORSO/LEFT_ARM/RIGHT_ARM/LEGS, nazwane sloty kosmetyczne oraz sockety z istniejącymi ścieżkami/ID modelu. Brakujące i niejednoznaczne powiązania są błędem inicjalizacji z diagnostyką dostępnych nazw; nie zgadujemy po dowolnym fragmencie nazwy.

Presety: FULL_BODY, ARMS_ONLY, BODY_WITHOUT_HEAD i własna kompozycja części. Użytkownik może niezależnie ukryć hełm/ubranie lub wybrać, czy narzędzie jest widoczne. Początkowo obsługujemy części tego samego modelu/szkieletu oraz sztywne załączniki. Retargetowanie ubrań z dowolnego obcego rigu wymaga osobnego kontraktu; nie dopasowujemy ich tylko po liczbie kości.

Stan widoczności i animacji jest per-avatar/per-view. Dwa obiekty współdzielące Model mogą jednocześnie pokazywać same ręce i całą postać bez zmiany wspólnego zasobu. Domyślne enabled części pozostaje respektowane. Zmiana presetu nie tworzy nowego modelu ani meshów w każdej klatce.

## Model z wieloma częściami oraz jedna skinned mesh

**Wiele NodePart/MeshPart:** filtrujemy draw calls na podstawie zwalidowanych selektorów części. Nie mutujemy wspólnego Model ani nie zmieniamy TRS kości. Gdy widoczność jest zależna od kamery, provider renderables filtruje właściwe części dla danego widoku.

**Jedna siatka ze wspólnym skinningiem:** wyłączenie kości nie jest maską jej wierzchołków. FPS wymaga podziału geometrii. Preferowany jest jawny podział regionów w assetach; alternatywą jest jednorazowy, jawnie wywołany preparator maski na podstawie wpływów kości.

Preparator grupuje całe trójkąty do zdefiniowanych regionów. Zachowuje indeksy kości, atrybuty wierzchołków, bind pose, materiały i macierze skinningu. Nie wycina wierzchołków ze współdzielonego źródłowego Model. Wynik i jego GPU buffers mają jawną własność i zwalniane są po ostatnim użytkowniku. Nie wykonuje się go w render loop.

Automatyczny podział po wpływach kości jest opt-in: trójkąty na styku barku i tułowia mogą być niejednoznaczne. Reguła klasyfikacji i diagnostyka takich trójkątów muszą być jawne; nie obiecujemy idealnej granicy ramion dla dowolnego modelu. Dla dokładnego wyniku wymagane są autorskie regiony. Nieobsługiwane topology/layout danych są odrzucane przed zmianą modelu; brakujące grupy nie oznaczają milczącego powodzenia.

## Integracja z obecnym animatorem

Zachowujemy jednego właściciela pozy i jeden zegar animacji dla kanonicznego avataru. Odtwarzanie FPS/TPP nie aktualizuje tej samej animacji dwa razy i nie zeruje czasu przy F5. Warstwy locomotion/upper-body/action używają obecnego ModelPoseAnimator, zamiast kolejnego konkurencyjnego AnimationController.

Dodajemy jawne selektory poddrzew do NodeMask oraz kompozycję masek potrzebną do upper/lower body. Dotychczasowe `paths/ids` zachowują dokładną semantykę; nowy helper poddrzewa nie zmienia po cichu starych masek. Maski są rozwiązywane i walidowane przy konfiguracji, nie przez przeszukiwanie nazw w każdej klatce.

Filtrowanie geometrii nigdy nie zmienia próbkowania klipu, macierzy kości, markerów ani callbacków. Całkowicie niewidoczny avatar nadal może aktualizować pozę dla gameplay/socketów. Optymalizacja pomijania animacji poza ekranem jest osobnym tematem.

## FPS, TPP i załączniki

- CameraRig3D przełącza FPS/TPP pod F5 i czyta ten sam stan gracza. To nie przełącznik debugowego lotu F.
- FPS rysuje wybrane ręce/narzędzie w osobnym viewmodel pass; UI pozostaje nad nimi. Kamera oczu i pass rąk mają jawne FOV/near plane, a world depth nie ucina dłoni o ścianę.
- TPP pokazuje pełny avatar; kamera skraca dystans przy kolizji ze ścianą i ignoruje własną bryłę gracza.
- Szkielet jest próbkowany raz. Renderable provider może użyć tej samej pozy z inną macierzą bazową dla świata i widoku rąk, bez zmieniania wspólnej instancji w trakcie innego passu.
- Socket narzędzia pobiera transformację kości także wtedy, gdy związana część geometrii jest ukryta. Mieszanie odrębnych animacji viewmodelu pozostaje jawnym rozszerzeniem, nie drugim niejawnym zegarem.
- Światowy avatar lokalnego gracza może pozostać pełny dla innych widoków. Obecny renderer nie ma kompletnego systemu cieni; ten projekt nie oznacza, że taki pass już istnieje.

Prezentacyjne bob/recoil/shake/FOV mogą korzystać z EffectSystem. Nie zapisują się do pozycji bryły gracza ani niszczą lokalnych TRS kości wytworzonych przez animator; mają własny etap/offset i zasady anulowania.

## Testy, które muszą przejść

- Maski dokładne i poddrzewa: duplikaty ID, niepoprawne ścieżki, zagnieżdżone kości i brak niejawnej zmiany starszego API.
- Dwa avatary z jednego Model, niezależne presety; powrót ARMS_ONLY → FULL_BODY odtwarza widoczność bez resetu animacji.
- Ukryte head/torso nie zmieniają macierzy dłoni ani socketu; warstwa akcji na ręce działa podczas locomotion nóg.
- Model wieloczęściowy oraz osobna fixture jednej skinned mesh: istniejące skinning/bone indices zachowane po podziale, źródło niezmienione, niejednoznaczne trójkąty raportowane.
- GPU: ramiona są widoczne w FPS, głowa/tułów nie zostawiają koloru ani głębi, pełna postać wraca w TPP, kamera nie wchodzi w ściany. Zrzuty i próby pikseli, nie wyłącznie enabled flags.
- F5 podczas ruchu, użycia narzędzia i pauzy: brak podwójnych markerów, bez przeskoku playhead i deformacji rigu.
- Usunięcie renderer/animator/avatar w dowolnej kolejności i cleanup po błędzie preparatora; żadnych wycieków własnych mesh buffers ani zwalniania caller-owned Model.
- Dotychczasowe testy pozy/animacji i desktop smoke. Android ma osobne ograniczenia; shader Shapes i hosty desktopowe nie stają się mobilne przez dodanie avataru.

## Podział realizacji

1. Maski poddrzew i widoczność części per-instance/per-view + testy izolacji i macierzy.
2. Jawny preparator regionów jednej skinned mesh + testy skinningu i własności.
3. Viewmodel FPS, pełny avatar TPP, rig kamery i wspólna demonstracja F5 z rzeczywistym modelem testowym.
4. Efekty prezentacyjne kamery/ciała spięte z istniejącym Feel API.
5. Osobno pełna fizyka Bullet i adapter voxelowy: [projekt](2026-10-15-physics3d-player-design.md).

Kryterium akceptacji: konfiguracja części/presetów pozwala użyć jednego przygotowanego assetu i klipów w FPS i TPP, bez ręcznej destrukcji kości i bez pisania od nowa kontrolera animacji. Nie deklarujemy pełnego portu Animancera ani automatycznego poprawnego podziału każdego dowolnego modelu.
