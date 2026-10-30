# Installation guide

1. Stop the server and back up the world and `plugins/TheMob`.
2. Install `TheMob-1.19.0-MC1.18-26.3.jar` in `plugins`. Remove the previous TheMob JAR so only one copy loads. API, source and JavaDoc JARs are development files and are not server plugins.
3. Use a Java runtime appropriate for the Minecraft distribution: recommended Java 17 for 1.18–1.19.4, Java 21 for 1.20–1.21.11, Java 25 for 26.1–26.3. See [compatibility](compatibility.md).
4. Start the server. As OP, run `/mob` to open administration. Select **Mobs und Bosse** to test a mob or **Plugin-Anbindungen** to inspect optional providers. `/mob connect check` remains available.
5. Change settings in a personal menu draft, save, then restart when applying skills or integration switches. The confirmed hard reload removes active TheMob mobs and discards menu drafts; it grants no kill progress.

Keep existing data when upgrading. Built-in additions are installed when missing. Use provider versions that match your server; the 26.3 BetterHud compile library in the source project is not a universal runtime dependency. TheMob also starts without BetterHud or PlaceholderAPI.

For a first test, use `/mob spawn tm_zombie_lvl_1`, or choose the equivalent menu action. Use `/mob items` to inspect native item IDs. Do not generate a dungeon near a build you want to keep: creation clears its entire reserved footprint up to world height after the admin confirmation.

The plugin does not downgrade Minecraft worlds or newer serialized items. The shared CoinOverlay client pack must also match the client version. [Update details](../reference/UPDATE.md).
