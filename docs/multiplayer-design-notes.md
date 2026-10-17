# Multiplayer — wstępny projekt

> Aktualizacja: użytkownik wskazał Colyseus Rooms, Java server authority i lokalny podgląd. [Aktualna rekomendacja i roadmap browser/Wasm](rooms-and-browser-roadmap.md) rozwija te wcześniejsze notatki. Multiplayer pozostaje projektem; nie dodano jeszcze transportu ani replikacji.


Cel użytkownika: model obiektów i komponentów wygodny jak w Roblox, z możliwością określenia części serwerowej/klienckiej i synchronizacji wybranych zmiennych. To plan; obecny rdzeń nie ma transportu, headless server ani replikacji.

Roblox udostępnia drzewo Instance, mechanizm replikacji, skrypty serwera/klienta oraz komunikację RemoteEvent/RemoteFunction. Silnik zarządza też network ownership symulacji fizycznej. Inspirujemy się podziałem odpowiedzialności; samo skopiowanie GameObject/Component nie odtwarza platformy, edytora ani usług Roblox.

## Proponowany podział

- Każda strona uruchamia własny Game. Rendering/UI/map textures są tylko na klientach. Dedykowany serwer używa core + physics bez inicjalizacji GL.
- NetworkIdentity ma trwały w sesji networkId, ownerClientId i jawne authority: SERVER albo OWNER. Lokalne GameObject.id nie jest ID sieciowym.
- Komponent określa execution side: SERVER, CLIENT lub BOTH; jest to metadana runtime sieciowego, nie rozproszone if w każdym hooku.
- Replicated fields deklarowane jawnie w schema/codec; stabilne numery, typy, wersjonowanie, dirty tracking. Nie serializujemy dowolnych prywatnych pól przez refleksję ani natywnych handles/texture/font.
- Serwer zatwierdza spawn/despawn, zmiany ownership i istotny gameplay. Klient wysyła walidowane komendy/intencje; deklaracja OWNER nie oznacza nieograniczonej możliwości zmiany świata serwera.
- Snapshot zawiera tick i networkId; zmiany stanu mogą użyć unreliable transport, spawn/despawn i komendy wymagają określonej niezawodności/kolejności.
- Na klientach interpolacja z buforem snapshotów; prediction/reconciliation dopiero dla wybranych kontrolerów. Box2D nie traktujemy jako deterministycznego lockstep między platformami.
- Interest management ogranicza replikację do zainteresowanych klientów. Late join dostaje baseline i późniejsze delty; potrzebna też obsługa usuniętych IDs.

## Etapy późniejsze

1. Sprawdzony headless Game+Physics i stały tick serwera; oddzielenie czasu klatki klienta od ticka sieciowego.
2. In-memory transport w testach; NetworkIdentity, spawn/despawn i dwa jawne pola testowe.
3. Snapshot/delta, late join, ownership i walidacja komend; test utraty/opóźnienia/duplikatów.
4. Wybrany transport, klient/server demo, interpolacja; dopiero potem prediction, interest i trwały świat.

Obecna architektura daje scenowe rejestry i core bez Artemis/GL w gameplay. Wymaga dalszego oddzielenia renderera/assets od symulacji i nie deklaruje automatycznej synchronizacji każdego komponentu.

Kontekst technologii: [Roblox client-server runtime](https://create.roblox.com/docs/projects/client-server), [network ownership](https://create.roblox.com/docs/physics/network-ownership). Opis stanowi notatki architektoniczne, nie wynik audytu wewnętrznego kodu Roblox.
