package org.plugin.theMob.hud.compass;

import java.util.Map;
import java.util.UUID;

/** Tracks only the wallet pack; other plugins' resource-pack responses never unlock the HUD. */
final class PackReadiness {
  enum Phase {
    REQUESTED,
    ACCEPTED,
    DOWNLOADED,
    LOADED,
    DECLINED,
    FAILED
  }

  private record Entry(UUID pack, Phase phase) {}

  private final Map<UUID, Entry> players = new java.util.concurrent.ConcurrentHashMap<>();

  void expect(UUID player, UUID pack, boolean preserveMatching) {
    Entry current = players.get(player);
    if (!preserveMatching || current == null || !current.pack().equals(pack)) {
      players.put(player, new Entry(pack, Phase.REQUESTED));
    }
  }

  boolean update(UUID player, UUID pack, Phase phase) {
    Entry current = players.get(player);
    if (current == null || !current.pack().equals(pack)) {
      return false;
    }
    players.put(player, new Entry(pack, phase));
    return true;
  }

  boolean loaded(UUID player) {
    Entry entry = players.get(player);
    return entry != null && entry.phase() == Phase.LOADED;
  }

  void forget(UUID player) {
    players.remove(player);
  }
}
