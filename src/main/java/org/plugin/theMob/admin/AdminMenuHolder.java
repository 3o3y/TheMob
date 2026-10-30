package org.plugin.theMob.admin;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

/** Actions are server-owned; GUI items never encode executable commands or authorization. */
public final class AdminMenuHolder implements InventoryHolder {
  final UUID owner, generation;
  final Map<Integer, Consumer<Player>> actions = new HashMap<>();
  private Inventory inventory;

  AdminMenuHolder(UUID owner, UUID generation) {
    this.owner = owner;
    this.generation = generation;
  }

  void bind(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
