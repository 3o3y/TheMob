package org.plugin.theMob.core.context;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerBarCoordinator {

    private final Map<UUID, PlayerBars> map = new HashMap<>();

    public PlayerBars of(Player p) {
        return map.computeIfAbsent(p.getUniqueId(), id -> new PlayerBars());
    }
    public void remove(Player p) {
        map.remove(p.getUniqueId());
    }
    public void clearAll() {
        map.clear();
    }
}
