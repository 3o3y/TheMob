package org.plugin.theMob.combat.pipeline.stage;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.combat.pipeline.DamageContext;
import org.plugin.theMob.combat.pipeline.DamageStage;
import org.plugin.theMob.combat.pipeline.StatKeys;

public final class WeaponStatsStage implements DamageStage {

    private final Plugin plugin;

    public WeaponStatsStage(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void apply(DamageContext ctx) {
        if (ctx == null || plugin == null) return;

        Player p = ctx.attacker();
        if (p == null || !p.isOnline()) return;

        ItemStack weapon = p.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        double v;

        v = StatKeys.getNumber(plugin, meta, "damage");
        if (v != 0.0) ctx.putWeaponStat("damage", v);

        v = StatKeys.getNumber(plugin, meta, "extra_damage");
        if (v != 0.0) ctx.putWeaponStat("extra_damage", v);

        v = StatKeys.getNumber(plugin, meta, "crit");
        if (v > 0.0) ctx.putWeaponStat("crit", v);

        v = StatKeys.getNumber(plugin, meta, "crit_multiplier");
        if (v > 0.0) ctx.putWeaponStat("crit_multiplier", v);

        v = StatKeys.getNumber(plugin, meta, "lifesteal");
        if (v > 0.0) ctx.putWeaponStat("lifesteal", v);

        v = StatKeys.getNumber(plugin, meta, "knockback");
        if (v != 0.0) ctx.putWeaponStat("knockback", v);
    }
}
