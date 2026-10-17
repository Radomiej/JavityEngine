# Plan realizacji — Strażnicy Bram

Status: etapy 1–4 wykonane dla pierwszego lokalnego rozdziału. Etapy 5–7 pozostają planem dalszego rozwoju.
GDD: [2026-10-17-rpg-gdd.md](superpowers/specs/2026-10-17-rpg-gdd.md).
Baza silnika: 95b951c249d59d2c7285701e6e2610933c71b75d,
modernization/java25-gradle-yoga.

## 1. Przygotowanie i bazowy pomiar

Uzyskać lokalny checkout dokładnej rewizji; przeczytać instrukcje podrzędne.
Zapewnić JDK25, wrapper Gradle oraz Xvfb/Mesa. Uruchomić istniejący build
i smoke przed zmianami; zapisać wyniki bazowe. Checkout pobrano, uruchamiając polecenie z dostępem sieciowym. Bazowy build przeszedł przed zmianami.

## 2. Pierwszy pionowy wycinek

Osobny moduł gry, launcher, osada i Łąki Slajmów. Ruch, kamera, interakcja,
atak z cooldownem, HP, głód, monety i EXP. Native HUD w języku polskim.
Warunek odbioru: można przejść od spawnu do walki i wrócić po jedzenie.

## 3. Sklep, misje i postęp

Transakcyjne zakupy, używanie jedzenia, czasowe efekty, dwie misje,
jednorazowe nagrody i wersjonowany zapis. Boss z sygnalizowanym atakiem
oraz portal sprawdzający osobiste zwycięstwo.
Warunek odbioru: pełna pętla pierwszej krainy po ponownym uruchomieniu.

## 4. Testy i prawdziwe screeny pierwszej wersji

Testy granic HP/głodu, zakupów bez pieniędzy, wygaśnięcia efektów,
jednorazowych nagród, ponawiania misji, śmierci, zapisu i blokady portalu.
Uruchomić cały reactor zgodnie z AGENTS.md. Osobno uruchomić OpenGL smoke
z rzeczywistą kontrolą obrazu, wejścia, resize i zamknięcia zasobów.
Zebrać PNG: spawn/sklep, walka z panelem misji, boss, odblokowany portal.
Nie zastępować ich renderami koncepcyjnymi ani screenami istniejących dem.

## 5. Zawartość PvE

Ostępy, hogliny, Wódz Piglinów; następnie gobliny, elity i finałowy boss.
Łucznik, strażnik, dodatkowe produkty i zadania. Każda nowa kraina przechodzi
osobny test postępu, ekonomii i dostępu przez portale.

## 6. Multiplayer jako osobny projekt techniczny

Najpierw symulacja serwera bez GL, dwa lokalne klienty, stabilne identyfikatory,
komendy i snapshoty. Następnie rzeczywisty transport, late join, rozłączenia,
walidacja zasięgu/cooldownu, interest management i interpolacja.
Podstawą jest docs/multiplayer-design-notes.md, które opisuje zamiar, nie gotowe API.

Dopiero po tym PvP: serwerowa ochrona stref, combat timer, atomowy transfer
połowy EXP i własności specjalnych itemów. Testy opóźnienia, duplikacji,
ponowienia śmierci i rozłączenia. Warunek odbioru: dwa rzeczywiste procesy
klientów widzą ten sam wynik walki i stan łupów po reconnect.

## 7. Sezony i nagrody

Serwerowy identyfikator sezonu, licznik odbiorów finałowej nagrody na konto,
zniżki i darmowe odblokowania. Wdrożyć po potwierdzeniu limitu przez użytkownika
i po otrzymaniu opisu specjalnych przedmiotów. Sprawdzić zmianę sezonu,
ponowne wysłanie żądania i brak podwójnego przyznania nagród.

## Następny krok

Pierwszy rozdział jest zaimplementowany i sprawdzony. Zasady dalszych rozdziałów
oraz multiplayer nadal opisuje GDD. [Uruchomienie i rzeczywisty zakres](gatekeepers.md).
