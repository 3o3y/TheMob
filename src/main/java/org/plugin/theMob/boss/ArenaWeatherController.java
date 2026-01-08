package org.plugin.theMob.boss;

import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ArenaWeatherController implements Listener {

    private final Plugin plugin;
    // MAIN THREAD ONLY
    private final Set<UUID> affectedPlayers = new HashSet<>();

    public ArenaWeatherController(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // =====================================================
    // APPLY / RESET
    // =====================================================

    public void applyToPlayer(Player p, String weather, String time) {
        if (p == null || !p.isOnline()) return;

        if (weather != null) {
            switch (weather.toUpperCase()) {
                case "CLEAR" -> p.setPlayerWeather(WeatherType.CLEAR);
                case "RAIN", "THUNDER" -> p.setPlayerWeather(WeatherType.DOWNFALL);
                case "NONE" -> p.resetPlayerWeather();
            }
        }

        if (time != null) {
            switch (time.toUpperCase()) {
                case "DAY" -> p.setPlayerTime(1000, false);
                case "NOON" -> p.setPlayerTime(6000, false);
                case "SUNSET" -> p.setPlayerTime(12000, false);
                case "NIGHT" -> p.setPlayerTime(13000, false);
                case "MIDNIGHT" -> p.setPlayerTime(18000, false);
                case "NONE" -> p.resetPlayerTime();
            }
        }

        affectedPlayers.add(p.getUniqueId());
    }

    public void reset(Player p) {
        if (p == null) return;
        p.resetPlayerWeather();
        p.resetPlayerTime();
        affectedPlayers.remove(p.getUniqueId());
    }

    // =====================================================
    // EVENTS
    // =====================================================

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        reset(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        reset(e.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!affectedPlayers.contains(p.getUniqueId())) return;
        if (e.getTo() == null) return;
        if (e.getFrom().getChunk().equals(e.getTo().getChunk())) return;

        if (!isInArena(p)) {
            reset(p);
        }
    }

    // =====================================================
    // ARENA CHECK (BIND HERE)
    // =====================================================

    private boolean isInArena(Player p) {
        // hook into your arena / spawn / hot-cold logic
        return true;
    }

    // =====================================================
    // HARD RESET (reload)
    // =====================================================

    public void resetAll() {
        for (UUID id : new HashSet<>(affectedPlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) reset(p);
        }
        affectedPlayers.clear();
    }
}
