package org.plugin.theMob.boss.behavior;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.core.ZombieKingBehavior;

class ZombieKingBehaviorTest {
  ServerMock server;
  TheMob plugin;
  World world;
  Mob boss;
  PlayerMock player;
  ZombieKingBehavior behavior;
  BossPhase phase;
  final List<EntityDamageByEntityEvent> hits = new ArrayList<>();

  static class CombatWorld extends org.mockbukkit.mockbukkit.world.WorldMock {
    Map<String, org.mockbukkit.mockbukkit.block.BlockMock> blocks;
    boolean collisions;

    CombatWorld() {
      super(new WorldCreator("world"));
    }

    @Override
    public org.mockbukkit.mockbukkit.block.BlockMock getBlockAt(int x, int y, int z) {
      // Preparing ordinary terrain needs no Mockito instances. Wrap only queried combat blocks.
      if (!collisions) return super.getBlockAt(x, y, z);
      if (blocks == null) blocks = new java.util.HashMap<>();
      return blocks.computeIfAbsent(
          x + ":" + y + ":" + z,
          key -> {
            var block = spy(super.getBlockAt(x, y, z));
            // The fixture contains air/full cubes; native partial collision remains a live-test
            // limit.
            doAnswer(call -> block.getType().isAir()).when(block).isPassable();
            return block;
          });
    }

    @Override
    public org.mockbukkit.mockbukkit.world.ChunkMock[] getLoadedChunks() {
      return new org.mockbukkit.mockbukkit.world.ChunkMock[0];
    }
  }

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    // Chunk tile enumeration is missing in MockBukkit; this combat fixture contains no tiles.
    world = new CombatWorld();
    server.addWorld((org.mockbukkit.mockbukkit.world.WorldMock) world);
    for (int x = -12; x <= 70; x++)
      for (int z = -12; z <= 12; z++) world.getBlockAt(x, 63, z).setType(Material.STONE);
    for (int x = -1; x <= 4; x++) for (int z = -1; z <= 0; z++) world.loadChunk(x, z);
    plugin = MockBukkit.load(TheMob.class);
    ((CombatWorld) world).collisions = true;
    boss = spy((Mob) world.spawnEntity(new Location(world, .5, 64, .5), EntityType.PIG));
    doReturn(true).when(boss).isAware();
    doNothing().when(boss).setAware(anyBoolean());
    doReturn(true).when(boss).hasLineOfSight(any(Entity.class));
    doReturn(true).when(boss).isOnGround();
    doReturn(3.6).when(boss).getHeight();
    doReturn(1.2).when(boss).getWidth();
    player = combatant("Fighter");
    player.setOnGround(true);
    player.teleport(new Location(world, 3, 64, .5));
    behavior = new ZombieKingBehavior(plugin);
    server.getPluginManager().registerEvents(behavior, plugin);
    server
        .getPluginManager()
        .registerEvent(
            EntityDamageByEntityEvent.class,
            new Listener() {},
            EventPriority.MONITOR,
            (listener, event) -> hits.add((EntityDamageByEntityEvent) event),
            plugin);
    enter("royal");
  }

  PlayerMock combatant(String name) {
    // MockBukkit.damage() omits events. Use its simulation to exercise the real listener pipeline.
    var fighter =
        new PlayerMock(server, name) {
          boolean replaying;

          @Override
          public void damage(double amount, Entity source) {
            if (replaying) {
              super.damage(amount, source);
              return;
            }
            replaying = true;
            try {
              simulateDamage(amount, source);
            } finally {
              replaying = false;
            }
          }
        };
    server.addPlayer(fighter);
    return fighter;
  }

  @AfterEach
  void close() {
    try {
      if (behavior != null) behavior.onExit(boss, phase);
    } finally {
      MockBukkit.unmock();
    }
  }

  void enter(String stage) {
    if (phase != null) behavior.onExit(boss, phase);
    var cfg = new YamlConfiguration();
    cfg.set("behavior.zombie-stage", stage);
    cfg.set("behavior.windup-ticks", 20);
    cfg.set("behavior.recovery-ticks", 30);
    cfg.set("behavior.cooldown-ticks", 60);
    cfg.set("behavior.attack-damage", 3);
    phase = new BossPhase(stage, 0, 100, stage, cfg);
    behavior.onEnter(boss, phase);
    plugin.activity().refreshPlayers();
  }

  void tick(long ticks) {
    server.getScheduler().performTicks(ticks);
    plugin.activity().refreshPlayers();
    behavior.tick(boss, phase);
  }

  @Test
  void royalCleaveHasWarningDirectionalDodgeAndRecoveryWindow() {
    tick(40);
    assertEquals(20, player.getHealth());
    tick(10);
    assertEquals(20, player.getHealth());
    player.teleport(new Location(world, .5, 64, 3)); // Leave the fixed 120-degree arc.
    tick(10);
    assertEquals(20, player.getHealth());
    tick(30);
    player.teleport(new Location(world, 3, 64, .5));
    tick(60);
    tick(20);
    assertEquals(17, player.getHealth());
    double health = player.getHealth();
    tick(10);
    assertEquals(health, player.getHealth());
  }

  @Test
  void royalCleaveCanTargetAndDamagePetOfCreativeAdmin() {
    player.setGameMode(GameMode.CREATIVE);
    player.setOp(true);
    var pet = (LivingEntity) world.spawnEntity(new Location(world, 3, 64, .5), EntityType.COW);
    pet.getPersistentDataContainer()
        .set(
            new NamespacedKey("cosmetics", "pet"),
            org.bukkit.persistence.PersistentDataType.BYTE,
            (byte) 1);
    double health = pet.getHealth();
    tick(40);
    assertEquals(health, pet.getHealth());
    tick(20);
    assertTrue(pet.getHealth() < health);
    assertEquals(20, player.getHealth());
  }

  @Test
  void royalCleaveCanBeDisabledWithoutDisablingOtherZombieSkills() throws Exception {
    var file = new File(plugin.getDataFolder(), "skills.yml");
    var yaml = YamlConfiguration.loadConfiguration(file);
    yaml.set("bosses.zombie-cleave", false);
    yaml.save(file);
    var field = TheMob.class.getDeclaredField("skills");
    field.setAccessible(true);
    field.set(plugin, new org.plugin.theMob.core.TheMobSkills(plugin));
    tick(40);
    tick(30);
    tick(80);
    assertTrue(hits.isEmpty());
    assertEquals(20, player.getHealth());
    assertTrue(plugin.skills().enabled("bosses.zombie-lunge"));
  }

  @Test
  void windupAndRecoverySuppressNativeContactDamageButScriptedHitStillWorks() {
    tick(40);
    assertTrue(nativeHit().isCancelled());
    tick(20);
    assertEquals(17, player.getHealth());
    assertTrue(nativeHit().isCancelled());
    tick(30);
    assertFalse(nativeHit().isCancelled());
  }

  @Test
  void huntLungeKeepsItsDirectionAndOnlyHitsActualTravelOnce() {
    player.teleport(new Location(world, 6, 64, .5));
    enter("hunt");
    tick(40);
    player.teleport(new Location(world, .5, 64, 6));
    tick(20);
    assertEquals(20, player.getHealth());
    assertTrue(boss.getVelocity().getX() > .29);
    assertEquals(0, boss.getVelocity().getZ(), .001);
    // MockBukkit does not integrate physics; explicitly move along the real velocity corridor.
    player.teleport(new Location(world, 3, 64, .5));
    boss.teleport(new Location(world, 3.5, 64, .5));
    tick(10);
    assertEquals(17, player.getHealth());
    boss.teleport(new Location(world, 5.5, 64, .5));
    tick(10);
    assertEquals(17, player.getHealth());
  }

  @Test
  void lungeNeverPredictivelyDamagesAPlayerBeyondTheActualPath() {
    player.teleport(new Location(world, 6, 64, .5));
    enter("hunt");
    tick(40);
    tick(20);
    assertEquals(20, player.getHealth());
    tick(20);
    assertEquals(20, player.getHealth());
  }

  @Test
  void lungeChecksScaledHeadroomAndStopsBeforeAWallOrCliff() {
    player.teleport(new Location(world, 6, 64, .5));
    enter("hunt");
    world.getBlockAt(1, 67, 0).setType(Material.STONE); // Head of a 1.8x zombie, above normal mobs.
    tick(40);
    tick(20);
    assertEquals(0, boss.getVelocity().lengthSquared());
    assertEquals(20, player.getHealth());
    world.getBlockAt(1, 67, 0).setType(Material.AIR);
    world.getBlockAt(1, 63, 0).setType(Material.AIR);
    enter("hunt");
    tick(40);
    tick(20);
    assertEquals(0, boss.getVelocity().lengthSquared());
    assertEquals(20, player.getHealth());
  }

  @Test
  void frenzyGroundWaveExpandsAndJumpingAvoidsIt() {
    player.teleport(new Location(world, 5.5, 64, .5));
    enter("frenzy");
    tick(40);
    tick(20);
    assertEquals(20, player.getHealth()); // Radius two.
    tick(10);
    assertEquals(20, player.getHealth()); // Radius four.
    player.setOnGround(false);
    player.teleport(new Location(world, 5.5, 65, .5));
    tick(10);
    assertEquals(20, player.getHealth());
    player.setOnGround(true);
    player.teleport(new Location(world, 5.5, 64, .5));
    enter("frenzy");
    tick(40);
    tick(20);
    tick(10);
    tick(10);
    assertEquals(17, player.getHealth());
  }

  @Test
  void desperateStageRetreatsBrieflyThenCommitsAndRecovers() {
    enter("desperate");
    tick(40); // Start retreat.
    tick(10);
    assertTrue(boss.getVelocity().getX() < 0);
    assertTrue(nativeHit().isCancelled());
    tick(30); // Bounded retreat expires even if physics cannot move the boss.
    assertEquals(20, player.getHealth());
    tick(20);
    assertEquals(17, player.getHealth());
    assertTrue(nativeHit().isCancelled());
  }

  @Test
  void phaseChangeDiscardsPendingAttackAndKeepsOriginalLeash() {
    tick(40);
    boss.teleport(new Location(world, 10.5, 64, .5));
    player.teleport(new Location(world, 13, 64, .5));
    enter("frenzy");
    tick(20);
    assertEquals(20, player.getHealth());
    assertEquals(
        .5,
        boss.getPersistentDataContainer()
            .get(
                new NamespacedKey(plugin, "boss_home_x"),
                org.bukkit.persistence.PersistentDataType.DOUBLE));
    boss.teleport(new Location(world, 60.5, 64, .5));
    player.teleport(new Location(world, 63, 64, .5));
    tick(100);
    assertEquals(20, player.getHealth());
    assertNull(boss.getTarget());
  }

  @Test
  void coldSuspendDiscardsWarningAndResumeRequiresFreshDelay() {
    tick(40);
    behavior.onSuspend(boss, phase);
    tick(20);
    assertEquals(20, player.getHealth());
    tick(40);
    assertEquals(20, player.getHealth());
    tick(20);
    assertEquals(17, player.getHealth());
  }

  @Test
  void lostLineOfSightOrDisplacementCancelsCommittedHit() {
    tick(40);
    doReturn(false).when(boss).hasLineOfSight(any(Entity.class));
    tick(20);
    assertEquals(20, player.getHealth());
    doReturn(true).when(boss).hasLineOfSight(any(Entity.class));
    enter("royal");
    tick(40);
    boss.teleport(new Location(world, 2.5, 64, .5));
    tick(20);
    assertEquals(20, player.getHealth());
  }

  @Test
  void protectedDamageHasNoAdditionalKnockbackAndNoDuplicateHit() {
    server
        .getPluginManager()
        .registerEvent(
            EntityDamageByEntityEvent.class,
            new Listener() {},
            EventPriority.HIGHEST,
            (listener, event) -> ((EntityDamageByEntityEvent) event).setCancelled(true),
            plugin);
    tick(40);
    tick(20);
    assertEquals(20, player.getHealth());
    assertEquals(0, player.getVelocity().lengthSquared());
    assertTrue(hits.stream().anyMatch(EntityDamageByEntityEvent::isCancelled));
  }

  @Test
  void creativeSpectatorAndNpcsNeverBecomeTargets() {
    for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
      player.setGameMode(mode);
      tick(100);
      assertNull(boss.getTarget());
      assertEquals(20, player.getHealth());
    }
    player.setGameMode(GameMode.SURVIVAL);
    player.setMetadata("NPC", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    tick(100);
    assertNull(boss.getTarget());
  }

  @Test
  void intentionalStandstillNeverTriggersGenericTeleportOrBlockBreaking() {
    tick(40);
    world.getBlockAt(1, 65, 0).setType(Material.OAK_DOOR);
    for (int i = 0; i < 100; i++) plugin.getStuckDefense().tick(boss);
    assertEquals(new Location(world, .5, 64, .5).toVector(), boss.getLocation().toVector());
    assertEquals(Material.OAK_DOOR, world.getBlockAt(1, 65, 0).getType());
    behavior.onExit(boss, phase);
    assertFalse(boss.hasMetadata(ZombieKingBehavior.METADATA));
    verify(boss, atLeastOnce()).setAware(true);
  }

  @Test
  void existingBossAndWarlordsReceiveStagesWithoutRewritingHpLootOrFiles() throws Exception {
    var cfg = plugin.mobs().mobConfigById("zombie_boss_template");
    var original =
        YamlConfiguration.loadConfiguration(
            new File(plugin.getDataFolder(), "mobs/boss_template.yml"));
    assertEquals(original.get("drops"), cfg.get("drops"));
    assertEquals(original.get("legendary-drops"), cfg.get("legendary-drops"));
    for (var entry :
        Map.of("phase1", "royal", "phase2", "hunt", "phase3", "frenzy", "phase4", "desperate")
            .entrySet()) {
      String path = "phases." + entry.getKey();
      assertEquals("zombie_king", cfg.getString(path + ".behavior.mode"));
      assertEquals(entry.getValue(), cfg.getString(path + ".behavior.zombie-stage"));
      assertEquals(original.getString(path + ".hp-range"), cfg.getString(path + ".hp-range"));
    }
    for (int level = 1; level <= 5; level++) {
      var warlord = plugin.mobs().mobConfigById("tm_boss_warlord_lvl_" + level);
      assertEquals("hunt", warlord.getString("phases.pressure.behavior.zombie-stage"));
      assertEquals("frenzy", warlord.getString("phases.rage.behavior.zombie-stage"));
    }
    assertNotEquals(
        "zombie_king",
        plugin
            .mobs()
            .mobConfigById("tm_boss_marksman_lvl_1")
            .getString("phases.guard.behavior.mode"));
    byte[] before =
        Files.readAllBytes(new File(plugin.getDataFolder(), "mobs/boss_template.yml").toPath());
    plugin.configs().reloadMobsValidated();
    assertArrayEquals(
        before,
        Files.readAllBytes(new File(plugin.getDataFolder(), "mobs/boss_template.yml").toPath()));
  }

  @Test
  void profileOptOutAndPerPhaseSettingsSurviveReload() throws Exception {
    File mobFile = new File(plugin.getDataFolder(), "mobs/boss_template.yml");
    var mob = YamlConfiguration.loadConfiguration(mobFile);
    mob.set("phases.phase1.behavior.attack-damage", 2.25);
    mob.save(mobFile);
    plugin.configs().reloadMobsValidated();
    assertEquals(
        2.25,
        plugin
            .configs()
            .mobConfigs()
            .get("zombie_boss_template")
            .getDouble("phases.phase1.behavior.attack-damage"));
    mob.set("zombie-behavior-disabled", true);
    mob.save(mobFile);
    plugin.configs().reloadMobsValidated();
    assertEquals(
        "idle",
        plugin
            .configs()
            .mobConfigs()
            .get("zombie_boss_template")
            .getString("phases.phase1.behavior.mode"));
    File settings = new File(plugin.getDataFolder(), "zombie-boss.yml");
    var global = YamlConfiguration.loadConfiguration(settings);
    global.set("enabled", false);
    global.save(settings);
    plugin.configs().reloadMobsValidated();
    assertEquals(
        "bruiser",
        plugin
            .configs()
            .mobConfigs()
            .get("tm_boss_warlord_lvl_1")
            .getString("phases.guard.behavior.mode"));
  }

  @Test
  void anUnloadedChunkStopsTheLungeWithoutLoadingIt() {
    boss.teleport(new Location(world, 15.5, 64, .5));
    player.teleport(new Location(world, 21, 64, .5));
    world.unloadChunk(1, 0, false);
    enter("hunt");
    tick(40);
    tick(20);
    assertFalse(world.isChunkLoaded(1, 0));
    assertEquals(0, boss.getVelocity().lengthSquared());
    assertEquals(20, player.getHealth());
  }

  @Test
  void actualColdZoneSuspensionDoesNotWakeTheBossOrReplayAnAttack() {
    tick(40);
    plugin.mobs().track(boss);
    player.teleport(new Location(world, 1000, 64, 1000));
    tick(250);
    assertFalse(plugin.activity().active(boss));
    assertFalse(boss.hasAI());
    player.teleport(new Location(world, 3, 64, .5));
    plugin.activity().refreshPlayers();
    plugin.activity().tick();
    tick(1);
    assertTrue(boss.hasAI());
    assertEquals(20, player.getHealth());
    tick(40);
    assertEquals(20, player.getHealth());
    tick(20);
    assertEquals(17, player.getHealth());
  }

  @Test
  void switchingTargetsDuringWarningDoesNotLockRecoveryForever() {
    tick(40);
    var replacement = server.addPlayer();
    replacement.teleport(new Location(world, 3, 64, .5));
    player.setGameMode(GameMode.SPECTATOR);
    tick(10);
    assertNull(boss.getTarget());
    tick(30);
    tick(1);
    assertEquals(replacement.getUniqueId(), boss.getTarget().getUniqueId());
  }

  @Test
  void phaseTransitionInsideADamageCallbackCancelsTheRestOfTheOldAttack() {
    var second = combatant("Second");
    second.teleport(new Location(world, 3, 64, 1));
    int[] callbacks = {0};
    server
        .getPluginManager()
        .registerEvent(
            EntityDamageByEntityEvent.class,
            new Listener() {},
            EventPriority.HIGHEST,
            (listener, event) -> {
              if (((EntityDamageByEntityEvent) event).getDamager().equals(boss)
                  && callbacks[0]++ == 0) enter("hunt");
            },
            plugin);
    tick(40);
    tick(20);
    assertEquals("hunt", phase.id());
    assertEquals(37, player.getHealth() + second.getHealth());
    tick(20);
    assertEquals(37, player.getHealth() + second.getHealth());
    assertEquals(1, callbacks[0]);
  }

  @Test
  void sharedMovementPassAdvancesLungeInShortStepsAndStopsAfterOneHit() {
    player.teleport(new Location(world, 6, 64, .5));
    enter("hunt");
    tick(40);
    tick(20);
    for (int i = 0; i < 10; i++) {
      // Explicit native-physics fixture: horizontal drag between two movement updates.
      boss.teleport(boss.getLocation().add(boss.getVelocity().clone().multiply(1.5)));
      server.getScheduler().performTicks(2);
      behavior.tickMovement();
    }
    assertEquals(17, player.getHealth());
    assertEquals(0, boss.getVelocity().lengthSquared());
    assertTrue(nativeHit().isCancelled()); // Recovery, no late native contact hit.
    server.getScheduler().performTicks(200);
    behavior.tickMovement();
    assertEquals(17, player.getHealth()); // No orphan moving state starts a new attack.
  }

  @Test
  void losingAllTargetsRemovesFastMovementAndItsResidualVelocity() {
    player.teleport(new Location(world, 6, 64, .5));
    enter("hunt");
    tick(40);
    tick(20);
    player.setGameMode(GameMode.SPECTATOR);
    behavior.tickMovement();
    assertEquals(0, boss.getVelocity().lengthSquared());
    player.setGameMode(GameMode.SURVIVAL);
    server.getScheduler().performTicks(200);
    behavior.tickMovement();
    assertEquals(20, player.getHealth());
    assertNull(boss.getTarget());
  }

  EntityDamageByEntityEvent nativeHit() {
    var event = new EntityDamageByEntityEvent(boss, player, DamageCause.ENTITY_ATTACK, 2);
    server.getPluginManager().callEvent(event);
    return event;
  }
}
