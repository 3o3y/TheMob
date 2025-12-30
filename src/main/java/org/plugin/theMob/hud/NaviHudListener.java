package org.plugin.theMob.hud;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class NaviHudListener implements Listener {

    private final NaviHudService hud;

    public NaviHudListener(NaviHudService hud) {
        this.hud = hud;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        hud.onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hud.onQuit(e.getPlayer());
    }
}
