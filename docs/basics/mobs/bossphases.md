# Boss phases

Each configured phase supplies an HP range and its behavior/buffs/actions. Existing descending ranges such as `100-65`, `65-30` and `30-0` remain valid; the admin editor accepts ascending or descending endpoint order without changing the stored range.

A true transition emits `TheMobPhaseChangeEvent`; initial phase assignment is distinct from a transition. BattlePass `themob_phase_enter` credits eligible active combatants for real transitions. The stored phase ID is the objective filter, not a translated display name.

Changing phase configuration requires applying the saved file. Do this outside an active encounter: the hard reload removes active managed entities. Skill switches apply after a full restart. [Zombie King](../../reference/ZOMBIE-KOENIG.md), [BattlePass](../../integrations/battlepass.md).
