package org.plugin.theMob.mob;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
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

        // 🔒 Get FINAL damage cause
        if (!(player.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg)) {
            return;
        }

        Entity damager = dmg.getDamager();

        // 🔁 Resolve projectile -> shooter
        if (damager instanceof org.bukkit.entity.Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Entity shooter) {
                damager = shooter;
            }
        }

        if (!(damager instanceof LivingEntity killer)) return;
        if (!mobs.isCustomMob(killer)) return;

        // ❌ Disable vanilla death message
        if (plugin.getConfig().getBoolean("death-messages.disable-vanilla", true)) {
            e.setDeathMessage(null);
        }

        // ✅ Optional custom message
        if (plugin.getConfig().getBoolean("death-messages.custom.enabled", false)) {
            String raw = plugin.getConfig().getString(
                    "death-messages.custom.message",
                    "&c{player} &7was slain by &6{mob}"
            );

            String msg = raw
                    .replace("{player}", player.getName())
                    .replace("{mob}", mobs.baseNameOf(killer));

            Bukkit.broadcastMessage(
                    ChatColor.translateAlternateColorCodes('&', msg)
            );
        }
    }
}
