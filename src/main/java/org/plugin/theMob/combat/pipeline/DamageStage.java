package org.plugin.theMob.combat.pipeline;

@FunctionalInterface
public interface DamageStage {
    void apply(DamageContext ctx);
}
