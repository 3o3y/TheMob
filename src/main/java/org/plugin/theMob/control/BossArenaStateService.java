package org.plugin.theMob.control;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

public final class BossArenaStateService {

    private final AutomationScalingSystem sys;
    private final NamespacedKey mobIdKey;

    public BossArenaStateService(AutomationScalingSystem sys) {
        this.sys = sys;
        this.mobIdKey = new NamespacedKey(sys.plugin(), "mob_id");
    }

    public String stateOf(String bossId, String arenaIgnored) {

        // 1) ALIVE
        for (var uuid : sys.budgets().aliveBossUuids()) {
            Entity e = Bukkit.getEntity(uuid);
            if (e == null) continue;

            String id = e.getPersistentDataContainer()
                    .get(mobIdKey, PersistentDataType.STRING);

            if (bossId.equals(id)) {
                return ChatColor.GREEN + "ALIVE";
            }
        }

        // 2) COOLDOWN
        long sec = sys.bossCooldowns().secondsRemaining(bossId);
        if (sec > 0) {
            return ChatColor.RED.toString() + sec + "s";
        }

        // 3) NONE
        return ChatColor.GRAY + "NONE";
    }
}
