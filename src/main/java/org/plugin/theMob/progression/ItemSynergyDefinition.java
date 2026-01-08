package org.plugin.theMob.progression;

import java.util.Set;

public record ItemSynergyDefinition(
        String synergyId,
        Set<String> requiredItems,
        double lifestealBonus
) {}
