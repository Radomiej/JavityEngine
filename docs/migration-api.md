# Inwentaryzacja migracji

Stan lokalnych źródeł, 2026-10-13. „Legacy” oznacza kod włączony do builda i zachowany do migracji; nie oznacza pełnego sprawdzenia każdej funkcji w działającej grze. Nowy rdzeń nie korzysta z Artemis.

| Dotychczasowa funkcja | Nowy odpowiednik | Pozostała praca |
| --- | --- | --- |
| JGameObject/JComponent/NativeComponent, lifecycle | GameObject/Component, deterministyczne hooki | Migracja skryptów użytkownika i nazw hooków |
| Transform 2D | Transform2D | Brak automatycznej hierarchii 2D; istnieje hierarchia 3D |
| JTime, pauza | Czas sceny, WORLD/UI | SceneTimers i SceneManager dostępne |
| JCamera/FollowCamera/RapidCamera | Camera2D/FollowTarget2D, Camera3D | Follow/orbit 3D, picking; usunięcie global main w grach |
| SpriteRenderer | SpriteRenderer2D | TextureAtlas przez SceneAssets i SpriteAnimator dostępne |
| TextRenderer/BitmapFontResource | UI Open Sans, TTF/OTF, BitmapFont i TextRenderer2D | Fallback/shaping języków |
| LineRenderer/ShapeComponent | ShapeRenderer2D i Rectangle UI, shader rounded/border | Pełne Shapes-like/materials/AA pozostają rozszerzeniem |
| Rigidbody, rectangle/circle collider | Physics2D/RigidBody2D/BoxCollider2D/CircleCollider2D | PolygonCollider2D, raycast snapshots, siły/impulsy dostępne; torque/siły w punkcie do rozszerzenia |
| TouchableObject/JInput | InputActions/PointerPicker/Pickable2D + InputMultiplexer/Stage | Gamepad i touch gestures do rozszerzenia |
| SoundPlayer, Sound/Music resources | AudioSource + SceneAssets, legacy zachowane | Spatial audio do rozszerzenia |
| SpineRenderer | Zachowany stary runtime | Aktualizacja Spine i zgodność eksportów/licencji |
| JSceneManager/CustomScene/SceneBuilder | Game, SceneManager, SceneTimers i Prefab/SceneJson | Import dowolnych starych gier wymaga mapperów |
| JSON/Kryo scenes i proxy refleksji | Zachowane w legacy, poprawione public fields | Nowy SceneJson v1 i jawne codeki; historyczne sceny nadal przez legacy |
| JResources | AssetManager renderera modeli, zasoby UI/map | SceneAssets z ownership; asynchroniczny asset streaming do rozszerzenia |
| Plugin map OSM/Marker/Layer | MapView/MapMarker/MapRoute, TileSource, LRU | AsyncTileSource/TileByteCache/HttpTileFetcher dostępne; GeoJSON layer do osobnej migracji |
| HTTP/downloader JavityNet | Moduł javity-net zachowany | Nowy provider map używa Java HttpClient, bez starego downloadera |
| Geolocation/navigation/intent | Zachowane klasy legacy | Adaptery i testy Android/iOS |
| XML UI/generator/Android | Historyczne katalogi zachowane | Migracja builda i uruchomienie platform |

Galaxy Rapid jest wrapperem/backendem libGDX obejmującym kamerę, renderer, wejście, zasoby i Artemis; nie jest zamiennikiem libGDX. Oryginalne źródła zostały włączone lokalnie do javity-backend. Plugin geograficzny w javity-map również jest lokalnie dostępny; nowy javity-map-core zastępuje jego część map bez zależności od engine/backend/net. Nie usuwamy legacy przed migracją gier i pozostałych wierszy tabeli.

Przykłady TestApp przebudowano jako --runtime: input/move, delayed sound, lifecycle i scene chooser; nie skopiowano całych gier ani obcych zestawów skin/assets. Eksporty skeleton.json/skeleton-two.json mają Spine 2.1.27, a zachowany runtime jest 3.5.51.1. Nie są dowodem zgodności współczesnego Spine.
