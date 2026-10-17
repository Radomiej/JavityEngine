# Lokalny Colyseus + Java authority

Status: wdrożenie zlecone przez użytkownika 2026-10-17; bounded vertical slice wcześniej opisanej roadmapy.

## Cel
Prawdziwy Colyseus Room i dwóch klientów oficjalnego JS SDK, którzy sterują prostymi GameObjectami przez intencje. Java jest jedynym autorem pozycji. Uruchomienie lokalne z jedną komendą po instalacji zależności. To nie pełny netcode FPS ani deployment platformy.

## Granice
Nowy opt-in `javity-rooms` (core + libGDX Json, bez GL/audio/native/server web dependency) dostarcza headless `LocalRoomSimulation` i `LocalRoomWorker`. Node gateway w `web/local-multiplayer` tworzy osobny worker Java na pokój i replikuje snapshoty przez Colyseus Schema. Brak zależności z core/UI/default desktop na rooms lub nowy ECS. Server JVM współdzieli nasze Game/Component; ECS jest opcjonalny, nie wymagany do gry multiplayer.

Wszystkie mutacje Game, tick i close należą do owner thread. Worker używa bounded kolejki wejść; reader thread wyłącznie dekoduje/umieszcza wiadomości. Stały krok 1/60, snapshot co 3 ticki, maksymalnie 8 graczy, prędkość 4 jednostki/s, pozycje clamp ±50, normalizacja wektora wejścia. Wejście wygasa po 250ms bez odświeżenia. Sequence jest nieujemnym bezpiecznym integerem JS, powtórzone/stare nie ruszają postaci. Klient nie wybiera sessionId ani pozycji. To prosty data mover, nie fizyka Bullet.

Wersjonowany JSONL protocol v1 po stdio: gateway generuje roomId/sessionId; ready, join, input, leave, shutdown i snapshots/errors/ack. stdout tylko protokół, stderr diagnostyka. Snapshot ma roomId, tick i mapę graczy sessionId→x,z,lastSequence. Sieciowa tożsamość sessionId, nie GameObject.id. Rozmiar linii max8192B/chars z jawnie opisaną polityką, kolejka max256, batch max64/tick; błędne dane nie kończą symulacji, przepełnienie/fatal framing kończy sesję worker kontrolowanie. Żaden proces z Internetu nie jest ładowany ani wykonywany.

Node: oficjalne npm @colyseus/core0.18.19, ws-transport0.18.4, schema5.0.40, sdk0.18.5 (metadata odczytane z registry), express zgodny peer, dokładnie przypięte dependencies i lockfile. Użyj JS ESM i defineTypes bez kompilatora TS jeśli upraszcza. Node>=22 (sprawdzone środowisko24). Server wiąże wyłącznie127.0.0.1, losowy port w testach, domyślnie2567 dev; bez Redis/Docker/cloud. Endpoint /health i statyczny podgląd browser z lokalnym bundlowanym SDK (bez CDN) umożliwia create/join przez roomId, WASD/strzałki, stan i leave. Zamknięcie klienta nie zabija innych graczy; ostatni leave autoDispose sprząta worker. onCreate/worker failure/timeout i SIGTERM/SIGINT muszą zatrzymać tylko własne children. Bounded pending writes/requests, timeouts i limity stdout; żadnego shell:true ani komendy JVM od klienta.

## Testy/dowody
Java: real Game objects, fixed steps, normalized speed, sequence/validation/timeout, join/leave/room limits, scene close i owner thread. Real worker process test join→input→snapshot→leave→shutdown oraz EOF/malformed/oversize lifecycle.
Node: real localhost Colyseus+Java process i dwóch SDK klientów: create/join, baseline/late join, movement server-authority, stale/malformed/teleport injection rejected, leave/rejoin, separate room isolation, all workers close. Nie mockuj Colyseus ani Java authority. Worker crash/startup failure tests i server shutdown cleanup. npm test ma timeout i nie zostawia procesów. Testy uruchamiaj z built installDist. Raport zapisuje wersje, zakres, source commit i real counts.

## Poza zakresem
Reconnection token/preservation, prediction/interpolation FPS, Java Colyseus SDK, auth/production security, shared JVM rooms, natywna fizyka pokoju, engine browser/Wasm. Browser preview prostych punktów to klient gateway, nie port renderera Javity. Android/web modułu Java pozostają UNVERIFIED.

User web kierunek: Yoga i Bullet przez osobne adaptery Wasm; MapLibre GL JS; browser JS zamiast Graal. Nie zakładać przeniesienia JNI ani bezpośrednich wywołań lokalnej Javy ze strony. JVM authority przez sieć pozostaje zgodny z tym kierunkiem.

## Uściślenia preflight (wiążące)
- JSON UTF-8, limit8192 bajtów bez LF sprawdzany podczas czytania (również niedokończonej linii); wersja `v:1`, `roomId` string w każdej ramce; komendy mają `type`, `requestId` string. `join`/`leave`: `sessionId`; `input`: `sessionId`, `sequence`, `x`, `z`; `shutdown` bez sessionId. Sekwencje bezpieczne integer0..9007199254740991, początkowe lastSequence=-1. Stare/niepoprawne wejście NIE odnawia timeoutu. `ready`: v/roomId/type. `ack`: v/roomId/type/requestId (dla zaakceptowanego join/leave/shutdown); `error` dodatkowo `code`/`message`, requestId jeśli poprawne. Snapshot: `type:snapshot`, tick, players jako tablica {sessionId,x,z,lastSequence}; tick bezpieczny integer. Stale input może być zignorowane bez ack, invalid input error. Unknown/extra fields, w tym `position`, `targetId`, muszą być odrzucone; gateway nigdy nie przekazuje obcego sessionId klienta.
- Java output: bounded kolejka max64 ramek, osobny daemon writer stdout; owner nie blokuje na write. Overflow/writer failure kończy main loop i Game.close na owner thread; writer nie zamyka Game. Shutdown ma bounded drain i supervisor kill deadline. Test niedrenowanego stdout flood i zamknięcia wejścia, proces kończy się bounded bez zalegania Game/thread.
- SDK preview/test po create/join wyłącza `room.reconnection.enabled`. Abrupt failure test po >5 sekundach uptime sprawdza brak timerów retry. Reconnect preservation nie jest wspierane.
- Jeden gateway naraz w Node test process (Colyseus matchmaker globalny). Konstruktor `gracefullyShutdown:false`, stop przez `await server.gracefullyShutdown(false)`; sygnały obsługuje tylko CLI. Room isolation na jednym gateway. Brak concurrent niezależnych fixtures w tym samym procesie.
