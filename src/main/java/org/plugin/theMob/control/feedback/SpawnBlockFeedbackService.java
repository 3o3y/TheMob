package org.plugin.theMob.control.feedback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.control.AutomationScalingSystem;
import org.plugin.theMob.control.ThrottleManager;
import org.plugin.theMob.control.TpsTracker;
import org.plugin.theMob.control.debug.SpawnStatistics;

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
    this.reload();
  }

  public void reload() {
    this.showToPlayers =
        this.plugin.getConfig().getBoolean("automation.feedback.show-to-players", false);
  }

  public void start() {
    if (this.task == null) {
      SpawnStatistics stats = this.sys.gate().stats();
      this.lastBlockedBudget = this.safe(stats.getBlockedBudget());
      this.lastBlockedThrottle = this.safe(stats.getBlockedThrottle());
      this.lastBlockedCooldown = this.safe(stats.getBlockedCooldown());
      this.task =
          new BukkitRunnable() {
            public void run() {
              SpawnBlockFeedbackService.this.tick();
            }
          };
      this.task.runTaskTimer(this.plugin, 40L, 20L);
    }
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }

    this.lastPlayerNotifyTick.clear();
  }

  private void tick() {
    if (!Bukkit.getOnlinePlayers().isEmpty()) {
      TpsTracker tps = this.sys.tps();
      ThrottleManager throttle = this.sys.throttling();
      SpawnStatistics gateStats = this.sys.gate().stats();
      double tps1m = tps.tps1m();
      ThrottleManager.State state = throttle.state(tps1m);
      if ("HARD_STOP".equalsIgnoreCase(state.name())) {
        this.notifyRateLimited("§4⛔ Spawns paused §7(§cTPS protection active§7)");
      }

      long blockedBudget = this.safe(gateStats.getBlockedBudget());
      long blockedThrottle = this.safe(gateStats.getBlockedThrottle());
      long blockedCooldown = this.safe(gateStats.getBlockedCooldown());
      if (blockedBudget > this.lastBlockedBudget) {
        this.notifyRateLimited("§e⚠ Spawns blocked §7(§fMob budget§7)");
      }

      if (blockedThrottle > this.lastBlockedThrottle) {
        this.notifyRateLimited("§e⚠ Spawns blocked §7(§fTPS throttle§7)");
      }

      if (blockedCooldown > this.lastBlockedCooldown) {
        this.notifyRateLimited("§e⚠ Boss spawn blocked §7(§fCooldown active§7)");
      }

      this.lastBlockedBudget = blockedBudget;
      this.lastBlockedThrottle = blockedThrottle;
      this.lastBlockedCooldown = blockedCooldown;
    }
  }

  private void notifyRateLimited(String msg) {
    long now = sys.tps().currentTick();
    if (now - this.lastGlobalNotifyTick >= 40L) {
      this.lastGlobalNotifyTick = now;

      for (Player p : Bukkit.getOnlinePlayers()) {
        if (this.isAllowed(p)) {
          this.sendActionBarRateLimited(p, msg);
        }
      }
    }
  }

  private boolean isAllowed(Player p) {
    return this.showToPlayers ? true : p.isOp() || p.hasPermission("themob.admin");
  }

  private void sendActionBarRateLimited(Player p, String msg) {
    long now = sys.tps().currentTick();
    long last = this.lastPlayerNotifyTick.getOrDefault(p.getUniqueId(), 0L);
    if (now - last >= 60L) {
      this.lastPlayerNotifyTick.put(p.getUniqueId(), now);
      p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }
  }

  private long safe(long v) {
    return Math.max(0L, v);
  }
}
