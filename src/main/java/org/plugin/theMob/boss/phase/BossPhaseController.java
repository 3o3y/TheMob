// src/main/java/org/plugin/theMob/boss/phase/BossPhaseController.java
package org.plugin.theMob.boss.phase;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BossPhaseController {

    private final BossPhaseResolver resolver;
    private final BossActionEngine actionEngine;
    private final BossBarService bars;

    // 🔁 STATE
    private final Map<UUID, BossPhase> lastPhase = new HashMap<>();
    private final Map<UUID, BossTemplate> templates = new HashMap<>();

    public BossPhaseController(
            BossPhaseResolver resolver,
            BossActionEngine actionEngine,
            BossBarService bars
    ) {
        this.resolver = resolver;
        this.actionEngine = actionEngine;
        this.bars = bars;
    }

    // =====================================================
    // SPAWN
    // =====================================================
    public void onBossSpawn(LivingEntity boss, BossTemplate template) {
        if (boss == null || template == null) return;

        UUID id = boss.getUniqueId();
        templates.put(id, template);

        if (bars != null) bars.registerBoss(boss);

        BossPhase phase = resolver.resolve(boss, template);
        if (phase == null) return;

        lastPhase.put(id, phase);

        if (bars != null) bars.setPhaseTitle(boss, phase.title());
        if (isPhaseVisible(phase.id())) showPhaseTitle(boss, phase);

        // 🔥 Effects + Weather + Sound → BossActionEngine
        actionEngine.onPhaseEnter(boss, phase);

        if (bars != null) bars.markDirty(boss);
    }

    // =====================================================
    // UPDATE (HP CHANGE)
    // =====================================================
    public void onBossUpdate(LivingEntity boss) {
        if (boss == null) return;

        UUID id = boss.getUniqueId();
        BossTemplate template = templates.get(id);
        if (template == null) return;

        BossPhase next = resolver.resolve(boss, template);
        if (next == null) return;

        BossPhase previous = lastPhase.get(id);

        if (previous == null || !previous.id().equals(next.id())) {

            if (previous != null) {
                actionEngine.onPhaseLeave(boss, previous);
            }

            lastPhase.put(id, next);

            if (bars != null) bars.setPhaseTitle(boss, next.title());
            if (isPhaseVisible(next.id())) showPhaseTitle(boss, next);

            actionEngine.onPhaseEnter(boss, next);
        }

        if (bars != null) bars.markDirty(boss);
    }

    // =====================================================
    // DEATH
    // =====================================================
    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;

        UUID id = boss.getUniqueId();

        BossPhase previous = lastPhase.remove(id);
        templates.remove(id);

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
    // ACCESS
    // =====================================================
    public BossPhase currentPhase(LivingEntity boss) {
        if (boss == null) return null;
        return lastPhase.get(boss.getUniqueId());
    }

    public Iterable<LivingEntity> activeBosses() {
        return templates.keySet().stream()
                .map(uuid -> {
                    for (var world : org.bukkit.Bukkit.getWorlds()) {
                        var e = world.getEntity(uuid);
                        if (e instanceof LivingEntity le) return le;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // =====================================================
    // VISUALS (NO SOUND!)
    // =====================================================
    private boolean isPhaseVisible(String phaseId) {
        return phaseId != null && !phaseId.equalsIgnoreCase("phase1");
    }

    private void showPhaseTitle(LivingEntity boss, BossPhase phase) {
        String title = phase.id().equalsIgnoreCase("phase4")
                ? "§4§lLAST PHASE"
                : "§c§l" + phase.title();

        String subtitle = phase.id().equalsIgnoreCase("phase4")
                ? "§c§lKILL THE BOSS!!!"
                : "§7Boss phase changed";

        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > 30 * 30) continue;
            p.sendTitle(title, subtitle, 10, 40, 10);
        }
    }
}
