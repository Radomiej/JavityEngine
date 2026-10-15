# Modernizacja Javity — projekt wykonanej bazy

Użytkownik autoryzował migrację API, włączenie Galaxy Rapid do silnika, uruchomienie projektu, Java/Gradle i UI Yoga w stylu Tailwind.

## Architektura

Jeden build Gradle 9.8.1 z wrapperem i checksum, pełne JDK 25 LTS, libGDX 1.14.2 i LWJGL 3.4.3. Maven Central jako jedyne repozytorium bibliotek. Lokalny javity-backend zawiera źródła Galaxy Rapid; javity-engine zachowuje stare API do migracji. javity-net i javity-map są lokalnymi modułami. Android, XML UI i generator pozostają poza buildem.

javity-core implementuje nowy Game/GameObject/Component bez Artemis: wiele komponentów typu, dziedziczenie w zapytaniach, indeksy sceny, tagi, snapshoty i stabilny cykl życia. hasComponent/getComponent są domyślnym API; requireComponent jest opcjonalną kontrolą konfiguracji. Dodanie w aktualizacji zaczyna pracę w kolejnej klatce, usunięcie wyłącza pracę natychmiast, onDestroy jest jednokrotne. WORLD zatrzymuje się przy pauzie, UI działa na czasie unscaled.

Transform2D i Transform3D są oddzielne. 3D ma Vector3, quaternion i hierarchię z blokadą cykli. Renderer obsługuje modele OBJ/G3DJ/G3DB, ModelInstance i AnimationController, a także sprite'y. Modele współdzielą zasób, ale nie stan instancji. AssetManager renderera zwalnia modele w swoim dispose.

javity-ui integruje prawdziwą natywną Yoga ze Scene2D. Klasy utility mają jawny katalog, jednostkę odstępów 4 i błędy dla nieznanych klas. Właściciel zamyka Flex i UiTheme. Aktualnie moduł jest desktopowy.

## Zgodność i migracja

Artemis 2.3.0 pozostaje w backendzie legacy; nie zastępujemy go innym ECS. silver-reflection zastępuje refleksja libGDX. Stary runtime Spine 3.5.51.1 pochodzi z Maven Central, z własnymi warunkami licencji.

Mapy zachowują historyczne API. Poprawki obejmują niezmienianie wektora wejściowego i kolejność osi kafelków. Cache, provider i rendering map wymagają kolejnego etapu; nie deklarujemy działania online.

## Weryfikacja

Build całego projektu, testy komponentów, transformacji 3D, refleksji, Yoga i projekcji. Smoke nowego demo: prawdziwe OpenGL, model OBJ z pliku, sprite 2D, 120 klatek, kliknięcia pauzy/wznowienia, resize i zrzut obrazu. Osobny smoke legacy uruchamia oryginalny pipeline Javity/Galaxy.

## Następne rozszerzenia

Adapter Box2D i map do nowego rdzenia, GLB/glTF/PBR, fizyka 3D, breakpointy i szerszy katalog UI, migracja Android/web. Obecny wynik jest działającą bazą, nie zakończeniem migracji wszystkich historycznych integracji.
