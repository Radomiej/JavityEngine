# Dokończenie aktualizacji desktop/core

Cel użytkownika: dokończyć aktualizację, migrując podstawowe dotychczasowe funkcje do nowego rdzenia. Legacy i źródła pluginów pozostają w repo dla zgodności danych. Nowe API bez Artemis/globalnych singletonów.

Zakres: polygon/raycast/force/impulse; audio komponentowe; tekst/linie/figury i animacja sprite; input actions/picking; scene manager, timery, zasoby; prefaby i jawny schemat serializacji; asynchroniczne mapy HTTP/disk cache; przykłady i testy.

Natywne Body/Fixture nie opuszczają physics. Raycast zwraca snapshot, callbacki nie wykonują gameplay wewnątrz lock. Audio/assets mają jawną własność. Sceny przełączają się na granicy klatki; timery używają czasu Game i anulują się przy dispose. Serializacja używa allowlist codeców, bez dowolnego instancjonowania klas po nazwie. Input za Stage w multiplexer, reset button state na granicy klatki. HTTP ma limit pending, retry/backpressure, lokalny cache i konfigurowalny URL/atrybucję; testy lokalne.

Nie obejmuje przyszłych MapLibre, multiplayer, pełnego Shapes/PBR ani migracji Android/web/iOS. Spine legacy pozostaje zgodny z historycznym runtime; nie deklarujemy zgodności z nowymi eksportami. Weryfikacja: natywne testy, cały reactor, rzeczywiste smoke GL i demo migracji.
