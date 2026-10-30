package org.plugin.theMob.player.stats.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class StatsMenuHolder implements InventoryHolder {
  private Inventory inventory;

  public void bind(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
