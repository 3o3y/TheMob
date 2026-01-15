package org.plugin.theMob.mob;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.ai.MobAIService;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;

import java.util.List;

public final class MobListener implements Listener {

    private final MobManager mobs;
    private final BossBarService bossBars;
    private final BossActionEngine bossActions;
    private final KeyRegistry keys;
    private final AutoSpawnManager autoSpawn;
    private final MobAIService mobAI;

    public MobListener(
            MobManager mobs,
            org.plugin.theMob.ui.MobHealthDisplay ignored,
            BossBarService bossBars,
            BossActionEngine bossActions,
            KeyRegistry keys,
            AutoSpawnManager autoSpawn,
            MobAIService mobAI
    ) {
        this.mobs = mobs;
        this.bossBars = bossBars;
        this.bossActions = bossActions;
        this.keys = keys;
        this.autoSpawn = autoSpawn;
        this.mobAI = mobAI;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();

        if (!mobs.isCustomMob(mob)) return;

        // =========================
        // AI CLEANUP
        // =========================
        if (mob instanceof Mob bukkitMob) {
            mobAI.unregister(bukkitMob);
        }

        // =========================
        // AUTOSPAWN
        // =========================
        autoSpawn.onMobDeath(mob);

        // =========================
        // VISUAL CLEANUP (BOSS)
        // =========================
        if (mobs.isBoss(mob)) {
            for (Entity nearby : mob.getWorld().getNearbyEntities(
                    mob.getLocation(),
                    3.0, 3.0, 3.0
            )) {
                if (nearby instanceof ArmorStand stand &&
                        stand.getPersistentDataContainer().has(
                                keys.VISUAL_HEAD,
                                PersistentDataType.INTEGER
                        )) {
                    stand.remove();
                }
            }
        }

        // =========================
        // BOSS LOGIC
        // =========================
        if (mobs.isBoss(mob)) {
            bossActions.onBossDeath(mob);
            autoSpawn.releaseBossLock(mob);
        }

        // =========================
        // DEATH COMMANDS
        // =========================
        List<String> deathCommands = mobs.getDeathCommands(mob);
        if (deathCommands != null && !deathCommands.isEmpty()) {

            Player killer = mob.getKiller();

            for (String raw : deathCommands) {

                // If command requires {player} but no killer exists -> skip
                if (killer == null && raw != null && raw.contains("{player}")) {
                    Bukkit.getLogger().info("[TheMob] Skipped death-command (no killer): " + raw);
                    continue;
                }

                String resolved = Placeholder.resolve(raw, mob, null, killer);

                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        ChatColor.translateAlternateColorCodes('&', resolved)
                );
            }
        }

        // =========================
        // FINAL CLEANUP
        // =========================
        mobs.onMobDeath(mob, e);
    }
}
