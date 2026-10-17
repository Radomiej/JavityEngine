# Tweeny i feedback

Nowe API jest w `org.javity.effects` (javity-core). To własny odpowiednik podstawowych sekwencji Feel; nie wymaga Unity, JavaScript ani zasobów GPU.

```java
game.addSystem(new EffectSystem()); // przed game.start()
var object = game.createObject("button-feedback");
var tweens = object.addComponent(new TweenPlayer(Clock.WORLD));
var feedback = object.addComponent(new FeedbackPlayer(Clock.WORLD));
game.start();

var move = tweens.to(Targets.positionX(object), 120, .25, Ease.OUT_QUAD);
move.onComplete(() -> finished());
move.cancel(CancelMode.KEEP_CURRENT);

var pulse = Feedback.sequence(
    Feedback.tween(Targets.scaleX(object), 1.15f, .08, Ease.OUT_QUAD),
    Feedback.tween(Targets.scaleX(object), 1f, .14, Ease.IN_OUT_QUAD));
var playing = feedback.play(pulse);
playing.cancel(CancelMode.RESTORE_START);
```

Cele należą do obiektu gracza: positionX/Y, scaleX/Y, rotation i kanały RGBA SpriteRenderer2D/ShapeRenderer2D. EffectSystem przechowuje scene-local dzierżawy; nowe aktywne odtwarzanie tej samej właściwości anuluje starsze. Przy restore ważny jest nadal aktualny token i żywy cel. RESTORE_START dotyczy bieżących aktywnych tweenów; nie cofa już zakończonych kroków całej sekwencji. Bezpośrednie zapisy gameplay do publicznego Transform2D nie są kontrolowane przez ten rejestr.

Clock.WORLD zatrzymuje się z pauzą świata; Clock.UI używa czasu unscaled. Wyłączenie komponentu/obiektu zatrzymuje aktualizacje; usunięcie anuluje odtwarzanie. Duration=0 zapisuje wynik przy update. Czas jest skończony i nieujemny, wartości skończone; interpolacja ma double intermediates.

Feedback.waitFor, callback, sequence i parallel tworzą niemodyfikowalne opisy z osobnymi stanami playback. Sequence przenosi pozostały czas przez kolejne kroki; parallel odrzuca dwa kroki zapisujące tę samą właściwość. Callback-created playback rusza w następnej klatce, również przy pause i delta=0. Callback może usunąć gracza/obiekt; dalsze zapisy są wtedy blokowane. Błąd callbacku propaguje i zwalnia odtwarzanie. Completion callbacks po utracie właściciela są ignorowane.

Podstawowe tweeny zapisują scale/color. TimeManager obsługuje już hit-stop/slow motion, a SoundManager — własne voice handles i buses. Additive punch/shake oraz screen flash mają osobne API prezentacji opisane poniżej. Particles pozostają osobnym tematem. Nie używaj globalnego AudioSource.stop/Sound.stop do anulowania pojedynczego feedbacku współdzielącego źródło.

Testy lifecycle są w EffectsTest; integrację GPU sprawdza demo portów. Wyniki całego etapu są w modernization-status.md.

Natywne UI można wygaszać przez `UiTargets.alpha` z opcjonalnego modułu `javity-ui`; [kontrakt](declarative-ui.md#efekty-bez-naruszania-layoutu). Callback React może uruchamiać feedback przez jawne `JavityGame.call`; [bridge](onejs-port.md#bridge-do-gry).

## Własne efekty i posiadane zasoby

`Feedback.custom(duration, factory)` tworzy nowy `FeedbackEffect` dla każdego faktycznie rozpoczętego odtwarzania. Hooki `start`, `update(elapsed, delta)`, `complete` i `close` mają domyślne implementacje. Fabryka jest leniwa; `close` wykona się raz także po anulowaniu, usunięciu właściciela lub błędzie hooka. Dla duration=0 kolejność to start → update(0,0) → complete → close. Zasoby ukryte w fabryce, która rzuci przed ich zwróceniem, musi posprzątać sama fabryka.

`Feedback.resource(duration, factory)` upraszcza pracę z AutoCloseable:

```java
var hit = Feedback.parallel(
    Feedback.resource(.20, () -> audio.play(hitSound, AudioBus.SFX)),
    Feedback.resource(.20, () -> time.freeze(.06)));
var running = feedback.play(hit);
// Anulowanie zamyka wyłącznie uchwyty pozyskane przez to odtwarzanie.
running.cancel();
```

`audio` (`SoundManager`), `time` (`TimeManager`) i `EffectSystem` rejestruj przed startem sceny. `hitSound` jest wcześniej wczytanym, pożyczonym zasobem. Lease freeze sam wygasa po .06 s nieskalowanego czasu; dłuższy krok resource zapewnia też możliwość jego anulowania po rozpoczęciu. Lease utworzony w callbacku kwalifikuje się od następnej klatki, więc krok resource trwający 0 lub kończący się w tej samej aktualizacji zamknąłby go przed użyciem. Do niezależnego czasu dźwięku/HUD-u wybierz `FeedbackPlayer(Clock.UI)`. Dostawcę zasobu rejestruj przed konsumentem; komponenty sprzątają się przed systemami, a systemy w odwrotnej kolejności rejestracji.

Cleanup kontynuuje po błędzie jednego zasobu i zachowuje kolejne wyjątki jako suppressed. Nie wykonuje `complete` przy anulowaniu. Checked exception z AutoCloseable.close jest opakowany z zachowanym cause. Fabryka anulująca własny playback przed zwróceniem zasobu również nie gubi tego zasobu.

EffectSystem ma wspólny limit 1024 zwykłych wywołań callbacków/hooków na klatkę gry, obejmujący fabrykę i hooki custom. Po jego przekroczeniu odtwarzanie jest anulowane, a posiadane zasoby sprzątane. Cleanup omija limit pracy, aby nie zostawić zasobów; nowa praca utworzona w callbacku nadal czeka na kolejną klatkę. Limit nie ogranicza czasu wykonywania dowolnego kodu wewnątrz pojedynczego hooka.

## Shake, punch i flash

`PresentationEffects` sumuje niezależne wkłady w `Presentation2D`; nie zmienia bazowego Transform2D używanego przez gameplay/fizykę. Przykład po starcie sceny:

```java
var presentation = object.addComponent(new PresentationEffects(Clock.WORLD));
var shake = presentation.shake(6, 3, .02f, .25, 20, 1234L);
var punch = presentation.punchPosition(80, 0, 8, .5, .4);
shake.cancel(); // punch pozostaje aktywny
```

Argumenty punch X/Y są początkową prędkością impulsu, a nie maksymalnym wychyleniem. Oscylator obsługuje tłumienie poniżej, równe i powyżej krytycznego. Shake używa deterministycznego seed i ciągłego czasu; wkład zanika dokładnie na końcu. Skala jest sumowana jako przyrost. Uchwyty można pause/resume/cancel; ich stare generacje nie anulują nowego efektu.

Przesunięcia shake są w jednostkach świata, amplituda obrotu w radianach, czas w sekundach, a częstotliwość w Hz.

Renderer 2D, picking i Camera2D używają tej samej pozy prezentacyjnej. ModelRenderer i Camera3D nie otrzymują shake z tego API. Limit to 64 wkłady na obiekt i 64 aktywne efekty gracza; przy pełnym wspólnym limicie gracz może wyprzeć wyłącznie własny najstarszy wkład. Usunięcie lub wymiana Presentation2D unieważnia stare uchwyty.

```java
var screen = game.addSystem(new ScreenEffects(1280, 720)); // przed startem
// Po game.start():
var flash = screen.flash(Color.WHITE, .35f, .18);
// Przy zmianie rozmiaru: screen.resize(width, height);
```

Flash zaczyna się od peak i zanika liniowo w nieskalowanym czasie UI, także podczas hit-stop. System posiada pulę 64 ekranowych figur; nie tworzy własnych zasobów GPU. Testy jednostkowe obejmują wkłady, lifetimes i wartości graniczne. Native mmSmoke potwierdził rzeczywiste piksele, kamerę i picking, niezmienioną transformację gameplay oraz cleanup podczas aktywnych efektów i ładowania sceny. [Wyniki](plugin-feature-coverage.md).
