# OneJS → Javity native UI

Zaimplementowany natywny host inspirowany publicznym OneJS (2026-10-14). Zakres jest opisany poniżej; nie jest to zgodność z całym Unity API. Źródła sprawdzone przed implementacją:

- [OneJS f36ff5e / 3.9.8](https://github.com/Singtaa/OneJS/tree/f36ff5e41a16eeddf40e775f28fa7eee856894a0), [MIT](https://github.com/Singtaa/OneJS/blob/f36ff5e41a16eeddf40e775f28fa7eee856894a0/LICENSE).
- [NodeBridge](https://github.com/Singtaa/OneJS/blob/f36ff5e41a16eeddf40e775f28fa7eee856894a0/Runtime/NodeBridge.cs): uporządkowane dołączanie/usuwanie elementów.
- [StyleBridge](https://github.com/Singtaa/OneJS/blob/f36ff5e41a16eeddf40e775f28fa7eee856894a0/Runtime/StyleBridge.cs): aplikacja zmienionych stylów i usuwanie poprzednich wartości.
- [QuickJSUIBridge](https://github.com/Singtaa/OneJS/blob/f36ff5e41a16eeddf40e775f28fa7eee856894a0/Runtime/QuickJSUIBridge.cs): kontekst dokumentu, eventy, tick i teardown.
- [onejs-react 51cb714 / 0.4.0](https://github.com/Singtaa/onejs-react/tree/51cb714bcedaf5f6adc0bf2601db2920ae19e40d), [renderer](https://github.com/Singtaa/onejs-react/blob/51cb714bcedaf5f6adc0bf2601db2920ae19e40d/src/renderer.ts): React 19/Reconciler 0.31, mutation host, mount/unmount i cleanup.

## Mapowanie hosta

| OneJS/Unity | Javity |
| --- | --- |
| VisualElement/View | UiNode View → Flex/Yoga |
| Text/Label | Scene2D Label |
| Button | Scene2D button + callback dokumentu |
| TextField | Scene2D TextField, controlled value/change |
| Toggle / Slider | Kontrolowane natywne widgety, typowane onChange |
| ScrollView | Scene2D ScrollPane + Flex |
| QuickJS context/bridge | Opcjonalny GraalJS ScriptUiRuntime |
| React host mutations | JS host instances → keyed UiDocument |
| USS/classes | Jawny podzbiór UtilityStyles, className |
| Unity Update tick | Jawny tick na wątku UI; czas z pętli Game |

Całego C# OneJS nie można uruchomić w JVM: zależy od Unity UI Toolkit, QuickJS native binding i Unity bridge typów. Przenosimy jego kontrakt hosta i fragmenty React adaptera, zastępując te zależności. Uwagi o pochodzeniu i licencjach pozostają w THIRD_PARTY.

Markup natywny jest podzbiorem XML-compatible HTML. TSX jest kompilowany do JavaScript poza JVM; JSX elementy stają się elementami Yoga/Scene2D. To dwie drogi do tego samego hosta. Nie deklarować dowolnego browser DOM/CSS, USS ani Unity API.

Moduł JS musi pozostać opcjonalny. JS context, schedulery, event handlers i React roots mają wspólny lifetime. Unmount zachodzi przed zamknięciem contextu, aby cleanup hooks nadal mogły się wykonać. GraalJS nie uruchamia Node APIs; npm biblioteka musi działać w embedded runtime. Java reflection/IO nie są częścią publicznego bridge.

## Uruchomienie aplikacji

`javity-script` zawiera GraalJS Community 25.0.2 i bundlowany host React 19. Java build nie potrzebuje Node. Aby zmienić TSX:

```sh
cd web/javity-ui
npm ci
npm run build
npm test
```

Aplikacja importuje React/hooki z `src/runtime.js`, który korzysta z już załadowanego globalnego `JavityReact`. Nie bundluj drugiej kopii React ani hosta. Gradle używa sprawdzonych do repo wyników `javity-ui.js` i `javity-sample.js`.

```java
UiDocument document = new UiDocument(theme);
ScriptUiRuntime runtime = new ScriptUiRuntime(document);
runtime.eval(appJavaScript);
runtime.tick(deltaSeconds); // wątek UI, niezależnie od pauzy WORLD
// Przy zamykaniu: runtime.close(); document.close();
```

`eval`, `tick`, `reload` i `close` działają na wątku utworzenia runtime. Runtime posiada kontekst JS, pożycza dokument. Unmount uruchamia cleanup hooków przed zamknięciem kontekstu. Reload po błędzie składni zachowuje stary kontekst; błąd wykonania nowego skryptu zamyka nowy kontekst i czyści widok.

Host obsługuje rzeczywiste `useState`, `useLayoutEffect`, uporządkowane rodzeństwo, fragmenty, keyed reuse oraz zdarzenia kontrolek. `id` wybiera globalny klucz dokumentu; klucze React mają zwykłe znaczenie lokalne wśród rodzeństwa. Dostępne elementy i props opisuje [deklaratywne UI](declarative-ui.md). Style inline: width/height/padding/gap oraz hex color/backgroundColor; reszta wymaga jawnej implementacji.

Schedulery `setTimeout`, `setInterval`, RAF i `queueMicrotask` korzystają z `tick`, ze wspólnym limitem 10 000 callbacków. Kod synchroniczny i wewnętrzne Promise jobs GraalJS nie podlegają temu licznikowi. Runtime jest przeznaczony do zaufanych skryptów gry. Nie dostarcza Node, browser DOM, arbitralnego Java reflection ani plikowego/network IO.

## Bridge do gry

Java rejestruje nazwane akcje, odczyty oraz pożyczone sygnały:

```java
runtime.bindAction("hit", () -> feedback.play(hitFeedback));
var healthSignal = radio.channel("health", Integer.class, 100);
runtime.bindSignal("health", healthSignal);
```

JavaScript wywołuje `JavityGame.call("hit")` i odczytuje `JavityGame.get("health")`. Wartości są null/string/boolean/skończoną liczbą double (duże integer mogą utracić precyzję), bez eksportowania obiektów Java. Rejestracje zachowują się przy reload, a `unbindAction`, `unbindValue`, `unbindSignal` i close zwalniają referencje. Nieznana nazwa zgłasza błąd; caller decyduje o walidacji komend gameplay. Przykładowe `feedback`, `hitFeedback` i `health` to obiekty własnej gry, nie globale silnika.

## Zamknięcie z callbacku

Akcja zarejestrowana przez `JavityGame` może zamknąć runtime wewnątrz zdarzenia React. Runtime staje się od razu niedostępny, a fizyczny unmount/zwolnienie kontekstu czeka na powrót z aktywnego callbacku JS. Późniejsze state updates nie odtwarzają native UI; pozostałe timery/microtaski są pomijane. Cleanup hooków może wywołać zarejestrowane akcje/odczyty Java podczas jawnego unmount, po czym referencje zostają zwolnione. `reload` wywołany ze zwykłego callbacku JS jest odrzucany przed zmianą kontekstu. Wąski wyjątek: reload zażądany podczas callbacku `Signal` jest odkładany do bezpiecznej granicy zewnętrznego `eval`, `tick` lub zdarzenia natywnego. Ostatnie poprawne żądanie wygrywa; stara praca i subskrypcje są anulowane, a samonapędzający się reload nie wykonuje się rekurencyjnie. Błąd składni żądanej wersji zachowuje działający widok i subskrypcje. Weryfikacja obejmuje rzeczywisty przycisk React zamykający runtime, po którym JS próbuje jeszcze zmienić stan.

Uruchomienie korzysta ze zwykłego JDK 25 i bibliotek GraalJS Community. Instalacja osobnej dystrybucji GraalVM nie jest wymagana.

## Sygnały i kontrolowany postęp

Aplikacja TSX importuje `useSignal` z tego samego shimu co React. Subskrypcja powstaje w efekcie i zamyka się przy unmount; sygnał Java nadal należy do gry. Publikuj na wątku UI runtime. Rebind lub reload usuwa stare subskrypcje JS, a zarejestrowane źródła Java pozostają dostępne.

```tsx
import { React, JavityReact, useSignal } from './runtime.js';
const { Progress } = JavityReact;
function HealthBar() {
  const health = useSignal('health');
  return <Progress id="health" className="h-3 rounded-full bg-slate-800 text-cyan-400"
                   min={0} max={100} value={health} duration={0.3} delay={0.15} />;
}
```

`duration` i `delay` są sekundami, muszą być skończone i nieujemne. Natywny Stage aktualizuj nieskalowanym czasem, aby animacja HUD-u działała podczas pauzy świata. Progress z tym samym globalnym id i typem może zachować natywny widget i jego animację przy przeniesieniu między kontenerami w jednym commit. Sam React key działa lokalnie wśród rodzeństwa i nie gwarantuje tej tożsamości; zmiana rodzica może odtworzyć komponent React i jego subskrypcje.

## Ustawienia i klawiatura

Java rejestruje typ argumentu akcji; bridge sprawdza go przed wywołaniem gameplay:

```java
runtime.bindNumberAction("volume", value -> settings.setVolume(value));
runtime.bindBooleanAction("mute", checked -> settings.setMuted(checked));
runtime.bindStringAction("playerName", name -> settings.setPlayerName(name));
```

`settings` jest obiektem aplikacji. Akcje przyjmują dokładnie jeden argument odpowiedniego typu; liczby muszą być skończone. Akcja `bindAction` pozostaje bezargumentowa.

```tsx
import { React, JavityReact, useSignal, useAction } from './runtime.js';
const { Toggle, Slider } = JavityReact;
function AudioSettings() {
  const muted = useSignal('muted');
  const volume = useSignal('volume');
  const setMuted = useAction('mute');
  const setVolume = useAction('volume');
  return <>
    <Toggle checked={muted} onChange={setMuted} />
    <Slider min={0} max={1} value={volume} step={0.1} onChange={setVolume} />
  </>;
}
```

Host musi również zarejestrować sygnały `muted` i `volume`. Widget przekazuje proponowaną wartość; faktyczny stan zmienia dopiero nowy prop React. Gameplay może odrzucić lub przekształcić propozycję. `useAction` zachowuje stabilny callback dla tej samej nazwy i zwraca `undefined`: akcje są bez wyniku, odczyty wykonuj przez sygnały lub `JavityGame.get`.

Efekt React powinien zwracać tylko funkcję cleanup albo `undefined`. Przy bezpośrednim wywołaniu bridge użyj bloku: `useEffect(() => { JavityGame.call('fire'); }, [])`. Sam bridge zwraca `null`, więc zapis `useEffect(() => JavityGame.call('fire'), [])` dawałby niepoprawną wartość cleanup.

`UiDocument.mount(stage)` dołącza dokument do pożyczonego Stage i obsługuje Tab/Shift+Tab między aktywnymi kontrolkami. Enter/Space aktywuje Button/Toggle, strzałki zmieniają Slider. `onFocus`, `onBlur`, `onKeyDown`, `onKeyUp` dotyczą rzeczywistych zdarzeń Stage; zwrócenie `true` z handlera klawisza zatrzymuje jego dalszą obsługę. Stage umieść przed adapterem gameplay i pomijaj skróty świata, gdy UI ma keyboard focus. Runtime zamykaj przed dokumentem, a dokument przed Stage/theme.

## Praca nad TSX

`npm run dev` w `web/javity-ui` obserwuje `src/dev.tsx` i zapisuje ignorowany `dist/javity-ui-dev.js`. Aplikacja używa istniejącego Reacta hosta. Przykład wymaga jawnych akcji i sygnałów wymienionych w kodzie; nie tworzy gry ani tych rejestracji automatycznie.

```java
var reload = new ScriptUiReloader(runtime, bundlePath); // java.nio.file.Path
// Na wątku UI, np. co 250 ms:
var result = reload.poll();
var diagnostic = reload.lastError(); // Optional<String>
// Przed zamknięciem runtime:
reload.close();
```

Pierwszy poll próbuje wczytać plik. Kolejne zwracają `UNCHANGED`, `RELOADED`, `ERROR` lub `CLOSED`; porównanie zawartości wykrywa również zmiany tej samej długości i daty modyfikacji. Plik ma limit 4 MiB i ścisłe UTF-8. Odczyt jest synchroniczny, dlatego helper służy do pracy developerskiej i powinien mieć umiarkowaną częstotliwość odpytywania. Nie posiada runtime ani dokumentu.

Błąd składni zostawia dotychczasowe UI, akcje i subskrypcje. Poprawny reload sprząta stare hooki, timery i subskrypcje. Błąd wykonania po rozpoczęciu wymiany zamyka runtime; wykonane już akcje Java pozostają i host musi odtworzyć runtime. Niezmieniony błędny skrypt nie jest ponownie parsowany. Przy zmianie sceny host najpierw rebinduje jej sygnały, a dopiero potem publikuje `sceneGeneration`; przykład kluczuje tym sygnałem poddrzewo HUD-u, żeby jego hooki podpięły nowe źródła.

## Wspólne demo

`./gradlew :javity-desktop:run --args='--mm'` uruchamia Relay Ward z produkcyjnym `mm.tsx`. Host i HUD demonstrują utrzymanie ustawień oraz zmianę źródeł health/droneState/canFire po transakcyjnym przełączeniu sceny. Gotowy workflow TSX i lokalnego `--ui-dev=<bundle>` opisuje [przewodnik tworzenia gry](game-authoring.md).
