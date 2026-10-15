# UI, animacje i efekty — kierunek rozwoju

Badanie: 2026-10-14. Użytkownik wskazał OneUI/HTML/Tailwind, Animancer i Feel. Użytkownik potwierdził OneJS i zlecił przeniesienie rozwiązań wszystkich trzech bibliotek. To projekt własnych odpowiedników na Java/libGDX. Porty API są wdrożone; plan plugin-ports śledzi integrację i weryfikację natywnego demo. Szczegóły: [feedback](feedback-api.md), [animacje](animation-api.md), [UI](declarative-ui.md), [OneJS](onejs-port.md).

## Źródła i wnioski

- [OneJS](https://onejs.com/docs) łączy React/TypeScript z natywnymi elementami Unity UI Toolkit; nie jest rendererem dowolnego HTML. [Styling](https://onejs.com/docs/core-concepts/styling) obejmuje style i utility classes. Dla Javity odpowiednikiem hosta jest Scene2D + Yoga; możliwość pisania TSX wymaga osobnej warstwy JavaScript i reconciler.
- [Animancer: odtwarzanie](https://kybernetik.com.au/animancer/docs/manual/playing/), [przejścia](https://kybernetik.com.au/animancer/docs/manual/transitions/) i [zdarzenia](https://kybernetik.com.au/animancer/docs/manual/events/) pokazują bezpośrednie sterowanie klipem przez obiekt stanu. W Javity warto oddzielić stan od renderera, udostępnić czas/prędkość i jawne przejście. Pełne warstwy z maskami kości i mixery wymagają backendu, nie tylko nowej nazwy API.
- [Feel: podstawy](https://feel-docs.moremountains.com/core-concepts.html) i [MMF_Player](https://feel-docs.moremountains.com/mmf-player.html) pokazują kontener efektów z odtwarzaniem, timingiem, resetem i kanałami. Odpowiednik Javity powinien mieć jawne cele sceny i wspólną oś czasu, aby anulowanie usuwało także opóźnione efekty.

## Stan Javity

| Obszar | Działa dziś | Brakująca warstwa |
| --- | --- | --- |
| UI | UiDocument/markup, bindingi, Button/Progress/TextField/ScrollView, React/TSX + GraalJS, reload | Pełne CSS, breakpointy, zawijany tekst, Unity UI Toolkit API |
| Sprite | SpriteAnimationPlayer: named clips, stan czasu/speed/pause, loop/complete | Crossfade sprite, blend trees |
| Model | ModelAnimator: play/crossFade, stany czasu/speed/pause, loop/complete | Warstwy/maski/mixery, root motion, IK |
| Efekty | TweenPlayer/FeedbackPlayer, WORLD/UI, sequence/parallel/wait/callback, lease/cancel | Additive punch/shake, per-voice audio, particles |

## Podejścia

1. **Natywne API Java na istniejącym rdzeniu — rekomendowane.** Zachowuje małą dystrybucję, model komponentów i bezpośredni debugging. Deklaratywne UI buduje natywne elementy. Nie daje od razu React/npm.
2. **Host JS + TSX reconciler do Scene2D/Yoga.** Najbliższy workflow OneJS; potrzebuje wyboru i testów runtime, bridge Java/JS, bundlera, zarządzania listenerami i reload. Osobny opcjonalny moduł po ustabilizowaniu natywnych kontrolek.
3. **WebView z prawdziwym HTML/CSS.** Przydatny dla złożonych dokumentów, ale zmienia koszty dystrybucji, focus, input i kompozycję GPU. Nie wybieramy go jako podstawy game UI.

## Kolejność batchy

1. Tweeny + FeedbackPlayer: wspólny zegar WORLD/UI, sequence/parallel, scale/color tween i callback; audio oraz additive punch osobno. Osobny projekt: [spec efektów](superpowers/specs/2026-10-14-feedback-design.md).
2. AnimationPlayer2D/3D: named clips, play/crossFade, stan time/speed/pause, events i onComplete. Utrzymać stare play(name,loop) jako adapter. Dodać własny niewielki model ze szkieletem i test poz; nie deklarować skinningu na podstawie obrotu sztywnego node.
3. Deklaratywne UI: View/Text/Button/ProgressBar/ScrollView, drzewo z kluczami, bindingi aktualizowane na wątku UI, ograniczone stany hover/focus/disabled. Błąd nieznanej klasy ma pozostać jawny. Restart UI zwalnia Yoga/listenery, pożyczony font/theme pozostają własnością wywołującego.
4. Opcjonalny spike JS/TSX: porównać runtime i bridge na jednej działającej kontrolce. Nie obiecywać pełnego HTML/CSS, Tailwind lub wszystkich pakietów npm.

## Wspólne reguły

Czas oparty o Game.step, bez Thread.sleep i Timer background. Obiekty i cele należą do jednej sceny; po destroy nie wykonują się callbacki. Efekty wizualne nie zmieniają Body/Fixture. Shake kamery musi mieć oddzielny presentation offset uwzględniony konsekwentnie w projekcji/pickingu i nie walczyć z FollowTarget2D; odłożony poza pierwszy batch. Audio feedback nie przejmuje własności Sound/Music i nie zatrzymuje cudzych głosów przy cancel.

Materiały, IK, animacje warstwowe/maski, root motion, particles i multiplayer pozostają dalszymi rozszerzeniami. Nowe API ma korzystać z obecnych komponentów zamiast tworzyć drugi system obiektów.
