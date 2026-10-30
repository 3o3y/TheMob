package org.plugin.theMob.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Register through Bukkit ServicesManager; TheMob lists enabled providers in its admin menu. */
public interface AdminMenuExtension {
  String id();

  String title();

  Material icon();

  /** Extra permission, checked in addition to TheMob's admin-menu permission. */
  default String permission() {
    return "themob.admin";
  }

  /** Server thread. The provider owns its menu and must revalidate its own mutations. */
  void open(Player administrator);
}
