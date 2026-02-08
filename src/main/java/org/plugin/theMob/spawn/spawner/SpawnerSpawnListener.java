package org.plugin.theMob.spawn.spawner;

import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class SpawnerSpawnListener implements Listener {

    private final KeyRegistry keys;

    public SpawnerSpawnListener(KeyRegistry keys) {
        this.keys = keys;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!(event.getSpawner().getBlock().getState() instanceof TileState tile)) return;

        if (tile.getPersistentDataContainer().has(
                keys.THEMOB_SPAWNER,
                PersistentDataType.BYTE
        )) {
            event.setCancelled(true);
        }
    }
}
