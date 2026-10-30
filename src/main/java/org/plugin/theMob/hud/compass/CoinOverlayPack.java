package org.plugin.theMob.hud.compass;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

/** The wallet owns the one shared BetterHud pack, including TheMob's compass resources. */
public final class CoinOverlayPack {
  private CoinOverlayPack() {}

  static boolean active(Plugin plugin) {
    return plugin != null
        && plugin.isEnabled()
        && "RESOURCE_HUD"
            .equalsIgnoreCase(plugin.getConfig().getString("overlay.mode", "RESOURCE_HUD"));
  }

  static PackOffer offer(String url, String hash, String prompt, boolean required) {
    // Match CoinOverlay's published-pack identity exactly; do not offer a second pack.
    PackOffer validated = new PackOffer(null, url, hash, prompt, required, false);
    UUID id =
        UUID.nameUUIDFromBytes(
            ("CoinOverlay:HUD:" + validated.url() + ":" + validated.sha1())
                .getBytes(StandardCharsets.UTF_8));
    return new PackOffer(id, validated.url(), validated.sha1(), prompt, required, false);
  }
}
