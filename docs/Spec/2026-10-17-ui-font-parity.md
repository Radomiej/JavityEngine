# UI font parity: unresolved cross-host raster difference

Date: 2026-10-17. Status: open BUG-UI-001. No tolerance increase or font-renderer change has been made. This spec records the remaining evidence and the next diagnostic slice.

## Observed contract and evidence

The HTML and native UI use the same bundled OpenSans-Regular.ttf, SHA256 `e64e508b2aa2880f907e470c4550980ec4c0694d103a43f36150ac3f93189bee`. Browser references require a loaded Open Sans400 face, exact resolved asset URL and HTTP200. Exact Chrome for Testing154.0.8037.57 is pinned. A forced Arial substitution fails the reference gate.

Native card-title ink measures79px. Both local Chromium and exact CfT154 measure75px, including real Mesa/Xvfb native comparison, which passes the existing4px bound. Hosted Ubuntu24.04 CI measures74px and therefore fails. Matching the browser, TTF, CSS, viewport and face is insufficient to explain that one-column difference. Hinting/LCD/subpixel/GPU flags, isolated Ubuntu FreeType/fontconfig binaries and a locked Tailwind rebuild did not reproduce74px locally. These probes do not prove an OS-library cause.

The current test uses a thresholded glyph bounding box, not complete image or text-shaping equivalence. Keep the existing assertions and record the hosted failure; a successful JVM build must not be reported as full native UI parity.

## Next diagnostic slice

1. Print bounded per-label browser text advance, font size/line-height/letter spacing, DOM text range and ink edge-column coverage at the existing threshold. Export matching native GlyphLayout/glyph advances and edge profiles before the assertion fails. Use job logs as well as artifacts because artifact downloads through this session identity returned403.
2. Distinguish actual glyph advance/layout drift from a soft antialias edge crossing the threshold. Reproduce the hosted case with those observations; keep the pinned browser and bundled-font guard.
3. If advances are wrong, fix font measurement/rendering together in the typed UI font path, preserving Yoga wrap and resource ownership. Do not apply a per-label scale or string-specific offset.
4. If only threshold uncertainty differs, define and test a bounded antialias-aware metric rather than raising a single constant until CI is green. Account for both horizontal edges; the present width-only check does not establish horizontal glyph placement.

## Acceptance for a future fix

- Same text and bundled font identity remain required; capture geometry and actual rendered pixels.
- Native/HTML tests cover short titles, wrapped text, multiple font sizes and Polish glyphs.
- Negative controls reject missing/clipped glyphs, wrong font, horizontal translation and materially changed advances.
- Existing control geometry, input ownership, baseline alignment and screenshot comparisons remain meaningful.
- Run the complete native UI gate on both the local reference environment and hosted runner. Report exact browser/font/platform provenance; no full CSS/Tailwind or arbitrary text-shaping claim follows from these fixtures.

Detailed diagnosis and source-linked gate history: [Bugs](../Bugs/2026-10-17-publication-review.md), [curated probe report](../artifacts/site/ui-font-diagnosis.txt).
