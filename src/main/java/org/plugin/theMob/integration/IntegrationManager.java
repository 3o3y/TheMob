package org.plugin.theMob.integration;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

/** Optional adapters use only the real installed providers, never bundled copies of their APIs. */
public final class IntegrationManager implements Listener, AutoCloseable {
  private final TheMob plugin;
  private final Map<String, IntegrationStatus> states = new LinkedHashMap<>();
  private final Map<UUID, Long> kills = new ConcurrentHashMap<>(),
      bosses = new ConcurrentHashMap<>();
  private final Map<String, Long> npcClicks = new HashMap<>();
  private volatile Map<String, String> placeholders = Map.of();
  private Plugin citizens, worldguard, jobs, vault;
  private QuestsHook quests;
  private ServerPluginAdapters extended;
  private BattlePassHook battlepass;
  private BattlePassObjectives battlePassObjectives =
      new BattlePassObjectives(List.of(), List.of());
  private org.plugin.theMob.integration.placeholder.TheMobExpansion expansion;

  public IntegrationManager(TheMob plugin) {
    this.plugin = plugin;
  }

  public Collection<IntegrationStatus> statuses() {
    return List.copyOf(states.values());
  }

  public BattlePassObjectives battlePassObjectives() {
    return battlePassObjectives;
  }

  public ProviderItems items() {
    return extended == null ? null : extended.items();
  }

  public void suspendExtended() {
    if (extended != null) {
      extended.close();
      extended = null;
    }
  }

  private Plugin enabled(String pluginName, String key) {
    if (!plugin.getConfig().getBoolean("integrations." + key + ".enabled", true)) {
      state(pluginName, IntegrationStatus.State.DISABLED, "In config.yml deaktiviert");
      return null;
    }
    Plugin external = Bukkit.getPluginManager().getPlugin(pluginName);
    if (external == null || !external.isEnabled()) {
      state(pluginName, IntegrationStatus.State.ABSENT, "Nicht installiert/aktiv");
      return null;
    }
    state(pluginName, IntegrationStatus.State.ACTIVE, external.getDescription().getVersion());
    return external;
  }

  void state(String name, IntegrationStatus.State state, String detail) {
    states.put(name, new IntegrationStatus(name, state, detail));
  }

  void failed(String name, Throwable failure) {
    state(
        name,
        IntegrationStatus.State.INCOMPATIBLE,
        failure.getClass().getSimpleName() + ": " + failure.getMessage());
    plugin
        .getLogger()
        .log(
            Level.WARNING,
            name + "-Anbindung deaktiviert: API passt nicht zum installierten Plugin.",
            failure);
  }

  public void prepareQuests() {
    if (quests != null || !plugin.getConfig().getBoolean("integrations.quests.enabled", true))
      return;
    Plugin external = Bukkit.getPluginManager().getPlugin("Quests");
    if (external != null)
      try {
        quests = new QuestsHook(external);
      } catch (Exception | LinkageError failure) {
        failed("Quests", failure);
      }
  }

  public void register() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    battlePassObjectives = BattlePassObjectives.load(plugin);
    for (String problem : battlePassObjectives.problems())
      plugin.getLogger().warning("BattlePass-Aufgabe deaktiviert: " + problem);
    citizens = enabled("Citizens", "citizens");
    worldguard = enabled("WorldGuard", "worldguard");
    vault = enabled("Vault", "vault");
    jobs = enabled("Jobs", "jobs");
    if (jobs != null) {
      bind(
          jobs, "com.gamingmesh.jobs.api.JobsPrePaymentEvent", EventPriority.HIGH, this::jobsMoney);
      bind(
          jobs,
          "com.gamingmesh.jobs.api.JobsExpGainEvent",
          EventPriority.HIGH,
          this::jobsExperience);
    }
    Plugin questPlugin = enabled("Quests", "quests");
    if (questPlugin == null && quests != null) {
      quests.close();
      quests = null;
    }
    if (questPlugin != null && quests == null) prepareQuests();
    if (questPlugin != null && quests != null)
      try {
        quests.ensureRegistered();
      } catch (Exception | LinkageError failure) {
        failed("Quests", failure);
      }
    Plugin pass = enabled("BattlePass", "battlepass");
    if (pass != null && battlepass == null) {
      try {
        battlepass = new BattlePassHook(plugin, pass);
      } catch (Exception | LinkageError failure) {
        failed("BattlePass", failure);
      }
    }
    if (citizens != null)
      bind(
          citizens,
          "net.citizensnpcs.api.event.NPCRightClickEvent",
          EventPriority.MONITOR,
          this::npcClick);
    Plugin placeholderProvider = enabled("PlaceholderAPI", "placeholderapi");
    if (placeholderProvider == null && expansion != null) {
      expansion.unregister();
      expansion = null;
    }
    if (placeholderProvider != null && expansion == null) {
      try {
        expansion = new org.plugin.theMob.integration.placeholder.TheMobExpansion(plugin, this);
        if (!expansion.register())
          throw new IllegalStateException("Placeholder %themob_...% bereits registriert");
      } catch (Exception | LinkageError failure) {
        expansion = null;
        failed("PlaceholderAPI", failure);
      }
    }
    updatePlaceholders();
    suspendExtended();
    extended = new ServerPluginAdapters(plugin, this);
    extended.register();
    Bukkit.getPluginManager().registerEvents(new DungeonClaimGuard(plugin, this), plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::updatePlaceholders, 20, 20);
  }

  @SuppressWarnings("unchecked")
  void bind(Plugin provider, String eventName, EventPriority priority, Consumer<Event> action) {
    try {
      Class<? extends Event> type =
          (Class<? extends Event>) Reflect.type(provider, eventName).asSubclass(Event.class);
      Bukkit.getPluginManager()
          .registerEvent(
              type,
              new Listener() {},
              priority,
              (listener, event) -> {
                if (type.isInstance(event)) action.accept(event);
              },
              plugin,
              true);
    } catch (Exception | LinkageError failure) {
      failed(provider.getName(), failure);
    }
  }

  public boolean isNpc(Entity entity) {
    if (entity == null) return false;
    if (entity.hasMetadata("NPC")) return true;
    if (citizens == null) return false;
    try {
      Object registry =
          Reflect.call(
              Reflect.type(citizens, "net.citizensnpcs.api.CitizensAPI"), "getNPCRegistry");
      return Boolean.TRUE.equals(Reflect.call(registry, "isNPC", entity));
    } catch (Exception | LinkageError failure) {
      failed("Citizens", failure);
      citizens = null;
      return entity.hasMetadata("NPC");
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void protectNpcTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
    if (event.getTarget() != null
        && isNpc(event.getTarget())
        && event.getEntity() instanceof LivingEntity living
        && plugin.api().mob(living).isPresent()) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void beforeSpawn(TheMobPreSpawnEvent event) {
    var status = states.get("WorldGuard");
    if (status != null && status.state() == IntegrationStatus.State.INCOMPATIBLE) {
      event.setCancelled(true);
      return;
    }
    if (worldguard == null) return;
    try {
      Object guard =
          Reflect.call(Reflect.type(worldguard, "com.sk89q.worldguard.WorldGuard"), "getInstance");
      Object platform = Reflect.call(guard, "getPlatform");
      Object container = Reflect.call(platform, "getRegionContainer");
      Object query = Reflect.call(container, "createQuery");
      Class<?> adapter = Reflect.type(worldguard, "com.sk89q.worldedit.bukkit.BukkitAdapter");
      Object location = Reflect.call(adapter, "adapt", event.getLocation());
      Class<?> flags = Reflect.type(worldguard, "com.sk89q.worldguard.protection.flags.Flags");
      Object spawning = flags.getField("MOB_SPAWNING").get(null);
      Object array =
          Array.newInstance(
              Reflect.type(worldguard, "com.sk89q.worldguard.protection.flags.StateFlag"), 1);
      Array.set(array, 0, spawning);
      if (!Boolean.TRUE.equals(Reflect.call(query, "testState", location, null, array)))
        event.setCancelled(true);
    } catch (Exception | LinkageError failure) {
      failed("WorldGuard", failure);
      worldguard = null;
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void minionDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    if (entity
            .getPersistentDataContainer()
            .getOrDefault(new NamespacedKey(plugin, "minion"), PersistentDataType.BYTE, (byte) 0)
        != 1) return;
    if (entity
            .getPersistentDataContainer()
            .getOrDefault(plugin.keys().BOSS_DEATH_HANDLED, PersistentDataType.INTEGER, 0)
        == 1) return;
    entity
        .getPersistentDataContainer()
        .set(plugin.keys().BOSS_DEATH_HANDLED, PersistentDataType.INTEGER, 1);
    plugin.api().died(entity);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void killed(TheMobDeathEvent event) {
    Player player = event.getKiller();
    if (player == null
        || isNpc(player)
        || isNpc(event.getEntity())
        || event.getMob().minion()
            && !plugin.getConfig().getBoolean("integrations.track-minion-kills", false)) return;
    kills.merge(player.getUniqueId(), 1L, Long::sum);
    if (event.getMob().boss()) bosses.merge(player.getUniqueId(), 1L, Long::sum);
    if (quests != null && active("Quests"))
      try {
        quests.kill(event);
      } catch (Exception | LinkageError failure) {
        failed("Quests", failure);
      }
    if (battlepass != null && active("BattlePass"))
      try {
        battlepass.kill(event);
      } catch (Exception | LinkageError failure) {
        failed("BattlePass", failure);
      }
    if (vault != null) rewardMoney(event);
    if (extended != null) extended.kill(event);
  }

  boolean active(String name) {
    var status = states.get(name);
    return status != null && status.state() == IntegrationStatus.State.ACTIVE;
  }

  private boolean combatCredit(Player player, boolean minion) {
    return org.plugin.theMob.boss.behavior.BossTargets.valid(player)
        && !isNpc(player)
        && (!minion || plugin.getConfig().getBoolean("integrations.track-minion-kills", false));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void phaseEntered(TheMobPhaseChangeEvent event) {
    if (battlepass == null || !active("BattlePass") || isNpc(event.getEntity())) return;
    try {
      battlepass.phase(event);
    } catch (Exception | LinkageError failure) {
      failed("BattlePass", failure);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void damaged(TheMobDamageEvent event) {
    if (battlepass == null
        || !active("BattlePass")
        || isNpc(event.getEntity())
        || !combatCredit(event.getPlayer(), event.getMob().minion())) return;
    try {
      battlepass.damage(event);
    } catch (Exception | LinkageError failure) {
      failed("BattlePass", failure);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void dropped(TheMobDropEvent event) {
    if (battlepass == null || !active("BattlePass") || event.getSource().killerId() == null) return;
    Player player = Bukkit.getPlayer(event.getSource().killerId());
    if (!combatCredit(player, event.getSource().minion())) return;
    try {
      battlepass.drop(event, player);
    } catch (Exception | LinkageError failure) {
      failed("BattlePass", failure);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void looted(TheMobLootEvent event) {
    if (battlepass == null
        || !active("BattlePass")
        || !combatCredit(event.getPlayer(), event.getSource().minion())) return;
    try {
      battlepass.loot(event);
    } catch (Exception | LinkageError failure) {
      failed("BattlePass", failure);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void dungeonCompleted(org.plugin.theMob.api.dungeon.DungeonCompletedEvent event) {
    if (battlepass == null || !active("BattlePass")) return;
    for (Player player : event.getParticipants())
      try {
        battlepass.dungeon(event, player);
      } catch (Exception | LinkageError failure) {
        failed("BattlePass", failure);
        break;
      }
  }

  @EventHandler
  public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
    String prefix = event.getPlayer().getUniqueId() + ":";
    npcClicks.keySet().removeIf(key -> key.startsWith(prefix));
  }

  private LivingEntity jobsVictim(Event event) throws ReflectiveOperationException {
    Object victim = Reflect.call(event, "getLivingEntity");
    return victim instanceof LivingEntity living ? living : null;
  }

  private boolean excluded(LivingEntity entity) {
    if (entity == null) return true;
    var mob = plugin.api().mob(entity);
    return mob.isEmpty() || isNpc(entity);
  }

  private double multiplier(LivingEntity entity, String key) {
    var config = plugin.mobs().mobConfigOf(entity);
    if (config == null) return 1;
    double value = config.getDouble("integrations.jobs." + key, 1);
    return Double.isFinite(value) ? org.plugin.theMob.compat.Numbers.clamp(value, 0, 1000) : 1;
  }

  private boolean blockMinion(LivingEntity entity) {
    return plugin.api().mob(entity).map(MobSnapshot::minion).orElse(false)
        && !plugin.getConfig().getBoolean("integrations.track-minion-kills", false);
  }

  private void jobsMoney(Event event) {
    if (!active("Jobs")) return;
    try {
      LivingEntity mob = jobsVictim(event);
      if (excluded(mob)) return;
      if (blockMinion(mob)) {
        Reflect.call(event, "setCancelled", true);
        return;
      }
      double money = ((Number) Reflect.call(event, "getAmount")).doubleValue();
      double points = ((Number) Reflect.call(event, "getPoints")).doubleValue();
      Reflect.call(event, "setAmount", money * multiplier(mob, "money-multiplier"));
      Reflect.call(event, "setPoints", points * multiplier(mob, "points-multiplier"));
    } catch (Exception | LinkageError failure) {
      failed("Jobs", failure);
    }
  }

  private void jobsExperience(Event event) {
    if (!active("Jobs")) return;
    try {
      LivingEntity mob = jobsVictim(event);
      if (excluded(mob)) return;
      if (blockMinion(mob)) {
        Reflect.call(event, "setCancelled", true);
        return;
      }
      double xp = ((Number) Reflect.call(event, "getExp")).doubleValue();
      Reflect.call(event, "setExp", xp * multiplier(mob, "experience-multiplier"));
    } catch (Exception | LinkageError failure) {
      failed("Jobs", failure);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void rewardMoney(TheMobDeathEvent event) {
    ConfigurationSection config = plugin.mobs().mobConfigById(event.getMob().definitionId());
    double amount = config == null ? 0 : config.getDouble("integrations.vault.money", 0);
    if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000) return;
    try {
      Class<?> type = Reflect.type(vault, "net.milkbowl.vault.economy.Economy");
      var registration = Bukkit.getServicesManager().getRegistration((Class) type);
      if (registration == null) {
        state("Vault", IntegrationStatus.State.ACTIVE, "Vault vorhanden, kein Economy-Provider");
        return;
      }
      Object response =
          Reflect.call(registration.getProvider(), "depositPlayer", event.getKiller(), amount);
      if (!Boolean.TRUE.equals(Reflect.call(response, "transactionSuccess")))
        plugin
            .getLogger()
            .warning(
                "Vault hat TheMob-Belohnung fuer "
                    + event.getKiller().getUniqueId()
                    + " abgelehnt.");
    } catch (Exception | LinkageError failure) {
      failed("Vault", failure);
      vault = null;
    }
  }

  private void npcClick(Event event) {
    if (!active("Citizens")) return;
    try {
      Object npc = Reflect.call(event, "getNPC");
      int id = ((Number) Reflect.call(npc, "getId")).intValue();
      String mob = plugin.getConfig().getString("integrations.citizens.spawn-on-click." + id, "");
      if (mob.isBlank()) return;
      Player player = (Player) Reflect.call(event, "getClicker");
      if (!player.hasPermission("themob.npc.use")) return;
      long now = System.nanoTime();
      String key = player.getUniqueId() + ":" + id;
      long seconds =
          org.plugin.theMob.compat.Numbers.clamp(
              plugin.getConfig().getLong("integrations.citizens.click-cooldown-seconds", 10),
              1,
              3600);
      if (now - npcClicks.getOrDefault(key, Long.MIN_VALUE / 2) < seconds * 1_000_000_000L) return;
      Location location =
          player.getLocation().clone().add(player.getLocation().getDirection().multiply(2));
      if (plugin.api().spawn(mob, location, SpawnReason.NPC).isPresent()) npcClicks.put(key, now);
    } catch (Exception | LinkageError failure) {
      failed("Citizens", failure);
    }
  }

  private void updatePlaceholders() {
    if (plugin.automation() == null) return;
    placeholders =
        Map.of(
            "mobs_alive",
            String.valueOf(plugin.automation().budgets().aliveTotal()),
            "bosses_alive",
            String.valueOf(plugin.automation().budgets().aliveBosses()),
            "minions_alive",
            String.valueOf(plugin.automation().budgets().aliveMinions()),
            "registered_mobs",
            String.valueOf(plugin.mobs().registeredIds().size()),
            "tps",
            String.format(Locale.ROOT, "%.2f", plugin.automation().tps().tps1m()),
            "version",
            plugin.getDescription().getVersion());
  }

  public String placeholder(UUID player, String key) {
    if (key.equals("kills_session")) return String.valueOf(kills.getOrDefault(player, 0L));
    if (key.equals("boss_kills_session")) return String.valueOf(bosses.getOrDefault(player, 0L));
    return placeholders.get(key);
  }

  public void close() {
    suspendExtended();
    HandlerList.unregisterAll(this);
    if (quests != null) quests.close();
    if (expansion != null) expansion.unregister();
    npcClicks.clear();
  }
}
