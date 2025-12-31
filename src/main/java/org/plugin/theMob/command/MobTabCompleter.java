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

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of(
                            "spawn",
                            "autospawn",
                            "list",
                            "del",
                            "killall",
                            "reload",
                            "toggle"
                    ).stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return List.of("hud");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            String prefix = args[1].toLowerCase();
            return mobs.registeredIds().stream()
                    .filter(id -> id.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("autospawn")) {
            String prefix = args[1].toLowerCase();
            return mobs.registeredIds().stream()
                    .filter(id -> id.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return List.of("autospawn");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("autospawn")) {
            return List.of("10", "60", "300", "600");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("autospawn")) {
            return List.of("1", "3", "5", "10");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            return List.of("autospawn");
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("del")
                && args[1].equalsIgnoreCase("autospawn")) {

            String prefix = args[2].toLowerCase();
            return mobs.registeredIds().stream()
                    .filter(id -> id.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        return new ArrayList<>();
    }
}
