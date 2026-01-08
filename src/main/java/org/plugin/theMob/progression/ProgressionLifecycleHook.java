package org.plugin.theMob.progression;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ProgressionLifecycleHook implements Listener {

    private final PlayerProgressionManager manager;

    public ProgressionLifecycleHook(PlayerProgressionManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.remove(event.getPlayer().getUniqueId());
    }
}
