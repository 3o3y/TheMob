package org.plugin.theMob.world;

import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ChunkTicketTracker {

    private final Plugin plugin;
    private final Set<Chunk> tracked = Collections.synchronizedSet(new HashSet<>());

    public ChunkTicketTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void add(Chunk chunk) {
        if (chunk == null) return;
        chunk.addPluginChunkTicket(plugin);
        tracked.add(chunk);
    }

    public void remove(Chunk chunk) {
        if (chunk == null) return;
        chunk.removePluginChunkTicket(plugin);
        tracked.remove(chunk);
    }

    public void releaseAll() {
        for (Chunk c : tracked) {
            c.removePluginChunkTicket(plugin);
        }
        tracked.clear();
    }
}
