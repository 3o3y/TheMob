package org.plugin.theMob.progression;

public record BossDifficultyProfile(
        String id,
        double healthMultiplier,
        double damageMultiplier,
        double lootMultiplier
) {}
