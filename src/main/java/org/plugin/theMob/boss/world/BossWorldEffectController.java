package org.plugin.theMob.boss.world;

import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BossWorldEffectController {

    // MAIN THREAD ONLY
    private LivingEntity activeBoss;
    private double radiusSq;

    private String weather;
    private String time;

    private final Set<UUID> affectedPlayers = new HashSet<>();

    // =====================================================
    // APPLY (PHASE ENTER)
    // =====================================================

    public void apply(LivingEntity boss, double radius, String weather, String time) {
        resetAll();

        if (boss == null || !boss.isValid() || boss.isDead()) return;

        this.activeBoss = boss;
        this.radiusSq = Math.max(0.0, radius * radius);
        this.weather = weather;
        this.time = time;

        for (Player p : boss.getWorld().getPlayers()) {
            if (isInside(p)) applyToPlayer(p);
        }
    }

    // =====================================================
    // TICK (CALLED FROM ENGINE)
    // =====================================================

    public void tick() {
        if (activeBoss == null || !activeBoss.isValid() || activeBoss.isDead()) {
            resetAll();
            return;
        }

        for (Player p : activeBoss.getWorld().getPlayers()) {
            boolean inside = isInside(p);
            boolean affected = affectedPlayers.contains(p.getUniqueId());

            if (inside && !affected) {
                applyToPlayer(p);
            } else if (!inside && affected) {
                reset(p);
            }
        }
    }

    // =====================================================
    // RESET
    // =====================================================

    public void resetAll() {
        for (UUID id : new HashSet<>(affectedPlayers)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) reset(p);
        }
        affectedPlayers.clear();

        activeBoss = null;
        radiusSq = 0.0;
        weather = null;
        time = null;
    }

    private void reset(Player p) {
        p.resetPlayerWeather();
        p.resetPlayerTime();
        affectedPlayers.remove(p.getUniqueId());
    }

    // =====================================================
    // INTERNAL
    // =====================================================

    private boolean isInside(Player p) {
        if (p == null || activeBoss == null) return false;
        if (!p.getWorld().equals(activeBoss.getWorld())) return false;
        return p.getLocation().distanceSquared(activeBoss.getLocation()) <= radiusSq;
    }

    private void applyToPlayer(Player p) {
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
}
