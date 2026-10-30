package org.plugin.theMob.progression;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ProgressionBootstrap {
  private final ProgressionConfig config;

  public ProgressionBootstrap(Plugin plugin, PlayerProgressionManager manager) {
    this.config = new ProgressionConfig(plugin);
    if (this.config.isEnabled()) {
      ItemKeyRegistry keys = new ItemKeyRegistry(plugin);
      CustomItemFactory itemFactory = new CustomItemFactory(keys);
      if (manager == null) return;
      Bukkit.getPluginManager()
          .registerEvents(new EquipListener(plugin, itemFactory, manager), plugin);
    }
  }
}
