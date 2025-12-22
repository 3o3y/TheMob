package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.entity.Player;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

import java.util.Map;
import java.util.function.Function;

public final class PlayerTotalsStage implements DamageStage {

    private final Function<Player, Map<String, Double>> totalsProvider;
    public PlayerTotalsStage(Function<Player, Map<String, Double>> totalsProvider) {
        this.totalsProvider = totalsProvider;
    }
    @Override
    public void apply(DamageContext ctx) {
        Player p = ctx.attacker();
        if (p == null) return;
        if (totalsProvider == null) return;
        Map<String, Double> totals = totalsProvider.apply(p);
        ctx.setPlayerTotals(totals);
    }
}
