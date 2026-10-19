# Integracja Javity Wasm z ORBIT

Baza: modernization/java25-gradle-yoga, commit0195f965ac8e061501ae878ec52fde50f8a07c1d. Osobna gałąź feat/orbit-wasm-publishing. Istniejący eksperymentalny gdx-teavm WasmGC/WebGL2 jest punktem integracji; nie zmieniamy desktopowego core ani nie deklarujemy pełnej zgodności silnika z web.

## Wynik

- Zadanie Gradle orbitPackage buduje aktualne WASM i pakuje cały runtime oraz zasoby do wersjonowanego .nekko v1.
- Manifest i SHA256 są zgodne z importerem nekko-launcher. Pakiet zachowuje struktury katalogów i względne URL, a skrypty inicjalizacji są zewnętrzne dla zgodności z CSP launchera (bez unsafe-inline dla JS).
- Do paczki trafiają przypięty SDK ORBIT, jawna allowlista originów launchera i mały most JavityOrbit. Tokeny kont/publikowania nigdy nie trafiają do paczki.
- Java/TeaVM ma asynchroniczny most przez JSO. Przykładowa scena odczytuje profil, zapisuje i odtwarza pozycję z chmury z expectedRevision; callback po dispose nie mutuje sceny.
- CLI i ręczny GitHub Actions workflow mogą opublikować .nekko przez /v1/admin/packages z konta Developer/Admin. Token wyłącznie z prywatnego pliku/sekretu CI; bez przekierowań HTTP.

## Plan wykonania

1. Bazowe testy core i istniejący build WASM; zapisać strukturę wygenerowanego loadera.
2. RED/GREEN: testy pakowania z hashami, transformacją CSP, zachowaniem WASM/resources i odrzuceniem symlinków/wyjścia poza limity.
3. Most SDK i Java oraz przykład cloud save; test MessagePort/profile/CAS/cleanup, osobno standalone.
4. Gradle orbitPackage/orbitPublish i workflow z artefaktem, dokumentacja i provenance SDK.
5. Pełny reactor, build WASM i rzeczywisty proof w launcherze ORBIT: obraz/sterowanie/profile/save/reload, bez błędów CSP. Osobno wskazać wynik środowiska Chromium/SwiftShader i brak certyfikacji mobile/Safari.
6. Commit i push wyłącznie nowej gałęzi; nie scalać gałęzi bazowej.

## Stan wykonania

Punkty 1–5 ukończone. Dowody i ograniczenia w [instrukcji](../orbit-wasm.md). Zmiana backendu jest odseparowana na `Radomiej/nekko-launcher:feat/javity-wasm-compat`; bez automatycznego wdrożenia ani merge do main.

## Aktualizacja2026-10-19

Użytkownik zlecił naprawę integracji i aktualizację gałęzi głównych; zastępuje to historyczne ograniczenie punktu6 do osobnego brancha. Zmiany są integrowane z aktualnym masterem Javity i main launchera, z zachowaniem późniejszych poprawek. Workflow buduje paczkę na master i w PR; publikacja gry nadal wymaga jawnego ręcznego uruchomienia oraz konfiguracji środowiska. Nie utożsamiaj pushu źródeł z wdrożeniem API ani wysłaniem paczki.
