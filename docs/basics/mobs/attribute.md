# Attributes

Mob base health, attack strength, movement and armor use Bukkit attributes. The compatibility layer maps historical names such as `GENERIC_MAX_HEALTH` to current names. Missing size attributes are skipped on old servers.

Player item health, armor and RPG defense have separate roles. Native armor is not divided by a hardcoded 20 to describe total equipment. Additional defense applies once with diminishing returns. Equipment health bookkeeping preserves current HP, including wounds, across reconnects without repeatedly adding health credit.

[Complete configuration and examples](../../reference/SPIELERWERTE.md). The detailed operator references currently use German; the public API developer guide is English.
