package org.plugin.theMob.boss;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class ArenaWeatherController implements Listener {
  private final Plugin plugin;
  private final Set<UUID> affectedPlayers = new HashSet<>();

  public ArenaWeatherController(Plugin plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  public void applyToPlayer(Player p, String weather, String time) {
    if (p != null && p.isOnline()) {
      if (weather != null) {
        String var4 = weather.toUpperCase();
        switch (var4) {
          case "CLEAR":
            p.setPlayerWeather(WeatherType.CLEAR);
            break;
          case "RAIN":
          case "THUNDER":
            p.setPlayerWeather(WeatherType.DOWNFALL);
            break;
          case "NONE":
            p.resetPlayerWeather();
        }
      }

      if (time != null) {
        String var6 = time.toUpperCase();
        switch (var6) {
          case "DAY":
            p.setPlayerTime(1000L, false);
            break;
          case "NOON":
            p.setPlayerTime(6000L, false);
            break;
          case "SUNSET":
            p.setPlayerTime(12000L, false);
            break;
          case "NIGHT":
            p.setPlayerTime(13000L, false);
            break;
          case "MIDNIGHT":
            p.setPlayerTime(18000L, false);
            break;
          case "NONE":
            p.resetPlayerTime();
        }
      }

      this.affectedPlayers.add(p.getUniqueId());
    }
  }

  public void reset(Player p) {
    if (p != null) {
      p.resetPlayerWeather();
      p.resetPlayerTime();
      this.affectedPlayers.remove(p.getUniqueId());
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    this.reset(e.getPlayer());
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent e) {
    this.reset(e.getPlayer());
  }

  @EventHandler
  public void onMove(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (this.affectedPlayers.contains(p.getUniqueId())) {
      if (e.getTo() != null) {
        if (!e.getFrom().getChunk().equals(e.getTo().getChunk())) {
          if (!this.isInArena(p)) {
            this.reset(p);
          }
        }
      }
    }
  }

  private boolean isInArena(Player p) {
    return true;
  }

  public void resetAll() {
    for (UUID id : new HashSet<>(this.affectedPlayers)) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) {
        this.reset(p);
      }
    }

    this.affectedPlayers.clear();
  }
}
