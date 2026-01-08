package org.plugin.theMob.control.feedback;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.control.AutomationScalingSystem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player/Admin-facing feedback so "nothing spawning" never feels like the plugin is broken.
 */
public final class SpawnBlockFeedbackService {

    private static final long TICK_INTERVAL = 20L;              // 1s
    private static final long PLAYER_COOLDOWN_TICKS = 60L;      // 3s
    private static final long GLOBAL_COOLDOWN_TICKS = 40L;      // 2s

    private final Plugin plugin;
    private final AutomationScalingSystem sys;

    private long lastBlockedBudget;
    private long lastBlockedThrottle;
    private long lastBlockedCooldown;

    private long lastGlobalNotifyTick = 0L;
    private final Map<UUID, Long> lastPlayerNotifyTick = new ConcurrentHashMap<>();

    private BukkitRunnable task;

    public SpawnBlockFeedbackService(Plugin plugin, AutomationScalingSystem sys) {
        this.plugin = plugin;
        this.sys = sys;
    }

    public void start() {
        if (task != null) return;

        var stats = sys.gate().stats();
        lastBlockedBudget = safe(stats.getBlockedBudget());
        lastBlockedThrottle = safe(stats.getBlockedThrottle());
        lastBlockedCooldown = safe(stats.getBlockedCooldown());

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 40L, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastPlayerNotifyTick.clear();
    }

    private void tick() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        var tps = sys.tps();
        var throttle = sys.throttling();
        var gateStats = sys.gate().stats();

        double tps1m = tps.tps1m();
        var state = throttle.state(tps1m);

        if ("HARD_STOP".equalsIgnoreCase(state.name())) {
            notifyAllPlayersRateLimited(
                    "§4⛔ Spawns pausiert §7(§cTPS Schutz aktiv§7) §8– warte kurz…"
            );
        }

        long blockedBudget = safe(gateStats.getBlockedBudget());
        long blockedThrottle = safe(gateStats.getBlockedThrottle());
        long blockedCooldown = safe(gateStats.getBlockedCooldown());

        long dBudget = blockedBudget - lastBlockedBudget;
        long dThrottle = blockedThrottle - lastBlockedThrottle;
        long dCooldown = blockedCooldown - lastBlockedCooldown;

        lastBlockedBudget = blockedBudget;
        lastBlockedThrottle = blockedThrottle;
        lastBlockedCooldown = blockedCooldown;

        if (dBudget > 0) {
            notifyAdminsRateLimited("§e⚠ Spawns blockiert §7(§fMob-Cap/Budget§7)");
        }
        if (dThrottle > 0) {
            notifyAdminsRateLimited("§e⚠ Spawns blockiert §7(§fTPS Throttle§7)");
        }
        if (dCooldown > 0) {
            notifyAdminsRateLimited("§e⚠ Boss-Spawn blockiert §7(§fCooldown aktiv§7)");
        }
    }

    private void notifyAllPlayersRateLimited(String msg) {
        long now = Bukkit.getCurrentTick();
        if (now - lastGlobalNotifyTick < GLOBAL_COOLDOWN_TICKS) return;
        lastGlobalNotifyTick = now;

        for (Player p : Bukkit.getOnlinePlayers()) {
            sendActionBarRateLimited(p, msg);
        }
    }

    private void notifyAdminsRateLimited(String msg) {
        long now = Bukkit.getCurrentTick();
        if (now - lastGlobalNotifyTick < GLOBAL_COOLDOWN_TICKS) return;
        lastGlobalNotifyTick = now;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("themob.admin")) continue;
            sendActionBarRateLimited(p, msg);
        }
    }

    private void sendActionBarRateLimited(Player p, String msg) {
        long now = Bukkit.getCurrentTick();
        long last = lastPlayerNotifyTick.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < PLAYER_COOLDOWN_TICKS) return;

        lastPlayerNotifyTick.put(p.getUniqueId(), now);
        p.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(msg)
        );
    }

    private long safe(long v) {
        return Math.max(0L, v);
    }
}
