# API tworzenia gier — implementacja 2026-10-20

Użytkownik zatwierdził realizację przeglądu z2026-10-19 przez agentów pod nadzorem. Baza: `908b758`. Zakres został podzielony na pięć niezależnych obszarów oraz diagnostykę istniejącego BUG-UI-001; wspólne lifecycle i buildy integruje agent nadrzędny.

## Kontrakty i wykonanie

| Obszar | Wdrożony zakres | Granica potwierdzenia |
| --- | --- | --- |
| Usługi platformy | PlatformServices/PlatformProvider/PlatformResult, jawny LocalPlatformProvider, provider ORBIT, typowane profile/zapisy/pokoje i RoomTicket. Owner-thread pump, anulowanie i cleanup. | Profil ORBIT aliasuje player.get; lokalny provider nie wydaje ticketów. Syntetyczny host przetestował rzeczywisty Wasm/SDK/MessagePort, a nie prywatny VPS. |
| UI i zasoby | ResourceScope LIFO, leniwe Component.own, onActivationChanged po ukończeniu onCreate, UiDocumentComponent. Panel należy do GameObjecta, pożycza Stage/theme i zwalnia fokus po ukryciu. | Scena jest jednowątkowa; scope nie przenosi zwalniania na wątek GL. Bez drugiego inspektora lub GameObjecta na każdy div. |
| Wejście | InputContext/InputBinding, priorytety i konsumpcja fizycznych źródeł, atomowy rebind, klawiatura, analog/button feed, pointer/touch, focus loss i unregister. | Host dostarcza gamepad przez feed; nie dodano natywnego sterownika kontrolerów. Ponownie ujawniony przytrzymany klawisz generuje jawnie opisany press. |
| Prefaby | AssetId/PrefabId, niezmienne definicje, katalog wersji/migracje, jawny resolver i walidowane nadpisania istniejących pól skalarnych. | Resolver sprawdza deklarowane zależności i pożycza zasoby. Codeki same jawnie pobierają assety; brak automatycznej refleksyjnej iniekcji lub dowolnych overrides struktury. |
| Replikacja | Jawny registry/codec schematu, niezależne NetworkId, authority i walidowane komendy, spawn/update/despawn, pełne snapshoty, delta gap/reconnect, interpolacja, binding GameObject i kodek JSON v1. | Test prawdziwego TCP używa kodeka biblioteki. Nie jest to działający adapter Colyseus z ticketami ani prediction/reconciliation. Pełny snapshot jest resetem broadcastowanym wszystkim peerom. |
| Fonty | Rozdzielone pomiary advance/layout i thresholded ink; profile kolumn przy progach180/220/245 w HTML i native, logowanie przed asercją. | BUG-UI-001 nadal otwarty dla CI. Bez podnoszenia tolerancji i bez zmiany font-renderera. |

## Przegląd i poprawki integracyjne

Po pierwszych implementacjach sprawdzono granice odpowiedzialności: zgodność DTO z launcherem `956386c`, anulowanie po callbacku i błędy cleanup, budżet callbacków na pump, aktywację dopiero po onCreate, oddanie fokusu, atomowe rebindowanie, faktyczny format danych SceneJson, rollback i borrow assetów, konieczność pełnego snapshotu po reconnect oraz nieuwierzytelniony sender. Poprawiono błędy kompilacji wynikające z API JsonValue i zgodności JUnit oraz testowy fixture Stage; nie zastąpiono tych błędów ignorowaniem testów.

Nie dodano TeaVM/JSO do core, drugiego ECS, globalnego schedulera ani zależności od usług prywatnego API. Historyczne API i testy pozostają w buildzie.

## Plan integracji i bramki

1. Podział plików między agentów, kontrakty i dokumentacja modułowa — wykonane.
2. Przegląd kodu i przypadków błędów; wspólne hooki core i UI — wykonane.
3. Pełny reactor/ECS, testy UI i realnego transportu, osobny build/test Wasm i testy przeglądarkowe — wyniki w artefaktach poniżej.
4. Zaktualizować przewodniki, TODO, changelog i AGENTS, zapisać ograniczenia, wypchnąć na aktualny master bez force push.

Przewodniki: [platforma](../platform-services.md), [UI](../ui-game-object.md), [wejście](../input-actions.md), [prefaby](../prefab-pipeline.md), [replikacja](../game-replication.md). [Dowody i polecenia](../artifacts/authoring-api/README.md).

Dodatkowy review naprawił regresję rewizji przy spóźnionych odpowiedziach zapisu, kanonizację pojedynczego JSON oraz niestabilny test procesu serwera, który wcześniej mógł sprawdzać snapshot sprzed zastosowania ruchu.
