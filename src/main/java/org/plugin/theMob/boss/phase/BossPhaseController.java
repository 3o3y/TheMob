package org.plugin.theMob.boss.phase;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
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

import java.time.Duration;
import java.util.*;

public final class BossPhaseController {

    private static final int BLOCKS_PER_CHUNK = 16;
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final BossPhaseResolver resolver;
    private final BossActionEngine actionEngine;
    private final BossBarService bars;
    private final PhaseBuffEngine buffEngine;
    private final AbilityEngine abilityEngine;
    private final PhaseCombatEngine combatEngine;
    private final BossWorldEffectController worldEffects;

    // 🔥 Bound later (avoids constructor cycles)
    private BossBehaviorController behaviorController;

    // MAIN THREAD ONLY
    private final Map<UUID, BossPhase> lastPhase = new HashMap<>();
    private final Map<UUID, BossTemplate> templates = new HashMap<>();

    // ✅ marks if current phase has been fully applied at least once
    private final Set<UUID> phaseApplied = new HashSet<>();

    public BossPhaseController(
            BossPhaseResolver resolver,
            BossActionEngine actionEngine,
            BossBarService bars,
            KeyRegistry keys,
            BossWorldEffectController worldEffects
    ) {
        this.resolver = resolver;
        this.actionEngine = actionEngine;
        this.bars = bars;
        this.buffEngine = new PhaseBuffEngine(keys);
        this.abilityEngine = new AbilityEngine(keys);
        this.combatEngine = new PhaseCombatEngine(keys);
        this.worldEffects = worldEffects;
    }

    // =====================================================
    // BINDING
    // =====================================================

    public void bindBehaviorController(BossBehaviorController controller) {
        this.behaviorController = controller;
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================

    public void onBossSpawn(LivingEntity boss, BossTemplate template) {
        if (boss == null || template == null || !boss.isValid() || boss.isDead()) return;

        boss.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        boss.getActivePotionEffects().forEach(e -> boss.removePotionEffect(e.getType()));

        if (boss instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(true);
            mob.setAI(true);
        }

        resetAttributesToBase(boss);

        UUID id = boss.getUniqueId();
        template.resetPhaseState();

        lastPhase.remove(id);
        templates.put(id, template);

        // force first onBossUpdate() to apply even if phase id matches
        phaseApplied.remove(id);

        BossPhase phase = resolver.resolve(boss, template);
        if (phase == null) return;

        lastPhase.put(id, phase);

        if (bars != null) {
            bars.registerBoss(boss);
            bars.setPhaseTitle(boss, phase.title());
            bars.markDirty(boss);
        }

        // Apply phase systems on spawn
        applyPhaseBuffs(boss, template, phase);

        ConfigurationSection cfg = template.phaseConfig(phase.id());
        if (cfg != null) {
            abilityEngine.apply(boss, cfg);
            combatEngine.apply(boss, cfg);
            applyWorldEffectsFromPhaseCfg(boss, template, cfg);
        }

        // ✅ Behavior init on spawn (THIS was missing)
        if (behaviorController != null) {
            behaviorController.onBossSpawn(boss, phase);
        }

        normalizeHealthToMax(boss);

        actionEngine.onPhaseEnter(boss, phase);
        showPhaseTitle(boss, phase, template);

        phaseApplied.add(id);
    }

    public void onBossUpdate(LivingEntity boss) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        UUID id = boss.getUniqueId();
        BossTemplate template = templates.get(id);
        if (template == null) return;

        BossPhase next = resolver.resolve(boss, template);
        if (next == null) return;

        BossPhase previous = lastPhase.get(id);

        boolean firstApply = !phaseApplied.contains(id);
        boolean phaseChanged = previous == null || !previous.id().equals(next.id());

        if (firstApply || phaseChanged) {

            // rollback old phase systems
            combatEngine.rollback(boss);
            abilityEngine.rollback(boss);
            buffEngine.rollbackPhase(boss);

            if (worldEffects != null) {
                worldEffects.reset(boss);
            }

            if (!firstApply && previous != null) {
                actionEngine.onPhaseLeave(boss, previous);
            }

            // set new phase
            lastPhase.put(id, next);
            phaseApplied.add(id);

            // ✅ Behavior rebind on phase change / first apply
            if (behaviorController != null) {
                behaviorController.onBossSpawn(boss, next);
            }

            if (bars != null) {
                bars.setPhaseTitle(boss, next.title());
                bars.markDirty(boss);
            }

            // apply new phase systems
            applyPhaseBuffs(boss, template, next);

            ConfigurationSection cfg = template.phaseConfig(next.id());
            if (cfg != null) {
                abilityEngine.apply(boss, cfg);
                combatEngine.apply(boss, cfg);
                applyWorldEffectsFromPhaseCfg(boss, template, cfg);
            }

            normalizeHealthToMax(boss);

            showPhaseTitle(boss, next, template);
            actionEngine.onPhaseEnter(boss, next);

        } else {
            if (bars != null) bars.markDirty(boss);
        }
    }

    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;

        boss.removePotionEffect(PotionEffectType.BLINDNESS);
        boss.removePotionEffect(PotionEffectType.SLOWNESS);

        if (boss instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(true);
            mob.setAI(false);
        }

        // ✅ stop behavior
        if (behaviorController != null) {
            behaviorController.onBossDeath(boss);
        }

        UUID id = boss.getUniqueId();

        BossPhase previous = lastPhase.remove(id);
        templates.remove(id);
        phaseApplied.remove(id);

        combatEngine.rollback(boss);
        abilityEngine.rollback(boss);
        buffEngine.rollbackPhase(boss);

        if (worldEffects != null) {
            worldEffects.reset(boss);
        }

        if (previous != null) {
            actionEngine.onPhaseLeave(boss, previous);
        }

        actionEngine.onBossDeath(boss);

        if (bars != null) {
            bars.setPhaseTitle(boss, null);
            bars.unregisterBoss(boss);
        }
    }

    // =====================================================
    // VISUALS (ARENA ONLY)
    // =====================================================

    private void showPhaseTitle(LivingEntity boss, BossPhase phase, BossTemplate template) {
        if (boss == null || phase == null || template == null) return;

        int radiusBlocks = Math.max(1, template.arenaRadiusChunks()) * BLOCKS_PER_CHUNK;
        double radiusSq = (double) radiusBlocks * radiusBlocks;
        var bossLoc = boss.getLocation();

        for (Player p : boss.getWorld().getPlayers()) {
            if (!p.isOnline()) continue;
            if (p.getLocation().distanceSquared(bossLoc) > radiusSq) continue;
            showPhaseTitleToPlayer(p, boss, phase);
        }
    }

    private void showPhaseTitleToPlayer(Player player, LivingEntity boss, BossPhase phase) {
        String rawPhaseTitle = phase.title();
        Component title = LEGACY.deserialize(rawPhaseTitle);

        String rawMobName = Placeholder.resolve("{mob_name}", boss, phase, player);
        Component subtitle = LEGACY.deserialize(rawMobName);

        player.showTitle(
                Title.title(
                        title,
                        subtitle,
                        Title.Times.times(
                                Duration.ofMillis(300),
                                Duration.ofMillis(1800),
                                Duration.ofMillis(300)
                        )
                )
        );
    }

    public BossPhase currentPhase(LivingEntity boss) {
        if (boss == null) return null;
        return lastPhase.get(boss.getUniqueId());
    }

    public Collection<LivingEntity> activeBosses() {
        List<LivingEntity> list = new ArrayList<>(templates.size());

        for (UUID id : templates.keySet()) {
            var e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                list.add(le);
            }
        }

        return list;
    }

    // =====================================================
    // BUFFS / WORLD / UTILS
    // =====================================================

    private void applyPhaseBuffs(LivingEntity boss, BossTemplate template, BossPhase phase) {
        ConfigurationSection cfg = template.phaseConfig(phase.id());
        if (cfg != null) buffEngine.applyPhase(boss, cfg);
    }

    private void applyWorldEffectsFromPhaseCfg(
            LivingEntity boss,
            BossTemplate template,
            ConfigurationSection phaseCfg
    ) {
        if (worldEffects == null) return;

        ConfigurationSection world = phaseCfg.getConfigurationSection("world");
        if (world == null) return;

        String weather = world.getString("weather");
        String time = world.getString("time");

        if ((weather == null || weather.isBlank()) &&
                (time == null || time.isBlank())) return;

        int radiusBlocks = Math.max(1, template.arenaRadiusChunks()) * BLOCKS_PER_CHUNK;
        worldEffects.apply(boss, radiusBlocks, weather, time);
    }

    private static void normalizeHealthToMax(LivingEntity boss) {
        AttributeInstance maxAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr == null) return;

        double max = maxAttr.getValue();
        double cur = boss.getHealth();
        boss.setHealth(Math.max(0.0, Math.min(cur, max)));
    }

    private void resetAttributesToBase(LivingEntity boss) {
        for (Attribute attr : Attribute.values()) {
            AttributeInstance inst = boss.getAttribute(attr);
            if (inst == null) continue;

            inst.getModifiers().forEach(inst::removeModifier);

            if (attr == Attribute.MOVEMENT_SPEED && inst.getBaseValue() <= 0) {
                inst.setBaseValue(0.23);
            }
            if (attr == Attribute.FOLLOW_RANGE && inst.getBaseValue() <= 0) {
                inst.setBaseValue(32.0);
            }
        }
    }
}
