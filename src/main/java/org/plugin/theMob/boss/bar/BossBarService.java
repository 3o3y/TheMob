package org.plugin.theMob.boss.bar;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.MainThread;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnPoint;
import org.plugin.theMob.spawn.type.SpawnType;

import java.util.*;

public final class BossBarService {

    private static final double RANGE = 24.0;
    private static final double RANGE_SQ = RANGE * RANGE;

    public static final NamespacedKey BOSSBAR_KEY =
            new NamespacedKey("themob", "bossbar");

    private final TheMob plugin;
    private final MobManager mobs;
    private final PlayerBarCoordinator playerBars;

    private final Map<UUID, LivingEntity> bosses = new HashMap<>();
    private final Map<UUID, UUID> playerBoss = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private final Map<UUID, String> phaseTitle = new HashMap<>();

    private BukkitRunnable task;

    public BossBarService(TheMob plugin, MobManager mobs, PlayerBarCoordinator playerBars) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.playerBars = playerBars;
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================
    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        };
        task.runTaskTimer(plugin, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        Iterator<org.bukkit.boss.KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            var bar = it.next();
            if (BOSSBAR_KEY.equals(bar.getKey())) {
                bar.removeAll();
            }
        }

        bosses.clear();
        playerBoss.clear();
        dirty.clear();
        phaseTitle.clear();
    }

    // =====================================================
    // API
    // =====================================================
    public void registerBoss(LivingEntity boss) {
        if (boss == null) return;
        if (!mobs.isBoss(boss)) return;
        if (isFollowPlayerMob(boss)) return;

        MainThread.run(plugin, () -> {
            bosses.put(boss.getUniqueId(), boss);
            dirty.add(boss.getUniqueId());
        });
    }

    public void unregisterBoss(LivingEntity boss) {
        if (boss == null) return;
        MainThread.run(plugin, () -> internalRemoveBoss(boss.getUniqueId()));
    }

    public void markDirty(LivingEntity boss) {
        if (boss == null) return;
        dirty.add(boss.getUniqueId());
    }

    public void setPhaseTitle(LivingEntity boss, String title) {
        if (boss == null) return;
        UUID id = boss.getUniqueId();
        if (title == null || title.isBlank()) phaseTitle.remove(id);
        else phaseTitle.put(id, title);
        dirty.add(id);
    }

    public void restore() {
        for (var w : Bukkit.getWorlds()) {
            for (LivingEntity le : w.getLivingEntities()) {
                if (!mobs.isBoss(le)) continue;
                if (isFollowPlayerMob(le)) continue;

                bosses.put(le.getUniqueId(), le);
                dirty.add(le.getUniqueId());
            }
        }
    }

    // =====================================================
    // INTERNALS
    // =====================================================
    private void tick() {

        bosses.entrySet().removeIf(e -> {
            LivingEntity b = e.getValue();
            if (b == null || !b.isValid() || b.isDead()) {
                internalCleanupAfterBossRemoval(e.getKey());
                return true;
            }
            return false;
        });

        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
        dirty.clear();
    }

    private void updatePlayer(Player p) {
        LivingEntity nearest = null;
        double best = RANGE_SQ;

        for (LivingEntity boss : bosses.values()) {
            if (boss.getWorld() != p.getWorld()) continue;
            double d = boss.getLocation().distanceSquared(p.getLocation());
            if (d > best) continue;
            best = d;
            nearest = boss;
        }

        UUID pid = p.getUniqueId();
        UUID current = playerBoss.get(pid);

        if (nearest == null) {
            if (current != null) clear(p);
            return;
        }

        UUID nid = nearest.getUniqueId();
        if (current != null && current.equals(nid)) {
            if (dirty.contains(nid)) updateBar(p, nearest);
            return;
        }

        playerBoss.put(pid, nid);
        showBar(p, nearest);
    }

    private void showBar(Player p, LivingEntity boss) {
        PlayerBarCoordinator.Ctx ctx = playerBars.of(p);
        BossBar bar = ctx.bossBar();
        if (bar == null) {
            bar = Bukkit.createBossBar(
                    BOSSBAR_KEY,
                    "",
                    BarColor.PURPLE,
                    BarStyle.SOLID
            );
            ctx.setBossBar(bar);
        }
        if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        updateBar(p, boss);
    }

    private void updateBar(Player p, LivingEntity boss) {
        PlayerBarCoordinator.Ctx ctx = playerBars.of(p);
        BossBar bar = ctx.bossBar();
        if (bar == null) return;

        double hp = hpProgress(boss);
        bar.setProgress(hp);
        bar.setColor(colorFor(hp));

        String base = mobs.baseNameOf(boss);
        if (base == null) base = boss.getType().name();
        String phase = phaseTitle.get(boss.getUniqueId());
        bar.setTitle(phase != null ? base + " §8| §e" + phase : base);
    }

    private void clear(Player p) {
        playerBoss.remove(p.getUniqueId());
        PlayerBarCoordinator.Ctx ctx = playerBars.of(p);
        BossBar bar = ctx.bossBar();
        if (bar != null) bar.removeAll();
        ctx.setBossBar(null);
    }

    private void internalRemoveBoss(UUID id) {
        bosses.remove(id);
        dirty.remove(id);
        phaseTitle.remove(id);
        internalCleanupAfterBossRemoval(id);
    }

    private void internalCleanupAfterBossRemoval(UUID bossId) {
        List<UUID> affected = null;

        for (var e : playerBoss.entrySet()) {
            if (bossId.equals(e.getValue())) {
                if (affected == null) affected = new ArrayList<>();
                affected.add(e.getKey());
            }
        }

        if (affected != null) {
            for (UUID pid : affected) {
                Player p = Bukkit.getPlayer(pid);
                if (p != null && p.isOnline()) clear(p);
                else playerBoss.remove(pid);
            }
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private boolean isFollowPlayerMob(LivingEntity mob) {
        String spawnId = mob.getPersistentDataContainer()
                .get(mobs.keys().AUTO_SPAWN_ID, PersistentDataType.STRING);

        if (spawnId == null) return false;

        SpawnPoint sp = mobs.getAutoSpawnManager().get(spawnId);
        return sp != null && sp.type() == SpawnType.FOLLOW_PLAYER;
    }

    private static double hpProgress(LivingEntity boss) {
        var attr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null || attr.getValue() <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, boss.getHealth() / attr.getValue()));
    }

    private static BarColor colorFor(double p) {
        if (p > 0.75) return BarColor.PURPLE;
        if (p > 0.50) return BarColor.GREEN;
        if (p > 0.25) return BarColor.YELLOW;
        return BarColor.RED;
    }
}
