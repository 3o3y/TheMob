package org.plugin.theMob.progression;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TieredLootTableService {

    private final List<TieredLootTable> tables = new ArrayList<>();
    private final Random random = new Random();

    public void register(TieredLootTable table) {
        tables.add(table);
    }

    public Material roll(String tier) {
        for (TieredLootTable table : tables) {
            if (!table.tier().equalsIgnoreCase(tier)) continue;
            if (random.nextDouble() <= table.chance()) {
                return table.material();
            }
        }
        return null;
    }
}
