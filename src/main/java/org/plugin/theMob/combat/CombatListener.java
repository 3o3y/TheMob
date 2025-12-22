package org.plugin.theMob.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamagePipeline;
import org.plugin.theMob.combat.pipeline.stage.*;
import org.plugin.theMob.mob.MobManager;

import java.util.Map;
import java.util.function.Function;

public final class CombatListener implements Listener {

    private final DamagePipeline pipeline;
    public CombatListener(
            org.bukkit.plugin.Plugin plugin,
            MobManager mobs,
            Function<Player, Map<String, Double>> playerTotalsProvider
    ) {
        this.pipeline = new DamagePipeline()
                .add(new PlayerTotalsStage(playerTotalsProvider))
                .add(new WeaponStatsStage(plugin))
                .add(new BaseScalingStage())
                .add(new BossPhaseReadStage(mobs))
                .add(new ReceiveMultiplierStage())
                .add(new CritStage())
                .add(new LifestealStage())
                .add(new FinalizeStage());
    }
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        if (attacker == null) return;
        DamageContext ctx = new DamageContext(e, attacker, victim);
        pipeline.run(ctx);
    }
}
