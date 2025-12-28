package org.plugin.theMob.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;

import java.util.*;

public final class BossActionEngine {

    private final TheMob plugin;
    private final Random rnd = new Random();

    // 🌍 World snapshots (per WORLD, not per boss)
    private final Map<World, WorldSnapshot> worldSnapshots = new HashMap<>();

    // 🔢 Active boss counter per world
    private final Map<World, Integer> worldBossCount = new HashMap<>();

    public BossActionEngine(TheMob plugin) {
        this.plugin = plugin;
    }

    // =====================================================
    // PHASE ENTER
    // =====================================================
    public void onPhaseEnter(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || !boss.isValid()) return;

        ConfigurationSection cfg = phase.cfg();
        if (cfg == null) return;

        trackWorldEnter(boss.getWorld());
        applyWorldSnapshotIfNeeded(boss.getWorld());

        applyAttributes(boss, cfg.getConfigurationSection("buffs"));
        applyAbilities(boss, cfg.getConfigurationSection("abilities"));
        applyEffects(boss, cfg.getConfigurationSection("effects"));
        applyPhysics(boss, cfg.getConfigurationSection("physics"));
        applyWorld(boss, cfg.getConfigurationSection("world"));

        ConfigurationSection onEnter = cfg.getConfigurationSection("on-enter");
        if (onEnter != null) runOnEnterEffects(boss, onEnter);

        ConfigurationSection actions = cfg.getConfigurationSection("actions");
        if (actions != null) {
            ConfigurationSection summon = actions.getConfigurationSection("summon-minions");
            if (summon != null && summon.getBoolean("enabled")) {
                runSummonMinionsOnce(boss, phase, summon);
            }
        }
    }

    // =====================================================
    // PHASE LEAVE
    // =====================================================
    public void onPhaseLeave(LivingEntity boss, BossPhase phase) {
        if (boss == null) return;

        resetAttributes(boss);
        clearPotionEffects(boss);

        boss.setGlowing(false);
        boss.setInvisible(false);
        boss.setInvulnerable(false);
        boss.setSilent(false);
        boss.setAI(true);
        boss.setGravity(true);
        boss.setFireTicks(0);
    }

    // =====================================================
    // BOSS DEATH / DESPAWN
    // =====================================================
    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;

        World w = boss.getWorld();
        trackWorldLeave(w);
    }

    // =====================================================
    // FAILSAFE (RELOAD / RESTART)
    // =====================================================
    public void restoreAllWorlds() {
        for (WorldSnapshot snap : worldSnapshots.values()) {
            restoreSnapshot(snap);
        }
        worldSnapshots.clear();
        worldBossCount.clear();
    }

    // =====================================================
    // WORLD TRACKING
    // =====================================================
    private void trackWorldEnter(World w) {
        worldBossCount.put(w, worldBossCount.getOrDefault(w, 0) + 1);
    }

    private void trackWorldLeave(World w) {
        int left = worldBossCount.getOrDefault(w, 0) - 1;
        if (left <= 0) {
            worldBossCount.remove(w);
            WorldSnapshot snap = worldSnapshots.remove(w);
            if (snap != null) restoreSnapshot(snap);
        } else {
            worldBossCount.put(w, left);
        }
    }

    private void applyWorldSnapshotIfNeeded(World w) {
        if (worldSnapshots.containsKey(w)) return;

        worldSnapshots.put(w, new WorldSnapshot(
                w,
                w.hasStorm(),
                w.isThundering(),
                w.getTime()
        ));
    }

    private void restoreSnapshot(WorldSnapshot snap) {
        World w = snap.world();
        w.setStorm(snap.storm());
        w.setThundering(snap.thunder());
        w.setTime(snap.time());
    }

    // =====================================================
    // WORLD EFFECTS
    // =====================================================
    private void applyWorld(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        World w = boss.getWorld();

        String weather = cfg.getString("weather");
        if ("CLEAR".equalsIgnoreCase(weather)) {
            w.setStorm(false);
            w.setThundering(false);
        } else if ("RAIN".equalsIgnoreCase(weather)) {
            w.setStorm(true);
            w.setThundering(false);
        } else if ("THUNDER".equalsIgnoreCase(weather)) {
            w.setStorm(true);
            w.setThundering(true);
        }

        String time = cfg.getString("time");
        if ("DAY".equalsIgnoreCase(time)) w.setTime(1000);
        else if ("NIGHT".equalsIgnoreCase(time)) w.setTime(13000);
    }

    // =====================================================
    // MINIONS (ONE SHOT)
    // =====================================================
    private void runSummonMinionsOnce(
            LivingEntity boss,
            BossPhase phase,
            ConfigurationSection cfg
    ) {
        NamespacedKey key = new NamespacedKey(plugin, "minions_" + phase.id());
        if (boss.getPersistentDataContainer().has(key, PersistentDataType.INTEGER)) return;

        boss.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);

        int amount = cfg.getInt("amount", 1);
        double radius = cfg.getDouble("radius", 5);
        int lifetime = cfg.getInt("cooldown", 15);
        EntityType type = EntityType.valueOf(cfg.getString("type", "ZOMBIE"));

        for (int i = 0; i < amount; i++) {
            Location loc = boss.getLocation().clone().add(
                    (rnd.nextDouble() * 2 - 1) * radius,
                    0,
                    (rnd.nextDouble() * 2 - 1) * radius
            );

            LivingEntity minion = (LivingEntity) boss.getWorld().spawnEntity(loc, type);

            minion.setPersistent(true);
            minion.setRemoveWhenFarAway(false);

            minion.getPersistentDataContainer().set(
                    plugin.keys().NO_DROPS,
                    PersistentDataType.INTEGER,
                    1
            );

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (minion.isValid()) minion.remove();
                }
            }.runTaskLater(plugin, lifetime * 20L);
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void applyAttributes(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        applyAttribute(boss, Attribute.MOVEMENT_SPEED, cfg.getDouble("movement-speed", 0));
        applyAttribute(boss, Attribute.ATTACK_DAMAGE, cfg.getDouble("damage", 0));
        applyAttribute(boss, Attribute.ARMOR, cfg.getDouble("armor", 0));
        applyAttribute(boss, Attribute.ARMOR_TOUGHNESS, cfg.getDouble("armor-toughness", 0));
        applyAttribute(boss, Attribute.KNOCKBACK_RESISTANCE, cfg.getDouble("knockback-resistance", 0));
    }

    private void applyAttribute(LivingEntity e, Attribute attr, double add) {
        if (add == 0) return;
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(inst.getBaseValue() + add);
    }

    private void resetAttributes(LivingEntity boss) {
        for (Attribute a : Attribute.values()) {
            AttributeInstance inst = boss.getAttribute(a);
            if (inst != null) inst.setBaseValue(inst.getDefaultValue());
        }
    }

    private void applyAbilities(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        boss.setAI(!cfg.getBoolean("no-ai", false));
        boss.setSilent(cfg.getBoolean("silent", false));
        boss.setInvulnerable(cfg.getBoolean("invulnerable", false));
        boss.setGlowing(cfg.getBoolean("glowing", false));
        boss.setInvisible(cfg.getBoolean("invisibility", false));
        boss.setGravity(cfg.getBoolean("gravity", true));
        boss.setPersistent(true);
    }

    private void applyEffects(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        if (cfg.getBoolean("fire-resistance", false))
            boss.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE,
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false
            ));
    }

    private void clearPotionEffects(LivingEntity e) {
        e.getActivePotionEffects().forEach(pe -> e.removePotionEffect(pe.getType()));
    }

    private void applyPhysics(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;
        boss.setGravity(cfg.getBoolean("gravity", true));
    }

    // =====================================================
// ON-ENTER EFFECTS (Particles / Sound / Title)
// =====================================================
    private void runOnEnterEffects(LivingEntity boss, ConfigurationSection onEnter) {
        ConfigurationSection effects = onEnter.getConfigurationSection("effects");
        if (effects == null) return;

        Location loc = boss.getLocation().clone().add(0, 1.0, 0);

        // -----------------------------
        // PARTICLES
        // -----------------------------
        ConfigurationSection particles = effects.getConfigurationSection("particles");
        if (particles != null) {
            try {
                Particle type = Particle.valueOf(
                        particles.getString("type", "FLAME").toUpperCase()
                );

                int amount = particles.getInt("amount", 20);
                double radius = particles.getDouble("radius", 1.0);
                double height = particles.getDouble("height", 1.0);
                int duration = particles.getInt("duration", 0);

                Runnable spawn = () -> boss.getWorld().spawnParticle(
                        type,
                        loc,
                        amount,
                        radius,
                        height,
                        radius,
                        0.02
                );

                if (duration <= 0) {
                    spawn.run();
                } else {
                    new BukkitRunnable() {
                        int ticks = duration * 20;

                        @Override
                        public void run() {
                            if (ticks <= 0 || !boss.isValid()) {
                                cancel();
                                return;
                            }
                            spawn.run();
                            ticks -= 5;
                        }
                    }.runTaskTimer(plugin, 0L, 5L);
                }

            } catch (Exception ex) {
                plugin.getLogger().warning(
                        "[Boss] Invalid particle config: " + ex.getMessage()
                );
            }
        }

        // -----------------------------
        // SOUND
        // -----------------------------
        ConfigurationSection sound = effects.getConfigurationSection("sound");
        if (sound != null) {
            try {
                boss.getWorld().playSound(
                        boss.getLocation(),
                        Sound.valueOf(sound.getString("type")),
                        (float) sound.getDouble("volume", 1),
                        (float) sound.getDouble("pitch", 1)
                );
            } catch (Exception ignored) {}
        }

        // -----------------------------
        // MESSAGE / TITLE
        // -----------------------------
        ConfigurationSection msg = effects.getConfigurationSection("message");
        if (msg != null) {
            for (Player p : boss.getWorld().getPlayers()) {
                p.sendTitle(
                        ChatColor.translateAlternateColorCodes('&',
                                msg.getString("title", "")
                        ),
                        ChatColor.translateAlternateColorCodes('&',
                                msg.getString("subtitle", "")
                        ),
                        10, 40, 10
                );
            }
        }
    }


    // =====================================================
    // RECORD
    // =====================================================
    private record WorldSnapshot(World world, boolean storm, boolean thunder, long time) {}
}
