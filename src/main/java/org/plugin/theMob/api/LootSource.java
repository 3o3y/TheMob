package org.plugin.theMob.api;

import java.util.Objects;
import java.util.UUID;

/** Source traits, shared only by equivalent loot stacks; not an individual mob-entity UUID. */
public record LootSource(
    String definitionId,
    String phaseId,
    SpawnReason spawnReason,
    boolean boss,
    boolean minion,
    int mobLevel,
    String worldName,
    UUID killerId) {
  public LootSource {
    Objects.requireNonNull(definitionId);
    Objects.requireNonNull(spawnReason);
    Objects.requireNonNull(worldName);
    if (definitionId.isBlank() || worldName.isBlank() || mobLevel < 1 || mobLevel > 1_000_000)
      throw new IllegalArgumentException("Invalid loot source");
  }
}
