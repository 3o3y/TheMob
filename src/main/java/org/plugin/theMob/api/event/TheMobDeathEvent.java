package org.plugin.theMob.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.plugin.theMob.api.*;

/** Synchronous TheMob API event. Cleanup/reload does not generate death events. */
public final class TheMobDeathEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final LivingEntity entity;
  private final MobSnapshot mob;
  private final Player killer;

  public TheMobDeathEvent(LivingEntity entity, MobSnapshot mob, Player killer) {
    this.entity = entity;
    this.mob = mob;
    this.killer = killer;
  }

  public LivingEntity getEntity() {
    return entity;
  }

  public MobSnapshot getMob() {
    return mob;
  }

  /** Null for environmental deaths and deaths without a player credited by Bukkit. */
  public Player getKiller() {
    return killer;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
