package org.plugin.theMob.mob;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.item.ItemLoreRenderer;
import org.plugin.theMob.progression.TieredLootTableService;

public final class MobDropEngine {
  private MobManager mobs;
  private final TheMob plugin;
  private final ItemBuilderFromConfig builder;
  private final ItemLoreRenderer loreRenderer = new ItemLoreRenderer();
  private final Random random = new Random();
  private TieredLootTableService tieredLoot;

  private record Drop(
      double chance, ItemStack prototype, String reference, int amount, boolean legendary) {}

  private final Map<String, List<Drop>> compiled = new java.util.HashMap<>();

  public MobDropEngine(TheMob plugin) {
    this.plugin = plugin;
    this.builder = new ItemBuilderFromConfig(plugin);
  }

  public void bind(MobManager manager) {
    this.mobs = manager;
    compiled.clear();
    for (String id : manager.registeredIds()) {
      var cfg = manager.mobConfigById(id);
      compiled.put(id + ":normal", compile(cfg.getMapList("drops"), false));
      compiled.put(id + ":legendary", compile(cfg.getMapList("legendary-drops"), true));
    }
  }

  private List<Drop> compile(List<Map<?, ?>> entries, boolean legendary) {
    var result = new java.util.ArrayList<Drop>();
    for (var raw : entries) {
      double chance = parse(raw.get("chance"), 1);
      if (!Double.isFinite(chance) || chance <= 0) continue;
      if (raw.get("provider") instanceof String provider && raw.get("item") instanceof String id) {
        double parsed = parse(raw.get("amount"), 1);
        if (!Double.isFinite(parsed) || parsed < 1 || parsed > 64 || parsed != Math.rint(parsed))
          continue;
        int amount = (int) parsed;
        result.add(new Drop(Math.min(1, chance), null, provider + ":" + id, amount, legendary));
        continue;
      }
      ItemStack item = builder.build(raw);
      if (item == null) continue;
      if (legendary) {
        var meta = item.getItemMeta();
        var rarity = new org.bukkit.NamespacedKey(plugin, "rarity");
        if (!meta.getPersistentDataContainer().has(rarity, PersistentDataType.STRING)) {
          meta.getPersistentDataContainer().set(rarity, PersistentDataType.STRING, "LEGENDARY");
          item.setItemMeta(meta);
        }
      }
      loreRenderer.apply(item);
      result.add(new Drop(Math.min(1, chance), item, null, 1, legendary));
    }
    return List.copyOf(result);
  }

  public void setTieredLoot(TieredLootTableService tieredLoot) {
    this.tieredLoot = tieredLoot;
  }

  public void handleDeath(LivingEntity mob, EntityDeathEvent event) {
    if (this.mobs != null && mob != null && event != null) {
      if (!mob.getPersistentDataContainer()
          .has(this.mobs.keys().DROPS_DONE, PersistentDataType.INTEGER)) {
        mob.getPersistentDataContainer()
            .set(this.mobs.keys().DROPS_DONE, PersistentDataType.INTEGER, 1);
        FileConfiguration cfg = this.mobs.mobConfigOf(mob);
        if (cfg != null) {
          boolean isBoss = this.mobs.isBoss(mob);
          Integer noDropsFlag =
              (Integer)
                  mob.getPersistentDataContainer()
                      .get(this.mobs.keys().NO_DROPS, PersistentDataType.INTEGER);
          if (!cfg.getBoolean("vanilla-drops", false) || noDropsFlag != null && noDropsFlag == 1)
            event.getDrops().clear();
          event.setDroppedExp(0);
          if (noDropsFlag == null || noDropsFlag != 1) {
            this.dropList(compiled.getOrDefault(mobs.mobIdOf(mob) + ":normal", List.of()), event);
          }

          if ((noDropsFlag == null || noDropsFlag != 1)
              && isBoss
              && cfg.getBoolean("opdrop", false)) {
            this.dropList(
                compiled.getOrDefault(mobs.mobIdOf(mob) + ":legendary", List.of()), event);
          }

          if ((noDropsFlag == null || noDropsFlag != 1) && this.tieredLoot != null) {
            String tier = cfg.getString("loot-tier");
            if (tier != null) {
              Material rolled = this.tieredLoot.roll(tier);
              if (rolled != null) {
                event.getDrops().add(new ItemStack(rolled));
              }
            }
          }

          if ((noDropsFlag == null || noDropsFlag != 1) && plugin.gear() != null)
            event
                .getDrops()
                .addAll(
                    plugin.gear().roll(mob, cfg.getInt("level", isBoss ? 5 : 1), isBoss, random));

          event.setDroppedExp(
              noDropsFlag != null && noDropsFlag == 1 ? 0 : Math.max(0, cfg.getInt("xp", 0)));
        }
      }
    }
  }

  private void dropList(List<Drop> list, EntityDeathEvent event) {
    if (list != null && !list.isEmpty()) {
      for (Drop drop : list)
        if (random.nextDouble() < drop.chance) {
          ItemStack item =
              drop.reference() == null
                  ? drop.prototype().clone()
                  : plugin.api().createItem(drop.reference()).orElse(null);
          if (item == null) continue;
          if (drop.reference() != null)
            item.setAmount(Math.min(drop.amount(), item.getMaxStackSize()));
          event.getDrops().add(item);
        }
    }
  }

  private double parse(Object o, double def) {
    try {
      return Double.parseDouble(String.valueOf(o));
    } catch (Exception var5) {
      return def;
    }
  }
}
