package org.plugin.theMob.player.stats.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class StatsMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("Your Stats")) {
            e.setCancelled(true);
        }
    }
}
