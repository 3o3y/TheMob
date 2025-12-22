package org.plugin.theMob.ui;

import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.mob.MobManager;

public final class MobHealthDisplay {

    private final MobManager mobs;
    public MobHealthDisplay(MobManager mobs) {
        this.mobs = mobs;
    }
    public void onSpawn(LivingEntity mob) {
        update(mob);
    }
    public void update(LivingEntity mob) {
        if (mob == null || !mob.isValid() || mob.isDead()) return;
        if (!mobs.isCustomMob(mob)) return;
        String base = mobs.baseNameOf(mob);
        if (base == null || base.isBlank()) {
            base = mob.getType().name();
        }
        AttributeInstance maxAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr == null) return;
        double hp = Math.max(0.0, mob.getHealth());
        double max = Math.max(1.0, maxAttr.getValue());
        String name = ChatColor.translateAlternateColorCodes(
                '&',
                base + " §c❤ §f" + (int) hp + "/" + (int) max
        );
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }
    public void onDeath(LivingEntity mob) {
    }
}
