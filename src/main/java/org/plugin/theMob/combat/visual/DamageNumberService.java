package org.plugin.theMob.combat.visual;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DamageNumberService {

    private static final int LIFETIME_TICKS = 16;

    // etwas schneller -> besser sichtbar
    private static final Vector FLOAT_VELOCITY = new Vector(0, 0.045, 0);

    private DamageNumberService() {}

    public static void spawn(
            Plugin plugin,
            Entity target,
            double damage,
            boolean crit
    ) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (target == null || !target.isValid()) return;

        World world = target.getWorld();

        Location loc = target.getLocation().add(
                0,
                Math.max(0.8, target.getHeight() * 0.85),
                0
        );

        TextDisplay text = world.spawn(loc, TextDisplay.class, td -> {
            td.setText(format(damage, crit));
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));

            // WICHTIG: kleinere LineWidth = größere Glyphs
            td.setLineWidth(80);
            td.setTextOpacity((byte) 255);

            // 🔥 REAL SCALE FIX
            float scale = crit ? 2.4f : 1.8f;

            td.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
            ));
        });

        if (crit) {
            text.setGlowColorOverride(Color.YELLOW);
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!text.isValid() || ticks++ >= LIFETIME_TICKS) {
                    text.remove();
                    cancel();
                    return;
                }
                text.teleport(text.getLocation().add(FLOAT_VELOCITY));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static String format(double dmg, boolean crit) {
        String value = dmg >= 100
                ? String.valueOf(Math.round(dmg))
                : String.format("%.1f", dmg);

        return crit
                ? "§6✧§e" + value + "§6✧"
                : "§c" + value;
    }
}
