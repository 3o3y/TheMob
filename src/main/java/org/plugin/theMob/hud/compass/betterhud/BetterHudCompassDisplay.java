package org.plugin.theMob.hud.compass.betterhud;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.bukkit.event.HudPlayerJoinEvent;
import kr.toxicity.hud.api.player.HudPlayer;
import kr.toxicity.hud.api.plugin.ReloadState;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.hud.compass.*;

/** Identical CoinOverlay ribbon, palette and arrow; BetterHud retains yaw and geometry. */
public final class BetterHudCompassDisplay implements CompassDisplay, Listener {
  private static final String MARKER = "themob_compass_marker";
  private static BetterHud registeredRenderer;
  private static volatile BetterHudCompassDisplay current;
  private final CompassService owner;
  private final BetterHud renderer;
  private final Plugin rendererPlugin;
  private final long animationStart = System.nanoTime();
  private final AtomicBoolean reloading = new AtomicBoolean();
  private final Set<UUID> addedCompasses = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final Set<UUID> addedMarkers = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final java.util.Map<UUID, kr.toxicity.hud.api.configuration.HudComponentSupplier<?>>
      previousDefaults = new java.util.concurrent.ConcurrentHashMap<>();
  private volatile boolean closed;

  public BetterHudCompassDisplay(CompassService owner, Plugin rendererPlugin) throws IOException {
    this.owner = owner;
    this.rendererPlugin = rendererPlugin;
    this.renderer = BetterHudAPI.inst();
    CompassFiles.install(rendererPlugin.getDataFolder().toPath(), owner.plugin()::getResource);
    CompassHeaderLayout.position(rendererPlugin.getDataFolder().toPath());
    Bukkit.getPluginManager().registerEvents(this, owner.plugin());
    current = this;
    // Register once per renderer, so /mob reload does not retain retired services in callbacks.
    synchronized (BetterHudCompassDisplay.class) {
      if (registeredRenderer != renderer) {
        renderer.addReloadStartTask(
            () -> {
              BetterHudCompassDisplay active = current;
              if (active != null) active.prepareHeader();
            });
        renderer.addReloadEndTask(
            result -> {
              BetterHudCompassDisplay active = current;
              if (active != null && result instanceof ReloadState.Success success) {
                active.correctHeader(success.resourcePack());
                active.refreshAll();
              }
            });
        registeredRenderer = renderer;
      }
    }
  }

  private void prepareHeader() {
    try {
      CompassHeaderLayout.position(rendererPlugin.getDataFolder().toPath());
    } catch (IOException failure) {
      owner
          .plugin()
          .getLogger()
          .log(
              Level.SEVERE,
              "Kompassposition oberhalb der Bossbar konnte nicht vorbereitet werden.",
              failure);
    }
  }

  private void correctHeader(java.util.Map<String, byte[]> pack) {
    try {
      int lines =
          org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                  rendererPlugin.getDataFolder().toPath().resolve("config.yml").toFile())
              .getInt("bossbar-line", 2);
      CompassHeaderLayout.correctPack(pack, lines);
    } catch (IOException | RuntimeException failure) {
      owner
          .plugin()
          .getLogger()
          .log(
              Level.SEVERE,
              "Kompass-Kopfbereich im gemeinsamen Paket konnte nicht korrigiert werden.",
              failure);
    }
  }

  @Override
  public boolean available() {
    return !closed && rendererPlugin.isEnabled();
  }

  @EventHandler
  public void onHudJoin(HudPlayerJoinEvent event) {
    if (available()) sync(event.player());
  }

  @Override
  public void refresh(UUID player) {
    if (!available() || renderer.isOnReload()) return;
    HudPlayer target = renderer.getPlayerManager().getHudPlayer(player);
    if (target != null) sync(target);
  }

  private void refreshAll() {
    if (!available()) return;
    renderer.getPlayerManager().getAllHudPlayer().forEach(this::sync);
  }

  @Override
  public void forget(UUID player) {
    previousDefaults.remove(player);
    addedMarkers.remove(player);
    addedCompasses.remove(player);
  }

  private void sync(HudPlayer target) {
    if (!available()) return;
    target
        .getHudObjects()
        .entrySet()
        .removeIf(
            entry -> {
              var supplier = entry.getValue();
              if (supplier.parent() instanceof kr.toxicity.hud.api.compass.Compass
                  && supplier.parent().getName().equals("default_compass")) {
                previousDefaults.put(target.uuid(), supplier);
                return true;
              }
              return false;
            });
    var compass = renderer.getCompassManager().getCompass("themob_compass");
    boolean visible = owner.visible(target.uuid());
    if (compass != null && visible && !target.getHudObjects().containsKey(compass.identifier())) {
      if (compass.add(target)) addedCompasses.add(target.uuid());
    }
    RainbowCompass.install(
        target.getHudObjects(),
        () -> (System.nanoTime() - animationStart) / 1_000_000_000.0,
        this::available,
        () -> owner.visible(target.uuid()),
        "themob_compass");
    var marker = renderer.getHudManager().getHud(MARKER);
    if (marker != null) {
      if (compass != null && visible) {
        if (!target.getHudObjects().containsKey(marker.identifier()) && marker.add(target))
          addedMarkers.add(target.uuid());
      } else if (addedMarkers.remove(target.uuid())) marker.remove(target);
    }
  }

  @Override
  public void reload(CommandSender sender) {
    if (owner.sharedCoinOverlayPack()) {
      if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "coinoverlay:coinsadmin hud reload"))
        CompassService.info(
            sender, "CoinOverlay baut das gemeinsame HUD-Paket einschliesslich Kompass.");
      else CompassService.error(sender, "CoinOverlay-Paketaufbau ist nicht verfuegbar.");
      return;
    }
    if (!available() || !reloading.compareAndSet(false, true)) {
      CompassService.info(sender, "Kompass-Ressourcen werden bereits aufgebaut.");
      return;
    }
    waitForIdle(sender, 0);
  }

  private void waitForIdle(CommandSender sender, int attempt) {
    if (!available() || !owner.plugin().isEnabled()) {
      reloading.set(false);
      return;
    }
    if (renderer.isOnReload()) {
      if (attempt >= 30) {
        reloading.set(false);
        CompassService.error(
            sender, "BetterHud ist beschaeftigt. Spaeter /mobkompass reload ausfuehren.");
      } else
        Bukkit.getScheduler()
            .runTaskLater(owner.plugin(), () -> waitForIdle(sender, attempt + 1), 40L);
      return;
    }
    Bukkit.getScheduler()
        .runTaskAsynchronously(
            owner.plugin(),
            () -> {
              try {
                var result = renderer.reload();
                if (result instanceof ReloadState.Failure failure)
                  throw new IllegalStateException(
                      "BetterHud-Kompass konnte nicht gebaut werden.", failure.throwable());
                if (result instanceof ReloadState.Success success) {
                  var exported =
                      HudPackExport.write(
                          success.resourcePack(),
                          owner.plugin().getDataFolder().toPath().resolve("resource-pack"));
                  main(
                      () -> {
                        refreshAll();
                        CompassService.info(
                            sender, "Kompass-Paket: " + exported.zip().toAbsolutePath());
                        CompassService.info(
                            sender,
                            "SHA-1: "
                                + exported.sha1()
                                + ". ZIP hochladen und URL/SHA-1 in server.properties oder"
                                + " navigation-hud.resource-pack eintragen.");
                      });
                }
              } catch (Exception | LinkageError failure) {
                owner
                    .plugin()
                    .getLogger()
                    .log(
                        Level.SEVERE,
                        "CoinOverlay-Kompass konnte nicht vorbereitet werden.",
                        failure);
                main(
                    () ->
                        CompassService.error(
                            sender, "Kompass-Aufbau fehlgeschlagen; Serverkonsole pruefen."));
              } finally {
                reloading.set(false);
              }
            });
  }

  private void main(Runnable action) {
    if (!available() || !owner.plugin().isEnabled()) return;
    try {
      Bukkit.getScheduler()
          .runTask(
              owner.plugin(),
              () -> {
                if (available()) action.run();
              });
    } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
    }
  }

  @Override
  public void close() {
    closed = true;
    if (current == this) current = null;
    HandlerList.unregisterAll(this);
    if (rendererPlugin.isEnabled()) {
      var compass = renderer.getCompassManager().getCompass("themob_compass");
      var marker = renderer.getHudManager().getHud(MARKER);
      for (HudPlayer target : renderer.getPlayerManager().getAllHudPlayer()) {
        RainbowCompass.restore(target.getHudObjects());
        if (compass != null && addedCompasses.contains(target.uuid())) compass.remove(target);
        if (marker != null && addedMarkers.contains(target.uuid())) marker.remove(target);
        var previous = previousDefaults.get(target.uuid());
        if (previous != null)
          target.getHudObjects().putIfAbsent(previous.parent().identifier(), previous);
      }
    }
    addedCompasses.clear();
    addedMarkers.clear();
    previousDefaults.clear();
  }
}
