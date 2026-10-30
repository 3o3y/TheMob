package org.plugin.theMob.spawn.egg;

import org.bukkit.Location;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

public final class SpawnEggListener implements Listener {
  private final SpawnEggService service;

  public SpawnEggListener(SpawnEggService service) {
    this.service = service;
  }

  @EventHandler
  public void onThrow(org.bukkit.event.player.PlayerEggThrowEvent e) {
    if (this.service.isTheMobEgg(e.getEgg().getItem())) e.setHatching(false);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onEggImpact(ProjectileHitEvent event) {
    if (event.getEntity() instanceof Egg egg) {
      if (egg.getShooter() instanceof Player player) {
        ItemStack usedEgg = egg.getItem();
        if (usedEgg != null && usedEgg.hasItemMeta()) {
          if (this.service.isTheMobEgg(usedEgg)) {
            event.setCancelled(true);
            Location spawnLoc = egg.getLocation().getBlock().getLocation().add(0.5, 0.01, 0.5);
            if (!this.service.spawnFromThrownEgg(player, usedEgg, spawnLoc)) {
              ItemStack refund = usedEgg.clone();
              refund.setAmount(1);
              for (ItemStack leftover : player.getInventory().addItem(refund).values())
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
              player.sendMessage("§eSpawn blocked; your egg was returned.");
            }
            egg.remove();
          }
        }
      }
    }
  }
}
