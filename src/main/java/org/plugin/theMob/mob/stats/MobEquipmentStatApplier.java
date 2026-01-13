package org.plugin.theMob.mob.stats;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.item.ItemStatReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MobEquipmentStatApplier {

    private final ItemStatReader statReader;
    private final KeyRegistry keys;

    public MobEquipmentStatApplier(ItemStatReader statReader, KeyRegistry keys) {
        this.statReader = statReader;
        this.keys = keys;
    }

    public void apply(LivingEntity mob, List<ItemStack> equipment) {
        if (mob == null || equipment == null || equipment.isEmpty()) return;

        // =====================================================
        // 1️⃣ Aggregate stats from all equipment items
        // =====================================================
        Map<String, Double> aggregated = new HashMap<>();

        for (ItemStack it : equipment) {
            if (it == null || it.getType().isAir()) continue;

            Map<String, Double> stats = statReader.read(it);
            if (stats == null || stats.isEmpty()) continue;

            stats.forEach((stat, value) ->
                    aggregated.merge(stat, value, Double::sum)
            );
        }

        if (aggregated.isEmpty()) return;

        // =====================================================
        // 2️⃣ Write allowed stats into mob PDC (authoritative)
        // =====================================================
        aggregated.forEach((stat, value) -> {
            NamespacedKey key = keys.mobStat(stat);
            if (key == null) return;

            mob.getPersistentDataContainer().set(
                    key,
                    PersistentDataType.DOUBLE,
                    value
            );
        });

        // =====================================================
        // 3️⃣ RESET vanilla attributes (VERY IMPORTANT)
        // =====================================================
        resetAttribute(mob, Attribute.ARMOR);
        resetAttribute(mob, Attribute.MAX_HEALTH);
        resetAttribute(mob, Attribute.KNOCKBACK_RESISTANCE);
        resetAttribute(mob, Attribute.MOVEMENT_SPEED);

        // =====================================================
        // 4️⃣ Apply aggregated attributes ONCE
        // =====================================================
        applyAttribute(mob, Attribute.ARMOR, aggregated.get("armor"));
        applyAttribute(mob, Attribute.MAX_HEALTH, aggregated.get("health"));
        applyAttribute(mob, Attribute.KNOCKBACK_RESISTANCE, aggregated.get("knockback_resistance"));

        if (aggregated.containsKey("movement_speed")) {
            AttributeInstance inst = mob.getAttribute(Attribute.MOVEMENT_SPEED);
            if (inst != null) {
                double base = inst.getDefaultValue();
                double bonus = aggregated.get("movement_speed");
                inst.setBaseValue(Math.min(0.7, base + bonus));
            }
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private void resetAttribute(LivingEntity mob, Attribute attr) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(inst.getDefaultValue());
        }
    }

    private void applyAttribute(LivingEntity mob, Attribute attr, Double value) {
        if (value == null || value == 0) return;

        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;

        inst.setBaseValue(inst.getBaseValue() + value);

        // Health special handling
        if (attr == Attribute.MAX_HEALTH) {
            mob.setHealth(Math.min(inst.getBaseValue(), mob.getHealth() + value));
        }
    }
}
