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

public final class SpawnBlockFeedbackService {

    private static final long TICK_INTERVAL = 20L;
    private static final long PLAYER_COOLDOWN_TICKS = 60L;
    private static final long GLOBAL_COOLDOWN_TICKS = 40L;

    private final Plugin plugin;
    private final AutomationScalingSystem sys;

    private boolean showToPlayers;

    private long lastBlockedBudget;
    private long lastBlockedThrottle;
    private long lastBlockedCooldown;

    private long lastGlobalNotifyTick = 0L;
    private final Map<UUID, Long> lastPlayerNotifyTick = new ConcurrentHashMap<>();

    private BukkitRunnable task;

    public SpawnBlockFeedbackService(Plugin plugin, AutomationScalingSystem sys) {
        this.plugin = plugin;
        this.sys = sys;
        reload();
    }

    public void reload() {
        showToPlayers = plugin.getConfig().getBoolean(
                "automation.feedback.show-to-players",
                false
        );
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
            notifyRateLimited("§4⛔ Spawns paused §7(§cTPS protection active§7)");
        }

        long blockedBudget = safe(gateStats.getBlockedBudget());
        long blockedThrottle = safe(gateStats.getBlockedThrottle());
        long blockedCooldown = safe(gateStats.getBlockedCooldown());

        if (blockedBudget > lastBlockedBudget) {
            notifyRateLimited("§e⚠ Spawns blocked §7(§fMob budget§7)");
        }
        if (blockedThrottle > lastBlockedThrottle) {
            notifyRateLimited("§e⚠ Spawns blocked §7(§fTPS throttle§7)");
        }
        if (blockedCooldown > lastBlockedCooldown) {
            notifyRateLimited("§e⚠ Boss spawn blocked §7(§fCooldown active§7)");
        }

        lastBlockedBudget = blockedBudget;
        lastBlockedThrottle = blockedThrottle;
        lastBlockedCooldown = blockedCooldown;
    }

    private void notifyRateLimited(String msg) {
        long now = Bukkit.getCurrentTick();
        if (now - lastGlobalNotifyTick < GLOBAL_COOLDOWN_TICKS) return;
        lastGlobalNotifyTick = now;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isAllowed(p)) continue;
            sendActionBarRateLimited(p, msg);
        }
    }

    private boolean isAllowed(Player p) {
        if (showToPlayers) return true;
        return p.isOp() || p.hasPermission("themob.admin");
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
