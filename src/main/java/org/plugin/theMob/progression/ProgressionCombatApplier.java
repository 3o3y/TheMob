package org.plugin.theMob.progression;

import org.bukkit.entity.Player;

public final class ProgressionCombatApplier {

    private final SetBonusEngine setBonuses;
    private final ItemSynergyResolver synergies;

    public ProgressionCombatApplier(SetBonusEngine setBonuses, ItemSynergyResolver synergies) {
        this.setBonuses = setBonuses;
        this.synergies = synergies;
    }

    public double applyDamage(PlayerProgressionState state, double baseDamage) {
        double damage = baseDamage;

        SetBonusDefinition set = setBonuses.resolve(state);
        if (set != null) {
            damage *= set.damageMultiplier();
        }

        for (ItemSynergyDefinition synergy : synergies.resolve(state)) {
            damage += damage * (synergy.lifestealBonus() / 100.0);
        }

        return damage;
    }
}
