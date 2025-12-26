package org.plugin.theMob.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnController;

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
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("themob.reload")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage("§a[TheMob] Reloaded.");
            return true;
        }
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
                    "manual@" + p.getUniqueId(),
                    p.getLocation().add(p.getLocation().getDirection().multiply(2))
            );

            p.sendMessage("§aSpawned mob: §e" + id);
            return true;
        }
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
                sender.sendMessage("§e/mob autospawn <id> <seconds> <maxSpawns>");
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

            if (seconds <= 0 || maxSpawns <= 0) {
                sender.sendMessage("§cSeconds and maxSpawns must be > 0.");
                return true;
            }

            boolean ok = spawns.startAutoSpawn(
                    id,
                    p.getLocation(),
                    seconds,
                    maxSpawns
            );

            sender.sendMessage(ok
                    ? "§aAuto-spawn created for §e" + id +
                    " §7(1 mob every " + seconds +
                    "s, max " + maxSpawns + " per cycle)"
                    : "§cFailed to create auto-spawn."
            );
            return true;
        }
        if (args[0].equalsIgnoreCase("del")
                && args.length >= 3
                && args[1].equalsIgnoreCase("autospawn")) {

            if (!sender.hasPermission("themob.spawn.set")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            String id = args[2].toLowerCase();
            boolean ok = spawns.deleteAutoSpawnByMobId(id);

            sender.sendMessage(ok
                    ? "§aAll auto-spawns removed for §e" + id
                    : "§cNo auto-spawn found for §e" + id
            );
            return true;
        }
        if (args[0].equalsIgnoreCase("killall")) {
            if (!sender.hasPermission("themob.killall")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            mobs.killAll();
            sender.sendMessage("§aAll custom mobs have been removed.");
            return true;
        }

        help(sender);
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§e/mob spawn <mob-id>");
        s.sendMessage("§e/mob autospawn <mob-id> <seconds> <maxSpawns>");
        s.sendMessage("§e/mob del autospawn <mob-id>");
        s.sendMessage("§e/mob killall");
        s.sendMessage("§e/mob reload");
    }
}
