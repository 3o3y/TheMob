# Items, rarity and drops

The catalog contains enchanted Rare and Epic equipment with 1–5 stars, persistent IDs and itemstats. Minecraft 26.3 has 160 variants; unavailable newer materials are omitted on old servers. `/mob items` shows the actual catalog.

Drop chances are fractions from 0 to 1. Native item IDs, level-bound essences and provider items can be used in custom drops. Provider drops retain their original name, enchantments and inventory PDC; their provenance is tracked on the dropped entity for objectives.

`themob_drop` counts newly generated quantities from an eligible kill; `themob_loot` counts quantities actually picked up. Re-dropping player inventory does not create new TheMob kill loot. [Provider items](../../integrations/overview.md), [BattlePass filters](../../integrations/battlepass.md).

[Complete configuration and examples](../../reference/MOBS-UND-ITEMS.md). The detailed operator references currently use German; the public API developer guide is English.
