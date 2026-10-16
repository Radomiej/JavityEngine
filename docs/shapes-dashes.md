# Dashed Shapes

`ShapeRenderer2D` supports immutable alternating dash patterns on open polylines. `DashPattern2D` accepts 2, 4, 6, or 8 positive finite lengths, starting with ink and ending with a gap. The period is summed in double precision. `setDashes(pattern, phase)` accepts finite signed phase in local path units and normalizes it by floor modulo; positive phase follows `mod(pathDistance + phase, period)`, with half-open ON intervals. `clearDashes()` restores a solid line. Pattern arrays are copied on input and output.

Lengths and phase use the same local units as stroke width. WORLD geometry follows object scale and Camera2D zoom. SCREEN geometry uses logical screen coordinates and ignores camera movement. HiDPI changes sampling and antialiasing, not the logical dash lengths. Nonuniform transforms stretch each segment according to its direction. Consecutive duplicate points consume no path distance. A repeated first point is an ordinary last segment; closed-path seam fitting is not provided.

The renderer evaluates clipped pattern intervals inside each polyline segment and takes one signed-distance union before applying antialiasing and material color. This preserves the selected cap at internal dash ends and keeps translucent overlaps to one alpha application. ROUND joins are added only when one ink interval continues strictly across the vertex. The existing 64 effective point limit remains in force. CPU preparation rejects non-finite/unsafe segment geometry, more than one million pattern cycles per segment, and smallest dash lengths below a conservative shader-float precision threshold. Phase packing preserves ON/OFF classification at interval boundaries, including ROUND dots formed from a single effective point.

| Supported | Not provided |
| --- | --- |
| Open POLYLINE dashes, 2–8 alternating entries, signed finite phase | Dashes on rectangles, circles, or polygons |
| BUTT, ROUND, and SQUARE at internal ink endpoints | Bevel/miter joins, arbitrary clipping, closed-seam fitting |
| WORLD and SCREEN local-unit behavior | Constant physical-pixel width or spacing |
| Existing color materials over a dash mask | GLES/Android shader adaptation |
| SceneJson v1 round-trip with explicit `dashes` and `dashPhase` fields | Serialization of custom material shader source |

Solid v1 documents omit both dash fields and retain the solid default. A document must provide both fields for a dashed shape, and malformed arrays, phases, or unsupported shape kinds fail the explicit shape codec.

The Mesa software-rendered desktop probe passed at a 1000×680 logical/physical viewport with Camera2D zoom 1. It checks known ink/gap samples, phase +7 changes, a double-precision phase immediately below an ON/OFF boundary, cumulative phase at a bend, no ink at an OFF vertex, one source-over blend at a translucent continuing corner, an ON branch at a crossing where another branch is OFF, internal BUTT/ROUND/SQUARE cut shapes including paired ROUND-negative/SQUARE-positive diagonal probes, an AA cut with partial coverage versus a hard cut with none, SCREEN ink and gap, and a visible 64-point/eight-entry pattern. It repeats SCREEN ink/gap and WORLD ink/gap at zoom 2, zoom 0.5, and after camera movement; transformed angled-cut AA produces more partial pixels than the hard control at all three camera stages.

A separate RGBA8888 offscreen fixture renders a 1000×680 logical projection into a 2000×1360 physical FBO. It confirms foreground and gap samples plus 7 partial pixels at the dashed AA cut. This is framebuffer-scale evidence, not OS HiDPI verification. The 1× gallery and 2× fixture are [shapes-dashes.png](artifacts/shapes-dashes/shapes-dashes.png) and [shapes-dashes-2x-framebuffer.png](artifacts/shapes-dashes/shapes-dashes-2x-framebuffer.png). Structured samples are in [shapes-dashes-results.json](artifacts/shapes-dashes/shapes-dashes-results.json) and [shapes-dashes-scale-results.json](artifacts/shapes-dashes/shapes-dashes-scale-results.json). The run leaves no GL error. Rerun with `./gradlew :javity-desktop:shapesSmoke`.
