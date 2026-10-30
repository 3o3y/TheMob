package org.plugin.theMob.combat.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DamagePipeline {
  private final List<DamageStage> stages = new ArrayList<>();

  public DamagePipeline add(DamageStage stage) {
    if (stage != null) {
      this.stages.add(stage);
    }

    return this;
  }

  public void run(DamageContext ctx) {
    if (ctx != null) {
      for (DamageStage stage : this.stages) {
        if (ctx.cancelled()) {
          return;
        }

        try {
          stage.apply(ctx);
        } catch (Throwable var5) {
          var5.printStackTrace();
        }
      }
    }
  }

  public List<DamageStage> stages() {
    return Collections.unmodifiableList(this.stages);
  }
}
