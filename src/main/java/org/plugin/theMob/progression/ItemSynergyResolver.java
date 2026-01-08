package org.plugin.theMob.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ItemSynergyResolver {

    private final List<ItemSynergyDefinition> synergies = new ArrayList<>();

    public void register(ItemSynergyDefinition def) {
        synergies.add(def);
    }

    public List<ItemSynergyDefinition> resolve(PlayerProgressionState state) {
        Set<String> equipped = state.getEquippedItems();
        List<ItemSynergyDefinition> active = new ArrayList<>();

        for (ItemSynergyDefinition def : synergies) {
            if (equipped.containsAll(def.requiredItems())) {
                active.add(def);
            }
        }
        return active;
    }
}
