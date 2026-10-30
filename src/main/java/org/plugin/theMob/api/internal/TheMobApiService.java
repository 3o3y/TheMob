package org.plugin.theMob.api.internal;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

/** Stable service facade: references remain usable across /mob reload. */
public final class TheMobApiService implements TheMobApi {
  private final TheMob plugin;

  public TheMobApiService(TheMob plugin) {
    this.plugin = plugin;
  }

  private void thread() {
    if (!Bukkit.isPrimaryThread())
      throw new IllegalStateException("TheMob API requires the server thread.");
    if (!plugin.isEnabled()) throw new IllegalStateException("TheMob is disabled.");
  }

  public int apiVersion() {
    return API_VERSION;
  }

  public MobDefinition definition(String id) {
    var config = plugin.mobs().mobConfigById(id);
    if (config == null) return null;
    try {
      var type =
          org.bukkit.entity.EntityType.valueOf(
              config
                  .getString("base-type", config.getString("type", "ZOMBIE"))
                  .toUpperCase(Locale.ROOT));
      if (!type.isAlive() || !type.isSpawnable()) return null;
      return new MobDefinition(
          id.toLowerCase(Locale.ROOT),
          config.getString("name", id),
          type,
          plugin.mobs().hasBossTemplate(id));
    } catch (IllegalArgumentException failure) {
      return null;
    }
  }

  public Collection<MobDefinition> definitions() {
    thread();
    return plugin.mobs().registeredIds().stream()
        .map(this::definition)
        .filter(Objects::nonNull)
        .toList();
  }

  public Optional<MobSnapshot> mob(LivingEntity entity) {
    thread();
    if (entity == null) return Optional.empty();
    var data = entity.getPersistentDataContainer();
    boolean minion =
        data.getOrDefault(new NamespacedKey(plugin, "minion"), PersistentDataType.BYTE, (byte) 0)
            == 1;
    String id = plugin.mobs().mobIdOf(entity);
    if (id == null && minion)
      id = data.get(new NamespacedKey(plugin, "api_parent_mob"), PersistentDataType.STRING);
    if (id == null) return Optional.empty();
    String phase = data.get(new NamespacedKey(plugin, "api_phase"), PersistentDataType.STRING);
    String source = data.get(new NamespacedKey(plugin, "api_source"), PersistentDataType.STRING);
    SpawnReason reason = SpawnReason.MANUAL;
    try {
      if (source != null) reason = SpawnReason.valueOf(source);
    } catch (IllegalArgumentException ignored) {
    }
    String name = data.get(plugin.keys().BASE_NAME, PersistentDataType.STRING);
    return Optional.of(
        new MobSnapshot(
            entity.getUniqueId(),
            id,
            name == null ? id : name,
            !minion && plugin.mobs().isBoss(entity),
            minion,
            phase,
            reason));
  }

  public Collection<MobSnapshot> activeMobs() {
    thread();
    var all = new ArrayList<MobSnapshot>();
    for (var entity : plugin.mobs().getAllLivingMobs())
      if (entity.isValid() && !entity.isDead()) mob(entity).ifPresent(all::add);
    return List.copyOf(all);
  }

  public Optional<LivingEntity> spawn(String id, Location location) {
    return spawn(id, location, SpawnReason.API);
  }

  public Optional<LivingEntity> spawn(String id, Location location, SpawnReason reason) {
    thread();
    if (id == null
        || location == null
        || location.getWorld() == null
        || !Double.isFinite(location.getX())
        || !Double.isFinite(location.getY())
        || !Double.isFinite(location.getZ())
        || !Float.isFinite(location.getYaw())
        || !Float.isFinite(location.getPitch())
        || !plugin.mobs().mobExists(id)) return Optional.empty();
    return Optional.ofNullable(
        plugin.mobs().spawnCustomMob(id, "api:" + reason.name(), location.clone()));
  }

  public boolean remove(UUID id) {
    thread();
    if (id == null) return false;
    var entity = Bukkit.getEntity(id);
    if (!(entity instanceof LivingEntity mob) || mob(mob).isEmpty()) return false;
    plugin.mobs().removeWithoutRewards(mob);
    return true;
  }

  public Collection<IntegrationStatus> integrations() {
    thread();
    return plugin.integrations() == null ? List.of() : plugin.integrations().statuses();
  }

  @Override
  public boolean supportsAdminMenu() {
    thread();
    return true;
  }

  @Override
  public boolean openAdminMenu(org.bukkit.entity.Player player) {
    thread();
    return player != null && plugin.adminMenus() != null && plugin.adminMenus().open(player);
  }

  @Override
  public boolean supportsCombatObjectives() {
    thread();
    return true;
  }

  @Override
  public int definitionLevel(String id) {
    thread();
    var cfg = id == null ? null : plugin.mobs().mobConfigById(id);
    return cfg == null
        ? 0
        : org.plugin.theMob.compat.Numbers.clamp(
            cfg.getInt("level", plugin.mobs().hasBossTemplate(id) ? 5 : 1), 1, 1_000_000);
  }

  @Override
  public Collection<String> itemIds() {
    thread();
    return plugin.gear() == null ? List.of() : plugin.gear().ids().stream().sorted().toList();
  }

  @Override
  public Optional<org.bukkit.inventory.ItemStack> createItem(String id) {
    thread();
    if (plugin.integrations() != null && plugin.integrations().items() != null)
      return plugin.integrations().items().create(id);
    return id == null || plugin.gear() == null
        ? Optional.empty()
        : Optional.ofNullable(plugin.gear().item(id));
  }

  @Override
  public Collection<String> itemProviders() {
    thread();
    return plugin.integrations() == null || plugin.integrations().items() == null
        ? List.of("themob")
        : plugin.integrations().items().ids();
  }

  @Override
  public Optional<ItemSnapshot> inspectItem(org.bukkit.inventory.ItemStack item) {
    thread();
    return ItemSnapshots.inspect(plugin, item);
  }

  @Override
  public boolean supportsDungeonRegions() {
    thread();
    return true;
  }

  @Override
  public Collection<DungeonRegion> dungeons() {
    thread();
    return plugin.dungeons() == null ? List.of() : plugin.dungeons().regions();
  }

  @Override
  public Optional<DungeonRegion> dungeonAt(Location location) {
    thread();
    return plugin.dungeons() == null ? Optional.empty() : plugin.dungeons().regionAt(location);
  }

  public SpawnReason reason(String source) {
    if (source == null) return SpawnReason.MANUAL;
    if (source.equals("egg")) return SpawnReason.EGG;
    if (source.equals("spawner")) return SpawnReason.SPAWNER;
    if (source.startsWith("api:")) {
      try {
        return SpawnReason.valueOf(source.substring(4));
      } catch (IllegalArgumentException ignored) {
      }
    }
    return SpawnReason.AUTO_SPAWN;
  }

  public boolean beforeSpawn(String id, Location location, SpawnReason reason) {
    var definition = definition(id);
    if (definition == null) return false;
    var event = new TheMobPreSpawnEvent(definition, location, reason);
    Bukkit.getPluginManager().callEvent(event);
    return !event.isCancelled();
  }

  public void spawned(LivingEntity entity, SpawnReason reason) {
    entity
        .getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "api_source"), PersistentDataType.STRING, reason.name());
    mob(entity)
        .ifPresent(
            mob -> {
              plugin.mobs().track(entity);
              Bukkit.getPluginManager().callEvent(new TheMobSpawnEvent(entity, mob));
              if (mob.boss() && mob.phaseId() != null)
                Bukkit.getPluginManager()
                    .callEvent(new TheMobPhaseChangeEvent(entity, mob, null, mob.phaseId()));
            });
  }

  public void phase(LivingEntity entity, String previous, String next) {
    entity
        .getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "api_phase"), PersistentDataType.STRING, next);
    mob(entity)
        .ifPresent(
            mob ->
                Bukkit.getPluginManager()
                    .callEvent(
                        new TheMobPhaseChangeEvent(
                            entity,
                            mob,
                            previous,
                            next,
                            previous == null || plugin.objectives() == null
                                ? List.of()
                                : plugin.objectives().participants(entity))));
  }

  public void died(LivingEntity entity) {
    mob(entity)
        .ifPresent(
            mob ->
                Bukkit.getPluginManager()
                    .callEvent(new TheMobDeathEvent(entity, mob, entity.getKiller())));
    plugin.mobs().untrack(entity.getUniqueId());
  }
}
