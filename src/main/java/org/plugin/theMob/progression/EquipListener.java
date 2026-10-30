package org.plugin.theMob.progression;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class EquipListener implements Listener {
  public EquipListener(Plugin plugin, CustomItemFactory items, PlayerProgressionManager states) {
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            () -> {
              for (Player player : Bukkit.getOnlinePlayers()) {
                var state = states.get(player.getUniqueId());
                for (String id : state.getEquippedItems()) state.unequip(id);
                for (ItemStack item : player.getInventory().getArmorContents()) {
                  String id = items.getItemId(item);
                  if (id != null) state.equip(id);
                }
                for (ItemStack item :
                    new ItemStack[] {
                      player.getInventory().getItemInMainHand(),
                      player.getInventory().getItemInOffHand()
                    }) {
                  String id = items.getItemId(item);
                  if (id != null) state.equip(id);
                }
              }
            },
            1,
            10);
  }
}
