package org.plugin.theMob.spawn.spawner;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.KeyRegistry;

public final class SpawnerTickService {

    private final TheMob plugin;
    private final SpawnerService service;
    private final KeyRegistry keys;

    public SpawnerTickService(
            TheMob plugin,
            SpawnerService service,
            KeyRegistry keys
    ) {
        this.plugin = plugin;
        this.service = service;
        this.keys = keys;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickAll();
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1s
    }

    private void tickAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof TileState tile)) continue;

                    if (!tile.getPersistentDataContainer().has(
                            keys.THEMOB_SPAWNER,
                            PersistentDataType.BYTE
                    )) continue;

                    service.trySpawn(tile);
                }
            }
        }
    }
}
