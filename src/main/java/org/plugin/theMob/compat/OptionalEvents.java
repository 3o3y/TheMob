package org.plugin.theMob.compat;

import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.plugin.Plugin;

/** Register optional new events without exposing their types in old listener signatures. */
public final class OptionalEvents {
  private OptionalEvents() {}

  public static boolean bind(
      Plugin plugin,
      Listener listener,
      String name,
      EventPriority priority,
      boolean ignoreCancelled,
      Consumer<Event> action) {
    if (Bukkit.getServer() == null || !plugin.isEnabled()) return false;
    try {
      Class<? extends Event> type =
          Class.forName(name, false, Bukkit.class.getClassLoader()).asSubclass(Event.class);
      Bukkit.getPluginManager()
          .registerEvent(
              type,
              listener,
              priority,
              (owner, event) -> action.accept(event),
              plugin,
              ignoreCancelled);
      return true;
    } catch (ClassNotFoundException legacy) {
      return false;
    }
  }
}
