package org.plugin.theMob.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

public final class Placeholder {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // injected from plugin bootstrap
    private static KeyRegistry keys;

    // PlaceholderAPI (soft)
    private static boolean PAPI_PRESENT;
    private static Method PAPI_SET_PLACEHOLDERS;

    private Placeholder() {}

    // =====================================================
    // INIT (MANDATORY)
    // =====================================================
    public static void init(KeyRegistry registry) {
        keys = registry;

        try {
            PAPI_PRESENT = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
            if (PAPI_PRESENT) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                PAPI_SET_PLACEHOLDERS =
                        papi.getMethod("setPlaceholders", Player.class, String.class);
            }
        } catch (Throwable ignored) {
            PAPI_PRESENT = false;
            PAPI_SET_PLACEHOLDERS = null;
        }
    }

    // =====================================================
    // STRING PIPELINE
    // =====================================================
    public static String resolve(
            String input,
            LivingEntity mob,
            BossPhase phase,
            Player viewer
    ) {
        if (input == null || input.isEmpty()) return input;
        if (mob == null) return input;

        if (keys == null) {
            Bukkit.getLogger().warning("[TheMob] Placeholder used before init()");
            return input;
        }

        Map<String, Supplier<String>> values =
                buildValues(mob, phase, viewer);

        String out = input;
        for (var e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", safe(e.getValue()));
        }

        return applyPlaceholderAPI(out, viewer);
    }

    // =====================================================
    // COMPONENT PIPELINE
    // =====================================================
    public static Component resolveComponent(
            String input,
            LivingEntity mob,
            BossPhase phase,
            Player viewer
    ) {
        String resolved = resolve(input, mob, phase, viewer);
        try {
            return MINI_MESSAGE.deserialize(resolved);
        } catch (Throwable ignored) {
            return Component.text(resolved);
        }
    }

    // =====================================================
    // VALUES
    // =====================================================
    private static Map<String, Supplier<String>> buildValues(
            LivingEntity mob,
            BossPhase phase,
            Player viewer
    ) {
        Map<String, Supplier<String>> map = new HashMap<>(32);

        // -------- MOB --------
        map.put("mob_name", () -> baseName(mob));
        map.put("mob_type", () -> mob.getType().name().toLowerCase(Locale.ROOT));
        map.put("mob_uuid", () -> mob.getUniqueId().toString());

        // -------- HEALTH --------
        map.put("health", () -> String.valueOf((int) Math.ceil(safeHealth(mob))));
        map.put("max_health", () -> {
            AttributeInstance a = mob.getAttribute(Attribute.MAX_HEALTH);
            return a != null ? String.valueOf((int) a.getValue()) : "0";
        });

        // -------- PHASE --------
        map.put("phase_id", () -> phase != null ? phase.id() : "none");
        map.put("phase_title", () -> phase != null ? phase.title() : "");

        // -------- PLAYER --------
        map.put("player", () -> viewer != null ? viewer.getName() : "");
        map.put("player_uuid", () -> viewer != null ? viewer.getUniqueId().toString() : "");

        // -------- LOCATION --------
        map.put("world", () -> safeWorld(mob));
        map.put("x", () -> String.valueOf(mob.getLocation().getBlockX()));
        map.put("y", () -> String.valueOf(mob.getLocation().getBlockY()));
        map.put("z", () -> String.valueOf(mob.getLocation().getBlockZ()));

        return map;
    }

    // =====================================================
    // BASE NAME (KEYREGISTRY!)
    // =====================================================
    private static String baseName(LivingEntity mob) {
        String name = mob.getPersistentDataContainer()
                .get(keys.BASE_NAME, PersistentDataType.STRING);

        if (name != null && !name.isBlank()) return name;

        String raw = mob.getCustomName();
        if (raw != null && !raw.isBlank()) {
            int heart = raw.indexOf('❤');
            return heart > 0 ? raw.substring(0, heart).trim() : raw;
        }

        return mob.getType().name().toLowerCase(Locale.ROOT);
    }

    // =====================================================
    // PLACEHOLDER API
    // =====================================================
    private static String applyPlaceholderAPI(String input, Player player) {
        if (!PAPI_PRESENT || PAPI_SET_PLACEHOLDERS == null) return input;
        if (player == null) return input;

        try {
            return (String) PAPI_SET_PLACEHOLDERS.invoke(null, player, input);
        } catch (Throwable ignored) {
            return input;
        }
    }

    // =====================================================
    // UTILS
    // =====================================================
    private static String safe(Supplier<String> s) {
        try {
            return Optional.ofNullable(s.get()).orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static double safeHealth(LivingEntity mob) {
        try {
            return Math.max(0.0, mob.getHealth());
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static String safeWorld(LivingEntity mob) {
        try {
            Location l = mob.getLocation();
            return l.getWorld() != null ? l.getWorld().getName() : "unknown";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }
}
