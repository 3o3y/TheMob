package org.plugin.theMob.integration;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

/** Optional, feature-specific native adapters. Missing APIs fail independently and visibly. */
final class ServerPluginAdapters implements Listener, AutoCloseable {
  private final TheMob plugin;
  private final IntegrationManager manager;
  private final Map<String, Plugin> providers = new LinkedHashMap<>();
  private final Map<UUID, String> contexts = new ConcurrentHashMap<>();
  private final ProviderItems items;
  private Object luckManager, luckCalculator, lands;
  private final Map<UUID, String> holograms = new LinkedHashMap<>();
  private org.bukkit.scheduler.BukkitTask pulse;

  ServerPluginAdapters(TheMob plugin, IntegrationManager manager) {
    this.plugin = plugin;
    this.manager = manager;
    items = new ProviderItems(plugin);
  }

  ProviderItems items() {
    return items;
  }

  void register() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    for (var target : IntegrationCatalog.targets().subList(7, 20)) {
      if (!plugin.getConfig().getBoolean("integrations." + target.id() + ".enabled", true)) {
        manager.state(target.name(), IntegrationStatus.State.DISABLED, "In config.yml deaktiviert");
        continue;
      }
      Plugin external =
          target.pluginNames().stream()
              .map(Bukkit.getPluginManager()::getPlugin)
              .filter(Objects::nonNull)
              .filter(Plugin::isEnabled)
              .findFirst()
              .orElse(null);
      if (external == null) {
        manager.state(target.name(), IntegrationStatus.State.ABSENT, "Nicht installiert/aktiv");
        continue;
      }
      providers.put(target.id(), external);
      manager.state(
          target.name(),
          IntegrationStatus.State.ACTIVE,
          external.getDescription().getVersion() + " | " + target.feature());
      try {
        initialize(target.id(), external);
      } catch (Exception | LinkageError failure) {
        manager.failed(target.name(), failure);
      }
    }
    if (providers.containsKey("luckperms") || providers.containsKey("decentholograms"))
      pulse = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 1, 20);
  }

  private void initialize(String id, Plugin provider) throws Exception {
    switch (id) {
      case "luckperms" -> luck(provider);
      case "mythicmobs" -> {
        Class<?> type = Reflect.type(provider, "io.lumine.mythic.bukkit.MythicBukkit");
        Object engine = Reflect.call(type, "inst"),
            factory = Reflect.call(engine, "getItemManager");
        factory.getClass().getMethod("getItemStack", String.class);
        items.add(id, key -> item(id, () -> Reflect.call(factory, "getItemStack", key)));
      }
      case "mmoitems" -> {
        Class<?> main = Reflect.type(provider, "net.Indyuce.mmoitems.MMOItems");
        java.lang.invoke.MethodHandles.publicLookup()
            .findVirtual(
                main,
                "getItem",
                java.lang.invoke.MethodType.methodType(
                    ItemStack.class, String.class, String.class));
        items.add(
            id,
            key ->
                item(
                    id,
                    () -> {
                      String[] parts = key.split("/", 2);
                      if (parts.length != 2) return null;
                      return NarrowApi.call(
                          main,
                          provider,
                          "getItem",
                          ItemStack.class,
                          new Class<?>[] {String.class, String.class},
                          parts[0].toUpperCase(Locale.ROOT),
                          parts[1]);
                    }));
      }
      case "advancedcrates" -> {
        Class<?> main = Reflect.type(provider, "me.PM2.AdvancedCrates.MonthlyCrate");
        Class<?>
            handlerType =
                Reflect.type(provider, "me.PM2.AdvancedCrates.cratesHandler.CrateHandler"),
            crateType = Reflect.type(provider, "me.PM2.AdvancedCrates.cratesHandler.Crate"),
            builderType =
                Reflect.type(provider, "me.PM2.AdvancedCrates.rewards.SaveableItemBuilder");
        Object handler =
            NarrowApi.call(main, null, "getCratesHandler", handlerType, new Class<?>[0]);
        if (handler == null)
          throw new IllegalStateException("AdvancedCrates noch nicht initialisiert");
        items.add(
            id,
            key ->
                item(
                    id,
                    () -> {
                      Object crate =
                          NarrowApi.call(
                              handlerType,
                              handler,
                              "getCrate",
                              crateType,
                              new Class<?>[] {String.class},
                              key);
                      if (crate == null) return null;
                      Object builder =
                          NarrowApi.call(
                              crateType, crate, "getKeyBuilder", builderType, new Class<?>[0]);
                      return builder == null
                          ? null
                          : NarrowApi.call(
                              builderType,
                              builder,
                              "getItemStack",
                              ItemStack.class,
                              new Class<?>[0]);
                    }));
      }
      case "economyshopgui" -> {
        Class<?> type =
            Reflect.type(provider, "me.gypopo.economyshopgui.api.events.PreTransactionEvent");
        type.getMethod("getPlayer");
        type.getMethod("getItems");
        type.getMethod("getTransactionType");
        manager.bind(provider, type.getName(), EventPriority.HIGH, this::shop);
        Class<?> api = Reflect.type(provider, "me.gypopo.economyshopgui.api.EconomyShopGUIHook");
        items.add(
            id,
            key ->
                item(
                    id,
                    () -> {
                      Object entry = Reflect.call(api, "getShopItem", key);
                      return entry == null ? null : Reflect.call(entry, "getItemToGive");
                    }));
      }
      case "auraskills" ->
          Reflect.call(Reflect.type(provider, "dev.aurelium.auraskills.api.AuraSkillsApi"), "get");
      case "mcmmo" ->
          java.lang.invoke.MethodHandles.publicLookup()
              .findStatic(
                  Reflect.type(provider, "com.gmail.nossr50.api.ExperienceAPI"),
                  "addRawXP",
                  java.lang.invoke.MethodType.methodType(
                      void.class, Player.class, String.class, float.class, String.class));
      case "griefprevention" -> {
        Object store = provider.getClass().getField("dataStore").get(provider);
        store.getClass().getMethod("getClaims");
      }
      case "towny" -> {
        Object api =
            Reflect.call(
                Reflect.type(provider, "com.palmergames.bukkit.towny.TownyAPI"), "getInstance");
        api.getClass().getMethod("getTownBlock", Location.class);
      }
      case "lands" -> {
        lands =
            Reflect.call(
                Reflect.type(provider, "me.angeschossen.lands.api.LandsIntegration"), "of", plugin);
        lands.getClass().getMethod("getLandByUnloadedChunk", World.class, int.class, int.class);
      }
      case "decentholograms" ->
          Reflect.type(provider, "eu.decentsoftware.holograms.api.DHAPI")
              .getMethod("createHologram", String.class, Location.class, boolean.class, List.class);
      default -> {} // EssentialsX and Multiverse keep native Bukkit metadata/events/permissions.
    }
  }

  @FunctionalInterface
  private interface Call {
    Object get() throws Exception;
  }

  private ItemStack item(String id, Call call) {
    if (!active(id)) return null;
    try {
      Object value = call.get();
      return value instanceof ItemStack stack ? stack : null;
    } catch (Exception | LinkageError failure) {
      manager.failed(name(id), failure);
      return null;
    }
  }

  private String name(String id) {
    return IntegrationCatalog.find(id).map(IntegrationCatalog.Target::name).orElse(id);
  }

  private boolean active(String id) {
    Plugin provider = providers.get(id);
    return provider != null && provider.isEnabled() && manager.active(name(id));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void spawn(TheMobPreSpawnEvent event) {
    if (!providers.containsKey("towny")) return;
    if (!active("towny")) {
      event.setCancelled(true);
      return;
    }
    try {
      Plugin provider = providers.get("towny");
      Object api =
          Reflect.call(
              Reflect.type(provider, "com.palmergames.bukkit.towny.TownyAPI"), "getInstance");
      if (Boolean.TRUE.equals(Reflect.call(api, "isTownyWorld", event.getLocation().getWorld()))
          && !Boolean.TRUE.equals(Reflect.call(api, "areMobsEnabled", event.getLocation())))
        event.setCancelled(true);
    } catch (Exception | LinkageError failure) {
      event.setCancelled(true);
      manager.failed("Towny", failure);
    }
  }

  void kill(TheMobDeathEvent event) {
    if (event.getKiller() == null) return;
    var cfg = plugin.mobs().mobConfigById(event.getMob().definitionId());
    if (cfg == null) return;
    for (String id : List.of("auraskills", "mcmmo")) {
      double bonus = cfg.getDouble("integrations." + id + ".bonus-xp", 0);
      if (!active(id) || !Double.isFinite(bonus) || bonus <= 0 || bonus > 1_000_000) continue;
      try {
        Plugin provider = providers.get(id);
        if (id.equals("auraskills")) {
          Object api =
              Reflect.call(
                  Reflect.type(provider, "dev.aurelium.auraskills.api.AuraSkillsApi"), "get");
          Object user = Reflect.call(api, "getUser", event.getKiller().getUniqueId());
          String skill =
              cfg.getString("integrations.auraskills.skill", "FIGHTING").toUpperCase(Locale.ROOT);
          Object constant =
              Reflect.type(provider, "dev.aurelium.auraskills.api.skill.Skills")
                  .getField(skill)
                  .get(null);
          Reflect.call(user, "addSkillXp", constant, bonus);
        } else {
          String skill = cfg.getString("integrations.mcmmo.skill", "SWORDS");
          NarrowApi.call(
              Reflect.type(provider, "com.gmail.nossr50.api.ExperienceAPI"),
              null,
              "addRawXP",
              void.class,
              new Class<?>[] {Player.class, String.class, float.class, String.class},
              event.getKiller(),
              skill,
              (float) bonus,
              "PVE");
        }
      } catch (Exception | LinkageError failure) {
        manager.failed(name(id), failure);
      }
    }
  }

  private void luck(Plugin provider) throws Exception {
    Object api = Reflect.call(Reflect.type(provider, "net.luckperms.api.LuckPermsProvider"), "get");
    luckManager = Reflect.call(api, "getContextManager");
    Class<?> type = Reflect.type(provider, "net.luckperms.api.context.ContextCalculator");
    luckCalculator =
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (self, method, args) -> {
              if (method.getDeclaringClass() == Object.class)
                return switch (method.getName()) {
                  case "hashCode" -> System.identityHashCode(self);
                  case "equals" -> self == args[0];
                  default -> "TheMob dungeon context";
                };
              if (method.getName().equals("calculate")) {
                if (args[0] instanceof Player player) {
                  String dungeon = contexts.get(player.getUniqueId());
                  if (dungeon != null) Reflect.call(args[1], "accept", "themob:dungeon", dungeon);
                }
                return null;
              }
              if (method.isDefault())
                return InvocationHandler.invokeDefault(
                    self, method, args == null ? new Object[0] : args);
              throw new UnsupportedOperationException(method.getName());
            });
    Reflect.call(luckManager, "registerCalculator", luckCalculator);
  }

  private void pulse() {
    if (active("luckperms"))
      for (Player p : Bukkit.getOnlinePlayers())
        try {
          String next =
              plugin
                  .api()
                  .dungeonAt(p.getLocation())
                  .filter(DungeonRegion::ready)
                  .map(DungeonRegion::id)
                  .orElse(null);
          String before =
              next == null ? contexts.remove(p.getUniqueId()) : contexts.put(p.getUniqueId(), next);
          if (!Objects.equals(before, next)) Reflect.call(luckManager, "signalContextUpdate", p);
        } catch (Exception | LinkageError failure) {
          manager.failed("LuckPerms", failure);
          break;
        }
    if (!active("decentholograms")
        || !plugin.getConfig().getBoolean("integrations.decentholograms.boss-holograms", false))
      return;
    try {
      Class<?> api =
          Reflect.type(providers.get("decentholograms"), "eu.decentsoftware.holograms.api.DHAPI");
      for (var entry : new ArrayList<>(holograms.entrySet())) {
        var entity = Bukkit.getEntity(entry.getKey());
        if (!(entity instanceof LivingEntity living)
            || living.isDead()
            || !living.isValid()
            || !plugin.activity().active(living)) {
          Reflect.call(api, "removeHologram", entry.getValue());
          holograms.remove(entry.getKey());
        }
      }
      for (var snapshot : plugin.api().activeMobs()) {
        if (!snapshot.boss()
            || holograms.size() >= 24 && !holograms.containsKey(snapshot.entityId())) continue;
        var entity = Bukkit.getEntity(snapshot.entityId());
        if (!(entity instanceof LivingEntity mob) || !plugin.activity().active(mob)) continue;
        var max = org.plugin.theMob.compat.BukkitTypes.instance(mob, "MAX_HEALTH");
        var lines =
            List.of(
                snapshot.displayName(),
                "§c"
                    + (int) Math.ceil(mob.getHealth())
                    + " / "
                    + (max == null ? "?" : (int) max.getValue()),
                "§7" + Objects.toString(snapshot.phaseId(), ""));
        String key = holograms.get(snapshot.entityId());
        if (key == null) {
          key = "themob_" + snapshot.entityId().toString().replace("-", "");
          Reflect.call(
              api,
              "createHologram",
              key,
              mob.getLocation().add(0, mob.getHeight() + 1, 0),
              false,
              lines);
          holograms.put(snapshot.entityId(), key);
        } else {
          Object holo = Reflect.call(api, "getHologram", key);
          if (holo != null) {
            Reflect.call(api, "setHologramLines", holo, lines);
            Reflect.call(
                api, "moveHologram", holo, mob.getLocation().add(0, mob.getHeight() + 1, 0));
          }
        }
      }
    } catch (Exception | LinkageError failure) {
      manager.failed("DecentHolograms", failure);
    }
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    contexts.remove(event.getPlayer().getUniqueId());
  }

  private void shop(Event event) {
    if (!providers.containsKey("economyshopgui")
        || !plugin.getConfig().getBoolean("integrations.economyshopgui.protect-custom-items", true))
      return;
    if (!active("economyshopgui")) {
      if (event instanceof Cancellable c && providers.get("economyshopgui").isEnabled())
        c.setCancelled(true);
      return;
    }
    try {
      String type = String.valueOf(Reflect.call(event, "getTransactionType"));
      if (!type.toUpperCase(Locale.ROOT).contains("SELL")) return;
      Player player = (Player) Reflect.call(event, "getPlayer");
      var templates = new ArrayList<ItemStack>();
      Object shopItems = Reflect.call(event, "getItems");
      if (shopItems instanceof Map<?, ?> map)
        for (Object shopItem : map.keySet()) {
          Object template = Reflect.call(shopItem, "getItemToGive");
          if (template instanceof ItemStack stack) templates.add(stack);
        }
      if (templates.isEmpty()) {
        Object template = Reflect.call(event, "getItemStack");
        if (template instanceof ItemStack stack) templates.add(stack);
      }
      for (ItemStack stack : player.getInventory().getStorageContents()) {
        if (!plugin.api().inspectItem(stack).map(ItemSnapshot::custom).orElse(false)) continue;
        for (ItemStack template : templates)
          if (stack.getType() == template.getType() && !stack.isSimilar(template)) {
            ((Cancellable) event).setCancelled(true);
            player.sendMessage("§eTheMob-Spezialitem vor Verkauf zum Vanilla-Preis geschuetzt.");
            return;
          }
      }
    } catch (Exception | LinkageError failure) {
      if (event instanceof Cancellable c) c.setCancelled(true);
      manager.failed("EconomyShopGUI", failure);
    }
  }

  @Override
  public void close() {
    HandlerList.unregisterAll(this);
    if (pulse != null) pulse.cancel();
    contexts.clear();
    if (luckManager != null && luckCalculator != null)
      try {
        Reflect.call(luckManager, "unregisterCalculator", luckCalculator);
      } catch (Exception | LinkageError ignored) {
      }
    if (providers.containsKey("decentholograms"))
      try {
        Class<?> api =
            Reflect.type(providers.get("decentholograms"), "eu.decentsoftware.holograms.api.DHAPI");
        for (String name : holograms.values()) Reflect.call(api, "removeHologram", name);
      } catch (Exception | LinkageError ignored) {
      }
    holograms.clear();
    providers.clear();
  }
}
