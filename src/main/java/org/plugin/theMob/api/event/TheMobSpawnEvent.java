package org.plugin.theMob.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.plugin.theMob.api.*;

/** Synchronous TheMob API event. Cleanup/reload does not generate death events. */
public final class TheMobSpawnEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final LivingEntity entity;
  private final MobSnapshot mob;

  public TheMobSpawnEvent(LivingEntity entity, MobSnapshot mob) {
    this.entity = entity;
    this.mob = mob;
  }

  public LivingEntity getEntity() {
    return entity;
  }

  public MobSnapshot getMob() {
    return mob;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
