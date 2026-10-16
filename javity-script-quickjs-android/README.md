# Provider QuickJS dla Androida

Moduł `javity-script-quickjs-android` dostarcza `QuickJsScriptUiEngineFactory`, czyli adapter silnika JavaScript dla przenośnego hosta `javity-script`. Wspólny `ScriptUiRuntime` i `ScriptUiHostSession` zarządzają Reactem, akcjami, sygnałami, timerami, kolejką microtasków, cyklem życia i zatwierdzaniem drzewa UI. Provider desktopowy `javity-script-graal` wybiera GraalJS przez `ServiceLoader`; aplikacja Android jawnie wstrzykuje fabrykę QuickJS. Adapter QuickJS nie dodaje zależności Graal ani Truffle.

## Użycie

Dodaj moduł jako zależność aplikacji Android, zainicjalizuj Androidowy backend Yoga i utwórz runtime na wątku UI. QuickJS, `UiDocument` i Scene2D muszą działać na tym samym wątku właściciela.

```java
AndroidYogaBackend.initialize(context);
UiDocument document = new UiDocument(new AndroidYogaBackend(), null);
ScriptUiRuntime runtime = new ScriptUiRuntime(
    document, new QuickJsScriptUiEngineFactory());
runtime.bindAction("confirm", () -> save());
runtime.eval("JavityGame.call('confirm')");
runtime.tick(deltaSeconds);

// Najpierw zamknij runtime; później zamknij pożyczony document.
runtime.close();
document.close();
```

`ScriptUiRuntime` pożycza `UiDocument`; zamknięcie runtime odmontowuje drzewo React i zwalnia powiązania hosta, ale dokument zamyka wywołujący. Silnik sprawdza składnię przed reloadem, zachowuje istniejące UI przy błędzie składni i wykonuje reload na wątku właściciela.

## Zachowanie adaptera

Adapter używa QuickJS-KT 1.0.15. Udostępnia synchroniczne host bindingi `FunctionBinding`, kompilację źródła do sprawdzenia składni, ewaluację przez API suspend opakowane w synchroniczny interfejs hosta oraz zamknięcie odroczone do końca aktywnego wywołania JNI. Ustawione limity to 64 MiB pamięci, 512 KiB stosu i 2 sekundy na ewaluację. Hostowe zadania asynchroniczne nie są wystawione jako bindingi; timery i `queueMicrotask` przechodzą przez przenośny scheduler hosta i postępują przy `runtime.tick(...)`.

Host nie używa wartości końcowej zwykłych jednostek skryptu. Adapter dopisuje do nich końcowe `void 0`, aby QuickJS nie traktował zwróconego Promise jako wyniku do oczekiwania. To nie jest reguła odrzucania top-level Promise. Zakres pozostaje zależny od API QuickJS-KT i wspólnego hosta; nie deklarujemy zgodności z całym środowiskiem OneJS ani pełnego zestawu API przeglądarki.

Provider nie jest automatycznie odkrywany z modułu Android: launcher przekazuje `new QuickJsScriptUiEngineFactory()` jawnie. `javity-ui-yoga-android` pozostaje osobnym providerem Yoga i wymaga inicjalizacji z Androidowym `Context` przed utworzeniem dokumentu.

## Status

Adapter przechodzi `assembleDebug` z Android Gradle Plugin. Pełny build desktopowy potwierdził 641 testów Java. Wszystkie 7 testów instrumentacyjnych w `src/androidTest` przeszło na ART/JNI emulatora API 35 x86_64: React, akcje i sygnały, timery, sprzątanie/reload oraz przerwanie nieskończonej pętli. [Zachowane wyniki](../docs/artifacts/android/quickjs-verification.json) dotyczą adaptera i hosta; pełna aplikacja z renderingiem wymaga osobnej weryfikacji.
