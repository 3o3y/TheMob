package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.player.stats.PlayerStatCache;
import org.plugin.theMob.progression.PlayerProgressionManager;
import org.plugin.theMob.progression.ProgressionCombatApplier;

public final class CombatBootstrap {

    private final TheMob plugin;

    private CombatDebugService debug;
    private DamageCalculator calculator;
    private MobMultiplierService multipliers;
    private CombatListener listener;

    private boolean enabled;

    public CombatBootstrap(TheMob plugin) {
        this.plugin = plugin;
    }

    // =====================================================
    // LEGACY ENABLE (v1.5 – v1.8)
    // =====================================================

    public void enable(PlayerStatCache cache, CustomEnchantSystem enchants) {
        enable(cache, enchants, null, null);
    }

    // =====================================================
    // v1.9 ENABLE (PROGRESSION AWARE)
    // =====================================================

    public synchronized void enable(
            PlayerStatCache cache,
            CustomEnchantSystem enchants,
            PlayerProgressionManager progression,
            ProgressionCombatApplier progressionCombat
    ) {
        if (enabled) return;
        enabled = true;

        this.debug = new CombatDebugService();
        this.multipliers = new MobMultiplierService(plugin);
        this.calculator = new DamageCalculator(multipliers);

        this.listener = new CombatListener(
                plugin,
                cache,
                calculator,
                debug,
                enchants,
                progression,
                progressionCombat
        );

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(listener, plugin);

        PluginCommand cmd = plugin.getCommand("combatdebug");
        if (cmd != null) {
            cmd.setExecutor(new CombatDebugCommand(debug));
        }
    }

    // =====================================================
    // DISABLE / RELOAD CLEANUP
    // =====================================================

    public synchronized void disable() {
        if (!enabled) return;
        enabled = false;

        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }

        PluginCommand cmd = plugin.getCommand("combatdebug");
        if (cmd != null) {
            cmd.setExecutor(null);
        }

        calculator = null;
        multipliers = null;
        debug = null;
    }
}
