# BattlePass objectives

AdvancedPlugins BattlePass supports `themob_kill`, `themob_boss_kill`, `themob_minion_kill`, `themob_objective`, `themob_phase_enter`, `themob_damage`, `themob_drop`, `themob_loot` and `themob_dungeon_complete`.

Kills use eligible native TheMob deaths; removal/reload does not count. Damage is effective HP damage without overkill. Phase goals use actual transitions for active combatants. Drop and loot objectives count item quantities, not stacks; they are separate goals. Dungeon completion requires the assigned endboss, with eligible participants credited and persistent per-run deduplication.

Place desired examples under `quests:` in an **active BattlePass week**, choose nonconflicting IDs, adjust points/requirements and run `/bpa reload`. Shipping examples does not activate a quest. `/mob battlepass` reports registration status; `incompatible` requires resolving the reported provider/API mismatch.

The real BattlePass 5.0.12 classes and native quest progress path are covered by automated API tests. Full database, seasons and live server behavior still need an actual test server. [Combat examples](../examples/battlepass-combat-examples.yml.md).

[Complete configuration and examples](../reference/BATTLEPASS.md). The detailed operator references currently use German; the public API developer guide is English.
