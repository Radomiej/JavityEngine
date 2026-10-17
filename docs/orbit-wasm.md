# Publikacja Javity WebAssembly w ORBIT

Integracja bazuje na `modernization/java25-gradle-yoga` / `0195f965ac8e061501ae878ec52fde50f8a07c1d`. Kod znajduje się na `feat/orbit-wasm-publishing`. Po stronie ORBIT wymagany jest branch `feat/javity-wasm-compat` z repozytorium Radomiej/nekko-launcher. Wdrożenie API pozostaje na prywatnym VPS.

## Build i publikacja

Wymagane: JDK 25, Python 3, dostęp do repozytoriów Gradle/Maven. Edytuj `experiments/javity-wasm/orbit-game.json`: ustaw własne id, nazwę i nową, niezmienną wersję gry. Origin oznacza schemat, host i port, bez końcowego ukośnika; podaj rzeczywiste adresy launchera, rozdzielone przecinkami.

```sh
./gradlew -p experiments/javity-wasm orbitPackage \
  -PorbitLauncherOrigins=https://play.example.com --no-daemon --max-workers=2
```

Wynik: `experiments/javity-wasm/build/orbit/javity-wasm.nekko`. Zawiera Wasm, loader, zasoby, przypięty SDK oraz manifest z SHA256 każdego pliku. Możesz wgrać go przez Studio w ORBIT albo opublikować z CLI:

```sh
ORBIT_API_URL=https://api.example.com ORBIT_TOKEN_FILE=/private/orbit-token \
  ./gradlew -p experiments/javity-wasm orbitPublish \
  -PorbitLauncherOrigins=https://play.example.com --no-daemon --max-workers=2
```

Token krótkotrwały konta Developer/Admin wygeneruj narzędziem `admin publish-token EMAIL` na serwerze ORBIT i zapisz w prywatnym pliku. Developer publikuje tylko własne gry. Publisher wymaga HTTPS i nie przekazuje sekretu przy przekierowaniu. Samodzielny CLI: `python3 tools/orbit/publish.py --package PATH`; lokalne HTTP wymaga jawnego `--allow-http-local`. Tokenów nie umieszczaj w grze ani repozytorium. Dla kolejnej publikacji zwiększ wersję.

## API gry i zapisy

`OrbitBrowser` jest adapterem wyłącznie browserowym; silnik desktopowy nie otrzymuje zależności od TeaVM. Przykład asynchronicznego wywołania w Javie:

```java
if (OrbitBrowser.available()) {
  OrbitBrowser.request("player.get", "{}",
      json -> { /* profil gracza; sprawdź lifecycle sceny */ },
      errorJson -> { /* błąd API */ });
}
```

Most `JavityOrbit.request` dopuszcza profil, zapisy, prywatne asety, achievements, multiplayer i ranking. Uprawnienia nadal egzekwuje host ORBIT; obecność metody nie daje grze uprawnień. Transport to SDK/MessagePort i jawna allowlista originów. Gra nie otrzymuje tokenu konta ani publikacji.

Demo pobiera profil z Javy. Panel ORBIT nad canvasem zapisuje i odczytuje pozycję postaci w slocie `progress`; Java stosuje przywrócenie w pętli renderowania. Zapis używa `expectedRevision`, a konflikt nie nadpisuje cudzej rewizji. Ponowne uruchomienie odtwarza pozycję. Zamknięcie gry zwalnia port, a callbacki po zamknięciu nie zmieniają stanu. Poza iframe demo działa samodzielnie, bez połączenia z ORBIT.

## CSP i obsługiwany runtime

TeaVM JSO używa dynamicznych funkcji JavaScript. Manifest jawnie wybiera `scriptPolicy: "teavm-jso"`; zgodne API ORBIT zezwala na `unsafe-eval` tylko dla zasobów tej gry. Pozostałe gry zachowują restrykcyjną politykę. Packer przenosi inline JS do osobnych plików; nie wymaga `unsafe-inline` dla skryptów. Origin gry i sandbox iframe pozostają odseparowane od konta i launchera.

Wymagany Wasm GC i WebGL2. Potwierdzono Chromium/SwiftShader, rzeczywisty renderer Javity i API ORBIT. Nie potwierdzono Safari, Android WebView, iOS ani całego silnika (native Yoga, Bullet, MapLibre pozostają poza tym eksperymentem). Tauri/Capacitor wymagają osobnej weryfikacji silnika WebView i skonfigurowania jego originu.

## GitHub Actions

Workflow `ORBIT WebAssembly package` buduje paczkę na branchu i publikuje artefakt do pobrania. Ustaw repository variable `ORBIT_LAUNCHER_ORIGINS`. Publikacja jest wyłącznie ręczna, z nową wersją i `publish=true`; environment `orbit-publishing` wymaga variable `ORBIT_API_URL` oraz secret `ORBIT_PUBLISH_TOKEN`. Można ustawić ochronę environment. Przycisk ręcznego uruchamiania może wymagać zarejestrowania workflow na default branchu; CLI działa niezależnie. Push sam nie publikuje gry na VPS.

## Weryfikacja 2026-10-17

- Python packer/publisher: 5 testów; hashe, zewnętrzne skrypty, symlinki, brak przekazania tokenu przez redirect.
- `./gradlew build --no-daemon --max-workers=2`: sukces całego domyślnego reactora; raporty obejmują 871 przypadków w 99 zestawach (część z cache). Pierwsze uruchomienie z większą równoległością ujawniło przeciążenie kolejki istniejącego testu worker stdout; kod workera nie był zmieniany.
- `orbitPackage` i testy eksperymentu: sukces; osobny proof standalone potwierdza rendering bez ORBIT.
- Rzeczywisty launcher/API/PostgreSQL/S3: publikacja paczki, profil przez Java/JSO, sterowanie, zapis, odczyt i przywrócenie po ponownym otwarciu. Canvas bez DOM: 1146 pomarańczowych i 9293 zielonych pikseli; WebGL2, glError=0, brak błędów strony.
- ORBIT: cargo clippy z `-D warnings`, testy Rust i 4 integracyjne testy pakietów przeszły.

Dowody: [launcher](artifacts/orbit/launcher.png), [sam canvas](artifacts/orbit/canvas.png), [wynik JSON](artifacts/orbit/verification.json). Rendering programowy nie jest testem wydajności GPU.

Powtórzenie proof wymaga testowego konta z grą w bibliotece i sesji w prywatnym pliku; Python Playwright/Pillow oraz Chromium:

```sh
python3 tools/orbit/launcher-proof.py --launcher-url http://localhost:5175 \
  --api-url http://localhost:5175/api --session-file /private/test-session \
  --game-id TWOJA_GRA --evidence-dir artifacts/orbit
```
