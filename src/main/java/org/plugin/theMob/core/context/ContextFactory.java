package org.plugin.theMob.core.context;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.mob.MobManager;

public final class ContextFactory {

    private final MobManager mobs;
    public ContextFactory(MobManager mobs) {
        this.mobs = mobs;
    }
    public MobContext mob(LivingEntity e) {
        if (e == null) return null;
        String id = mobs.mobIdOf(e);
        if (id == null) return null;
        boolean boss = mobs.isBoss(e);
        return new MobContext(e.getUniqueId(), id, boss, e);
    }
    public BossContext boss(LivingEntity e) {
        if (e == null) return null;
        String id = mobs.mobIdOf(e);
        if (id == null) return null;
        Integer flag = e.getPersistentDataContainer()
                .get(mobs.keys().IS_BOSS, PersistentDataType.INTEGER);
        boolean isBoss = flag != null ? flag == 1 : mobs.isBoss(e);
        if (!isBoss) return null;
        BossTemplate tpl = mobs.bossTemplate(id);
        if (tpl == null) return null;
        return new BossContext(e.getUniqueId(), id, e, tpl);
    }
}
