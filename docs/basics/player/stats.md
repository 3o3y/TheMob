# Player stat rules

Vanilla damage remains the base. TheMob damage/extra_damage, critical hits, lifesteal and defense each apply in their designated step; they are not duplicated because another stat menu displays them. Armor, health, absorption and hunger are distinct values.

Equipment modifiers are replaced by their own stable identity. Removing them at logout records only the HP lost to the lowered maximum; login restores that credit after reapplying equipment, clamps to the actual maximum and consumes it once. Hunger remains 0–20. The displayed armor scale belongs to CoinOverlay and must match its current HUD.

[Complete configuration and examples](../../reference/SPIELERWERTE.md). The detailed operator references currently use German; the public API developer guide is English.
