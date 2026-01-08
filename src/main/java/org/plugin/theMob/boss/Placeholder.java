package org.plugin.theMob.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.function.Supplier;

public final class Placeholder {

    // =====================================================
    // NAMESPACE
    // =====================================================
    private static final NamespacedKey BASE_NAME_KEY =
            new NamespacedKey("themob", "base_name");

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Placeholder() {}

    // =====================================================
    // STRING PIPELINE
    // =====================================================
    public static String resolve(
            String input,
            LivingEntity boss,
            BossPhase phase,
            Player viewer
    ) {
        if (input == null || input.isEmpty()) return "";
        if (boss == null || !boss.isValid()) return input;

        Map<String, Supplier<String>> values = buildValues(boss, phase, viewer);

        String out = input;
        for (Map.Entry<String, Supplier<String>> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue().get());
        }

        // PlaceholderAPI (optional, last)
        out = applyPlaceholderAPI(out, viewer);
        return out;
    }

    // =====================================================
    // COMPONENT PIPELINE
    // =====================================================
    public static Component resolveComponent(
            String input,
            LivingEntity boss,
            BossPhase phase,
            Player viewer
    ) {
        return MINI_MESSAGE.deserialize(resolve(input, boss, phase, viewer));
    }

    // =====================================================
    // VALUE REGISTRY
    // =====================================================
    private static Map<String, Supplier<String>> buildValues(
            LivingEntity boss,
            BossPhase phase,
            Player viewer
    ) {
        Map<String, Supplier<String>> map = new HashMap<>();

        // -------- MOB --------
        map.put("mob_name", () -> baseName(boss));
        map.put("mob_type", () -> boss.getType().name().toLowerCase(Locale.ROOT));
        map.put("mob_uuid", () -> boss.getUniqueId().toString());

        // -------- HEALTH --------
        map.put("health", () -> String.valueOf((int) boss.getHealth()));
        map.put("max_health", () -> {
            var a = boss.getAttribute(Attribute.MAX_HEALTH);
            return a != null ? String.valueOf((int) a.getValue()) : "0";
        });
        map.put("health_percent", () -> {
            var a = boss.getAttribute(Attribute.MAX_HEALTH);
            if (a == null || a.getValue() <= 0) return "0";
            return String.valueOf((int) ((boss.getHealth() / a.getValue()) * 100));
        });

        // -------- PHASE --------
        map.put("phase_id", () -> phase != null ? phase.id() : "none");
        map.put("phase_title", () -> phase != null && phase.title() != null ? phase.title() : "");

        // -------- WORLD / LOCATION --------
        Location bl = boss.getLocation();
        map.put("world", () -> bl.getWorld().getName());
        map.put("x", () -> String.valueOf(bl.getBlockX()));
        map.put("y", () -> String.valueOf(bl.getBlockY()));
        map.put("z", () -> String.valueOf(bl.getBlockZ()));
        map.put("chunk_x", () -> String.valueOf(bl.getChunk().getX()));
        map.put("chunk_z", () -> String.valueOf(bl.getChunk().getZ()));

        // -------- DISTANCE --------
        map.put("distance", () -> {
            if (viewer == null || !viewer.isOnline()) return "-";
            if (!viewer.getWorld().equals(bl.getWorld())) return "-";
            return String.valueOf((int) Math.round(viewer.getLocation().distance(bl)));
        });

        // -------- PLAYER --------
        map.put("player", () -> viewer != null ? viewer.getName() : "unknown");
        map.put("player_uuid", () -> viewer != null ? viewer.getUniqueId().toString() : "unknown");

        // -------- META --------
        map.put("online_players", () -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        map.put("server", () -> Bukkit.getServer().getName());

        return map;
    }

    // =====================================================
    // BASE NAME
    // =====================================================
    private static String baseName(LivingEntity boss) {
        String name = boss.getPersistentDataContainer()
                .get(BASE_NAME_KEY, PersistentDataType.STRING);

        if (name != null && !name.isBlank()) return name;

        String raw = boss.getCustomName();
        if (raw != null && !raw.isBlank()) {
            int heart = raw.indexOf('❤');
            return heart > 0 ? raw.substring(0, heart).trim() : raw;
        }

        return boss.getType().name().toLowerCase(Locale.ROOT);
    }

    // =====================================================
    // PLACEHOLDERAPI (OPTIONAL)
    // =====================================================
    private static String applyPlaceholderAPI(String input, Player player) {
        if (input == null || input.isEmpty()) return "";
        if (player == null) return input;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return input;

        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) papi
                    .getMethod("setPlaceholders", Player.class, String.class)
                    .invoke(null, player, input);
        } catch (Throwable ignored) {
            return input;
        }
    }
}
