# Maintenance and Pages implementation plan

Spec: [maintenance and Pages](2026-10-17-maintenance-and-pages.md). Execution: user-authorized agents for independent Android diagnosis, site and README/CHANGELOG; coordinator owns shared instructions, ignore rules, publication and merge.

1. Inspect BUG-ANDROID-001 logs/test ownership and reproduce if tools exist; record blockers and supply a full-suite diagnostic workflow without speculative app edits.
2. Update AGENTS and ignore local SDK/config/cache files; verify curated docs remain tracked and wrapper JAR is not hidden.
3. Implement static showcase in site/, review current APIs/commands and source screenshot credits. Verify desktop/mobile screenshots, keyboard and links.
4. Update README/CHANGELOG and spec/TODO statuses; compare claims with evidence.
5. Add static publication validation and static validation workflow and existing branch-based Pages publishing; validate the actual publication artifact before changing Pages configuration.
6. Run default build plus ECS usage, review agents’ diffs, commit and push feature branch. Fetch master, merge without force and verify merged tree before push.
7. Publish Pages and inspect deployment/live content. Record precise unresolved SDK/token/runtime limitations instead of marking them fixed.

Review focus: repository subpath links, missing screenshots, unsupported-platform claims, stale root Pages content, accidental exclusion of curated evidence. Each is checked at integration with local artifact validation, browser checks, documentation review and git check-ignore.
