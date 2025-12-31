package org.plugin.theMob.boss;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Locale;

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
        String mobName = null;

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

        if (mobName == null || mobName.isEmpty()) {
            String raw = boss.getCustomName();
            if (raw != null) {
                mobName = raw.replaceAll(" ?❤.*", "");
            }
        }

        if (mobName == null || mobName.isEmpty()) {
            mobName = boss.getType().name().toLowerCase(Locale.ROOT);
        }

        out = out.replace("{mob_name}", mobName);

        if (phase != null) {
            out = out.replace("{phase_id}", phase.id());
        } else {
            out = out.replace("{phase_id}", "none");
        }

        if (phase != null && phase.title() != null) {
            out = out.replace("{phase_title}", phase.title());
        } else {
            out = out.replace("{phase_title}", "");
        }

        out = out.replace("{world}", boss.getWorld().getName());

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

        if (out.contains("{player}")) {
            String name = (viewer != null ? viewer.getName() : "unknown");
            out = out.replace("{player}", name);
        }


        return out;
    }

}
