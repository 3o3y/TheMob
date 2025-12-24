// src/main/java/org/plugin/theMob/ui/MobHealthDisplay.java
package org.plugin.theMob.ui;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.plugin.theMob.mob.MobManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MobHealthDisplay {

    // v1.1 rule:
    // <= 20 blocks: NAME
    // <= 10 blocks: NAME + HEALTH
    // never through walls/floors
    private static final double NAME_RANGE = 14.0;
    private static final double FULL_RANGE = 7.0;
    private static final double NAME_RANGE_SQ = NAME_RANGE * NAME_RANGE;
    private static final double FULL_RANGE_SQ = FULL_RANGE * FULL_RANGE;

    private final JavaPlugin plugin;
    private final MobManager mobs;

    private final Set<UUID> tracked = new HashSet<>();
    private BukkitRunnable moveTask;

    public MobHealthDisplay(JavaPlugin plugin, MobManager mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
        startMoveUpdater();
    }

    public void onSpawn(LivingEntity mob) {
        if (mob == null) return;
        tracked.add(mob.getUniqueId());
        update(mob);
    }

    public void onDeath(LivingEntity mob) {
        if (mob == null) return;
        tracked.remove(mob.getUniqueId());
        mob.setCustomNameVisible(false);
    }

    public void update(LivingEntity mob) {
        if (mob == null || !mob.isValid() || mob.isDead()) return;
        if (!mobs.isCustomMob(mob)) return;

        Visibility vis = resolveVisibility(mob);
        if (!vis.visible) {
            mob.setCustomNameVisible(false);
            return;
        }

        String base = mobs.baseNameOf(mob);
        if (base == null || base.isBlank()) base = mob.getType().name();

        String name;
        if (vis.full) {
            AttributeInstance maxAttr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (maxAttr == null) return;
            int hp = (int) Math.max(0, mob.getHealth());
            int max = (int) Math.max(1, maxAttr.getValue());
            name = base + " §c❤ §f" + hp + "/" + max;
        } else {
            name = base;
        }

        if (!name.equals(mob.getCustomName())) mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }

    private void startMoveUpdater() {
        // move hardening: refresh visibility/format periodically (lightweight)
        moveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (tracked.isEmpty()) return;

                tracked.removeIf(uuid -> {
                    var e = Bukkit.getEntity(uuid);
                    if (!(e instanceof LivingEntity mob)) return true;
                    if (!mob.isValid() || mob.isDead()) return true;
                    update(mob);
                    return false;
                });
            }
        };
        moveTask.runTaskTimer(plugin, 10L, 10L); // every 0.5s
    }

    private Visibility resolveVisibility(LivingEntity mob) {
        boolean visible = false;
        boolean full = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOnline()) continue;
            if (p.getWorld() != mob.getWorld()) continue;

            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d > NAME_RANGE_SQ) continue;

            if (!hasClearLOS(p, mob)) continue;

            visible = true;
            if (d <= FULL_RANGE_SQ) {
                full = true;
                break;
            }
        }

        return new Visibility(visible, full);
    }

    private boolean hasClearLOS(Player p, LivingEntity mob) {
        if (!p.hasLineOfSight(mob)) return false;

        Vector from = p.getEyeLocation().toVector();
        Vector to = mob.getEyeLocation().toVector();
        Vector dir = to.subtract(from);
        double len = dir.length();
        if (len <= 0.1) return true;

        dir.normalize().multiply(0.5);
        Vector cursor = from.clone();
        for (double i = 0; i < len; i += 0.5) {
            cursor.add(dir);
            if (!cursor.toLocation(p.getWorld()).getBlock().isPassable()) return false;
        }
        return true;
    }

    private record Visibility(boolean visible, boolean full) {}
}
