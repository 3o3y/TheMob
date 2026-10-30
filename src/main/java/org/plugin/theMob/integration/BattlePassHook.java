package org.plugin.theMob.integration;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.*;
import org.plugin.theMob.api.event.*;

/** Late-bound BattlePass action API, including v5's Instantiator varargs registration. */
final class BattlePassHook {
  private final Object actions;
  private final TheMob plugin;

  BattlePassHook(TheMob plugin, Plugin battlepass) throws Exception {
    this(
        plugin,
        Reflect.pluginClass(battlepass, "ExternalActionContainer"),
        currentRegistry(Reflect.pluginClass(battlepass, "ActionRegistry")));
  }

  /**
   * Resolves one exact API descriptor; reflecting over BattlePlugin links its optional PAPI/Vault
   * types.
   */
  static Object currentRegistry(Class<?> type) throws Exception {
    try {
      Object registry =
          MethodHandles.publicLookup()
              .findStatic(type, "getRegistry", MethodType.methodType(type))
              .invoke();
      if (registry == null)
        throw new IllegalStateException("BattlePass ActionRegistry ist noch nicht bereit");
      return registry;
    } catch (Exception | Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new ReflectiveOperationException(
          "BattlePass ActionRegistry konnte nicht geladen werden", failure);
    }
  }

  BattlePassHook(TheMob plugin, Plugin battlepass, Class<?> base) throws Exception {
    this(plugin, base, Reflect.call(battlepass, "getActionRegistry"));
  }

  BattlePassHook(TheMob plugin, Class<?> base, Object registry) throws Exception {
    this.plugin = plugin;
    actions = ApiSubclass.container(base, plugin, "themob");
    register(registry, actions);
  }

  static void register(Object registry, Object actions) throws ReflectiveOperationException {
    for (Method method : registry.getClass().getMethods()) {
      if (!method.getName().equals("quest") || method.getParameterCount() != 1) continue;
      Class<?> parameter = method.getParameterTypes()[0];
      Class<?> factory = parameter.isArray() ? parameter.getComponentType() : parameter;
      if (!factory.isInterface()) continue;
      var methods =
          Arrays.stream(factory.getMethods())
              .filter(
                  m ->
                      Modifier.isAbstract(m.getModifiers())
                          && m.getDeclaringClass() != Object.class)
              .toList();
      if (methods.size() != 1) continue;
      Method init = methods.get(0);
      if (init.getParameterCount() != 1
          || !init.getParameterTypes()[0].isAssignableFrom(JavaPlugin.class)
          || !init.getReturnType().isInstance(actions)) continue;
      Object proxy =
          Proxy.newProxyInstance(
              factory.getClassLoader(),
              new Class<?>[] {factory},
              (self, called, args) -> {
                if (called.getDeclaringClass() == Object.class)
                  return switch (called.getName()) {
                    case "toString" -> "TheMob BattlePass factory";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> throw new UnsupportedOperationException(called.getName());
                  };
                if (called.getName().equals(init.getName()) && called.getParameterCount() == 1)
                  return actions;
                throw new UnsupportedOperationException(called.toString());
              });
      Object argument = proxy;
      if (parameter.isArray()) {
        argument = Array.newInstance(factory, 1);
        Array.set(argument, 0, proxy);
      }
      method.invoke(registry, new Object[] {argument});
      return;
    }
    throw new NoSuchMethodException(registry.getClass().getName() + ".quest(factory/varargs)");
  }

  void kill(TheMobDeathEvent event) throws ReflectiveOperationException {
    if (event.getKiller() == null) return;
    progress("kill", event, event.getMob().definitionId());
    if (event.getMob().boss()) progress("boss_kill", event, event.getMob().definitionId());
    if (event.getMob().minion()) progress("minion_kill", event, event.getMob().definitionId());
    for (var objective : plugin.integrations().battlePassObjectives().rules())
      if (objective.matches(event)) progress("objective", event, objective.id());
  }

  void phase(TheMobPhaseChangeEvent event) throws ReflectiveOperationException {
    if (event.getPreviousPhase() == null
        || Objects.equals(event.getPreviousPhase(), event.getNextPhase())) return;
    for (Player player : event.getParticipants()) {
      if (!org.plugin.theMob.boss.behavior.BossTargets.valid(player)
          || plugin.integrations().isNpc(player)) continue;
      Object builder = start("phase_enter", player, event.getNextPhase());
      mobFilters(
          builder,
          event.getMob(),
          event.getEntity().getWorld().getName(),
          event.getEntity().getType().name().toLowerCase(Locale.ROOT));
      Reflect.call(builder, "subRoot", "previous-phase", event.getPreviousPhase());
      finish(builder, BigDecimal.ONE);
    }
  }

  void damage(TheMobDamageEvent event) throws ReflectiveOperationException {
    Object builder = start("damage", event.getPlayer(), event.getMob().definitionId());
    mobFilters(
        builder,
        event.getMob(),
        event.getEntity().getWorld().getName(),
        event.getEntity().getType().name().toLowerCase(Locale.ROOT));
    finish(builder, BigDecimal.valueOf(event.getDamage()));
  }

  void drop(TheMobDropEvent event, Player player) throws ReflectiveOperationException {
    loot(
        "drop",
        player,
        event.getSource(),
        event.getItemSnapshot(),
        event.getEntity().getWorld().getName());
  }

  void loot(TheMobLootEvent event) throws ReflectiveOperationException {
    loot(
        "loot",
        event.getPlayer(),
        event.getSource(),
        event.getItemSnapshot(),
        event.getPlayer().getWorld().getName());
  }

  private void loot(
      String action, Player player, LootSource source, ItemSnapshot item, String world)
      throws ReflectiveOperationException {
    Object builder = start(action, player, item.material().name().toLowerCase(Locale.ROOT));
    var filters = new LinkedHashMap<String, String>();
    filters.put("mob", source.definitionId());
    filters.put("phase", source.phaseId() == null ? "unphased" : source.phaseId());
    filters.put("spawn-reason", source.spawnReason().name().toLowerCase(Locale.ROOT));
    filters.put("boss", Boolean.toString(source.boss()));
    filters.put("minion", Boolean.toString(source.minion()));
    filters.put("world", world);
    filters.put("source-world", source.worldName());
    filters.put("item", item.id());
    filters.put("rarity", item.rarity());
    filters.put("stars", Integer.toString(item.stars()));
    filters.put("custom", Boolean.toString(item.custom()));
    filters.put("own-kill", Boolean.toString(player.getUniqueId().equals(source.killerId())));
    for (var entry : filters.entrySet())
      Reflect.call(builder, "subRoot", entry.getKey(), entry.getValue());
    numberFilters(builder, "stars", item.stars(), 0, 5);
    numberFilters(builder, "level", source.mobLevel(), 1, 1_000_000);
    finish(builder, BigDecimal.valueOf(item.amount()));
  }

  private Object start(String action, Player player, String root)
      throws ReflectiveOperationException {
    Object builder = Reflect.call(actions, "executionBuilder", action);
    Reflect.call(builder, "player", player);
    Reflect.call(builder, "root", root);
    return builder;
  }

  private void finish(Object builder, BigDecimal amount) throws ReflectiveOperationException {
    Reflect.call(builder, "progress", amount);
    Reflect.call(builder, "buildAndExecute");
  }

  private void mobFilters(Object builder, MobSnapshot mob, String world, String type)
      throws ReflectiveOperationException {
    for (var entry :
        Map.of(
                "mob",
                mob.definitionId(),
                "world",
                world,
                "phase",
                mob.phaseId() == null ? "unphased" : mob.phaseId(),
                "spawn-reason",
                mob.spawnReason().name().toLowerCase(Locale.ROOT),
                "entity-type",
                type,
                "boss",
                Boolean.toString(mob.boss()),
                "minion",
                Boolean.toString(mob.minion()))
            .entrySet()) Reflect.call(builder, "subRoot", entry.getKey(), entry.getValue());
    numberFilters(builder, "level", plugin.api().definitionLevel(mob.definitionId()), 1, 1_000_000);
  }

  private void numberFilters(Object builder, String key, int actual, int lower, int upper)
      throws ReflectiveOperationException {
    Reflect.call(builder, "subRoot", key, Integer.toString(actual));
    Predicate<Object> min = value -> threshold(value, actual, lower, upper, true);
    Predicate<Object> max = value -> threshold(value, actual, lower, upper, false);
    Reflect.call(builder, "subRoot", "min-" + key, min);
    Reflect.call(builder, "subRoot", "max-" + key, max);
  }

  private static boolean threshold(
      Object value, int actual, int lower, int upper, boolean minimum) {
    try {
      String text = String.valueOf(value);
      if (!text.matches("[0-9]{1,7}")) return false;
      int number = Integer.parseInt(text);
      return number >= lower && number <= upper && (minimum ? actual >= number : actual <= number);
    } catch (NumberFormatException invalid) {
      return false;
    }
  }

  void dungeon(
      org.plugin.theMob.api.dungeon.DungeonCompletedEvent event, org.bukkit.entity.Player player)
      throws ReflectiveOperationException {
    Object builder = Reflect.call(actions, "executionBuilder", "dungeon_complete");
    Reflect.call(builder, "player", player);
    Reflect.call(builder, "root", event.getDungeon().id());
    for (var entry :
        Map.of(
                "dungeon",
                event.getDungeon().id(),
                "style",
                event.getStyle(),
                "world",
                event.getWorldName(),
                "boss",
                event.getBossMobId(),
                "levels",
                Integer.toString(event.getLevels()))
            .entrySet()) Reflect.call(builder, "subRoot", entry.getKey(), entry.getValue());
    Reflect.call(builder, "progressSingle");
    Reflect.call(builder, "buildAndExecute");
  }

  private void progress(String action, TheMobDeathEvent event, String id)
      throws ReflectiveOperationException {
    Object builder = Reflect.call(actions, "executionBuilder", action);
    Reflect.call(builder, "player", event.getKiller());
    Reflect.call(builder, "root", id);
    mobFilters(
        builder,
        event.getMob(),
        event.getEntity().getWorld().getName(),
        event.getEntity().getType().name().toLowerCase(Locale.ROOT));
    Reflect.call(builder, "progressSingle");
    Reflect.call(builder, "buildAndExecute");
  }
}
