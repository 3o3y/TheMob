# Switchable skills and item effects

TheMob's own skills are configurable independently from your SkilltreePlugin. `/mob` → **Skills** exposes the global switch, boss switches and item-effect switches. Existing `/mob skills` commands remain. Saved activation changes apply after a full restart.

Boss options cover coordinated Zombie King attacks, tactical modes, phase abilities/buffs/combat/effects, world effects and minion summons. Item effects include critical hits, lifesteal, poison, ignite, slow, execute and knockback. A disabled parent disables its children even if an individual switch remains true.

SkilltreePlugin 2.0.0 can keep its own XP/skill UI and modifiers. Other supported skill providers receive only explicitly configured bonus XP; no duplicate automatic progression is assumed. [Default switches](../examples/skills.yml.md).

[Complete configuration and examples](../reference/SKILLS.md). The detailed operator references currently use German; the public API developer guide is English.
