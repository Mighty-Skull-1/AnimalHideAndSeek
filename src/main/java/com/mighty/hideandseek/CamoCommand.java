package com.mighty.hideandseek;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CamoCommand implements CommandExecutor, TabCompleter {

    private final HideAndSeek plugin;

    public CamoCommand(HideAndSeek plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        // BLOCK CAMO CHANGE: Forces them to stick to the starting GUI choice during a match
        if (plugin.isGameRunning()) {
            player.sendMessage("§cYou cannot change your disguise during an active match!");
            return true;
        }

        player.sendMessage("§cYou can only choose your camo when the game starts!");
        return true;
    }

    public static void clearMobPhysics(Player player) {
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>(); // Disable completions since command is locked during games
    }
}
