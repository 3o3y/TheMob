package org.plugin.theMob.combat.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DamageNumberService {

    private static final int LIFETIME_TICKS = 18;

    // ~150 ms Grouping Window (nanoTime!)
    private static final long GROUP_WINDOW_NS = 150_000_000L;

    private static final Vector FLOAT_NORMAL = new Vector(0, 0.03, 0);
    private static final Vector FLOAT_CRIT   = new Vector(0, 0.055, 0);

    private static final Map<UUID, ActiveNumber> ACTIVE = new HashMap<>();

    private DamageNumberService() {}

    // =====================================================
    // SPAWN
    // =====================================================
    public static void spawn(Plugin plugin, Entity target, double damage, boolean crit) {
        if (!(target instanceof LivingEntity living)) return;
        if (!living.isValid() || damage <= 0) return;

        UUID id = living.getUniqueId();
        long now = System.nanoTime();

        ActiveNumber active = ACTIVE.get(id);
        if (active != null && active.crit == crit && now - active.lastTick <= GROUP_WINDOW_NS) {
            active.add(damage, now);
            return;
        }

        double scale = 1.0;
        AttributeInstance scaleAttr = living.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scale = Math.max(0.1, scaleAttr.getValue());
        }

        Location loc = living.getLocation().add(
                randomOffset(),
                living.getHeight() * scale + 0.6,
                randomOffset()
        );

        TextDisplay text = living.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setTextOpacity((byte) 255);
            td.setVisibleByDefault(true);

            if (crit) {
                td.setGlowColorOverride(Color.ORANGE);
            }
        });

        ActiveNumber created = new ActiveNumber(plugin, text, damage, crit, now);
        ACTIVE.put(id, created);

        // ⏱ Start animation NEXT tick (Paper-safe)
        plugin.getServer().getScheduler().runTask(plugin, created::start);
    }

    // =====================================================
    // ACTIVE NUMBER
    // =====================================================
    private static final class ActiveNumber {

        private final Plugin plugin;
        private final TextDisplay text;
        private final boolean crit;

        private double totalDamage;
        private long lastTick;
        private int ticks;

        ActiveNumber(Plugin plugin, TextDisplay text, double damage, boolean crit, long now) {
            this.plugin = plugin;
            this.text = text;
            this.crit = crit;
            this.totalDamage = damage;
            this.lastTick = now;
        }

        void add(double dmg, long now) {
            totalDamage += dmg;
            lastTick = now;
            updateText();
        }

        void start() {
            updateText();

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!text.isValid() || ticks++ > LIFETIME_TICKS) {
                        cleanup();
                        cancel();
                        return;
                    }

                    text.teleport(text.getLocation().add(
                            crit ? FLOAT_CRIT : FLOAT_NORMAL
                    ));
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        void updateText() {
            String value = totalDamage >= 100
                    ? String.valueOf(Math.round(totalDamage))
                    : String.format("%.1f", totalDamage);

            text.text(
                    crit
                            ? Component.text(ChatColor.GOLD + "✦ " + value, NamedTextColor.RED)
                            : Component.text(value, NamedTextColor.GOLD)
            );
        }

        void cleanup() {
            text.remove();
            ACTIVE.values().removeIf(v -> v == this);
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private static double randomOffset() {
        return (Math.random() - 0.5) * 0.22;
    }
}
