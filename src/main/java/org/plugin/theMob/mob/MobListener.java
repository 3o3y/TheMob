package org.plugin.theMob.mob;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.behavior.BossBehaviorController;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;
import org.plugin.theMob.ui.MobHealthDisplay;

public final class MobListener implements Listener {
  private final MobManager mobs;
  private final BossBarService bossBars;
  private final BossActionEngine bossActions;
  private final BossBehaviorController behaviors;
  private final KeyRegistry keys;
  private final AutoSpawnManager autoSpawn;
  private final TheMob plugin;

  public MobListener(
      TheMob plugin,
      MobManager mobs,
      MobHealthDisplay ignored,
      BossBarService bossBars,
      BossActionEngine bossActions,
      BossBehaviorController behaviors,
      KeyRegistry keys,
      AutoSpawnManager autoSpawn) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.bossBars = bossBars;
    this.bossActions = bossActions;
    this.behaviors = behaviors;
    this.keys = keys;
    this.autoSpawn = autoSpawn;
  }

  @EventHandler
  public void onDeath(EntityDeathEvent e) {
    LivingEntity mob = e.getEntity();
    if (this.mobs.isCustomMob(mob)) {
      if (mob.getPersistentDataContainer()
              .getOrDefault(this.keys.BOSS_DEATH_HANDLED, PersistentDataType.INTEGER, 0)
          == 1) return;
      mob.getPersistentDataContainer()
          .set(this.keys.BOSS_DEATH_HANDLED, PersistentDataType.INTEGER, 1);
      this.plugin.api().died(mob);
      if (this.mobs.isBoss(mob)) {
        var cfg = this.mobs.mobConfigOf(mob);
        long seconds =
            cfg == null
                ? 0
                : cfg.getLong(
                    "boss-cooldown-seconds",
                    this.plugin.getConfig().getLong("boss-cooldowns.default-seconds", 0));
        if (seconds > 0)
          this.plugin
              .automation()
              .bossCooldowns()
              .setCooldownSeconds(this.mobs.mobIdOf(mob), seconds);
      }
      this.autoSpawn.onMobDeath(mob);
      boolean isBoss = this.mobs.isBoss(mob);
      if (isBoss) {
        if (this.behaviors != null) {
          this.behaviors.onBossDeath(mob);
        }

        this.plugin.bossPhases().onBossDeath(mob);
        this.bossActions.cleanupBoss(mob);
        this.autoSpawn.releaseBossLock(mob);
        this.bossBars.removeBossCompletely(mob);

        for (Entity nearby : mob.getWorld().getNearbyEntities(mob.getLocation(), 3.0, 3.0, 3.0)) {
          if (nearby instanceof ArmorStand stand
              && stand
                  .getPersistentDataContainer()
                  .has(this.keys.VISUAL_HEAD, PersistentDataType.INTEGER)) {
            stand.remove();
          }
        }
      }

      List<String> cmds = this.mobs.getDeathCommands(mob);
      if (cmds != null && !cmds.isEmpty()) {
        Player killer = mob.getKiller();

        for (String raw : cmds) {
          if (killer != null || !raw.contains("{player}")) {
            String resolved = Placeholder.resolve(raw, mob, null, killer);
            Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(), ChatColor.translateAlternateColorCodes('&', resolved));
          }
        }
      }

      this.mobs.onMobDeath(mob, e);
      this.plugin.getStuckDefense().remove(mob);
    }
  }
}
