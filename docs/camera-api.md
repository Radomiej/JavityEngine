# API kamer

## Nowa kamera 2D

```java
GameObject cameraObject = game.createObject("camera").setPosition(400, 300);
Camera2D camera = cameraObject.addComponent(new Camera2D());
camera.resize(800, 600);
camera.setPixelsPerUnit(1);
camera.setZoom(1);
FollowTarget2D follow = cameraObject.addComponent(new FollowTarget2D(player));
follow.offset.set(0, 40);
follow.setSmoothing(5); // 0: snap; >0: exponential response / sekundę.
Vector2 point = camera.screenToWorld(mouseX, mouseY);
Vector2 aim = camera.directionFromScreen(player.transform.position.x,
    player.transform.position.y, mouseX, mouseY);
```

`Camera2D` używa pozycji i kąta w radianach Transform2D właściciela. Konwersje mają screen origin w lewym górnym rogu (tak jak libGDX Input). Świat ma Y w górę. `worldToScreen` zwraca tę samą konwencję wejścia. Zoom >1 oddala, pixelsPerUnit ustala gęstość świata. Direction zwraca znormalizowany wektor lub zero w punkcie początkowym. Obecnie full viewport, bez letterbox/split screen.

GdxRenderer wybiera pierwszy aktywny, enabled i zainicjalizowany Camera2D w stabilnej kolejności obiektów; aktualizuje jego viewport i native camera. Bez komponentu zachowuje dotychczasową kamerę renderera. Po usunięciu używanego komponentu wraca do widoku pikselowego.

FollowTarget2D działa w late update po fizyce; pauza WORLD zatrzymuje śledzenie. Ignoruje zniszczony/nieaktywny target, nie dopuszcza targetu z innej sceny. Smoothing używa exp, więc reakcja na nieruchomy target nie zależy od dzielenia czasu na klatki.

## 3D i legacy

Camera3D ma PerspectiveCamera libGDX: position, direction, up, lookAt, near/far i fieldOfView. Renderer ustawia viewport. Natywne project/unproject/getPickRay są dostępne przez `camera.camera`, ale nie dodano jeszcze wysokopoziomowego follow/orbit, picking modeli ani wspólnej obsługi viewport. Zaplanowane w TODO. Transform3D kamery nie jest automatycznie podłączony do PerspectiveCamera.

Legacy JCamera/RapidCamera i FollowCamera nadal istnieją. FollowCamera przesuwa globalną main camera na transform obiektu. Naprawiono błąd JCamera: getZoom/setZoom używają teraz kamery danej instancji, nie statycznej main. Nie przenosimy globalnej main do nowego API.

Przegląd wykazał: stare API nie ma kompletnego systemu kamer 3D, orbit ani direction helpers. Nowy Camera2D wypełnia podstawowe konwersje, zoom, obrót, follow i celowanie; reszta wymaga osobnego etapu.
