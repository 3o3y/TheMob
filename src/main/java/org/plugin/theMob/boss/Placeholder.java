package org.plugin.theMob.boss;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * v1.4 Placeholder Resolver
 *
 * Supported placeholders:
 *  {mob_name}   → Custom name or entity type
 *  {phase_id}   → Boss phase id
 *  {phase_title}→ Boss phase title
 *  {world}      → World name
 *  {distance}   → Distance player → boss (rounded)
 *
 * Intentionally lightweight and side-effect free.
 */
public final class Placeholder {

    private Placeholder() {
        // utility
    }

    public static String resolve(
            String input,
            LivingEntity boss,
            BossPhase phase,
            Player viewer
    ) {
        if (input == null || input.isEmpty()) return "";

        String out = input;

        // -------------------------------------------------
// MOB NAME (BASE NAME, NO HP)
// -------------------------------------------------
        String mobName = null;

// 1️⃣ Prefer BASE_NAME from PDC (clean, static)
        if (boss.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(
                        org.bukkit.Bukkit.getPluginManager().getPlugins()[0],
                        "base_name"
                ),
                org.bukkit.persistence.PersistentDataType.STRING
        )) {
            mobName = boss.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(
                            org.bukkit.Bukkit.getPluginManager().getPlugins()[0],
                            "base_name"
                    ),
                    org.bukkit.persistence.PersistentDataType.STRING
            );
        }

// 2️⃣ Fallback: strip HP from custom name
        if (mobName == null || mobName.isEmpty()) {
            String raw = boss.getCustomName();
            if (raw != null) {
                mobName = raw.replaceAll(" ?❤.*", "");
            }
        }

// 3️⃣ Final fallback
        if (mobName == null || mobName.isEmpty()) {
            mobName = boss.getType().name().toLowerCase(Locale.ROOT);
        }

        out = out.replace("{mob_name}", mobName);


        // -------------------------------------------------
        // PHASE ID
        // -------------------------------------------------
        if (phase != null) {
            out = out.replace("{phase_id}", phase.id());
        } else {
            out = out.replace("{phase_id}", "none");
        }

        // -------------------------------------------------
        // PHASE TITLE
        // -------------------------------------------------
        if (phase != null && phase.title() != null) {
            out = out.replace("{phase_title}", phase.title());
        } else {
            out = out.replace("{phase_title}", "");
        }

        // -------------------------------------------------
        // WORLD
        // -------------------------------------------------
        out = out.replace("{world}", boss.getWorld().getName());

        // -------------------------------------------------
        // DISTANCE (player specific)
        // -------------------------------------------------
        if (viewer != null && viewer.isOnline()) {
            Location pl = viewer.getLocation();
            Location bl = boss.getLocation();

            if (pl.getWorld().equals(bl.getWorld())) {
                int dist = (int) Math.round(pl.distance(bl));
                out = out.replace("{distance}", String.valueOf(dist));
            } else {
                out = out.replace("{distance}", "-");
            }
        } else {
            out = out.replace("{distance}", "-");
        }

        // -------------------------------------------------
// PLAYER (killer)
// -------------------------------------------------
        if (out.contains("{player}")) {
            String name = (viewer != null ? viewer.getName() : "unknown");
            out = out.replace("{player}", name);
        }


        return out;
    }

}
