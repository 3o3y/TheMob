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
            bars.setPhaseTitle(boss, phase.title()); // raw title
            bars.markDirty(boss);
        }

        // APPLY BUFFS FOR INITIAL PHASE
        applyPhaseBuffs(boss, template, phase);

        ConfigurationSection cfg = template.phaseConfig(phase.id());
        if (cfg != null) {
            abilityEngine.apply(boss, cfg);
            combatEngine.apply(boss, cfg);
            applyWorldEffectsFromPhaseCfg(boss, cfg);
        }

        actionEngine.onPhaseEnter(boss, phase);
    }

    public void onBossUpdate(LivingEntity boss) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        // keep world effects stable while boss is alive
        if (worldEffects != null) {
            worldEffects.tick();
        }

        UUID id = boss.getUniqueId();
        BossTemplate template = templates.get(id);
        if (template == null) return;

        BossPhase next = resolver.resolve(boss, template);
        if (next == null) return;

        BossPhase previous = lastPhase.get(id);

        if (previous == null || !previous.id().equals(next.id())) {

            // rollback old phase buffs first
            combatEngine.rollback(boss);
            abilityEngine.rollback(boss);
            buffEngine.rollbackPhase(boss);

            // reset old world effects (phase change)
            if (worldEffects != null) {
                worldEffects.resetAll();
            }

            if (previous != null) {
                actionEngine.onPhaseLeave(boss, previous);
            }

            lastPhase.put(id, next);

            if (bars != null) {
                bars.setPhaseTitle(boss, next.title());
                bars.markDirty(boss);
            }

            // apply new phase buffs
            applyPhaseBuffs(boss, template, next);

            ConfigurationSection cfg = template.phaseConfig(next.id());
            if (cfg != null) {
                abilityEngine.apply(boss, cfg);
                combatEngine.apply(boss, cfg);
                applyWorldEffectsFromPhaseCfg(boss, cfg);
            }

            showPhaseTitle(boss, next);
            actionEngine.onPhaseEnter(boss, next);

        } else {
            if (bars != null) bars.markDirty(boss);
        }
    }

    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;

        UUID id = boss.getUniqueId();

        BossPhase previous = lastPhase.remove(id);
        templates.remove(id);

        // rollback buffs on death too
        combatEngine.rollback(boss);
        abilityEngine.rollback(boss);
        buffEngine.rollbackPhase(boss);

        // reset world effects on death too
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
    // QUERIES (NO SIDE EFFECTS)
    // =====================================================

    public BossPhase currentPhase(LivingEntity boss) {
        if (boss == null) return null;
        return lastPhase.get(boss.getUniqueId());
    }

    public Collection<LivingEntity> activeBosses() {
        List<LivingEntity> list = new ArrayList<>(templates.size());

        for (UUID id : templates.keySet()) {
            for (var world : Bukkit.getWorlds()) {
                var e = world.getEntity(id);
                if (e instanceof LivingEntity le && le.isValid()) {
                    list.add(le);
                    break;
                }
            }
        }
        return list;
    }

    // =====================================================
    // BUFFS (APPLY)
    // =====================================================

    private void applyPhaseBuffs(LivingEntity boss, BossTemplate template, BossPhase phase) {
        if (boss == null || template == null || phase == null) return;

        ConfigurationSection phaseCfg = template.phaseConfig(phase.id());
        if (phaseCfg != null) {
            buffEngine.applyPhase(boss, phaseCfg);
        }
    }

    // =====================================================
    // WORLD EFFECTS (APPLY)
    // =====================================================

    private void applyWorldEffectsFromPhaseCfg(LivingEntity boss, ConfigurationSection phaseCfg) {
        if (worldEffects == null || boss == null || phaseCfg == null) return;

        ConfigurationSection world = phaseCfg.getConfigurationSection("world");
        if (world == null) return;

        double radius = world.getDouble("radius", 24.0);
        String weather = world.getString("weather", null);
        String time = world.getString("time", null);

        // If none are set, do nothing (no reset here, because reset is handled on phase change/death)
        if ((weather == null || weather.isBlank()) && (time == null || time.isBlank())) return;

        worldEffects.apply(boss, radius, weather, time);
    }

    // =====================================================
    // VISUALS
    // =====================================================

    private void showPhaseTitle(LivingEntity boss, BossPhase phase) {
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > 30 * 30) continue;
            showPhaseTitleToPlayer(p, boss, phase);
        }
    }

    private void showPhaseTitleToPlayer(Player player, LivingEntity boss, BossPhase phase) {

        String rawTitle = "{phase_title}";
        String rawSubtitle = "{mob_name}";

        Component title = Placeholder.resolveComponent(rawTitle, boss, phase, player);
        Component subtitle = Placeholder.resolveComponent(rawSubtitle, boss, phase, player);

        player.showTitle(
                Title.title(
                        title,
                        subtitle,
                        Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(2000),
                                Duration.ofMillis(500)
                        )
                )
        );
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
