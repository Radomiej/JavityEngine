# Lokalne pokoje Colyseus

`javity-rooms` prowadzi prostą symulację w prawdziwych `Game`/`GameObject` z core. `web/local-multiplayer` zapewnia lokalny Colyseus Room i podgląd przeglądarkowy. Każdy pokój ma osobny proces Java; Node przypisuje niezmienny `sessionId`, sprawdza kształt i zakres wejścia, a wynikową pozycję i tick otrzymuje wyłącznie ze snapshotów Java.

To ograniczony proof integracji Room → oficjalny JS SDK → JSONL → Java → Schema. Podgląd pokazuje punkty, nie uruchamia renderera Javity. Nie jest to target TeaVM/Wasm, pełny netcode, fizyka Bullet ani gotowy deployment.

## Uruchomienie

Wymagane są JDK 25, Node.js 24 (Node 22+ jest wymagany przez serwer Colyseus) i npm. Z katalogu repozytorium:

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
