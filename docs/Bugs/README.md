# Rejestr błędów

Przeglądy zapisują konkretną przyczynę, reprodukcję, status i dowód naprawy. Zaznaczone pole oznacza zweryfikowaną naprawę; otwarte pole oznacza nadal wymagane działanie. Brak implementacji lub brak testu platformy nie jest automatycznie błędem.

- [Review jakości Wasm i integracji — 2026-10-17](2026-10-17-wasm-integration-review.md).
- [Specyfikacja utrzymania i rozwoju](../Spec/2026-10-17-runtime-wasm-quality.md).
- [Zadania](../TODO.md).

Nowy wpis powinien mieć trwałe ID, priorytet, zakres wersji, kroki odtworzenia, oczekiwane zachowanie i bramkę zamknięcia. Historyczne dowody pozostają przypisane do źródłowego commita; nie traktujemy ich jako świeżych testów zmienionego kodu.

- [Android runner follow-up — reproduction limits and closure gate](2026-10-17-android-followup.md).

- [Publication and maintenance integration findings](2026-10-17-publication-review.md).

- [Tożsamość komponentów i błędy cleanup — 2026-10-19](2026-10-19-core-api-review.md).

- [Przegląd integracji nowych API — 2026-10-20](2026-10-20-authoring-integration.md).

## Jakość silnika — 2026-10-20

[Potwierdzone problemy, naprawy i bramki](2026-10-20-engine-quality.md):
Q-001/Q-002 zamknięte regresjami core; Q-003 ma zmierzony spadek alokacji CPU.
Osobne wpisy opisują naprawy glTF/VAO/FBO/root-motion/fontu. BUG-UI-001 zamknięty
świeżym zielonym Desktop CI39391819541/source`f2fafce`. [Pierwotny audyt](../engine-quality-review.md)
zachowuje historyczną reprodukcję sprzed poprawek.
