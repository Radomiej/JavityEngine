# Tweeny i feedback — specyfikacja pierwszego rozszerzenia

Status: realizacja zatwierdzona poleceniem /goal użytkownika; task A planu plugin-ports. Data: 2026-10-14. Kontekst: [kierunek OneJS/Animancer/Feel](../../unity-inspired-api.md).

## Cel i kryterium użyteczności

Użytkownik chce prostego silnika komponentowego z wygodnym UI, kontrolą animacji i efektami podobnymi do narzędzi Unity. Pierwszy batch dostarcza małe, własne API animowania właściwości oraz odtwarzania kompozycji efektów w Java/libGDX. Sukces: hit/button może uruchomić scale/color pulse z opóźnieniem i callbackiem, a scena może go anulować bez pozostawienia opóźnionych zapisów. Nie wymaga JavaScript, Unity, nowych natywnych zależności ani dodatkowego ECS.

## Zakres

W `javity-core`: Ease, TweenHandle, TweenPlayer, typowane FloatTarget i Feedback/FeedbackPlayer. W pierwszej wersji cele ograniczone do Transform2D własnego obiektu oraz kanałów koloru SpriteRenderer2D/ShapeRenderer2D na tym samym obiekcie. Brak dowolnej refleksji, lambda-setterów w publicznych fabrykach i celów z innej sceny.

Feedback ma wait, tween, sequence, parallel i callback. Audio integracja pozostaje następnym krokiem, ponieważ AudioSource.play() nie zwraca uchwytu konkretnego głosu. Nie można obiecać anulowania odtwarzania pojedynczego efektu przez zatrzymanie całego AudioSource. Nie dodajemy shake kamery, hit-stop, particles, JS, UI adapterów, pooling, JSON graph ani editor tooling w tym batchu.

## Architektura i własność

Czysty wewnętrzny TimelineRunner zarządza czasem i uchwytami bez zależności od GPU. TweenPlayer i FeedbackPlayer są komponentami z własnym runnerem, nie tykają siebie nawzajem. Tylko jeden komponent odtwarzania może mieć aktywną własność tej samej właściwości; scene-local PropertyLeases używa tożsamości obiektu/właściwości i tokenów generacji. Rejestr jest powiązany z Game, bez globalnego singletona; wpisy usuwane po completion/cancel/release. Wszystkie zapisy i callbacki na wątku Game.step.

Obiekty opisu Feedback są niemodyfikowalne i nie zawierają bieżącego czasu. Każde play tworzy odrębny stan odtwarzania. Nie współdzielić handle między odtworzeniami. Target jest pożyczony; gracz nie niszczy obiektu, komponentu, fontu, texture ani materiału.

## Kontrakt czasu i interpolacji

Clock.WORLD ustawia UpdateMode.WORLD, Clock.UI ustawia UpdateMode.UI. WORLD zatrzymuje się podczas pauzy i używa czasu sceny; UI używa unscaled delta istniejącego Game. Disable/inactive zatrzymuje czas, destroy/remove anuluje. Clock nie zmienia się podczas playback.

Duration i delay muszą być skończone i nieujemne; wartości początkowe/końcowe skończone. Ease: LINEAR, IN_QUAD, OUT_QUAD, IN_OUT_QUAD. Interpolacja z double intermediates i walidacją wyniku float. Czas lokalny w double, endpoint zapisany dokładnie. Tween o czasie zero rozpoczyna się i kończy przy najbliższym update, nie synchronicznie w play(). Rozpoczęcie oznacza pobranie aktualnej wartości celu, również dla odroczonego kroku sekwencji.

Handle: RUNNING, COMPLETED, CANCELLED. cancel jest idempotentny. onComplete wywołane raz tylko po naturalnym końcu. Cancel domyślnie KEEP_CURRENT, opcjonalnie RESTORE_START. Restoration tylko dla wciąż ważnego targetu i aktualnej dzierżawy. Supersede zawsze KEEP_CURRENT przed przechwyceniem nowej wartości. Nieaktualny uchwyt nie może odtworzyć starej wartości.

## Feedback i przepływ

Sequence wykonuje dzieci po kolei, parallel uruchamia je razem i kończy po ostatnim. Wait zużywa czas bez zapisów. Pozostały czas bieżącego update przechodzi przez zakończone kroki sekwencji; bez utraty czasu między klatkami. Callback jest jednorazowym krokiem o czasie zero. Skończony acykliczny opis bez pętli gwarantuje ograniczoną liczbę kroków przy duration=0.

Parallel z dwoma tweenami tej samej właściwości odrzucany podczas walidacji opisu. Dwa oddzielne playback konkurujące o właściwość: nowe przejmuje dzierżawę; starego rodzica anulujemy kaskadowo, aby nie uruchomił dalszych kroków później. Pozostałe równoległe playback na innych właściwościach pozostają aktywne.

Callbacks wykonywane po zmianie stanu i zwolnieniu dzierżawy. Kolekcje iterowane jako snapshot; callback może anulować peer, usunąć komponent/obiekt albo rozpocząć nowy playback. Nowe play z callbacku zaczyna się dopiero przy następnym update. Po callbacku sprawdzić ważność ownera i handle przed kontynuowaniem. Błąd callbacku oznacza anulowanie jego playback i propagację; finally sprząta jego zasoby, bez deklaracji że Game automatycznie zamknie całą scenę.

## Przykładowa powierzchnia API — planowana

```java
var tweens = object.addComponent(new TweenPlayer(Clock.WORLD));
var move = tweens.to(Targets.positionX(object), 120f, .25f, Ease.OUT_QUAD);
move.cancel(CancelMode.KEEP_CURRENT);

var feedback = object.addComponent(new FeedbackPlayer(Clock.WORLD));
var hit = Feedback.sequence(
    Feedback.tween(Targets.scaleX(object), 1.15f, .08f, Ease.OUT_QUAD),
    Feedback.tween(Targets.scaleX(object), 1f, .14f, Ease.OUT_QUAD),
    Feedback.callback(() -> finished()));
var playback = feedback.play(hit);
```

Przykład opisuje zwykły scale tween, nie additive punch. Jeśli gameplay równocześnie zapisuje scale, musi użyć innego pola albo jawnie anulować efekt; rejestr kontroluje graczy API, nie dowolne publiczne zapisy. Fabryki Targets sprawdzają ważność obiektu i wymagany komponent; nie używać has+get w update.

## Testy i weryfikacja

- Manual Game.step: WORLD pause/timeScale, UI podczas pause, disable/active, endpoint/ease/duration0 i invalid values.
- Same-property replacement między graczami, brak starych restoration, cleanup rejestru po destroy i zamknięciu sceny.
- Sequence carry-over, parallel completion, wait, callback kolejność, cancel cascade oraz konflikt właściwości w parallel.
- Callback rozpoczyna kolejny playback, anuluje peer, usuwa ownera i rzuca wyjątek; brak kolejnych zapisów/callbacków i leaków.
- Osobne demo --feedback: figury SDF, pulse/color tween, WORLD/UI pause, cancel/reset i resize. Test pikseli końcowego koloru/skali oraz brak GL error; bez odsłuchu czy zależności internetowych.
- Pełny reactor i regresje istniejących pięciu smoke; wyniki i podgląd w docs. Bez commit/push.

## Dalsze batchy

AnimationPlayer2D/3D z named clips/state/time/speed/once/loop/crossFade/events jako osobny projekt. Generowany test node animation sprawdza semantykę, ale nie zastępuje GL testu rzeczywistego skinningu. Deklaratywne UI i optional JS/TSX osobno. Camera shake dopiero po oddzielnym additive presentation offset i testach projection/picking/follow.
