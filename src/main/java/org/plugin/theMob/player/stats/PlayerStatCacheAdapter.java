package org.plugin.theMob.player.stats;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Function;

public final class PlayerStatCacheAdapter implements Function<Player, Map<String, Double>> {

    private final PlayerStatCache cache;
    public PlayerStatCacheAdapter(PlayerStatCache cache) {
        this.cache = cache;
    }
    @Override
    public Map<String, Double> apply(Player player) {
        if (cache == null || player == null) return java.util.Collections.emptyMap();
        return cache.get(player); // <- falls deine Methode anders heißt: HIER anpassen
    }
}
