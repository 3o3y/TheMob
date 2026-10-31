# TheMob Developer SDK 1.19.1

Public Bukkit API **1.3.1**, implemented by TheMob **1.19.1** for **Minecraft 1.18–26.3 (Java 17 bytecode)**. Use it to connect your quest objectives, Jobs adapters, encounter systems, protection plugins and dungeon travel providers. The API and examples are released under the scoped MIT license in `API-LICENSE.txt`; this does not relicense the complete plugin or third-party dependencies.

This is a redistributable SDK archive. It does not imply publication to Maven Central, GitHub or a public download host.

## SDK layout

- `api/`: core and dungeon binary JARs, source JARs and JavaDoc JARs.
- `javadoc/index.html`: browsable documentation for both public packages.
- `maven-repository/`: a local Maven-layout repository with POMs and SHA-1/SHA-256/SHA-512 checksums.
- `example/`: a complete Gradle wrapper project; both compile-only API libraries are already included in `libraries/`.
- `test-addon/`: the compiled sample, for explicit installation on a test server only.
- `ADMIN-VERBINDUNGEN.md`: German operator setup, permissions and troubleshooting.

The public namespaces are `org.plugin.theMob.api` and `org.plugin.theMob.api.dungeon`. `api.internal`, `integration`, `mob` and other implementation packages are outside the supported contract. Do not use reflection into TheMob internals or read its private PDC keys.

## Build the example

Extract the entire SDK, open `example/` in IntelliJ with JDK 25, then run:

```powershell
.\gradlew.bat build
```

On Linux/macOS, use `sh gradlew build`. The first build needs access to Gradle/Maven repositories unless the dependencies are already cached. The resulting `build/libs/TheMobAddonExample-1.0.0.jar` contains only the add-on. Install TheMob and this example on a test server, then use `/themobexample status` as OP. The explicit `/themobexample spawn pig_lvl_1` test obeys TheMob's normal spawn checks. The example only records its own demonstration counters and never grants Jobs payments, quest progress or BattlePass rewards.

## Add dependencies to your plugin

Gradle Kotlin DSL, using the two binary JARs from `api/`:

```kotlin
repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}
dependencies {
    compileOnly(files("libraries/TheMob-API-1.3.1.jar"))
    compileOnly(files("libraries/TheMob-Dungeon-API-1.3.1.jar")) // only if used
    compileOnly("org.spigotmc:spigot-api:1.18-R0.1-SNAPSHOT")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
```

Alternatively point your Maven/Gradle repository configuration at the extracted `maven-repository/` directory. Coordinates are **`org.plugin.themob:themob-api:1.3.1`** and **`org.plugin.themob:themob-dungeon-api:1.3.1`**. Maven dependencies use `<scope>provided</scope>`; Gradle uses `compileOnly`. Example Maven dependency:

```xml
<dependency>
  <groupId>org.plugin.themob</groupId>
  <artifactId>themob-api</artifactId>
  <version>1.3.1</version>
  <scope>provided</scope>
</dependency>
```

Do not shade, relocate or package these API classes inside your add-on. The running TheMob plugin supplies the shared class definitions. Do not install API/source/JavaDoc JARs as server plugins. POM files use provided dependencies; your add-on still declares its own server API dependency. A separate local repository may later be hosted by the server owner without changing coordinates.

Declare the runtime dependency in your add-on's `plugin.yml`:

```yaml
name: MyTheMobBridge
version: 1.0.0
main: example.MyTheMobBridge
api-version: '1.18'
depend: [TheMob]
```

The simple supported setup uses a hard dependency. If TheMob is optional, isolate **all** TheMob types in a bridge class loaded only after checking the provider. `softdepend` alone does not make missing class references safe.

## Acquire stable services

```java
TheMobApi api = Bukkit.getServicesManager().load(TheMobApi.class);
if (api == null || api.apiVersion() != TheMobApi.API_VERSION) {
    getServer().getPluginManager().disablePlugin(this);
    return;
}
TheMobDungeonApi dungeons = Bukkit.getServicesManager().load(TheMobDungeonApi.class);
```

API artifact version `1.3.1` is distinct from `apiVersion() == 1`, the compatibility major. API 1.3 adds permission-checked admin-menu access, public menu/item services and a cancellable dungeon construction event. API 1.2 introduced combat/loot events and item metadata. Existing method and constructor descriptors remain available, including the four-argument phase event constructor. Older API-1.0/1.1 add-ons remain binary compatible. Check `supportsCombatObjectives()` before using the new feature set; optional old-provider support must isolate new event classes too.

An add-on using new 1.2 methods/events requires TheMob 1.17.0 or newer. Backward binary compatibility means old add-ons work on the new provider; it does not make new interface methods available in old installed TheMob JARs. To support an older provider, inspect its plugin version before loading the 1.2 bridge, or handle the relevant missing-method/class linkage explicitly. A direct call to the new capability method alone cannot protect an old runtime whose interface does not contain that method yet.

Services retain identity across `/mob reload`, but entities and configuration snapshots do not. Requery snapshots after reload; do not cache entity references indefinitely. Disabling TheMob unregisters its services. Add-ons must stop using a disabled provider.

## Execution and trust

Call the API on the **server thread**, including queries. Bukkit event handlers and normal commands already run there. Schedule work from async database/network callbacks with `Bukkit.getScheduler().runTask(yourPlugin, ...)`. Copy UUIDs/strings into background jobs instead of using live Entity/Player/World objects off-thread. Wrong-thread or disabled-provider access fails explicitly.

The API is intended for trusted server plugins. A successful API call does not imply a player permission check; your own commands and menus must authorize the requesting player. The sample spawn command requires its own OP-default permission. The API retains TheMob's spawn budgets, cooldowns and pre-spawn veto path. It does not make arbitrary installed Java plugins untrusted or sandboxed.

## Core queries and mutations

| Method | Contract |
|---|---|
| `definitions()` | Immutable summaries: YAML definition ID, display name, Bukkit EntityType, boss flag. Includes generated level variants. |
| `mob(LivingEntity)` | Optional immutable identity snapshot. Available during the death event as well. Not a live handle and not an alive check. |
| `activeMobs()` | Registry-based snapshots of valid living TheMob entities in loaded worlds, including associated minions. Does not scan/load every world chunk. |
| `spawn(id, location)` | Optional initialized entity through the normal spawn pipeline. Input Location is copied. Unknown/invalid ID/location, cancellation, region, budget or cooldown rejection returns empty. Non-finite coordinates/rotation are rejected. |
| `remove(uuid)` | Administrative removal with no kill, XP, loot or external progress. Null/unknown UUID returns false. |
| `integrations()` | Snapshots of the 20 curated provider states. Ready adapter status does not prove an active objective exists in the third-party plugin. |
| `supportsDungeonRegions()` | Additive capability check for footprint queries. |
| `dungeons()` / `dungeonAt(location)` | Immutable reserved footprints without loading chunks; unfinished/failed builds are also reserved. |

`MobSnapshot` supplies `entityId()`, `definitionId()`, `displayName()`, `boss()`, `minion()`, nullable `phaseId()` and `spawnReason()`. Minions use their parent's definition ID, `boss() == false` and `SpawnReason.MINION`. Spawn reasons are MANUAL, AUTO_SPAWN, EGG, SPAWNER, MINION, NPC and API. Use the definition ID rather than `EntityType.ZOMBIE` to distinguish custom zombies and boss levels.

```java
api.spawn("tm_zombie_lvl_3", player.getLocation()).ifPresent(entity -> {
    UUID entityId = entity.getUniqueId();
    // Store identity if needed; do not create a second artificial kill event.
});
```

## Quest/Jobs objective listener

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onTheMobKill(TheMobDeathEvent event) {
    Player player = event.getKiller();
    if (player == null || event.getMob().minion()
            || player.hasMetadata("NPC") || event.getEntity().hasMetadata("NPC")) return;
    String objectiveKey = event.getMob().definitionId();
    // Forward ONE unit to your plugin's native objective using player UUID + objectiveKey.
    // Also available: boss(), phaseId(), spawnReason(), and event.getEntity().getWorld().
}
```

Create/register the custom objective through **your target plugin's API**, then forward this event into that objective. Do not assume every quest product supports the same objective class. The bundled example's counter marks the forwarding point; it is deliberately not a universal adapter for unknown third-party APIs.

Built-in Jobs integration modifies Jobs' existing payment/experience event; it does not create a second payment. Built-in Quests registers `TheMob Kill`. Built-in BattlePass supplies nine kill/group/phase/damage/drop/loot/dungeon quest types. If using one of these paths, do not also forward the same action into the same objective/reward from your custom bridge.

### Event guarantees

- `TheMobPreSpawnEvent`: synchronous and cancellable before the entity is created; resolved safe-ground position is returned as a defensive copy. Cancel to veto; modifying the returned Location does not retarget the spawn.
- `TheMobSpawnEvent`: initialized/registered entity with initial phase in its snapshot. Boss start-phase notification follows it.
- `TheMobPhaseChangeEvent`: first phase has `previousPhase == null`; later notifications represent actual phase changes. Each boss has independent monotonic phase state.
- `TheMobDeathEvent`: once per native death. `getKiller()` may be null. Administrative removal, reload and minion cleanup do not issue kills. Minion native deaths remain visible; filter them explicitly unless your objective is intended to count them.
- `DungeonCompletedEvent`: successful durably recorded run for its authoritative participant list. Entry alone is not completion.
- `TheMobDamageEvent`: uncancelled final health damage, capped against overkill, attributed to player attacks/projectiles/owned pets. Published next tick after cancellation recheck; the captured mob may already have died.
- `TheMobDropEvent`: accepted native item spawn from final TheMob death loot. Includes original mob snapshot, source traits and defensive item copy. Published next tick; collection is not implied.
- `TheMobLootEvent`: accepted player pickup from provenanced ground loot, including partial stack quantity. Provenance persists on the item ENTITY across unload/restart and never on the inventory stack. Player redrops cannot farm loot progress. Different source traits do not merge; equivalent traits still can.

Phase events add immutable `getParticipants()`: recent damaging combatants within 120 seconds, still eligible/alive and in the configured arena radius. Initial phase has an empty list. Do not give bystanders phase credit. `supportsCombatObjectives()`, `definitionLevel(id)`, `itemIds()`, `createItem(id)` and `inspectItem(stack)` expose the new capability and cloned reward items. `createItem` is not a loot event; delivery, permission checks and inventory leftovers belong to the caller.

`ItemSnapshot` uses authoritative metadata, not display text: ID, material, rarity (`rare`, `epic`, `legendary`, `custom`, `vanilla`), stars 0–5, amount and custom flag. Catalog ID example: `epic_sword_5`; essence: `essence:zombie:5`; vanilla fallback: `minecraft:diamond`. Zero stars means unstarred. `LootSource` gives definition, phase, reason, boss/minion flags, configured level, source world and nullable killer UUID. Source traits intentionally describe equivalent loot, not a unique mob entity. `TheMobDropEvent` additionally exposes the original `MobSnapshot` for precise mob identity.

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onLoot(TheMobLootEvent event) {
    ItemSnapshot item = event.getItemSnapshot();
    if (!item.rarity().equals("epic") || item.stars() < 3) return;
    // Forward item.amount() to ONE native collection objective for event.getPlayer().
}
```

Subscribe to these events; do **not** construct/redispatch them to request a kill or reward. Do not also count `EntityDeathEvent` for the same objective. The events are trusted server observations; installed Java plugins can forge arbitrary events, so installing a bridge is a server trust decision. There is no public damage-leaderboard contract.

## Dungeon integration

`DungeonRegion` is world-UUID bound and height-independent, with inclusive `minX/minZ`, exclusive `maxX/maxZ`, and `ready()` indicating a finished, available dungeon. Snapshots do not update themselves. Protection/claim plugins should include unfinished reserved areas; movement/travel exceptions normally require `ready()`.

`TheMobDungeonApi` offers `at`, `regions`, `intersecting`, `blocksClaim`, `complete`, `supportsExitAltars` and `atExitAltar`. Querying does not load chunks. `complete(id, runUuid, participantUuids)` is for a trusted plugin that has already verified its encounter. It filters participants and durably deduplicates the most recent 256 run UUIDs per dungeon. Keep one UUID for one real run; do not generate a new UUID to retry the same completion. Do not call `complete` in response to `DungeonCompletedEvent`.

```java
@EventHandler
public void onDungeon(DungeonCompletedEvent event) {
    for (Player player : event.getParticipants()) {
        // Forward dungeon ID + run UUID + player UUID into your own objective exactly once.
    }
}
```

Travel plugins may register `DungeonTravelProvider` via Bukkit ServicesManager. Return immutable `DungeonTravelTarget` values for the actual permitted targets of that player. `travel` must revalidate altar/source, current target permissions and safe landing immediately before teleporting, then return actual success. Unregister your provider on disable. The altar rechecks service identity and source validity, and falls back to native return/entrance choices without a travel provider.

## Compatibility and verification

The build compiles the sample against only the public JARs and Spigot dependencies. The SDK also supplies a standalone Gradle wrapper project, tested after extracting the final ZIP. Existing API member descriptors are checked against 1.1.0; unchanged public classes and the full dungeon API retain their bytecode. Native BattlePass progress tests use the actual 5.0.12 provider classes; generic Jobs/Quests provider fixtures do not replace a live test of your exact installation.

API input validation, pre-spawn vetoes, one native death, administrative cleanup, independent phases, dungeon run deduplication, admin permissions, backup preservation and example objective forwarding are covered by local tests. Real client/server/third-party integration remains an operator test. See `API.md`, `DUNGEON-API.md` and `ADMIN-VERBINDUNGEN.md` for additional German contracts and installation notes.

## API 1.3 services and construction guard

The compiled `example/` add-on registers both `AdminMenuExtension` and `TheMobItemProvider` through `Bukkit.getServicesManager().register(..., this, ServicePriority.Normal)` and unregisters its services on disable. The menu entry requires both TheMob menu access and `themobexample.admin`. The native item reference `themobexample:token` creates a demonstration paper token without delivery or payment. Return empty for unknown IDs, use a unique lowercase namespace, never reserve `themob`, and never shade these interfaces. Native provider metadata is preserved.

Call `supportsAdminMenu()` and `openAdminMenu(player)` on a 1.3-capable runtime. The latter enforces TheMob's menu permission; it is not a way to grant administration. A plugin extending TheMob's menu owns its own view and must revalidate permissions for its actions. All factories/menu callbacks execute on the server thread. Query `itemProviders()` for available namespaces; `itemIds()` continues to enumerate the built-in catalog.

Listen to `TheMobDungeonPreCreateEvent` to veto construction/resume before reservation/planning changes. Check `getSender()` and immutable `getRegion()`; `getPlayer()` is null for console resume. Region upper bounds are exclusive and height independent. New builds use provisional ID `pending`. This is a preflight event, not continuous claim-locking. A bridge requiring new 1.3 classes must require TheMob 1.19.1 or isolate that bridge behind an older-runtime compatibility layer.

The source/JavaDoc and local Maven repository carry all new contracts. See `API.md` for metadata persistence limits and `INTEGRATIONEN-20.md` for the exact native versus Bukkit-only integration matrix. No third-party provider JARs are included.

## Older Java runtimes

API 1.3.1 uses Java 17 bytecode. Existing API-1.x method descriptors remain compatible, but add-ons previously built as Java 25 bytecode need recompiling with release 17 to run on older Java servers. A compatible API does not convert an add-on's class-file version. The standalone SDK example compiles against the 1.18 server API and emits Java 17 classes; the full plugin build and current test tooling use JDK 25.
