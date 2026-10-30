package org.plugin.theMob.api.dungeon;

import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.plugin.theMob.api.DungeonRegion;

/** Successful, durably recorded completion. Entering alone never fires this event. */
public final class DungeonCompletedEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final DungeonRegion dungeon;
  private final UUID runId;
  private final List<Player> participants;
  private final String bossMobId;
  private final String style, worldName;
  private final int levels;

  public DungeonCompletedEvent(
      DungeonRegion dungeon,
      UUID runId,
      List<Player> participants,
      String bossMobId,
      String style,
      String worldName,
      int levels) {
    this.dungeon = Objects.requireNonNull(dungeon);
    this.runId = Objects.requireNonNull(runId);
    this.participants = List.copyOf(participants);
    this.bossMobId = Objects.requireNonNull(bossMobId);
    this.style = Objects.requireNonNull(style);
    this.worldName = Objects.requireNonNull(worldName);
    this.levels = levels;
  }

  public DungeonRegion getDungeon() {
    return dungeon;
  }

  public UUID getRunId() {
    return runId;
  }

  public List<Player> getParticipants() {
    return participants;
  }

  public String getBossMobId() {
    return bossMobId;
  }

  public String getStyle() {
    return style;
  }

  public String getWorldName() {
    return worldName;
  }

  public int getLevels() {
    return levels;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
