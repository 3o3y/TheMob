# Radar HUD

Radar and boss displays track eligible nearby entities. TheMob excludes Cosmetics-marked visual/pet entities and its own temporary display actors from radar and health bars. This exclusion does not prevent configured mobs and bosses from fighting Pets as real combat targets.

Chunk enumeration is shared inside each radar pass. Visibility, distance and activity settings still apply; radar is not an unlimited global entity scan. The compass has a personal text fallback when a graphical pack is unavailable.

[Complete configuration and examples](../reference/KOMPASS.md). The detailed operator references currently use German; the public API developer guide is English.
