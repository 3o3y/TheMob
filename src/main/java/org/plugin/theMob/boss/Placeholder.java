package org.plugin.theMob.boss;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public final class Placeholder {

    // ❌ no TheMob.getInstance()
    // ✅ use fixed namespace instead
    private static final NamespacedKey BASE_NAME_KEY =
            new NamespacedKey("themob", "base_name");

    private Placeholder() {}

    public static String resolve(
            String input,
            LivingEntity boss,
            BossPhase phase,
            Player viewer
    ) {
        if (input == null || input.isEmpty()) return "";
        if (boss == null || !boss.isValid()) return input;

        String out = input;

        // =====================================================
        // MOB NAME
        // =====================================================
        String mobName = boss.getPersistentDataContainer()
                .get(BASE_NAME_KEY, PersistentDataType.STRING);

        if (mobName == null || mobName.isEmpty()) {
            String raw = boss.getCustomName();
            if (raw != null && !raw.isEmpty()) {
                int heart = raw.indexOf('❤');
                mobName = (heart > 0 ? raw.substring(0, heart).trim() : raw);
            }
        }

        if (mobName == null || mobName.isEmpty()) {
            mobName = boss.getType().name().toLowerCase(Locale.ROOT);
        }

        out = out.replace("{mob_name}", mobName);

        // =====================================================
        // PHASE
        // =====================================================
        if (phase != null) {
            out = out.replace("{phase_id}", phase.id());
            out = out.replace("{phase_title}", phase.title() != null ? phase.title() : "");
        } else {
            out = out.replace("{phase_id}", "none");
            out = out.replace("{phase_title}", "");
        }

        // =====================================================
        // WORLD
        // =====================================================
        out = out.replace("{world}", boss.getWorld().getName());

        // =====================================================
        // DISTANCE
        // =====================================================
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

        // =====================================================
        // PLAYER
        // =====================================================
        if (out.contains("{player}")) {
            out = out.replace(
                    "{player}",
                    viewer != null ? viewer.getName() : "unknown"
            );
        }

        return out;
    }
}
