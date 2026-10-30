package org.plugin.theMob.integration.placeholder;

import java.util.Locale;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.integration.IntegrationManager;

/** Only reads precomputed snapshots/counters, including when PlaceholderAPI runs off-thread. */
public final class TheMobExpansion extends PlaceholderExpansion {
  private final TheMob plugin;
  private final IntegrationManager integrations;

  public TheMobExpansion(TheMob plugin, IntegrationManager integrations) {
    this.plugin = plugin;
    this.integrations = integrations;
  }

  public String getIdentifier() {
    return "themob";
  }

  public String getAuthor() {
    return "3o3y";
  }

  public String getVersion() {
    return plugin.getDescription().getVersion();
  }

  public boolean persist() {
    return true;
  }

  public String onRequest(OfflinePlayer player, String parameter) {
    return integrations.placeholder(
        player == null ? new UUID(0, 0) : player.getUniqueId(), parameter.toLowerCase(Locale.ROOT));
  }
}
