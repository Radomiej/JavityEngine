# JavityEngine project site

This is a dependency-free static site for GitHub Pages. Open `index.html` directly, or serve it from the repository root with:

```sh
python3 -m http.server 8000 --directory site
```

Then visit `http://127.0.0.1:8000`. All page styles and behavior are local; typography falls back to system fonts.

## Screenshot sources and credits

The site uses compressed WebP copies of repository demo captures:

- `assets/voxel-fps.webp` — `docs/artifacts/environment/voxel-scene-fps.png`, an upright desktop OpenGL/Mesa voxel FPS capture. Original voxel atlas and avatar assets are authored for JavityEngine; see `THIRD_PARTY.md` and the asset notice at `javity-desktop/src/main/resources/org/javity/demo/voxels/LICENSE.txt`.
- `assets/last-light.webp` — `docs/artifacts/environment/last-light.png`, an original JavityEngine courtyard and lighting fixture capture. Its verification and scope are recorded in `docs/artifacts/environment/README.md`.
- `assets/shapes-demo.webp` — `docs/demo-shape-materials.png`, an actual desktop Shapes Lab capture. The implementation and demo limits are in `docs/shapes-and-shaders.md`.
- `assets/physics3d-demo.webp` — `docs/artifacts/physics3d/physics3d-third-person.png`, an actual desktop Bullet 3D demo capture. Scope and verification are in `docs/physics3d.md` and the artifact directory.
- `assets/relay-ward-demo.webp` — `docs/demo-mm.png`, the Relay Ward gameplay and HUD capture described in `docs/game-authoring.md`.
- `assets/crowd-instancing.webp` — `docs/artifacts/instancing/showcase-instanced.png`, a real instancing showcase capture. The Fox model is by PixelMannen (CC0 1.0); rig and animations by tomkranis (CC BY 4.0); conversion by AsoboStudio/scurest (CC BY 4.0). Attribution and modification details: `tools/animation-validation/fox/NOTICE.md` and `SOURCE.md`.
- `assets/gorzow-map.webp` — `docs/demo-gorzow-night-map.png`, using OpenStreetMap data © OpenStreetMap contributors, OpenMapTiles and OpenFreeMap. Data is under ODbL 1.0; the included attribution and notices are documented in `THIRD_PARTY.md` and `javity-desktop/src/main/resources/org/javity/demo/maplibre-gorzow/licenses/`.

The screenshots are resized only as needed and converted to WebP for download size. The map source's attribution remains visible on the page. Project code is licensed under Apache License 2.0; third-party asset terms remain separate.

## Publication

The repository already publishes `master:/` through GitHub Pages. Root `.nojekyll` keeps it static; root `index.html` redirects to `site/` and no longer advertises the obsolete setup JAR. The `Engine showcase checks` workflow validates and uploads the standalone `site/` artifact, while GitHub’s existing Pages deployment publishes the branch. Switching the Pages source to Actions through the current API identity returned403, so this batch preserves the working branch-based configuration. `python3 tools/verify-site.py site` checks local references and fragments before publication.

Optional browser QA: install Python Playwright and `/usr/bin/chromium`, then run `python3 tools/site-browser-proof.py` from the repository root. It serves only the static site at `/JavityEngine/`, checks four viewport widths, actual image loading, example commands, keyboard tabs and mobile navigation, and writes captures to ignored `artifacts/site/`. This verifies the static presentation, not engine runtime.
