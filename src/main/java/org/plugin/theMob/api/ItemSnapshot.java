package org.plugin.theMob.api;

import java.util.Objects;
import org.bukkit.Material;

/** Immutable item identity. Stars are 0 for an unstarred item; amount counts individual items. */
public record ItemSnapshot(
    String id, Material material, String rarity, int stars, int amount, boolean custom) {
  public ItemSnapshot {
    Objects.requireNonNull(id);
    Objects.requireNonNull(material);
    Objects.requireNonNull(rarity);
    if (id.isBlank() || material.isAir() || stars < 0 || stars > 5 || amount < 1)
      throw new IllegalArgumentException("Invalid item snapshot");
  }
}
