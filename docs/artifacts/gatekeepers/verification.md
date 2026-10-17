# Strażnicy Bram — dowody wykonania

Data: 2026-10-17. Baza: `95b951c249d59d2c7285701e6e2610933c71b75d`,
gałąź `modernization/java25-gradle-yoga`. Lokalna gałąź robocza:
`feature/gatekeepers-rpg`. Zmiany nie zostały wysłane do GitHuba.

## Wyniki

| Sprawdzenie | Wynik |
| --- | --- |
| Bazowy build silnika przed zmianami | PASS, 94 zadania |
| Końcowy cały reactor `./gradlew build` | PASS |
| Testy jednostkowe istniejących modułów | 805 testów, 0 błędów |
| Testy nowej gry | 17 testów, 0 błędów |
| Łącznie | 822 testy w 94 zestawach, 0 błędów |
| Dotychczasowy `:javity-desktop:smoke` | PASS, 120 klatek, kamera, Box2D, Yoga, pauza/resize |
| Gra, zainstalowana dystrybucja uruchomiona z `/tmp` | PASS, 33 asercje, 2092 klatki |
| Rozdzielczości sprawdzone w rzeczywistym oknie | 1920×1080, 1440×900, 1280×720 |
| `:gatekeepers:installDist`, `:gatekeepers:distZip` | PASS, natywna paczka Linux x64 |

Testy uruchomiono przy użyciu JDK25, Xvfb i Mesa llvmpipe
(LLVM19.1.7, 256bits). Gra używa rzeczywistego OpenGL przez LWJGL;
to nie jest screenshot makiety ani render przeglądarkowy.
Znane ostrzeżenie `sun.misc.Unsafe::objectFieldOffset` pochodzi z LWJGL3.4.3
i występuje także w dotychczasowym demo silnika. Nie zatrzymało testów.

Smoki zostały wykonane dwukrotnie: przez Gradle oraz przez gotowy skrypt
`bin/gatekeepers` poza checkoutem. Ostatnia wersja paczki obejmuje dodatkowo
sprawdzenie szerokiego okna1920×1080. Szczegóły ostatniego przebiegu:
[verification.txt](verification.txt).

## Co sprawdził natywny test gry

Start w osadzie, ruch prawdziwego CharacterController2D po wejściu klawiatury,
sklep otwierany przez E, zakup kliknięciem w przycisk Scene2D/Yoga, odrzucenie
zakupu bez pieniędzy, brak ataku pod panelem, leczenie przez1, utrata fokusu,
pauza, zamknięta brama, sześć rzeczywistych zabójstw, wypłata misji bez
duplikowania, walka z bossem, odblokowanie bramy, powrót z zapowiedzi Ostępów,
nagroda za bossa, zakup złotego jabłka za zarobione pieniądze, siła przez2,
zapis i ponowny odczyt profilu oraz niepusty framebuffer w każdej scenie.

Smoke ustawia pozycję gracza przy kolejnych celach. Walka używa prawdziwego
cooldownu i obrażeń; test nie ustawia HP bossa ani wyniku nagrody.
Nie jest to test multiplayer ani dowód ręcznego przejścia całej mapy.

## Zrzuty

- [Osada](01-osada.png), [sklep](02-kupiec.png), [walka](03-walka.png).
- [Misja](04-misja.png), [nagroda](05-nagroda.png), [boss](06-boss.png).
- [Boss pokonany](07-boss-pokonany.png), [portal](08-portal.png), [Ostępy — zapowiedź](09-ostepy.png).
- [1280×720](10-resize-1280.png), [złote jabłko](11-zlote-jablko.png), [1920×1080](00-resize-1920.png).

Oryginalne PNG skopiowano z framebufferów bez retuszu. Obejrzano osadę,
sklep, walkę, bossa, siłę i warianty rozmiaru; panele pozostają czytelne.

## Przegląd i decyzje

Samodzielny przegląd kodu, bez delegowania zgodnie z AGENTS.md. To nie jest
niezależny review drugiego autora. Wykryte błędy odtworzono testami przed poprawką:
opuszczenie areny/reset bossa, ochrona osady, przywołanie dwóch strażników,
nieprawidłowe kodowanie zapisu, przepełnienie licznika jedzenia oraz jedzenie
przy0 HP. Wszystkie odnośne testy przeszły od RED do GREEN.

Decyzje implementacyjne:

- Wykonanie bez ponownego pytania o zgodę po „Rób akceptuje”; szczegółowy plan
  rozwija zaakceptowany zakres. Koszt ewentualnej pomyłki: szczegóły pozostają
  do korekty w lokalnym kodzie, bez publikacji.
- Pierwsza mapa i zapowiedź Ostępów działają w jednej scenie Game. Nie ma
  potrzeby przebudowywania scen podczas przejścia do niezbudowanego rozdziału;
  późniejsze osobne krainy będą wymagały przejść przez SceneManager.
- Przejście przez portal nie udaje gotowej drugiej krainy. Komunikat jawnie
  wskazuje kolejny etap, a interakcja powrotu działa.

Ograniczenia pierwszej wersji: brak kolizji z dekoracjami i dźwięku, jedna
grywalna klasa/kraina, zapis lokalny, brak prawdziwego PvP. Pozostałe klasy,
hogliny/gobliny, sezony i specjalne itemy są w roadmapie. Nie sprawdzano
Windowsa, macOS ani Androida.
