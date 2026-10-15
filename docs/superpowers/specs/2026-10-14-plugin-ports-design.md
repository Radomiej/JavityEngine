# Porty API OneJS, Animancer i Feel do Javity

Użytkownik zatwierdził realizację i wybrał agentów (/goal 2026-10-14), następnie potwierdził OneJS i polecił czytać publiczny kod. Zachować wcześniej zmodernizowany silnik i branch, bez commit/push. Własny native host na Java25/libGDX/Yoga; opcjonalny moduł JS. Nie zastępować silnika Unity lub browserem.

## Zakres dostarczany

1. Feel-style: wspólne ease/tween/feedback, WORLD/UI, sequence/parallel/wait/callback, cancel/reset, scene-local własność właściwości. Szczegółowe zasady w feedback-design.md; audio/punch/shake zostają oddzielone, jeśli wymagają nowego ownership. Typowane cele transform i koloru istniejących rendererów.
2. Animancer-style: SpriteClip/named sprite playback oraz ModelAnimator na istniejącym ModelInstance. Play ONCE/LOOP, speed, local pause, state time, events start/loop/complete, crossFade dla modelu. Jedna ścieżka animacji, nie maski/layers/root-motion/IK. Stare API zachowane; model nigdy nie ma dwóch updaterów. Testy generowanej pozy node, oddzielne instancje i native demo deformacji jeśli testowy skinned mesh.
3. OneJS native host: deklaratywne UiNode/UiDocument z View/Text/Button/Progress, state binding/callbacks, className, XML-compatible subset HTML (div/span/button/progress), strict validation. Scene2D/Yoga własność, cleanup/reload bez wycieków, brak arbitralnego HTML/CSS. Ograniczone utility classes jawne.
4. Opcjonalne JS/TSX: javity-script z GraalJS 25.x (osobno od core/UI). Natywny host buduje UiNode; JavaScript funkcje i zdarzenia, tick scheduling, React reconciler/JSX bundle jako sprawdzony przykład. Używać publicznej architektury OneJS z MIT notice przy adaptowanym kodzie. Nie deklarować pełnej zgodności z jego Unity API lub wszystkimi npm/Web APIs.
5. UiTargets alpha łączy TweenPlayer z native UI bez zapisywania wymiarów Yoga; owner GameObject i dokument określają lifetime celu. Demo --plugins łączy wszystko: UI przycisk, binding, feedback, sprite namedclip i animowany model. GPU smoke potwierdza zachowanie i regresje.

## Granice

Runtime i stany playback poza draw. GPU zasoby caller-owned lub scene-owned jak dotąd. JS context jawnie zamykany, ograniczony host dostęp bez arbitrary Java reflection, callback na wątku render/UI. Runtime JS opcjonalny; podstawowe UI/tweeny/animacje nie wymagają npm ani JS. DOM refs/tracked listener cleanup deterministyczny. JSX kompilowany narzędziem buildowym do JS; natywny engine nie udaje parsera browserowego HTML.

## Kryteria końca

Testy semantyki tween/callback/cancel, model actual node poses i crossfade, named sprite clip, UI parse/update/click/reload/dispose/Yoga, JS handler i scheduler. Pełny clean reactor; native plugins smoke120frames i stare5smokes. Dokładna tabela przeniesiono/nieprzeniesiono oraz licencje i instructions build/JS. Nie oznaczać pełnych pluginów Unity jako zgodnych z Javity.
