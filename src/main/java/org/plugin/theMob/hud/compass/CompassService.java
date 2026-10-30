package org.plugin.theMob.hud.compass;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.hud.compass.betterhud.BetterHudCompassDisplay;

/** Owns standalone compass preferences, or yields the single shared compass to CoinOverlay. */
public final class CompassService
    implements CommandExecutor, TabCompleter, Listener, AutoCloseable {
  private final TheMob plugin;
  private final CompassPreferences preferences;
  private final Set<UUID> hiddenByHud = ConcurrentHashMap.newKeySet();
  private final ExecutorService storage =
      Executors.newSingleThreadExecutor(
          r -> {
            var thread = new Thread(r, "TheMob-Compass-Storage");
            thread.setDaemon(true);
            return thread;
          });
  private final boolean delegated;
  private final boolean sharedCoinOverlayPack;
  private ResourcePackDelivery delivery;
  private CompassDisplay display;
  private volatile boolean closed;

  public CompassService(TheMob plugin) throws IOException {
    this.plugin = plugin;
    preferences =
        new CompassPreferences(plugin.getDataFolder().toPath().resolve("compass.properties"));
    Plugin renderer = Bukkit.getPluginManager().getPlugin("BetterHud");
    Plugin coins = Bukkit.getPluginManager().getPlugin("CoinOverlay");
    sharedCoinOverlayPack = CoinOverlayPack.active(coins);
    boolean requested =
        !"legacy"
            .equalsIgnoreCase(
                plugin.getConfig().getString("navigation-hud.compass-style", "coinoverlay"));
    boolean available = requested && renderer != null && renderer.isEnabled();
    delegated = available && usesCoinOverlayCompass(coins);
    if (available && !delegated) {
      try {
        delivery = new ResourcePackDelivery(this);
        display = new BetterHudCompassDisplay(this, renderer);
        Bukkit.getPluginManager().registerEvents(delivery, plugin);
        Bukkit.getScheduler()
            .runTaskLater(plugin, () -> display.reload(Bukkit.getConsoleSender()), 40L);
        plugin.getLogger().info("CoinOverlay-Kompass mit BetterHud aktiviert.");
      } catch (IOException | RuntimeException | LinkageError unavailable) {
        delivery = null;
        display = null;
        plugin.getLogger().warning("BetterHud-API nicht passend; Textkompass bleibt aktiv.");
      }
    } else {
      delivery = null;
      display = null;
      if (delegated)
        plugin
            .getLogger()
            .info("Kompass wird gemeinsam mit CoinOverlay verwendet; keine doppelte Anzeige.");
      else if (requested)
        plugin
            .getLogger()
            .warning(
                "Der CoinOverlay-Kompass benoetigt BetterHud 2.2.0-SNAPSHOT-449 und dessen"
                    + " Ressourcenpaket. Bis dahin bleibt der Textkompass aktiv.");
    }
    Bukkit.getPluginManager().registerEvents(this, plugin);
    PluginCommand command = plugin.getCommand("mobkompass");
    if (command != null) {
      command.setExecutor(this);
      command.setTabCompleter(this);
    }
    for (Player player : Bukkit.getOnlinePlayers()) joined(player);
  }

  static boolean usesCoinOverlayCompass(Plugin plugin) {
    return plugin != null
        && plugin.isEnabled()
        && plugin.getDescription().getCommands().containsKey("kompass")
        && "RESOURCE_HUD"
            .equalsIgnoreCase(plugin.getConfig().getString("overlay.mode", "RESOURCE_HUD"));
  }

  public TheMob plugin() {
    return plugin;
  }

  public boolean sharedCoinOverlayPack() {
    return sharedCoinOverlayPack;
  }

  public boolean resourceCompass() {
    return delegated || display != null && display.available();
  }

  public boolean resourceCompass(UUID player) {
    return resourceCompass() && visible(player);
  }

  public boolean visible(UUID id) {
    return !closed
        && preferences.visible(id)
        && !hiddenByHud.contains(id)
        && (delivery == null || delivery.ready(id));
  }

  public boolean personalVisible(UUID id) {
    return preferences.visible(id);
  }

  public void hudVisible(UUID id, boolean visible) {
    boolean changed = visible ? hiddenByHud.remove(id) : hiddenByHud.add(id);
    if (changed) refresh(id);
  }

  public void refresh(UUID id) {
    if (!closed && display != null) display.refresh(id);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    joined(event.getPlayer());
  }

  private void joined(Player player) {
    if (delivery != null) delivery.joined(player);
    refresh(player.getUniqueId());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID id = event.getPlayer().getUniqueId();
    hiddenByHud.remove(id);
    if (display != null) display.forget(id);
    if (delivery != null) delivery.quit(event.getPlayer());
  }

  private void change(CommandSender sender, UUID id, CompassPreferences.Action action) {
    storage.execute(
        () -> {
          try {
            boolean enabled =
                id == null
                    ? preferences.changeServer(action)
                    : preferences.changePlayer(id, action);
            if (!closed && plugin.isEnabled())
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        if (closed) return;
                        if (id == null)
                          Bukkit.getOnlinePlayers().forEach(p -> refresh(p.getUniqueId()));
                        else refresh(id);
                        info(
                            sender,
                            "Kompass "
                                + (id == null ? "serverweit " : "persoenlich ")
                                + (enabled ? "eingeschaltet." : "ausgeschaltet.")
                                + (enabled && !preferences.serverEnabled()
                                    ? " Die Serversperre bleibt aktiv."
                                    : ""));
                      });
          } catch (IOException exception) {
            plugin
                .getLogger()
                .log(
                    Level.WARNING,
                    "Kompass-Einstellung konnte nicht gespeichert werden.",
                    exception);
            if (!closed && plugin.isEnabled())
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () ->
                          error(
                              sender,
                              "Speichern fehlgeschlagen; bisherige Einstellung bleibt erhalten."));
          } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
            // Shutdown can overtake the completion of an atomic storage update.
          }
        });
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (closed) return true;
    if (delegated) {
      String target =
          args.length == 1 && args[0].equalsIgnoreCase("reload")
              ? "coinoverlay:coinsadmin hud reload"
              : args.length == 1 && args[0].equalsIgnoreCase("pack")
                  ? "coinoverlay:coins pack"
                  : "coinoverlay:kompass " + String.join(" ", args);
      if (!Bukkit.dispatchCommand(sender, target))
        error(sender, "CoinOverlay-Kompassbefehl ist nicht verfuegbar.");
      return true;
    }
    try {
      if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
        if (!admin(sender))
          throw new IllegalArgumentException("Dafuer brauchst du themob.compass.admin.");
        if (display != null) display.reload(sender);
        else info(sender, "BetterHud ist nicht aktiv; der Textkompass wird verwendet.");
        return true;
      }
      if (args.length == 1 && args[0].equalsIgnoreCase("pack")) {
        if (!sender.hasPermission("themob.compass"))
          throw new IllegalArgumentException("Keine Kompass-Berechtigung.");
        if (!(sender instanceof Player player))
          throw new IllegalArgumentException("Nur Spieler koennen das Paket anfordern.");
        if (delivery != null) delivery.resend(player);
        else info(sender, "BetterHud ist nicht aktiv; kein Kompass-Paket eingerichtet.");
        return true;
      }
      CompassRequest request = CompassRequest.parse(args);
      if (request.scope() != CompassRequest.Scope.SELF && !admin(sender))
        throw new IllegalArgumentException("Dafuer brauchst du themob.compass.admin.");
      if (request.scope() == CompassRequest.Scope.SERVER) {
        if (request.operation() == CompassRequest.Operation.STATUS)
          info(sender, "Kompass serverweit " + state(preferences.serverEnabled()) + ".");
        else change(sender, null, request.operation().action());
        return true;
      }
      UUID id;
      if (request.scope() == CompassRequest.Scope.SELF) {
        if (!sender.hasPermission("themob.compass"))
          throw new IllegalArgumentException("Keine Kompass-Berechtigung.");
        if (!(sender instanceof Player player))
          throw new IllegalArgumentException("Konsole: /mobkompass server oder spieler NAME.");
        id = player.getUniqueId();
      } else {
        Player online = Bukkit.getPlayerExact(request.target());
        OfflinePlayer known =
            online == null
                ? java.util.Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> request.target().equalsIgnoreCase(p.getName()))
                    .findFirst()
                    .orElse(null)
                : online;
        if (known == null)
          throw new IllegalArgumentException(
              "Spieler unbekannt. Er muss den Server zuerst betreten haben.");
        id = known.getUniqueId();
      }
      if (request.operation() == CompassRequest.Operation.STATUS)
        info(
            sender,
            "Kompass persoenlich "
                + state(preferences.personalEnabled(id))
                + ", serverweit "
                + state(preferences.serverEnabled())
                + ".");
      else change(sender, id, request.operation().action());
    } catch (IllegalArgumentException exception) {
      error(sender, exception.getMessage());
    }
    return true;
  }

  private boolean admin(CommandSender sender) {
    return sender.hasPermission("themob.compass.admin") || sender.hasPermission("themob.admin");
  }

  private String state(boolean enabled) {
    return enabled ? "eingeschaltet" : "ausgeschaltet";
  }

  public static void info(CommandSender sender, String text) {
    sender.sendMessage("§e[TheMob] " + text);
  }

  public static void error(CommandSender sender, String text) {
    sender.sendMessage("§c[TheMob] " + text);
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission("themob.compass") && !admin(sender)) return List.of();
    if (args.length == 2
        && (args[0].equalsIgnoreCase("spieler") || args[0].equalsIgnoreCase("player"))
        && admin(sender))
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
          .sorted()
          .toList();
    List<String> choices =
        args.length <= 1
            ? admin(sender)
                ? List.of("an", "aus", "status", "server", "spieler", "pack", "reload")
                : List.of("an", "aus", "status", "pack")
            : admin(sender)
                    && (args.length == 2 && args[0].equalsIgnoreCase("server")
                        || args.length == 3
                            && (args[0].equalsIgnoreCase("spieler")
                                || args[0].equalsIgnoreCase("player")))
                ? List.of("an", "aus", "status")
                : List.of();
    String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
    return choices.stream().filter(n -> n.startsWith(prefix)).toList();
  }

  @Override
  public void close() {
    closed = true;
    if (display != null) display.close();
    HandlerList.unregisterAll(this);
    if (delivery != null) HandlerList.unregisterAll(delivery);
    storage.shutdown();
    try {
      if (!storage.awaitTermination(2, TimeUnit.SECONDS))
        plugin.getLogger().warning("Kompass-Speicherauftrag wird noch abgeschlossen.");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
    hiddenByHud.clear();
  }
}
