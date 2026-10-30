# Add mobs

Open `/mob` → **Mobs und Bosse**, select a definition and edit or copy it. New or overridden catalog profiles are saved under `mobs/admin/`. A custom file with the same `mob-id` overrides that generated definition after restart or the confirmed hard reload.

Use unique IDs for new profiles, a `base-type` present in the running Minecraft version, finite positive health and valid item materials. The editor exposes real YAML keys; it does not infer the balance of arbitrary custom fields. A basic original [mob template](../examples/mob_template.yml.md) and [boss template](../examples/boss_template.yml.md) are included.

[Complete configuration and examples](../reference/MOBS-UND-ITEMS.md). The detailed operator references currently use German; the public API developer guide is English.
