package org.plugin.theMob.api.event;

import org.bukkit.Location;
import org.bukkit.event.*;
import org.plugin.theMob.api.*;

/** Synchronous TheMob API event. Cleanup/reload does not generate death events. */
public final class TheMobPreSpawnEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final MobDefinition definition;
  private final Location location;
  private final SpawnReason reason;
  private boolean cancelled;

  public TheMobPreSpawnEvent(MobDefinition definition, Location location, SpawnReason reason) {
    this.definition = definition;
    this.location = location.clone();
    this.reason = reason;
  }

  public MobDefinition getDefinition() {
    return definition;
  }

  /** Resolved spawn location, including the configured safe-ground offset. Returned as a copy. */
  public Location getLocation() {
    return location.clone();
  }

  public SpawnReason getReason() {
    return reason;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
