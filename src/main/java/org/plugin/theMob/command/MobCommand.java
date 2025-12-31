package org.plugin.theMob.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnController;
import org.plugin.theMob.spawn.type.SpawnMode;

import java.util.Arrays;

public final class MobCommand implements CommandExecutor {

    private final TheMob plugin;
    private final MobManager mobs;
    private final SpawnController spawns;

    public MobCommand(TheMob plugin, MobManager mobs, SpawnController spawns) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.spawns = spawns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            help(sender);
            return true;
        }

        // -------------------------------------------------
        // /mob toggle hud
        // -------------------------------------------------
        if (args[0].equalsIgnoreCase("toggle")
                && args.length >= 2
                && args[1].equalsIgnoreCase("hud")) {

            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players can toggle HUD.");
                return true;
            }

            boolean enabled = org.plugin.theMob.hud.PlayerHudState.toggle(p.getUniqueId());

            p.sendMessage(enabled
                    ? "§aNavigation HUD enabled."
                    : "§cNavigation HUD disabled.");

            return true;
        }

        // -------------------------------------------------
        // /mob reload
        // -------------------------------------------------
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("themob.reload")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage("§a[TheMob] Reloaded.");
            return true;
        }

        // -------------------------------------------------
        // /mob spawn <mob-id>
        // -------------------------------------------------
        if (args[0].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players can spawn mobs.");
                return true;
            }

            if (args.length < 2) {
                p.sendMessage("§e/mob spawn <mob-id>");
                return true;
            }

            String id = args[1].toLowerCase();
            if (!mobs.mobExists(id)) {
                p.sendMessage("§cUnknown mob: §e" + id);
                return true;
            }

            mobs.spawnCustomMob(
                    id,
                    null,
                    p.getLocation().add(
                            p.getLocation().getDirection().normalize().multiply(2)
                    )
            );

            p.sendMessage("§aSpawned mob: §e" + id);
            return true;
        }

        // -------------------------------------------------
        // LEGACY: /mob autospawn <id> <seconds> <maxSpawns>
        // alias to: /mob set autospawn ...
        // -------------------------------------------------
        if (args[0].equalsIgnoreCase("autospawn")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }

            if (!sender.hasPermission("themob.spawn.set")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length < 4) {
                sender.sendMessage("§e/mob autospawn <mob-id> <seconds> <maxSpawns>");
                return true;
            }

            String id = args[1].toLowerCase();
            if (!mobs.mobExists(id)) {
                sender.sendMessage("§cUnknown mob: §e" + id);
                return true;
            }

            int seconds;
            int maxSpawns;

            try {
                seconds = Integer.parseInt(args[2]);
                maxSpawns = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cSeconds and maxSpawns must be numbers.");
                return true;
            }

            boolean ok = spawns.setAutoSpawnFixedPoint(
                    id,
                    p.getLocation(),
                    seconds,
                    maxSpawns
            );

            sender.sendMessage(ok
                    ? "§aAuto-spawn created for §e" + id
                    : "§cFailed to create auto-spawn."
            );
            return true;
        }

        // -------------------------------------------------
        // /mob set ...
        // -------------------------------------------------
        if (args[0].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }

            if (!sender.hasPermission("themob.spawn.set")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length < 2) {
                setHelp(sender);
                return true;
            }

            String sub = args[1].toLowerCase();

            if (sub.equals("autospawn")) {
                if (args.length < 5) {
                    sender.sendMessage("§e/mob set autospawn <mob-id> <seconds> <maxSpawns>");
                    return true;
                }
                String mobId = args[2].toLowerCase();
                if (!mobs.mobExists(mobId)) {
                    sender.sendMessage("§cUnknown mob: §e" + mobId);
                    return true;
                }
                int seconds, maxSpawns;
                try {
                    seconds = Integer.parseInt(args[3]);
                    maxSpawns = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cSeconds and maxSpawns must be numbers.");
                    return true;
                }

                boolean ok = spawns.setAutoSpawnFixedPoint(mobId, p.getLocation(), seconds, maxSpawns);
                sender.sendMessage(ok ? "§aFIXED_POINT spawn saved." : "§cFailed.");
                return true;
            }

            if (sub.equals("randomradius")) {
                if (args.length < 7) {
                    sender.sendMessage("§e/mob set randomradius <mob-id> <seconds> <maxSpawns> <minradius> <maxradius>");
                    return true;
                }
                String mobId = args[2].toLowerCase();
                if (!mobs.mobExists(mobId)) {
                    sender.sendMessage("§cUnknown mob: §e" + mobId);
                    return true;
                }
                int seconds, maxSpawns, minR, maxR;
                try {
                    seconds = Integer.parseInt(args[3]);
                    maxSpawns = Integer.parseInt(args[4]);
                    minR = Integer.parseInt(args[5]);
                    maxR = Integer.parseInt(args[6]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNumbers required.");
                    return true;
                }

                boolean ok = spawns.setRandomRadius(mobId, p.getLocation(), seconds, maxSpawns, minR, maxR);
                sender.sendMessage(ok ? "§aRANDOM_RADIUS spawn saved." : "§cFailed.");
                return true;
            }

            if (sub.equals("followplayer")) {
                if (args.length < 10) {
                    sender.sendMessage("§e/mob set followplayer <player> <mob-id> <seconds> <maxSpawns> <onetime/endless> <mindistance> <maxdistance> <message>");
                    return true;
                }

                String playerName = args[2];
                String mobId = args[3].toLowerCase();
                if (!mobs.mobExists(mobId)) {
                    sender.sendMessage("§cUnknown mob: §e" + mobId);
                    return true;
                }

                int seconds, maxSpawns, minD, maxD;
                SpawnMode mode = SpawnMode.fromString(args[6]);

                try {
                    seconds = Integer.parseInt(args[4]);
                    maxSpawns = Integer.parseInt(args[5]);
                    minD = Integer.parseInt(args[7]);
                    maxD = Integer.parseInt(args[8]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNumbers required.");
                    return true;
                }

                String message = joinFrom(args, 9);

                boolean ok = spawns.setFollowPlayer(
                        playerName,
                        mobId,
                        seconds,
                        maxSpawns,
                        mode,
                        minD,
                        maxD,
                        message
                );

                sender.sendMessage(ok ? "§aFOLLOW_PLAYER spawn saved." : "§cFailed.");
                return true;
            }

            if (sub.equals("randomworld")) {
                if (args.length < 8) {
                    sender.sendMessage("§e/mob set randomworld <mob-id> <seconds> <maxSpawns> <onetime/endless> <message-timer> <message>");
                    sender.sendMessage("§7World is taken from your current world.");
                    return true;
                }

                String mobId = args[2].toLowerCase();
                if (!mobs.mobExists(mobId)) {
                    sender.sendMessage("§cUnknown mob: §e" + mobId);
                    return true;
                }

                int seconds, maxSpawns, msgTimer;
                SpawnMode mode = SpawnMode.fromString(args[5]);
                try {
                    seconds = Integer.parseInt(args[3]);
                    maxSpawns = Integer.parseInt(args[4]);
                    msgTimer = Integer.parseInt(args[6]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNumbers required.");
                    return true;
                }

                String message = joinFrom(args, 7);
                World w = p.getWorld();

                boolean ok = spawns.setRandomWorld(
                        w.getName(),
                        mobId,
                        seconds,
                        maxSpawns,
                        mode,
                        msgTimer,
                        message
                );

                sender.sendMessage(ok ? "§aRANDOM_WORLD spawn saved." : "§cFailed.");
                return true;
            }

            setHelp(sender);
            return true;
        }

        // -------------------------------------------------
        // /mob list ...
        // -------------------------------------------------
        if (args[0].equalsIgnoreCase("list")) {

            if (!sender.hasPermission("themob.spawn.set")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length >= 2 && args[1].equalsIgnoreCase("autospawn")) {
                var list = spawns.listAutoSpawns();
                if (list.isEmpty()) {
                    sender.sendMessage("§7No auto-spawns configured.");
                    return true;
                }

                sender.sendMessage("§6§lAuto-Spawns:");
                for (var s : list) {
                    sender.sendMessage(
                            "§e- " + s.mobId() +
                                    " §7" + s.world() +
                                    " §f" + s.x() + ", " + s.y() + ", " + s.z()
                    );
                }
                return true;
            }

            if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                var lines = spawns.listAllLines();
                if (lines.isEmpty()) {
                    sender.sendMessage("§7No spawns configured.");
                    return true;
                }
                sender.sendMessage("§6§lAll Spawns:");
                for (String line : lines) {
                    sender.sendMessage("§e- §f" + line);
                }
                return true;
            }

            sender.sendMessage("§e/mob list autospawn");
            sender.sendMessage("§e/mob list all");
            return true;
        }

        // -------------------------------------------------
        // /mob del ...
        // -------------------------------------------------
        if (args[0].equalsIgnoreCase("del")) {

            if (!sender.hasPermission("themob.spawn.set")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length < 2) {
                delHelp(sender);
                return true;
            }

            String sub = args[1].toLowerCase();

            if (sub.equals("autospawn")) {
                if (args.length < 3) {
                    sender.sendMessage("§e/mob del autospawn <mob-id>");
                    return true;
                }
                String mobId = args[2].toLowerCase();
                boolean ok = spawns.deleteAutoSpawnByMobId(mobId);

                sender.sendMessage(ok
                        ? "§aAll FIXED_POINT auto-spawns removed for §e" + mobId
                        : "§cNo FIXED_POINT auto-spawn found for §e" + mobId
                );
                return true;
            }

            if (sub.equals("followplayer")) {
                if (args.length < 4) {
                    sender.sendMessage("§e/mob del followplayer <player> <mob-id>");
                    return true;
                }
                boolean ok = spawns.deleteFollowPlayer(args[2], args[3]);
                sender.sendMessage(ok ? "§aFOLLOW_PLAYER removed." : "§cNot found.");
                return true;
            }

            if (sub.equals("randomworld")) {
                if (args.length < 4) {
                    sender.sendMessage("§e/mob del randomworld <world> <mob-id>");
                    return true;
                }
                boolean ok = spawns.deleteRandomWorld(args[2], args[3]);
                sender.sendMessage(ok ? "§aRANDOM_WORLD removed." : "§cNot found.");
                return true;
            }

            if (sub.equals("randomradius")) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cOnly players can use this command.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§e/mob del randomradius <mob-id>");
                    sender.sendMessage("§7Deletes the RANDOM_RADIUS spawn at your current block position.");
                    return true;
                }
                boolean ok = spawns.deleteRandomRadiusAt(args[2], p.getLocation());
                sender.sendMessage(ok ? "§aRANDOM_RADIUS removed." : "§cNot found at your position.");
                return true;
            }

            delHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("killall")) {
            if (!sender.hasPermission("themob.killall")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            mobs.killAll();
            spawns.getAutoSpawnManager().onKillAll(); // 🔥 WICHTIG

            sender.sendMessage("§aAll custom mobs have been removed.");
            return true;
        }


        help(sender);
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§e/mob spawn <mob-id>");
        s.sendMessage("§e/mob killall");
        s.sendMessage("§e/mob set autospawn <mob-id> <seconds> <maxSpawns>");
        s.sendMessage("§e/mob set followplayer <player> <mob-id> <seconds> <maxSpawns> <onetime/endless> <mindistance> <maxdistance> <message>");
        s.sendMessage("§e/mob set randomradius <mob-id> <seconds> <maxSpawns> <minradius> <maxradius>");
        s.sendMessage("§e/mob set randomworld <mob-id> <seconds> <maxSpawns> <onetime/endless> <message-timer> <message>");
        s.sendMessage("§7Placeholders: {world} {x} {y} {z}");
        s.sendMessage("§e/mob del autospawn <mob-id>");
        s.sendMessage("§e/mob del followplayer <player> <mob-id>");
        s.sendMessage("§e/mob del randomradius <mob-id>  §7(uses your position)");
        s.sendMessage("§e/mob del randomworld <world> <mob-id>");
        s.sendMessage("§e/mob list autospawn");
        s.sendMessage("§e/mob list all");
        s.sendMessage("§e/mob reload");
        s.sendMessage("§e/mob toggle hud");
        s.sendMessage("§7Legacy alias: /mob autospawn <mob-id> <seconds> <maxSpawns>");
    }

    private void setHelp(CommandSender s) {
        s.sendMessage("§e/mob set autospawn <mob-id> <seconds> <maxSpawns>");
        s.sendMessage("§e/mob set followplayer <player> <mob-id> <seconds> <maxSpawns> <onetime/endless> <mindistance> <maxdistance> <message>");
        s.sendMessage("§e/mob set randomradius <mob-id> <seconds> <maxSpawns> <minradius> <maxradius>");
        s.sendMessage("§e/mob set randomworld <mob-id> <seconds> <maxSpawns> <onetime/endless> <message-timer> <message>");
    }

    private void delHelp(CommandSender s) {
        s.sendMessage("§e/mob del autospawn <mob-id>");
        s.sendMessage("§e/mob del followplayer <player> <mob-id>");
        s.sendMessage("§e/mob del randomradius <mob-id>  §7(uses your position)");
        s.sendMessage("§e/mob del randomworld <world> <mob-id>");
    }

    private String joinFrom(String[] args, int start) {
        if (start >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }
}
