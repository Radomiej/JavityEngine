# Maintenance, integration and GitHub Pages

Date: 2026-10-17. User authorized bug diagnosis/fixes, parallel agents, documentation updates, push, merge into the default branch and publication of an engine showcase. Default branch verified through GitHub API: `master`.

## Scope and boundaries

- Diagnose remaining confirmed BUG-ANDROID-001 from its actual evidence. Close only after an unfiltered full-suite pass and an evidence-backed diagnosis; never infer a fix from isolated cases or larger timeouts. Missing SDK/adb is a reproduction blocker, not an engine fix.
- Keep the public GameObject/Component API and optional Artemis unchanged. Preserve existing owners, borrowed resources, licenses and provenance. This batch does not implement the entire feature backlog.
- Update `AGENTS.md` (canonical instructions), `.gitignore`, user-facing README and an Unreleased CHANGELOG. Ignore local configuration/build caches without hiding curated proof or sample configuration.
- Create dependency-free responsive `site/` with actual screenshots, verified Java snippets, launch commands, credits and links. Explain platform limits and partial Tailwind/Unity-inspired coverage. No fabricated visuals or performance claims.
- Deploy only the reviewed static site via GitHub Pages. Use the existing `master:/` Pages source when API access cannot change it; root index redirects to site/ and root .nojekyll disables Jekyll. A validation workflow uploads the standalone site artifact. Record deployment state independently. Never claim a live deployment from a successful push alone.

## Acceptance

- [x] Android diagnosis recorded; a closure claim requires full runner evidence.
- [x] README, CHANGELOG, instructions and ignore rules consistent with current modules/platform boundaries.
- [x] Site works at a repository subpath, desktop/mobile and keyboard; local files/fragments resolve, no browser errors or horizontal overflow.
- [x] Default reactor build and ECS usage pass on the integrated tree; test counts distinguish cached results.
- [ ] Feature branch pushed, current remote master integrated without force push, merged tree verified and pushed.
- [ ] Live Pages URL checked over HTTPS; deployment status and any access failures recorded.

## Publication design

The source is `site/`; assets are local, compressed copies of curated project screenshots. Build/publication do not require the engine or Node dependencies. GitHub links target `master`, the verified default branch. Site verification checks the exact artifact intended for publication. Historical root Pages content must not continue advertising obsolete setup downloads.

An Android runtime workflow may supply the missing unfiltered emulator evidence on a hardware-accelerated hosted runner. A committed workflow alone is not proof that it ran. Full logs and artifacts must be retained on failure.

## Publication configuration decision

Existing Pages source is `master:/` with legacy build_type. API PUT to switch to workflow and enforce HTTPS returned403 (`Resource not accessible by integration`), despite repository-level admin permission being reported. Preserve that source and use its existing built-in Pages deployment. Root redirect and .nojekyll replace the obsolete page without needing a new setting. The site check workflow validates/uploads, it does not claim to deploy. HTTPS reachability is tested separately; no claim that the enforce-HTTPS setting was changed.
