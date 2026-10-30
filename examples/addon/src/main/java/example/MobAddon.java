package example;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.api.AdminMenuExtension;
import org.plugin.theMob.api.TheMobApi;
import org.plugin.theMob.api.TheMobItemProvider;
import org.plugin.theMob.api.dungeon.DungeonCompletedEvent;
import org.plugin.theMob.api.dungeon.TheMobDungeonApi;
import org.plugin.theMob.api.event.TheMobDeathEvent;

public class MobAddon extends JavaPlugin implements Listener {
  private TheMobApi api;
  private TheMobDungeonApi dungeons;
  private NamespacedKey kills, completed;

  @Override
  public void onEnable() {
    api = Bukkit.getServicesManager().load(TheMobApi.class);
    if (api == null || api.apiVersion() != 1) {
      getLogger().severe("TheMob API 1 fehlt");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    dungeons = Bukkit.getServicesManager().load(TheMobDungeonApi.class);
    if (dungeons == null || dungeons.apiVersion() != 1) {
      getLogger().severe("TheMob Dungeon API 1 fehlt; TheMob aktualisieren.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    kills = new NamespacedKey(this, "example_kills");
    completed = new NamespacedKey(this, "example_dungeons");
    Bukkit.getPluginManager().registerEvents(this, this);
    if (api.supportsAdminMenu()) {
      Bukkit.getServicesManager()
          .register(
              AdminMenuExtension.class,
              new AdminMenuExtension() {
                public String id() {
                  return "example_progress";
                }

                public String title() {
                  return "Beispiel: eigener Fortschritt";
                }

                public Material icon() {
                  return Material.BOOK;
                }

                public String permission() {
                  return "themobexample.admin";
                }

                public void open(Player administrator) {
                  onCommand(
                      administrator,
                      getCommand("themobexample"),
                      "themobexample",
                      new String[] {"status"});
                }
              },
              this,
              org.bukkit.plugin.ServicePriority.Normal);
      Bukkit.getServicesManager()
          .register(
              TheMobItemProvider.class,
              new TheMobItemProvider() {
                public String id() {
                  return "themobexample";
                }

                public java.util.Optional<org.bukkit.inventory.ItemStack> create(String itemId) {
                  if (!itemId.equals("token")) return java.util.Optional.empty();
                  var item = new org.bukkit.inventory.ItemStack(Material.PAPER);
                  var meta = item.getItemMeta();
                  meta.setDisplayName("§eBeispielmarke");
                  meta.getPersistentDataContainer()
                      .set(
                          new NamespacedKey(MobAddon.this, "token"),
                          PersistentDataType.BYTE,
                          (byte) 1);
                  item.setItemMeta(meta);
                  return java.util.Optional.of(item);
                }
              },
              this,
              org.bukkit.plugin.ServicePriority.Normal);
    }
    getLogger()
        .info(
            "Beispiel verbunden: "
                + api.definitions().size()
                + " Definitionen. /themobexample status");
  }

  @Override
  public void onDisable() {
    Bukkit.getServicesManager().unregisterAll(this);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onKill(TheMobDeathEvent event) {
    Player player = event.getKiller();
    if (player == null
        || event.getMob().minion()
        || player.hasMetadata("NPC")
        || event.getEntity().hasMetadata("NPC")) return;
    increment(player, kills);
    // Custom objective adapter goes HERE: event.getMob().definitionId(), boss(), phaseId().
    // Register one native objective in your quest/jobs plugin, then forward progress to its API.
    // Do not also count Bukkit EntityDeathEvent or pay a second built-in Jobs/BattlePass reward.
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDungeon(DungeonCompletedEvent event) {
    for (Player player : event.getParticipants()) increment(player, completed);
    // Forward event.getRunId(), event.getDungeon().id() and participants to your own objective.
    // Do not call complete() in response to DungeonCompletedEvent (that would recurse).
  }

  private void increment(Player player, NamespacedKey key) {
    long old =
        Math.max(
            0, player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L));
    player
        .getPersistentDataContainer()
        .set(key, PersistentDataType.LONG, old == Long.MAX_VALUE ? old : old + 1);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission("themobexample.admin")) {
      sender.sendMessage("§cDafuer fehlt dir themobexample.admin.");
      return true;
    }
    if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("status")) {
      sender.sendMessage(
          "§aTheMob API "
              + api.apiVersion()
              + ": "
              + api.definitions().size()
              + " Definitionen, "
              + dungeons.regions().size()
              + " Dungeonbereiche.");
      if (sender instanceof Player player) {
        sender.sendMessage(
            "§7Nur Beispielzaehler: Kills="
                + player
                    .getPersistentDataContainer()
                    .getOrDefault(kills, PersistentDataType.LONG, 0L)
                + ", Dungeons="
                + player
                    .getPersistentDataContainer()
                    .getOrDefault(completed, PersistentDataType.LONG, 0L));
      }
      return true;
    }
    if (args.length == 2 && args[0].equalsIgnoreCase("spawn") && sender instanceof Player player) {
      var spawned = api.spawn(args[1], player.getLocation());
      sender.sendMessage(
          spawned.isPresent()
              ? "§aTestmob erzeugt."
              : "§eAbgewiesen: ID, Limit, Cooldown, Region oder PreSpawn-Event.");
      return true;
    }
    sender.sendMessage("§e/themobexample status | /themobexample spawn <Mob-ID>");
    return true;
  }
}
