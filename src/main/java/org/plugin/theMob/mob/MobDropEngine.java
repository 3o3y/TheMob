package org.plugin.theMob.mob;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.item.ItemLoreRenderer;
import org.plugin.theMob.progression.TieredLootTableService;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MobDropEngine {

    private MobManager mobs;
    private final ItemBuilderFromConfig builder;
    private final ItemLoreRenderer loreRenderer = new ItemLoreRenderer();
    private final Random random = new Random();

    // v1.9 optional
    private TieredLootTableService tieredLoot;

    public MobDropEngine(ItemBuilderFromConfig builder) {
        this.builder = builder;
    }

    public void bind(MobManager manager) {
        this.mobs = manager;
    }

    public void setTieredLoot(TieredLootTableService tieredLoot) {
        this.tieredLoot = tieredLoot;
    }

    public void handleDeath(LivingEntity mob, EntityDeathEvent event) {
        if (mobs == null || mob == null || event == null) return;

        if (mob.getPersistentDataContainer().has(
                mobs.keys().DROPS_DONE,
                PersistentDataType.INTEGER
        )) return;

        mob.getPersistentDataContainer().set(
                mobs.keys().DROPS_DONE,
                PersistentDataType.INTEGER,
                1
        );

        Integer noDrops = mob.getPersistentDataContainer()
                .get(mobs.keys().NO_DROPS, PersistentDataType.INTEGER);

        event.getDrops().clear();
        event.setDroppedExp(0);

        if (noDrops != null && noDrops == 1) return;

        FileConfiguration cfg = mobs.mobConfigOf(mob);
        if (cfg == null) return;

        // ---------- Normal drops ----------
        dropList(cfg.getMapList("drops"), mob);

        // ---------- Boss legendary drops ----------
        if (mobs.isBoss(mob) && cfg.getBoolean("opdrop", false)) {
            dropList(cfg.getMapList("legendary-drops"), mob);
        }

        // ---------- v1.9 Tiered loot ----------
        if (tieredLoot != null) {
            String tier = cfg.getString("loot-tier");
            if (tier != null) {
                Material rolled = tieredLoot.roll(tier);
                if (rolled != null) {
                    mob.getWorld().dropItemNaturally(
                            mob.getLocation(),
                            new ItemStack(rolled)
                    );
                }
            }
        }

        handleXp(cfg, mob);
    }

    // =====================================================
    // XP
    // =====================================================
    private void handleXp(FileConfiguration cfg, LivingEntity mob) {
        int xp = cfg.getInt("xp", 0);
        if (xp <= 0) return;

        World world = mob.getWorld();
        ExperienceOrb orb = world.spawn(mob.getLocation(), ExperienceOrb.class);
        orb.setExperience(xp);

        world.spawnParticle(
                Particle.HAPPY_VILLAGER,
                mob.getLocation().add(0, 0.8, 0),
                10, 0.4, 0.4, 0.4, 0.01
        );
    }

    // =====================================================
    // DROPS
    // =====================================================
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
