package org.plugin.theMob.player.stats.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.List;

public final class StatsMenuItemBuilder {

    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private StatsMenuItemBuilder() {}
    public static ItemStack stat(String name, double value, String desc) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(name);
        meta.setLore(List.of(
                "§f" + DF.format(value),
                "",
                desc
        ));
        it.setItemMeta(meta);
        return it;
    }
}
