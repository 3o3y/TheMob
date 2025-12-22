package org.plugin.theMob.boss.bar;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.mob.MobManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossBarService {

    private static final double RANGE = 24.0;
    private static final double RANGE_SQ = RANGE * RANGE;

    private final TheMob plugin;
    private final MobManager mobs;
    private final PlayerBarCoordinator bars;

// bossUUID -> boss entity
    private final Map<UUID, LivingEntity> bosses = new HashMap<>();
// playerUUID -> bossUUID
    private final Map<UUID, UUID> playerBoss = new HashMap<>();
// bossUUID dirty
    private final Set<UUID> dirtyBosses = new HashSet<>();
// bossUUID -> phase title
    private final Map<UUID, String> phaseTitle = new HashMap<>();
    private final Set<UUID> pendingRemoval = new HashSet<>();
    private BukkitRunnable task;
    private boolean running = false;
    public BossBarService(TheMob plugin, MobManager mobs, PlayerBarCoordinator bars) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.bars = bars;
    }
// LIFECYCLE
    public void start() {
        if (running) return;
        running = true;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 10L, 10L);
    }
    public void shutdown() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            clear(p);
        }
        bosses.clear();
        playerBoss.clear();
        dirtyBosses.clear();
        phaseTitle.clear();
        pendingRemoval.clear();
    }
// REGISTRY
    public void registerBoss(LivingEntity boss) {
        if (boss == null) return;
        UUID id = boss.getUniqueId();
        if (bosses.containsKey(id)) return;
        bosses.put(id, boss);
        dirtyBosses.add(id);
    }
    public void unregisterBoss(LivingEntity boss) {
        if (boss == null) return;
        pendingRemoval.add(boss.getUniqueId());
    }
    public void markDirty(LivingEntity boss) {
        if (boss == null) return;
        dirtyBosses.add(boss.getUniqueId());
    }
// PHASE TITLE PUSH
    public void setPhaseTitle(LivingEntity boss, String title) {
        if (boss == null) return;
        UUID id = boss.getUniqueId();
        if (title == null || title.isBlank()) {
            phaseTitle.remove(id);
        } else {
            phaseTitle.put(id, title);
        }
        dirtyBosses.add(id); // trigger refresh for viewers
    }
// TICK
    private void tick() {
        if (!pendingRemoval.isEmpty()) {
            for (UUID id : pendingRemoval) {
                internalRemoveBoss(id);
            }
            pendingRemoval.clear();
        }
        Iterator<Map.Entry<UUID, LivingEntity>> it = bosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingEntity> e = it.next();
            LivingEntity b = e.getValue();
            if (b == null || !b.isValid() || b.isDead()) {
                UUID id = e.getKey();
                it.remove(); // safe iterator remove
                internalCleanupAfterBossRemoval(id);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
        dirtyBosses.clear();
    }
// INTERNAL REMOVE
    private void internalRemoveBoss(UUID id) {
        if (id == null) return;
        LivingEntity removed = bosses.remove(id);
        internalCleanupAfterBossRemoval(id);
    }
    private void internalCleanupAfterBossRemoval(UUID id) {
        dirtyBosses.remove(id);
        phaseTitle.remove(id);
        Set<UUID> affectedPlayers = null;
        for (Map.Entry<UUID, UUID> e : playerBoss.entrySet()) {
            if (id.equals(e.getValue())) {
                if (affectedPlayers == null) affectedPlayers = new HashSet<>();
                affectedPlayers.add(e.getKey());
            }
        }
        if (affectedPlayers != null) {
            for (UUID pid : affectedPlayers) {
                Player p = Bukkit.getPlayer(pid);
                if (p != null && p.isOnline()) {
                    clear(p);
                } else {
                    playerBoss.remove(pid);
                }
            }
        }
    }
    private void updatePlayer(Player p) {
        LivingEntity nearest = null;
        double best = RANGE_SQ;
        for (LivingEntity boss : bosses.values()) {
            if (boss == null) continue;
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
            if (dirtyBosses.contains(nid)) updateBar(p, nearest);
            return;
        }
        playerBoss.put(pid, nid);
        showBar(p, nearest);
    }
    private void clear(Player p) {
        playerBoss.remove(p.getUniqueId());
        var ctx = bars.of(p);
        BossBar bar = ctx.bossBar();
        if (bar != null) {
            bar.removeAll();
        }
        ctx.setBossBar(null);
        ctx.hideBoss();
    }
// BAR OPS
    private void showBar(Player p, LivingEntity boss) {
        var ctx = bars.of(p);
        BossBar bar = ctx.bossBar();
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
            ctx.setBossBar(bar);
        }
        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }
        ctx.showBoss();
        updateBar(p, boss);
    }
    private void updateBar(Player p, LivingEntity boss) {
        BossBar bar = bars.of(p).bossBar();
        if (bar == null) return;
        double hp = hpProgress(boss);
        bar.setProgress(hp);
        bar.setColor(colorFor(hp));
        String base = mobs.baseNameOf(boss);
        if (base == null) base = boss.getType().name();
        String phase = phaseTitle.get(boss.getUniqueId());
        if (phase != null) {
            bar.setTitle(base + " §8| §e" + phase);
        } else {
            bar.setTitle(base);
        }
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
