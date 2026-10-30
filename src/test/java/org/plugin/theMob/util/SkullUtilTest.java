package org.plugin.theMob.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SkullUtilTest {
  private String encode(String json) {
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void readsFormattedTextureJsonAndPreservesSkinUrl() {
    var skin =
        SkullUtil.skinUrl(
            encode(
                """
                { "textures": { "SKIN": { "url": "https://textures.minecraft.net/texture/abc123" } } }
                """));
    assertNotNull(skin);
    assertEquals("https://textures.minecraft.net/texture/abc123", skin.toExternalForm());
  }

  @Test
  void invalidTextureInputFallsBackToAnUntexturedHead() {
    for (String value :
        new String[] {
          null,
          "",
          "!invalid",
          encode("{}"),
          encode("{\"textures\":{\"SKIN\":{\"url\":\"https://example.org/a\"}}}")
        }) assertNull(SkullUtil.skinUrl(value));
  }
}
