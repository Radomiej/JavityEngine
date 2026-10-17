# Tworzenie gry w Javity

Javity ma jeden model GameObject + Component. Gameplay możesz pisać w Javie; opcjonalny TSX/React tworzy natywne UI. GraalJS Community działa na zwykłym JDK 25. Wspólne demo Relay Ward łączy animacje, feedback, audio, transakcyjne sceny i natywny HUD React/Yoga.

## Uruchomienie

```sh
./gradlew build
./gradlew :javity-desktop:run --args='--mm'
./gradlew :javity-desktop:mmSmoke
./gradlew :javity-desktop:run --args='--ui-parity'
```

Desktop wymaga OpenGL. W Relay Ward strzelasz kliknięciem w arenę, Space albo przyciskiem Fire; H leczy, D rozpoczyna Deploy, Escape anuluje ładowanie. Suwak i Mute sterują audio oraz przeżywają zmianę sceny. Tab/Shift+Tab przenoszą focus; skróty świata ustępują kontrolkom UI. `--ui-parity` porównuje bazowe widgety z referencją HTML/Tailwind.

Kod kompletnego hosta: `javity-desktop/src/main/java/org/javity/demo/MmSystemsDemo.java`. HUD: `web/javity-ui/src/mm.tsx`; wspólne drzewo referencyjne: `web/javity-ui-reference/mm-fixture.json`. To przykład składania niezależnych API, nie wymagany szablon każdej gry.

## Gra blokowa i postać 3D

`--voxels` uruchamia świat chunków z prostym kontrolerem AABB. `--physics3d` uruchamia osobną scenę Bullet z dynamicznymi bryłami i natywnym kontrolerem kapsułowym. W obu widokach postaci F5 przełącza FPS/TPP; ręce i narzędzie są osobnym widokiem tej samej animowanej postaci. W demie Bullet F3 pokazuje geometrię kolizji, E uruchamia użycie narzędzia, a przyciski myszy edytują bloki.

```sh
./gradlew :javity-desktop:run --args='--voxels'
./gradlew :javity-desktop:run --args='--physics3d'
./gradlew :javity-desktop:physics3dSmoke
```

Do własnej gry wybierz jednego właściciela ruchu postaci. Natywnemu kontrolerowi przekazuj wejście, a rigid body przemieszczaj poleceniami fizyki; bezpośrednia edycja Transform3D po związaniu z Bullet jest odrzucana. Grafika pożycza modele i tekstury. Widoki części avataru określają widoczność, animator pozostaje jedynym właścicielem pozy kości. [Avatar](avatars.md), [fizyka 3D](physics3d.md) i [voxele](voxels.md) opisują konfigurację, własność i ograniczenia.

F1 w demie inspektora otwiera drzewo GameObjectów z transformacjami, undo/redo, gizmos i jawnym eksportem SceneJson. Eksport potrzebuje skonfigurowanych codeców i celu zapisu; nie pomija nieobsługiwanych komponentów. [Instrukcja narzędzi runtime](runtime-tools.md).

## Właściciele i zegary

Rejestruj systemy przed `game.start()`. Dostawcę zasobów rejestruj przed konsumentem. Zamknięcie sceny najpierw sprząta komponenty, następnie systemy w odwrotnej kolejności rejestracji. Nie zwalniaj pożyczonego Model, fontu ani tekstury w komponencie.

```java
var game = new Game();
var radio = game.addSystem(new Radio());
var time = game.addSystem(new TimeManager());
var audio = game.addSystem(new SoundManager());
game.addSystem(new EffectSystem());
var health = radio.channel("health", Double.class, 100.0);
var hero = game.createObject("hero");
var feedback = hero.addComponent(new FeedbackPlayer(Clock.UI));
game.start();
```

Klasy tego przykładu pochodzą z `org.javity.core`, `org.javity.signals`, `org.javity.services`, `org.javity.audio` i `org.javity.effects`. Każdy system należy do jednej sceny. `Clock.WORLD` podlega pauzie i skali czasu; `Clock.UI` działa w nieskalowanym czasie. Aktualizacje audio/HUD-u mogą zatem działać podczas hit-stop.

## Feedback bez gubienia zasobów

```java
var hit = Feedback.parallel(
    Feedback.resource(.20, () -> audio.play(hitSound, AudioBus.SFX)),
    Feedback.resource(.20, () -> time.freeze(.06)));
var running = feedback.play(hit);
// running.cancel() zamyka tylko dzierżawy tego odtwarzania.
```

`hitSound` wcześniej wczytuje właściciel zasobów i pożycza go SoundManagerowi. Fabryki wykonują się dopiero, kiedy krok naprawdę startuje. Przy anulowaniu lub błędzie posiadane uchwyty zostają zamknięte. Do sterowania jednym głosem używaj VoiceHandle, zamiast globalnego Sound.stop(). [Pełny kontrakt](feedback-api.md).

## Zdarzenia i sekwencje

```java
var sequencer = hero.addComponent(new Sequencer(Clock.WORLD));
var burst = Sequence.builder(1)
    .cue(0, () -> launch())
    .cue(.5, () -> impact())
    .build();
sequencer.play(burst, 120, false).onComplete(() -> ready());
```

Importy sekwencji: `org.javity.sequence`. Cue są wyrażone w beat, tempo w BPM. Wywołania launch/impact/ready to własne metody gameplay. Nowy playback zaczyna się w następnej kwalifikującej klatce; sekwencja nie jest osobnym globalnym schedulerem.

## Animacja i UI

[API animacji](animation-api.md) opisuje klipy, rzeczywiste crossfade, seek i named markers. [Port OneJS](onejs-port.md) opisuje budowanie TSX, useSignal, kontrolowane widgety i jawne akcje Java. Nie bundluj drugiej kopii React i nie udostępniaj dowolnych obiektów Java skryptowi. [Zakres funkcji i dowody](plugin-feature-coverage.md) opisuje sprawdzone zachowania i granice poszczególnych odpowiedników.

## Wejście UI i świata

Umieść Stage przed adapterem gameplay w InputMultiplexer. Pomijaj skróty świata, kiedy `stage.getKeyboardFocus() != null`: pole tekstowe lub nieaktywny widget może nie zużyć keyDown, a to nie powinno uruchomić strzału/zmiany animacji. Gdy Stage nie zużył kliknięcia w obszar gry, adapter może wyczyścić focus i wykonać akcję. Ogranicz takie kliknięcia do pola gry, żeby dekoracyjne elementy HUD-u nie wyzwalały akcji pod spodem.

## TSX i szybka iteracja

Zbudowane bundle są zasobami modułu javity-script; zwykłe uruchomienie gry nie wymaga Node. Po zmianie produkcyjnego TSX wykonaj `npm run build` w `web/javity-ui`. Do pracy nad własnym HUD-em uruchom w dwóch terminalach:

```sh
cd web/javity-ui
npm ci
npm run dev
```

```sh
./gradlew :javity-desktop:run --args="--mm --ui-dev=$PWD/web/javity-ui/dist/javity-ui-dev.js"
```

Drugi terminal uruchom z głównego katalogu repozytorium. `src/dev.tsx` jest działającym przykładem: `useSignal('health')`, `useAction('fire')` i kontrolowane Slider/Toggle. Importuj shim `./runtime.js`; nie dołączaj drugiej kopii React. Health ma zakres 0–1, volume 0–1, muted jest boolean. Akcje fire/heal/deploy/cancel nie przyjmują argumentów; volume przyjmuje number, mute boolean.

Watcher zapisuje ignorowany bundle lokalny. Host sprawdza go na wątku UI: poprawna zmiana odtwarza drzewo React, błąd składni pozostawia poprzednie UI. Błąd wykonania po rozpoczęciu reload zamyka runtime — nie jest transakcją cofającą gameplay. Zobacz [pełny kontrakt reload](onejs-port.md).

## Sygnały przy zmianie sceny

Utrzymuj ustawienia w hoście, a health i stan postaci w Radio bieżącej sceny. Po `SceneManager.step` najpierw wykryj nową scenę i podmień jej źródła sygnałów, potem emituj generację/status i wykonaj tick React. Callback zakończenia ładowania zapisuje flagę Java; nie powinien uruchamiać JS pomiędzy zamknięciem starej sceny a podpięciem nowej.

Komponent React z `key={generation}` odtwarza hooki sceny. Ustawienia mogą być jego persistent rodzeństwem. Natywny aktor o tym samym globalnym id i typie może zachować tożsamość mimo remountu React. Trwałe akcje hosta pobierają aktualny controller, zamiast przechowywać obiekt poprzedniej sceny.
