package org.plugin.theMob.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.plugin.theMob.api.MobSnapshot;

/** Uncancelled player damage to TheMob; effective health damage is capped against overkill. */
public final class TheMobDamageEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final LivingEntity entity;
  private final MobSnapshot mob;
  private final double damage;

  public TheMobDamageEvent(Player player, LivingEntity entity, MobSnapshot mob, double damage) {
    this.player = java.util.Objects.requireNonNull(player);
    this.entity = java.util.Objects.requireNonNull(entity);
    this.mob = java.util.Objects.requireNonNull(mob);
    if (!Double.isFinite(damage) || damage <= 0)
      throw new IllegalArgumentException("Invalid damage");
    this.damage = damage;
  }

  public Player getPlayer() {
    return player;
  }

  public LivingEntity getEntity() {
    return entity;
  }

  public MobSnapshot getMob() {
    return mob;
  }

  public double getDamage() {
    return damage;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
