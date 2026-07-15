package com.mighty.hideandseek;

import me.libraryaddict.disguise.DisguiseAPI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory; // Fixed: Missing import added
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class HideAndSeek extends JavaPlugin {

    private final Set<UUID> hiders = new HashSet<>();
    private final Set<UUID> seekers = new HashSet<>();
    private final Set<UUID> lockedCamoHiders = new HashSet<>(); 
    
    private Scoreboard gameBoard;
    private Team hidersTeam;
    private Team seekersTeam;
    private Objective objective;
    
    private int timeLeft = 300; 
    private BukkitRunnable gameTimerTask;
    private boolean gameRunning = false;
    private Location startLocation;
    private Location cageLocation; 
    private boolean seekersReleased = false;
    private boolean lateGameBuffsApplied = false;

    @Override
    public void onEnable() {
        setupScoreboard();

        CamoCommand camoCommand = new CamoCommand(this);
        this.getCommand("camo").setExecutor(camoCommand);
        this.getCommand("camo").setTabCompleter(camoCommand);
        
        TeamCommand teamCommand = new TeamCommand(this);
        this.getCommand("hns").setExecutor(teamCommand);
        this.getCommand("hns").setTabCompleter(teamCommand);
        
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        
        getLogger().info("Animal Hide and Seek Final Version has been enabled!");
    }

    @Override
    public void onDisable() {
        stopGame();
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        gameBoard = manager.getNewScoreboard();

        hidersTeam = gameBoard.registerNewTeam("Hiders");
        hidersTeam.setPrefix("§a[Hider] ");
        hidersTeam.setColor(org.bukkit.ChatColor.GREEN);

        seekersTeam = gameBoard.registerNewTeam("Seekers");
        seekersTeam.setPrefix("§c[Seeker] ");
        seekersTeam.setColor(org.bukkit.ChatColor.RED);

        objective = gameBoard.registerNewObjective("hnsboard", Criteria.DUMMY, "§6§lMIGHTY H&S");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void updateScoreboardDisplay() {
        for (String entry : gameBoard.getEntries()) {
            gameBoard.resetScores(entry);
        }

        int minutes = timeLeft / 60;
        int seconds = timeLeft % 60;
        String formattedTime = String.format("%02d:%02d", minutes, seconds);

        objective.getScore("§7----------------").setScore(6);
        if (!seekersReleased) {
            objective.getScore("§eRelease In: §c" + (timeLeft - 270) + "s").setScore(5);
        } else {
            objective.getScore("§eTime Left: §b" + formattedTime).setScore(5);
        }
        objective.getScore("§f ").setScore(4);
        objective.getScore("§aHiders Alive: §f" + hiders.size()).setScore(3);
        objective.getScore("§cSeekers: §f" + seekers.size()).setScore(2);
        objective.getScore("§7---------------- ").setScore(1);
    }

    public void startGame(Location hostLocation) {
        if (gameRunning) return;
        gameRunning = true;
        timeLeft = 300; 
        seekersReleased = false;
        lateGameBuffsApplied = false;
        this.startLocation = hostLocation.clone();

        this.cageLocation = new Location(hostLocation.getWorld(), hostLocation.getX(), 250, hostLocation.getZ());
        buildSkyBox(cageLocation);

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;

        hiders.clear();
        seekers.clear();
        lockedCamoHiders.clear();
        hidersTeam.getEntries().forEach(hidersTeam::removeEntry);
        seekersTeam.getEntries().forEach(seekersTeam::removeEntry);

        Collections.shuffle(players);
        int seekerCount = (players.size() >= 8) ? 2 : 1;

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.setScoreboard(gameBoard);
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear(); 

            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, true, false, false));

            if (i < seekerCount) {
                seekers.add(p.getUniqueId());
                seekersTeam.addEntry(p.getName());
                p.setGlowing(true);
                
                p.teleport(cageLocation.clone().add(0, 1, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 600, 1, true, false, false));
                p.sendTitle("§c§lCRAFTING CAGE", "§7Hiders are running! 10s headstart.", 10, 40, 10);
                
                giveTrackerCompass(p);
            } else {
                hiders.add(p.getUniqueId());
                hidersTeam.addEntry(p.getName());
                p.setGlowing(false);
                p.teleport(startLocation);
                p.sendTitle("§a§lRUN AWAY!", "§eYou have 10 seconds to find a spot!", 10, 40, 10);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (gameRunning && hiders.contains(p.getUniqueId())) {
                            p.sendTitle("§6§lCHOOSE NOW!", "§7Select your disguise model!", 5, 20, 5);
                            openCamoSelectionGUI(p);
                            giveTauntClock(p);
                        }
                    }
                }.runTaskLater(this, 200L); 
            }
        }

        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    Bukkit.broadcastMessage("§a§l[!] Time is up! HIDERS WIN THE MATCH!");
                    stopGame();
                    cancel();
                    return;
                }
                
                if (hiders.isEmpty()) {
                    Bukkit.broadcastMessage("§c§lAll Hiders caught! SEEKERS WIN THE MATCH!");
                    stopGame();
                    cancel();
                    return;
                }

                if (!seekersReleased) {
                    for (UUID uuid : seekers) {
                        Player seeker = Bukkit.getPlayer(uuid);
                        if (seeker != null) {
                            Location current = seeker.getLocation();
                            if (current.getY() < 249) {
                                seeker.teleport(cageLocation.clone().add(0, 1, 0));
                            }
                        }
                    }
                }

                if (timeLeft == 270 && !seekersReleased) {
                    seekersReleased = true;
                    removeSkyBox(cageLocation); 
                    
                    for (UUID uuid : seekers) {
                        Player seeker = Bukkit.getPlayer(uuid);
                        if (seeker != null) {
                            seeker.removePotionEffect(PotionEffectType.BLINDNESS);
                            seeker.teleport(startLocation); 
                            seeker.sendTitle("§f ", "§c§lRELEASED! HUNT THEM DOWN!", 5, 30, 5);
                        }
                    }
                    Bukkit.broadcastMessage("§c§l[!] SEEKERS HAVE BEEN RELEASED FROM THE SKY BOX!");
                }

                if (timeLeft == 90 && !lateGameBuffsApplied) {
                    lateGameBuffsApplied = true;
                    Bukkit.broadcastMessage("§c§l[!] 90 SECONDS LEFT! SEEKERS HAVE BEEN BUFFED!");
                    
                    ItemStack knockbackStick = new ItemStack(Material.WOODEN_SWORD, 1);
                    ItemMeta meta = knockbackStick.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§c§lFINALE SWORD");
                        meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
                        knockbackStick.setItemMeta(meta);
                    }

                    for (UUID uuid : seekers) {
                        Player seeker = Bukkit.getPlayer(uuid);
                        if (seeker != null) {
                            seeker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1800, 0, true, false, false));
                            seeker.getInventory().addItem(knockbackStick);
                        }
                    }
                }

                timeLeft--;
                updateScoreboardDisplay();
            }
        };
        gameTimerTask.runTaskTimer(this, 0L, 20L);
    }

    private void buildSkyBox(Location loc) {
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = cy; y <= cy + 3; y++) {
                    if (x == cx - 2 || x == cx + 2 || z == cz - 2 || z == cz + 2 || y == cy || y == cy + 3) {
                        loc.getWorld().getBlockAt(x, y, z).setType(Material.BEDROCK);
                    } else {
                        loc.getWorld().getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }
    }

    private void removeSkyBox(Location loc) {
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = cy; y <= cy + 3; y++) {
                    loc.getWorld().getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    public void openCamoSelectionGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 18, "§6§lChoose Your Camo!");
        gui.setItem(0, createGuiItem(Material.PORKCHOP, "§aPig Camo", "§7Disguise as a Pig"));
        gui.setItem(1, createGuiItem(Material.BEEF, "§eCow Camo", "§7Disguise as a Cow"));
        gui.setItem(2, createGuiItem(Material.FEATHER, "§fChicken Camo", "§7Disguise as a Chicken"));
        gui.setItem(3, createGuiItem(Material.WHITE_WOOL, "§7Sheep Camo", "§7Disguise as a Sheep"));
        gui.setItem(4, createGuiItem(Material.BONE, "§7Wolf Camo", "§7Disguise as a Wolf"));
        gui.setItem(5, createGuiItem(Material.SWEET_BERRIES, "§6Fox Camo", "§7Disguise as a Fox"));
        gui.setItem(6, createGuiItem(Material.COD, "§6Cat Camo", "§7Disguise as a Cat"));
        gui.setItem(7, createGuiItem(Material.CARROT, "§aRabbit Camo", "§7Disguise as a Rabbit"));
        gui.setItem(8, createGuiItem(Material.ECHO_SHARD, "§8Bat Camo", "§7Disguise as a Bat"));
        gui.setItem(9, createGuiItem(Material.GUNPOWDER, "§2Creeper Camo", "§7Disguise as a Creeper"));
        gui.setItem(10, createGuiItem(Material.ROTTEN_FLESH, "§2Zombie Camo", "§7Disguise as a Zombie"));
        gui.setItem(11, createGuiItem(Material.ARROW, "§7Skeleton Camo", "§7Disguise as a Skeleton"));
        gui.setItem(12, createGuiItem(Material.SPIDER_EYE, "§5Spider Camo", "§7Disguise as a Spider"));
        gui.setItem(13, createGuiItem(Material.IRON_INGOT, "§fIron Golem Camo", "§7Disguise as an Iron Golem"));
        gui.setItem(14, createGuiItem(Material.EMERALD, "§aVillager Camo", "§7Disguise as a Biome Villager"));
        player.openInventory(gui);
    }

    private ItemStack createGuiItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void giveTauntClock(Player p) {
        ItemStack clock = new ItemStack(Material.CLOCK, 1);
        ItemMeta meta = clock.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lTAUNT MENU");
            meta.setLore(Arrays.asList("§7Right-click to open", "§7the noise picker GUI!"));
            clock.setItemMeta(meta);
        }
        p.getInventory().addItem(clock);
    }

    private void giveTrackerCompass(Player p) {
        ItemStack compass = new ItemStack(Material.COMPASS, 1);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lSEEKER TRACKER");
            meta.setLore(Arrays.asList("§7Right-click to point compass", "§7to the nearest active Hider!", "§845s Cooldown"));
            compass.setItemMeta(meta);
        }
        p.getInventory().addItem(compass);
    }

    public void stopGame() {
        gameRunning = false;
        if (gameTimerTask != null) {
            gameTimerTask.cancel();
        }
        if (cageLocation != null) {
            removeSkyBox(cageLocation);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.setGlowing(false);
            p.setGameMode(GameMode.SURVIVAL); 
            p.getInventory().clear(); 
            
            if (DisguiseAPI.isDisguised(p)) {
                DisguiseAPI.undisguiseToAll(p);
            }
            
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            p.removePotionEffect(PotionEffectType.SPEED);
            p.removePotionEffect(PotionEffectType.SATURATION); 
        }
        hiders.clear();
        seekers.clear();
        lockedCamoHiders.clear();
    }

    public boolean isGameRunning() { return gameRunning; }
    public int getTimeLeft() { return timeLeft; }
    public Set<UUID> getHiders() { return hiders; }
    public Set<UUID> getSeekers() { return seekers; }
    public Set<UUID> getLockedCamoHiders() { return lockedCamoHiders; }
    public Team getHidersTeam() { return hidersTeam; }
    public Team getSeekersTeam() { return seekersTeam; }
}
