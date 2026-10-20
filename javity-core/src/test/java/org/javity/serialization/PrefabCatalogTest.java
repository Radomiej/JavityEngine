package org.javity.serialization;

import static org.junit.Assert.*;

import java.util.List;
import org.javity.core.Component;
import org.javity.core.Game;
import org.javity.render.ShapeRenderer2D;
import org.junit.Test;

public class PrefabCatalogTest {
  private static int releases;

  private static final class Releasable extends Component {
    @Override
    protected void onRelease() {
      releases++;
    }
  }

  private static final class Broken extends Component {}

  private static final String SCENE =
      "{\"version\":1,\"objects\":[{\"name\":\"critter\",\"active\":true,"
          + "\"x\":1,\"y\":2,\"rotation\":0,\"sx\":1,\"sy\":1,\"ox\":0,\"oy\":0,"
          + "\"tags\":[],\"components\":[]}]}";

  @Test
  public void appliesValidatedOverrideAndAllocatesFreshObjectIds() {
    PrefabId id = new PrefabId("actors/critter");
    PrefabDefinition source = new PrefabDefinition(id, 1, SCENE);
    PrefabCatalog catalog = new PrefabCatalog(1).register(source);
    assertEquals(SCENE, source.sceneJson());
    try (Game game = new Game()) {
      var first =
          catalog.instantiate(
              game,
              id,
              SceneJson.standard(),
              assetResolver(),
              List.of(PrefabOverride.number("objects[0].x", 42)));
      var second = catalog.instantiate(game, id, SceneJson.standard(), assetResolver(), List.of());
      assertEquals(42, first.get(0).transform.position.x, 0);
      assertEquals(1, second.get(0).transform.position.x, 0);
      assertNotSame(first.get(0), second.get(0));
      assertNotEquals(first.get(0).id, second.get(0).id);
    }
  }

  @Test
  public void componentOverrideTargetsAnExistingStandardCodecField() {
    SceneJson sceneJson = SceneJson.standard();
    PrefabId id = new PrefabId("shapes/box");
    try (Game source = new Game();
        Game target = new Game()) {
      source.createObject("box").addComponent(ShapeRenderer2D.rectangle(10, 4));
      PrefabCatalog catalog =
          new PrefabCatalog(1)
              .register(new PrefabDefinition(id, 1, sceneJson.write(source.getObjects())));
      var result =
          catalog.instantiate(
              target,
              id,
              sceneJson,
              assetResolver(),
              List.of(
                  PrefabOverride.number("objects[0].components[0].data.width", 123456.789123d),
                  PrefabOverride.number("objects[0].components[0].data.layer", 17)));
      ShapeRenderer2D parsed = result.get(0).getComponent(ShapeRenderer2D.class);
      assertEquals((float) 123456.789123d, parsed.width, 0);
      assertEquals(17, parsed.layer);
      assertEquals(4, parsed.height, 0);
    }
  }

  @Test
  public void migratesInOrderAndRejectsMissingMigration() {
    PrefabId id = new PrefabId("actors/migrating");
    PrefabCatalog catalog =
        new PrefabCatalog(3)
            .register(new PrefabDefinition(id, 1, SCENE))
            .registerMigration(id, 1, json -> json.replace("critter", "migrated"))
            .registerMigration(id, 2, json -> json.replace("\"x\":1", "\"x\":7"));
    try (Game game = new Game()) {
      var result = catalog.instantiate(game, id, SceneJson.standard(), assetResolver(), List.of());
      assertEquals("migrated", result.get(0).name);
      assertEquals(7, result.get(0).transform.position.x, 0);
    }
    PrefabCatalog unsupported = new PrefabCatalog(2).register(new PrefabDefinition(id, 1, SCENE));
    try (Game game = new Game()) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              unsupported.instantiate(game, id, SceneJson.standard(), assetResolver(), List.of()));
      assertTrue(game.getObjects().isEmpty());
    }
  }

  @Test
  public void rejectsBadPathsTypesUnknownIdsAndMissingAssetsBeforeCreatingObjects() {
    PrefabId id = new PrefabId("actors/asset-user");
    AssetId<String> texture = new AssetId<>("textures/critter");
    PrefabCatalog catalog =
        new PrefabCatalog(1).register(new PrefabDefinition(id, 1, SCENE, List.of(texture)));
    try (Game game = new Game()) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              catalog.instantiate(
                  game, new PrefabId("missing"), SceneJson.standard(), assetResolver(), List.of()));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              catalog.instantiate(
                  game,
                  id,
                  SceneJson.standard(),
                  assetResolver(),
                  List.of(PrefabOverride.text("objects[0].active", "no"))));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              catalog.instantiate(
                  game,
                  id,
                  SceneJson.standard(),
                  assetResolver(),
                  List.of(PrefabOverride.number("objects[0].notAField", 3))));
      AssetResolver missing =
          new AssetResolver() {
            @Override
            public <T> T resolve(AssetId<T> requested) {
              return null;
            }
          };
      assertThrows(
          IllegalArgumentException.class,
          () -> catalog.instantiate(game, id, SceneJson.standard(), missing, List.of()));
      assertTrue(game.getObjects().isEmpty());
    }
  }

  @Test
  public void resolverReturnsHostOwnedAssetWithoutCatalogTakingOwnership() {
    AssetId<String> id = new AssetId<>("audio/theme");
    String hostAsset = new String("host-owned");
    assertSame(hostAsset, new PrefabCatalog(1).resolve(assetResolverFor(id, hostAsset), id));
    assertThrows(IllegalArgumentException.class, () -> new AssetId<>(" "));
  }

  @Test
  public void failedCodecReadHidesPartialObjectsAndDisposesTheirComponentsOnGameClose() {
    releases = 0;
    SceneJson schema =
        SceneJson.standard()
            .register(
                "releasable",
                Releasable.class,
                ignored -> SceneJson.object(),
                ignored -> new Releasable())
            .register(
                "broken",
                Broken.class,
                ignored -> SceneJson.object(),
                ignored -> {
                  throw new IllegalArgumentException("bad data");
                });
    String failing =
        "{\"version\":1,\"objects\":[{\"name\":\"partial\",\"components\":["
            + "{\"type\":\"releasable\",\"data\":{}},{\"type\":\"broken\",\"data\":{}}]}]}";
    PrefabId id = new PrefabId("broken/codec");
    PrefabCatalog catalog = new PrefabCatalog(1).register(new PrefabDefinition(id, 1, failing));
    Game game = new Game();
    assertThrows(
        IllegalArgumentException.class,
        () -> catalog.instantiate(game, id, schema, assetResolver(), List.of()));
    assertTrue(game.getObjects().isEmpty());
    assertEquals(0, releases);
    game.close();
    assertEquals(1, releases);
  }

  private static AssetResolver assetResolver() {
    return new AssetResolver() {
      @Override
      public <T> T resolve(AssetId<T> id) {
        return null;
      }
    };
  }

  private static AssetResolver assetResolverFor(AssetId<String> expected, String asset) {
    return new AssetResolver() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T resolve(AssetId<T> id) {
        return id.equals(expected) ? (T) asset : null;
      }
    };
  }
}
