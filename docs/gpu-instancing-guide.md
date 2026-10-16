# Jak włączyć instancing modeli

Dodaj `InstancedModel` obok zwykłego `ModelRenderer`. Ten sam opt-in obsługuje sztywne modele i modele z kośćmi. Każdy właściciel nadal ma własny `ModelInstance`, animator, zegar i zdarzenia.

```java
Game game = new Game();
// Opcjonalne; zmierz koszt całego kroku przed włączeniem w grze:
// game.addSystem(new PoseBatchSystem(1024));
GameObject object = game.createObject("Fox");
object.addComponent(new Transform3D());
object.addComponent(new ModelRenderer(borrowedFox));
object.addComponent(new InstancedModel());
ModelPoseAnimator pose = object.addComponent(new ModelPoseAnimator());
GdxRenderer renderer = new GdxRenderer(24, 4); // Fox:24 kości,4 atrybuty wag
// Utwórz też Camera3D, skonfiguruj viewport i rozpocznij grę.
game.start();
game.step(0);
pose.play("Walk", PlayMode.LOOP);
```

`game.step(delta)` aktualizuje kanoniczną pozę; `renderer.render(game)` tylko ją odczytuje. GPU instancing działa także bez `PoseBatchSystem` i z istniejącym `AnimationController`. Nie dodawaj drugiego właściciela pozy do tego samego modelu. Domyślny `GdxRenderer()` ma12 kości; Fox wymaga jawnej konfiguracji24/4 także dla zwykłego fallbacku. Model musi pochodzić ze współdzielonego, wczytanego zasobu; tworzenie nowej siatki dla każdego właściciela uniemożliwia wspólne grupowanie.

`renderer.instanceStats()` raportuje kandydatów, widoczność, fallback, grupy, draw calls i rzeczywiście przesłane dynamiczne bajty. `renderer.instanceFallbackReasons()` wyjaśnia odmowę. `draws` dotyczy ścieżki instanced; zwykły fallback ma dodatkowe draw calls. Jedna grupa oznacza zgodny MeshPart/materiał/pass, więc wielomateriałowy model może wymagać kilku draw calls. Różne pozy tej samej geometrii mogą pozostać w jednym draw. Domyślna pojemność draw to256 instancji, palet1024 wiersze; większy tłum dzieli draw albo przechodzi na jawny fallback po przekroczeniu budżetu.

WORLD regiony `AvatarView` zachowują podział części, prezentację i kanoniczne sockety. Osobny viewmodel FPS pozostaje na zwykłej ścieżce. Culling używa konserwatywnych bounds aktualnych kości, a nie tylko bind pose.

Wspierany podzbiór to trójkąty z pozycją/normalną, opcjonalnym UV0 i poprawnymi wagami; dodatnia jednorodna skala; diffuse color/texture, opaque i jawny alpha-test; ambient oraz do dwóch directional lights. `GL30_UNAVAILABLE`, limity atrybutów/tekstury/vertex texture units, budżet palet, niepoprawne wagi/zakresy, nieregularne transformacje i nieobsługiwane materiały/światła dają zwykły fallback. Fog, spot/point lights, blending i własne shadery wymagają osobnej implementacji. Fallback zachowuje zachowanie zwykłego renderera, łącznie z jego walidacją błędnego assetu.

Renderer posiada kopie geometrii, VAO, instance buffers, programy i palety RGBA32F; pożycza źródłowe `Model`, `Mesh` i tekstury. Po zmianie źródłowych vertex/index danych wywołaj `renderer.invalidateGeometry(mesh)` przed następnym renderem, poza aktywnym batchem. Zmiana sceny usuwa stare grupy i sloty, także gdy nowa scena nie ma kamery. Zamknij renderer na wątku GL przed źródłowymi assetami i kontekstem; zamknij `Game`, aby zwolnić opcjonalny cache sceny.

Po rzeczywistym odtworzeniu kontekstu, po managed restoration libGDX, host wywołuje `renderer.restoreContextShaderBindings()` na wątku właściciela. Bezpośrednie `StaticInstanceBatch`/`SkinnedInstanceBatch` wymagają własnego `contextRestored()`. Hook odtwarza surowe instance buffers/palety i lokalizacje shaderów. Wywoływanie go jako resetu w nadal żywym kontekście nie jest właściwym lifecycle.

Cache CPU jest oddzielnym opt-in: redukcja pełnych kompozycji nie gwarantuje niższego czasu CPU, ponieważ weryfikacja źródeł i kopiowanie też kosztują. [Powtarzalny benchmark i dowody](artifacts/instancing/README.md), [oracle importowanego Foxa](artifacts/skinned-instancing/README.md), [decyzje architektoniczne](gpu-instancing-decisions.md). Fox: PixelMannen (model CC0), tomkranis (rig/animacje CC BY4.0), AsoboStudio/scurest (konwersja CC BY4.0); [pełne notices](../tools/animation-validation/fox/NOTICE.md).
