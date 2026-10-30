package org.plugin.theMob.boss;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.minion.BossMinionController;
import org.plugin.theMob.boss.minion.BossMinionSpawner;

public final class BossActionEngine implements Listener {
  private final TheMob plugin;
  private final Random rnd = new Random();
  private final BossMinionController minionController;
  private final BossMinionSpawner minionSpawner;
  private BukkitRunnable tickTask;

  public BossActionEngine(TheMob plugin) {
    this.plugin = plugin;
    this.minionController = new BossMinionController(plugin);
    this.minionSpawner = new BossMinionSpawner(plugin, this.minionController);
    this.tickTask =
        new BukkitRunnable() {
          public void run() {
            BossActionEngine.this.minionController.tick();
          }
        };
    this.tickTask.runTaskTimer(plugin, 20L, 20L);
  }

  public void onPhaseEnter(LivingEntity boss, BossPhase phase) {
    if (boss != null && phase != null && boss.isValid() && !boss.isDead()) {
      ConfigurationSection cfg = phase.cfg();
      if (cfg != null) {
        if (plugin.skills().enabled("bosses.phase-abilities")) {
          this.applyAbilities(boss, cfg.getConfigurationSection("abilities"));
          this.applyEffects(boss, cfg.getConfigurationSection("effects"));
          this.applyPhysics(boss, cfg.getConfigurationSection("physics"));
        }
        if (plugin
            .skills()
            .enabled(
                "bosses." + cfg.getString("behavior.mode", "aggressive").toLowerCase(Locale.ROOT)))
          this.applyBehaviorOverrides(boss, cfg.getConfigurationSection("behavior"));
        ConfigurationSection onEnter = cfg.getConfigurationSection("on-enter");
        if (onEnter != null && plugin.skills().enabled("bosses.phase-effects")) {
          this.runOnEnterEffects(boss, onEnter);
        }

        ConfigurationSection actions = cfg.getConfigurationSection("actions");
        if (actions != null) {
          ConfigurationSection summon = actions.getConfigurationSection("summon-minions");
          if (summon != null && plugin.skills().enabled("bosses.summon-minions")) {
            this.minionSpawner.onPhaseEnter(boss, phase, summon);
          }
        }
      }
    }
  }

  public void onPhaseLeave(LivingEntity boss, BossPhase phase) {
    if (boss != null && phase != null) {
      boss.removePotionEffect(org.plugin.theMob.compat.BukkitTypes.potion("BLINDNESS"));
      boss.removePotionEffect(org.plugin.theMob.compat.BukkitTypes.potion("SLOWNESS"));
      if (boss instanceof Mob mob) {
        mob.setAware(true);
      }

      this.minionSpawner.onPhaseLeave(boss, phase);
    }
  }

  public void cleanupBoss(LivingEntity boss) {
    this.onBossDeath(boss);
  }

  public void onBossDeath(LivingEntity boss) {
    if (boss != null) {
      boss.removePotionEffect(org.plugin.theMob.compat.BukkitTypes.potion("BLINDNESS"));
      boss.removePotionEffect(org.plugin.theMob.compat.BukkitTypes.potion("SLOWNESS"));
      if (boss instanceof Mob mob) {
        mob.setTarget(null);
        mob.setAware(true);
        mob.setAI(false);
      }

      UUID id = boss.getUniqueId();
      this.minionSpawner.stopAllForBoss(id);
      this.minionController.cleanupBoss(id);
      // XP is issued exactly once by MobDropEngine.
    }
  }

  private void applyBehaviorOverrides(LivingEntity boss, ConfigurationSection cfg) {
    if (cfg != null) {
      String mode = cfg.getString("mode", "aggressive").toLowerCase(Locale.ROOT);
      if ("flee".equals(mode)) {
        boss.addPotionEffect(
            new PotionEffect(
                org.plugin.theMob.compat.BukkitTypes.potion("BLINDNESS"),
                Integer.MAX_VALUE,
                1,
                false,
                false));
        boss.addPotionEffect(
            new PotionEffect(
                org.plugin.theMob.compat.BukkitTypes.potion("SLOWNESS"),
                Integer.MAX_VALUE,
                1,
                false,
                false));
        if (boss instanceof Mob mob) {
          mob.setTarget(null);
          mob.setAware(true);
          mob.setAI(true);
        }
      }
    }
  }

  private void applyAbilities(LivingEntity boss, ConfigurationSection cfg) {
    if (cfg != null) {
      boss.setAI(!cfg.getBoolean("no-ai", false));
      boss.setSilent(cfg.getBoolean("silent", false));
      boss.setInvulnerable(cfg.getBoolean("invulnerable", false));
      boss.setGlowing(cfg.getBoolean("glowing", false));
      boss.setInvisible(cfg.getBoolean("invisibility", false));
      boss.setGravity(cfg.getBoolean("gravity", true));
      boss.setPersistent(true);
    }
  }

  private void applyEffects(LivingEntity boss, ConfigurationSection cfg) {
    if (cfg != null) {
      if (cfg.getBoolean("fire-resistance", false)) {
        boss.addPotionEffect(
            new PotionEffect(
                org.plugin.theMob.compat.BukkitTypes.potion("FIRE_RESISTANCE"),
                Integer.MAX_VALUE,
                0,
                false,
                false));
      }
    }
  }

  private void applyPhysics(LivingEntity boss, ConfigurationSection cfg) {
    if (cfg != null) {
      boss.setGravity(cfg.getBoolean("gravity", true));
    }
  }

  private void runOnEnterEffects(LivingEntity boss, ConfigurationSection onEnter) {
    ConfigurationSection effects = onEnter.getConfigurationSection("effects");
    if (effects != null) {
      Location loc = boss.getLocation().add(0.0, 1.0, 0.0);
      ConfigurationSection particles = effects.getConfigurationSection("particles");
      if (particles != null) {
        try {
          Particle type =
              Particle.valueOf(particles.getString("type", "FLAME").toUpperCase(Locale.ROOT));
          boss.getWorld()
              .spawnParticle(
                  type,
                  loc,
                  particles.getInt("amount", 40),
                  particles.getDouble("radius", 1.5),
                  particles.getDouble("height", 1.5),
                  particles.getDouble("radius", 1.5),
                  particles.getDouble("speed", 0.03));
        } catch (Exception var9) {
        }
      }

      ConfigurationSection sound = effects.getConfigurationSection("sound");
      if (sound != null) {
        try {
          Sound s =
              org.plugin.theMob.compat.BukkitTypes.sound(
                  sound.getString("type", "ENTITY_WITHER_SPAWN"));
          if (s == null) return;
          boss.getWorld()
              .playSound(
                  boss.getLocation(),
                  s,
                  (float) sound.getDouble("volume", 1.0),
                  (float) sound.getDouble("pitch", 1.0));
        } catch (Exception var8) {
        }
      }

      ConfigurationSection msg = effects.getConfigurationSection("message");
      if (msg != null) {
        this.sendPhaseEnterMessage(boss, msg);
      }
    }
  }

  private void sendPhaseEnterMessage(LivingEntity boss, ConfigurationSection msg) {
    String raw = msg.getString("text", "");
    if (raw != null && !raw.isEmpty()) {
      double r2 = Math.pow(msg.getDouble("radius", 32.0), 2.0);

      for (Player p : boss.getWorld().getPlayers()) {
        if (!(p.getLocation().distanceSquared(boss.getLocation()) > r2)) {
          String resolved = Placeholder.resolve(raw, boss, null, p);
          p.spigot()
              .sendMessage(
                  ChatMessageType.ACTION_BAR,
                  TextComponent.fromLegacyText(
                      ChatColor.translateAlternateColorCodes('&', resolved)));
        }
      }
    }
  }

  public void shutdown() {
    if (this.tickTask != null) {
      this.tickTask.cancel();
      this.tickTask = null;
    }

    this.minionSpawner.shutdown();
    this.minionController.clearAll();
  }
}
