package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ClientPackTest {
  @Test
  void readyClientPacksContainEveryCompassBitmapAndMatchingFonts() throws Exception {
    for (String folder : new String[] {"client-pack", "client-pack-coinoverlay"}) {
      Path root = Path.of(folder);
      var meta =
          JsonParser.parseString(Files.readString(root.resolve("pack.mcmeta"))).getAsJsonObject();
      assertEquals(97, meta.getAsJsonObject("pack").getAsJsonArray("min_format").get(0).getAsInt());
      for (String name :
          new String[] {"compass_themob_compass", "hud_themob_compass_marker_image"}) {
        var font =
            JsonParser.parseString(
                    Files.readString(root.resolve("assets/betterhud/font/" + name + ".json")))
                .getAsJsonObject();
        int bitmaps = 0;
        for (var value : font.getAsJsonArray("providers")) {
          var provider = value.getAsJsonObject();
          if (!provider.get("type").getAsString().equals("bitmap")) continue;
          bitmaps++;
          String[] texture = provider.get("file").getAsString().split(":", 2);
          assertTrue(
              Files.isRegularFile(
                  root.resolve("assets/" + texture[0] + "/textures/" + texture[1])));
        }
        assertEquals(name.startsWith("compass_") ? 100 : 1, bitmaps);
      }
      assertTrue(
          Files.size(root.resolve("betterhud_26_3/assets/minecraft/shaders/core/text.vsh")) > 1000);
    }
  }

  @Test
  void standaloneCompassLeavesVanillaVitalsAndExperienceVisible() throws Exception {
    Path root = Path.of("client-pack");
    assertFalse(Files.exists(root.resolve("assets/minecraft/textures/gui/sprites/hud/heart")));
    assertFalse(
        Files.readString(root.resolve("betterhud_26_3/assets/minecraft/shaders/core/text.vsh"))
            .contains("vec3 exp ="));
    assertArrayEquals(
        Files.readAllBytes(Path.of("src/main/resources/hud/assets/themob/compass-arrow.png")),
        Files.readAllBytes(
            root.resolve("assets/betterhud/textures/image_themob_compass-arrow.png")));
  }
}
