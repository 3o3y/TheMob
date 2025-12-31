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
                    null,
                    p.getLocation().add(
                            p.getLocation().getDirection().normalize().multiply(2)
                    )
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

            boolean ok = spawns.startAutoSpawn(
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

        if (args[0].equalsIgnoreCase("list")
                && args.length >= 2
                && args[1].equalsIgnoreCase("autospawn")) {

            if (!sender.hasPermission("themob.spawn.set")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

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
        s.sendMessage("§e/mob list autospawn");
        s.sendMessage("§e/mob del autospawn <mob-id>");
        s.sendMessage("§e/mob killall");
        s.sendMessage("§e/mob reload");
        s.sendMessage("§e/mob toggle hud");

    }
}
