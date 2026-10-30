package org.plugin.theMob.hud;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class NaviHudListener implements Listener {
  private final NaviHudService hud;

  public NaviHudListener(NaviHudService hud) {
    this.hud = hud;
  }

  @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombat(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
    if (e.getEntity() instanceof org.bukkit.entity.Player player) this.hud.markCombat(player);
    if (e.getDamager() instanceof org.bukkit.entity.Player player) this.hud.markCombat(player);
    if (e.getDamager() instanceof org.bukkit.entity.Projectile projectile
        && projectile.getShooter() instanceof org.bukkit.entity.Player player)
      this.hud.markCombat(player);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    this.hud.onJoin(e.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    this.hud.onQuit(e.getPlayer());
  }
}
