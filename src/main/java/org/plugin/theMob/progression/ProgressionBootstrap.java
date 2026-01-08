package org.plugin.theMob.progression;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ProgressionBootstrap {

    private final ProgressionConfig config;

    public ProgressionBootstrap(Plugin plugin) {
        this.config = new ProgressionConfig(plugin);

        if (!config.isEnabled()) return;

        ItemKeyRegistry keys = new ItemKeyRegistry(plugin);
        CustomItemFactory itemFactory = new CustomItemFactory(keys);
        PlayerProgressionManager manager = new PlayerProgressionManager();

        Bukkit.getPluginManager().registerEvents(
                new EquipListener(itemFactory, manager),
                plugin
        );
    }
}
