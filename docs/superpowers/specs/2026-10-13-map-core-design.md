# Mapy dla nowego rdzenia

Cel: migrować plugin geograficzny OSM do komponentów bez Artemis. Osobny opcjonalny moduł javity-map-core zależy tylko od javity-core. Legacy javity-map zachowuje stare API.

Projekcja Web Mercator używa double i kafelków 256 px; długość geograficzna zawija się, szerokość jest ograniczona do zakresu Mercatora. MapView jest komponentem kamery: center, integer zoom, viewport, pan i zoom przy wskazanym pikselu. Widoczność wyliczana bez obiektów dla każdego kafelka, przy poprawnym zawijaniu X i ograniczeniu Y.

TileSource dostarcza Pixmap lub null; renderer przejmuje Pixmap i zwalnia po uploadzie do Texture. FileTileSource czyta z/x/y.png, bez sieci. Renderer posiada ograniczony LRU tekstur i zwalnia go przy dispose. Brak kafelka nie jest cache'owany; ponowienia zależą od źródła. Render jest synchroniczny na wątku GL; asynchroniczny HTTP pozostaje osobnym etapem.

MapMarker i MapRoute są komponentami nowej sceny. Rysowanie używa caller-owned Batch i kamery pikselowej; przywraca kolor Batch. Renderer nie zarządza projekcją Batch. Markery/trasy są w przestrzeni geograficznej, nie Transform2D. Testy: projekcja, dateline, zoom anchor, widoczne kafelki, LRU i ownership; demo offline z rzeczywistym GL, resize/pan/zoom i zwalnianiem zasobów.

Uzupełnienie użytkownika: style JSON jak MapLibre i Shapes wymagają osobnego projektu; na teraz priorytet migracji. Dodano font Open Sans/FreeType, Camera2D i FollowTarget2D. Pełny backlog i audyt starego API w docs/TODO.md i docs/migration-api.md; multiplayer tylko notatki projektu.
