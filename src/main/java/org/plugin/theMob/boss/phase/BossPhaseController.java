// src/main/java/org/plugin/theMob/boss/phase/BossPhaseController.java
package org.plugin.theMob.boss.phase;

import org.bukkit.Sound;
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

    private final Map<UUID, String> lastPhase = new HashMap<>();
    private final Map<UUID, BossTemplate> templates = new HashMap<>();

    public BossPhaseController(BossPhaseResolver resolver, BossActionEngine actionEngine, BossBarService bars) {
        this.resolver = resolver;
        this.actionEngine = actionEngine;
        this.bars = bars;
    }

    // SPAWN
    public void onBossSpawn(LivingEntity boss, BossTemplate template) {
        if (boss == null || template == null) return;

        UUID id = boss.getUniqueId();
        templates.put(id, template);

        if (bars != null) bars.registerBoss(boss);

        BossPhase phase = resolver.resolve(boss, template);
        if (phase == null) return;

        lastPhase.put(id, phase.id());
        if (bars != null) bars.setPhaseTitle(boss, phase.title());

        actionEngine.onPhaseEnter(boss, phase);
        if (isPhaseVisible(phase.id())) showPhaseTitle(boss, phase);

        if (bars != null) bars.markDirty(boss);
    }

    // UPDATE (damage / heal / move tick via lifecycle tracker)
    public void onBossUpdate(LivingEntity boss) {
        if (boss == null) return;

        UUID id = boss.getUniqueId();
        BossTemplate template = templates.get(id);
        if (template == null) return;

        BossPhase phase = resolver.resolve(boss, template);
        if (phase == null) return;

        String prev = lastPhase.get(id);
        if (prev == null || !prev.equals(phase.id())) {
            lastPhase.put(id, phase.id());
            if (bars != null) bars.setPhaseTitle(boss, phase.title());
            if (isPhaseVisible(phase.id())) showPhaseTitle(boss, phase);
            actionEngine.onPhaseEnter(boss, phase);
        }

        if (bars != null) bars.markDirty(boss);
    }

    // =====================================================
// READ-ONLY ACCESS
// =====================================================
    public BossPhase currentPhase(LivingEntity boss) {
        if (boss == null) return null;

        BossTemplate template = templates.get(boss.getUniqueId());
        if (template == null) return null;

        return resolver.resolve(boss, template);
    }

    // =====================================================
// ACTIVE BOSSES (READ-ONLY)
// =====================================================
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


    // DEATH
    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;

        UUID id = boss.getUniqueId();
        lastPhase.remove(id);
        templates.remove(id);

        if (bars != null) {
            bars.setPhaseTitle(boss, null);
            bars.unregisterBoss(boss);
        }

    }

    private boolean isPhaseVisible(String phaseId) {
        if (phaseId == null) return false;
        return !phaseId.equalsIgnoreCase("phase1");
    }

    private void showPhaseTitle(LivingEntity boss, BossPhase phase) {
        String phaseId = phase.id();
        String titleText = phaseId.equalsIgnoreCase("phase4") ? "§4§lLAST PHASE" : "§c§l" + phase.title();
        String subtitle = phaseId.equalsIgnoreCase("phase4") ? "§c§lKILL THE BOSS!!!" : "§7Boss phase changed";

        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > (30 * 30)) continue;
            p.sendTitle(titleText, subtitle, 10, 40, 10);
            p.playSound(
                    p.getLocation(),
                    Sound.ITEM_GOAT_HORN_SOUND_1,
                    0.6f,
                    phaseId.equalsIgnoreCase("phase4") ? 0.6f : 1.2f
            );
        }
    }
}
