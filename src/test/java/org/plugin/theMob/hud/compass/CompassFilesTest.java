package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import javax.imageio.ImageIO;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompassFilesTest {
  @TempDir Path folder;

  @Test
  void installsCoinOverlayGeometryAndExactGoldArrowWithIndependentNames() throws Exception {
    assertTrue(CompassFiles.install(folder, getClass().getClassLoader()::getResourceAsStream));
    var arrow = ImageIO.read(folder.resolve("assets/themob/compass-arrow.png").toFile());
    assertEquals(9, arrow.getWidth());
    assertEquals(9, arrow.getHeight());
    assertEquals(0, arrow.getRGB(0, 0) >>> 24);
    assertTrue((arrow.getRGB(4, 0) >>> 24) > 0);
    assertEquals(0xFFD700, arrow.getRGB(4, 6) & 0xFFFFFF);
    var config =
        YamlConfiguration.loadConfiguration(
            folder.resolve("compasses/themob-compass.yml").toFile());
    assertEquals(.75, config.getDouble("themob_compass.scale"));
    assertEquals(20, config.getInt("themob_compass.length"));
    assertEquals(50, config.getInt("themob_compass.gui.x"));
    assertEquals(8, config.getInt("themob_compass.pixel.y"));
    assertFalse(config.getBoolean("themob_compass.default"));
    var marker =
        YamlConfiguration.loadConfiguration(folder.resolve("huds/themob-compass.yml").toFile());
    assertEquals(18, marker.getInt("themob_compass_marker.layouts.1.pixel.y"));
    assertEquals(50, marker.getInt("themob_compass_marker.layouts.1.gui.x"));
  }

  @Test
  void repeatedInstallationPreservesCustomLayoutAndOtherHudFiles() throws Exception {
    CompassFiles.install(folder, getClass().getClassLoader()::getResourceAsStream);
    var layout = folder.resolve("huds/themob-compass.yml");
    Files.writeString(layout, "custom: keep-me\n");
    assertFalse(CompassFiles.install(folder, getClass().getClassLoader()::getResourceAsStream));
    assertEquals("custom: keep-me\n", Files.readString(layout));
  }
}
