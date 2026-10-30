package org.plugin.theMob.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.plugin.theMob.api.*;

/** Synchronous TheMob API event. Cleanup/reload does not generate death events. */
public final class TheMobPhaseChangeEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final LivingEntity entity;
  private final MobSnapshot mob;
  private final String previousPhase, nextPhase;
  private final java.util.List<org.bukkit.entity.Player> participants;

  public TheMobPhaseChangeEvent(
      LivingEntity entity, MobSnapshot mob, String previousPhase, String nextPhase) {
    this(entity, mob, previousPhase, nextPhase, java.util.List.of());
  }

  public TheMobPhaseChangeEvent(
      LivingEntity entity,
      MobSnapshot mob,
      String previousPhase,
      String nextPhase,
      java.util.List<org.bukkit.entity.Player> participants) {
    this.entity = entity;
    this.mob = mob;
    this.previousPhase = previousPhase;
    this.nextPhase = nextPhase;
    this.participants = java.util.List.copyOf(participants);
  }

  /** API 1.2: eligible recent combatants still present at this phase change. Empty at spawn. */
  public java.util.List<org.bukkit.entity.Player> getParticipants() {
    return participants;
  }

  public LivingEntity getEntity() {
    return entity;
  }

  public MobSnapshot getMob() {
    return mob;
  }

  /** Null on the initial phase. */
  public String getPreviousPhase() {
    return previousPhase;
  }

  public String getNextPhase() {
    return nextPhase;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
