package org.plugin.theMob.boss;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
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

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public final class BossActionEngine implements Listener {

    private final TheMob plugin;
    private final Random rnd = new Random();

    private final BossMinionController minionController;
    private final BossMinionSpawner minionSpawner;

    private BukkitRunnable tickTask;

    public BossActionEngine(TheMob plugin) {
        this.plugin = plugin;

        this.minionController = new BossMinionController(plugin);
        this.minionSpawner = new BossMinionSpawner(plugin, minionController);

        // Only minion lifecycle / timed logic
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                // keep minion system stable over long uptimes
                minionController.tick();
            }
        };
        tickTask.runTaskTimer(plugin, 20L, 20L);
    }

    // =====================================================
    // PHASE HOOKS
    // =====================================================

    public void onPhaseEnter(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || !boss.isValid() || boss.isDead()) return;

        ConfigurationSection cfg = phase.cfg();
        if (cfg == null) return;

        // -------------------------------------------------
        // IMPORTANT:
        // - NO attribute changes here (handled by PhaseBuffEngine)
        // - NO world effects here (handled by BossPhaseController / WorldController)
        // -------------------------------------------------

        applyAbilities(boss, cfg.getConfigurationSection("abilities"));
        applyEffects(boss, cfg.getConfigurationSection("effects"));
        applyPhysics(boss, cfg.getConfigurationSection("physics"));

        // on-enter visuals (particles/sound/message)
        ConfigurationSection onEnter = cfg.getConfigurationSection("on-enter");
        if (onEnter != null) runOnEnterEffects(boss, onEnter);

        // actions (minions)
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
        if (boss == null) return;

        UUID id = boss.getUniqueId();

        minionSpawner.stopAllForBoss(id);
        minionController.cleanupBoss(id);

        spawnBossXpExplosion(boss);
    }

    // =====================================================
    // ABILITIES / EFFECTS / PHYSICS
    // =====================================================

    private void applyAbilities(LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return;

        boss.setAI(!cfg.getBoolean("no-ai", false));
        boss.setSilent(cfg.getBoolean("silent", false));
        boss.setInvulnerable(cfg.getBoolean("invulnerable", false));
        boss.setGlowing(cfg.getBoolean("glowing", false));
        boss.setInvisible(cfg.getBoolean("invisibility", false));
        boss.setGravity(cfg.getBoolean("gravity", true));

        // prevent vanilla despawn weirdness
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
        if (cfg == null) return;
        boss.setGravity(cfg.getBoolean("gravity", true));
    }

    // =====================================================
    // ON-ENTER EFFECTS (Particles / Sound / Message)
    // =====================================================

    private void runOnEnterEffects(LivingEntity boss, ConfigurationSection onEnter) {
        ConfigurationSection effects = onEnter.getConfigurationSection("effects");
        if (effects == null) return;

        Location loc = boss.getLocation().add(0, 1, 0);

        // -------- PARTICLES --------
        ConfigurationSection particles = effects.getConfigurationSection("particles");
        if (particles != null) {
            try {
                Particle type = Particle.valueOf(
                        particles.getString("type", "FLAME").toUpperCase(Locale.ROOT)
                );

                int amount = particles.getInt("amount", 20);
                double radius = particles.getDouble("radius", 1.0);
                double height = particles.getDouble("height", 1.0);
                double speed = particles.getDouble("speed", 0.02);

                boss.getWorld().spawnParticle(
                        type,
                        loc,
                        amount,
                        radius,
                        height,
                        radius,
                        speed
                );
            } catch (Exception ignored) {}
        }

        // -------- SOUND --------
        ConfigurationSection sound = effects.getConfigurationSection("sound");
        if (sound != null) {
            try {
                Sound s = Sound.valueOf(
                        sound.getString("type", "ENTITY_WITHER_SPAWN").toUpperCase(Locale.ROOT)
                );
                float volume = (float) sound.getDouble("volume", 1.0);
                float pitch = (float) sound.getDouble("pitch", 1.0);

                boss.getWorld().playSound(boss.getLocation(), s, volume, pitch);
            } catch (Exception ignored) {}
        }

        // -------- MESSAGE --------
        ConfigurationSection msg = effects.getConfigurationSection("message");
        if (msg != null) sendPhaseEnterMessage(boss, msg);
    }

    private void sendPhaseEnterMessage(LivingEntity boss, ConfigurationSection msg) {
        String raw = msg.getString("text", "");
        if (raw == null || raw.isEmpty()) return;

        double radius = msg.getDouble("radius", 32.0);
        double r2 = radius * radius;

        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > r2) continue;

            String resolved = Placeholder.resolve(raw, boss, null, p);
            String legacy = ChatColor.translateAlternateColorCodes('&', resolved);

            p.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(legacy)
            );
        }
    }

    // =====================================================
    // XP EXPLOSION (Boss Death Reward)
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
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        // hard cleanup
        minionController.clearAll();
    }
}
