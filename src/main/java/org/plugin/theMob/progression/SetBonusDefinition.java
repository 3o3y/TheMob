package org.plugin.theMob.progression;

import java.util.Set;

public record SetBonusDefinition(
        String setId,
        Set<String> requiredItems,
        double damageMultiplier,
        double defenseMultiplier
) {}
