package org.plugin.theMob.boss;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.minion.BossMinionController;
import org.plugin.theMob.boss.minion.BossMinionSpawner;
import org.plugin.theMob.boss.world.BossWorldEffectController;

import java.util.*;

public final class BossActionEngine implements Listener {

    private final TheMob plugin;
    private final Random rnd = new Random();

    private final BossMinionController minionController;
    private final BossMinionSpawner minionSpawner;
    private final BossWorldEffectController worldEffects = new BossWorldEffectController();

    private final Map<UUID, BossSnapshot> bossSnapshots = new HashMap<>();
    private BukkitRunnable tickTask;

    public BossActionEngine(TheMob plugin) {
        this.plugin = plugin;

        this.minionController = new BossMinionController(plugin);
        this.minionSpawner = new BossMinionSpawner(plugin, minionController);

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                worldEffects.tick();
                cleanupSnapshots();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    // =====================================================
    // PHASE HOOKS
    // =====================================================

    public void onPhaseEnter(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || !boss.isValid()) return;

        ensureSnapshot(boss);
        ConfigurationSection cfg = phase.cfg();
        if (cfg == null) return;

        applyAttributes(boss, cfg.getConfigurationSection("buffs"));
        applyAbilities(boss, cfg.getConfigurationSection("abilities"));
        applyEffects(boss, cfg.getConfigurationSection("effects"));
        applyPhysics(boss, cfg.getConfigurationSection("physics"));

        ConfigurationSection world = cfg.getConfigurationSection("world");
        if (world != null) {
            worldEffects.apply(
                    boss,
                    world.getDouble("radius", 32.0),
                    world.getString("weather"),
                    world.getString("time")
            );
        }

        ConfigurationSection onEnter = cfg.getConfigurationSection("on-enter");
        if (onEnter != null) runOnEnterEffects(boss, onEnter);

        ConfigurationSection actions = cfg.getConfigurationSection("actions");
        if (actions != null) {
            ConfigurationSection summon = actions.getConfigurationSection("summon-minions");
            if (summon != null) {
                minionSpawner.onPhaseEnter(boss, phase, summon);
            }
        }
    }

    public void onPhaseLeave(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null) return;
        minionSpawner.onPhaseLeave(boss, phase);
    }

    public void onBossDeath(LivingEntity boss) {
        worldEffects.resetAll();

        if (boss != null) {
            minionSpawner.stopAllForBoss(boss.getUniqueId());
            minionController.cleanupBoss(boss.getUniqueId());
            spawnBossXpExplosion(boss);
            bossSnapshots.remove(boss.getUniqueId());
        }
    }

    // =====================================================
    // SNAPSHOTS
    // =====================================================

    private void ensureSnapshot(LivingEntity boss) {
        bossSnapshots.computeIfAbsent(
                boss.getUniqueId(),
                id -> BossSnapshot.capture(boss)
        );
    }

    private void cleanupSnapshots() {
        bossSnapshots.keySet().removeIf(id -> {
            for (World w : Bukkit.getWorlds()) {
                var e = w.getEntity(id);
                if (e instanceof LivingEntity le && le.isValid()) return false;
            }
            return true;
        });
    }

    // =====================================================
    // ATTRIBUTES / EFFECTS
    // =====================================================

    private void applyAttributes(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        BossSnapshot snap = bossSnapshots.get(boss.getUniqueId());

        setAttr(boss, Attribute.MOVEMENT_SPEED, snap, cfg, "movement-speed");
        setAttr(boss, Attribute.ATTACK_DAMAGE, snap, cfg, "damage");
        setAttr(boss, Attribute.ARMOR, snap, cfg, "armor");
        setAttr(boss, Attribute.ARMOR_TOUGHNESS, snap, cfg, "armor-toughness");
        setAttr(boss, Attribute.KNOCKBACK_RESISTANCE, snap, cfg, "knockback-resistance");
    }

    private void setAttr(LivingEntity e, Attribute attr, BossSnapshot snap, ConfigurationSection cfg, String key) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;

        double add = cfg.getDouble(key, 0);
        inst.setBaseValue(snap.base(attr) + add);
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
        if (cfg.getBoolean("fire-resistance", false)) {
            boss.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE,
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false
            ));
        }
    }

    private void applyPhysics(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg != null) boss.setGravity(cfg.getBoolean("gravity", true));
    }

    // =====================================================
    // ENTER EFFECTS
    // =====================================================

    private void runOnEnterEffects(LivingEntity boss, ConfigurationSection onEnter) {
        ConfigurationSection effects = onEnter.getConfigurationSection("effects");
        if (effects == null) return;

        Location loc = boss.getLocation().add(0, 1, 0);

        ConfigurationSection particles = effects.getConfigurationSection("particles");
        if (particles != null) {
            try {
                Particle type = Particle.valueOf(
                        particles.getString("type", "FLAME").toUpperCase(Locale.ROOT)
                );
                boss.getWorld().spawnParticle(
                        type, loc,
                        particles.getInt("amount", 20),
                        particles.getDouble("radius", 1),
                        particles.getDouble("height", 1),
                        particles.getDouble("radius", 1),
                        0.02
                );
            } catch (Exception ignored) {}
        }

        ConfigurationSection sound = effects.getConfigurationSection("sound");
        if (sound != null) {
            try {
                boss.getWorld().playSound(
                        boss.getLocation(),
                        Sound.valueOf(sound.getString("type", "ENTITY_WITHER_SPAWN").toUpperCase(Locale.ROOT)),
                        (float) sound.getDouble("volume", 1),
                        (float) sound.getDouble("pitch", 1)
                );
            } catch (Exception ignored) {}
        }

        ConfigurationSection msg = effects.getConfigurationSection("message");
        if (msg != null) sendPhaseEnterMessage(boss, msg);
    }

    // =====================================================
    // MESSAGES
    // =====================================================

    private void sendPhaseEnterMessage(LivingEntity boss, ConfigurationSection msg) {
        String raw = msg.getString("text", "");
        if (raw.isEmpty()) return;

        double radius = msg.getDouble("radius", 32.0);
        double r2 = radius * radius;

        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > r2) continue;

            String text = ChatColor.translateAlternateColorCodes(
                    '&',
                    Placeholder.resolve(raw, boss, null, p)
            );

            p.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(text)
            );
        }
    }

    // =====================================================
    // XP EXPLOSION
    // =====================================================

    private void spawnBossXpExplosion(LivingEntity boss) {
        FileConfiguration cfg = plugin.mobs().mobConfigOf(boss);
        if (cfg == null) return;

        int totalXp = cfg.getInt("xp", 0);
        if (totalXp <= 0) return;

        World w = boss.getWorld();
        Location c = boss.getLocation().add(0, 1, 0);

        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 2);
        w.spawnParticle(Particle.HAPPY_VILLAGER, c, 60, 1.8, 1.2, 1.8, 0.05);
        w.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 1.4f, 0.6f);

        int orbCount = 20;
        int xpPerOrb = Math.max(1, totalXp / orbCount);

        for (int i = 0; i < orbCount; i++) {
            Location l = c.clone().add(
                    rnd.nextGaussian() * 1.2,
                    rnd.nextDouble() * 0.8,
                    rnd.nextGaussian() * 1.2
            );
            ExperienceOrb orb = w.spawn(l, ExperienceOrb.class);
            orb.setExperience(xpPerOrb);
        }
    }

    // =====================================================
    // SHUTDOWN
    // =====================================================

    public void shutdown() {
        worldEffects.resetAll();

        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        bossSnapshots.clear();
    }

    // =====================================================
    // SNAPSHOT
    // =====================================================

    private record BossSnapshot(Map<Attribute, Double> bases) {

        static BossSnapshot capture(LivingEntity e) {
            Map<Attribute, Double> map = new HashMap<>();
            for (Attribute a : Attribute.values()) {
                AttributeInstance inst = e.getAttribute(a);
                if (inst != null) map.put(a, inst.getBaseValue());
            }
            return new BossSnapshot(map);
        }

        double base(Attribute a) {
            return bases.getOrDefault(a, 0.0);
        }
    }
}
