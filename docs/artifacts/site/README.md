# Static showcase verification

Source and command provenance: [verification.json](verification.json). [Browser result](result.json), [desktop](desktop-top.png), [mobile](mobile-top.png), [default reactor inventory](reactor-inventory.json).

The browser proof tests the actual site at the repository URL prefix, loaded images, five example commands, keyboard tabs, mobile navigation and four widths without horizontal overflow or observed JS/console errors. It does not establish engine runtime or broad browser/accessibility coverage. Root image sizing was checked visually and by a cover-aspect assertion.

Hosted full Android run39046532187/source5d2a206 passed5/5 with zero failures/skips; BUG-ANDROID-001 is closed for that scoped KVM emulator gate. Historical TCG ANR cause and physical ARM64 remain unverified. The Bash harness was checked with fakes for both success and failure preservation; that does not test an emulator. BUG-UI-001 remains open: native UI parity passes locally under Mesa/Xvfb, but the hosted font-width gate still fails. Pages publication is confirmed independently by the manifest.

Live publication verified over HTTPS: [site](https://radomiej.github.io/JavityEngine/) and [eight exact source/artifact hash comparisons](live-pages.json). Existing branch-based Pages deployment succeeded for the merge commit; no repository settings were changed after the API403.
