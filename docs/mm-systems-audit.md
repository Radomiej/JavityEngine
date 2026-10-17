# Audyt Feel i pozostałych systemów More Mountains

2026-10-14. Otworzono publiczną dokumentację Feel 6.1: [indeks](https://feel-docs.moremountains.com/), Core Concepts/MMF_Player, katalog feedbacków, tworzenie własnego feedbacku, springs/screen shakes oraz wszystkie 12 stron sekcji Other tools. To audyt kontraktów i mapowanie na Javity; nie import komercyjnego kodu Unity ani deklaracja pełnej zgodności.

## Co jest już wdrożone

`EffectSystem`, `TweenPlayer` i `FeedbackPlayer`: cele własnego GameObject, Transform2D/kolory sprite i figur, UI alpha, zegary WORLD/UI, sequence/parallel/wait/callback, anulowanie KEEP_CURRENT/RESTORE_START, dzierżawy właściwości i cleanup. [API](feedback-api.md). Named animation/crossFade modeli są w osobnym [API animacji](animation-api.md); OneJS/React steruje nimi przez jawne akcje Java.

## Bieżący etap rozbudowy

Zakończono i niezależnie sprawdzono typowane `EventBus`/`Radio`/`Signal`, enum `StateMachine`, `TimeManager` z dzierżawami slow motion i hit-stop, `SoundManager`, pulę floating text, beat sequencer, custom/resource feedback oraz additive shake/punch/screen flash. Pauza świata nie zatrzymuje UI. Relay Ward łączy te systemy w rzeczywistym renderze OpenGL; [pokrycie](plugin-feature-coverage.md) i [przewodnik](game-authoring.md) opisują bieżące zachowanie i granice.

React jest wykonywany przez GraalJS Community **na zwykłym JDK 25**; instalacja dystrybucji GraalVM nie jest wymagana. To własny host React/Scene2D/Yoga z ograniczonym katalogiem utilities, a nie przeglądarka osadzona w grze.

## Mapa systemów

| Przeczytana dokumentacja | Stan Javity | Kontrakt i granice |
| --- | --- | --- |
| [Tweens and Events](https://feel-docs.moremountains.com/tweens-events.html) | Tweeny, callbacki i typowany EventBus działają | Typowane scene-local zdarzenia z jawnie zwalnianą subskrypcją |
| [MMTools](https://feel-docs.moremountains.com/mmtools.html) | Są helpery kamery, timery, input, zasoby i UI; nie cały katalog MMTools | Pooling, sygnały i FSM jako małe moduły, bez globalnych singletonów |
| [MMSoundManager](https://feel-docs.moremountains.com/mmsoundmanager.html) | SoundManager: buses, voice IDs, volume/pitch/pan, fades/ducking, wyjątkowo bezpieczne sprzątanie; native OpenAL null sprawdzone w Relay Ward (bez odsłuchu) | Master/Music/SFX/UI buses, voice handles, pooling, ducking/fades; cancel zatrzymuje tylko własny głos |
| [MMAudioAnalyzer](https://feel-docs.moremountains.com/mmaudioanalyzer.html) | Brak FFT i analizy audio | PCM/FFT/bands/beat events; jawny backend danych, bo libGDX Sound/Music nie udostępnia miksu PCM do analizy |
| [MMSequencer](https://feel-docs.moremountains.com/mmsequencer.html) | Immutable Sequence/Track/Cue, BPM i playhead, loop, cue0, WORLD/UI; 1024 cue/boundaries na klatkę | Dane ścieżek/kroków, tempo, playhead, loop i zsynchronizowane wyzwalanie; nie drugi scheduler czasu |
| [MMRadio](https://feel-docs.moremountains.com/mmradio.html) | Scene-local Radio/Signal + useSignal React i jawne subskrypcje działają | Kanały/sygnały, odczyt zmian, remap, jawne typed targets; to sygnały lokalne, nie transport multiplayer |
| [MMProgressBar](https://feel-docs.moremountains.com/mm-progress-bar.html) | ProgressBar foreground/delayed gain/loss; pełna ścieżka React i piksele sprawdzone | Foreground + delayed gain/loss bars, easing i feedback; bez nadpisywania layoutu Yoga |
| [MMSceneLoading](https://feel-docs.moremountains.com/mm-scene-loading.html) | Transakcyjne ładowanie, uchwyty/fade, cancel/failure i agregacja błędów sprawdzone; native PNG/cancel/success/failure sprawdzone w Relay Ward | Asynchroniczny postęp ładowania, ekran/overlay, anulowanie; additive worlds wymagają osobnego modelu własności |
| [MMStateMachine](https://feel-docs.moremountains.com/mmstatemachine.html) | Enum StateMachine z enter/exit i obserwacją przejść działa | Enum/typed states, enter/exit, obserwacja zmian i deterministyczne przejścia bez refleksji |
| [MMTimeManager](https://feel-docs.moremountains.com/mmtimemanager.html) | TimeManager: bazowa skala i dzierżawy, UI clock, rzeczywisty Box2D 2×/freeze/resume sprawdzone | Nakładane slow motion/hit-stop z uchwytami, czas nieskalowany, reset przy zamknięciu sceny; Box2D ma zachować stały krok |
| [MMFloatingText](https://feel-docs.moremountains.com/mm-floating-text.html) | FloatingTextPool: domyślnie 32 sloty, pełny reset, stale generations, WORLD/UI; native render sprawdzony w Relay Ward | Pooling, world-to-screen, kanały, animacja/kolor/fade, lifetime i pełne resetowanie zasobów |
| [Nice Vibrations](https://feel-docs.moremountains.com/nice-vibrations.html) | Brak wdrożonego backendu haptics | Capability-based API + gamepad/mobile adapters; nie deklarować HD haptics bez testu sprzętu |

## Integracja i dalsze rozszerzenia

Relay Ward demonstruje additive punch/shake, prezentacyjną kamerę i picking, flash, czas WORLD/UI, pooling/audio, animacje, 1D pose mixer oraz masked override layer. Natywny HUD React ma Toggle/Slider, keyboard/focus, wrapping i obrazy; wspólna referencja HTML/Tailwind sprawdza geometrię, tekst, kolory i kształty. Uruchom `./gradlew :javity-desktop:run --args='--mm'`.

FFT/haptics, zaawansowane materiały/postprocessing, produkcyjny importer rigów i dodatkowe katalogi feedbacków wymagają osobnych etapów. Nie są ukryte pod nazwą istniejącego API.

[Pełna lista feedbacków](https://feel-docs.moremountains.com/list_mmfeedbacks.html) pokazuje, że pełne Feel obejmuje znacznie więcej niż tween player: particles, postprocessing, audio, transformacje 3D, UI Toolkit, czas i shakers. [Tworzenie feedbacku](https://feel-docs.moremountains.com/creating_mmfeedbacks.html) wskazuje osobne initialization/play/stop/reset. Publiczny Feedback.custom/resource zapewnia już leniwe tworzenie, hooki lifecycle i dokładnie jednokrotne cleanup, również po anulowaniu lub błędzie.

Otwarto wskazane strony dokumentacji. Nie audytowano wszystkich klas API ani osobnych silników TopDown/Corgi/Highroad i nie uruchamiano ich Unity demos. Pozostałe kontrakty śledzi [plan rozbudowy](superpowers/plans/2026-10-14-mm-systems.md). Nie deklarujemy pełnego Feel 1:1. Szczegóły gotowych odpowiedników i ograniczeń zawiera [pokrycie funkcji](plugin-feature-coverage.md).
