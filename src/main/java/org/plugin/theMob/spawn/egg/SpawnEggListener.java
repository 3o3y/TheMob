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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEggImpact(ProjectileHitEvent event) {

        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!(egg.getShooter() instanceof Player player)) return;

        ItemStack usedEgg = egg.getItem();
        if (usedEgg == null || !usedEgg.hasItemMeta()) return;

        if (!service.isTheMobEgg(usedEgg)) return;

        event.setCancelled(true);

        Location spawnLoc = egg.getLocation()
                .getBlock()
                .getLocation()
                .add(0.5, 0.01, 0.5);

        service.spawnFromThrownEgg(player, usedEgg, spawnLoc);
        egg.remove();
    }
}
