# Presentation3D GPU proof

`./gradlew :javity-desktop:presentation3dSmoke` runs a standalone libGDX desktop fixture with a real OpenGL context. It writes five framebuffer captures here:

- `world-base.png` — canonical model with identity presentation.
- `model-offset.png` — the same canonical transform with one local model contribution.
- `world-camera-offset.png` — the world camera after FOV and collision checked translation presentation.
- `viewmodel-offset.png` and `viewmodel-zero.png` — the same viewmodel camera with and without its local offset.

The probe prints measured pixel centroids, presented FOV and near-plane sweep radius. It also checks final camera picking/frustum state, stable canonical transform values, and disposal of the framebuffer texture while the GL context remains live. A real `VoxelCameraObstruction` fixture verifies stationary 50°/68° near-plane clearance against a loaded wall, fallback and wall-removal readmission, and preservation of every camera output when the authored fallback is unsafe. `verification.json` records this native run and its framebuffer hashes.
