# config.yml

The main file is `plugins/TheMob/config.yml`. Use `/mob` → **Alle Einstellungen** to inspect and edit nested values, or edit YAML while the server is stopped. The menu preserves unknown custom keys and validates its saved draft.

The global configuration controls spawn budgets, TPS throttling, combat, diagnostics, HUD behavior and optional integrations. `catalog.yml`, `equipment.yml`, `abilities.yml`, `battlepass.yml`, `dungeon.yml` and `zombie-boss.yml` cover their individual systems. Mob definitions remain under `mobs/`.

Integration switches use `integrations.<provider>.enabled`. Do not assume all providers are required. `/mob connect check` reports detected, ready, disabled or incompatible connections. Restart after changing integration or skill activation.

The release contains a [complete default config](../examples/config.yml.md) and [skill switches](../examples/abilities.yml.md). Existing installations keep their current files; defaults in the release are reference material, not an instruction to replace tuned production settings. [Performance](perfomance.md), [integrations](../integrations/overview.md).
