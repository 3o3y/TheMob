package org.plugin.theMob.boss;

import org.bukkit.*;
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
    private final Set<UUID> affectedPlayers = new HashSet<>();

    public ArenaWeatherController(Plugin plugin) {
        this.plugin = plugin;
    }

    public void applyToPlayer(Player p, String weather, String time) {
        if (weather != null) {
            switch (weather.toUpperCase()) {
                case "CLEAR" -> p.setPlayerWeather(WeatherType.CLEAR);
                case "RAIN", "THUNDER" -> p.setPlayerWeather(WeatherType.DOWNFALL);
            }
        }

        if (time != null) {
            switch (time.toUpperCase()) {
                case "DAY" -> p.setPlayerTime(1000, false);
                case "NIGHT" -> p.setPlayerTime(13000, false);
            }
        }

        affectedPlayers.add(p.getUniqueId());
    }

    public void reset(Player p) {
        p.resetPlayerWeather();
        p.resetPlayerTime();
        affectedPlayers.remove(p.getUniqueId());
    }

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
        if (!affectedPlayers.contains(e.getPlayer().getUniqueId())) return;

        if (e.getFrom().getChunk().equals(e.getTo().getChunk())) return;

        // Arena-Check muss von außen kommen (Radius / Hot-Cold)
        if (!isInArena(e.getPlayer())) {
            reset(e.getPlayer());
        }
    }

    // =====================================================
    // PLACEHOLDER – bindest du an Hot/Cold Logic
    // =====================================================
    private boolean isInArena(Player p) {
        // 👉 hier deine ArenaRadiusChunks / SpawnController Logik
        return true;
    }

    // =====================================================
    // HARD RESET (reload)
    // =====================================================
    public void resetAll() {
        for (UUID id : affectedPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) reset(p);
        }
        affectedPlayers.clear();
    }
}
