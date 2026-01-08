package org.plugin.theMob.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;

import java.util.EnumMap;
import java.util.Map;

public final class BossImmunityListener implements Listener {

    private final MobManager mobs;
    private final BossPhaseController phases;

    private static final Map<EntityDamageEvent.DamageCause, String> IMMUNITY_KEYS =
            new EnumMap<>(EntityDamageEvent.DamageCause.class);

    static {
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.FIRE, "fire");
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.FIRE_TICK, "fire");
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.LAVA, "fire");

        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.FALL, "fall");
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.DROWNING, "drowning");
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.POISON, "poison");
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.LIGHTNING, "lightning");

        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.PROJECTILE, "projectile");
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, "explosion");
        IMMUNITY_KEYS.put(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, "explosion");
    }

    public BossImmunityListener(MobManager mobs, BossPhaseController phases) {
        this.mobs = mobs;
        this.phases = phases;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;
        if (!boss.isValid() || boss.isDead()) return;

        BossPhase phase = phases.currentPhase(boss);
        if (phase == null) return;

        ConfigurationSection imm = phase.cfg().getConfigurationSection("immunities");
        if (imm == null || imm.getKeys(false).isEmpty()) return;

        String key = IMMUNITY_KEYS.get(event.getCause());
        if (key == null) return;

        if (imm.getBoolean(key, false)) {
            event.setCancelled(true);
        }
    }
}
