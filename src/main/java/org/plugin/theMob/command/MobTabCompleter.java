package org.plugin.theMob.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
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

        // =========================
        // ROOT
        // =========================
        if (args.length == 1) {
            return filter(args[0],
                    "spawn", "set", "list", "del", "killall",
                    "reload", "toggle", "diag", "debug"
            );
        }

        // =========================
        // DIAG
        // =========================
        if (args.length == 2 && eq(args, 0, "diag")) {
            return filter(args[1],
                    "status", "tps", "budgets", "throttle", "alive", "cooldown"
            );
        }

        // =========================
        // TOGGLE
        // =========================
        if (args.length == 2 && eq(args, 0, "toggle")) {
            return filter(args[1], "hud");
        }

        // =========================
        // SPAWN
        // =========================
        if (args.length == 2 && eq(args, 0, "spawn")) {
            return mobIds(args[1]);
        }

        // =========================
        // SET
        // =========================
        if (args.length == 2 && eq(args, 0, "set")) {
            return filter(args[1],
                    "autospawn", "followplayer", "randomradius", "randomworld"
            );
        }

        if (isSet(args, "autospawn")) {
            if (args.length == 3) return mobIds(args[2]);
            if (args.length == 4) return numbers(args[3], "10", "30", "60", "120");
            if (args.length == 5) return numbers(args[4], "1", "3", "5", "10");
        }

        if (isSet(args, "randomradius")) {
            if (args.length == 3) return mobIds(args[2]);
            if (args.length == 4) return numbers(args[3], "30", "60", "120");
            if (args.length == 5) return numbers(args[4], "1", "3", "5");
            if (args.length == 6) return numbers(args[5], "8", "16", "24");
            if (args.length == 7) return numbers(args[6], "24", "32", "48");
        }

        if (isSet(args, "followplayer")) {
            if (args.length == 3) return onlinePlayers(args[2]);
            if (args.length == 4) return mobIds(args[3]);
            if (args.length == 5) return numbers(args[4], "10", "20", "30");
            if (args.length == 6) return numbers(args[5], "1", "3", "5");
            if (args.length == 7) return filter(args[6], "onetime", "endless");
            if (args.length == 8) return numbers(args[7], "6", "10", "16");
            if (args.length == 9) return numbers(args[8], "16", "24", "32");
        }

        if (isSet(args, "randomworld")) {
            if (args.length == 3) return mobIds(args[2]);
            if (args.length == 4) return numbers(args[3], "60", "120", "300");
            if (args.length == 5) return numbers(args[4], "1", "3", "5");
            if (args.length == 6) return filter(args[5], "onetime", "endless");
            if (args.length == 7) return numbers(args[6], "30", "60", "120");
        }

        // =========================
        // LIST
        // =========================
        if (args.length == 2 && eq(args, 0, "list")) {
            return filter(args[1], "all", "autospawn");
        }

        // =========================
        // DEL
        // =========================
        if (args.length == 2 && eq(args, 0, "del")) {
            return filter(args[1],
                    "autospawn", "followplayer", "randomradius", "randomworld"
            );
        }

        if (isDel(args, "autospawn") && args.length == 3) {
            return mobIds(args[2]);
        }

        if (isDel(args, "followplayer")) {
            if (args.length == 3) return onlinePlayers(args[2]);
            if (args.length == 4) return mobIds(args[3]);
        }

        if (isDel(args, "randomworld")) {
            if (args.length == 3) {
                return Bukkit.getWorlds().stream().map(w -> w.getName()).toList();
            }
            if (args.length == 4) return mobIds(args[3]);
        }

        return List.of();
    }

    // =================================================
    // Helpers
    // =================================================
    private boolean eq(String[] args, int i, String v) {
        return args.length > i && args[i].equalsIgnoreCase(v);
    }

    private boolean isSet(String[] args, String sub) {
        return args.length > 1 && eq(args, 0, "set") && eq(args, 1, sub);
    }

    private boolean isDel(String[] args, String sub) {
        return args.length > 1 && eq(args, 0, "del") && eq(args, 1, sub);
    }

    private List<String> mobIds(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase();
        return mobs.registeredIds().stream()
                .filter(id -> id.startsWith(p))
                .sorted()
                .toList();
    }

    private List<String> onlinePlayers(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().toLowerCase().startsWith(p)) {
                out.add(pl.getName());
            }
        }
        return out;
    }

    private List<String> numbers(String prefix, String... values) {
        String p = prefix == null ? "" : prefix;
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v.startsWith(p)) out.add(v);
        }
        return out;
    }

    private List<String> filter(String prefix, String... values) {
        String p = prefix == null ? "" : prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v.startsWith(p)) out.add(v);
        }
        return out;
    }
}
