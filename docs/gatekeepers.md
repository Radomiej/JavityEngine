# Strażnicy Bram — PvE 0.2

Lokalne RPG 2D na Javity Engine, rozwinięte z pierwszego rozdziału bazującego
na `95b951c249d59d2c7285701e6e2610933c71b75d`. Gra korzysta z rzeczywistych
Game/GameObject/Component, CharacterController2D, Camera2D, rendererów oraz Yoga UI.

## Uruchomienie Windows x64

Rozpakuj **cały ZIP** i uruchom `Start.bat`. Wariant z `runtime` zawiera prywatną
Javę 25; instalacja Javy i prawa administratora nie są potrzebne. Samodzielny
JAR wymaga 64-bitowej Javy 25: `java -jar Straznicy-Bram.jar`.
[Instrukcja Windows](gatekeepers-windows.txt) opisuje sterowanie i zapis.

JAR zawiera zależności Java oraz wymagane DLL-e Windows x64 (w tym Yoga).
Dodatkowe biblioteki Linux umożliwiają przetestowanie tego samego artefaktu
w środowisku budowania. Nie należy traktować testu Linux/Wine jako testu
na fizycznym Windowsie.

Paczkę Windows uruchomiono także przez jej własny `Start.bat` pod Wine 10:
dołączona Java 25, natywne DLL-e Windows i dwa pełne obiegi PvE przeszły
24 sprawdzenia przy 1849 klatkach. [Raport](artifacts/gatekeepers-pve/verification.md).

## Budowanie

```sh
./gradlew :gatekeepers:run
./gradlew :gatekeepers:executableJar
python3 tools/verify-gatekeepers-jar.py games/gatekeepers/build/libs/Straznicy-Bram.jar
python3 tools/package-gatekeepers-windows.py --runtime temurin25-windows-x64-jre.zip --output output
```

JDK 25, wrapper Gradle i OpenGL wymagane do budowania/uruchomienia.
Pakowanie używa podanego archiwum runtime; nie pobiera go przy starcie gry.
Manifest ma Main-Class, Multi-Release i Enable-Native-Access. Usługi SPI
bibliotek są scalane, a licencje zachowane. Start nie wymaga checkoutu ani Node.

## Sterowanie

| Akcja | Skrót |
| --- | --- |
| Ruch | WASD / strzałki |
| Atak / atak w kierunku patrzenia | LPM / Spacja |
| Unik z krótką ochroną | Shift |
| Rozmowa, misja, arena, nagroda | E |
| Przerwij arenę i wróć do osady | R |
| Chleb / złote jabłko | 1 / 2 |
| Ekwipunek / pauza | I / Esc |

## Pełna pętla PvE

Spawn → kupiec i zapasy → walka z mobami lub wejście na arenę →
EXP/monety/misje → leczenie → ukończenie trzech fal → odbiór 60 monet/100 EXP →
powrót → ulepszenie miecza → kolejna próba. Arena jest dostępna od początku
przez portal w południowo-wschodniej części osady; nie trzeba najpierw zabijać bossa.

Fale mają 2, 3 i 4 przeciwników; ostatnia zawiera Brutala z 75 HP.
Przeciwnicy sygnalizują ataki. Miecz trafia do 3 celów w stożku, sprawdza zasięg
oraz przeszkody i odrzuca mniejsze moby. Unik ma odnowienie 1,8 s; po otrzymaniu
obrażeń działa krótka ochrona przed jednoczesnymi trafieniami wielu mobów.

Walka/odpoczynek/zwycięstwo/odbiór są osobnymi stanami. Nagrodę odbiera się
raz na zwycięstwo. Śmierć lub przerwanie resetuje próbę bez końcowej nagrody;
monety/EXP za już pokonane moby pozostają. Każdą próbę można powtórzyć.

Ulepszenia miecza kosztują 60 i 120 monet i trwale zmieniają obrażenia 12→16→20.
Sklep ma chleb za 4 i złote jabłko za 100 (leczenie i siła I przez 30 s).
Zlecenie na 6 slajmów daje 8 monet/20 EXP i można je ponawiać. Król Slajmów
oraz jego jednorazowe zlecenie i portal nadal działają.

Budynki, staw i pnie drzew mają statyczne kolizje. Rozmieszczenie drzew jest
wspólne dla grafiki i kolizji. To proste rozwiązywanie ruchu gry, bez deklaracji
użycia Box2D. Mniejsze dekoracje są nieblokujące.

## Części przydatne innym grom

- Nowe **MinimapModel/Minimap** w `javity-ui`: świat, znaczniki i pożyczona tekstura.
- Nowe **MeleeAttack2D** w `javity-core`: czas ataku i geometria stożka bez GL.
- Wszystkie paski używają istniejącego **ProgressBar** silnika.
- Ceny, zapis profilu, AI, fale, nagrody i kolizje zawartości pozostają w module gry.

[API i przykłady](minimap-and-melee.md).

## Zapis

`${user.home}/.gatekeepers/profile.properties`, co 10 s i przy zwykłym zamknięciu.
Wersja 2 zachowuje dotychczasowe pola, poziom miecza i liczbę wygranych aren.
Wersja 1 jest migrowana bez kasowania postępu. Pozycja i trwająca fala nie są
trwałe; wznowienie rozpoczyna się w osadzie. Uszkodzony profil pozostaje
nietknięty; gra pokazuje błąd i wyłącza automatyczny zapis tej sesji.

Własny plik: `java -jar Straznicy-Bram.jar --save=/absolutna/sciezka/profil.properties`.

## Weryfikacja

```sh
./gradlew build :javity-desktop:smoke :gatekeepers:smoke :gatekeepers:pveSmoke
java -jar Straznicy-Bram.jar --pve-smoke --captures=/absolutny/katalog
```

Smoki wymagają ekranu/OpenGL; na serwerze użyj Xvfb/Mesa. `pveSmoke` steruje
klawiaturą i kliknięciami: chodzi od spawnu do wejścia, przechodzi dwie areny,
wraca, kupuje oba ulepszenia i sprawdza zapis. Nie teleportuje gracza poza
zwykłymi przejściami gry ani nie ustawia HP/nagród. Starszy `smoke` zachowuje
inscenizowane pozycje do osobnego sprawdzenia bossa, portalu i resize.
Żaden smoke nie zmienia profilu użytkownika.

## Pozostały zakres

Bez PvP i transportu multiplayer. Kolejne krainy, klasy, sezony, specjalne
przedmioty i audio pozostają do dalszej pracy. Nie testowano Androida/macOS.

[GDD](superpowers/specs/2026-10-17-rpg-gdd.md) ·
[Plan PvE/Windows](superpowers/plans/2026-10-17-pve-windows.md) ·
[Dowody PvE](artifacts/gatekeepers-pve/verification.md).
