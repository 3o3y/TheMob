package org.plugin.theMob.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class SetBonusEngine {

    private final Map<String, SetBonusDefinition> sets = new HashMap<>();

    public void register(SetBonusDefinition def) {
        sets.put(def.setId(), def);
    }

    public SetBonusDefinition resolve(PlayerProgressionState state) {
        Set<String> equipped = state.getEquippedItems();

        for (SetBonusDefinition def : sets.values()) {
            if (equipped.containsAll(def.requiredItems())) {
                return def;
            }
        }
        return null;
    }
}
