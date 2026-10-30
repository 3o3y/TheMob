package org.plugin.theMob.progression;

public final class ProgressionCombatApplier {
  private final SetBonusEngine setBonuses;
  private final ItemSynergyResolver synergies;

  public ProgressionCombatApplier(SetBonusEngine setBonuses, ItemSynergyResolver synergies) {
    this.setBonuses = setBonuses;
    this.synergies = synergies;
  }

  public double applyDamage(PlayerProgressionState state, double baseDamage) {
    double damage = baseDamage;
    SetBonusDefinition set = this.setBonuses.resolve(state);
    if (set != null) {
      damage = baseDamage * set.damageMultiplier();
    }

    return damage;
  }
}
