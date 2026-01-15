package org.plugin.theMob.mob.ability;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.plugin.theMob.core.KeyRegistry;

import java.util.Locale;

public final class AbilityEngine {

    private final KeyRegistry keys;

    public AbilityEngine(KeyRegistry keys) {
        this.keys = keys;
    }

    // =====================================================
    // APPLY ABILITIES (PHASE ENTER)
    // =====================================================
    public void apply(LivingEntity mob, ConfigurationSection phaseCfg) {
        if (mob == null || phaseCfg == null) return;

        ConfigurationSection abilities = phaseCfg.getConfigurationSection("abilities");
        if (abilities == null) return;

        // -------------------------
        // FLAGS
        // -------------------------
        mob.setSilent(abilities.getBoolean("silent", mob.isSilent()));
        mob.setInvulnerable(abilities.getBoolean("invulnerable", mob.isInvulnerable()));
        mob.setGravity(abilities.getBoolean("gravity", mob.hasGravity()));
        mob.setAI(!abilities.getBoolean("no-ai", false));
        mob.setGlowing(abilities.getBoolean("glowing", mob.isGlowing()));

        // -------------------------
        // POTION EFFECTS
        // -------------------------
        ConfigurationSection effects = abilities.getConfigurationSection("effects");
        if (effects != null) {
            applyPotion(mob, effects, "speed", PotionEffectType.SPEED);
            applyPotion(mob, effects, "strength", PotionEffectType.STRENGTH);
            applyPotion(mob, effects, "resistance", PotionEffectType.RESISTANCE);
            applyPotion(mob, effects, "regeneration", PotionEffectType.REGENERATION);
            applyPotion(mob, effects, "jump-boost", PotionEffectType.JUMP_BOOST);
            applyPotion(mob, effects, "fire-resistance", PotionEffectType.FIRE_RESISTANCE);
            applyPotion(mob, effects, "invisibility", PotionEffectType.INVISIBILITY);
            applyPotion(mob, effects, "water-breathing", PotionEffectType.WATER_BREATHING);
            applyPotion(mob, effects, "slow-falling", PotionEffectType.SLOW_FALLING);
        }

        // -------------------------
        // IMMUNITIES (MARK ONLY)
        // -------------------------
        ConfigurationSection immune = abilities.getConfigurationSection("immune");
        if (immune != null) {
            immune.getKeys(false).forEach(key -> {
                mob.getPersistentDataContainer().set(
                        keys.mobStat("immune_" + normalize(key)),
                        PersistentDataType.INTEGER,
                        immune.getBoolean(key) ? 1 : 0
                );
            });
        }
    }

    // =====================================================
    // ROLLBACK (PHASE LEAVE)
    // =====================================================
    public void rollback(LivingEntity mob) {
        if (mob == null) return;

        mob.setInvulnerable(false);
        mob.setSilent(false);
        mob.setGlowing(false);
        mob.setAI(true);
        mob.setGravity(true);

        for (PotionEffect effect : mob.getActivePotionEffects()) {
            mob.removePotionEffect(effect.getType());
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void applyPotion(
            LivingEntity mob,
            ConfigurationSection sec,
            String key,
            PotionEffectType type
    ) {
        if (!sec.contains(key)) return;

        if (type == null) return;

        int amp = sec.getInt(key + ".amplifier", sec.getInt(key, 0));
        boolean permanent = sec.getBoolean(key + ".permanent", false);

        if (amp <= 0 && !sec.getBoolean(key, false)) return;

        mob.addPotionEffect(
                new PotionEffect(
                        type,
                        permanent ? Integer.MAX_VALUE : 20 * 60,
                        amp,
                        false,
                        false
                )
        );
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace("-", "_");
    }
}
