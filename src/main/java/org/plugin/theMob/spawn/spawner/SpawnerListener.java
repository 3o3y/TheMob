package org.plugin.theMob.spawn.spawner;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.plugin.theMob.spawn.egg.SpawnEggService;

public final class SpawnerListener implements Listener {
  private final SpawnerService service;
  private final SpawnEggService eggs;

  public SpawnerListener(SpawnerService service, SpawnEggService eggs) {
    this.service = service;
    this.eggs = eggs;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onUseEgg(PlayerInteractEvent event) {
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      ItemStack hand = event.getItem();
      if (hand != null && hand.getType() == Material.EGG) {
        if (this.eggs.isTheMobEgg(hand)) {
          Block target = event.getClickedBlock();
          if (target != null && target.getType() == Material.SPAWNER) {
            if (target.getState() instanceof TileState tile) {
              event.setCancelled(true);
              this.service.applyEggToSpawner(event.getPlayer(), tile, hand);
            }
          }
        }
      }
    }
  }
}
