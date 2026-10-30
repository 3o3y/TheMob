package org.plugin.theMob.boss.world;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

public final class BossWorldEffectController implements Listener {
  private record Effect(LivingEntity boss, double radiusSquared, String weather, String time) {}

  private final Map<UUID, Effect> effects = new HashMap<>();
  private final Set<UUID> affected = new HashSet<>();

  public BossWorldEffectController(Plugin plugin) {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 10);
  }

  public void apply(LivingEntity boss, double radius, String weather, String time) {
    if (boss == null) return;
    if (!boss.isValid() || boss.isDead()) {
      reset(boss);
      return;
    }
    effects.put(boss.getUniqueId(), new Effect(boss, Math.max(0, radius * radius), weather, time));
    tick();
  }

  public void reset(LivingEntity boss) {
    if (boss != null) effects.remove(boss.getUniqueId());
    tick();
  }

  public void tick() {
    effects.values().removeIf(effect -> !effect.boss().isValid() || effect.boss().isDead());
    for (Player player : Bukkit.getOnlinePlayers()) {
      Effect selected = null;
      double nearest = Double.MAX_VALUE;
      for (Effect effect : effects.values())
        if (player.getWorld().equals(effect.boss().getWorld())) {
          double distance = player.getLocation().distanceSquared(effect.boss().getLocation());
          if (distance <= effect.radiusSquared() && distance < nearest) {
            nearest = distance;
            selected = effect;
          }
        }
      if (selected == null) {
        if (affected.remove(player.getUniqueId())) resetPlayer(player);
        continue;
      }
      affected.add(player.getUniqueId());
      String weather = String.valueOf(selected.weather()).toUpperCase(Locale.ROOT);
      if (weather.equals("RAIN") || weather.equals("THUNDER"))
        player.setPlayerWeather(WeatherType.DOWNFALL);
      else if (weather.equals("CLEAR")) player.setPlayerWeather(WeatherType.CLEAR);
      else player.resetPlayerWeather();
      switch (String.valueOf(selected.time()).toUpperCase(Locale.ROOT)) {
        case "DAY" -> player.setPlayerTime(1000, false);
        case "NOON" -> player.setPlayerTime(6000, false);
        case "SUNSET" -> player.setPlayerTime(12000, false);
        case "NIGHT" -> player.setPlayerTime(13000, false);
        case "MIDNIGHT" -> player.setPlayerTime(18000, false);
        default -> player.resetPlayerTime();
      }
    }
  }

  private void resetPlayer(Player player) {
    player.resetPlayerWeather();
    player.resetPlayerTime();
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    if (affected.remove(e.getPlayer().getUniqueId())) resetPlayer(e.getPlayer());
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent e) {
    if (affected.remove(e.getPlayer().getUniqueId())) resetPlayer(e.getPlayer());
  }

  public void shutdown() {
    effects.clear();
    for (UUID id : affected) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) resetPlayer(p);
    }
    affected.clear();
  }
}
