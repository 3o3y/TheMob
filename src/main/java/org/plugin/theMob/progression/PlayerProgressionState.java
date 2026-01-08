package org.plugin.theMob.progression;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerProgressionState {

    private final UUID playerId;
    private final Set<String> equippedItemIds = new HashSet<>();

    public PlayerProgressionState(UUID playerId) {
        this.playerId = playerId;
    }

    public void equip(String itemId) {
        equippedItemIds.add(itemId);
    }

    public void unequip(String itemId) {
        equippedItemIds.remove(itemId);
    }

    public boolean hasItem(String itemId) {
        return equippedItemIds.contains(itemId);
    }

    public Set<String> getEquippedItems() {
        return Set.copyOf(equippedItemIds);
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
