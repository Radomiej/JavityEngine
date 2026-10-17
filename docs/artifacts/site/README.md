# Static showcase verification

Source and command provenance: [verification.json](verification.json). [Browser result](result.json), [desktop](desktop-top.png), [mobile](mobile-top.png), [default reactor inventory](reactor-inventory.json).

The browser proof tests the actual site at the repository URL prefix, loaded images, five example commands, keyboard tabs, mobile navigation and four widths without horizontal overflow or observed JS/console errors. It does not establish engine runtime or broad browser/accessibility coverage. Root image sizing was checked visually and by a cover-aspect assertion.

Android runner defect remains open until a real unfiltered run is diagnosed. The Bash harness was checked with fakes for both success and failure preservation; that does not test an emulator. Pages is considered live only after the publication check recorded in the manifest.

Live publication verified over HTTPS: [site](https://radomiej.github.io/JavityEngine/) and [eight exact source/artifact hash comparisons](live-pages.json). Existing branch-based Pages deployment succeeded for the merge commit; no repository settings were changed after the API403.
