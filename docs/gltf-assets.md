# Opcjonalny glTF/GLB i PBR

`javity-gltf` przypina **gdx-gltf 2.3.0** (Apache 2.0), importer i rzeczywiste
shadery PBR z https://github.com/mgsx-dev/gdx-gltf/tree/2.3.0. Artefakt
`com.github.mgsx-dev.gdx-gltf:gltf:2.3.0` pochodzi z JitPack; repozytorium ma filtr
wyłącznie tej grupy. libGDX pozostaje **1.14.2** przez istniejącą politykę
rozwiązywania zależności. Rdzeń nie zależy od gdx-gltf. Aplikacja dodaje
`implementation project(':javity-gltf')`.

## Uruchomienie

```sh
./gradlew :javity-gltf:test
./gradlew :javity-desktop:gltf
./gradlew :javity-desktop:gltfSmoke
./gradlew :javity-desktop:gltfGl20Smoke
```

Smoke wymaga rzeczywistego OpenGL, np. `xvfb-run -a` z Mesa na serwerze.
Interaktywne demo: trzy modele (statyczny i dwie instancje tego samego modelu
skinned), Space przełącza światło, Esc kończy. Kamera, Transform3D,
DirectionalLight3D i ModelRenderer należą do tego samego `Game`; adapter nie
uruchamia osobnego SceneManager ani równoległego grafu sceny.

## Autorowanie i własność

```java
GltfAssets assets = game.addSystem(new GltfAssets(32));
AssetId<GltfAsset> player = new AssetId<>("characters/player");
assets.register(player, Gdx.files.internal("models/player.glb"));
PbrShaderProvider shaders = new PbrShaderProvider(32, 2, 4, 0);
GdxRenderer renderer = shaders.createRenderer(); // renderer owns provider
AssetLease<GltfAsset> lease = assets.request(player);
if (lease.state() == AssetLease.State.FAILED) {
    throw new IllegalStateException("Player import failed", lease.failure());
}
GltfInstance instance = lease.get().instantiate();
GameObject object = game.createObject("Player");
object.addComponent(new Transform3D());
ModelRenderer model = object.addComponent(instance.createRenderer());
model.play("Walk", true);
lease.close(); // instance continues to pin its complete asset
// Teardown: remove/close game components, then instance.close(), then renderer.dispose().
```

Register service przed konsumentami i przed `game.start()`. `request()` jest
**synchronicznym** importem/uploadem na wątku właściciela GL, poza aktywnymi
batchami. Używa istniejącego `AssetId<GltfAsset>` i `AssetLease<GltfAsset>`;
nie podszywa się pod budżetowaną kolejkę `AssetStreams`. Stan lease to READY,
FAILED lub CLOSED; FAILED zachowuje przyczynę. Brak ID i duplikaty manifestu są
błędami konfiguracji. Równoległe otwarte lease tego samego ID dzielą asset.
Zamknięcie ostatniego lease usuwa generację z katalogu; następny request może
załadować nową, mimo że instancje jeszcze przypinają starą.

`GltfAsset` posiada kompletny upstream `SceneAsset`: wszystkie importowane
sceny/modele, meshe, tekstury i retained pixmaps. `ModelInstance` i
`ModelRenderer` pożyczają je; nie wolno wywoływać `model.dispose()` na modelu
instancji. Każda instancja ma własne węzły, kopie materiałów, macierze kości i
kontroler animacji ModelRenderer. Klipy/keyframes i GPU geometria pozostają
współdzielone. `close()` assetu blokuje nowe instancje i odracza zwolnienie GPU
aż wszystkie istniejące instancje zostaną zamknięte. Zamykaj uchwyty instancji
po odłączeniu renderujących komponentów, także po błędach inicjalizacji.
Własny Component może zarejestrować uchwyt przez `own(instance)`; aplikacja może
użyć `ResourceScope`. Zamknięcie lease/instance/asset jest idempotentne i wymaga
wątku konstruktora. Zamknięcie usługi unieważnia lease, ale nie odbiera zasobów
żywym instancjom.

`ManagedTextureResolver` tworzy zarządzane tekstury z `PixmapTextureData`;
źródłowe Pixmap pozostają własnością assetu do końca jego życia. Jest to jawna
zmiana względem domyślnego TextureResolver upstream, który tworzy tekstury
niezarządzane. Pozwala odtworzyć dane bez ponownego odczytu pliku.

`TransactionalGltfLoader` używa publicznego API przypiętej wersji, aby podczas
nieudanego importu zwolnić także meshe utworzone przed powrotem `loadScene()`.
Upstream rollback ich nie obejmuje. Nie kopiujemy parsera ani shaderów.
Preflight CPU nie jest pełnym walidatorem glTF ani sandboxem dla niezaufanych
plików; assety powinny przejść oficjalny Khronos glTF Validator przed dystrybucją.
Limit 64 MiB dotyczy pliku głównego; nie jest limitem całego pakietu zależności.
Import `.gltf` zachowuje relatywne lokalne zależności; nie pobiera HTTP.

## Materiały i światło

Fabryka `instance.createRenderer()` deklaruje wymaganie `PbrShaderProvider`.
Próba narysowania takiego komponentu domyślnym GdxRenderer kończy się jawnym
błędem zamiast utraty właściwości materiału. Niskopoziomowy
`new ModelRenderer(instance.modelInstance())` świadomie omija ten guard.
PBR to opt-in dla całego passu modeli; zwykłe materiały diffuse wymagają jawnej
konwersji przez aplikację. `ColorAttribute.Diffuse`, `Specular`, `Shininess`
i pozostałe atrybuty poza jawnym profilem są odrzucane przed alokacją shadera.
Tekstury materiału muszą używać `PBRTextureAttribute`. Proceduralna geometria
używa `PBRColorAttribute.BaseColorFactor` i metallic/roughness, również podłoże
w scenie cieni. Adapter nie wykonuje niejawnej konwersji do Lambert.

| Obszar | Kontrakt |
| --- | --- |
| Materiały | Metallic/roughness, base color/alpha, normal, emissive, occlusion, double sided oraz upstream texture transform z jednym wspólnym offset/scale/rotation na efektywny zestaw UV w obrębie materiału; różne transformacje kanałów na tym samym UV są odrzucane. PNG/JPEG, UV0/UV1, vertex COLOR_0. |
| Kolor | Upstream `SRGB.ACCURATE` dla tekstur koloru i ręczna gamma 2.2 na wyjściu; mapy danych pozostają liniowe. Ten pass rysuje do zwykłego framebufferu RGBA. Nie dodawaj drugiej konwersji sRGB/gamma. Konstruktor z `OutputColorSpace.LINEAR` wyłącza tylko gamma wyjściową dla HDR/tone mapping; dokładne dekodowanie wejściowych map koloru sRGB pozostaje włączone. `outputColorSpace()` ujawnia tryb; upstream shader nie wykonuje ukrytego tone mapping. |
| Światła kierunkowe/ambient | Bieżący snapshot RenderEnvironment3D albo legacy Environment. Widoczność, transformacje i capacity pozostają regułami rdzenia. |
| Światła punktowe | `RangePbrShader` zachowuje scene range/pozycję/kolor/intensity i BRDF upstream. Dla skończonego range stosuje ten sam `(max(0,1-distance/range))²` co Javity forward. Jawny `PointLightEx.range` również działa; legacy bez range zachowuje upstream `1/(1+distance²)`. |
| Spot/fog | Javity cosine^exponent spot i liniowe near/far fog są odrzucane podczas draw; nie mapujemy ich na inny model bez zgody autora. |
| IBL | Brak domyślnego IBL. `environmentExtras()` pożycza jawnie dostarczone atrybuty diffuse/specular cubemap + BRDF LUT upstream. Ich zasoby należą do aplikacji i muszą przeżyć wszystkie draw calls. Sam import ich nie generuje. |
| Instancing | Custom provider kieruje również `InstancedModel` do zwykłych draw calls. Nie używa shaderów instancingu, które nie zachowują pełnego PBR. |
| Viewmodel | Ten sam provider i zgodne materiały; odrębna kamera/pass istniejącego renderera. |
| Context restore | Po standardowych invalidacjach managed Texture/ShaderProgram/Mesh/GLFrameBuffer wywołaj `renderer.restoreContextShaderBindings()`. Odtwarza shadery PBR i przez wspólny core `ManagedMeshBindingsRecovery` czyści zachowany przez libGDX1.14.2 cache lokalizacji VAO. Współdzielone meshe i modele zachowują tożsamość. Adapter zachowuje do 16 odrębnych Environment na view; ich shadowMap i światła pozostają stabilne do końca batcha. Dowód używa rzeczywistego drugiego, niewspółdzielącego kontekstu GLFW; nie jest testem Android/EGL. |

## Jawny profil importu

Obsługiwane: glTF 2.0 `.gltf` i `.glb`, domyślna scena, trójkąty, statyczne meshe,
rig do zadeklarowanego limitu kości z maksymalnie czterema wagami/wierzchołek,
klipy **LINEAR TRS** i istniejący ModelAnimator/ModelPoseAnimator. Nazwy węzłów i
efektywne identyfikatory klipów muszą być unikalne (również nazwy generowane
przez upstream `animation0`, `animation1`, ... dla nienazwanych klipów). Kamery i światła osadzone w pliku nie są
instancjonowane jako komponenty Javity; aplikacja tworzy własne. Importowany
asset nadal posiada pozostałe sceny, ale API instancjonuje wyłącznie domyślną.

Dozwolone rozszerzenia: `KHR_materials_unlit`, `KHR_texture_transform`,
`KHR_materials_emissive_strength`. Inne rozszerzenia są odrzucane nawet jako
opcjonalne, aby nie ignorować intencji materiału. Dotyczy to m.in.
Draco/Meshopt, KTX2/BasisU, transmission/volume/IOR/specular/iridescence,
clearcoat i osadzonych `KHR_lights_punctual`. Odrzucane są również morph targets,
STEP/CUBICSPLINE, sparse accessors, linie/punkty i dodatkowe zestawy wag/kolorów.
To celowo węższy profil niż pełna biblioteka gdx-gltf.

Przypięty gdx-gltf 2.3.0 definiuje `PointLightEx.range`, lecz jego shader PBR
nie odczytuje tego pola. `RangePbrShader` rozszerza prawdziwy shader PBR:
rozbudowuje rozwinięty źródłowy shader Apache-2.0 o tablicę range i mnożnik
wkładu światła, przesyłany po standardowym `bindLights`. Dwie konkretne kotwice
źródła muszą wystąpić dokładnie raz; zmiana biblioteki bez aktualizacji adaptera
kończy się błędem zamiast niejawnej utraty zasięgu. Finite range znosi upstream
`1/(1+d²)` i stosuje kwadratowy cutoff rdzenia; materiały, BRDF i pozostałe
przetwarzanie shaderów pochodzą z gdx-gltf. Brak retargetingu, root
motion, HDR/tone mapping, automatycznej optymalizacji i certyfikacji Android/web.

## Dowody i licencje

Testy CPU obejmują niezależną pozę, odroczenie i idempotencję cleanup,
FAILED/retry lease, nieprawidłowe GLB, limity kości, odrzucane rozszerzenia,
zachowanie metadata range, ochronę przed zmianą źródła shadera i przejścia oświetlenia. Test GPU sprawdza rzeczywisty import
GLB/glTF, zachowanie pięciu kanałów map, współdzielony Model i niezależne
palety/pozy, piksele po zmianie światła i Transform3D, zwykły fallback instancing,
rollback błędu po utworzeniu mesha, finite range4 vs0.25, zmianę roughness, cache shaderów, managed reload po usunięciu nazw tekstur oraz native texture deletion. Osobny krok przełącza GLFW na drugi, niewspółdzielący kontekst i wymaga identycznych pikseli po invalidacji/odbudowie. Używa `RecoverableFrameBuffer`, aby odbudowa nie powielała rejestracji managed FBO.
Zrzuty są w `build/reports/gltf/gl20` lub `gl30`. Wynik kompilacji/testów należy
raportować oddzielnie od wyniku uruchomienia tego smoke.

Małe fixture i generator w `javity-gltf` są własną geometrią/proceduralnymi
teksturami CC0; licencja znajduje się obok plików. `invalid-after-mesh.glb` jest
celowo wadliwym fixture rollbacku. Nie jest przykładem eksportu produkcyjnego.
