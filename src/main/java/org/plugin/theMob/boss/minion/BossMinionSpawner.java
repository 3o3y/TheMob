package org.plugin.theMob.boss.minion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class BossMinionSpawner {

    private final TheMob plugin;
    private final BossMinionController controller;
    private final Random rnd = new Random();

    // bossId|phaseId -> repeating task
    private final Map<String, BukkitRunnable> repeatTasks = new ConcurrentHashMap<>();

    public BossMinionSpawner(TheMob plugin, BossMinionController controller) {
        this.plugin = plugin;
        this.controller = controller;
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================

    public void shutdown() {
        for (BukkitRunnable r : repeatTasks.values()) {
            try { r.cancel(); } catch (Throwable ignored) {}
        }
        repeatTasks.clear();
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

    // =====================================================
    // PHASE HOOKS
    // =====================================================

    public void onPhaseEnter(LivingEntity boss, BossPhase phase, ConfigurationSection cfg) {
        if (boss == null || phase == null || cfg == null) return;

        UUID bossId = boss.getUniqueId();
        String phaseId = phase.id();

        if (!cfg.getBoolean("enabled", false)) {
            stopRepeatTask(bossId, phaseId);
            return;
        }

        boolean repeat = cfg.getBoolean("repeat", false);

        summonOnceControlled(boss, phase, cfg);

        if (!repeat) {
            stopRepeatTask(bossId, phaseId);
            return;
        }

        int cooldownSeconds = Math.max(1, cfg.getInt("cooldown", 15));
        startRepeatTask(boss, phase, cfg, cooldownSeconds);
    }

    public void onPhaseLeave(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null) return;
        stopRepeatTask(boss.getUniqueId(), phase.id());
    }

    // =====================================================
    // REPEAT TASKS
    // =====================================================

    private void startRepeatTask(LivingEntity boss, BossPhase phase, ConfigurationSection cfg, int cooldownSeconds) {
        UUID bossId = boss.getUniqueId();
        String phaseId = phase.id();
        String key = bossId + "|" + phaseId;

        stopRepeatTask(bossId, phaseId);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    stopRepeatTask(bossId, phaseId);
                    return;
                }
                summonOnceControlled(boss, phase, cfg);
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

    // =====================================================
    // SPAWNING
    // =====================================================

    private void summonOnceControlled(LivingEntity boss, BossPhase phase, ConfigurationSection cfg) {
        if (!boss.isValid()) return;

        UUID bossId = boss.getUniqueId();
        String phaseId = phase.id();

        int maxAlive = Math.max(0, cfg.getInt("max-alive", 0));
        if (maxAlive > 0 &&
                controller.countBossPhaseMinions(bossId, phaseId) >= maxAlive) {
            return;
        }

        int amount = Math.max(1, cfg.getInt("amount", 1));
        double radius = Math.max(0.0, cfg.getDouble("radius", 5.0));

        int lifetimeSeconds = Math.max(
                1,
                cfg.contains("lifetime")
                        ? cfg.getInt("lifetime", 15)
                        : cfg.getInt("cooldown", 15)
        );

        double scale = Math.max(0.1, Math.min(cfg.getDouble("scale", 1.0), 5.0));

        EntityType type = parseType(cfg.getString("type", "ZOMBIE"));
        if (!MinionRules.isAllowed(type)) return;

        String name = cfg.getString("name", "");
        boolean noDrops = cfg.getBoolean("no-drops", true);

        NamespacedKey guardKey = new NamespacedKey(plugin, "minion_tick_" + phaseId);
        long tick = Bukkit.getCurrentTick();
        Long last = boss.getPersistentDataContainer().get(guardKey, PersistentDataType.LONG);
        if (Objects.equals(last, tick)) return;
        boss.getPersistentDataContainer().set(guardKey, PersistentDataType.LONG, tick);

        for (int i = 0; i < amount; i++) {

            if (!boss.isValid()) break;

            if (maxAlive > 0 &&
                    controller.countBossPhaseMinions(bossId, phaseId) >= maxAlive) {
                break;
            }

            Location loc = boss.getLocation().clone().add(
                    (rnd.nextDouble() * 2 - 1) * radius,
                    0,
                    (rnd.nextDouble() * 2 - 1) * radius
            );

            var spawned = boss.getWorld().spawnEntity(loc, type);
            if (!(spawned instanceof LivingEntity minion)) {
                spawned.remove();
                continue;
            }

            minion.setPersistent(true);
            minion.setRemoveWhenFarAway(false);

            if (scale != 1.0) {
                AttributeInstance scaleAttr = minion.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) scaleAttr.setBaseValue(scale);
            }

            if (name != null && !name.isEmpty()) {
                minion.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
                minion.setCustomNameVisible(true);
            }

            if (noDrops) {
                minion.getPersistentDataContainer()
                        .set(plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 1);
            }

            controller.register(bossId, phaseId, minion);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (minion.isValid()) minion.remove();
                }
            }.runTaskLater(plugin, lifetimeSeconds * 20L);
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private EntityType parseType(String raw) {
        if (raw == null || raw.isBlank()) return EntityType.ZOMBIE;
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[TheMob] Invalid minion type: " + raw);
            return EntityType.ZOMBIE;
        }
    }
}
