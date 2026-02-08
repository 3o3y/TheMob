package org.plugin.theMob.spawn.spawner;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class SpawnerFactory {

    private final KeyRegistry keys;

    public SpawnerFactory(KeyRegistry keys) {
        this.keys = keys;
    }

    public boolean markAsTheMobSpawner(Block block) {
        if (block.getType() != Material.SPAWNER) return false;
        if (!(block.getState() instanceof TileState tile)) return false;

        tile.getPersistentDataContainer().set(
                keys.THEMOB_SPAWNER,
                PersistentDataType.BYTE,
                (byte) 1
        );
        tile.update(true);
        return true;
    }
}
