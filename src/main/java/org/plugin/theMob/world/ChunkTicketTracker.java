package org.plugin.theMob.world;

import java.util.*;
import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;

public final class ChunkTicketTracker {
  private final Plugin plugin;
  private final Map<Chunk, Integer> references = new HashMap<>();

  public ChunkTicketTracker(Plugin plugin) {
    this.plugin = plugin;
  }

  public void add(Chunk chunk) {
    if (chunk != null && references.merge(chunk, 1, Integer::sum) == 1)
      chunk.addPluginChunkTicket(plugin);
  }

  public void remove(Chunk chunk) {
    if (chunk == null) return;
    Integer count = references.get(chunk);
    if (count == null) return;
    if (count <= 1) {
      references.remove(chunk);
      chunk.removePluginChunkTicket(plugin);
    } else references.put(chunk, count - 1);
  }

  public void releaseAll() {
    for (Chunk chunk : references.keySet()) chunk.removePluginChunkTicket(plugin);
    references.clear();
  }
}
