package org.plugin.theMob.boss.phase;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;

import java.util.*;

public final class BossPhaseController {

    private final BossPhaseResolver resolver;
    private final BossActionEngine actionEngine;
    private final BossBarService bars;

    // MAIN THREAD ONLY
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
    // LIFECYCLE
    // =====================================================

    public void onBossSpawn(LivingEntity boss, BossTemplate template) {
        if (boss == null || template == null) return;

        UUID id = boss.getUniqueId();
        templates.put(id, template);

        if (bars != null) bars.registerBoss(boss);

        BossPhase phase = resolver.resolve(boss, template);
        if (phase == null) return;

        lastPhase.put(id, phase);

        if (bars != null) {
            bars.setPhaseTitle(boss, phase.title());
            bars.markDirty(boss);
        }

        actionEngine.onPhaseEnter(boss, phase);
    }

    public void onBossUpdate(LivingEntity boss) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

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

            if (bars != null) {
                bars.setPhaseTitle(boss, next.title());
                bars.markDirty(boss);
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
    // QUERIES
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
    // VISUALS
    // =====================================================

    private void showPhaseTitle(LivingEntity boss, BossPhase phase) {
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > 30 * 30) continue;
            showPhaseTitleToPlayer(p, phase);
        }
    }

    private void showPhaseTitleToPlayer(Player player, BossPhase phase) {
        player.sendTitle(
                "§c§l" + phase.title(),
                "§7Boss phase",
                10, 40, 10
        );
    }

    // =====================================================
    // SHUTDOWN
    // =====================================================

    public void shutdown() {
        lastPhase.clear();
        templates.clear();
    }
}
