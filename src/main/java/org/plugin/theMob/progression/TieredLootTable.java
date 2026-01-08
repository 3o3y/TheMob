package org.plugin.theMob.progression;

import org.bukkit.Material;

public record TieredLootTable(
        String tier,
        Material material,
        double chance
) {}
