package org.plugin.theMob.ui;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.mob.MobManager;

public final class MobHealthDisplay {
  private static final double NAME_RANGE = 14.0;
  private static final double FULL_RANGE = 7.0;
  private static final double NAME_RANGE_SQ = 196.0;
  private static final double FULL_RANGE_SQ = 49.0;
  private final JavaPlugin plugin;
  private final MobManager mobs;
  private final BossBarService bossBars;
  private final Set<UUID> tracked = new HashSet<>();
  private BukkitRunnable moveTask;
  private int cursor;

  public MobHealthDisplay(JavaPlugin plugin, MobManager mobs, BossBarService bossBars) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.bossBars = bossBars;
    this.startMoveUpdater();
  }

  public void onSpawn(LivingEntity mob) {
    if (mob != null && !org.plugin.theMob.core.CosmeticEntities.isVisual(mob)) {
      this.tracked.add(mob.getUniqueId());
      this.update(mob);
    }
  }

  public void onDeath(LivingEntity mob) {
    if (mob != null) {
      this.tracked.remove(mob.getUniqueId());
      mob.setCustomNameVisible(false);
    }
  }

  public void update(LivingEntity mob) {
    if (mob != null
        && mob.isValid()
        && !mob.isDead()
        && !org.plugin.theMob.core.CosmeticEntities.isVisual(mob)) {
      if (plugin instanceof org.plugin.theMob.TheMob owner
          && owner.activity() != null
          && !owner.activity().active(mob)) {
        if (mob.isCustomNameVisible()) mob.setCustomNameVisible(false);
        return;
      }
      if (this.mobs.isCustomMob(mob)) {
        FileConfiguration cfg = this.mobs.mobConfigOf(mob);
        boolean displayHealth = cfg == null || cfg.getBoolean("display-health", true);
        MobHealthDisplay.Visibility vis = this.resolveVisibility(mob);
        if (!vis.visible) {
          mob.setCustomNameVisible(false);
        } else {
          String base = this.mobs.baseNameOf(mob);
          if (base == null || base.isBlank()) {
            base = mob.getType().name();
          }

          if (!displayHealth) {
            mob.setCustomName(base);
            mob.setCustomNameVisible(true);
          } else {
            String name;
            if (vis.full) {
              AttributeInstance maxAttr =
                  org.plugin.theMob.compat.BukkitTypes.instance(mob, "MAX_HEALTH");
              if (maxAttr == null) {
                return;
              }

              int max = (int) Math.max(1.0, maxAttr.getValue());
              int hp;
              if (this.mobs.isBoss(mob)) {
                double progress = this.bossBars.getBossProgress(mob);
                if (progress < 0.0) {
                  return;
                }

                hp = (int) Math.round(progress * max);
              } else {
                hp = (int) Math.max(0.0, mob.getHealth());
              }

              name = base + " §c❤ §f" + hp + "/" + max;
            } else {
              name = base;
            }

            if (!name.equals(mob.getCustomName())) {
              mob.setCustomName(name);
            }

            mob.setCustomNameVisible(true);
          }
        }
      }
    }
  }

  private void startMoveUpdater() {
    this.moveTask =
        new BukkitRunnable() {
          public void run() {
            if (!MobHealthDisplay.this.tracked.isEmpty()) {
              UUID[] ids = tracked.toArray(UUID[]::new);
              int work =
                  Math.min(
                      ids.length,
                      org.plugin.theMob.compat.Numbers.clamp(
                          plugin.getConfig().getInt("performance.nameplates-per-cycle", 128),
                          16,
                          512));
              for (int i = 0; i < work; i++) {
                UUID uuid = ids[(cursor + i) % ids.length];
                if (Bukkit.getEntity(uuid) instanceof LivingEntity mob) {
                  if (mob.isValid() && !mob.isDead()) {
                    MobHealthDisplay.this.update(mob);
                  } else {
                    tracked.remove(uuid);
                  }
                } else {
                  tracked.remove(uuid);
                }
              }
              cursor = (cursor + work) % ids.length;
            }
          }
        };
    this.moveTask.runTaskTimer(this.plugin, 5L, 5L);
  }

  private MobHealthDisplay.Visibility resolveVisibility(LivingEntity mob) {
    boolean visible = false;
    boolean full = false;

    var candidates =
        plugin instanceof org.plugin.theMob.TheMob owner && owner.activity() != null
            ? owner.activity().nearby(mob.getLocation(), NAME_RANGE, false)
            : Bukkit.getOnlinePlayers();
    for (Player p : candidates) {
      if (p.getWorld() == mob.getWorld()) {
        double d = p.getLocation().distanceSquared(mob.getLocation());
        if (!(d > 196.0) && this.hasClearLOS(p, mob)) {
          visible = true;
          if (d <= 49.0) {
            full = true;
            break;
          }
        }
      }
    }

    return new MobHealthDisplay.Visibility(visible, full);
  }

  private boolean hasClearLOS(Player p, LivingEntity mob) {
    return p.hasLineOfSight(mob);
  }

  private record Visibility(boolean visible, boolean full) {}
}
