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
    public boolean onCommand(
            CommandSender sender,
            Command cmd,
            String label,
            String[] args
    ) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
// /mob reload
        if (args[0].equalsIgnoreCase("reload")) {
            perm(sender, "themob.reload");
            plugin.reloadPlugin();
            sender.sendMessage("§a[TheMob] Reloaded.");
            return true;
        }
// /mob spawn <id>
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
                    p.getLocation().add(p.getLocation().getDirection().multiply(2))
            );
            p.sendMessage("§aSpawned mob: §e" + id);
            return true;
        }
// /mob autospawn <id> <seconds> <amount>
        if (args[0].equalsIgnoreCase("autospawn")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage("§e/mob autospawn <id> <seconds> <amount>");
                return true;
            }
            String id = args[1].toLowerCase();
            int seconds = Integer.parseInt(args[2]);
            int amount = Integer.parseInt(args[3]);
            if (!mobs.mobExists(id)) {
                sender.sendMessage("§cUnknown mob: " + id);
                return true;
            }
            if (seconds <= 0 || amount <= 0) {
                sender.sendMessage("§cSeconds and amount must be > 0");
                return true;
            }
            spawns.startAutoSpawnAt(
                    id,
                    p.getLocation(),
                    seconds,
                    amount
            );
            sender.sendMessage(
                    "§aAuto-spawn started for §e" + id +
                            " §7(" + amount + " mobs, every " + seconds + "s)"
            );
            return true;
        }
// /mob del autospawn <id>
        if (args[0].equalsIgnoreCase("del")
                && args.length >= 3
                && args[1].equalsIgnoreCase("autospawn")) {
            String id = args[2].toLowerCase();
            if (!sender.hasPermission("themob.spawn.set")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            boolean ok = spawns.deleteAutoSpawn(id);
            sender.sendMessage(ok
                    ? "§aAuto-spawn removed for §e" + id
                    : "§cNo auto-spawn found for §e" + id
            );
            return true;
        }
// /mob killall
        if (args[0].equalsIgnoreCase("killall")) {
            if (!sender.hasPermission("themob.killall")) {
                sender.sendMessage("§cYou do not have permission.");
                return true;
            }
            mobs.killAll();
            sender.sendMessage("§aAll custom mobs have been removed.");
            return true;
        }
        help(sender);
        return true;
    }
// UTIL
    private void help(CommandSender s) {
        s.sendMessage("§e/mob spawn <id>");
        s.sendMessage("§e/mob autospawn <id> <seconds> <amount>");
        s.sendMessage("§e/mob killall");
        s.sendMessage("§e/mob reload");
    }
    private void perm(CommandSender s, String perm) {
        if (!s.hasPermission(perm)) {
            throw new RuntimeException("Missing permission: " + perm);
        }
    }
}
