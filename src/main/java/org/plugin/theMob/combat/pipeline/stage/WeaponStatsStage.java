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
        Player p = ctx.attacker();
        if (p == null) return;
        ItemStack weapon = p.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;
        double dmg = StatKeys.getNumber(plugin, meta, "damage");
        double extra = StatKeys.getNumber(plugin, meta, "extra_damage");
        double crit = StatKeys.getNumber(plugin, meta, "crit");
        double critMul = StatKeys.getNumber(plugin, meta, "crit_multiplier");
        double lifesteal = StatKeys.getNumber(plugin, meta, "lifesteal");
        double knockback = StatKeys.getNumber(plugin, meta, "knockback");
        if (dmg != 0) ctx.putWeaponStat("damage", dmg);
        if (extra != 0) ctx.putWeaponStat("extra_damage", extra);
        if (crit != 0) ctx.putWeaponStat("crit", crit);
        if (critMul != 0) ctx.putWeaponStat("crit_multiplier", critMul);
        if (lifesteal != 0) ctx.putWeaponStat("lifesteal", lifesteal);
        if (knockback != 0) ctx.putWeaponStat("knockback", knockback);
    }
}
