package org.plugin.theMob.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.world.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.dungeon.*;

class DungeonAltarTest {
  ServerMock server;
  TheMob plugin;
  WorldMock world;
  Player player;
  DungeonRecord record;

  static class TicketWorld extends WorldMock {
    Map<Long, ChunkMock> chunks;

    TicketWorld() {
      super(new WorldCreator("altar_world"));
    }

    @Override
    public ChunkMock getChunkAt(int x, int z) {
      if (chunks == null) chunks = new HashMap<>();
      long key = ((long) x << 32) ^ (z & 0xffffffffL);
      var known = chunks.get(key);
      if (known != null) return known;
      var chunk =
          spy(super.getChunkAt(x, z)); // MockBukkit does not implement native plugin tickets.
      doReturn(true).when(chunk).addPluginChunkTicket(any());
      doReturn(true).when(chunk).removePluginChunkTicket(any());
      doReturn(new org.bukkit.block.BlockState[0]).when(chunk).getTileEntities();
      chunks.put(key, chunk);
      return chunk;
    }

    @Override
    public ChunkMock[] getLoadedChunks() {
      return chunks == null ? new ChunkMock[0] : chunks.values().toArray(ChunkMock[]::new);
    }
  }

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    world = new TicketWorld();
    server.addWorld(world);
    plugin = MockBukkit.load(TheMob.class);
    var store = new DungeonStore(plugin.getDataFolder().toPath().resolve("dungeons.yml"));
    record = store.reserve(DungeonStyle.CRYPT, 7, world, 100, 64, 100);
    record.apply(DungeonPlanner.generate(record.style, record.seed));
    record.state = DungeonRecord.State.READY;
    record.altar = null;
    record.altarVersion = 0;
    store.save();
    plugin.reloadPlugin();
    record = plugin.dungeons().get(record.id);
    player = server.addPlayer();
    player.setOp(true);
    player.teleport(new Location(world, .5, 64, .5));
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  void built() {
    plugin.dungeons().altar(player, record.id, false);
    server.getScheduler().performTicks(30);
    assertEquals(AltarLayout.VERSION, record.altarVersion);
  }

  Location altar() {
    return new Location(
        world,
        record.x() + record.altar.x() + .5,
        record.baseY + record.altar.y(),
        record.z() + record.altar.z() + .5);
  }

  void open() {
    player.teleport(altar().clone().add(0, 0, -3));
    var e =
        new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            null,
            altar().getBlock(),
            org.bukkit.block.BlockFace.UP,
            EquipmentSlot.HAND);
    server.getPluginManager().callEvent(e);
    assertEquals(org.bukkit.event.Event.Result.DENY, e.useInteractedBlock());
    assertInstanceOf(
        DungeonAltars.View.class, player.getOpenInventory().getTopInventory().getHolder());
  }

  InventoryClickEvent click(int slot, ClickType type, InventoryAction action) {
    var e =
        new InventoryClickEvent(
            player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, type, action);
    server.getPluginManager().callEvent(e);
    assertTrue(e.isCancelled());
    server.getScheduler().performOneTick();
    return e;
  }

  @ParameterizedTest
  @EnumSource(DungeonStyle.class)
  void everyGeneratedStyleHasAnAltarOnItsLastFloorWithValidPhysicalBlocks(DungeonStyle style) {
    var plan = DungeonPlanner.generate(style, 33);
    var pos = plan.altar();
    assertNotNull(pos);
    assertEquals(1, plan.rooms().stream().filter(r -> r.name().equals("Bosskammer")).count());
    assertEquals(DungeonPlan.floor(plan.levels() - 1) + 1, pos.y());
    assertEquals("minecraft:enchanting_table", plan.block(pos.x(), pos.y(), pos.z()));
    assertEquals(
        4,
        AltarLayout.blocks(pos).values().stream()
            .filter(s -> s.startsWith("minecraft:soul_lantern"))
            .count());
    for (String state : AltarLayout.blocks(pos).values())
      assertNotNull(Bukkit.createBlockData(state));
  }

  @Test
  void upgradesAnExistingBossRoomAndPersistsThePhysicalAltarAcrossReload() {
    assertNull(record.altar);
    built();
    assertEquals(Material.ENCHANTING_TABLE, altar().getBlock().getType());
    var before = record.altar;
    player.teleport(altar().clone().add(0, 0, -3));
    var api = server.getServicesManager().load(TheMobDungeonApi.class);
    assertTrue(api.supportsExitAltars());
    assertTrue(api.atExitAltar(player, record.id));
    plugin.reloadPlugin();
    record = plugin.dungeons().get(record.id);
    assertEquals(before, record.altar);
    assertEquals(1, record.altarVersion);
    assertSame(api, server.getServicesManager().load(TheMobDungeonApi.class));
    assertTrue(api.atExitAltar(player, record.id));
    assertTrue(record.completedRuns.isEmpty());
    long adds = 0, removes = 0;
    for (var chunk : ((TicketWorld) world).chunks.values())
      for (var call : mockingDetails(chunk).getInvocations()) {
        if (call.getMethod().getName().equals("addPluginChunkTicket")) adds++;
        if (call.getMethod().getName().equals("removePluginChunkTicket")) removes++;
      }
    assertTrue(adds > 0);
    assertEquals(adds, removes);
  }

  @Test
  void ordinaryPlayersGetTheMissingAltarWhenEnteringAnOldDungeon() {
    player.setOp(false);
    plugin.dungeons().enter(player, record.id);
    server.getScheduler().performTicks(30);
    assertEquals(1, record.altarVersion);
    assertEquals(Material.ENCHANTING_TABLE, altar().getBlock().getType());
  }

  @Test
  void menuWorksWithoutPkAndCanReturnToTheOriginalEntryLocation() {
    built();
    var original = player.getLocation().clone();
    plugin.dungeons().enter(player, record.id);
    open();
    Inventory menu = player.getOpenInventory().getTopInventory();
    assertEquals(Material.OAK_DOOR, menu.getItem(46).getType());
    click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    assertEquals(original, player.getLocation());
    assertTrue(record.completedRuns.isEmpty());
  }

  @Test
  void providerTargetsArePaginatedAndCannotBeShiftClickedOutOfTheMenu() {
    built();
    var provider = mock(DungeonTravelProvider.class);
    List<DungeonTravelTarget> targets = new ArrayList<>();
    for (int i = 0; i < 50; i++)
      targets.add(
          new DungeonTravelTarget(
              "target" + i, "Ziel " + i, Material.ENDER_PEARL, List.of("Reiseziel")));
    when(provider.destinations(player, record.id)).thenReturn(targets);
    server
        .getServicesManager()
        .register(DungeonTravelProvider.class, provider, plugin, ServicePriority.Normal);
    open();
    click(53, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    var view = (DungeonAltars.View) player.getOpenInventory().getTopInventory().getHolder();
    assertEquals(1, view.page);
    click(0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
    verify(provider).travel(player, record.id, "target45");
    assertEquals(0, player.getInventory().all(Material.ENDER_PEARL).size());
  }

  @Test
  void staleProviderAndRemoteMenuClicksDoNotTeleport() {
    built();
    var provider = mock(DungeonTravelProvider.class);
    when(provider.destinations(player, record.id))
        .thenReturn(List.of(new DungeonTravelTarget("node", "Home", Material.RED_BED, List.of())));
    server
        .getServicesManager()
        .register(DungeonTravelProvider.class, provider, plugin, ServicePriority.Normal);
    open();
    server.getServicesManager().unregister(DungeonTravelProvider.class, provider);
    click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    verify(provider, never()).travel(any(), anyString(), anyString());
    server
        .getServicesManager()
        .register(DungeonTravelProvider.class, provider, plugin, ServicePriority.Normal);
    open();
    var late =
        new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL);
    player.teleport(new Location(world, .5, 64, .5));
    server.getPluginManager().callEvent(late);
    server.getScheduler().performOneTick();
    assertTrue(late.isCancelled());
    verify(provider, never()).travel(any(), anyString(), anyString());
  }

  @Test
  void relocatingAnAltarRequiresAnAdminAndDisablesTheOldCenter() {
    built();
    var previous = altar().getBlock();
    player.setOp(false);
    player.teleport(new Location(world, 150, 85, 150));
    plugin.dungeons().altar(player, record.id, true);
    server.getScheduler().performTicks(5);
    assertEquals(Material.ENCHANTING_TABLE, previous.getType());
    player.setOp(true);
    plugin.dungeons().altar(player, record.id, true);
    server.getScheduler().performTicks(30);
    assertEquals(Material.ENCHANTING_TABLE, world.getBlockAt(150, 85, 150).getType());
    assertEquals(Material.POLISHED_BLACKSTONE_BRICKS, previous.getType());
  }

  @Test
  void closingTheServiceClosesAnOpenAltarMenu() {
    built();
    open();
    plugin.reloadPlugin();
    var top = player.getOpenInventory().getTopInventory();
    assertTrue(top == null || !(top.getHolder() instanceof DungeonAltars.View));
  }

  @Test
  void retrofittingPreservesLootContainersAndCanUseAnotherFreeAltarPosition() {
    var pos = AltarLayout.choose(record.rooms).orElseThrow();
    var block =
        world.getBlockAt(record.x() + pos.x(), record.baseY + pos.y(), record.z() + pos.z());
    block.setType(Material.CHEST);
    var chest = (org.bukkit.block.Chest) block.getState();
    chest.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));
    plugin.dungeons().altar(player, record.id, false);
    server.getScheduler().performTicks(30);
    assertEquals(Material.CHEST, block.getType());
    assertEquals(
        5, ((org.bukkit.block.Chest) block.getState()).getInventory().getItem(0).getAmount());
    assertEquals(0, record.altarVersion);
    player.teleport(new Location(world, 150, 85, 150));
    plugin.dungeons().altar(player, record.id, true);
    server.getScheduler().performTicks(30);
    assertEquals(1, record.altarVersion);
    assertEquals(Material.ENCHANTING_TABLE, world.getBlockAt(150, 85, 150).getType());
    assertEquals(Material.CHEST, block.getType());
  }
}
