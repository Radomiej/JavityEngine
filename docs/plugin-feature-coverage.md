# Feel, Animancer i OneJS — zakres Javity

To własne API Java/libGDX inspirowane publiczną dokumentacją. Nie importujemy komercyjnych bibliotek Unity ani nie obiecujemy ich pełnej zgodności. Zakres opiera się na testach kontraktów, natywnym renderowaniu oraz porównaniach z prawdziwą przeglądarką. Nie obejmuje całego katalogu komercyjnych pakietów.

| Obszar | Wdrożone i sprawdzone | Dowód integracji / granica |
| --- | --- | --- |
| Feel: kompozycja | Tween/Feedback sequence, parallel, wait, callback, custom/resource; WORLD/UI, uchwyty i anulowanie; additive shake, springs/punch, flash | Relay Ward: render shake/camera/flash, rzeczywiste picking i hit-stop |
| Feel: prezentacja 3D | Osobne kanały modeli, kamery świata i viewmodelu; aktualna baza FOV i kontrola kolizji; brak zmian kanonicznego TRS. Presety punch, shake, recoil, spring, bob i FOV uzyskały niezależną akceptację przeglądu FIX1. | [Demo Feel3D i framebuffer GL30](feel3d.md), [zrzuty i pomiary](artifacts/feel3d/README.md), [dowody CameraRig3D](artifacts/presentation3d/README.md) |
| Feel: particles/postprocess | Deterministyczne additive billboard bursts z głębokością; world-only tint/desaturation/vignette | [Particles GL30](particles3d.md), [postprocess](world-postprocess.md); bez soft particles/bloom/HDR |
| Feel: analiza/haptics | Jawne PCM FFT/RMS/pasma; możliwości, dzierżawy i UNSUPPORTED | [DSP](audio-analysis.md), [haptics](haptics.md); bez dekodera miksu i backendu urządzenia |
| Feel: usługi | EventBus, Radio/Signal, enum FSM, TimeManager, SoundManager buses/fades/ducking, pooled floating text, beat sequencer z uporządkowanymi cue | Relay Ward: native OpenAL null i render pooled floating text |
| Feel: UI/sceny | Animated/delayed ProgressBar, aktualizacja React bez wymiany widgetu; transakcyjne ładowanie i fade | Relay Ward: rzeczywisty PNG, cancel/success/failure, odtworzone sygnały HUD-u |
| Animancer | Klipy modeli i sprite, pause/speed, callbacki, rzeczywisty legacy model crossfade, seek, named markers, reusable transitions, prawdziwy 1D mixer i masked override layers. ModelPoseAnimator obsługuje zamrożone źródło crossfade klipu bazowego i masked sparse warstwy; fade wagi warstwy jest niezależny. | Proceduralny rig z pikselami i macierzami; nie test importu produkcyjnego rigu. Dowody i ograniczenia crossfade pose opisuje [API animacji](animation-api.md). |
| Host UI inspirowany OneJS | Wspólny host React 19/TSX z wybieralnym silnikiem: GraalJS Community na desktopie i provider QuickJS na Androidzie. Host udostępnia keyed native actors, signals/hooks, jawne typowane akcje, kontrolki i ograniczony katalog widgetów. | Relay Ward i skompilowany dev.tsx na desktopie; [status QuickJS Android](../javity-script-quickjs-android/README.md). To ograniczony host Javity, nie pełna implementacja OneJS. |
| Tailwind/HTML | Natywna Yoga/Flexbox, zagnieżdżone View/div, jawne utilities, typography, rounded/border, stany kontrolek | Wspólny HUD 1280×720/DPR1: geometria stałych aktorów 0 px |

## Jak weryfikujemy

JUnit obejmuje lifetimes, walidację, deterministyczny czas i rzeczywiste pozy modeli; testy JavaScript sprawdzają host React. Desktop smoke uruchamia prawdziwy OpenGL i sprawdza framebuffer oraz macierze skinningu. Referencja UI pochodzi z Chromium i rzeczywistego Tailwind 4.3.3, z tym samym fontem, treścią, viewportem i DPR. Sama kompilacja ani headless test nie potwierdza wyglądu.

Dotychczasowa referencja bazowych kontrolek ma odchylenie geometrii do 2,12 px, pionowych granic tekstu do 1 px i szerokości glyphów do 4 px. Kolory wnętrz i bazowego obramowania input są zgodne w próbkowanych punktach; różnice kolorów stanów wynoszą najwyżej 1 poziom kanału. Szczegóły: [porównanie HTML](ui-html-parity.md). Toggle/Slider mają osobne zgodne fixture: geometria 0 px, pionowy obrys tekstu 0 px i szerokość do 2 px; kolory wnętrz do 1 poziomu kanału. Stabilne przypadki zawijania tekstu mają zgodne podziały słów i pionowy obrys; długie teksty 24 px blisko progu zawijania pozostają opisanym ograniczeniem. Obrazy mają geometrię 0 px i średnią różnicę RGB poniżej 1,74 w obu badanych stanach; schemat Image i jego walidacja leaf przeszły niezależny przegląd.

HUD Relay Ward ma osobny zestaw pięciu stanów: idle, hit, settings, loading i arena. Stałe ramki aktorów są zgodne (0 px); wybrane pionowe obrysy tekstu są zgodne, a szerokość krótkich napisów różni się do 2 px. Wnętrza próbkowanych elementów mają te same kolory. Krawędzie rasteryzowanych kontrolek mają tolerancję 4 px, a atlasowy portret średnią różnicę RGB 3,364. Szerokość naturalna dłuższego tekstu różni się maksymalnie o 5,55 px w tym zestawie; to jawna metryka diagnostyczna, nie deklaracja zgodności wszystkich glyphów. Zobacz `artifacts/mm-systems/mm-comparison.json`.

## Świadome granice

Desktop Yoga i grafika wymagają natywnych bindingów. Nie deklarujemy całego HTML/DOM/CSS/Tailwind, UI Toolkit ani kompatybilności prefabów Unity. Audio FFT wymaga źródła PCM; libGDX Sound/Music go nie udostępnia. HD haptics wymaga backendu i testu urządzenia. General 2D/additive model blending, root motion, IK, multiplayer oraz edytor Unity są osobnymi tematami.

## Źródła

- [Feel 6.1](https://feel-docs.moremountains.com/): katalog feedbacków, springs/screen shakes, custom feedback i strony Other tools; [mapowanie systemów](mm-systems-audit.md).
- [Animancer](https://kybernetik.com.au/animancer/docs/): playing, transitions, events, mixers i layers; [API animacji Javity](animation-api.md).
- [OneJS](https://github.com/Singtaa/OneJS) i publiczne źródła onejs-react. Strony onejs.com/docs zwróciły HTTP 403 podczas audytu, dlatego nie przypisujemy im nieprzeczytanej treści. [Port OneJS](onejs-port.md).
- [libGDX 1.14.2](https://github.com/libgdx/libgdx/tree/1.14.2): publiczne kontrakty keyframes, ModelInstance i skinning.

## Końcowa weryfikacja desktop — 2026-10-15

Czysty reactor: 335 testów Java, 0 failures/errors/skipped; host React: 10/10. Trzy produkcyjne bundle odtwarzają się identycznie bajtowo. Osiem trybów native przeszło OpenGL smoke: modern, map, runtime, shapes, plugins, UI parity, Relay Ward oraz legacy. Audio sprawdzono przez native OpenAL null, bez odsłuchu.

Gotowa dystrybucja uruchamia MM i UI parity poza checkoutem. Test rzeczywistego skompilowanego dev.tsx przeszedł edycję, błąd składni z zachowaniem poprzedniego UI, naprawę/reload, zmianę sceny i świeżą aktualizację health. Zamknięcie podczas ładowania i aktywnych efektów zwolniło 8/8 zasobów, anulowało kandydata i usunęło obie tekstury bez błędu GL.

Wszystkie paski w pięciu stanach mają zgodne końce wypełnienia do 1 px; kolorowe ślady mają zgodne końce i tint. [Screenshot native](demo-mm.png) i [HTML](demo-mm-html.png). Integralność lokalnej dystrybucji ZIP i wymagane zasoby/licencje zostały sprawdzone. GitHub Actions przygotowano, lecz nie uruchamiano zdalnie.

## Wspólna regresja nowych funkcji — 2026-10-16

Po niezależnych przeglądach: końcowy pełny build desktopowy potwierdził 646 testów Java w 75 zestawach, bez błędów. Pięć testów OpenGL presentation3dSmoke, particleSmoke, postprocessSmoke, shapesSmoke i materialsSmoke zakończyło się powodzeniem. Host React 12/12; wszystkie trzy bundle odtworzone bajtowo. Presety Feel3D uzyskały akceptację niezależnego przeglądu FIX1; [demo Feel3D](feel3d.md) i zachowane pomiary opisują ich zakres. Adapter QuickJS przeszedł `assembleDebug`; 7 testów instrumentacyjnych na rzeczywistym ART/JNI emulatora API 35 x86_64 zakończyło się powodzeniem ([wynik](artifacts/android/quickjs-verification.json)). Starszy wynik 335 testów powyżej opisuje poprzednią dystrybucję, a 614 testów — wcześniejszą regresję, nie aktualny wynik desktopowy.

Android: 13 testów CPU aplikacji oraz pięć przypadków aplikacji wykonanych osobno na API35 x86_64 przy density320 przeszły. Ograniczony test release R8 potwierdził start, piksele widoczne na zrzucie, kliknięcie i powrót z tła. Łączny runner TCG miał ANR; fizyczny ARM64 i wydajność nie są potwierdzone. [Dowody i zakres](android-status.md).
