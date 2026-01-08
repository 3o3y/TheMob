package org.plugin.theMob.progression;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ProgressionV19Bootstrap {

    private final PlayerProgressionManager players;
    private final ProgressionCombatApplier combatApplier;
    private final TieredLootTableService lootTables;

    public ProgressionV19Bootstrap(Plugin plugin) {

        ProgressionConfig cfg = new ProgressionConfig(plugin);
        if (!cfg.isEnabled()) {
            this.players = null;
            this.combatApplier = null;
            this.lootTables = null;
            return;
        }

        this.players = new PlayerProgressionManager();

        SetBonusEngine setBonuses = new SetBonusEngine();
        ItemSynergyResolver synergies = new ItemSynergyResolver();
        BossDifficultyRegistry difficulties = new BossDifficultyRegistry();

        this.combatApplier = new ProgressionCombatApplier(setBonuses, synergies);
        this.lootTables = new TieredLootTableService();

        Bukkit.getPluginManager().registerEvents(
                new ProgressionLifecycleHook(players),
                plugin
        );

        // Default difficulties
        difficulties.register(new BossDifficultyProfile("normal", 1.0, 1.0, 1.0));
        difficulties.register(new BossDifficultyProfile("hard", 1.5, 1.3, 1.4));
        difficulties.register(new BossDifficultyProfile("mythic", 2.2, 1.8, 2.0));
    }

    public PlayerProgressionManager players() {
        return players;
    }

    public ProgressionCombatApplier combat() {
        return combatApplier;
    }

    public TieredLootTableService loot() {
        return lootTables;
    }
}
