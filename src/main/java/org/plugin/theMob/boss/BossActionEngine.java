package org.plugin.theMob.boss;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;

import java.util.*;

public final class BossActionEngine {

    private final TheMob plugin;
    private final Random rnd = new Random();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    public BossActionEngine(TheMob plugin) {
        this.plugin = plugin;
    }
    public void onPhaseEnter(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null) return;
        ConfigurationSection actions = phase.cfg().getConfigurationSection("actions");
        if (actions == null) return;
        ConfigurationSection summon = actions.getConfigurationSection("summon-minions");
        if (summon != null && summon.getBoolean("enabled", false)) {
            runSummonMinions(boss, summon);
        }
    }
// SUMMON MINIONS
    private void runSummonMinions(LivingEntity boss, ConfigurationSection cfg) {
        int cooldownSeconds = Math.max(0, cfg.getInt("cooldown", 0));
        if (!canRun(boss.getUniqueId(), "summon-minions", cooldownSeconds)) return;
        World w = boss.getWorld();
        int amount = Math.max(0, cfg.getInt("amount", 1));
        if (amount <= 0) return;
        double radius = Math.max(0.5, cfg.getDouble("radius", 5.0));
        String customMinionId = cfg.getString("mob-id");
        if (customMinionId == null) customMinionId = cfg.getString("mobId");
        if (customMinionId != null) customMinionId = customMinionId.toLowerCase(Locale.ROOT);
        String nameOverride = cfg.getString("name", null);
        int lifetimeSeconds = Math.max(1, cfg.getInt("lifetime-seconds", 20));
        String typeName = cfg.getString("type", "ZOMBIE");
        EntityType fallbackType;
        try { fallbackType = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { fallbackType = null; }
        for (int i = 0; i < amount; i++) {
            Location base = boss.getLocation();
            double dx = (rnd.nextDouble() * 2 - 1) * radius;
            double dz = (rnd.nextDouble() * 2 - 1) * radius;
            Location loc = base.clone().add(dx, 0, dz);
            LivingEntity spawned = null;

            if (customMinionId != null && plugin.mobs().mobExists(customMinionId)) {
                String spawnId = "boss:" + boss.getUniqueId() + ":minion";
                spawned = plugin.mobs().spawnCustomMob(customMinionId, spawnId, loc);
            }

            if (spawned == null && fallbackType != null) {
                var ent = w.spawnEntity(loc, fallbackType);
                if (ent instanceof LivingEntity le) {
                    spawned = le;
                }
            }

            if (spawned == null) continue;

            spawned.getPersistentDataContainer().set(
                    plugin.keys().NO_DROPS,
                    PersistentDataType.INTEGER,
                    1
            );
            if (nameOverride != null && !nameOverride.isBlank()) {
                spawned.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', nameOverride));
            } else if (spawned.getCustomName() == null || spawned.getCustomName().isBlank()) {
                String bossName = boss.getCustomName() != null ? boss.getCustomName() : "Boss";
                spawned.setCustomName("§eMinion of " + bossName);
            }
            spawned.setCustomNameVisible(true);
            final LivingEntity finalSpawned = spawned;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (finalSpawned.isValid() && !finalSpawned.isDead()) {
                        finalSpawned.remove();
                    }
                }
            }.runTaskLater(plugin, lifetimeSeconds * 20L);
        }
    }
    private LivingEntity findNearestCustomAt(Location loc, String mobId, double radius) {
        double rSq = radius * radius;
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (var e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!plugin.mobs().isCustomMob(le)) continue;
            String id = plugin.mobs().mobIdOf(le);
            if (id == null || !id.equalsIgnoreCase(mobId)) continue;
            double dSq = le.getLocation().distanceSquared(loc);
            if (dSq <= rSq && dSq < bestSq) {
                best = le;
                bestSq = dSq;
            }
        }
        return best;
    }
// Cooldown helper
    private boolean canRun(UUID bossId, String actionKey, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return true;
        long now = org.bukkit.Bukkit.getCurrentTick();
        long cdTicks = cooldownSeconds * 20L;
        Map<String, Long> map = cooldowns.computeIfAbsent(bossId, k -> new HashMap<>());
        Long last = map.get(actionKey);
        if (last != null && (now - last) < cdTicks) return false;
        map.put(actionKey, now);
        return true;
    }
}
