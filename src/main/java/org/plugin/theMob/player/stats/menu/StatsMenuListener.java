package org.plugin.theMob.player.stats.menu;

import org.bukkit.event.*;
import org.bukkit.event.inventory.*;

public final class StatsMenuListener implements Listener {
  private final org.plugin.theMob.TheMob plugin;

  public StatsMenuListener(org.plugin.theMob.TheMob plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onClick(InventoryClickEvent e) {
    if (org.plugin.theMob.compat.Views.top(e.getView()).getHolder() instanceof StatsMenuHolder) {
      e.setCancelled(true);
      if (e.getRawSlot() < 0
          || e.getRawSlot() >= org.plugin.theMob.compat.Views.top(e.getView()).getSize()
          || !(e.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
      var item = e.getCurrentItem();
      if (item == null
          || !item.hasItemMeta()
          || !item.getItemMeta()
              .getPersistentDataContainer()
              .has(
                  new org.bukkit.NamespacedKey(plugin, "open_skilltree"),
                  org.bukkit.persistence.PersistentDataType.BYTE)) return;
      org.bukkit.Bukkit.getScheduler()
          .runTask(
              plugin,
              () -> {
                var provider = org.bukkit.Bukkit.getPluginManager().getPlugin("SkilltreePlugin");
                if (!player.isOnline()
                    || !player.hasPermission("skilltree.use")
                    || provider == null
                    || !provider.isEnabled()
                    || !(org.plugin.theMob.compat.Views.top(player.getOpenInventory()).getHolder()
                        instanceof StatsMenuHolder)) return;
                player.closeInventory();
                player.performCommand("skilltreeplugin:skills");
              });
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent e) {
    if (org.plugin.theMob.compat.Views.top(e.getView()).getHolder() instanceof StatsMenuHolder)
      e.setCancelled(true);
  }
}
