# Weryfikacja batcha runtime — 2026-10-16

Zakres: czytelny voxel FPS, narzędzia sceny/Feel, lokalny streaming i otoczenie. Baza zmian: `0c9a236`. Każdy etap miał niezależny przegląd specyfikacji i jakości; poprawki sprawdzano ponownie. Ta strona odróżnia checkpointy od końcowego wyniku całego batcha.

| Etap | Dowód | Wynik checkpointa |
| --- | --- | --- |
| FPS | [Ręka, kilof, akcja, ruch, FPS/TPP i resize](artifacts/voxel-fps/README.md) | CPU i rzeczywisty desktop GL: PASS |
| Narzędzia | [Tree viewer, gizma, undo i input](runtime-tools.md), [Feel](artifacts/feel-preview/) | 748 testów; rzeczywiste kliknięcia panelu, pauza/wznawianie i oddanie focusu: PASS |
| Streaming | [Model z teksturą, ładowanie, cache i cleanup](artifacts/asset-streaming/README.md) | 782 testy; 183 natywne kontrole, 12 renderowanych klatek podczas ładowania: PASS |
| Światła — fundament | [Kontrakt komponentów i shaderów](scene-lighting.md) | 461 testów core po poprawce; rzeczywisty scene shader GL20/GL30: PASS |
| Niebo | [Kadry i regresja recovery](artifacts/sky/README.md) | 800 testów; gradient/cubemap, głębokość, stan GL, błędy i managed recovery: PASS |
| Otoczenie — integracja | [Kadry i metryki](artifacts/environment/README.md) | 805 testów / 91 suite; 25 kontroli na każdym GL20/GL30; przegląd zadania PASS |

W przeglądzie nieba znaleziono i naprawiono rzeczywisty błąd VAO: po odtworzeniu mesh/shadera tło znikało, jeżeli lokalizacja pozycji się nie zmieniła. Test regresji obejmuje niezmienione i zmienione lokalizacje, pierwszy oraz następne kadry i zachowanie VAO wywołującego. To desktopowe odtworzenie zasobów zarządzanych, **nie test utraty EGL na Androidzie**.

Kod otoczenia jest zintegrowany w `8521f75`; niezależny przegląd zadania PASS. Trwa końcowy przegląd całego zakresu. Ostateczne polecenia, licznik testów oraz commit zostaną zapisane po ich ukończeniu. Dwie drobne uwagi z przeglądów etapów są przekazane do tego przeglądu: retencja starych failed lease w demo streamingu i wykrywanie pierwszego światła dodanego do już zaznaczonego obiektu.

## Granice wyników

- APK debug z nowymi źródłami skompilowano przed końcową wąską poprawką focusu UI; nie jest to dowód runtime. Android nowego streamingu i otoczenia: **UNVERIFIED**. Wcześniejsze testy Androida nie zastępują testów nowych funkcji.
- Światła sceny/mgła wymuszają zwykły rendering modeli zamiast obecnego instancingu. To koszt poprawności obrazu, nie regresja ukryta pod deklaracją wydajności.
- Streaming dotyczy lokalnych modeli/tekstur; nie dostarcza automatycznego HTTP, hot reloadu ani nieskończonego świata.
- [Roślinność](superpowers/specs/2026-10-16-foliage-components-design.md) pozostaje projektem do przeglądu, nie wdrożonym API.
- `python3 tools/animation-validation/benchmark_instancing.py --verify-only`: PASS, 13 wariantów. To weryfikacja integralności istniejących dowodów. Historyczne benchmarki instancingu pozostają pomiarami wcześniejszego commita; nie są nowymi pomiarami zaktualizowanego renderera.

[Pełny zapis decyzji i kosztów](runtime-batch-decisions.md). Aktualny stan: [kolejka prac](work-queue.md).
