package org.plugin.theMob.integration;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;
import org.plugin.theMob.api.internal.ItemSnapshots;
import org.plugin.theMob.boss.behavior.BossTargets;

/** Bounded, event-driven combat credit and entity-only loot provenance. No world scans. */
public final class ObjectiveEventService implements Listener, AutoCloseable {
  private final TheMob plugin;
  private final NamespacedKey receipt, provenance, itemIdentity;

  private record Pending(
      MobSnapshot mob, LootSource source, ItemSnapshot identity, ItemStack prototype, long time) {}

  private final Map<String, Pending> pending = new LinkedHashMap<>();
  private boolean clearPendingScheduled;
  private final Map<ItemSpawnEvent, Pending> spawning = new IdentityHashMap<>();
  private final Map<UUID, Map<UUID, Long>> combatants = new LinkedHashMap<>();
  private final Set<EntityPickupItemEvent> pickups =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private final org.bukkit.scheduler.BukkitTask cleanup;

  public ObjectiveEventService(TheMob plugin) {
    this.plugin = plugin;
    receipt = new NamespacedKey(plugin, "loot_receipt");
    provenance = new NamespacedKey(plugin, "loot_source_v1");
    itemIdentity = new NamespacedKey(plugin, "loot_identity_v1");
    Bukkit.getPluginManager().registerEvents(this, plugin);
    cleanup = Bukkit.getScheduler().runTaskTimer(plugin, this::prune, 200, 200);
  }

  private boolean npc(Entity entity) {
    return entity.hasMetadata("NPC")
        || plugin.integrations() != null && plugin.integrations().isNpc(entity);
  }

  private boolean eligible(Player player) {
    return BossTargets.valid(player) && !npc(player);
  }

  private Player attacker(Entity entity) {
    if (entity instanceof Player player) return player;
    if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player)
      return player;
    if (entity instanceof Tameable tame && tame.getOwner() instanceof Player player) return player;
    return null;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void damage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof LivingEntity target) || npc(target)) return;
    Player player = attacker(event.getDamager());
    if (!eligible(player)) return;
    var mob = plugin.api().mob(target).orElse(null);
    if (mob == null) return;
    double amount = Math.min(target.getHealth(), event.getFinalDamage());
    if (!Double.isFinite(amount) || amount <= 0) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (event.isCancelled() || !eligible(player)) return;
              if (mob.boss()
                  && target.isValid()
                  && !target.isDead()
                  && target
                          .getPersistentDataContainer()
                          .getOrDefault(
                              plugin.keys().BOSS_DEATH_HANDLED, PersistentDataType.INTEGER, 0)
                      != 1) {
                if (!combatants.containsKey(target.getUniqueId()) && combatants.size() >= 1024)
                  combatants.remove(combatants.keySet().iterator().next());
                var players =
                    combatants.computeIfAbsent(
                        target.getUniqueId(), ignored -> new LinkedHashMap<>());
                if (!players.containsKey(player.getUniqueId()) && players.size() >= 64)
                  players.remove(players.keySet().iterator().next());
                players.put(player.getUniqueId(), System.nanoTime());
              }
              Bukkit.getPluginManager()
                  .callEvent(new TheMobDamageEvent(player, target, mob, amount));
            });
  }

  public List<Player> participants(LivingEntity boss) {
    var players = combatants.get(boss.getUniqueId());
    if (players == null) return List.of();
    var config = plugin.mobs().mobConfigOf(boss);
    double radius =
        16.0
            * org.plugin.theMob.compat.Numbers.clamp(
                config == null ? 3 : config.getInt("arena.radius-chunks", 3), 1, 8);
    long cutoff = System.nanoTime() - 120_000_000_000L;
    return players.entrySet().stream()
        .filter(e -> e.getValue() >= cutoff)
        .map(e -> Bukkit.getPlayer(e.getKey()))
        .filter(this::eligible)
        .filter(
            p ->
                p.getWorld().equals(boss.getWorld())
                    && p.getLocation().distanceSquared(boss.getLocation()) <= radius * radius)
        .toList();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void death(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    combatants.remove(entity.getUniqueId());
    var mob = plugin.api().mob(entity).orElse(null);
    if (mob == null
        || npc(entity)
        || entity.getKiller() != null && npc(entity.getKiller())
        || entity
                .getPersistentDataContainer()
                .getOrDefault(plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 0)
            == 1) return;
    LootSource source =
        new LootSource(
            mob.definitionId(),
            mob.phaseId(),
            mob.spawnReason(),
            mob.boss(),
            mob.minion(),
            plugin.api().definitionLevel(mob.definitionId()),
            entity.getWorld().getName(),
            entity.getKiller() == null ? null : entity.getKiller().getUniqueId());
    for (ItemStack item : event.getDrops()) {
      if (item == null || item.getType().isAir() || item.getAmount() < 1 || pending.size() >= 8192)
        continue;
      var meta = item.getItemMeta();
      ItemStack original = item.clone();
      ItemSnapshot identity = ItemSnapshots.inspect(plugin, item).orElseThrow();
      String token = UUID.randomUUID().toString();
      meta.getPersistentDataContainer().set(receipt, PersistentDataType.STRING, token);
      item.setItemMeta(meta);
      pending.put(token, new Pending(mob, source, identity, original, System.nanoTime()));
    }
    // Native death outputs spawn in the same tick. A plugin moving them straight to an inventory
    // must not turn a later player redrop into fresh provenanced loot.
    if (!pending.isEmpty() && !clearPendingScheduled) {
      clearPendingScheduled = true;
      Bukkit.getScheduler()
          .runTask(
              plugin,
              () -> {
                pending.clear();
                clearPendingScheduled = false;
              });
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void spawn(ItemSpawnEvent event) {
    Item entity = event.getEntity();
    ItemStack item = entity.getItemStack();
    if (!item.hasItemMeta()) return;
    var meta = item.getItemMeta();
    String token = meta.getPersistentDataContainer().get(receipt, PersistentDataType.STRING);
    if (token == null) return;
    meta.getPersistentDataContainer().remove(receipt);
    item.setItemMeta(meta);
    entity.setItemStack(item);
    Pending entry = pending.remove(token);
    if (entry == null || System.nanoTime() - entry.time() > 30_000_000_000L) return;
    write(entity, entry.source());
    spawning.put(event, entry);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void spawned(ItemSpawnEvent event) {
    Pending entry = spawning.remove(event);
    if (entry == null || event.isCancelled()) return;
    Item entity = event.getEntity();
    ItemStack item = entity.getItemStack().clone();
    ItemSnapshot snapshot =
        item.isSimilar(entry.prototype())
            ? quantity(entry.identity(), item.getAmount())
            : ItemSnapshots.inspect(plugin, item).orElse(null);
    if (snapshot == null) return;
    writeIdentity(entity, snapshot);
    // Capture this spawn's stack before a subsequent merge or pickup changes the entity.
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (event.isCancelled()) return;
              Bukkit.getPluginManager()
                  .callEvent(
                      new TheMobDropEvent(entity, entry.mob(), entry.source(), item, snapshot));
            });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void pickup(EntityPickupItemEvent event) {
    if (!(event.getEntity() instanceof Player player)
        || !eligible(player)
        || pickups.contains(event)) return;
    LootSource source = read(event.getItem());
    if (source == null) return;
    ItemStack item = event.getItem().getItemStack().clone();
    int amount =
        org.plugin.theMob.compat.Numbers.clamp(
            item.getAmount() - event.getRemaining(), 0, item.getAmount());
    if (amount == 0) return;
    item.setAmount(amount);
    ItemSnapshot snapshot = readIdentity(event.getItem());
    if (snapshot == null) snapshot = ItemSnapshots.inspect(plugin, item).orElse(null);
    if (snapshot == null) return;
    ItemSnapshot picked = quantity(snapshot, amount);
    pickups.add(event);
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              pickups.remove(event);
              if (event.isCancelled() || !eligible(player)) return;
              Bukkit.getPluginManager()
                  .callEvent(new TheMobLootEvent(player, source, item, picked));
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void merge(ItemMergeEvent event) {
    var first = read(event.getEntity());
    var second = read(event.getTarget());
    if (!Objects.equals(first, second)) event.setCancelled(true);
    var firstItem = readIdentity(event.getEntity());
    var secondItem = readIdentity(event.getTarget());
    if (first != null
        && !Objects.equals(
            firstItem == null ? null : quantity(firstItem, 1),
            secondItem == null ? null : quantity(secondItem, 1))) event.setCancelled(true);
  }

  private static ItemSnapshot quantity(ItemSnapshot info, int amount) {
    return new ItemSnapshot(
        info.id(), info.material(), info.rarity(), info.stars(), amount, info.custom());
  }

  private void writeIdentity(Item entity, ItemSnapshot info) {
    var data = entity.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
    put(data, "id", info.id());
    put(data, "material", info.material().name());
    put(data, "rarity", info.rarity());
    data.set(key("stars"), PersistentDataType.INTEGER, info.stars());
    data.set(key("custom"), PersistentDataType.BYTE, (byte) (info.custom() ? 1 : 0));
    entity.getPersistentDataContainer().set(itemIdentity, PersistentDataType.TAG_CONTAINER, data);
  }

  private ItemSnapshot readIdentity(Item entity) {
    try {
      var data =
          entity.getPersistentDataContainer().get(itemIdentity, PersistentDataType.TAG_CONTAINER);
      if (data == null) return null;
      Material material = Material.valueOf(get(data, "material"));
      if (material != entity.getItemStack().getType()) return null;
      return new ItemSnapshot(
          get(data, "id"),
          material,
          get(data, "rarity"),
          data.get(key("stars"), PersistentDataType.INTEGER),
          1,
          data.get(key("custom"), PersistentDataType.BYTE) == 1);
    } catch (IllegalArgumentException | NullPointerException | ClassCastException invalid) {
      return null;
    }
  }

  private void write(Item item, LootSource source) {
    var data = item.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
    put(data, "mob", source.definitionId());
    put(data, "phase", source.phaseId() == null ? "" : source.phaseId());
    put(data, "reason", source.spawnReason().name());
    put(data, "world", source.worldName());
    put(data, "killer", source.killerId() == null ? "" : source.killerId().toString());
    data.set(key("level"), PersistentDataType.INTEGER, source.mobLevel());
    data.set(key("boss"), PersistentDataType.BYTE, (byte) (source.boss() ? 1 : 0));
    data.set(key("minion"), PersistentDataType.BYTE, (byte) (source.minion() ? 1 : 0));
    item.getPersistentDataContainer().set(provenance, PersistentDataType.TAG_CONTAINER, data);
  }

  private NamespacedKey key(String name) {
    return new NamespacedKey(plugin, name);
  }

  private void put(PersistentDataContainer data, String key, String value) {
    data.set(key(key), PersistentDataType.STRING, value);
  }

  private String get(PersistentDataContainer data, String key) {
    return Objects.requireNonNull(data.get(key(key), PersistentDataType.STRING));
  }

  private LootSource read(Item item) {
    try {
      var data =
          item.getPersistentDataContainer().get(provenance, PersistentDataType.TAG_CONTAINER);
      if (data == null) return null;
      String phase = get(data, "phase"), killer = get(data, "killer");
      return new LootSource(
          get(data, "mob"),
          phase.isEmpty() ? null : phase,
          SpawnReason.valueOf(get(data, "reason")),
          data.get(key("boss"), PersistentDataType.BYTE) == 1,
          data.get(key("minion"), PersistentDataType.BYTE) == 1,
          data.get(key("level"), PersistentDataType.INTEGER),
          get(data, "world"),
          killer.isEmpty() ? null : UUID.fromString(killer));
    } catch (IllegalArgumentException | NullPointerException | ClassCastException invalid) {
      return null;
    }
  }

  private void prune() {
    long now = System.nanoTime();
    pending.values().removeIf(entry -> now - entry.time() > 30_000_000_000L);
    combatants
        .values()
        .forEach(players -> players.values().removeIf(time -> now - time > 120_000_000_000L));
    combatants.entrySet().removeIf(entry -> entry.getValue().isEmpty());
  }

  @Override
  public void close() {
    cleanup.cancel();
    HandlerList.unregisterAll(this);
    pending.clear();
    spawning.clear();
    combatants.clear();
    pickups.clear();
  }
}
