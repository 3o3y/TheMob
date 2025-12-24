package org.plugin.theMob.boss.lifecycle;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.core.MainThread;
import org.plugin.theMob.mob.MobManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BossLifecycleTracker {

    private final Plugin plugin;
    private final MobManager mobs;
    private final BossBarService bars;
    private final BossPhaseController phases;

    private final Set<UUID> tracked = new HashSet<>();

    public BossLifecycleTracker(
            Plugin plugin,
            MobManager mobs,
            BossBarService bars,
            BossPhaseController phases
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.bars = bars;
        this.phases = phases;
    }

    // ----------------------------
    // spawn → move → fight → death
    // ----------------------------
    public void onSpawn(LivingEntity mob) {
        if (mob == null) return;
        if (!mobs.isBoss(mob)) return;

        tracked.add(mob.getUniqueId());

        BossTemplate tpl = mobs.bossTemplate(mobs.mobIdOf(mob));
        if (tpl != null) {
            MainThread.run(plugin, () -> phases.onBossSpawn(mob, tpl));
        } else {
            MainThread.run(plugin, () -> bars.registerBoss(mob));
        }
    }

    public void onMoveTick() {
        // hardening: remove invalid/dead safely; refresh bars if needed
        tracked.removeIf(id -> {
            var e = Bukkit.getEntity(id);
            if (!(e instanceof LivingEntity boss)) return true;
            if (!boss.isValid() || boss.isDead()) {
                MainThread.run(plugin, () -> {
                    bars.unregisterBoss(boss);
                    phases.onBossDeath(boss);
                });
                return true;
            }
            // mark dirty so BossBarService refreshes title/progress
            bars.markDirty(boss);
            return false;
        });
    }

    public void onFight(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;
        MainThread.run(plugin, () -> phases.onBossUpdate(boss));
    }

    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;
        MainThread.run(plugin, () -> phases.onBossUpdate(boss));
    }

    public void onDeath(LivingEntity boss) {
        if (boss == null) return;
        if (!mobs.isBoss(boss)) return;

        tracked.remove(boss.getUniqueId());
        MainThread.run(plugin, () -> {
            phases.onBossDeath(boss);
            bars.unregisterBoss(boss);
        });
    }

    public void shutdown() {
        tracked.clear();
    }
}
