package com.mighty.hideandseek;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final HideAndSeek plugin;

    public TeamCommand(HideAndSeek plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hideandseek.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /hns <start|test|stop|clear|hider|seeker>");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("start")) {
            if (!(sender instanceof Player)) return true;
            plugin.startGame(((Player) sender).getLocation(), false);
            Bukkit.broadcastMessage("§6[H&S] Match launched successfully!");
            return true;
        }

        // FEATURE: Solitary Test framework activation hook
        if (sub.equals("test")) {
            if (!(sender instanceof Player)) return true;
            plugin.startGame(((Player) sender).getLocation(), true);
            sender.sendMessage("§a[H&S] Launched Solo Test Mode! Morphing active in 10 seconds.");
            return true;
        }

        if (sub.equals("stop") || sub.equals("clear")) {
            plugin.stopGame();
            Bukkit.broadcastMessage("§6[H&S] Match terminated and variables cleared.");
            return true;
        }

        if (args.length < 2) return true;
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) return true;

        plugin.getHiders().remove(target.getUniqueId());
        plugin.getSeekers().remove(target.getUniqueId());
        plugin.getHidersTeam().removeEntry(target.getName());
        plugin.getSeekersTeam().removeEntry(target.getName());

        if (sub.equals("hider")) {
            plugin.getHiders().add(target.getUniqueId());
            plugin.getHidersTeam().addEntry(target.getName());
        } else if (sub.equals("seeker")) {
            plugin.getSeekers().add(target.getUniqueId());
            plugin.getSeekersTeam().addEntry(target.getName());
            target.setGlowing(true);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("start", "test", "stop", "clear", "hider", "seeker"), new ArrayList<>());
        }
        return new ArrayList<>();
    }
}
