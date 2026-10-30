package org.plugin.theMob.control;

import java.util.UUID;
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
    for (UUID uuid : this.sys.budgets().aliveBossUuids()) {
      Entity e = Bukkit.getEntity(uuid);
      if (e != null) {
        String id =
            (String) e.getPersistentDataContainer().get(this.mobIdKey, PersistentDataType.STRING);
        if (bossId.equals(id)) {
          return ChatColor.GREEN + "ALIVE";
        }
      }
    }

    long sec = this.sys.bossCooldowns().secondsRemaining(bossId);
    return sec > 0L ? ChatColor.RED.toString() + sec + "s" : ChatColor.GRAY + "NONE";
  }
}
