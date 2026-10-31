package org.plugin.theMob.item.loot;

import org.bukkit.Material;

public record TieredLootTable(String tier, Material material, double chance) {}
