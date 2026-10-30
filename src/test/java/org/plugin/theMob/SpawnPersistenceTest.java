package org.plugin.theMob;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.bukkit.Location;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class SpawnPersistenceTest {
  ServerMock server;
  TheMob plugin;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  @Test
  void radiusSettingsSurviveSaveAndReload() {
    assertTrue(
        plugin
            .spawns()
            .setRandomRadius(
                "pig_lvl_1", new Location(server.getWorld("world"), 20, 65, 20), 17, 4, 8, 29));
    plugin.reloadPlugin();
    var point = plugin.spawns().points().getFirst();
    assertEquals(8, point.minRadius());
    assertEquals(29, point.maxRadius());
    assertEquals(2, point.arenaRadiusChunks());
    assertEquals(17, point.intervalSeconds());
    assertEquals(4, point.maxSpawns());
  }

  @Test
  void disabledAndCustomArenaSettingsSurviveAnotherSpawnEdit() {
    var cfg = plugin.configs().autoSpawn();
    var saved = new LinkedHashMap<String, Object>();
    saved.putAll(
        Map.of(
            "type",
            "RANDOM_RADIUS",
            "mobId",
            "pig_lvl_1",
            "world",
            "world",
            "enabled",
            false,
            "intervalSeconds",
            20,
            "maxSpawns",
            3,
            "minRadius",
            11,
            "maxRadius",
            33,
            "arenaRadiusChunks",
            5));
    saved.putAll(Map.of("x", 0, "y", 64, "z", 0));
    cfg.set("spawns", List.of(saved));
    plugin.configs().saveAutoSpawn();
    plugin.reloadPlugin();
    var point = plugin.spawns().points().getFirst();
    assertFalse(point.enabled());
    assertTrue(
        plugin
            .spawns()
            .setAutoSpawnFixedPoint(
                "pig_lvl_1", new Location(server.getWorld("world"), 30, 64, 30), 25, 3));
    plugin.reloadPlugin();
    point =
        plugin.spawns().points().stream()
            .filter(p -> p.type() == org.plugin.theMob.spawn.type.SpawnType.RANDOM_RADIUS)
            .findFirst()
            .orElseThrow();
    assertFalse(point.enabled());
    assertEquals(5, point.arenaRadiusChunks());
    assertEquals(11, point.minRadius());
    assertEquals(33, point.maxRadius());
  }

  @Test
  void followPlayerPointsLoadWithoutRequiringFixedWorldCoordinates() {
    plugin
        .spawns()
        .setFollowPlayer(
            "Tester",
            "pig_lvl_1",
            18,
            5,
            org.plugin.theMob.spawn.type.SpawnMode.ENDLESS,
            9,
            26,
            "Warnung");
    plugin.reloadPlugin();
    var point = plugin.spawns().points().getFirst();
    assertEquals(org.plugin.theMob.spawn.type.SpawnType.FOLLOW_PLAYER, point.type());
    assertEquals("Tester", point.playerName());
    assertEquals(9, point.minDistance());
    assertEquals(26, point.maxDistance());
    assertEquals("Warnung", point.message());
  }
}
