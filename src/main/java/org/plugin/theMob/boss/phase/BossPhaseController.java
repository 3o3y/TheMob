package org.plugin.theMob.boss.phase;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.boss.bar.BossBarService;

import java.time.Duration;
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
            bars.setPhaseTitle(boss, phase.title()); // raw title
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
    // VISUALS (PLACEHOLDER + MINIMESSAGE)
    // =====================================================

    private void showPhaseTitle(LivingEntity boss, BossPhase phase) {
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) > 30 * 30) continue;
            showPhaseTitleToPlayer(p, boss, phase);
        }
    }

    private void showPhaseTitleToPlayer(Player player, LivingEntity boss, BossPhase phase) {

        String rawTitle = "<red><bold>{phase_title}</bold></red>";
        String rawSubtitle = "<gray>{mob_name}</gray>";

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
    }
}
