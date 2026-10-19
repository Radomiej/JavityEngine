# Błędy rdzenia API — 2026-10-19

Baza: `2ec7684`, po integracji ORBIT. Zmiany dotyczą CPU lifecycle i membership komponentów; nie są nową walidacją GPU ani Androida.

- [x] **BUG-API-001 / P2 — usuwanie równej zamiast wskazanej instancji.** GameObject.removeComponent używał List.remove(Object), czyli equals. Dwa komponenty z równym equals pozwalały usunąć pierwszy, ale oznaczyć i zakolejkować drugi; indeksy i lifecycle rozchodziły się. Także obca równa instancja mogła usunąć komponent. Naprawa: wyszukanie dokładnej instancji przez == i usunięcie jej indeksu. Regresje sprawdzają membership, indeks sceny, stan initialized oraz tożsamość payloadu powiadomienia.
- [x] **BUG-API-002 / P2 — self-suppression przerywa cleanup.** Powtarzane rzucenie tego samego RuntimeException/Error z callbacków powodowało Throwable.addSuppressed(this), czyli IllegalArgumentException. W pętli cleanup mogło to pominąć kolejne komponenty/obiekty. Naprawa: nie dopisywać tego samego obiektu błędu, zachowując agregację różnych błędów. Regresje obejmują Component, GameSystem, kolejne komponenty i obiekty, kolejkę usuniętych oraz Game.onDestroy.
- [x] **BUG-API-003 / P2 — flush zasłaniał pierwotny błąd aktualizacji.** Komponent usuwa się podczas onUpdate i rzuca błąd; onRelease również rzuca. Wcześniejszy finally zwracał wyłącznie błąd cleanup. Naprawa: pierwotny błąd aktualizacji pozostaje zgłaszany, błąd cleanup staje się suppressed. Również identyczny AssertionError nie powoduje self-suppression, a kolejny krok potwierdza odblokowanie straży stepping.

## Weryfikacja

Pierwsze siedem testów LifecycleSafetyTest odtworzyło błędy na niezmienionym rdzeniu: **7/7 FAIL**. Po poprawce te same testy przeszły. Dodany ósmy przypadek pokrywa identyczny AssertionError w aktualizacji i cleanup; pełny reactor obejmuje wszystkie osiem. Nie zmieniono lookup po typie, kolejności faz ani zasad odroczonego zwalniania.

Polecenia końcowe i wyniki są zapisywane w podsumowaniu [przeglądu API](../Spec/2026-10-19-engine-api-review.md). Otwarty BUG-UI-001 i pozostałe braki platform pozostają osobnymi zadaniami; ten przegląd ich nie zamyka.
