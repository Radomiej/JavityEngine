# Foliage native evidence — 2026-10-20

Isolated JDK25 compilation of current full core + FoliageDemo/FoliagePixelProof/
PbrFoliage succeeded. `PbrFoliageTest`: 1 passed CPU regression.

Real Xvfb :248 + Mesa, GL30 and GL20, both passed:

```
FoliagePixelProof: PASS cutout gaps/opaque depth/front occlusion/front-back/ambient/directional/point/fog/revision/transform/remove/readd/no-camera/GPU buffer deletion/borrowed texture; opaque=1080 gaps=15304
FoliageDemo: PASS beauty day/close/dusk FoliageStats[patches=12, meshes=12, vertices=19296, indices=28944, geometryBytes=675360, submitted=12, visible=12, culled=0, draws=12, builds=12, buildFailures=0] trees=InstanceStats[submitted=6, visible=6, culled=0, instanced=0, fallback=6, groups=0, draws=0, uploadBytes=0]
```

Three deterministic captures per context: day-wide, day-close, dusk-wide. Tree
candidates use the existing ordinary scene-lighting fallback; foliage is merged
ordinary geometry. Source atlas was regenerated and SHA256 compared unchanged.
Actual context loss, Android/Wasm runtime, shadows and hardware performance are
not covered by this evidence.
