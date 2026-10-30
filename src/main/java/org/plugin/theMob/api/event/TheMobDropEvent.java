package org.plugin.theMob.api.event;

import org.bukkit.entity.Item;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.plugin.theMob.api.*;

/** Confirmed, uncancelled native item spawn from TheMob death loot. Does not imply collection. */
public final class TheMobDropEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Item entity;
  private final MobSnapshot mob;
  private final LootSource source;
  private final ItemStack item;
  private final ItemSnapshot snapshot;

  public TheMobDropEvent(
      Item entity, MobSnapshot mob, LootSource source, ItemStack item, ItemSnapshot snapshot) {
    this.entity = java.util.Objects.requireNonNull(entity);
    this.mob = java.util.Objects.requireNonNull(mob);
    this.source = java.util.Objects.requireNonNull(source);
    this.item = item.clone();
    this.snapshot = java.util.Objects.requireNonNull(snapshot);
  }

  public Item getEntity() {
    return entity;
  }

  public MobSnapshot getMob() {
    return mob;
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
