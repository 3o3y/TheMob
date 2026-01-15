package org.plugin.theMob.boss.phase;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

public final class AttributeSnapshot {

    private final Map<String, Double> values = new HashMap<>();

    public static AttributeSnapshot capture(LivingEntity entity) {
        AttributeSnapshot snap = new AttributeSnapshot();

        for (Attribute attr : Attribute.values()) {
            AttributeInstance inst = entity.getAttribute(attr);
            if (inst != null) {
                snap.values.put(attr.name(), inst.getBaseValue());
            }
        }
        return snap;
    }

    public void restore(LivingEntity entity) {
        for (var e : values.entrySet()) {
            try {
                Attribute attr = Attribute.valueOf(e.getKey());
                AttributeInstance inst = entity.getAttribute(attr);
                if (inst != null) {
                    inst.setBaseValue(e.getValue());
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    // ===============================
    // PDC SERIALIZATION
    // ===============================
    public void writeTo(PersistentDataContainer tag) {
        for (var e : values.entrySet()) {
            tag.set(
                    new org.bukkit.NamespacedKey("themob", "attr_" + e.getKey().toLowerCase()),
                    PersistentDataType.DOUBLE,
                    e.getValue()
            );
        }
    }

    public static AttributeSnapshot readFrom(PersistentDataContainer tag) {
        AttributeSnapshot snap = new AttributeSnapshot();

        for (Attribute attr : Attribute.values()) {
            var key = new org.bukkit.NamespacedKey(
                    "themob",
                    "attr_" + attr.name().toLowerCase()
            );

            if (tag.has(key, PersistentDataType.DOUBLE)) {
                snap.values.put(
                        attr.name(),
                        tag.get(key, PersistentDataType.DOUBLE)
                );
            }
        }
        return snap;
    }
}
