package org.plugin.theMob.progression;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class EquipListener implements Listener {

    private final CustomItemFactory items;
    private final PlayerProgressionManager states;

    public EquipListener(CustomItemFactory items, PlayerProgressionManager states) {
        this.items = items;
        this.states = states;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEquip(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        PlayerProgressionState state = states.get(player.getUniqueId());

        if (current != null) {
            String id = items.getItemId(current);
            if (id != null) state.unequip(id);
        }

        if (cursor != null) {
            String id = items.getItemId(cursor);
            if (id != null) state.equip(id);
        }
    }
}
