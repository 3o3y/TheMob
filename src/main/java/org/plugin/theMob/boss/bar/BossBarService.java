package org.plugin.theMob.boss.bar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnPoint;
import org.plugin.theMob.spawn.type.SpawnType;

public final class BossBarService implements Listener {
  public static final NamespacedKey BOSSBAR_KEY = new NamespacedKey("themob", "bossbar");
  private static final int BLOCKS_PER_CHUNK = 16;
  private final TheMob plugin;
  private final MobManager mobs;
  private final PlayerBarCoordinator playerBars;
  private final Map<UUID, LivingEntity> bosses = new HashMap<>();
  private final Map<UUID, UUID> playerBoss = new HashMap<>();
  private final Set<UUID> dirty = new HashSet<>();
  private final Map<UUID, String> phaseTitle = new HashMap<>();
  private BukkitRunnable task;

  public BossBarService(TheMob plugin, MobManager mobs, PlayerBarCoordinator playerBars) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.playerBars = playerBars;
  }

  public void start() {
    if (this.task == null) {
      this.task =
          new BukkitRunnable() {
            public void run() {
              BossBarService.this.tick();
            }
          };
      this.task.runTaskTimer(this.plugin, 10L, 10L);
    }
  }

  public void shutdown() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }

    Iterator<KeyedBossBar> it = Bukkit.getBossBars();

    while (it.hasNext()) {
      KeyedBossBar bar = it.next();
      if (BOSSBAR_KEY.equals(bar.getKey())) {
        bar.removeAll();
        Bukkit.removeBossBar(bar.getKey());
      }
    }

    this.bosses.clear();
    this.playerBoss.clear();
    this.dirty.clear();
    this.phaseTitle.clear();
  }

  public void registerBoss(LivingEntity boss) {
    if (boss != null
        && !org.plugin.theMob.core.CosmeticEntities.isVisual(boss)
        && this.mobs.isBoss(boss)) {
      if (!this.isFollowPlayerMob(boss)) {
        this.bosses.putIfAbsent(boss.getUniqueId(), boss);
        this.dirty.add(boss.getUniqueId());
      }
    }
  }

  public void unregisterBoss(LivingEntity boss) {
    if (boss != null) {
      UUID bid = boss.getUniqueId();
      this.bosses.remove(bid);
      this.dirty.remove(bid);
      this.phaseTitle.remove(bid);

      for (UUID pid : new HashSet<>(this.playerBoss.keySet())) {
        UUID current = this.playerBoss.get(pid);
        if (bid.equals(current)) {
          Player p = Bukkit.getPlayer(pid);
          if (p != null && p.isOnline()) {
            PlayerBarCoordinator.Ctx ctx = this.playerBars.of(p);
            BossBar bar = ctx.bossBar();
            if (bar != null) {
              bar.removePlayer(p);
            }
          }

          this.playerBoss.remove(pid);
        }
      }
    }
  }

  public void setPhaseTitle(LivingEntity boss, String title) {
    if (boss != null) {
      UUID id = boss.getUniqueId();
      if (title != null && !title.isBlank()) {
        this.phaseTitle.put(id, title);
      } else {
        this.phaseTitle.remove(id);
      }

      this.dirty.add(id);
    }
  }

  public void markDirty(LivingEntity boss) {
    if (boss != null) {
      this.dirty.add(boss.getUniqueId());
    }
  }

  public void restore() {
    for (World w : Bukkit.getWorlds()) {
      for (LivingEntity le : w.getLivingEntities()) {
        if (!org.plugin.theMob.core.CosmeticEntities.isVisual(le)
            && this.mobs.isBoss(le)
            && !this.isFollowPlayerMob(le)) {
          this.bosses.putIfAbsent(le.getUniqueId(), le);
          this.dirty.add(le.getUniqueId());
        }
      }
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    Bukkit.getScheduler().runTask(this.plugin, () -> this.updatePlayer(e.getPlayer()));
  }

  private void tick() {
    Iterator<Entry<UUID, LivingEntity>> it = this.bosses.entrySet().iterator();

    while (it.hasNext()) {
      Entry<UUID, LivingEntity> entry = it.next();
      LivingEntity b = entry.getValue();
      if (b == null
          || !b.isValid()
          || b.isDead()
          || org.plugin.theMob.core.CosmeticEntities.isVisual(b)) {
        UUID id = entry.getKey();
        it.remove();
        this.dirty.remove(id);
        this.phaseTitle.remove(id);
      }
    }

    for (Player p : Bukkit.getOnlinePlayers()) {
      this.updatePlayer(p);
    }
  }

  private void updatePlayer(Player p) {
    UUID pid = p.getUniqueId();
    UUID current = this.playerBoss.get(pid);
    LivingEntity activeBoss = null;

    for (LivingEntity boss : this.bosses.values()) {
      if (boss.getWorld() == p.getWorld() && this.isInsideArena(p, boss)) {
        activeBoss = boss;
        break;
      }
    }

    if (activeBoss == null) {
      if (current != null) {
        PlayerBarCoordinator.Ctx ctx = this.playerBars.of(p);
        BossBar bar = ctx.bossBar();
        if (bar != null) {
          bar.removePlayer(p);
          ctx.setBossBar(null);
        }

        this.playerBoss.remove(pid);
      }
    } else {
      UUID bid = activeBoss.getUniqueId();
      if (current != null && current.equals(bid)) {
        if (true) {
          this.updateBar(p, activeBoss);
        }
      } else {
        this.playerBoss.put(pid, bid);
        this.showBar(p, activeBoss);
      }
    }
  }

  private boolean isInsideArena(Player p, LivingEntity boss) {
    BossTemplate tpl = this.mobs.getBossTemplate(boss);
    if (tpl == null) {
      return false;
    } else {
      int chunks = Math.max(1, tpl.arenaRadiusChunks());
      double radius = chunks * 16;
      double radiusSq = radius * radius;
      return p.getWorld() == boss.getWorld()
          && p.getLocation().distanceSquared(boss.getLocation()) <= radiusSq + 4.0;
    }
  }

  private void showBar(Player p, LivingEntity boss) {
    PlayerBarCoordinator.Ctx ctx = this.playerBars.of(p);
    BossBar bar = ctx.bossBar();
    if (bar == null) {
      bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID, new BarFlag[0]);
      ctx.setBossBar(bar);
    }

    if (!bar.getPlayers().contains(p)) {
      bar.addPlayer(p);
    }

    this.updateBar(p, boss);
  }

  private void updateBar(Player p, LivingEntity boss) {
    PlayerBarCoordinator.Ctx ctx = this.playerBars.of(p);
    BossBar bar = ctx.bossBar();
    if (bar != null) {
      double hp = hpProgress(boss);
      bar.setProgress(hp);
      bar.setColor(colorFor(hp));
      bar.setTitle(Placeholder.resolve("{mob_name}", boss, null, p));
      this.dirty.remove(boss.getUniqueId());
    }
  }

  private void removeBoss(UUID id) {
    this.bosses.remove(id);
    this.dirty.remove(id);
    this.phaseTitle.remove(id);
  }

  public double getBossProgress(LivingEntity boss) {
    if (boss == null) {
      return -1.0;
    } else {
      UUID id = boss.getUniqueId();
      if (!this.bosses.containsKey(id)) {
        return -1.0;
      } else {
        AttributeInstance attr = org.plugin.theMob.compat.BukkitTypes.instance(boss, "MAX_HEALTH");
        return attr != null && !(attr.getValue() <= 0.0)
            ? Math.max(0.0, Math.min(1.0, boss.getHealth() / attr.getValue()))
            : -1.0;
      }
    }
  }

  private boolean isFollowPlayerMob(LivingEntity mob) {
    if (this.mobs.getAutoSpawnManager() == null) {
      return false;
    } else {
      String spawnId =
          (String)
              mob.getPersistentDataContainer()
                  .get(this.mobs.keys().AUTO_SPAWN_ID, PersistentDataType.STRING);
      if (spawnId == null) {
        return false;
      } else {
        SpawnPoint sp = this.mobs.getAutoSpawnManager().get(spawnId);
        return sp != null && sp.type() == SpawnType.FOLLOW_PLAYER;
      }
    }
  }

  private static double hpProgress(LivingEntity boss) {
    AttributeInstance attr = org.plugin.theMob.compat.BukkitTypes.instance(boss, "MAX_HEALTH");
    return attr != null && !(attr.getValue() <= 0.0)
        ? Math.max(0.0, Math.min(1.0, boss.getHealth() / attr.getValue()))
        : 0.0;
  }

  private static BarColor colorFor(double p) {
    if (p > 0.75) {
      return BarColor.PURPLE;
    } else if (p > 0.5) {
      return BarColor.GREEN;
    } else {
      return p > 0.25 ? BarColor.YELLOW : BarColor.RED;
    }
  }

  public void removeBossCompletely(LivingEntity boss) {
    if (boss != null) {
      UUID bid = boss.getUniqueId();
      this.removeBoss(bid);

      for (Player p : Bukkit.getOnlinePlayers()) {
        PlayerBarCoordinator.Ctx ctx = this.playerBars.of(p);
        BossBar bar = ctx.bossBar();
        if (bar != null) {
          bar.removePlayer(p);
          ctx.setBossBar(null);
        }
      }

      this.playerBoss.values().removeIf(id -> id.equals(bid));
    }
  }
}
