# Physical dungeons

Admin-only generation builds a unique empty crypt, mine, tower, world tree, ten-storey god tower or labyrinth. The connected walkable route is at least 500 blocks; this means path length, not a 500×500 footprint. Current layouts reserve a 113×133-block field.

Creation clears terrain and structures in that reserved field from the foundation to world height. The generated region is persistently protected from normal mining, explosions, fire, pistons, fluids and entity modification. Admins explicitly enable build mode to furnish it; mobs and loot are not automatically populated.

Use `/mob dungeon enter <id>` for multiplayer entry. Assign the designated endboss for completion objectives. Defeating that boss credits eligible present participants once per run. An exit altar in the boss room opens travel options; PKPlugin can supply banner/node/city destinations, while return and entrance remain available without it.

Built-in claim checks can block creation before damage occurs. The public pre-create event supports additional protection systems. PKPlugin integration allows free movement inside a finished dungeon while preventing PK banners/buildings in the reserved region.

[Complete configuration and examples](../reference/DUNGEONS.md). The detailed operator references currently use German; the public API developer guide is English.
