# Sky pass — desktop proof

Source: `889ef3b`. Actual `:javity-desktop:skySmoke` GL30/GL20, software Mesa, not an Android/EGL device test. [Screens GL20](../../sky-pass-task2-gl20.png) / [GL30](../../sky-pass-task2-gl30.png) are diagnostic sentinel frames, not environment beauty shots.

[RED excerpt](red.txt) records the unchanged-location managed mesh+shader recovery failure before the fix. [GREEN excerpt](green-sentinels.txt) records the first/repeated matching frames with unchanged locations and moved raw attributes/uniforms after the owner hook. These files are selected exact lines from actual logs, not independent reruns. The full reactor at this checkpoint passed 800 tests / 89 suites.

Independent review found the VAO cached-location defect; scoped re-review approved the owner-only mesh replacement after native proof. Other controls include gradient/cubemap translation and rotation, orthographic rays, depth, actual caller state, raw compile/link failure cleanup, borrowed cubemap lifetime, world/viewmodel/SCREEN isolation and repeated disposal. Exact contracts and bounded GL20 input support: [scene lighting](../../scene-lighting.md).

**Android sky rendering and actual EGL loss/recovery: UNVERIFIED.** Desktop compiler acceptance of local GLES300 source is only a dialect check.
