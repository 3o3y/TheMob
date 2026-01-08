package org.plugin.theMob.combat.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DamagePipeline {

    private final List<DamageStage> stages = new ArrayList<>();

    public DamagePipeline add(DamageStage stage) {
        if (stage != null) {
            stages.add(stage);
        }
        return this;
    }

    public void run(DamageContext ctx) {
        if (ctx == null) return;

        for (DamageStage stage : stages) {
            if (ctx.cancelled()) return;

            try {
                stage.apply(ctx);
            } catch (Throwable t) {
                // Pipeline muss weiterleben – Fehler isolieren
                t.printStackTrace();
            }
        }
    }

    public List<DamageStage> stages() {
        return Collections.unmodifiableList(stages);
    }
}
