# Mapy offline, linie i demo voxelowe — rozpoznanie 2026-10-15

Status: audyt istniejącego kodu i propozycja zakresu; nowe funkcje poniżej nie są jeszcze wdrożone.

## Co faktycznie działa

Nowy moduł map czyta lokalne kafelki PNG256×256 z root/z/x/y.png. Nie wymaga internetu. MapView/markery/trasy są zapisywane przez jawne codeki SceneJson v1, ale sam dataset, obrazy kafelków i provider nie są częścią sceny. Nie istnieje własny format wektorowej mapy bazowej. Demo --map pokazuje syntetyczne kafelki i nie przedstawia prawdziwych ulic.

Uruchomiono gotową dystrybucję --map --smoke poza repozytorium: PASS120 klatek, pan/zoom/dateline, markery/trasa, cache64/load124, resize i polskie glyphy. Test był bez pobierania kafelków z sieci. Przywrócono lokalny Xorg po przerwie środowiska; pierwsze uruchomienie zatrzymało się na inicjalizacji GLFW przed kodem mapy.

## Decyzja o wewnętrznym formacie

Doprecyzowanie użytkownika: własne mapy do gier i aplikacji, ze stylowaniem podobnym do MapLibre. Kierunkiem jest model wektorowy z oddzielnymi danymi i stylem; sam pakiet PNG nie realizuje tego celu. Poniższe warianty opisują różne zastosowania, a nie gotowe funkcje.

- Pakiet XYZ: wersjonowany manifest JSON z tileSize/scheme/minZoom/maxZoom/center/bounds/attribution i ścieżką do lokalnych PNG; wykorzystuje obecny renderer.
- Wektory offline: osobny, ograniczony dataset punktów/linii/prostych polygonów i jawnych warstw stylu. Pozwala kolorować drogi/budynki, ale wymaga modelu danych, indeksowania i nowego renderowania; nie jest pełnym formatem MapLibre.
- Poziomy gry: osobny importer danych poziomu, niezależny od Web Mercator i geograficznych kafelków.

## Propozycja linii

SOLID/DASHED/DOTTED, długość kreski/przerwy i przesunięcie fazy. Dla tras mapowych szerokość i wzór w pikselach ekranu, zgodnie z obecnym kontraktem. Faza biegnie przez całą trasę i zakręty, także po rozpakowaniu dateline; nie zaczyna od nowa na każdym segmencie. Jawne limity punktów/fragmentów i brak generowania nieograniczonej geometrii co klatkę. Zachować domyślną linię ciągłą i serializację starszych scen. Shapes może otrzymać analogiczny styl w swoich jednostkach lokalnych.

## Propozycja voxel showcase

Skończony teren z kilku chunków, nie GameObject na każdy blok. Siatka zawiera wyłącznie widoczne ściany; korzysta z obecnych ModelRenderer/Transform3D/Camera3D. Kamera swobodna WASD + mouse look, wskazywanie DDA, podstawowe usuwanie/stawianie bloków i ograniczona przebudowa są możliwe bez tworzenia nowego silnika fizyki. To kreatywne demo, nie pełny Minecraft, multiplayer ani nieskończony świat.

Tekstury: własny, modyfikowalny atlas PNG pixel-art — trawa, ziemia, kamień, drewno, piasek i liście. Nearest filtering, padding oraz kontrola UV. Modele chunków i atlas mają jawnego właściciela; stare siatki po edycji są zwalniane dopiero po odłączeniu od renderowania.

Weryfikacja po wdrożeniu: testy fazy kreskowania/codec, piksele kreska-przerwa i ciągłość na zakrętach; voxel face counts6/10 dla jednego/dwóch sąsiednich bloków, ukryte ściany i granice chunków, ray/DDA, UV atlasu; prawdziwy smoke OpenGL, screenshoty, resize oraz cleanup.

## Kierunek po doprecyzowaniu — własne mapy jak w MapLibre

MapLibre oddziela źródła danych (GeoJSON, kafelki wektorowe lub raster) od uporządkowanych warstw stylu JSON. Warstwa wybiera źródło i obiekty przez filtr, definiuje layout oraz paint; wyrażenia mogą uzależniać wygląd od właściwości obiektu i zoomu. Jedno źródło może zasilać kilka warstw, np. obrys i środek drogi. Renderer obsługuje widoczny obszar i interakcję, a edytor stylu jest osobnym narzędziem, np. Maputnik. Edycja geometrii wymaga osobnego edytora lub integracji narzędzi do rysowania.

Propozycja dla Javity, jeszcze niewdrożona:

1. Wersjonowany pakiet mapy: manifest, geometria z trwałymi identyfikatorami i właściwościami, styl, lokalne ikony i fonty. Początkowo czytelny JSON i pliki zasobów; archiwizacja i binarny indeks dopiero przy potrzebie.
2. Dwa jawne układy współrzędnych: lokalne XY świata gry oraz geograficzne lon/lat dla aplikacji mapowych. Nie przedstawiać lokalnego XY jako standardowego GeoJSON. Rozszerzenia formatu Javity nie oznaczają zgodności ze specyfikacją MapLibre.
3. Początkowe warstwy: tło, wypełnienia polygonów, linie z obrysem i kreskowaniem, punkty/ikony oraz ograniczone etykiety. Filtrowanie po właściwościach i zakresach zoomu, jawnie udokumentowany podzbiór stylów. Polygony z otworami, triangulacja i nakładanie etykiet wymagają osobnych testów oraz określenia obsługiwanego zakresu.
4. Zapytanie o wskazany obiekt zwraca jego identyfikator i właściwości. Gra może powiązać je z komponentami, ale kolizje i stan rozgrywki pozostają oddzielnymi danymi.
5. Pełny tryb offline wymaga lokalnych danych, stylu, ikon i fontów. Dla dużych map późniejszy importer MVT i kontener PMTiles lub MBTiles; nie zakładać ich automatycznej obsługi przez obecny renderer.
6. Demo autorskiego miasteczka: budynki, park, rzeka, drogi, przerywane oznaczenia i punkty zainteresowania; dwa style tej samej geometrii, wskazywanie obiektów i zmiana koloru drogi bez zmiany danych.

Źródło rozpoznania: dokumentacja MapLibre GL JS odczytana przez Context7 2026-10-15, przykłady źródeł GeoJSON/vector/raster i warstw. Oficjalne odnośniki: https://maplibre.org/maplibre-style-spec/ oraz https://maplibre.org/maputnik/ . Nie deklarujemy pełnej kompatybilności z MapLibre ani gotowego edytora.
