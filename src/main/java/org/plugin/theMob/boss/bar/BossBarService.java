package org.plugin.theMob.boss.bar;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.Placeholder;
import org.plugin.theMob.core.context.PlayerBarCoordinator;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnPoint;
import org.plugin.theMob.spawn.type.SpawnType;

import java.util.*;

public final class BossBarService implements Listener {

    public static final NamespacedKey BOSSBAR_KEY =
            new NamespacedKey("themob", "bossbar");

    private static final int BLOCKS_PER_CHUNK = 16;

    private final TheMob plugin;
    private final MobManager mobs;
    private final PlayerBarCoordinator playerBars;

    // MAIN THREAD ONLY
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
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 10L, 10L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        Iterator<KeyedBossBar> it = Bukkit.getBossBars();
        while (it.hasNext()) {
            KeyedBossBar bar = it.next();
            if (BOSSBAR_KEY.equals(bar.getKey())) {
                bar.removeAll();
                Bukkit.removeBossBar(bar.getKey());
            }
        }

        bosses.clear();
        playerBoss.clear();
        dirty.clear();
        phaseTitle.clear();
    }

    // =====================================================
    // PUBLIC API
    // =====================================================

    public void registerBoss(LivingEntity boss) {
        if (boss == null || !mobs.isBoss(boss)) return;
        if (isFollowPlayerMob(boss)) return;

        bosses.putIfAbsent(boss.getUniqueId(), boss);
        dirty.add(boss.getUniqueId());
    }

    public void unregisterBoss(LivingEntity boss) {
        if (boss == null) return;

        UUID bid = boss.getUniqueId();

        // Boss aus Tracking entfernen
        bosses.remove(bid);
        dirty.remove(bid);
        phaseTitle.remove(bid);

        // BossBar von ALLEN Spielern entfernen
        for (UUID pid : new HashSet<>(playerBoss.keySet())) {
            UUID current = playerBoss.get(pid);
            if (!bid.equals(current)) continue;

            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline()) {
                PlayerBarCoordinator.Ctx ctx = playerBars.of(p);
                BossBar bar = ctx.bossBar();
                if (bar != null) {
                    bar.removePlayer(p);
                }
            }

            playerBoss.remove(pid);
        }
    }


    public void setPhaseTitle(LivingEntity boss, String title) {
        if (boss == null) return;

        UUID id = boss.getUniqueId();
        if (title == null || title.isBlank()) phaseTitle.remove(id);
        else phaseTitle.put(id, title);

        dirty.add(id);
    }

    public void markDirty(LivingEntity boss) {
        if (boss != null) dirty.add(boss.getUniqueId());
    }

    // =====================================================
    // RESTORE
    // =====================================================

    public void restore() {
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity le : w.getLivingEntities()) {
                if (!mobs.isBoss(le)) continue;
                if (isFollowPlayerMob(le)) continue;

                bosses.putIfAbsent(le.getUniqueId(), le);
                dirty.add(le.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> updatePlayer(e.getPlayer()));
    }

    // =====================================================
    // TICK
    // =====================================================

    private void tick() {

        Iterator<Map.Entry<UUID, LivingEntity>> it = bosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = it.next();
            LivingEntity b = entry.getValue();

            if (b == null || !b.isValid() || b.isDead()) {
                UUID id = entry.getKey();
                it.remove();
                dirty.remove(id);
                phaseTitle.remove(id);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
    }

    // =====================================================
    // PLAYER UPDATE (ARENA-BASED)
    // =====================================================

    private void updatePlayer(Player p) {
        UUID pid = p.getUniqueId();
        UUID current = playerBoss.get(pid);

        LivingEntity activeBoss = null;

        for (LivingEntity boss : bosses.values()) {
            if (boss.getWorld() != p.getWorld()) continue;
            if (!isInsideArena(p, boss)) continue;

            activeBoss = boss;
            break;
        }

        // ❌ Kein Boss mehr → BossBar sauber entfernen
        if (activeBoss == null) {

            if (current != null) {
                PlayerBarCoordinator.Ctx ctx = playerBars.of(p);
                BossBar bar = ctx.bossBar();

                if (bar != null) {
                    bar.removePlayer(p);
                    ctx.setBossBar(null);
                }

                playerBoss.remove(pid);
            }

            return;
        }


        UUID bid = activeBoss.getUniqueId();

        if (current != null && current.equals(bid)) {
            if (dirty.contains(bid)) updateBar(p, activeBoss);
            return;
        }

        playerBoss.put(pid, bid);
        showBar(p, activeBoss);
    }

    private boolean isInsideArena(Player p, LivingEntity boss) {
        BossTemplate tpl = mobs.getBossTemplate(boss);
        if (tpl == null) return false;

        int chunks = Math.max(1, tpl.arenaRadiusChunks());
        double radius = chunks * BLOCKS_PER_CHUNK;
        double radiusSq = radius * radius;

        return p.getWorld() == boss.getWorld()
                && p.getLocation().distanceSquared(boss.getLocation()) <= (radiusSq + 4);
    }

    // =====================================================
    // BAR RENDERING
    // =====================================================

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

        bar.setTitle(Placeholder.resolve("{mob_name}", boss, null, p));
        dirty.remove(boss.getUniqueId());

    }

    private void removeBoss(UUID id) {
        bosses.remove(id);
        dirty.remove(id);
        phaseTitle.remove(id);
    }
    // BossBarService.java
    public double getBossProgress(LivingEntity boss) {
        if (boss == null) return -1;

        UUID id = boss.getUniqueId();
        if (!bosses.containsKey(id)) return -1;

        // BossBar wird pro Player gerendert,
        // Progress ist aber global identisch
        var attr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null || attr.getValue() <= 0) return -1;

        return Math.max(0.0, Math.min(1.0,
                boss.getHealth() / attr.getValue()
        ));
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private boolean isFollowPlayerMob(LivingEntity mob) {
        if (mobs.getAutoSpawnManager() == null) return false;

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
    public void removeBossCompletely(LivingEntity boss) {
        if (boss == null) return;

        UUID bid = boss.getUniqueId();

        // Boss aus Registry
        removeBoss(bid);

        // BossBar von ALLEN Spielern entfernen
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerBarCoordinator.Ctx ctx = playerBars.of(p);
            BossBar bar = ctx.bossBar();

            if (bar != null) {
                bar.removePlayer(p);
                ctx.setBossBar(null);
            }
        }

        // Player → Boss Mapping löschen
        playerBoss.values().removeIf(id -> id.equals(bid));
    }

}
