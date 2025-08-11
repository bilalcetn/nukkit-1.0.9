package AllOne;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.player.PlayerFoodLevelChangeEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Main extends PluginBase implements Listener {

    private Config spawnConfig;
    private final Set<String> vanishedPlayers = new HashSet<>();
    private final Map<String, Boolean> geceDurumu = new HashMap<>();

    @Override
    public void onEnable() {
        this.getLogger().info("AllOne plugin aktif!");
        this.getServer().getPluginManager().registerEvents(this, this);

        this.getDataFolder().mkdirs();
        this.spawnConfig = new Config(new File(this.getDataFolder(), "spawn.yml"), Config.YAML);

        this.getServer().getScheduler().scheduleRepeatingTask(new Task() {
            @Override
            public void onRun(int currentTick) {
                for (Level level : getServer().getLevels().values()) {
                    // Yagmur engelle
                    if (level.isRaining()) {
                        level.setRaining(false);
                        getLogger().info("Yagmur engellendi: " + level.getName());
                    }

                    // Gece durumu kontrolü, sadece gece baslarken gunduze cevir
                    long time = level.getTime();

                    geceDurumu.putIfAbsent(level.getName(), false);
                    boolean isNight = geceDurumu.get(level.getName());

                    if (time >= 13000 && !isNight) {
                        level.setTime(1000);
                        geceDurumu.put(level.getName(), true);
                        getLogger().info("Gece basladi, " + level.getName() + " gunduze alindi.");
                    } else if (time < 12000 && isNight) {
                        geceDurumu.put(level.getName(), false);
                        getLogger().info(level.getName() + " artik gece degil.");
                    }
                }
            }
        }, 20 * 10);

        this.getLogger().info("Tum gorevler ve eventler kayit edildi.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("vanish")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Bu komut sadece oyun icinde kullanilabilir.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.isOp()) {
                player.sendMessage(TextFormat.RED + "Bu komutu kullanmak icin yetkin yok.");
                return true;
            }
            if (vanishedPlayers.contains(player.getName())) {
                for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                    p.showPlayer(player);
                }
                vanishedPlayers.remove(player.getName());
                player.sendMessage(TextFormat.GOLD + "Artik gorunursun.");
            } else {
                for (Player p : Server.getInstance().getOnlinePlayers().values()) {
                    if (!p.getName().equals(player.getName())) {
                        p.hidePlayer(player);
                    }
                }
                vanishedPlayers.add(player.getName());
                player.sendMessage(TextFormat.GOLD + "Artik gorunmezsin.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("setbase")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Bu komut sadece oyun icinde kullanilabilir.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.isOp()) {
                player.sendMessage(TextFormat.RED + "Bu komutu kullanmak icin yetkin yok.");
                return true;
            }
            Location loc = player.getLocation();
            spawnConfig.set("x", loc.getX());
            spawnConfig.set("y", loc.getY());
            spawnConfig.set("z", loc.getZ());
            spawnConfig.set("level", loc.getLevel().getName());
            spawnConfig.set("yaw", loc.getYaw());
            spawnConfig.set("pitch", loc.getPitch());
            spawnConfig.set("atstart", true);
            spawnConfig.save();
            player.sendMessage(TextFormat.GREEN + "Base basariyla ayarlandi.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("base")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Bu komut sadece oyun icinde kullanilabilir.");
                return true;
            }
            Player player = (Player) sender;
            teleportToSpawn(player);
            player.sendMessage(TextFormat.YELLOW + "Base'e geldin.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("ping")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                int ping = player.getPing();
                player.sendMessage(TextFormat.YELLOW + "Ping: " + ping + "ms");
            } else {
                sender.sendMessage("Komut sadece oyuncular icindir.");
            }
            return true;
        }

        return false;
    }

    private void teleportToSpawn(Player player) {
        if (!spawnConfig.exists("x")) {
            player.sendMessage(TextFormat.RED + "Base ayarlanmadi.");
            return;
        }
        double x = spawnConfig.getDouble("x");
        double y = spawnConfig.getDouble("y");
        double z = spawnConfig.getDouble("z");
        float yaw = (float) spawnConfig.getDouble("yaw", 0);
        float pitch = (float) spawnConfig.getDouble("pitch", 0);
        String levelName = spawnConfig.getString("level");

        if (!Server.getInstance().isLevelLoaded(levelName)) {
            Server.getInstance().loadLevel(levelName);
        }

        Level level = Server.getInstance().getLevelByName(levelName);
        if (level == null) {
            player.sendMessage(TextFormat.RED + "Dunya yuklenemedi.");
            return;
        }

        Location spawnLoc = new Location(x, y, z, yaw, pitch, level);
        player.teleport(spawnLoc);
    }

@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    if (spawnConfig.getBoolean("atstart", true)) {
        getServer().getScheduler().scheduleDelayedTask(this, () -> teleportToSpawn(player), 20);
    }

    if (player.getGamemode() == Player.CREATIVE) {
        player.setGamemode(Player.SURVIVAL);
        // 1 tick sonra tekrar Creative yap
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            player.setGamemode(Player.CREATIVE);
        }, 1);
    }
}


    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (block.getId() == Block.TNT && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.RED + "TNT kullanimi engellendi.");
        }
    }

    @EventHandler
    public void onPlayerFoodLevelChange(PlayerFoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Item item = player.getInventory().getItemInHand();

        if (item.getId() == Item.FLINT_AND_STEEL && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(TextFormat.RED + "Cakmak kullanimi kapatildi!");
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        String className = entity.getClass().getSimpleName();

        if (className.equals("EntityMinecartEmpty")
                || className.equals("EntityMinecartChest")
                || className.equals("EntityMinecartHopper")
                || className.equals("EntityMinecartTNT")) {
            entity.close();
        }
    }
}
