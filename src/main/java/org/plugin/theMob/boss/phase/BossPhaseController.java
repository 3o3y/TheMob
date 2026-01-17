package org.plugin.theMob.boss.phase;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.combat.PhaseCombatEngine;
import org.plugin.theMob.boss.world.BossWorldEffectController;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.ability.AbilityEngine;

import java.time.Duration;
import java.util.*;

public final class BossPhaseController {

    private static final int BLOCKS_PER_CHUNK = 16;

    private final BossPhaseResolver resolver;
    private final BossActionEngine actionEngine;
    private final BossBarService bars;
    private final PhaseBuffEngine buffEngine;
    private final AbilityEngine abilityEngine;
    private final PhaseCombatEngine combatEngine;
    private final BossWorldEffectController worldEffects;

    // MAIN THREAD ONLY
    private final Map<UUID, BossPhase> lastPhase = new HashMap<>();
    private final Map<UUID, BossTemplate> templates = new HashMap<>();

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
    // LIFECYCLE
    // =====================================================

    public void onBossSpawn(LivingEntity boss, BossTemplate template) {
        if (boss == null || template == null) return;

        UUID id = boss.getUniqueId();
        templates.put(id, template);

        BossPhase phase = resolver.resolve(boss, template);
        if (phase == null) return;

        lastPhase.put(id, phase);

        if (bars != null) {
            bars.registerBoss(boss);
            bars.setPhaseTitle(boss, phase.title());
            bars.markDirty(boss);
        }

        applyPhaseBuffs(boss, template, phase);

        ConfigurationSection cfg = template.phaseConfig(phase.id());
        if (cfg != null) {
            abilityEngine.apply(boss, cfg);
            combatEngine.apply(boss, cfg);
            applyWorldEffectsFromPhaseCfg(boss, template, cfg);
        }

        actionEngine.onPhaseEnter(boss, phase);
        showPhaseTitle(boss, phase, template);
    }

    public void onBossUpdate(LivingEntity boss) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        BossTemplate template = templates.get(boss.getUniqueId());
        if (template == null) return;

        BossPhase next = resolver.resolve(boss, template);
        if (next == null) return;

        BossPhase previous = lastPhase.get(boss.getUniqueId());

        if (previous == null || !previous.id().equals(next.id())) {

            combatEngine.rollback(boss);
            abilityEngine.rollback(boss);
            buffEngine.rollbackPhase(boss);

            if (worldEffects != null) {
                worldEffects.resetAll();
            }

            if (previous != null) {
                actionEngine.onPhaseLeave(boss, previous);
            }

            lastPhase.put(boss.getUniqueId(), next);

            if (bars != null) {
                bars.setPhaseTitle(boss, next.title());
                bars.markDirty(boss);
            }

            applyPhaseBuffs(boss, template, next);

            ConfigurationSection cfg = template.phaseConfig(next.id());
            if (cfg != null) {
                abilityEngine.apply(boss, cfg);
                combatEngine.apply(boss, cfg);
                applyWorldEffectsFromPhaseCfg(boss, template, cfg);
            }

            showPhaseTitle(boss, next, template);
            actionEngine.onPhaseEnter(boss, next);

        } else {
            if (bars != null) bars.markDirty(boss);
        }
    }

    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;

        BossPhase previous = lastPhase.remove(boss.getUniqueId());
        templates.remove(boss.getUniqueId());

        combatEngine.rollback(boss);
        abilityEngine.rollback(boss);
        buffEngine.rollbackPhase(boss);

        if (worldEffects != null) {
            worldEffects.resetAll();
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
        int radiusBlocks = resolveArenaRadiusBlocks(template);
        double radiusSq = (double) radiusBlocks * radiusBlocks;

        var bossLoc = boss.getLocation();

        for (Player p : boss.getWorld().getPlayers()) {
            if (!p.isOnline()) continue;
            if (p.getLocation().distanceSquared(bossLoc) > radiusSq) continue;
            showPhaseTitleToPlayer(p, boss, phase);
        }
    }

    private int resolveArenaRadiusBlocks(BossTemplate template) {
        int chunks = Math.max(1, template.arenaRadiusChunks());
        return chunks * BLOCKS_PER_CHUNK;
    }

    private void showPhaseTitleToPlayer(Player player, LivingEntity boss, BossPhase phase) {
        Component title = Placeholder.resolveComponent("{phase_title}", boss, phase, player);
        Component subtitle = Placeholder.resolveComponent("{mob_name}", boss, phase, player);

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

    // =====================================================
    // BUFFS
    // =====================================================

    private void applyPhaseBuffs(LivingEntity boss, BossTemplate template, BossPhase phase) {
        if (boss == null || template == null || phase == null) return;

        ConfigurationSection cfg = template.phaseConfig(phase.id());
        if (cfg != null) {
            buffEngine.applyPhase(boss, cfg);
        }
    }

    // =====================================================
    // WORLD EFFECTS (ARENA ONLY)
    // =====================================================

    private void applyWorldEffectsFromPhaseCfg(
            LivingEntity boss,
            BossTemplate template,
            ConfigurationSection phaseCfg
    ) {
        if (worldEffects == null || boss == null || phaseCfg == null) return;

        ConfigurationSection world = phaseCfg.getConfigurationSection("world");
        if (world == null) return;

        String weather = world.getString("weather");
        String time = world.getString("time");

        if ((weather == null || weather.isBlank()) && (time == null || time.isBlank())) return;

        int radiusBlocks = resolveArenaRadiusBlocks(template);

        worldEffects.apply(boss, radiusBlocks, weather, time);
    }

    public BossPhase currentPhase(LivingEntity boss) {
        if (boss == null) return null;
        return lastPhase.get(boss.getUniqueId());
    }
    public Collection<LivingEntity> activeBosses() {
        List<LivingEntity> list = new ArrayList<>(templates.size());

        for (UUID id : templates.keySet()) {
            for (var world : Bukkit.getWorlds()) {
                var e = world.getEntity(id);
                if (e instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                    list.add(le);
                    break;
                }
            }
        }

        return list;
    }

    // =====================================================
    // SHUTDOWN
    // =====================================================

    public void shutdown() {
        lastPhase.clear();
        templates.clear();

        if (worldEffects != null) {
            worldEffects.resetAll();
        }
    }

}
