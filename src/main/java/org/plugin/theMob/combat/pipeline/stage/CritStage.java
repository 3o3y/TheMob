package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;

import java.util.concurrent.ThreadLocalRandom;

public final class CritStage implements DamageStage {

    @Override
    public void apply(DamageContext ctx) {
        if (ctx == null || ctx.isCrit()) return;

        Player p = ctx.attacker();
        if (p == null || !p.isOnline()) return;

        // =========================
        // CRIT CHANCE (%)
        // =========================
        double chance = ctx.stat("crit_chance"); // 8 = 8%
        if (chance <= 0.0) return;

        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= chance) return;

        // =========================
        // CRIT MULTIPLIER
        // =========================
        double mul = ctx.stat("crit_multiplier");
        if (mul < 1.0) mul = 1.0;

        // =========================
        // APPLY (MULTIPLY, NOT ADD)
        // =========================
        ctx.setCrit(true);
        ctx.setDamage(ctx.damage() * mul);

        // =========================
        // FEEDBACK
        // =========================
        p.playSound(
                p.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_CRIT,
                1.0f,
                1.2f
        );
    }
}
