package org.plugin.theMob.spawn.spawner;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
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

    public SpawnerListener(
            SpawnerService service,
            SpawnEggService eggs
    ) {
        this.service = service;
        this.eggs = eggs;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseEgg(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = event.getItem();
        if (hand == null || hand.getType() != Material.EGG) return;
        if (!eggs.isTheMobEgg(hand)) return;

        Block target = event.getClickedBlock();
        if (target == null || target.getType() != Material.SPAWNER) return;
        if (!(target.getState() instanceof TileState tile)) return;

        event.setCancelled(true);
        service.applyEggToSpawner(event.getPlayer(), tile, hand);
    }
}
