# Shapes AA — batch desktop

Cel: shaderowe antyaliasowane circle/rectangle/polyline w istniejącym libGDX, bez Unity lub kopiowania Shapes. Użytkownik autoryzował batch i agentów Luna pod nadzorem roota. Starsze zmiany w workspace zachować; bez commit/push.

API ShapeRenderer2D: public boolean antiAlias (true poza polygon), float cornerRadius=0; enum LineCap BUTT/ROUND/SQUARE; enum LineJoin ROUND; lineCap=ROUND, lineJoin=ROUND. Dodatkowo enum Space WORLD/SCREEN i space=WORLD. Stroke ma szerokość w lokalnych jednostkach i jest wyśrodkowany na konturze. Radius prostokąta clamp do połowy min(width,height). Polygon pozostaje legacy triangulated i bez nowego AA. Screen figury używają pixel projection i sortują się po WORLD, przed zewnętrznym UI.

SmoothShapeRenderer w javity-core/render posiada shader, biały texture i batch. draw(ShapeRenderer2D shape, Matrix4 projection, Matrix4 transform, int viewportWidth, int viewportHeight); dispose(). Wywołanie poza aktywnym caller Batch. Circle/rectangle/polyline korzystają z odległości signed-distance i fwidth dla ~1px fringe; antiAlias=false daje twarde pokrycie tą samą geometrią. Shader/texture tylko raz per renderer. Geometry shaderu w lokalnych współrzędnych, świat i screen kontroluje caller. Obszar quadu zawiera stroke i AA fringe obliczony z projection*transform/viewport; zero scale pomija rysowanie.

Polyline: unia odległości segmentów/jointów w jednym draw, aby alpha nie dodawała się na łączeniach. Do 64 efektywnych punktów, jawny wyjątek przy przekroczeniu zamiast cichego obcinania. Powtórzone punkty pomijać; pojedynczy punkt przy ROUND cap może dać dysk, inne caps nie mają powierzchni. Round joints; butt/square/round endpoints. Finite geometry validation zanim dotrze do GL. Brak deklaracji pełnego AA dla polygon albo pełnych Shapes join/material APIs.

Root integruje GdxRenderer (wspólne layer/order, WORLD→SCREEN), launcher/build/CI i dokumentację. Luna A: shader/math backend i własne testy. Luna B: style komponentu + SceneJson + testy kontraktu. Luna C: osobne ShapesDemo GL/smoke. Bez wspólnej edycji tych samych plików. Agent reports wymagają review i świeżych testów roota.

Weryfikacja: geometry validation, powtórzenia/degeneraty, round-trip i defaults starego JSON; GL pixels coverage AA przeciw alias, alpha na jointach, caps, zoom/anisotropic scale, SCREEN/WORLD, stabilna kolejność i brak wpływu na sprite/text/UI. Czysty reactor, poprzednie cztery dema oraz nowe shapes smoke. Linux x64/JDK25; nie deklarować mobile/web.
