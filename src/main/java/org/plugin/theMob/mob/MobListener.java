package org.plugin.theMob.mob;

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

import java.util.List;

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
            org.plugin.theMob.ui.MobHealthDisplay ignored,
            BossBarService bossBars,
            BossActionEngine bossActions,
            BossBehaviorController behaviors,
            KeyRegistry keys,
            AutoSpawnManager autoSpawn
    ) {
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
        if (!mobs.isCustomMob(mob)) return;

        // budget / autospaWN cleanup
        autoSpawn.onMobDeath(mob);

        final boolean isBoss = mobs.isBoss(mob);

        if (isBoss) {
            // ✅ stop behaviors FIRST (clean exit)
            if (behaviors != null) {
                behaviors.onBossDeath(mob);
            }

            // phase + actions + locks + bars
            plugin.bossPhases().onBossDeath(mob);
            bossActions.onBossDeath(mob);
            autoSpawn.releaseBossLock(mob);
            bossBars.removeBossCompletely(mob);

            // remove visuals (armorstands)
            for (Entity nearby : mob.getWorld().getNearbyEntities(mob.getLocation(), 3, 3, 3)) {
                if (nearby instanceof ArmorStand stand
                        && stand.getPersistentDataContainer().has(keys.VISUAL_HEAD, PersistentDataType.INTEGER)) {
                    stand.remove();
                }
            }
        }

        // death commands
        List<String> cmds = mobs.getDeathCommands(mob);
        if (cmds != null && !cmds.isEmpty()) {
            Player killer = mob.getKiller();

            for (String raw : cmds) {
                if (killer == null && raw.contains("{player}")) continue;

                String resolved = Placeholder.resolve(raw, mob, null, killer);
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        ChatColor.translateAlternateColorCodes('&', resolved)
                );
            }
        }

        // unified mob death lifecycle (drops/ui/autospawn cleanup hooks)
        mobs.onMobDeath(mob, e);
        plugin.getStuckDefense().remove(mob);
    }
}
