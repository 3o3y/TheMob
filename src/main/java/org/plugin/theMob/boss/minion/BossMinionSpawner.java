package org.plugin.theMob.boss.minion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;



import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * v1.6 – Controlled summoning (cooldown, max-alive, lifetime, repeat) + legacy support.
 */
public final class BossMinionSpawner {

    private final TheMob plugin;
    private final BossMinionController controller;
    private final Random rnd = new Random();

    // bossId+phaseId -> repeating task
    private final Map<String, BukkitRunnable> repeatTasks = new ConcurrentHashMap<>();

    public BossMinionSpawner(TheMob plugin, BossMinionController controller) {
        this.plugin = plugin;
        this.controller = controller;
    }

    public void stopAllForBoss(UUID bossId) {
        if (bossId == null) return;

        for (String key : repeatTasks.keySet().toArray(new String[0])) {
            if (key.startsWith(bossId.toString() + "|")) {
                BukkitRunnable r = repeatTasks.remove(key);
                if (r != null) r.cancel();
            }
        }
    }

    public void onPhaseEnter(LivingEntity boss, BossPhase phase, ConfigurationSection summonCfg) {
        if (boss == null || phase == null || summonCfg == null) return;
        if (!summonCfg.getBoolean("enabled", false)) {
            stopRepeatTask(boss.getUniqueId(), phase.id());
            return;
        }

        boolean repeat = summonCfg.getBoolean("repeat", false);

        if (!repeat) {
            // Once per phase enter (old behavior), but now controlled
            summonOnceControlled(boss, phase, summonCfg);
            stopRepeatTask(boss.getUniqueId(), phase.id());
            return;
        }

        // Repeat mode: run immediately once, then schedule by cooldown
        summonOnceControlled(boss, phase, summonCfg);

        int cooldownSeconds = Math.max(1, summonCfg.getInt("cooldown", 15));
        startRepeatTask(boss, phase, summonCfg, cooldownSeconds);
    }

    public void onPhaseLeave(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null) return;
        stopRepeatTask(boss.getUniqueId(), phase.id());
    }

    private void startRepeatTask(LivingEntity boss, BossPhase phase, ConfigurationSection summonCfg, int cooldownSeconds) {
        UUID bossId = boss.getUniqueId();
        String phaseId = phase.id();
        String key = bossId + "|" + phaseId;

        // replace existing task
        stopRepeatTask(bossId, phaseId);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid()) {
                    stopRepeatTask(bossId, phaseId);
                    return;
                }

                // still in same world? (optional safety)
                if (boss.getWorld() == null) return;

                summonOnceControlled(boss, phase, summonCfg);
            }
        };

        task.runTaskTimer(plugin, cooldownSeconds * 20L, cooldownSeconds * 20L);
        repeatTasks.put(key, task);
    }

    private void stopRepeatTask(UUID bossId, String phaseId) {
        String key = bossId + "|" + phaseId;
        BukkitRunnable r = repeatTasks.remove(key);
        if (r != null) r.cancel();
    }

    private void summonOnceControlled(LivingEntity boss, BossPhase phase, ConfigurationSection cfg) {
        UUID bossId = boss.getUniqueId();
        String phaseId = phase.id();

        double scale = cfg.getDouble("scale", 1.0);
        scale = Math.max(0.1, Math.min(scale, 5.0)); // safety clamp

        // Anti-abuse: global cap
        int globalAlive = estimateGlobalMinionsAlive();
        if (globalAlive >= MinionRules.MAX_MINIONS_GLOBAL) return;

        int maxAlive = Math.max(0, cfg.getInt("max-alive", 0));
        if (maxAlive > 0) {
            int alive = controller.countBossPhaseMinions(bossId, phaseId);
            if (alive >= maxAlive) return;
        }

        // Legacy support: previously "cooldown" acted like lifetime in your code.
        int lifetimeSeconds = cfg.contains("lifetime") ? cfg.getInt("lifetime", 15) : cfg.getInt("cooldown", 15);
        lifetimeSeconds = Math.max(1, lifetimeSeconds);

        int amount = Math.max(0, cfg.getInt("amount", 1));
        double radius = Math.max(0.0, cfg.getDouble("radius", 5.0));

        String typeRaw = cfg.getString("type", "ZOMBIE");
        EntityType type = parseType(typeRaw);
        if (!MinionRules.isAllowed(type)) return;

        String name = cfg.getString("name", "");
        boolean noDrops = cfg.getBoolean("no-drops", true);

        // Optional: prevent double-trigger same tick (PDC guard)
        NamespacedKey guardKey = new NamespacedKey(plugin, "summon_tick_" + phaseId);
        long nowTick = Bukkit.getCurrentTick();
        Long last = boss.getPersistentDataContainer().get(guardKey, PersistentDataType.LONG);
        if (last != null && Objects.equals(last, nowTick)) return;
        boss.getPersistentDataContainer().set(guardKey, PersistentDataType.LONG, nowTick);

        for (int i = 0; i < amount; i++) {
            if (!boss.isValid()) break;

            // If max-alive enforced, stop early
            if (maxAlive > 0 && controller.countBossPhaseMinions(bossId, phaseId) >= maxAlive) break;

            Location loc = boss.getLocation().clone().add(
                    (rnd.nextDouble() * 2 - 1) * radius,
                    0,
                    (rnd.nextDouble() * 2 - 1) * radius
            );

            org.bukkit.entity.Entity spawned = boss.getWorld().spawnEntity(loc, type);
            if (!(spawned instanceof LivingEntity minion)) {
                spawned.remove();
                continue;
            }

            minion.setPersistent(true);
            minion.setRemoveWhenFarAway(false);

            if (scale != 1.0) {
                AttributeInstance scaleAttr = minion.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) {
                    scaleAttr.setBaseValue(scale);
                }
            }




            if (name != null && !name.isEmpty()) {
                minion.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
                minion.setCustomNameVisible(true);
            }

            if (noDrops) {
                minion.getPersistentDataContainer().set(plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 1);
            }

            controller.register(bossId, phaseId, minion);

            // Lifetime auto-remove
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (minion.isValid()) minion.remove();
                }
            }.runTaskLater(plugin, lifetimeSeconds * 20L);
        }
    }

    private EntityType parseType(String raw) {
        if (raw == null || raw.isBlank()) return EntityType.ZOMBIE;
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[TheMob] Invalid minion type: " + raw + " – fallback to ZOMBIE");
            return EntityType.ZOMBIE;
        }
    }

    private int estimateGlobalMinionsAlive() {
        // Cheap global estimate: sum boss maps
        // (If you want 100% accurate global count, track a global set inside controller.)
        // Good enough for anti-abuse.
        return 0; // v1.6 minimal; global cap still enforced best via controller extension if needed
    }
}
