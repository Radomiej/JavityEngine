> Scope superseded: the user subsequently requested keeping Artemis as a NEW optional ECS subsystem. This inventory describes possible retirement of the historical Galaxy runtime, not authorization to ban Artemis or execute the withdrawn full-retirement plan. No product files were removed.

# JavityEngine legacy removal inventory (variant B)

Read-only audit at `95b951c` on `modernization/java25-gradle-yoga`; user selected complete deletion. Counts are tracked file count and current working-tree byte sizes (`git ls-files`, `stat`); no product files were changed and no builds were run.

## Deletion roots and size

| Root | Tracked files | Bytes |
| --- | ---: | ---: |
| `javity-backend/` | 101 | 145,980 |
| `javity-engine/` | 89 | 172,732 |
| `javity-map/` | 16 | 36,982 |
| `javity-net/` | 19 | 25,647 |
| `javity-android/` | 11 | 27,896 |
| `javity-xml-ui/` | 17 | 532,742 |
| `javity-project-generator/` | 169 | 2,703,121 |
| **Total** | **422** | **3,645,100** |

The two old launcher files requested for removal are `javity-desktop/src/main/java/org/javity/demo/DemoApplication.java` (1,533 B) and `DemoScene.java` (3,178 B). Do not remove `javity-android-app/`: it is the actively used modern opt-in Android app, separately included by `-PjavityAndroid=true`, and is used by CI. Do not remove `javity-map-core/` or `javity-maplibre/`; the first backs desktop `MapDemo`/`RuntimeDemo`, and the second backs MapLibre/Gorzow demos and tools.

## Additional required edits/removals

- `settings.gradle`: remove the `include` entries for `javity-backend`, `javity-engine`, `javity-net`, and `javity-map`; keep `javity-desktop` and modern modules. The three out-of-reactor roots are already not included.
- `javity-desktop/build.gradle`: remove `implementation project(':javity-engine')`.
- `javity-desktop/src/main/java/org/javity/demo/DesktopLauncher.java`: remove the `--legacy` selector, `DemoApplication` construction, and any obsolete `otherDemo` handling tied only to `--legacy`. Other selector branches are modern and remain.
- Delete `DemoApplication.java` and `DemoScene.java` above. `ModernDemo` does not depend on legacy; `MapDemo` and `RuntimeDemo` import `org.javity.map.*`, which resolves to the modern `javity-map-core`, not the old `pl.radomiej.map` plugin.
- `build.gradle`: remove root `artemisVersion` once all module roots are removed. Keep `gdxVersion`/`lwjglVersion`; modern modules use these.
- There are no other active Gradle dependency edges or retained product-source imports into the seven roots found in non-deleted sources. The only desktop source consumer of `org.javity.engine` is the two old demo classes; the old `javity-map` edge is internal to that root.

## Live docs that become inaccurate

Update these as part of the deletion so users do not get obsolete build/API guidance:

- `README.md`: module table entries for backend/engine/net/map; claim historical Android/XML/generator remain; legacy map/Spine paragraphs; command `--legacy --smoke` (currently in build/run instructions).
- `AGENTS.md`: module inventory says backend/engine/net and old Android/XML/generator remain; build/run instructions include `--legacy --smoke`; update removal decision and migration guidance.
- `docs/TODO.md`: item 189 asks whether to retire legacy; mark the chosen retirement complete and preserve explicit non-conversion/loss notes. Items on Spine runtime/re-export and “legacy retained” status need relabeling as historical/retired or closed with reason.
- `docs/migration-api.md`: currently describes old APIs, serializers, downloader, plugin, and Spine as preserved/available. Recast as a retirement/migration-loss record: there is no built-in reader/converter for old JSON/Kryo scenes, XML projects, old plugin maps, or old mobile helpers; new SceneJson v1 is not a conversion path.
- `docs/superpowers/specs/2026-10-16-core-consolidation-design.md`: it currently calls A the recommendation and explicitly says not to remove XML/generator/Android, preserves the legacy launcher, and anticipates an opt-in compatibility graph. Update decision/status to B and remove proposed A1 execution steps as current instructions; retain the dated audit as provenance if clearly marked superseded.
- `THIRD_PARTY.md`: remove active attribution/source entries for Galaxy Rapid (`javity-backend/src`), old map plugin (`javity-map/src`), Artemis, and Spine runtime if no remaining active product use exists. Preserve unrelated Open Sans, MapLibre, Android-modern, project asset, and other modern dependency notices. If retaining a note of the old licenses for historical provenance, label them historical and make clear the binaries/source are no longer shipped.
- `docs/android-status.md`: its statement that historical `javity-android` remains outside the reactor becomes false; keep the historical test evidence but state the helper module was retired. Keep the separate modern Android app status intact.

Historical audit/spec/plan files under `docs/audits/`, `docs/superpowers/plans/`, and dated specs can remain as records of what was true then. They should not be rewritten to erase provenance; only the current design/status docs and directly referenced guidance should be corrected. Re-scan hyperlinks and command examples after deletion for broken references.

## Assets, licenses, and modern consumers to keep

- Keep all modern demo data under `javity-desktop/src/main/resources`, especially `org/javity/demo/asset-streaming/**`, `org/javity/demo/maplibre-town/**`, `org/javity/demo/maplibre-gorzow/**`, `org/javity/demo/voxels/**`, and `runtime/**`. These feed live modern demos. In particular retain Gorzow's ODbL data/license/attribution, Open Sans glyph licenses, synthetic town sources/licenses, voxel atlas notice, asset-streaming notice, and runtime WAV/avatar/tile assets.
- Keep `javity-ui`'s bundled Open Sans font and its license/notice; it is the modern UI default. Keep MapLibre module notices/resources and map generation/import tools.
- No Spine skeleton/atlas export or runtime asset is tracked outside `javity-engine`/`javity-backend`; modern animation/avatar fixtures are synthetic and independent (confirmed by `THIRD_PARTY.md`). Remove the legacy Spine code/runtime dependency with the legacy roots, while retaining historical docs/provenance that record Spine 2.1.27 source exports and the retired 3.5.51.1 runtime. Do not describe modern `ModelPoseAnimator` or demo avatar material as a migrated/compatible Spine implementation.
- Do not delete `docs/artifacts/**` as part of source-root removal: these are historical verification evidence. Their old logs may mention legacy build tasks; preserve those as dated evidence unless a separate evidence-retention decision is made.

## Capability losses to state plainly

Deleting these roots withdraws `JGameObject`/`JComponent`/Galaxy Rapid/Artemis, old JSON/Kryo serializers and reflective proxies, `javity-map` OSM plugin, `javity-net` downloader, old Android location/intent helpers, XML UI, and the libGDX project generator. No automatic conversion of projects or saved scenes is present. The new component model, `javity-map-core`, modern Android app, and new UI are separate implementations and do not guarantee source/data compatibility.

## Controller addendum

The desktop CI workflow `.github/workflows/desktop.yml` still explicitly launches `--legacy --smoke`; remove that invocation with the runtime deletion and retain modern smoke gates. The ordinary source comment in `ModernDemo.java` also mentions the removed parallel backend. Root `.project` is generic Eclipse metadata, not a legacy dependency; no need to delete it as part of runtime retirement.

Runtime preflight in the current executor found only system JDK21; the previous ephemeral JDK25, X server and Android SDK are not present at their old /tmp paths. Gradle cache remains available. Execution must provision/check JDK25 and real GL/Android tooling again before claiming new build/native/APK results. No tests were run for this inventory.
