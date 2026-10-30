package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/** Normal CUSTOM healing pipeline, allowing Skilltree healing modifiers and server vetoes. */
public final class PlayerHealing {
  private PlayerHealing() {}

  public static double heal(Player player, double amount) {
    if (player == null || player.isDead() || !Double.isFinite(amount) || amount <= 0) return 0;
    var max = org.plugin.theMob.compat.BukkitTypes.instance(player, "MAX_HEALTH");
    if (max == null || player.getHealth() >= max.getValue()) return 0;
    var event =
        new EntityRegainHealthEvent(
            player,
            Math.min(amount, max.getValue() - player.getHealth()),
            EntityRegainHealthEvent.RegainReason.CUSTOM);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()
        || player.isDead()
        || !Double.isFinite(event.getAmount())
        || event.getAmount() <= 0) return 0;
    double before = player.getHealth();
    player.setHealth(Math.min(max.getValue(), before + event.getAmount()));
    return Math.max(0, player.getHealth() - before);
  }
}
