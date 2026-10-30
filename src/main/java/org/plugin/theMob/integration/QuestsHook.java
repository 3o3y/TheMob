package org.plugin.theMob.integration;

import java.util.*;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.api.event.TheMobDeathEvent;

/** Uses the public concrete BukkitCustomObjective API from Quests 5.x. */
final class QuestsHook implements AutoCloseable {
  private final Plugin quests;
  private final Object objective;
  private Collection<Object> objectives;

  @SuppressWarnings("unchecked")
  QuestsHook(Plugin quests) throws Exception {
    this(quests, Reflect.type(quests, "me.pikamug.quests.module.BukkitCustomObjective"));
  }

  @SuppressWarnings("unchecked")
  QuestsHook(Plugin quests, Class<?> type) throws Exception {
    this.quests = quests;
    objective = ApiSubclass.objective(type);
    Reflect.call(objective, "setName", "TheMob Kill");
    Reflect.call(objective, "setAuthor", "TheMob");
    Reflect.call(objective, "setItem", "IRON_SWORD", (short) 0);
    Reflect.call(objective, "setShowCount", true);
    Reflect.call(objective, "setCountPrompt", "Anzahl TheMob-Kills");
    Reflect.call(
        objective, "addStringPrompt", "Mob ID", "Mob-ID, mehrere IDs mit Komma, oder *", "*");
    Reflect.call(objective, "addStringPrompt", "Boss only", "Nur Bosse: true oder false", "false");
    Reflect.call(objective, "setDisplay", "TheMob %Mob ID%: %count%");
    ensureRegistered();
  }

  @SuppressWarnings("unchecked")
  void ensureRegistered() throws ReflectiveOperationException {
    objectives = (Collection<Object>) Reflect.call(quests, "getCustomObjectives");
    if (!objectives.contains(objective)) objectives.add(objective);
  }

  @SuppressWarnings("unchecked")
  void kill(TheMobDeathEvent event) throws ReflectiveOperationException {
    if (event.getKiller() == null) return;
    ensureRegistered();
    UUID id = event.getKiller().getUniqueId();
    Object quester = Reflect.call(quests, "getQuester", id);
    if (quester == null) return;
    Map<Object, Object> active = (Map<Object, Object>) Reflect.call(quester, "getCurrentQuests");
    for (Object quest : new ArrayList<>(active.keySet())) {
      Object raw = Reflect.call(objective, "getDataForPlayer", id, objective, quest);
      if (!(raw instanceof Map<?, ?> data)) continue;
      String filter = String.valueOf(data.get("Mob ID"));
      if (!matches(filter, event.getMob().definitionId())) continue;
      if (Boolean.parseBoolean(String.valueOf(data.get("Boss only"))) && !event.getMob().boss())
        continue;
      Reflect.call(objective, "incrementObjective", id, objective, quest, 1);
    }
  }

  static boolean matches(String filter, String id) {
    if (filter == null || filter.equals("null") || filter.isBlank() || filter.trim().equals("*"))
      return true;
    return Arrays.stream(filter.split(",")).anyMatch(s -> s.trim().equalsIgnoreCase(id));
  }

  public void close() {
    objectives.remove(objective);
  }
}
