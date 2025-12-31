package org.plugin.theMob.mob;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.item.ItemLoreRenderer;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MobDropEngine {

    private MobManager mobs;
    private final ItemBuilderFromConfig builder;
    private final ItemLoreRenderer loreRenderer = new ItemLoreRenderer();
    private final Random random = new Random();

    public MobDropEngine(ItemBuilderFromConfig builder) {
        this.builder = builder;
    }

    public void bind(MobManager manager) {
        this.mobs = manager;
    }

    public void handleDeath(LivingEntity mob, EntityDeathEvent event) {
        if (mobs == null || mob == null || event == null) return;
        if (mob.getPersistentDataContainer().has(
                mobs.keys().DROPS_DONE,
                PersistentDataType.INTEGER
        )) {
            return;
        }
        mob.getPersistentDataContainer().set(
                mobs.keys().DROPS_DONE,
                PersistentDataType.INTEGER,
                1
        );
        Integer noDrops = mob.getPersistentDataContainer()
                .get(mobs.keys().NO_DROPS, PersistentDataType.INTEGER);
        if (noDrops != null && noDrops == 1) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }

        FileConfiguration cfg = mobs.mobConfigOf(mob);
        if (cfg == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        dropList(cfg.getMapList("drops"), mob);
        if (mobs.isBoss(mob) && cfg.getBoolean("opdrop", false)) {
            dropList(cfg.getMapList("legendary-drops"), mob);
        }

    }

    private void dropList(List<Map<?, ?>> list, LivingEntity mob) {
        if (list == null || list.isEmpty()) return;

        for (Map<?, ?> raw : list) {
            double chance = parse(raw.get("chance"), 1.0);
            if (random.nextDouble() > chance) continue;

            ItemStack it = builder.build(raw);
            if (it == null) continue;

            loreRenderer.apply(it);
            mob.getWorld().dropItemNaturally(mob.getLocation(), it);
        }
    }

    private double parse(Object o, double def) {
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }
}
