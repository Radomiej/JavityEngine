# Rzeczywisty rig: Fox

2026-10-16 — PASS na desktopowych GL20 compatibility i GL30 core, Mesa software. Model zewnętrzny, 24 kości i 1728 wierzchołków; Survey/Walk/Run. Własny `ModelPoseAnimator` i `GdxRenderer` pracują bez zmian kodu produkcyjnego.

![Oryginalne klipy renderowane przez Javity](fox-animations.gif)

[Trzy kadry](fox-clips.png). GIF zawiera 60 rzeczywistych klatek silnika; jest zmniejszony do720×504 z zachowaniem podpisów autorów. Czas klatek odzwierciedla czas źródłowych klipów z zaokrągleniem formatu GIF; animacja preview nie zastępuje asercji zegara.

- [Wynik GL20](gl20/verification.json), [wynik GL30](gl30/verification.json).
- [Crossfade](gl30/crossfade-mid.png), [maska ogona](gl30/tail-mask.png), [mikser50%](gl30/mixer-50.png).
- [Źródło/licencja](../../../tools/animation-validation/fox/NOTICE.md), [instrukcja testu i viewera](../../../tools/animation-validation/README.md).

Sprawdzenia: 12 próbek oryginalnego glTF ×1728 skinned vertices; każdy z trzech klipów zmienia piksele geometrii (HUD wykluczony); ciągłość/start/środek/koniec crossfade; maska ogona z izolacją innych lokalnych kości; phase-synchronous mixer; WORLD/playback pause i resume/speed2x; izolacja instancji i brak dodatkowej aktualizacji podczas renderowania; marker i completion ONCE dokładnie raz; źródłowa tekstura zwolniona przy dispose.

Maksymalny błąd względem matematycznego SLERP źródła: **0.00699997 jednostki assetu**, przy przyjętej tolerancji0.01 i skali sceny0.02. To około0.00014 jednostki świata. libGDX aproksymuje SLERP dla małych kątów, a jego helper nie zawsze normalizuje wynik; referencja blendingu jest normalizowana zgodnie z kontraktem Javity. Czas testu jest dzielony na kroki<=50ms: pojedyncze Game.step ogranicza dt do100ms. Nie zmieniano tych kontraktów, by dopasować wynik.

Ograniczenia: offline konwersja konkretnego glTF do G3DJ, nie runtime importer GLB/PBR. Nie potwierdza root motion, IK, retargetingu, klasyfikacji regionów avataru, wszystkich rigów ani Androida. Weryfikowano animacje, nie słuch, fizyczny sprzęt GPU czy pełne API Unity Animancer.
