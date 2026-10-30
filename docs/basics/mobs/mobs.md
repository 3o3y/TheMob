# Mob definitions

A custom definition has a stable `mob-id`, a supported `base-type`, display settings, attributes, optional equipment, itemstats and drop rules. Boss definitions also specify `boss` and phase sections. Files live in `plugins/TheMob/mobs`; own definitions override matching generated IDs.

Use the [original basic template](../../examples/mob_template.yml.md) or [boss template](../../examples/boss_template.yml.md) as the complete schema reference. Their original content is preserved. The admin tree editor can edit all sections and list entries; it uses the actual YAML keys.

Item bonuses and mob base attributes are distinct. Drop `chance` values use 0–1; `0.05` means 5%. Use a valid material/entity from the running Minecraft version. Newer vanilla types cannot be spawned on old servers.

[Add mobs](../add-mobs.md), [items](item.md), [phases](bossphases.md), [full catalogue/balance](../../reference/MOBS-UND-ITEMS.md).
