# Decyzje wykonawcze instancingu A/B/C

Zapis decyzji z nadzorowanej implementacji i przeglądów. Dowody pomiarowe są w [projekcie](gpu-instancing-design.md) i powiązanych artefaktach. Status nie oznacza pełnej zgodności z komercyjnymi bibliotekami ani gotowości każdej platformy.

## A — statyczne modele

- Continue in the existing modernization feature checkout — user authorized modifying this engine branch and accumulated work is here — costs isolation from concurrent edits; no concurrent implementers.

- Skill helper scripts are unavailable via cloud resource reader; create equivalent plan-specific briefs/review artifacts locally — preserves requirements, costs manual packaging.

- Per-view packed GPU buffers compare packed matrices/order rather than treating stable CPU dirty indices as GPU offsets — culling changes offsets — costs small CPU packing comparison.

- Opt-in prepared geometry is immutable until explicit invalidation; provide invalidateGeometry(Mesh) on the owned batch and renderer forwarding — libGDX Mesh has no content revision and per-frame full vertex copies violate spec — forgotten invalidation would render stale geometry, documented at opt-in.

- Actual new-context recovery is consolidated into stage C Android gate, exercising static and skinned paths together; stage A only supplies recovery hook and keeps claim unverified — avoids calling simulated same-context reset recovery proof — platform readiness remains open until that gate passes.

- If shader arithmetic cannot remove moved-integration crease variance, allow at most one >2 RGB pixel only within one pixel of a baseline >16/255 color discontinuity, with identical coverage; main256 fixture keeps original strict tolerance. Evidence: exactly one pixel (105,91), ordinary[57,200,111], instance[50,176,98], all other pixels≤2. Require negative comparison controls. Reason: shared-edge raster winner can vary with uniform versus instanced transform optimization. Cost if wrong: could miss one localized edge shading defect; exception must be reported quantitatively.

## B — dokładne pozy CPU

- Validate immutable clip/rig candidate against live source data at each composition before sharing — existing mutable libGDX channels must remain observable — costs source comparison CPU, benchmark must expose net benefit.

- Use the established modernization checkout and manually packaged skill artifacts as in stage A — costs isolation/manual packaging.

- Cache resolve checks current Game.getFrameIndex on every call, not only onBeforeUpdate — pause/UI/init can bypass WORLD phases — costs a constant-time comparison per composition.

- Add no-copy live-content snapshot matching and bounded shared snapshot lookup during integration — recopying all keyframes per composition would add excessive allocation — costs O(source-content) validation until an explicit future immutable-source API is introduced.

- Unrepresentable mutated source data bypasses cache and uses original composition; only snapshot/key construction incompatibility is caught, never playback callbacks or actual sampling failures — preserves original lazy behavior — costs uncached work for such exceptional inputs.

## C — palety GPU

- Use focused managed RGBA32F texture data or explicit raw rebuild; do not assume pinned FloatTextureData works on GLES3 — its Android branch uses legacy OES path — costs a small texture data adapter.

- Bone palette identity includes actual matrix bits and bone layout in addition to local pose identity — mesh parts may have different inverse binds/orders — costs matrix equality checks but prevents pose contamination.

- maxRows is a total BonePalette row budget, GPU allocation lazy; overflow explicitly falls back and texture pages cannot grow unbounded — plan wording could be read per-page without total limit — costs reduced instancing beyond configured budget, never missing objects. Initial renderer budget1024 rows and draw capacity256.

- Shared instance transport/shader/state helpers may be extracted and animated native probe kept separate from static demo — avoids duplicated400-line pipeline — costs broader reviewed renderer refactor.

- Compatible palette layout is determined by exact MeshPart/vertex weights plus ordered bone-matrix count/page, not source Model/hierarchy identity; each renderable uploads its authoritative ordered matrices and only identical matrix content dedups — permits distinct semantic rigs with the same actual GPU slot layout safely — costs a stronger validation requirement; if ordering were ignored, poses would contaminate, so independent-pose TF and inverse-bind tests are required.

- Android actual-context proof accepts native EGL handle ABA, as existing test explicitly documents; require forced non-preserved GLSurfaceView destruction/recreation, retained owners, managed reconsume and raw reupload/pixel evidence, record handle values but do not assert unequal numbers — avoids false failures from native handle reuse.

## Zakres pozostałych prac

Optymalizacja kosztu walidacji mutable źródeł pozy wymaga osobnego jawnego kontraktu assetów. Cache CPU pozostaje opcjonalny; ograniczenie liczby pełnych kompozycji nie oznacza poprawy całkowitego czasu CPU. Fizyczny Android ARM i pomiary na urządzeniu pozostają oddzielnymi bramkami. Bieżące dowody GLES/EGL i benchmarków należy sprawdzać w artefaktach, nie wnioskować z kompilacji.
