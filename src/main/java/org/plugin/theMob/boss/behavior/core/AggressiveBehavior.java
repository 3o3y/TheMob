package org.plugin.theMob.boss.behavior.core;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AggressiveBehavior implements BossBehavior {

    // =========================
    // TUNING
    // =========================
    private static final double AGGRO_RADIUS = 18.0;
    private static final double MOVE_SPEED = 0.32;
    private static final double BASE_Y = 0.08;
    private static final double JUMP_Y = 0.48;

    private static final int STUCK_TICKS = 6;
    private static final double MIN_PROGRESS_SQ = 0.015;

    private static final int LOCAL_TP_RADIUS = 2;
    private static final int TP_ATTEMPTS = 20;

    private static final double DASH_DISTANCE = 3.2;
    private static final double DASH_FORCE = 0.65;

    private static final String AGGRO_TEAM = "theMob_aggressive";

    // =========================
    // STATE
    // =========================
    private final Map<UUID, Integer> stuckTicks = new HashMap<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Map<UUID, Double> originalAttackSpeed = new HashMap<>();
    private final Map<UUID, Boolean> glowing = new HashMap<>();

    @Override
    public String id() {
        return "aggressive";
    }

    // =====================================================
    // ENTER (EINMAL PRO PHASE)
    // =====================================================
    @Override
    public void onEnter(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        UUID id = boss.getUniqueId();

        mob.setAI(true);
        mob.setAware(true);
        mob.setSilent(false);

        applyRedGlow(boss);
        boostAttackSpeed(boss);

        stuckTicks.put(id, 0);
        lastPos.put(id, boss.getLocation().clone());
        glowing.put(id, true);
    }

    // =====================================================
    // EXIT (NUR BEI PHASENWECHSEL)
    // =====================================================
    @Override
    public void onExit(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        UUID id = boss.getUniqueId();

        mob.setTarget(null);

        removeRedGlow(boss);
        restoreAttackSpeed(boss);

        stuckTicks.remove(id);
        lastPos.remove(id);
        glowing.remove(id);
    }

    // =====================================================
    // TICK (NIEMALS GLOW ANFASSEN)
    // =====================================================
    @Override
    public void tick(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        UUID id = boss.getUniqueId();

        Player target = findNearestPlayer(boss);
        if (target == null) return;

        mob.setTarget(target);

        Location loc = boss.getLocation();
        Vector dir = target.getLocation().toVector().subtract(loc.toVector());
        double distSq = dir.lengthSquared();
        if (distSq < 0.0001) return;

        dir.normalize();

        Vector velocity = dir.multiply(MOVE_SPEED);
        velocity.setY(
                shouldStepUp(loc, dir, resolveStepHeight(boss))
                        ? JUMP_Y
                        : BASE_Y
        );

        boss.setVelocity(velocity);
        boss.setRotation(yawFrom(dir), loc.getPitch());

        if (distSq <= DASH_DISTANCE * DASH_DISTANCE) {
            Vector dash = dir.multiply(DASH_FORCE);
            dash.setY(0.15);
            boss.setVelocity(dash);
        }

        Location last = lastPos.get(id);
        int stuck = stuckTicks.getOrDefault(id, 0);

        if (last != null && loc.distanceSquared(last) < MIN_PROGRESS_SQ) stuck++;
        else stuck = 0;

        stuckTicks.put(id, stuck);
        lastPos.put(id, loc.clone());

        if (stuck >= STUCK_TICKS) {
            Location safe = findLocalSafeSpot(boss);
            if (safe != null) boss.teleport(safe);
            stuckTicks.put(id, 0);
        }
    }

    // =====================================================
    // GLOW
    // =====================================================
    private void applyRedGlow(LivingEntity boss) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(AGGRO_TEAM);

        if (team == null) {
            team = sb.registerNewTeam(AGGRO_TEAM);
            team.color(NamedTextColor.RED);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }

        String entry = boss.getUniqueId().toString(); // ✅ FIX
        team.addEntry(entry);

        boss.setGlowing(true);
    }


    private void removeRedGlow(LivingEntity boss) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(AGGRO_TEAM);

        if (team != null) {
            team.removeEntry(boss.getUniqueId().toString()); // ✅ FIX
        }

        boss.setGlowing(false);
    }


    // =====================================================
    // COMBAT
    // =====================================================
    private void boostAttackSpeed(LivingEntity boss) {
        AttributeInstance attr = boss.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;

        originalAttackSpeed.putIfAbsent(boss.getUniqueId(), attr.getBaseValue());
        attr.setBaseValue(originalAttackSpeed.get(boss.getUniqueId()) * 2.0);
    }

    private void restoreAttackSpeed(LivingEntity boss) {
        AttributeInstance attr = boss.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;

        Double original = originalAttackSpeed.remove(boss.getUniqueId());
        if (original != null) attr.setBaseValue(original);
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private Player findNearestPlayer(LivingEntity boss) {
        double best = AGGRO_RADIUS * AGGRO_RADIUS;
        Player nearest = null;

        for (Player p : boss.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private boolean shouldStepUp(Location from, Vector dir, int maxStep) {
        for (int y = 1; y <= maxStep; y++) {
            Location base = from.clone().add(dir).add(0, y, 0);
            if (base.clone().add(0, -1, 0).getBlock().getType().isSolid()
                    && base.getBlock().getType().isAir()
                    && base.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private Location findLocalSafeSpot(LivingEntity boss) {
        Location center = boss.getLocation();
        for (int i = 0; i < TP_ATTEMPTS; i++) {
            int dx = (int) (Math.random() * (LOCAL_TP_RADIUS * 2 + 1)) - LOCAL_TP_RADIUS;
            int dy = (int) (Math.random() * (LOCAL_TP_RADIUS * 2 + 1)) - LOCAL_TP_RADIUS;
            int dz = (int) (Math.random() * (LOCAL_TP_RADIUS * 2 + 1)) - LOCAL_TP_RADIUS;

            Location l = center.clone().add(dx, dy, dz);
            if (l.clone().add(0, -1, 0).getBlock().getType().isSolid()
                    && l.getBlock().getType().isAir()
                    && l.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return l;
            }
        }
        return null;
    }

    private int resolveStepHeight(LivingEntity boss) {
        AttributeInstance scale = boss.getAttribute(Attribute.SCALE);
        return scale != null && scale.getValue() > 1.3 ? 2 : 1;
    }

    private float yawFrom(Vector v) {
        return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }
}
