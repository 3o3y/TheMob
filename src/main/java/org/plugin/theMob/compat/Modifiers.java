package org.plugin.theMob.compat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.attribute.AttributeModifier;

/** Own modifiers retain stable keys on modern Bukkit and deterministic UUIDs on older Bukkit. */
public final class Modifiers {
  private Modifiers() {}

  public static AttributeModifier additive(NamespacedKey key, double value) {
    try {
      Class<?> group = Class.forName("org.bukkit.inventory.EquipmentSlotGroup");
      return AttributeModifier.class
          .getConstructor(
              NamespacedKey.class, double.class, AttributeModifier.Operation.class, group)
          .newInstance(
              key, value, AttributeModifier.Operation.ADD_NUMBER, group.getField("ANY").get(null));
    } catch (ClassNotFoundException | NoSuchMethodException legacy) {
      try {
        return AttributeModifier.class
            .getConstructor(
                UUID.class, String.class, double.class, AttributeModifier.Operation.class)
            .newInstance(
                UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)),
                key.toString(),
                value,
                AttributeModifier.Operation.ADD_NUMBER);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Legacy modifier API", failure);
      }
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Modifier API", failure);
    }
  }

  public static String identity(AttributeModifier modifier) {
    if ((Object) modifier instanceof Keyed keyed) return keyed.getKey().toString();
    try {
      return (String) AttributeModifier.class.getMethod("getName").invoke(modifier);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Modifier identity API", failure);
    }
  }
}
