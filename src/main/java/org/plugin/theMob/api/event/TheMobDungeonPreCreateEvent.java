package org.plugin.theMob.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.plugin.theMob.api.DungeonRegion;

/**
 * Synchronous veto before reservation, planning or terrain changes. Height-independent footprint.
 */
public final class TheMobDungeonPreCreateEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final org.bukkit.command.CommandSender sender;
  private final DungeonRegion region;
  private final String style;
  private boolean cancelled;

  public TheMobDungeonPreCreateEvent(
      org.bukkit.command.CommandSender sender, DungeonRegion region, String style) {
    this.sender = java.util.Objects.requireNonNull(sender);
    this.region = java.util.Objects.requireNonNull(region);
    this.style = java.util.Objects.requireNonNull(style);
  }

  /** Null for a console-requested resume. */
  public Player getPlayer() {
    return sender instanceof Player p ? p : null;
  }

  public org.bukkit.command.CommandSender getSender() {
    return sender;
  }

  public DungeonRegion getRegion() {
    return region;
  }

  public String getStyle() {
    return style;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
