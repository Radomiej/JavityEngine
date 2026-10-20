# Przegląd integracji API — 2026-10-20

Wpisy dotyczą implementacji przygotowanej przez agentów na bazie908b758; nie oznaczają błędów wcześniejszej opublikowanej wersji.

- [x] **BUG-API-004 / P2 — aktywacja panelu przed końcem onCreate.** Flaga created służy też cleanup nieudanej inicjalizacji, dlatego nie wystarcza do wysyłania hooka widoczności. Osobne activationReady dopiero po udanym onCreate; regresja z reentrant zmianą enabled/active podczas onCreate.
- [x] **BUG-API-005 / P2 — przerwany cleanup anulowanych operacji.** Fasada platformy kończy anulowanie wszystkich żądań i zamyka providera mimo błędów cancel. Zachowany pierwszy błąd, kolejne różne suppressed; owner-thread cancel i odrzucenie spóźnionych/zdublowanych callbacków.
- [x] **BUG-API-006 / P2 — niezgodne DTO ORBIT.** Room list/create/join/close miały błędny początkowy kształt. Dopasowane do rzeczywistego SDK/hosta956386c, parsery odrzucają brak wymaganych pól i błędne rewizje. Java i panel wymieniają ostatnio zaobserwowaną rewizję zapisu; test Wasm/MessagePort sprawdza naprzemienne zapisy bez konfliktu i odrzucenie złych współrzędnych.
- [x] **BUG-API-007 / P2 — delta bez pełnego baseline i z pominiętą rewizją.** Klient wymaga pełnego snapshotu początkowo/po reconnect/gapie. Niepoprawny payload nie zatwierdza części stanu ani historii; unknown despawn jest idempotentny przy różnych baseline peerów. Testy obejmują pełny reset pokoju, tombstones i ruch po TCP.
- [x] **BUG-API-008 / P2 — rebind i konsumpcja źródła.** Wymiana tych samych przytrzymanych klawiszy odbywa się atomowo, bez sztucznych release/press. Menu konsumuje źródło fizyczne, również gdy akcja gameplay ma inną nazwę; testy unregister, focus loss i anulowania touch.
- [x] **BUG-API-009 / P2 — ukryty panel zachowywał fokus.** Zmiana visible/touchable nie wystarcza Stage keyboard/scroll/touch focus. Przy ukryciu Stage.unfocus zwalnia fokus poddrzewa; test sprawdza klawiaturę i scroll, native smoke sprawdza widoczność po aktywacji/dezaktywacji.
- [x] **BUG-API-010 / P2 — niezweryfikowany nadawca komendy na granicy wire.** Kodek komend nie przyjmuje sender z JSON; host przekazuje osobno tożsamość zweryfikowanej sesji. Test odrzuca podszywający się payload. Walidowane są wersje, pola, rozmiary, schema IDs i base64.

**BUG-UI-001 pozostaje otwarty.** Lokalny pomiar Card title: browser range76.34/canvas76.33, ink75; native GlyphLayout79, ink79. To pokazuje różnicę miar layoutu, ale samo nie wyjaśnia dodatkowej kolumny różnicy rasteryzacji na hostowanym CI. Nowe diagnostyki nie stanowią naprawy ani pełnej zgodności HTML/CSS.

Wyniki końcowe: [artefakty](../artifacts/authoring-api/README.md). Nie deklarujemy nowego testu Androida, prywatnego API ani produkcyjnego multiplayer na podstawie tych testów.

- [x] **API-011 — save response ordering and payload integrity.** Older read replies no longer regress the current save revision or restore stale positions. Payloads are canonicalized as a single JSON value before request assembly; JSON `null` remains a stored value, distinct from a missing save. Covered by Java regressions and three deterministic Node race cases.
