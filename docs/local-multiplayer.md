# Lokalne pokoje Colyseus

`javity-rooms` prowadzi prostą symulację w prawdziwych `Game`/`GameObject` z core. `web/local-multiplayer` zapewnia lokalny Colyseus Room i podgląd przeglądarkowy. Każdy pokój ma osobny proces Java; Node przypisuje niezmienny `sessionId`, sprawdza kształt i zakres wejścia, a wynikową pozycję i tick otrzymuje wyłącznie ze snapshotów Java.

To ograniczony proof integracji Room → oficjalny JS SDK → JSONL → Java → Schema. Podgląd pokazuje punkty, nie uruchamia renderera Javity. Nie jest to target TeaVM/Wasm, pełny netcode, fizyka Bullet ani gotowy deployment.

## Jedna komenda: workspace development

Z katalogu repozytorium uruchom:

```sh
./gradlew dev
# inny port, także port efemeryczny:
./gradlew dev -PdevPort=0
```

Gradle buduje worker i wykonuje `npm ci` dla lokalnej paczki Colyseus (ponownie po zmianie lockfile). Następnie proces Java `LocalDevServerMain` uruchamia i nadzoruje Node. Wypisuje URL dopiero po readiness Colyseus. Ctrl+C zamyka gateway i jego workery; odłączenie kontrolnego stdin po nagłym zakończeniu rodzica też zamyka Node. Nie trzeba ręcznie uruchamiać kilku terminali. Wymagane pozostają JDK 25 i npm; minimum pakietów to Node.js 22, a opisany proof wykonano na Node.js 24 (22/23 nie sprawdzono w tym środowisku). Pierwsze uruchomienie pobiera zależności.

Podgląd **Client view** pokazuje replikę klienta i wysyła intencje ruchu. **Server view** pokazuje bezpośrednie snapshoty Java, wyłącza sterowanie i nie tworzy kolejnego gracza. Można też wpisać ID pokoju i oglądać jego serwer bez dołączania klientem. To podgląd danych przykładowego pokoju; nie jest zdalnym inspektorem całego drzewa GameObject ani przekazaniem authority klientowi. Przełączanie widoku zachowuje istniejącą sesję klienta.

Development endpoints `GET /dev/rooms` i `GET /dev/rooms/:roomId` są włączane przez lokalny supervisor, działają tylko przy loopback i zwracają ostatni snapshot oraz PID workera. Zwykłe `npm start` nadal ma je wyłączone. Platforma może używać `LocalDevServer.start(workspace, port, false)` i `close()`; nie potrzebuje sterowania procesem przez shell. `JAVITY_NODE_BINARY` wybiera executable Node, a istniejące `JAVITY_ROOMS_WORKER` wybiera zgodny launcher authority.

### Hostowanie i przyszła platforma

**Liczba JVM nie jest kontraktem silnika.** Aktualny domyślny host nadal izoluje każdy pokój w workerze JVM. Nie wymaga to od twórcy gry samodzielnego zarządzania procesami. Przyszła platforma może wymienić hostowanie na pule procesów lub wiele pokoi w jednej JVM, zachowując osobne `Game`, owner-thread i lifetimes. Nie przebudowujemy teraz transportu JSONL na nieprzetestowany multiplekser.

Colyseus jest lokalną paczką workspace `web/local-multiplayer`, używającą oficjalnych zależności npm. Nie potrzeba kopiować upstream repo ani implementować serwera Colyseus od nowa. Ta sama paczka i Java authority mogą później trafić do kontenera. Zbudowane workspace uruchomi supervisor:

```sh
java -cp 'javity-rooms/build/install/javity-rooms/lib/*' \
  org.javity.rooms.LocalDevServerMain --workspace=. --port=2567 --container
```

`--container` jawnie wybiera `0.0.0.0`, wyłącza development endpoints i pozwala wystawić port przez host/container. To entry point hostowania; nie wykonuje wdrożenia. Zewnętrzna platforma odpowiada za HTTPS/WSS, auth, limity, routing i health checks (`/health`). Przed publicznym udostępnieniem wymagane są ich osobne testy. Sam preview i przykładowy ruch nadal nie stanowią gotowej gry ani pełnej replikacji dowolnych komponentów/fizyki.

## Ręczne uruchomienie składników

Wymagane są JDK 25, npm i Node.js co najmniej 22 według `engines` paczek. Zapisana weryfikacja używa Node.js 24; nie stanowi osobnego testu wersji 22/23. Z katalogu repozytorium:

```sh
./gradlew :javity-rooms:installDist
npm --prefix web/local-multiplayer ci
npm --prefix web/local-multiplayer start
```

Launcher Java korzysta z `JAVA_HOME` albo `PATH`. Ustaw je na pełne JDK 25, np.:

```sh
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
```

Gateway wiąże `127.0.0.1:2567`. `PORT=0 npm start` wybiera port efemeryczny. `JAVITY_ROOMS_WORKER` może wskazać inny, zgodny launcher; argument room ID dodaje gateway. Serwer, worker i worker JVM są lokalnymi procesami. Nie uruchamiają shell commandów z wiadomości klienta.

Otwórz `http://127.0.0.1:2567/`. Przyciski **Create room**, **Join room** i **Leave room** używają SDK Colyseus. Aby zobaczyć dwóch graczy, utwórz pokój w jednej karcie, skopiuj jego ID, dołącz z drugiej karty i poruszaj się WASD/strzałkami. SDK browser jest serwowany lokalnie z zainstalowanego `@colyseus/sdk`; strona nie korzysta z CDN. `GET /health` zwraca `{ "status": "ok" }`.

Ostatni klient zamyka swój worker; jego rozłączenie nie zatrzymuje innych pokoi. `Ctrl+C` / `SIGTERM` zamyka wyłącznie gateway i posiadane przez niego procesy. Nie ma reconnect preservation. Połącz klienta ponownie przez nowe `create`/`join` i nową sesję.

## Zachowanie i weryfikacja

Worker używa wersjonowanego JSONL v1 po stdin/stdout. Linie mają limit 8192 bajtów; wejście Node/Java, kolejki i lifetime procesu są ograniczone. Authority wykonuje krok 60 Hz i snapshot co trzy kroki, przyjmuje maksymalnie osiem graczy, normalizuje intencję, ogranicza pozycję do ±50, wygasza wejście po 250 ms i odrzuca powtórzone/stare/niebezpieczne sekwencje. Klient nie wybiera `sessionId` ani pozycji; pola `position`/`targetId` i pozostałe nieznane pola inputu są odrzucane. Podstawowy kontrakt JSONL oraz testy child-process są w [module `javity-rooms`](../javity-rooms/src/main/java/org/javity/rooms/LocalRoomWorker.java).

Test end-to-end używa zbudowanego installDist, prawdziwego localhost Colyseus servera i dwóch oficjalnych SDK klientów. Nie zastępuje Java workerem mockowanym:

```sh
./gradlew :javity-rooms:test :javity-rooms:installDist --no-daemon
npm --prefix web/local-multiplayer ci
npm --prefix web/local-multiplayer test
```

Test potwierdza create/join, baseline late join, ruch wyliczony w Javie, odrzucenie malformed/teleport/unsafe input, starą sekwencję, leave/rejoin, izolację dwóch pokoi, błąd startu, awarię workera po ponad 5 s oraz cleanup przy ostatnim leave i zamknięciu gateway z aktywnym klientem. Test potrzebuje zezwolenia środowiska na loopback TCP i JDK 25 w `PATH`. Wyniki i dokładny zakres: [verification.json](artifacts/local-multiplayer/verification.json).

Ręczna weryfikacja preview w przeglądarce jest osobna i nie wynika z testu Node. Większe zakresy — prediction/interpolacja, reconnect, auth, produkcyjny deployment, native SDK Java, fizyka pokoju i browser/Wasm engine — pozostają niezweryfikowane albo osobnym zadaniem. Kierunek browser adapterów opisuje [audyt](audits/2026-10-17-consolidation/browser-wasm-ports.md).
