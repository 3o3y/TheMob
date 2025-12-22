package org.plugin.theMob.player.stats.menu;

import org.bukkit.Material;

public record StatsDefinition(
        String key,
        String name,
        int slot,
        Material icon
) {}
