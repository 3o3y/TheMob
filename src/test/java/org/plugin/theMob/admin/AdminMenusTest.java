package org.plugin.theMob.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.AdminMenuExtension;

class AdminMenusTest {
  ServerMock server;
  TheMob plugin;
  PlayerMock player;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.load(TheMob.class);
    player = server.addPlayer();
    player.setOp(true);
    server.getScheduler().cancelTasks(plugin);
  }

  @AfterEach
  void close() {
    MockBukkit.unmock();
  }

  Inventory top() {
    return player.getOpenInventory().getTopInventory();
  }

  boolean admin() {
    return top() != null && top().getHolder() instanceof AdminMenuHolder;
  }

  InventoryClickEvent queue(PlayerMock p, int slot, ClickType click) {
    var event =
        new InventoryClickEvent(
            p.getOpenInventory(),
            InventoryType.SlotType.CONTAINER,
            slot,
            click,
            InventoryAction.PICKUP_ALL);
    server.getPluginManager().callEvent(event);
    return event;
  }

  void click(int slot) {
    assertTrue(queue(player, slot, ClickType.LEFT).isCancelled());
    server.getScheduler().performOneTick();
  }

  int slot(String label) {
    for (int i = 0; i < top().getSize(); i++) {
      var item = top().getItem(i);
      if (item != null && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals(label))
        return i;
    }
    fail("Menu entry missing: " + label);
    return -1;
  }

  @Test
  void mobOpensNativeAdminInventoryAndCommandsRemainAvailable() {
    assertTrue(server.dispatchCommand(player, "mob"));
    assertInstanceOf(AdminMenuHolder.class, top().getHolder());
    assertEquals(54, top().getSize());
    for (var item : top().getContents())
      if (item != null) {
        assertFalse(item.getItemMeta().hasCustomModelData());
        assertFalse(
            item.getItemMeta().getDisplayName().chars().anyMatch(c -> c >= 0xe000 && c <= 0xf8ff));
      }
    player.closeInventory();
    server.dispatchCommand(player, "mob abilities status");
    assertFalse(admin());
    server.dispatchCommand(server.getConsoleSender(), "mob");
    assertTrue(server.getConsoleSender().nextMessage() != null);
  }

  @Test
  void ordinaryPlayerCannotOpenOrUseAdminApi() {
    player.setOp(false);
    assertFalse(plugin.api().openAdminMenu(player));
    assertFalse(admin());
  }

  @Test
  void removingLiveSpawnPointRequiresConfirmationAndCancelKeepsIt() {
    plugin.spawns().setAutoSpawnFixedPoint("pig_lvl_1", player.getLocation(), 20, 2);
    plugin.adminMenus().open(player);
    click(14);
    click(22);
    click(0);
    click(33);
    assertTrue(player.getOpenInventory().getTitle().contains("Bestaetigen"));
    assertEquals(1, plugin.spawns().points().size());
    click(33);
    assertEquals(1, plugin.spawns().points().size());
    click(0);
    click(33);
    click(29);
    assertTrue(plugin.spawns().points().isEmpty());
  }

  @Test
  void navigationUsesNativeCatalogAndPaginationWithoutGivingDisplayItems() {
    server.dispatchCommand(player, "mob");
    click(10);
    assertTrue(player.getOpenInventory().getTitle().contains("Mobkatalog"));
    click(52);
    assertTrue(player.getOpenInventory().getTitle().contains("2/"));
    assertEquals(-1, player.getInventory().first(Material.ZOMBIE_HEAD));
    click(49);
    assertTrue(player.getOpenInventory().getTitle().contains("Administration"));
  }

  @Test
  void bottomShiftDoubleAndNumberKeyClicksCannotTakeItemsOrTriggerActions() {
    plugin.adminMenus().open(player);
    Inventory inventory = top();
    for (ClickType click :
        List.of(
            ClickType.SHIFT_LEFT,
            ClickType.NUMBER_KEY,
            ClickType.DOUBLE_CLICK,
            ClickType.DROP,
            ClickType.SWAP_OFFHAND)) {
      assertTrue(queue(player, 10, click).isCancelled());
      server.getScheduler().performOneTick();
      assertSame(inventory, top());
    }
    assertTrue(queue(player, 54, ClickType.SHIFT_LEFT).isCancelled());
    server.getScheduler().performOneTick();
    assertSame(inventory, top());
  }

  @Test
  void deferredActionRechecksPermissionAndCurrentInventory() {
    plugin.adminMenus().open(player);
    Inventory inventory = top();
    queue(player, 12, ClickType.LEFT);
    player.setOp(false);
    server.getScheduler().performOneTick();
    assertSame(inventory, top());
    player.setOp(true);
    queue(player, 12, ClickType.LEFT);
    player.closeInventory();
    server.getScheduler().performOneTick();
    assertFalse(admin());
  }

  @Test
  void foreignViewerCannotExecuteAnotherPlayersHolder() {
    plugin.adminMenus().open(player);
    Inventory shared = top();
    var other = server.addPlayer();
    other.setOp(true);
    other.openInventory(shared);
    assertTrue(queue(other, 12, ClickType.LEFT).isCancelled());
    server.getScheduler().performOneTick();
    assertSame(shared, other.getOpenInventory().getTopInventory());
  }

  @Test
  void lateChatReplyCannotActOnANewMenuSession() {
    plugin.adminMenus().open(player);
    click(10);
    click(0);
    var reply = new AsyncPlayerChatEvent(false, player, "pig", new HashSet<>());
    server.getPluginManager().callEvent(reply);
    assertTrue(reply.isCancelled());
    plugin.adminMenus().open(player);
    Inventory current = top();
    server.getScheduler().performOneTick();
    assertSame(current, top());
  }

  @Test
  void editorIsCompleteButDoesNotExposeRuntimeDungeonDatabase() throws Exception {
    Files.writeString(
        plugin.getDataFolder().toPath().resolve("dungeons.yml"), "private-runtime-data: true\n");
    plugin.adminMenus().configs.files(player, 0);
    assertTrue(slot("config.yml") >= 0);
    for (var item : top().getContents())
      if (item != null)
        assertNotEquals("dungeons.yml", ChatColor.stripColor(item.getItemMeta().getDisplayName()));
    plugin.adminMenus().configs.open(player, "config.yml");
    assertNotNull(plugin.adminMenus().session(player).document);
    assertTrue(slot("integrations") >= 0);
    click(slot("integrations"));
    assertTrue(slot("luckperms") >= 0);
    assertTrue(slot("economyshopgui") >= 0);
  }

  @Test
  void configurationWriteRequiresItsOwnPermissionEvenWithMenuAccess() throws Exception {
    player.setOp(false);
    player.addAttachment(plugin, "themob.admin.menu", true);
    byte[] original = Files.readAllBytes(plugin.getDataFolder().toPath().resolve("config.yml"));
    assertTrue(plugin.api().openAdminMenu(player));
    click(30);
    assertTrue(player.getOpenInventory().getTitle().contains("Administration"));
    assertArrayEquals(
        original, Files.readAllBytes(plugin.getDataFolder().toPath().resolve("config.yml")));
  }

  @Test
  void destructiveActionsOpenConfirmationBeforeRunning() {
    plugin.adminMenus().open(player);
    click(34);
    click(29);
    assertTrue(player.getOpenInventory().getTitle().startsWith("Bestaetigen"));
    click(33);
    assertTrue(player.getOpenInventory().getTitle().contains("Wartung"));
    click(49);
    click(16);
    click(0);
    click(0);
    assertTrue(player.getOpenInventory().getTitle().startsWith("Bestaetigen"));
    assertFalse(plugin.dungeons().busy());
  }

  @Test
  void apiExtensionsAreBoundToLiveRegistrationAndPermission() {
    var addon = MockBukkit.createMockPlugin("MenuAddon");
    var called = new AtomicInteger();
    var extension =
        new AdminMenuExtension() {
          public String id() {
            return "example";
          }

          public String title() {
            return "Example extension";
          }

          public Material icon() {
            return Material.BOOK;
          }

          public String permission() {
            return "example.admin";
          }

          public void open(Player p) {
            called.incrementAndGet();
          }
        };
    Bukkit.getServicesManager()
        .register(AdminMenuExtension.class, extension, addon, ServicePriority.Normal);
    plugin.adminMenus().integrations(player, 0);
    int index = slot("Example extension");
    queue(player, index, ClickType.LEFT);
    Bukkit.getServicesManager().unregister(AdminMenuExtension.class, extension);
    server.getScheduler().performOneTick();
    assertEquals(0, called.get());
    Bukkit.getServicesManager()
        .register(AdminMenuExtension.class, extension, addon, ServicePriority.Normal);
    plugin.adminMenus().integrations(player, 0);
    click(slot("Example extension"));
    assertEquals(1, called.get());
  }

  @Test
  void reloadClosesDraftSessionsButKeepsApiFacade() {
    var api = plugin.api();
    api.openAdminMenu(player);
    var old = plugin.adminMenus();
    plugin.reloadPlugin();
    assertSame(api, plugin.api());
    assertNotSame(old, plugin.adminMenus());
    assertFalse(admin());
    assertTrue(api.openAdminMenu(player));
  }
}
