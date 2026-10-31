package org.plugin.theMob.hud.compass;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.plugin.theMob.TheMob;

/** Sends one optional download offer per join and waits for the matching client load result. */
final class ResourcePackDelivery implements Listener {
  private final TheMob plugin;
  private final CompassService owner;
  private final boolean enabled;
  private final boolean shared;
  private final long delay;
  private final PackOffer offer;
  private static final java.util.Map<TheMob, PackReadiness> READINESS =
      new java.util.WeakHashMap<>();
  private final PackReadiness readiness;

  ResourcePackDelivery(CompassService owner) {
    this(owner, org.plugin.theMob.compat.PackAccess::nativePack);
  }

  ResourcePackDelivery(CompassService owner, java.util.function.Supplier<?> serverPack) {
    this.owner = owner;
    this.plugin = owner.plugin();
    synchronized (READINESS) {
      readiness = READINESS.computeIfAbsent(plugin, ignored -> new PackReadiness());
    }
    shared = owner.sharedCoinOverlayPack();
    FileConfiguration config =
        shared
            ? Bukkit.getPluginManager().getPlugin("CoinOverlay").getConfig()
            : plugin.getConfig();
    String path = shared ? "resource-pack." : "navigation-hud.resource-pack.";
    enabled = config.getBoolean(path + "offer-on-join", true);
    delay = Math.max(1L, config.getLong(path + "join-delay-ticks", 30L));
    PackOffer selected = null;
    try {
      var nativePack = org.plugin.theMob.compat.PackAccess.inspect(serverPack.get());
      String url = config.getString(path + "url", "").strip();
      String hash = config.getString(path + "sha1", "").strip();
      if (nativePack != null
          && (url.isEmpty()
              || (url.equals(nativePack.url())
                  && (hash.isEmpty() || hash.equalsIgnoreCase(nativePack.hash()))))) {
        selected =
            new PackOffer(
                nativePack.id(),
                nativePack.url(),
                nativePack.hash(),
                nativePack.prompt(),
                false,
                true);
      } else if (!url.isEmpty()) {
        selected =
            shared
                ? CoinOverlayPack.offer(
                    url,
                    hash,
                    config.getString(path + "prompt", "Server-Ressourcenpaket laden?"),
                    false)
                : new PackOffer(
                    null,
                    url,
                    hash,
                    config.getString(
                        path + "prompt", "Server-Ressourcenpaket fuer den Kompass laden?"),
                    false,
                    false);
      } else if (enabled) {
        plugin
            .getLogger()
            .warning(
                "Ressourcenpaket-URL fehlt. CoinOverlay-HUD-Paket in server.properties"
                    + (shared
                        ? " oder CoinOverlay/config.yml konfigurieren."
                        : " oder TheMob/config.yml konfigurieren."));
      }
    } catch (IllegalArgumentException exception) {
      plugin.getLogger().warning("Paketanfrage nicht eingerichtet: " + exception.getMessage());
    }
    offer = selected;
  }

  void joined(Player player) {
    if (offer == null) {
      return;
    }
    readiness.expect(player.getUniqueId(), offer.id(), offer.nativeOffer() || shared);
    // Native offers are already sent during the server login/configuration handshake.
    if (enabled && !offer.nativeOffer() && !shared) {
      Bukkit.getScheduler()
          .runTaskLater(
              plugin,
              () -> {
                if (Bukkit.getPlayer(player.getUniqueId()) == player) {
                  send(player);
                }
              },
              delay);
    }
  }

  void resend(Player player) {
    if (shared) {
      if (!Bukkit.dispatchCommand(player, "coinoverlay:coins pack"))
        CompassService.error(player, "CoinOverlay-Paketbefehl ist nicht verfuegbar.");
      return;
    }
    if (offer == null || !enabled) {
      CompassService.error(player, "Das Server-Ressourcenpaket ist noch nicht eingerichtet.");
      return;
    }
    send(player);
  }

  private void send(Player player) {
    readiness.expect(player.getUniqueId(), offer.id(), false);
    owner.refresh(player.getUniqueId());
    try {
      org.plugin.theMob.compat.PackAccess.send(
          player, offer.id(), offer.url(), offer.hashBytes(), offer.prompt(), offer.required());
    } catch (IllegalArgumentException exception) {
      plugin
          .getLogger()
          .warning(
              "Paketanfrage fuer "
                  + player.getName()
                  + " fehlgeschlagen: "
                  + exception.getMessage());
      CompassService.error(player, "Das Server-Ressourcenpaket konnte nicht angeboten werden.");
    }
  }

  @EventHandler
  public void onPackStatus(PlayerResourcePackStatusEvent event) {
    var sourceId = org.plugin.theMob.compat.PackAccess.id(event);
    if (offer == null || sourceId != null && !offer.id().equals(sourceId)) {
      return;
    }
    Player player = event.getPlayer();
    // A native pack can finish loading before PlayerJoinEvent fires.
    if (offer.nativeOffer() || shared) {
      readiness.expect(player.getUniqueId(), offer.id(), true);
    }
    PackReadiness.Phase phase =
        switch (event.getStatus()) {
          case SUCCESSFULLY_LOADED -> PackReadiness.Phase.LOADED;
          case ACCEPTED -> PackReadiness.Phase.ACCEPTED;
          case DOWNLOADED -> PackReadiness.Phase.DOWNLOADED;
          case DECLINED -> PackReadiness.Phase.DECLINED;
          default -> PackReadiness.Phase.FAILED;
        };
    if (!readiness.update(player.getUniqueId(), offer.id(), phase)) {
      return;
    }
    owner.refresh(player.getUniqueId());
    if (!player.isOnline()) {
      return;
    }
    if (phase == PackReadiness.Phase.DECLINED) {
      CompassService.info(
          player,
          "Ohne Ressourcenpaket wird der Textkompass verwendet. Erneut laden: /mobkompass"
              + " pack");
    } else if (phase == PackReadiness.Phase.FAILED) {
      CompassService.error(
          player,
          "Das Server-Ressourcenpaket konnte nicht geladen werden. Erneut versuchen: /mobkompass"
              + " pack");
    }
  }

  boolean ready(Player player) {
    return ready(player.getUniqueId());
  }

  boolean ready(java.util.UUID playerId) {
    return readiness.loaded(playerId);
  }

  void quit(Player player) {
    readiness.forget(player.getUniqueId());
  }

  String status() {
    if (!enabled) {
      return "Automatische Paketanfrage deaktiviert. Ohne bestaetigtes Laden wird der Textkompass"
          + " verwendet.";
    }
    if (shared) return "Gemeinsames CoinOverlay-HUD-Paket; Download und Angebot durch CoinOverlay.";
    if (offer == null) {
      return "Download-URL/SHA-1 fehlen oder sind ungueltig. HUD bleibt bis zum Laden"
          + " ausgeblendet.";
    }
    return offer.nativeOffer()
        ? "Paketanfrage beim Join ueber server.properties; keine doppelte Plugin-Anfrage."
        : "Paketanfrage beim Join aktiv; HUD erscheint nach erfolgreichem Laden.";
  }
}
