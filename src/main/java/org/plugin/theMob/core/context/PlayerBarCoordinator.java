package org.plugin.theMob.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public final class PlayerBarCoordinator {
  private final Map<UUID, PlayerBarCoordinator.Ctx> map = new HashMap<>();

  public PlayerBarCoordinator.Ctx of(Player p) {
    return this.map.computeIfAbsent(p.getUniqueId(), k -> new PlayerBarCoordinator.Ctx());
  }

  public void remove(Player p) {
    PlayerBarCoordinator.Ctx ctx = this.map.remove(p.getUniqueId());
    if (ctx != null) {
      ctx.hideBoss();
      ctx.hideHud();
    }
  }

  public void clearAll() {
    for (PlayerBarCoordinator.Ctx ctx : this.map.values()) {
      ctx.hideBoss();
      ctx.hideHud();
    }

    this.map.clear();
  }

  public static final class Ctx {
    private BossBar bossBar;
    private BossBar hudBar;

    public BossBar bossBar() {
      return this.bossBar;
    }

    public BossBar hudBar() {
      return this.hudBar;
    }

    public void setBossBar(BossBar b) {
      this.bossBar = b;
    }

    public void setHudBar(BossBar b) {
      this.hudBar = b;
    }

    public void hideBoss() {
      if (this.bossBar != null) {
        this.bossBar.removeAll();
      }
    }

    public void hideHud() {
      if (this.hudBar != null) {
        this.hudBar.removeAll();
      }
    }
  }
}
