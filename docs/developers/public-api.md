# Public API 1.3.1

Use `org.plugin.theMob.api.TheMobApi` and `org.plugin.theMob.api.dungeon.TheMobDungeonApi`, acquired from Bukkit `ServicesManager`. The compatibility major remains `apiVersion() == 1`; artifact 1.3.1 contains Java 17 bytecode and preserves earlier public 1.x method/constructor descriptors.

Declare `depend: [TheMob]`; use the API only on the server thread. Compile against the two SDK JARs with `compileOnly`/`provided`, and never shade them into an add-on. The running TheMob plugin provides the shared types. SDK includes source/JavaDoc JARs, a local Maven repository, scoped API license and a standalone Gradle example.

Core events cover pre-spawn (cancellable), spawn, death, phase change, effective damage, generated drops and actual loot pickup. Dungeon API supplies immutable region snapshots, completion events and travel providers. `TheMobDungeonPreCreateEvent` can veto construction. `AdminMenuExtension` and `TheMobItemProvider` register through Bukkit services.

Query definitions and active mob snapshots, spawn through the normal checks, create/inspect supported items and remove mobs through the public service. Administrative removal does not fake a death. Use run IDs for idempotent dungeon consumers. Do not count both public and raw Bukkit death events for the same objective.

[Complete configuration and examples](../reference/DEVELOPER-GUIDE.md). The detailed operator references currently use German; the public API developer guide is English.
