package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.player.stats.PlayerStatCache;

public final class CombatBootstrap {

    private final TheMob plugin;

    private CombatDebugService debug;
    private DamageCalculator calculator;
    private MobMultiplierService multipliers;
    private CombatListener listener;

    public CombatBootstrap(TheMob plugin) {
        this.plugin = plugin;
    }

    public void enable(PlayerStatCache cache, CustomEnchantSystem enchants) {
        this.debug = new CombatDebugService();
        this.multipliers = new MobMultiplierService(plugin);
        this.calculator = new DamageCalculator(multipliers);
        this.listener = new CombatListener(plugin, cache, calculator, debug, enchants);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(listener, plugin);

        if (plugin.getCommand("combatdebug") != null) {
            plugin.getCommand("combatdebug").setExecutor(new CombatDebugCommand(debug));
        }
    }
}

