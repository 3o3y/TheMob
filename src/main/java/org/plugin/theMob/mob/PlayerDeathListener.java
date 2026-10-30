package org.plugin.theMob.mob;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.plugin.theMob.TheMob;

public final class PlayerDeathListener implements Listener {
  private final TheMob plugin;
  private final MobManager mobs;

  public PlayerDeathListener(TheMob plugin, MobManager mobs) {
    this.plugin = plugin;
    this.mobs = mobs;
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent e) {
    Player player = e.getEntity();
    if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg) {
      Entity damager = dmg.getDamager();
      if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
        damager = shooter;
      }

      if (damager instanceof LivingEntity killer) {
        if (this.mobs.isCustomMob(killer)) {
          if (this.plugin.getConfig().getBoolean("death-messages.disable-vanilla", true)) {
            e.setDeathMessage(null);
          }

          if (this.plugin.getConfig().getBoolean("death-messages.custom.enabled", false)) {
            String raw =
                this.plugin
                    .getConfig()
                    .getString(
                        "death-messages.custom.message", "&c{player} &7was slain by &6{mob}");
            String msg =
                raw.replace("{player}", player.getName())
                    .replace("{mob}", this.mobs.baseNameOf(killer));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
          }
        }
      }
    }
  }
}
