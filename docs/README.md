# The Mob

**TheMob 1.19.1** brings custom mobs, phased bosses, protected physical dungeons and RPG loot to **Spigot and Paper 1.18–26.3**. The plugin version and Minecraft version are separate numbers. The core is Java 17 bytecode; run the Java version required by your server.

Use **`/mob`** as an administrator to open the complete inventory menu. It needs no resource pack. Existing commands remain available. Create and edit mobs, phases, drops, spawn schedules and dungeons, or manage skills and optional integrations from the same menu. Short text or number inputs use private chat prompts.

The current release includes level 1–5 variants for each supported mob species on your Minecraft version, Rare/Epic enchanted equipment with 1–5 stars, four coordinated Zombie King phases, hot/cold activity management and twenty selected RPG/server integrations. New mobs use TheMob spawns, eggs, spawners, commands or API calls; natural spawns are not replaced.

The public **API 1.3.1** supplies stable Bukkit services, kill/phase/damage/drop/loot events, dungeon completion events, item providers and admin menu extensions. BattlePass supports nine native objective families. Integrations are optional and require provider versions that support the chosen Minecraft server.

Start with the [installation guide](getting-started/quickstart.md), [admin menu](administration/menu.md), [compatibility](getting-started/compatibility.md) or [developer API](developers/public-api.md). Documentation sources, IntelliJ/Gradle project and SDK are included with the release. No public Git or Maven download repository has been created by preparing this release.

The admin menu and text compass work without a pack. The graphical compass uses the shared CoinOverlay resource pack and a matching HUD provider; select assets appropriate for the client version.

This documentation replaces the old 1.7 overview. Automated API and simulated-server results are documented under [validation](reference/VALIDATION.md); native AI, client rendering, live provider combinations and load testing still require a test server.
