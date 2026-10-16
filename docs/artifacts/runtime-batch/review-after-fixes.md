# Scoped final runtime fix re-review

Reviewed immutable range `41518e9a3210e5f7fb05069994ab4619f58d7b8d..d1070a293e57531bd2c5a78ac8bd4dff7bbd50f0`.

**Spec: PASS. Quality: PASS. All three final findings are addressed.** No introduced Important defect, residual actionable finding, or explicit adjudication need was identified in this scoped fix. The original whole-goal review remains applicable; its three P3 findings are now closed.

## Finding dispositions

1. **P3 failed retry history — ADDRESSED.** `AssetStreamingDemo.requestMissing` obtains the new request before changing retained history, closes the older previous failure, then retains only current plus immediately previous FAILED. A synchronous request rejection leaves the existing state intact. No AssetStreams service, generation, cache, physical-release or consumer ownership contract changed. The actual-loader probe now executes four retries/five failures and checks distinct new lease identity, initially absent failure, previous FAILED state both at request issuance and after retry completion, older CLOSED states, exact two-element service ownership and empty ownership after scene close. The probe's own bounded list retains old handles only to verify their terminal states; production history is bounded.

2. **P3 first dynamic light discovery — ADDRESSED.** `LightAuthoringPanel.update` reloads when cached light is null and the current selected object now has a Light3D. Existing non-null drafts do not enter that branch, so unrelated updates/component insertion do not replace dirty state. Existing selection/component-removal logic and targeted focus cleanup remain intact. The new smoke sequence exercises the real overlay/Stage panel: initially lightless selection, first add/init/update without reselection, visible panel, dirty draft preservation across unrelated and second-light insertion, Apply to the exact original component, removal/fallback to remaining light, final removal/hide/focus release, readdition/Apply and retained panel-root identity. The sequence runs after the historical screenshots and is smoke-only.

3. **P3 FPS capture provenance — ADDRESSED.** The README names historical Task 2 checkpoint `7a1d475` and links the later scene-environment FPS capture and evidence README. Both relative links independently resolve. The committed five-file fix contains no PNG or JSON changes, so historical metrics and captures retain their original provenance. Existing environment verification hashes remain Task 3 checkpoint evidence, not relabeled final-code evidence.

## Evidence checked

Read the complete supplied fix diff/package and author report; cross-checked current source and actual immutable git diff. `git diff --check 41518e9..d1070a2` passes. HEAD matches the reviewed commit.

Inspected the exact retained execution logs:

- `/tmp/javity-runtime-final-fix-red.log`: both actual native regressions fail before the fixes, specifically `Retry retained obsolete failed lease ownership` and `First added light was not discovered on the same selection`.
- `/tmp/javity-runtime-final-fix-green.log`: BUILD SUCCESSFUL in 10s; asset streaming, environment demo with explicit dynamic-authoring PASS, and existing inspector interaction/lifecycle smoke pass.
- `/tmp/javity-runtime-final-fix-retry-green.log`: final strengthened retry-completion assertion passes; BUILD SUCCESSFUL in 2s.
- `/tmp/javity-runtime-final-fix-streaming-proof.json`: 211 checks, four retries, bounded failure history, one default model/dependency load, zero detached model pixels and balanced 12 buffer/8 texture creation-deletion totals. Existing safe-boundary cache/pressure, owner-thread, paused-loading and close-during-load controls remain active.

Independently recounted affected retained XML: 121 tests / 17 suites / zero failures, errors or skips. The author's report correctly distinguishes newly executed UI tests from up-to-date asset tests. These are accepted recorded executions; this reviewer did not duplicate builds/native runs.

Root's fresh full build/affected-native verification is running separately. Working-tree generated streaming artifacts and root-owned `docs/artifacts/runtime-batch/` observed during that run were left untouched; they do not alter the reviewed committed diff. No product edits, commits or subagents were used; only this report was written.

The prior scope limits remain: new Android streaming/environment runtime and actual per-feature EGL recovery are UNVERIFIED. Neither this desktop fix nor prior APK compilation adds a device/runtime/performance claim. No additional fix wave is requested.
