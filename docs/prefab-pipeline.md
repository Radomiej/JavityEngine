# Prefab catalog pipeline

`Prefab` remains the small compatibility wrapper around a serialized `SceneJson` document. `PrefabCatalog` adds stable IDs, per-catalog format versions, explicit asset dependency checks, migrations, and authored-field overrides while still using the registered `SceneJson` codecs to create components. It does not load classes from serialized names or inject assets into components automatically.

## Example

This example assumes the host already loaded `borrowedTexture`. The component borrows that texture and does not dispose it. Its codec closes over the same host resolver used by the catalog, so component creation resolves the stable ID through an explicit, application-owned seam.

```java
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.JsonValue;
import java.util.List;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.core.GameObject;
import org.javity.serialization.*;

final class TextureMarker extends Component {
  final AssetId<Texture> imageId;
  final Texture image; // borrowed; never disposed by this component

  TextureMarker(AssetId<Texture> imageId, Texture image) {
    this.imageId = imageId;
    this.image = image;
  }
}

List<GameObject> spawnCritter(Game game, Texture borrowedTexture) {
  AssetId<Texture> imageId = new AssetId<>("textures/critter");
  AssetResolver hostAssets = new AssetResolver() {
    @Override
    @SuppressWarnings("unchecked")
    public <T> T resolve(AssetId<T> requested) {
      if (requested.equals(imageId)) return (T) borrowedTexture;
      throw new IllegalArgumentException("Unknown asset ID: " + requested.value());
    }
  };

  SceneJson sceneJson = SceneJson.standard();
  sceneJson.register(
      "texture-marker",
      TextureMarker.class,
      marker -> {
        JsonValue data = SceneJson.object();
        SceneJson.text(data, "image", marker.imageId.value());
        return data;
      },
      data -> {
        AssetId<Texture> id = new AssetId<>(data.getString("image"));
        return new TextureMarker(id, hostAssets.resolve(id));
      });

  PrefabId critterId = new PrefabId("actors/critter");
  String authoredScene = """
      {"version":1,"objects":[{"name":"critter","active":true,
       "x":1,"y":2,"rotation":0,"sx":1,"sy":1,"ox":0,"oy":0,
       "tags":[],"components":[{"type":"texture-marker","enabled":true,
       "updateMode":"WORLD","data":{"image":"textures/critter"}}]}]}
      """;

  PrefabCatalog catalog = new PrefabCatalog(2)
      .register(new PrefabDefinition(critterId, 1, authoredScene, List.of(imageId)))
      .registerMigration(critterId, 1,
          json -> json.replace("\"name\":\"critter\"", "\"name\":\"critter-v2\""));

  return catalog.instantiate(
      game,
      critterId,
      sceneJson,
      hostAssets,
      List.of(PrefabOverride.number("objects[0].x", 48)));
}
```

The catalog runs each required migration in order, from definition version `v` to `v + 1`, until its `currentVersion` is reached. Each step receives an immutable JSON string and must return a new valid document without external side effects. Missing steps, null results, or thrown failures reject instantiation. A definition newer than the catalog is rejected when registered. The catalog reparses the stored definition string for each instantiation, so applying overrides or running a migration cannot mutate the saved definition.

`PrefabDefinition.assets` lists the typed dependencies that the catalog resolves before creating any scene objects. Resolution is validation only: returned values remain borrowed and are not stored, closed, or passed implicitly to codecs. As shown above, an explicit codec may close over the same host resolver to obtain assets while reading component data. This keeps asset ownership and the component's actual resource use visible in application code. A missing dependency must fail in the resolver; a null result is also rejected.

Overrides replace existing scalar fields only. Supported object fields are `name`, `active`, and the serialized 2D transform values `x`, `y`, `rotation`, `sx`, `sy`, `ox`, and `oy`. Component overrides use paths such as `objects[0].components[0].data.width` and must name an existing scalar in that codec's serialized `data`. The replacement must keep the existing JSON scalar type. Paths cannot change object IDs, codec keys, component structure, or create fields. Duplicate paths, missing fields, wrong types, and out-of-range indexes fail before `SceneJson.read` begins.

`SceneJson.read` remains responsible for component construction and rollback. If a codec fails after one or more objects have been created, those objects are marked destroyed and immediately disappear from `Game.getObjects()`. Component `onRelease` cleanup is deferred until the game's normal cleanup flush, such as `Game.close()`; the failed instantiation does not roll back game ID allocation or arbitrary side effects performed by codecs. Keep codec reads deterministic and avoid external side effects before successful component construction.

Existing callers can keep using `new Prefab(json).instantiate(game, sceneJson)`. That path has no catalog version, asset dependency, or override layer.
