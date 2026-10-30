package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackOfferTest {
  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

  @Test
  void usesAStableIdAndTwentyByteHashForThePublishedZip() {
    PackOffer first =
        new PackOffer(
            null, "https://example.org/hud.zip", HASH.toUpperCase(), "Laden?", false, false);
    PackOffer same =
        new PackOffer(null, "https://example.org/hud.zip", HASH, "Anderer Text", true, false);
    assertEquals(first.id(), same.id());
    assertEquals(HASH, first.sha1());
    assertEquals(20, first.hashBytes().length);
    assertNotEquals(
        first.id(), new PackOffer(null, first.url(), "f".repeat(40), "", false, false).id());
  }

  @Test
  void keepsTheNativeServerPackIdForMatchingClientResponses() {
    UUID nativeId = UUID.randomUUID();
    PackOffer nativePack =
        new PackOffer(nativeId, "https://example.org/hud.zip", "", "", false, true);
    assertEquals(nativeId, nativePack.id());
    assertNull(nativePack.hashBytes());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "file:///server/hud.zip",
        "ftp://example.org/hud.zip",
        "https:///hud.zip",
        "https://user:pass@example.org/hud.zip",
        "https://example.org/hud.zip#fragment",
        "https://example.org/a b.zip",
        "https://example.org/Münze.zip"
      })
  void rejectsUrlsThatCannotBeUsedAsPublicMinecraftDownloads(String url) {
    assertThrows(
        IllegalArgumentException.class, () -> new PackOffer(null, url, HASH, "", false, false));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "abc",
        "0123456789abcdef0123456789abcdef0123456789ab",
        "g0123456789abcdef0123456789abcdef01234567"
      })
  void requiresTheExactPublishedSha1ForPluginOffers(String hash) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PackOffer(null, "https://example.org/hud.zip", hash, "", false, false));
  }
}
