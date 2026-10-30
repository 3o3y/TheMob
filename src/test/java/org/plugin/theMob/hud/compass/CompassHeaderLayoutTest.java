package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompassHeaderLayoutTest {
  @TempDir Path root;

  @Test
  void reservesTwoCarrierRowsBeforeNativeBossbarsAndBacksUpOnlyOnce() throws Exception {
    var config = root.resolve("config.yml");
    String original = "bossbar-line: 1\nmerge-boss-bar: true\nmy-setting: keep\n";
    Files.writeString(config, original);
    CompassHeaderLayout.reserve(root);
    var current = YamlConfiguration.loadConfiguration(config.toFile());
    assertEquals(2, current.getInt("bossbar-line"));
    assertEquals(2, current.getInt("bossbar-resource-pack-line"));
    assertFalse(current.getBoolean("merge-boss-bar"));
    assertEquals("keep", current.getString("my-setting"));
    assertEquals(original, Files.readString(root.resolve("config.yml.before-themob-1.10.2")));
    String once = Files.readString(config);
    CompassHeaderLayout.reserve(root);
    assertEquals(once, Files.readString(config));
    // First native title begins at 3 + 2*19 = 41. The arrow ends at y=27.
    assertTrue(CompassHeaderLayout.ARROW_Y + 9 < 3 + 2 * 19);
    assertTrue(CompassHeaderLayout.COMPASS_Y - 8 >= 0);
  }

  @Test
  void migratesExistingCoinOverlayPositionsAndPreservesOtherLayoutSettings() throws Exception {
    Files.createDirectories(root.resolve("compasses"));
    Files.createDirectories(root.resolve("huds"));
    Path compass = root.resolve("compasses/themob-compass.yml");
    Path marker = root.resolve("huds/themob-compass.yml");
    String before =
        "themob_compass:\n  scale: 0.75\n  gui: {x: 50, y: 0}\n  pixel: {x: 0, y: 48}\n";
    Files.writeString(compass, before);
    Files.writeString(
        marker,
        "themob_compass_marker:\n"
            + "  layouts:\n"
            + "    1:\n"
            + "      name: themob_compass_marker_layout\n"
            + "      gui: {x: 50, y: 0}\n"
            + "      pixel: {x: 0, y: 58}\n"
            + "    2:\n"
            + "      name: custom\n"
            + "      pixel: {x: 1, y: 90}\n");
    CompassHeaderLayout.position(root);
    var c = YamlConfiguration.loadConfiguration(compass.toFile());
    var m = YamlConfiguration.loadConfiguration(marker.toFile());
    assertEquals(8, c.getInt("themob_compass.pixel.y"));
    assertEquals(.75, c.getDouble("themob_compass.scale"));
    assertEquals(18, m.getInt("themob_compass_marker.layouts.1.pixel.y"));
    assertEquals(90, m.getInt("themob_compass_marker.layouts.2.pixel.y"));
    assertEquals(
        before,
        Files.readString(compass.resolveSibling("themob-compass.yml.before-themob-1.10.2")));
    // CoinOverlay 1.7.4 can apply its older migration again before each build.
    Files.writeString(compass, before);
    CompassHeaderLayout.position(root);
    assertEquals(
        8, YamlConfiguration.loadConfiguration(compass.toFile()).getInt("themob_compass.pixel.y"));
  }

  @Test
  void preservesAdditionalCarrierRowsForOtherHuds() throws Exception {
    Files.writeString(root.resolve("config.yml"), "bossbar-line: 4\nmerge-boss-bar: true\n");
    CompassHeaderLayout.reserve(root);
    var config = YamlConfiguration.loadConfiguration(root.resolve("config.yml").toFile());
    assertEquals(4, config.getInt("bossbar-line"));
    assertEquals(4, config.getInt("bossbar-resource-pack-line"));
  }

  @Test
  void compensatesNativeRowPitchWithoutMovingOtherHudAssetsAndIsIdempotent() throws Exception {
    byte[] original =
        "#version 330\n#define DEFAULT_OFFSET 27\nvoid main(){}\n".getBytes(StandardCharsets.UTF_8);
    Map<String, byte[]> pack = new TreeMap<>();
    pack.put(CompassHeaderLayout.SHADER, original);
    byte[] texture = {1, 2, 3};
    pack.put("texture.png", texture);
    CompassHeaderLayout.correctPack(pack, 2);
    byte[] once = pack.get(CompassHeaderLayout.SHADER);
    assertTrue(new String(once, StandardCharsets.UTF_8).contains("#define DEFAULT_OFFSET 29\n"));
    assertSame(texture, pack.get("texture.png"));
    CompassHeaderLayout.correctPack(pack, 2);
    assertArrayEquals(once, pack.get(CompassHeaderLayout.SHADER));
    assertArrayEquals(
        "#version 330\n#define DEFAULT_OFFSET 27\nvoid main(){}\n".getBytes(StandardCharsets.UTF_8),
        original);
  }

  @Test
  void rejectsMissingOrAmbiguousShaderBeforeMutatingPack() {
    for (String source :
        List.of("#version 330\n", "#define DEFAULT_OFFSET 27\n#define DEFAULT_OFFSET 10\n")) {
      Map<String, byte[]> pack = new TreeMap<>();
      byte[] original = source.getBytes(StandardCharsets.UTF_8);
      pack.put(CompassHeaderLayout.SHADER, original);
      assertThrows(java.io.IOException.class, () -> CompassHeaderLayout.correctPack(pack, 2));
      assertSame(original, pack.get(CompassHeaderLayout.SHADER));
    }
  }
}
