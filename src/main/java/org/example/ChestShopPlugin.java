package org.example;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ChestShopPlugin extends JavaPlugin {
    private Map<Location, ChestShop> shops = new HashMap<>();
    private Economy economy;
    private Set<UUID> bypassMode = new HashSet<>();
    private Set<UUID> chestBypassMode = new HashSet<>();
    private String serverName = "Admin Shop";
    private ShopSignHandler signHandler; // Add this line
    private NotificationManager notificationManager;


    public boolean toggleBypass(Player player) {
        if (bypassMode.contains(player.getUniqueId())) {
            bypassMode.remove(player.getUniqueId());
            return false;
        } else {
            bypassMode.add(player.getUniqueId());
            return true;
        }
    }

    public boolean toggleChestBypass(Player player) {
        if (chestBypassMode.contains(player.getUniqueId())) {
            chestBypassMode.remove(player.getUniqueId());
            return false;
        } else {
            chestBypassMode.add(player.getUniqueId());
            return true;
        }
    }

    public boolean isInChestBypassMode(Player player) {
        return chestBypassMode.contains(player.getUniqueId());
    }

    public boolean isInBypassMode(Player player) {
        return bypassMode.contains(player.getUniqueId());
    }

    private void saveData() {
        File file = new File(getDataFolder(), "data.yml");
        YamlConfiguration config = new YamlConfiguration();

        // Save server name
        config.set("serverName", serverName);

        // Save shops
        int i = 0;
        for (Map.Entry<Location, ChestShop> entry : shops.entrySet()) {
            config.set("shops." + i + ".location", entry.getKey().serialize());
            config.set("shops." + i + ".data", entry.getValue().serialize());
            i++;
        }

        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().severe("Could not save data to " + file + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadData() {
        File file = new File(getDataFolder(), "data.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load server name
        serverName = config.getString("serverName", "Admin Shop");
        ChestShop.setAdminShopDisplayName(serverName);

        // Load shops
        ConfigurationSection shopsSection = config.getConfigurationSection("shops");
        if (shopsSection != null) {
            for (String key : shopsSection.getKeys(false)) {
                try {
                    ConfigurationSection shopSection = shopsSection.getConfigurationSection(key);
                    if (shopSection == null) continue;

                    Map<String, Object> locationData = shopSection.getConfigurationSection("location").getValues(true);
                    Location location = Location.deserialize(locationData);

                    Map<String, Object> shopData = shopSection.getConfigurationSection("data").getValues(true);
                    ChestShop shop = new ChestShop(shopData.get("owner").toString(), location);

                    if (shopData.containsKey("buyPrice"))
                        shop.setBuyPrice(((Number) shopData.get("buyPrice")).doubleValue());
                    if (shopData.containsKey("sellPrice"))
                        shop.setSellPrice(((Number) shopData.get("sellPrice")).doubleValue());
                    if (shopData.containsKey("quantity"))
                        shop.setQuantity(((Number) shopData.get("quantity")).intValue());
                    if (shopData.containsKey("isAdminShop"))
                        shop.setAdminShop((Boolean) shopData.get("isAdminShop"));
                    if (shopData.containsKey("isPending"))
                        shop.setPending((Boolean) shopData.get("isPending"));
                    if (shopData.containsKey("item")) {
                        Map<String, Object> itemData = shopSection.getConfigurationSection("data.item").getValues(true);
                        shop.setItem(ItemStack.deserialize(itemData));
                    }

                    shops.put(location, shop);
                    getLogger().info("Loaded shop at " + location.toString());
                } catch (Exception e) {
                    getLogger().warning("Failed to load shop at index " + key + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getCommand("cs").setExecutor(new ChestShopCommand(this));
        loadData();

        this.notificationManager = new NotificationManager(this);

        this.signHandler = new ShopSignHandler(this);
        getLogger().info("ChestShop Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        saveData();
        bypassMode.clear();
        chestBypassMode.clear();
        getLogger().info("ChestShop Plugin has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public void addShop(Location location, ChestShop shop) {
        shops.put(location, shop);
        saveData();
    }

    public void removeShop(Location location) {
        shops.remove(location);
        saveData();
    }
    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public ChestShop getShop(Location location) {
        return shops.get(location);
    }

    public Economy getEconomy() {
        return economy;
    }

    public void setServerName(String name) {
        this.serverName = name;
        ChestShop.setAdminShopDisplayName(name);
        saveData();
    }
    public ShopSignHandler getSignHandler() {
        return signHandler;
    }

}
