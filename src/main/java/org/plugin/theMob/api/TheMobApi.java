package org.plugin.theMob.api;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Version 1. Use Bukkit ServicesManager.load(TheMobApi.class). All methods run on the server
 * thread. Depend on TheMob; compileOnly the API JAR. Do not shade or include the API in another
 * plugin.
 */
public interface TheMobApi {
  int API_VERSION = 1;

  int apiVersion();

  Collection<MobDefinition> definitions();

  Optional<MobSnapshot> mob(LivingEntity entity);

  Collection<MobSnapshot> activeMobs();

  /** Empty if cancelled, blocked by budgets/cooldown/region or the definition does not exist. */
  Optional<LivingEntity> spawn(String definitionId, Location location);

  /** Administrative removal: no kill event, loot, XP or external progress. */
  boolean remove(UUID entityId);

  Collection<IntegrationStatus> integrations();

  /** API 1.3: native Bukkit inventory admin UI; never bypasses the player's permissions. */
  default boolean supportsAdminMenu() {
    return false;
  }

  default boolean openAdminMenu(org.bukkit.entity.Player administrator) {
    return false;
  }

  /** API 1.3: native and registered item namespaces. Creation implies no payment or delivery. */
  default Collection<String> itemProviders() {
    return List.of("themob");
  }

  /** API 1.2: phase participants, effective damage, drop/loot events and item metadata. */
  default boolean supportsCombatObjectives() {
    return false;
  }

  /** API 1.2: configured level; 0 when the definition is unknown. */
  default int definitionLevel(String definitionId) {
    return 0;
  }

  /** API 1.2: stable Rare/Epic equipment catalog IDs, not arbitrary external inventory items. */
  default Collection<String> itemIds() {
    return List.of();
  }

  /** API 1.2: cloned catalog reward item; caller handles permissions, delivery and leftovers. */
  default Optional<org.bukkit.inventory.ItemStack> createItem(String itemId) {
    return Optional.empty();
  }

  /**
   * API 1.2: material identity fallback for ordinary items, with custom catalog/essence metadata.
   */
  default Optional<ItemSnapshot> inspectItem(org.bukkit.inventory.ItemStack item) {
    return Optional.empty();
  }

  /** Additive API 1.1 capability. Older implementations may retain the empty defaults. */
  default boolean supportsDungeonRegions() {
    return false;
  }

  /** All reserved footprints, including unfinished/failed builds; immutable snapshots. */
  default Collection<DungeonRegion> dungeons() {
    return List.of();
  }

  /**
   * Any reserved footprint at this location, regardless of height or whether a player entered by
   * command.
   */
  default Optional<DungeonRegion> dungeonAt(Location location) {
    return dungeons().stream().filter(region -> region.contains(location)).findFirst();
  }
}
