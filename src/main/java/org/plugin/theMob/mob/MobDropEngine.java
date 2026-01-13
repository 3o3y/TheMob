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
import org.plugin.theMob.TheMob;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.item.ItemLoreRenderer;
import org.plugin.theMob.progression.TieredLootTableService;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MobDropEngine {

    private MobManager mobs;

    private final TheMob plugin;
    private final ItemBuilderFromConfig builder;
    private final ItemLoreRenderer loreRenderer = new ItemLoreRenderer();
    private final Random random = new Random();

    private TieredLootTableService tieredLoot;

    // ✅ RICHTIGER KONSTRUKTOR
    public MobDropEngine(TheMob plugin) {
        this.plugin = plugin;
        this.builder = new ItemBuilderFromConfig(plugin);
    }

    public void bind(MobManager manager) {
        this.mobs = manager;
    }

    public void setTieredLoot(TieredLootTableService tieredLoot) {
        this.tieredLoot = tieredLoot;
    }

    // =====================================================
    // CORE
    // =====================================================
    public void handleDeath(LivingEntity mob, EntityDeathEvent event) {
        if (mobs == null || mob == null || event == null) return;

        // ❌ doppelte Ausführung verhindern
        if (mob.getPersistentDataContainer().has(
                mobs.keys().DROPS_DONE,
                PersistentDataType.INTEGER
        )) return;

        mob.getPersistentDataContainer().set(
                mobs.keys().DROPS_DONE,
                PersistentDataType.INTEGER,
                1
        );

        FileConfiguration cfg = mobs.mobConfigOf(mob);
        if (cfg == null) return;

        boolean isBoss = mobs.isBoss(mob);

        Integer noDropsFlag = mob.getPersistentDataContainer()
                .get(mobs.keys().NO_DROPS, PersistentDataType.INTEGER);

        event.getDrops().clear();
        event.setDroppedExp(0);

        // =================================================
// NORMAL DROPS (nur blockiert bei NO_DROPS)
// =================================================
        if (noDropsFlag == null || noDropsFlag != 1) {
            dropList(cfg.getMapList("drops"), mob);
        }

// =================================================
// OP / LEGENDARY DROPS (BOSS → IMMER erlaubt)
// =================================================
        if (isBoss && cfg.getBoolean("opdrop", false)) {
            dropList(cfg.getMapList("legendary-drops"), mob);
        }


        // =================================================
        // v1.9 TIERED LOOT
        // =================================================
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
