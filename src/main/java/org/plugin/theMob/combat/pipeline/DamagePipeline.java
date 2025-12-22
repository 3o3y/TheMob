package org.plugin.theMob.combat.pipeline;

import java.util.ArrayList;
import java.util.List;

public final class DamagePipeline {

    private final List<DamageStage> stages = new ArrayList<>();
    public DamagePipeline add(DamageStage stage) {
        if (stage != null) stages.add(stage);
        return this;
    }
    public void run(DamageContext ctx) {
        for (DamageStage s : stages) {
            if (ctx.cancelled()) return;
            s.apply(ctx);
        }
    }
}
