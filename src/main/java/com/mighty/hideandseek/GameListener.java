package com.mighty.hideandseek;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.VillagerWatcher;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {

    private final HideAndSeek plugin;
    private final String tauntGuiTitle = "§6§lSelect a Taunt!";
    private final String camoGuiTitle = "§6§lChoose Your Camo!";
    private final Map<UUID, Long> compassCooldown = new HashMap<>();
    private final Map<UUID, Long> tauntCooldown = new HashMap<>();
    private final Map<UUID, BukkitRunnable> sneakTimerTasks = new HashMap<>();

    public GameListener(HideAndSeek plugin) {
        this.plugin = plugin;
    }

    // FEATURE: Enforce Lock Placement and Freeze Mechanics
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.isGameRunning() && plugin.getFrozenHiders().contains(player.getUniqueId())) {
            // Cancel movement entirely but allow looking around smoothly
            if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ() || event.getFrom().getY() != event.getTo().getY()) {
                org.bukkit.Location back = event.getFrom().clone();
                back.setYaw(event.getTo().getYaw());
                back.setPitch(event.getTo().getPitch());
                event.setTo(back);
            }
            return;
        }

        if (DisguiseAPI.isDisguised(player)) {
            if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getZ() == event.getTo().getZ()) {
                player.setVelocity(new org.bukkit.util.Vector(0, player.getVelocity().getY(), 0));
            }
        }
    }

    // FEATURE: 2-Second Sneak Activation System
    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isGameRunning() || !plugin.getHiders().contains(player.getUniqueId())) return;

        if (event.isSneaking()) {
            // Start counting 2 seconds (40 ticks)
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && player.isSneaking()) {
                        if (plugin.getFrozenHiders().contains(player.getUniqueId())) {
                            // Unfreeze
                            plugin.getFrozenHiders().remove(player.getUniqueId());
                            player.sendTitle("§a§lUNFROZEN", "§7You can now run and slide!", 5, 20, 5);
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                        } else {
                            // Freeze
                            plugin.getFrozenHiders().add(player.getUniqueId());
                            player.sendTitle("§c§lFROZEN IN PLACE", "§7Sneak for 2s to unlock movement!", 5, 20, 5);
                            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 2.0f);
                        }
                    }
                }
            };
            sneakTimerTasks.put(player.getUniqueId(), task);
            task.runTaskLater(plugin, 40L); // 40 ticks = 2 seconds
        } else {
            // Player stopped sneaking early, cancel task execution
            if (sneakTimerTasks.containsKey(player.getUniqueId())) {
                sneakTimerTasks.get(player.getUniqueId()).cancel();
                sneakTimerTasks.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (sneakTimerTasks.containsKey(player.getUniqueId())) {
            sneakTimerTasks.get(player.getUniqueId()).cancel();
            sneakTimerTasks.remove(player.getUniqueId());
        }
        if (plugin.isGameRunning() && plugin.getHiders().contains(player.getUniqueId())) {
            plugin.getHiders().remove(player.getUniqueId());
            plugin.getFrozenHiders().remove(player.getUniqueId());
            plugin.getDisconnectedHiders().add(player.getUniqueId()); 
            plugin.updateScoreboardDisplay();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.isGameRunning()) {
            if (plugin.getDisconnectedHiders().contains(player.getUniqueId())) {
                plugin.getDisconnectedHiders().remove(player.getUniqueId());
                plugin.getHiders().add(player.getUniqueId());
                plugin.getHidersTeam().addEntry(player.getName());
                player.setGameMode(GameMode.SURVIVAL);
                if (!player.getInventory().contains(Material.CLOCK)) {
                    plugin.giveTauntClock(player);
                }
                plugin.updateScoreboardDisplay();
            } else {
                player.setGameMode(GameMode.SPECTATOR);
            }
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, true, false, false));
        }
    }

    @EventHandler
    public void onSeekerRespawn(PlayerRespawnEvent event) {
        Player seeker = event.getPlayer();
        if (plugin.isGameRunning() && plugin.getSeekers().contains(seeker.getUniqueId())) {
            plugin.giveTrackerCompass(seeker);
            seeker.setGlowing(true);
        }
    }

    @EventHandler
    public void onCamoGuiClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(camoGuiTitle)) return;
        Player player = (Player) event.getPlayer();
        if (plugin.isGameRunning() && plugin.getHiders().contains(player.getUniqueId())) {
            if (!plugin.getLockedCamoHiders().contains(player.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.openCamoSelectionGUI(player));
            }
        }
    }

    @EventHandler
    public void onCamoGuiClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(camoGuiTitle)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        String displayName = event.getCurrentItem().getItemMeta().getDisplayName();
        DisguiseType disguiseType = null;
        
        if (displayName.equals("§aPig Camo")) disguiseType = DisguiseType.PIG;
        else if (displayName.equals("§eCow Camo")) disguiseType = DisguiseType.COW;
        else if (displayName.equals("§fChicken Camo")) disguiseType = DisguiseType.CHICKEN;
        else if (displayName.equals("§7Sheep Camo")) disguiseType = DisguiseType.SHEEP;
        else if (displayName.equals("§7Wolf Camo")) disguiseType = DisguiseType.WOLF;
        else if (displayName.equals("§6Fox Camo")) disguiseType = DisguiseType.FOX;
        else if (displayName.equals("§6Cat Camo")) disguiseType = DisguiseType.CAT;
        else if (displayName.equals("§aRabbit Camo")) disguiseType = DisguiseType.RABBIT;
        else if (displayName.equals("§8Bat Camo")) disguiseType = DisguiseType.BAT;
        else if (displayName.equals("§2Creeper Camo")) disguiseType = DisguiseType.CREEPER;
        else if (displayName.equals("§2Zombie Camo")) disguiseType = DisguiseType.ZOMBIE;
        else if (displayName.equals("§7Skeleton Camo")) disguiseType = DisguiseType.SKELETON;
        else if (displayName.equals("§5Spider Camo")) disguiseType = DisguiseType.SPIDER;
        else if (displayName.equals("§fIron Golem Camo")) disguiseType = DisguiseType.IRON_GOLEM;
        else if (displayName.equals("§aVillager Camo")) disguiseType = DisguiseType.VILLAGER;

        if (disguiseType != null) {
            MobDisguise disguise = new MobDisguise(disguiseType);
            disguise.setViewSelfDisguise(true);
            disguise.setHearSelfDisguise(true);
            disguise.setSelfDisguiseVisible(true);
            disguise.setModifyBoundingBox(true); 

            LivingWatcher watcher = disguise.getWatcher();
            if (watcher != null) {
                watcher.setItemInMainHand(new ItemStack(Material.AIR));
                watcher.setItemInOffHand(new ItemStack(Material.AIR));
            }
            
            if (disguiseType == DisguiseType.VILLAGER && watcher instanceof VillagerWatcher) {
                VillagerWatcher villagerWatcher = (VillagerWatcher) watcher;
                Biome biome = player.getLocation().getBlock().getBiome();
                villagerWatcher.setProfession(Villager.Profession.NONE);
                villagerWatcher.setType(getVillagerTypeFromBiome(biome));
            }

            DisguiseAPI.disguiseToAll(player, disguise);
            applyMobPhysics(player, disguiseType);
            
            plugin.getLockedCamoHiders().add(player.getUniqueId());
            player.closeInventory();
        }
    }

    private Villager.Type getVillagerTypeFromBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        if (name.contains("desert")) return Villager.Type.DESERT;
        if (name.contains("savanna")) return Villager.Type.SAVANNA;
        if (name.contains("snow") || name.contains("ice") || name.contains("frozen")) return Villager.Type.SNOW;
        if (name.contains("swamp")) return Villager.Type.SWAMP;
        if (name.contains("taiga")) return Villager.Type.TAIGA;
        if (name.contains("jungle")) return Villager.Type.JUNGLE;
        return Villager.Type.PLAINS;
    }

    private void applyMobPhysics(Player player, DisguiseType type) {
        CamoCommand.clearMobPhysics(player);
        switch (type) {
            case CHICKEN:
            case BAT:
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, -1, 0, true, false, false));
                break;
            case RABBIT:
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, -1, 1, true, false, false));
                break;
            case WOLF:
            case FOX:
            case CAT:
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 0, true, false, false));
                break;
            case SPIDER:
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 0, true, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, -1, 0, true, false, false));
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onCompassInteract(PlayerInteractEvent event) {
        Player seeker = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.COMPASS) {
            if (item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§c§lSEEKER TRACKER")) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);

                    if (!plugin.getSeekers().contains(seeker.getUniqueId())) return;

                    long now = System.currentTimeMillis();
                    int dynamicCompassLimit = (plugin.getTimeLeft() <= 60) ? 10 : 45;

                    if (compassCooldown.containsKey(seeker.getUniqueId())) {
                        long lastUse = compassCooldown.get(seeker.getUniqueId());
                        long secondsLeft = dynamicCompassLimit - ((now - lastUse) / 1000);
                        if (secondsLeft > 0) {
                            seeker.sendMessage("§cYour tracker is on cooldown for " + secondsLeft + "s!");
                            return;
                        }
                    }

                    Player nearestHider = null;
                    double nearestDistance = Double.MAX_VALUE;

                    for (UUID hiderId : plugin.getHiders()) {
                        Player hider = Bukkit.getPlayer(hiderId);
                        if (hider != null && hider.getWorld().equals(seeker.getWorld())) {
                            double dist = seeker.getLocation().distance(hider.getLocation());
                            if (dist < nearestDistance) {
                                nearestDistance = dist;
                                nearestHider = hider;
                            }
                        }
                    }

                    if (nearestHider != null) {
                        seeker.setCompassTarget(nearestHider.getLocation());
                        seeker.sendMessage("§a§l[!] §eNearest hider tracked! (" + (int) nearestDistance + " blocks away)");
                        seeker.playSound(seeker.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                        compassCooldown.put(seeker.getUniqueId(), now);
                    } else {
                        seeker.sendMessage("§cNo active hiders found on the map!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onClockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.CLOCK) {
            if (item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§6§lTAUNT MENU")) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);

                    long now = System.currentTimeMillis();
                    int dynamicLimit = (plugin.getTimeLeft() <= 60) ? 15 : 60;

                    if (tauntCooldown.containsKey(player.getUniqueId())) {
                        long lastUse = tauntCooldown.get(player.getUniqueId());
                        long secondsLeft = dynamicLimit - ((now - lastUse) / 1000);
                        if (secondsLeft > 0) {
                            player.sendMessage("§cTaunt is on cooldown! Wait " + secondsLeft + "s.");
                            return;
                        }
                    }
                    openTauntGUI(player);
                }
            }
        }
    }

    private void openTauntGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, tauntGuiTitle);
        gui.setItem(1, createGuiItem(Material.FIREWORK_ROCKET, "§eFirework Burst", "§7Play a sharp firework detonation!"));
        gui.setItem(3, createGuiItem(Material.NETHER_STAR, "§cWither Alarm", "§7Play a terrifying Wither roar sound!"));
        gui.setItem(5, createGuiItem(Material.ANVIL, "§7Anvil Drop", "§7Play a heavy metallic anvil smash!"));
        gui.setItem(7, createGuiItem(Material.ENDER_PEARL, "§bEnderman Distortion", "§7Play an eerie teleport distortion!"));
        player.openInventory(gui);
    }

    private ItemStack createGuiItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(tauntGuiTitle)) return;
        event.setCancelled(true); 
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        String itemName = event.getCurrentItem().getItemMeta().getDisplayName();
        Sound soundToPlay = null;

        if (itemName.equals("§eFirework Burst")) soundToPlay = Sound.ENTITY_FIREWORK_ROCKET_BLAST;
        else if (itemName.equals("§cWither Alarm")) soundToPlay = Sound.ENTITY_WITHER_SPAWN;
        else if (itemName.equals("§7Anvil Drop")) soundToPlay = Sound.BLOCK_ANVIL_LAND;
        else if (itemName.equals("§bEnderman Distortion")) soundToPlay = Sound.ENTITY_ENDERMAN_TELEPORT;

        if (soundToPlay != null) {
            player.getWorld().playSound(player.getLocation(), soundToPlay, 3.0f, 1.0f);
            Bukkit.broadcastMessage("§6§l[TAUNT] " + player.getName() + " triggered a noisy alert nearby!");
            
            tauntCooldown.put(player.getUniqueId(), System.currentTimeMillis());
            player.closeInventory();
        }
    }

    @EventHandler
    public void onHiderHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player seeker = (Player) event.getDamager();
            Player hider = (Player) event.getEntity();

            if (plugin.getSeekers().contains(seeker.getUniqueId()) && plugin.getHiders().contains(hider.getUniqueId())) {
                if (DisguiseAPI.isDisguised(hider)) {
                    DisguiseAPI.undisguiseToAll(hider);
                    eliminateHider(hider, "was found by Seeker " + seeker.getName());
                }
            } else if (plugin.getHiders().contains(seeker.getUniqueId()) && plugin.getHiders().contains(hider.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSpectatorChat(AsyncPlayerChatEvent event) {
        Player chatter = event.getPlayer();
        if (chatter.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true); 
            String formattedMessage = "§7[SPEC] " + chatter.getName() + ": " + event.getMessage();
            chatter.sendMessage(formattedMessage);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) p.sendMessage(formattedMessage);
            }
        }
    }

    @EventHandler
    public void onHiderDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getHiders().contains(player.getUniqueId())) {
            eliminateHider(player, "died during the hunt!");
        }
        if (plugin.getSeekers().contains(player.getUniqueId())) {
            event.getDrops().removeIf(item -> item.getType() == Material.COMPASS && item.hasItemMeta() && item.getItemMeta().getDisplayName().equals("§c§lSEEKER TRACKER"));
        }
    }

    private void eliminateHider(Player player, String reason) {
        if (DisguiseAPI.isDisguised(player)) {
            DisguiseAPI.undisguiseToAll(player);
        }
        CamoCommand.clearMobPhysics(player);
        player.getInventory().clear(); 
        player.removePotionEffect(PotionEffectType.SATURATION); 
        plugin.getHiders().remove(player.getUniqueId());
        plugin.getFrozenHiders().remove(player.getUniqueId()); // Make sure to clean up freeze map profiles
        plugin.getHidersTeam().removeEntry(player.getName());
        player.setGameMode(GameMode.SPECTATOR);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        Bukkit.broadcastMessage("§c§l[H&S] " + player.getName() + " " + reason + " and is now a Spectator!");
        plugin.updateScoreboardDisplay();
    }
}
