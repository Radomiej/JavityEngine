# Native engine gallery / demo-page refresh — 2026-10-20

Engine capture source: `f8f5767`, based on `bbaf31e`. Existing dark ink/teal
presentation is preserved. The hero, gallery and examples show current Fox Garden,
foliage and imported-avatar features, with explicit native commands and credits.

## Summary and environment

Six HUD-free Full-HD frames come directly from native desktop GL; two640×480
avatar frames come from the real imported-GLB smoke. The engine renderer supplies
all lighting, shadows and tone mapping. WebP conversion adds no effects, crops,
retouching or composite layers. [Media provenance](../../../site/assets/gallery-manifest.json)
records source/encoded hashes, dimensions and launcher hashes. Sources are
reproducible with `qualityGallery`, `foliageGallery` and `importedAvatarSmoke`.

Native: Linux x64, JDK25.0.4.1, Xvfb1920×1200, Mesa llvmpipe.
Browser: Chromium154.0.8037.57, project Playwright harness at `/JavityEngine/`.
No connected-browser control tool was exposed after discovery; the established
AGENTS-approved Chromium proof was used without accessing a user's browser
session. Desktop1440×1000 and mobile390×844; additional overflow checks360/768.

## Changes verified

- Hero and eight-photo responsive gallery use local images and preserve Fox
  CC0/CC BY4.0, original foliage CC0 and humanoid CC0 notices.
- Native modal enlarges the actual selected image; Close, Escape and backdrop
  restore focus and page scrolling. Native modal makes background controls inert.
- Eight example tabs retain old demos and add quality, foliage and avatar commands.
  Arrow/Home/End navigation preserves single selection and roving tabindex.
- Gallery capture is separate from existing smoke assertions and normal HUD/input;
  mutually exclusive flags fail explicitly. CI retains full-HD capture artifacts
  and the site workflow runs pinned-Chrome responsive interaction checks.

## Checks

| Gate | Result |
| --- | --- |
| Full Gradle reactor and gallery/quality/foliage smoke | PASS26s,123tasks; unchanged results reused where UP-TO-DATE |
| Actual native gallery dimensions and images | PASS, eight new native Full-HD PNGs generated; six selected for site |
| Fresh imported-avatar native proof | PASS, canonical bones/socket/independent views/lifetimes |
| Static local files/fragments, JavaScript syntax | PASS |
| Page identity/nonblank/no framework overlay | PASS |
| Console errors | None |
| All gallery/tab images decode | PASS |
| Eight commands/guides and keyboard selection | PASS |
| Eight zooms on desktop and mobile | PASS, Escape/Close/focus return |
| Responsive overflow360/390/768/1440 | None |
| Mobile navigation | PASS |
| JavaScript-disabled negative control | Expected FAIL at tab-foliage selection |
| Independent Sol code/media/modal review | APPROVE, no findings |

Interaction: load page → decode actual images → switch all examples → exercise
roving keys → enlarge each capture → Escape/Close → return focus → repeat mobile
and menu navigation. Reviewer independently tested mobile Tab/Shift+Tab containment
and backdrop closure. Existing launchers, shape/map/physics/voxel examples remain.

## Evidence and limits

[Desktop](gallery-desktop.png), [mobile](gallery-mobile.png),
[zoom](gallery-zoom.png), [browser result](gallery-browser-result.json),
[negative control](gallery-negative-result.json),
[engine log](gallery-engine-gates.txt), [review](gallery-code-review.md).

These are native desktop screenshots and static-site tests. They do not establish
new features running in the browser, physical GPU performance, or additional
Android runtime coverage. Publication is verified separately after push; an
uploaded static artifact alone does not prove GitHub Pages deployment.
