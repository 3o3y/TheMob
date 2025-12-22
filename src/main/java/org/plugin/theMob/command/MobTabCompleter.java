package org.plugin.theMob.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.plugin.theMob.mob.MobManager;

import java.util.ArrayList;
import java.util.List;

public final class MobTabCompleter implements TabCompleter {

    private final MobManager mobs;
    public MobTabCompleter(MobManager mobs) {
        this.mobs = mobs;
    }
    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.add("spawn");
            list.add("autospawn");
            list.add("del");
            list.add("killall");
            list.add("reload");
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            list.addAll(mobs.registeredIds());
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("autospawn")) {
            list.addAll(mobs.registeredIds());
            return list;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("autospawn")) {
            list.add("10");
            list.add("60");
            list.add("300");
            list.add("600");
            return list;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("autospawn")) {
            list.add("1");
            list.add("3");
            list.add("5");
            list.add("10");
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            list.add("autospawn");
            return list;
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("del")
                && args[1].equalsIgnoreCase("autospawn")) {
            list.addAll(mobs.registeredIds());
            return list;
        }
        return list;
    }
}
