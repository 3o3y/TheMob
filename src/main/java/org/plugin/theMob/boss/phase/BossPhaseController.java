package org.plugin.theMob.boss.phase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.behavior.BossBehaviorController;
import org.plugin.theMob.boss.combat.PhaseCombatEngine;
import org.plugin.theMob.boss.world.BossWorldEffectController;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.ability.AbilityEngine;

public final class BossPhaseController {
  private final org.plugin.theMob.TheMob plugin;
  private static final int BLOCKS_PER_CHUNK = 16;
  private final BossPhaseResolver resolver;
  private final BossActionEngine actionEngine;
  private final BossBarService bars;
  private final PhaseBuffEngine buffEngine;
  private final AbilityEngine abilityEngine;
  private final PhaseCombatEngine combatEngine;
  private final BossWorldEffectController worldEffects;
  private BossBehaviorController behaviorController;
  private final Map<UUID, BossPhase> lastPhase = new HashMap<>();
  private final Map<UUID, BossTemplate> templates = new HashMap<>();
  private final Set<UUID> phaseApplied = new HashSet<>();

  public BossPhaseController(
      org.plugin.theMob.TheMob plugin,
      BossPhaseResolver resolver,
      BossActionEngine actionEngine,
      BossBarService bars,
      KeyRegistry keys,
      BossWorldEffectController worldEffects) {
    this.plugin = plugin;
    this.resolver = resolver;
    this.actionEngine = actionEngine;
    this.bars = bars;
    this.buffEngine = new PhaseBuffEngine(keys);
    this.abilityEngine = new AbilityEngine(keys);
    this.combatEngine = new PhaseCombatEngine(keys);
    this.worldEffects = worldEffects;
  }

  public void bindBehaviorController(BossBehaviorController controller) {
    this.behaviorController = controller;
  }

  public void onBossSpawn(LivingEntity boss, BossTemplate template) {
    if (boss != null && template != null && boss.isValid() && !boss.isDead()) {
      boss.setVelocity(new Vector(0, 0, 0));
      boss.getActivePotionEffects().forEach(e -> boss.removePotionEffect(e.getType()));
      if (boss instanceof Mob mob) {
        mob.setTarget(null);
        mob.setAware(true);
        mob.setAI(true);
      }

      this.resetAttributesToBase(boss);
      UUID id = boss.getUniqueId();
      template = template.newInstance();
      this.lastPhase.remove(id);
      this.templates.put(id, template);
      this.phaseApplied.remove(id);
      BossPhase phase = this.resolver.resolve(boss, template);
      if (phase != null) {
        this.lastPhase.put(id, phase);
        if (this.bars != null) {
          this.bars.registerBoss(boss);
          this.bars.setPhaseTitle(boss, phase.title());
          this.bars.markDirty(boss);
        }

        this.applyPhaseBuffs(boss, template, phase);
        ConfigurationSection cfg = template.phaseConfig(phase.id());
        if (cfg != null) {
          if (plugin.abilities().enabled("bosses.phase-abilities"))
            this.abilityEngine.apply(boss, cfg);
          if (plugin.abilities().enabled("bosses.phase-combat")) this.combatEngine.apply(boss, cfg);
          this.applyWorldEffectsFromPhaseCfg(boss, template, cfg);
        }

        if (this.behaviorController != null) {
          this.behaviorController.onBossSpawn(boss, phase);
        }

        normalizeHealthToMax(boss);
        this.actionEngine.onPhaseEnter(boss, phase);
        this.showPhaseTitle(boss, phase, template);
        this.phaseApplied.add(id);
        boss.getPersistentDataContainer()
            .set(
                new org.bukkit.NamespacedKey(plugin, "api_phase"),
                org.bukkit.persistence.PersistentDataType.STRING,
                phase.id());
      }
    }
  }

  public void onBossUpdate(LivingEntity boss) {
    if (boss != null && boss.isValid() && !boss.isDead()) {
      UUID id = boss.getUniqueId();
      BossTemplate template = this.templates.get(id);
      if (template != null) {
        BossPhase next = this.resolver.resolve(boss, template);
        if (next != null) {
          BossPhase previous = this.lastPhase.get(id);
          boolean firstApply = !this.phaseApplied.contains(id);
          boolean phaseChanged = previous == null || !previous.id().equals(next.id());
          if (firstApply || phaseChanged) {
            if (plugin.activity() != null) plugin.activity().wake(boss);
            this.combatEngine.rollback(boss);
            this.abilityEngine.rollback(boss);
            this.buffEngine.rollbackPhase(boss);
            if (this.worldEffects != null) {
              this.worldEffects.reset(boss);
            }

            if (!firstApply && previous != null) {
              this.actionEngine.onPhaseLeave(boss, previous);
            }

            this.lastPhase.put(id, next);
            this.phaseApplied.add(id);
            if (this.behaviorController != null) {
              this.behaviorController.onBossSpawn(boss, next);
            }

            if (this.bars != null) {
              this.bars.setPhaseTitle(boss, next.title());
              this.bars.markDirty(boss);
            }

            this.applyPhaseBuffs(boss, template, next);
            ConfigurationSection cfg = template.phaseConfig(next.id());
            if (cfg != null) {
              if (plugin.abilities().enabled("bosses.phase-abilities"))
                this.abilityEngine.apply(boss, cfg);
              if (plugin.abilities().enabled("bosses.phase-combat"))
                this.combatEngine.apply(boss, cfg);
              this.applyWorldEffectsFromPhaseCfg(boss, template, cfg);
            }

            normalizeHealthToMax(boss);
            this.showPhaseTitle(boss, next, template);
            this.actionEngine.onPhaseEnter(boss, next);
            plugin.api().phase(boss, previous == null ? null : previous.id(), next.id());
          } else if (this.bars != null) {
            this.bars.markDirty(boss);
          }
        }
      }
    }
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

      if (this.behaviorController != null) {
        this.behaviorController.onBossDeath(boss);
      }

      UUID id = boss.getUniqueId();
      BossPhase previous = this.lastPhase.remove(id);
      this.templates.remove(id);
      this.phaseApplied.remove(id);
      this.combatEngine.rollback(boss);
      this.abilityEngine.rollback(boss);
      this.buffEngine.rollbackPhase(boss);
      if (this.worldEffects != null) {
        this.worldEffects.reset(boss);
      }

      if (previous != null) {
        this.actionEngine.onPhaseLeave(boss, previous);
      }

      this.actionEngine.cleanupBoss(boss);
      if (this.bars != null) {
        this.bars.setPhaseTitle(boss, null);
        this.bars.unregisterBoss(boss);
      }
    }
  }

  private void showPhaseTitle(LivingEntity boss, BossPhase phase, BossTemplate template) {
    if (boss != null && phase != null && template != null) {
      int radiusBlocks = Math.max(1, template.arenaRadiusChunks()) * 16;
      double radiusSq = (double) radiusBlocks * radiusBlocks;
      Location bossLoc = boss.getLocation();

      for (Player p : boss.getWorld().getPlayers()) {
        if (p.isOnline() && !(p.getLocation().distanceSquared(bossLoc) > radiusSq)) {
          this.showPhaseTitleToPlayer(p, boss, phase);
        }
      }
    }
  }

  private void showPhaseTitleToPlayer(Player player, LivingEntity boss, BossPhase phase) {
    String rawPhaseTitle = phase.title();
    String title = ChatColor.translateAlternateColorCodes('&', rawPhaseTitle);
    String rawMobName = Placeholder.resolve("{mob_name}", boss, phase, player);
    String subtitle = ChatColor.translateAlternateColorCodes('&', rawMobName);
    player.sendTitle(title, subtitle, 6, 36, 6);
  }

  public BossPhase currentPhase(LivingEntity boss) {
    return boss == null ? null : this.lastPhase.get(boss.getUniqueId());
  }

  public Collection<LivingEntity> activeBosses() {
    List<LivingEntity> list = new ArrayList<>(this.templates.size());

    for (UUID id : this.templates.keySet()) {
      if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && !le.isDead()) {
        list.add(le);
      }
    }

    return list;
  }

  private void applyPhaseBuffs(LivingEntity boss, BossTemplate template, BossPhase phase) {
    ConfigurationSection cfg = template.phaseConfig(phase.id());
    if (cfg != null && plugin.abilities().enabled("bosses.phase-buffs")) {
      this.buffEngine.applyPhase(boss, cfg);
    }
  }

  private void applyWorldEffectsFromPhaseCfg(
      LivingEntity boss, BossTemplate template, ConfigurationSection phaseCfg) {
    if (this.worldEffects != null && plugin.abilities().enabled("bosses.world-effects")) {
      ConfigurationSection world = phaseCfg.getConfigurationSection("world");
      if (world != null) {
        String weather = world.getString("weather");
        String time = world.getString("time");
        if (weather != null && !weather.isBlank() || time != null && !time.isBlank()) {
          int radiusBlocks = Math.max(1, template.arenaRadiusChunks()) * 16;
          this.worldEffects.apply(boss, radiusBlocks, weather, time);
        }
      }
    }
  }

  private static void normalizeHealthToMax(LivingEntity boss) {
    AttributeInstance maxAttr = org.plugin.theMob.compat.BukkitTypes.instance(boss, "MAX_HEALTH");
    if (maxAttr != null) {
      double max = maxAttr.getValue();
      double cur = boss.getHealth();
      boss.setHealth(Math.max(0.0, Math.min(cur, max)));
    }
  }

  private void resetAttributesToBase(LivingEntity boss) {
    for (Attribute attr : org.bukkit.Registry.ATTRIBUTE) {
      AttributeInstance inst = boss.getAttribute(attr);
      if (inst != null) {
        // Phase engines own their modifiers; preserve equipment modifiers.
        if (attr == org.plugin.theMob.compat.BukkitTypes.attribute("MOVEMENT_SPEED")
            && inst.getBaseValue() <= 0.0) {
          inst.setBaseValue(0.23);
        }

        if (attr == org.plugin.theMob.compat.BukkitTypes.attribute("FOLLOW_RANGE")
            && inst.getBaseValue() <= 0.0) {
          inst.setBaseValue(32.0);
        }
      }
    }
  }
}
