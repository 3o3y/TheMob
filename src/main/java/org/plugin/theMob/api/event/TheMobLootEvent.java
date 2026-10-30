package org.plugin.theMob.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.plugin.theMob.api.*;

/** Uncancelled native player collection of provenance-tagged loot, including partial pickups. */
public final class TheMobLootEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final LootSource source;
  private final ItemStack item;
  private final ItemSnapshot snapshot;

  public TheMobLootEvent(Player player, LootSource source, ItemStack item, ItemSnapshot snapshot) {
    this.player = java.util.Objects.requireNonNull(player);
    this.source = java.util.Objects.requireNonNull(source);
    this.item = item.clone();
    this.snapshot = java.util.Objects.requireNonNull(snapshot);
  }

  public Player getPlayer() {
    return player;
  }

  public LootSource getSource() {
    return source;
  }

  public ItemStack getItem() {
    return item.clone();
  }

  public ItemSnapshot getItemSnapshot() {
    return snapshot;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
