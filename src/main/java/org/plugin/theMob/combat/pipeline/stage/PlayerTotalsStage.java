package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.entity.Player;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public final class PlayerTotalsStage implements DamageStage {

    private final Function<Player, Map<String, Double>> totalsProvider;

    public PlayerTotalsStage(Function<Player, Map<String, Double>> totalsProvider) {
        this.totalsProvider = totalsProvider;
    }

    @Override
    public void apply(DamageContext ctx) {
        if (ctx == null || totalsProvider == null) return;

        Player p = ctx.attacker();
        if (p == null || !p.isOnline()) return;

        Map<String, Double> totals = totalsProvider.apply(p);
        if (totals == null || totals.isEmpty()) {
            ctx.setPlayerTotals(Collections.emptyMap());
            return;
        }

        // defensive copy to prevent external mutation during pipeline
        ctx.setPlayerTotals(Map.copyOf(totals));
    }
}
