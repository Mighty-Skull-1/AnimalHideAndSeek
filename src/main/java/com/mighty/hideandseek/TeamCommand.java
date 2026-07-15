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
            sender.sendMessage("§cUsage: /hns <start|stop|clear|hider|seeker>");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("start")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can execute start in game to map positions!");
                return true;
            }
            Player host = (Player) sender;
            plugin.startGame(host.getLocation());
            Bukkit.broadcastMessage("§6[H&S] The Hide and Seek match has started!");
            return true;
        }

        if (sub.equals("stop")) {
            plugin.stopGame();
            Bukkit.broadcastMessage("§6[H&S] The Hide and Seek match was stopped.");
            return true;
        }

        if (sub.equals("clear")) {
            plugin.stopGame();
            Bukkit.broadcastMessage("§a[H&S] Teams cleared.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /hns " + sub + " <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return true;
        }

        plugin.getHiders().remove(target.getUniqueId());
        plugin.getSeekers().remove(target.getUniqueId());
        plugin.getHidersTeam().removeEntry(target.getName());
        plugin.getSeekersTeam().removeEntry(target.getName());

        if (sub.equals("hider")) {
            plugin.getHiders().add(target.getUniqueId());
            plugin.getHidersTeam().addEntry(target.getName());
            target.sendMessage("§aYou have been put on the HIDERS team!");
        } else if (sub.equals("seeker")) {
            plugin.getSeekers().add(target.getUniqueId());
            plugin.getSeekersTeam().addEntry(target.getName());
            target.setGlowing(true);
            target.sendMessage("§cYou have been put on the SEEKERS team!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("start", "stop", "clear", "hider", "seeker"), new ArrayList<>());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("hider") || args[0].equalsIgnoreCase("seeker"))) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>());
        }
        return new ArrayList<>();
    }
}
