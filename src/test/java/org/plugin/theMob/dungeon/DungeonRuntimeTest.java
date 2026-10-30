package org.plugin.theMob.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.plugin.theMob.TheMob;

class DungeonRuntimeTest {
  ServerMock server;
  World world;
  @TempDir Path temp;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  @Test
  void metadataSurvivesRestartAndNeverReusesInterruptedPlots() throws Exception {
    Path file = temp.resolve("dungeons.yml");
    var store = new DungeonStore(file);
    var first = store.reserve(DungeonStyle.GOD_TOWER, 555, world, 0, 55, 0);
    first.apply(DungeonPlanner.generate(first.style, first.seed));
    first.state = DungeonRecord.State.READY;
    var interrupted = store.reserve(DungeonStyle.MINE, 444, world, 256, 55, 0);
    interrupted.state = DungeonRecord.State.BUILDING;
    store.save();
    var loaded = new DungeonStore(file);
    assertEquals(DungeonRecord.State.READY, loaded.get(first.id).state);
    assertEquals(first.rooms, loaded.get(first.id).rooms);
    assertEquals(10, loaded.get(first.id).levels);
    assertEquals(DungeonRecord.State.INTERRUPTED, loaded.get(interrupted.id).state);
    var next = loaded.reserve(DungeonStyle.CRYPT, 888, world, 512, 55, 0);
    assertEquals(2, next.slot);
    assertFalse(first.contains(next.x(), next.z()));
  }

  @Test
  void malformedMetadataIsRejectedInsteadOfOverwritingPlots() throws Exception {
    Path file = temp.resolve("dungeons.yml");
    Files.writeString(file, "format: 999\nnext-slot: 0\n");
    assertThrows(Exception.class, () -> new DungeonStore(file));
    assertTrue(Files.readString(file).contains("999"));
  }

  @Test
  void publicDungeonApiReportsReservedBoundsAndReadinessFromPersistedDataAcrossReloads()
      throws Exception {
    var plugin = MockBukkit.load(TheMob.class);
    var api = plugin.api();
    assertTrue(api.supportsDungeonRegions());
    assertTrue(api.dungeons().isEmpty());
    assertTrue(api.dungeonAt(null).isEmpty());
    var file = new DungeonStore(plugin.getDataFolder().toPath().resolve("dungeons.yml"));
    var ready = file.reserve(DungeonStyle.CRYPT, 10, world, -100, 55, -100);
    ready.state = DungeonRecord.State.READY;
    var cancelled = file.reserve(DungeonStyle.MINE, 11, world, 256, 55, 0);
    cancelled.state = DungeonRecord.State.CANCELLED;
    file.save();
    plugin.reloadPlugin();
    assertSame(api, plugin.api());
    var snapshot = api.dungeonAt(new Location(world, -99.9, 250, -99.9)).orElseThrow();
    assertEquals(ready.id, snapshot.id());
    assertTrue(snapshot.ready());
    assertEquals(-100, snapshot.minX());
    assertEquals(13, snapshot.maxX());
    assertEquals(33, snapshot.maxZ());
    assertTrue(api.dungeonAt(new Location(world, 13, 55, -99)).isEmpty());
    assertTrue(api.dungeonAt(new Location(world, -100.001, 55, -99)).isEmpty());
    assertFalse(api.dungeonAt(new Location(world, 256, -50, 0)).orElseThrow().ready());
    assertTrue(api.dungeonAt(new Location(server.addSimpleWorld("other"), -99, 55, -99)).isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> api.dungeons().clear());
    plugin.dungeons().get(ready.id).state = DungeonRecord.State.FAILED;
    assertFalse(api.dungeonAt(new Location(world, -99, 55, -99)).orElseThrow().ready());
    assertTrue(snapshot.ready());
  }

  @Test
  void onlyAdminsCanGenerateAndOnlyReadyDungeonsCanBeEntered() {
    var plugin = MockBukkit.load(TheMob.class);
    var player = server.addPlayer();
    player.setOp(false);
    DungeonCommand.execute(plugin.dungeons(), player, new String[] {"dungeon"});
    assertFalse(plugin.dungeons().busy());
    assertTrue(plugin.dungeons().records().isEmpty());
    assertTrue(player.nextMessage().contains("Nur Admins"));
    DungeonCommand.execute(plugin.dungeons(), player, new String[] {"dungeon", "edit", "d00001"});
    assertTrue(player.nextMessage().contains("Nur Admins"));
    DungeonCommand.execute(plugin.dungeons(), player, new String[] {"dungeon", "enter", "d00001"});
    assertEquals(world, player.getWorld());
    assertTrue(player.nextMessage().contains("nicht fertig"));
  }

  @Test
  void adminCreationReservesTheActualSiteAndCancellationAndReloadKeepItsProtection() {
    var plugin = MockBukkit.load(TheMob.class);
    // MockBukkit lacks native passability. Stub only the two known air blocks at the waiting spot.
    var site =
        new org.mockbukkit.mockbukkit.world.WorldMock(new WorldCreator("dungeon_site")) {
          @Override
          public org.mockbukkit.mockbukkit.block.BlockMock getBlockAt(
              org.mockbukkit.mockbukkit.world.Coordinate coordinate) {
            var block = super.getBlockAt(coordinate);
            if (block.getX() == 0
                && block.getZ() == 15
                && (block.getY() == 73 || block.getY() == 74)) {
              var air = spy(block);
              doReturn(true).when(air).isPassable();
              return air;
            }
            return block;
          }
        };
    server.addWorld(site);
    world = site;
    var player = server.addPlayer();
    player.setOp(true);
    player.teleport(new Location(world, 0.5, 73, 0.5));
    world.getBlockAt(0, 72, 15).setType(Material.STONE); // safe waiting spot, outside the footprint
    DungeonCommand.execute(plugin.dungeons(), player, new String[] {"dungeon", "labyrinth"});
    assertTrue(plugin.dungeons().busy(), plugin.dungeons().status());
    var r = plugin.dungeons().records().iterator().next();
    assertEquals(-56, r.x());
    assertEquals(-123, r.z());
    assertEquals(world.getUID(), r.worldId);
    assertEquals(64, r.baseY);
    Block structure = world.getBlockAt(-50, 80, -120);
    assertTrue(plugin.dungeons().protectedBlock(structure));
    assertFalse(plugin.dungeons().canEdit(player, structure));
    plugin.dungeons().cancel(player);
    assertFalse(plugin.dungeons().busy());
    plugin.reloadPlugin();
    assertTrue(plugin.dungeons().protectedBlock(structure));
    assertEquals(DungeonRecord.State.CANCELLED, plugin.dungeons().get(r.id).state);
    plugin.dungeons().edit(player, r.id);
    assertTrue(plugin.dungeons().canEdit(player, structure));
    plugin.dungeons().edit(player, "off");
    assertFalse(plugin.dungeons().canEdit(player, structure));
    plugin.dungeons().create(player, DungeonStyle.CRYPT);
    assertFalse(plugin.dungeons().busy());
    assertEquals(1, plugin.dungeons().records().size());
    plugin.dungeons().resume(player, r.id);
    assertTrue(plugin.dungeons().busy());
    plugin.dungeons().cancel(player);
  }

  @Test
  void structureCannotBeMinedOrChangedEvenByAnOpUntilExplicitEditMode() {
    Player player = server.addPlayer();
    player.setOp(true);
    Block protectedBlock = world.getBlockAt(10, 70, 10);
    protectedBlock.setType(Material.STONE_BRICKS);
    var protection = new DungeonProtection(b -> b.getX() == 10, (p, b) -> false);
    var breaking = new BlockBreakEvent(protectedBlock, player);
    protection.breakBlock(breaking);
    assertTrue(breaking.isCancelled());
    var placing =
        new BlockPlaceEvent(
            protectedBlock,
            protectedBlock.getState(),
            protectedBlock,
            new ItemStack(Material.STONE),
            player,
            true,
            org.bukkit.inventory.EquipmentSlot.HAND);
    protection.place(placing);
    assertTrue(placing.isCancelled());
    var editing = new DungeonProtection(b -> true, (p, b) -> p.equals(player));
    var allowed = new BlockBreakEvent(protectedBlock, player);
    editing.breakBlock(allowed);
    assertFalse(allowed.isCancelled());
  }

  @Test
  void explosionsFireWithersPistonsAndLiquidCannotChangeProtectedBlocks() {
    Block inside = world.getBlockAt(10, 70, 10), outside = world.getBlockAt(11, 70, 10);
    var protection = new DungeonProtection(b -> b.getX() == 10, (p, b) -> false);
    var explosion = mock(EntityExplodeEvent.class);
    List<Block> blocks = new ArrayList<>(List.of(inside, outside));
    when(explosion.blockList()).thenReturn(blocks);
    protection.explode(explosion);
    assertEquals(List.of(outside), blocks);
    var wither = mock(EntityChangeBlockEvent.class);
    when(wither.getBlock()).thenReturn(inside);
    protection.entity(wither);
    verify(wither).setCancelled(true);
    var burn = new BlockBurnEvent(inside, outside);
    protection.burn(burn);
    assertTrue(burn.isCancelled());
    var piston =
        new BlockPistonExtendEvent(world.getBlockAt(12, 70, 10), List.of(outside), BlockFace.WEST);
    protection.extend(piston);
    assertTrue(piston.isCancelled());
    var liquid = new BlockFromToEvent(outside, inside);
    protection.flow(liquid);
    assertTrue(liquid.isCancelled());
    var leaves = new LeavesDecayEvent(inside);
    protection.decay(leaves);
    assertTrue(leaves.isCancelled());
  }

  @Test
  void axesCannotStripTheWorldTreeAndUnrelatedWorldBlocksAreUnaffected() {
    Player player = server.addPlayer();
    Block log = world.getBlockAt(10, 70, 10);
    log.setType(Material.OAK_LOG);
    var protection = new DungeonProtection(b -> b.getX() == 10, (p, b) -> false);
    var strip =
        new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.DIAMOND_AXE),
            log,
            BlockFace.UP);
    protection.interact(strip);
    assertEquals(org.bukkit.event.Event.Result.DENY, strip.useInteractedBlock());
    log.setType(Material.SPAWNER);
    var egg =
        new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.EGG), log, BlockFace.UP);
    protection.beforeInteract(egg);
    assertEquals(org.bukkit.event.Event.Result.DENY, egg.useInteractedBlock());
    var unrelated = new BlockBreakEvent(world.getBlockAt(30, 70, 10), player);
    protection.breakBlock(unrelated);
    assertFalse(unrelated.isCancelled());
  }

  @Test
  void batchedWriterCapsBlocksAndChunksAndReleasesItsTicket() {
    Plugin plugin = MockBukkit.createMockPlugin();
    World target = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(Material.STONE);
    when(chunk.getEntities()).thenReturn(new Entity[0]);
    when(target.getMinHeight()).thenReturn(-64);
    when(target.getMaxHeight()).thenReturn(320);
    when(target.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
    when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);
    var plan = DungeonPlanner.generate(DungeonStyle.CRYPT, 12);
    var record = new DungeonRecord(0, plan.style(), plan.seed(), DungeonRecord.State.BUILDING);
    try (var writer =
        new DungeonBuilder(plugin, target, record, plan, new Location(target, 0, 70, 200))) {
      assertFalse(writer.tick(17, Long.MAX_VALUE));
      verify(block, times(17)).setBlockData(any(), eq(false));
      verify(target, times(1)).getChunkAt(anyInt(), anyInt());
      verify(chunk).addPluginChunkTicket(plugin);
    }
    verify(chunk).removePluginChunkTicket(plugin);
  }

  @Test
  void allGeneratedBlockStatesAreValidOnTheTargetServerApi() {
    for (var style : DungeonStyle.values())
      for (String state : DungeonPlanner.generate(style, 42).palette())
        assertNotNull(Bukkit.createBlockData(state), state);
  }

  @Test
  void clearingAtNegativeUnalignedCoordinatesNeverChangesBlocksOutsideTheFootprint() {
    Plugin plugin = MockBukkit.createMockPlugin();
    World target = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    Block block = mock(Block.class);
    when(target.getMinHeight()).thenReturn(0);
    when(target.getMaxHeight()).thenReturn(40);
    when(target.getChunkAt(-1, -1)).thenReturn(chunk);
    when(chunk.getEntities()).thenReturn(new Entity[0]);
    when(chunk.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);
    when(block.getType()).thenReturn(Material.STONE);
    var plan = DungeonPlanner.generate(DungeonStyle.MAZE, 17);
    var record = new DungeonRecord(0, plan.style(), 17, DungeonRecord.State.BUILDING);
    record.originX = -5;
    record.originZ = -7;
    record.baseY = 0;
    try (var builder =
        new DungeonBuilder(plugin, target, record, plan, new Location(target, 0, 8, 200))) {
      assertFalse(builder.tick(8192, Long.MAX_VALUE));
    }
    verify(target, times(1)).getChunkAt(-1, -1);
    verify(chunk, times(5 * 7 * 34))
        .getBlock(
            intThat(x -> x >= 11 && x <= 15),
            intThat(y -> y >= 6 && y < 40),
            intThat(z -> z >= 9 && z <= 15));
    verify(chunk, times(5 * 7 * 34)).getBlock(anyInt(), anyInt(), anyInt());
    var data = org.mockito.ArgumentCaptor.forClass(org.bukkit.block.data.BlockData.class);
    verify(block, times(5 * 7 * 34)).setBlockData(data.capture(), eq(false));
    assertTrue(data.getAllValues().stream().anyMatch(b -> b.getMaterial() == Material.AIR));
    assertTrue(data.getAllValues().stream().anyMatch(b -> b.getMaterial() != Material.AIR));
    verify(block, never()).breakNaturally();
  }

  @Test
  void multipleRegularPlayersCanEnterAndLeaveWhileOnlyManualMobSpawnsAreAllowed() throws Exception {
    var plugin = MockBukkit.load(TheMob.class);
    var store = new DungeonStore(plugin.getDataFolder().toPath().resolve("dungeons.yml"));
    var r = store.reserve(DungeonStyle.CRYPT, 55, world, 100, 64, 100);
    r.state = DungeonRecord.State.READY;
    store.save();
    plugin.reloadPlugin();
    var first = server.addPlayer();
    var second = server.addPlayer();
    first.setOp(false);
    second.setOp(false);
    first.teleport(new Location(world, 0.5, 73, 0.5));
    second.teleport(new Location(world, 10.5, 73, 10.5));
    var a = first.getLocation().clone();
    var b = second.getLocation().clone();
    plugin.dungeons().enter(first, r.id);
    plugin.dungeons().enter(second, r.id);
    assertEquals(156.5, first.getLocation().getX());
    assertEquals(first.getLocation(), second.getLocation());
    Zombie zombie = mock(Zombie.class);
    when(zombie.getLocation()).thenReturn(first.getLocation());
    var natural =
        new org.bukkit.event.entity.CreatureSpawnEvent(
            zombie, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
    plugin.dungeons().natural(natural);
    assertTrue(natural.isCancelled());
    var manual =
        new org.bukkit.event.entity.CreatureSpawnEvent(
            zombie, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
    plugin.dungeons().natural(manual);
    assertFalse(manual.isCancelled());
    plugin.dungeons().leave(first);
    plugin.dungeons().leave(second);
    assertEquals(a, first.getLocation());
    assertEquals(b, second.getLocation());
  }

  @Test
  void evacuatingManyExistingMobsIsAlsoBatchedBeforeTerrainChanges() {
    Plugin plugin = MockBukkit.createMockPlugin();
    World target = mock(World.class);
    Chunk chunk = mock(Chunk.class);
    when(target.getMinHeight()).thenReturn(-64);
    when(target.getMaxHeight()).thenReturn(320);
    when(target.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
    LivingEntity[] entities = new LivingEntity[40];
    Location safe = new Location(target, 0, 73, 200);
    for (int i = 0; i < entities.length; i++) {
      entities[i] = mock(LivingEntity.class);
      when(entities[i].isValid()).thenReturn(true);
      when(entities[i].getLocation()).thenReturn(new Location(target, 1, 73, 1));
      when(entities[i].teleport(safe)).thenReturn(true);
    }
    when(chunk.getEntities()).thenReturn(entities);
    var plan = DungeonPlanner.generate(DungeonStyle.MAZE, 1);
    var r = new DungeonRecord(0, plan.style(), 1, DungeonRecord.State.BUILDING);
    try (var builder = new DungeonBuilder(plugin, target, r, plan, safe)) {
      assertFalse(builder.tick(1536, Long.MAX_VALUE));
      for (int i = 0; i < 40; i++) verify(entities[i], times(i < 16 ? 1 : 0)).teleport(safe);
      verify(chunk, never()).getBlock(anyInt(), anyInt(), anyInt());
    }
  }
}
