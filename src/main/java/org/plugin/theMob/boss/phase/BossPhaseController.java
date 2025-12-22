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
import java.util.UUID;

public final class BossPhaseController {

    private final BossPhaseResolver resolver;
    private final BossActionEngine actionEngine;
    private final BossBarService bars;
    private final Map<UUID, String> lastPhase = new HashMap<>();
    private final Map<UUID, BossTemplate> templates = new HashMap<>();
    private final Map<UUID, String> lastTitle = new HashMap<>();
    public BossPhaseController(
            BossPhaseResolver resolver,
            BossActionEngine actionEngine,
            BossBarService bars
    ) {
        this.resolver = resolver;
        this.actionEngine = actionEngine;
        this.bars = bars;
    }
// SPAWN
    public void onBossSpawn(LivingEntity boss, BossTemplate template) {
        if (boss == null || template == null) return;
        UUID id = boss.getUniqueId();
        templates.put(id, template);
        bars.registerBoss(boss);
        BossPhase phase = resolver.resolve(boss, template);
        if (phase == null) return;
        lastPhase.put(id, phase.id());
        lastTitle.put(id, phase.title());
        bars.setPhaseTitle(boss, phase.title());
        actionEngine.onPhaseEnter(boss, phase);
        if (isPhaseVisible(phase.id())) {
            showPhaseTitle(boss, phase);
        }
        bars.markDirty(boss);
    }
// UPDATE (damage / heal)
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
            lastTitle.put(id, phase.title());
            bars.setPhaseTitle(boss, phase.title());
            if (isPhaseVisible(phase.id())) {
                showPhaseTitle(boss, phase);
            }
            actionEngine.onPhaseEnter(boss, phase);
        }
        bars.markDirty(boss);
    }
// DEATH
    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;
        UUID id = boss.getUniqueId();
        lastPhase.remove(id);
        templates.remove(id);
        lastTitle.remove(id);
        bars.setPhaseTitle(boss, null);
        bars.unregisterBoss(boss);
    }
// PHASE VISIBILITY RULES
    private boolean isPhaseVisible(String phaseId) {
        if (phaseId == null) return false;
        if (phaseId.equalsIgnoreCase("phase1")) return false;
        return true;
    }
// VISUAL OUTPUT
    private void showPhaseTitle(LivingEntity boss, BossPhase phase) {
        String phaseId = phase.id();
        String titleText;
        if (phaseId.equalsIgnoreCase("phase4")) {
            titleText = "§4§lLAST PHASE";
        } else {
            titleText = "§c§l" + phase.title();
        }
        String subtitle =
                phaseId.equalsIgnoreCase("phase4")
                        ? "§c§lKILL THE BOSS!!!"
                        : "§7Boss phase changed";
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > (30 * 30)) continue;
            p.sendTitle(
                    titleText,
                    subtitle,
                    10, 40, 10
            );
            p.playSound(
                    p.getLocation(),
                    Sound.ITEM_GOAT_HORN_SOUND_1,
                    0.6f,
                    phaseId.equalsIgnoreCase("phase4") ? 0.6f : 1.2f
            );
        }
    }
}
