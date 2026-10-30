package org.plugin.theMob.hud.compass;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** The exact public ZIP and hash being offered, independent of freshly generated local builds. */
record PackOffer(
    UUID id, String url, String sha1, String prompt, boolean required, boolean nativeOffer) {
  PackOffer {
    URI address;
    try {
      address = URI.create(url);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "resource-pack.url muss ein direkter HTTP(S)-Download-Link sein.");
    }
    if (!("https".equalsIgnoreCase(address.getScheme())
            || "http".equalsIgnoreCase(address.getScheme()))
        || address.getHost() == null
        || address.getUserInfo() != null
        || address.getFragment() != null
        || !url.equals(address.toASCIIString())) {
      throw new IllegalArgumentException(
          "resource-pack.url muss ein direkter HTTP(S)-Download-Link ohne Login sein.");
    }
    sha1 = sha1.strip().toLowerCase(Locale.ROOT);
    if (!sha1.isEmpty() && !sha1.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException(
          "resource-pack.sha1 muss genau 40 hexadezimale Zeichen enthalten.");
    }
    if (!nativeOffer && sha1.isEmpty()) {
      throw new IllegalArgumentException(
          "resource-pack.sha1 fehlt. Den Wert aus TheMob-Compass-26.3.zip.sha1 eintragen.");
    }
    if (id == null) {
      id =
          UUID.nameUUIDFromBytes(
              ("TheMob:HUD:" + url + ":" + sha1).getBytes(StandardCharsets.UTF_8));
    }
  }

  byte[] hashBytes() {
    return sha1.isEmpty() ? null : HexFormat.of().parseHex(sha1);
  }
}
