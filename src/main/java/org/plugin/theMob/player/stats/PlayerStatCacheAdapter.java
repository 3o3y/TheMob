package org.plugin.theMob.player.stats;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.entity.Player;

public final class PlayerStatCacheAdapter implements Function<Player, Map<String, Double>> {
  private final PlayerStatCache cache;

  public PlayerStatCacheAdapter(PlayerStatCache cache) {
    this.cache = cache;
  }

  public Map<String, Double> apply(Player player) {
    return this.cache != null && player != null ? this.cache.get(player) : Collections.emptyMap();
  }
}
