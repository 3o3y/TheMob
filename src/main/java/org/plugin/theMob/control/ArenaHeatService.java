package org.plugin.theMob.control;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaHeatService {
  private final Map<String, Long> hotUntil = new ConcurrentHashMap<>();

  public void markHot(String arenaId, long seconds) {
    this.hotUntil.put(arenaId, System.currentTimeMillis() + seconds * 1000L);
  }

  public boolean isHot(String arenaId) {
    Long until = this.hotUntil.get(arenaId);
    if (until == null) {
      return false;
    } else if (System.currentTimeMillis() >= until) {
      this.hotUntil.remove(arenaId);
      return false;
    } else {
      return true;
    }
  }
}
