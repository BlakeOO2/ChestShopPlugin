package org.example;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;




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
            String path = "shops." + i;
            config.set(path + ".location", entry.getKey().serialize());

            ChestShop shop = entry.getValue();
            Map<String, Object> shopData = new HashMap<>();
            shopData.put("owner", shop.getOwnerName());
            shopData.put("buyPrice", shop.getBuyPrice());
            shopData.put("sellPrice", shop.getSellPrice());
            shopData.put("quantity", shop.getQuantity());
            shopData.put("isAdminShop", shop.isAdminShop());
            shopData.put("isPending", shop.isPending());

            // Special handling for items
            ItemStack item = shop.getItem();
            if (item != null) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("type", item.getType().name());
                itemData.put("amount", item.getAmount());

                // Handle special items
                if (item.hasItemMeta()) {
                    switch (item.getType()) {
                        case ENCHANTED_BOOK:
                            if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                                Map<String, Integer> enchants = new HashMap<>();
                                for (Map.Entry<Enchantment, Integer> ench : meta.getStoredEnchants().entrySet()) {
                                    enchants.put(ench.getKey().getKey().toString(), ench.getValue());
                                }
                                itemData.put("enchantments", enchants);
                            }
                            break;

                        case SHULKER_BOX:
                        case BLACK_SHULKER_BOX:
                        case BLUE_SHULKER_BOX:
                        case BROWN_SHULKER_BOX:
                        case CYAN_SHULKER_BOX:
                        case GRAY_SHULKER_BOX:
                        case GREEN_SHULKER_BOX:
                        case LIGHT_BLUE_SHULKER_BOX:
                        case LIGHT_GRAY_SHULKER_BOX:
                        case LIME_SHULKER_BOX:
                        case MAGENTA_SHULKER_BOX:
                        case ORANGE_SHULKER_BOX:
                        case PINK_SHULKER_BOX:
                        case PURPLE_SHULKER_BOX:
                        case RED_SHULKER_BOX:
                        case WHITE_SHULKER_BOX:
                        case YELLOW_SHULKER_BOX:
                            if (item.getItemMeta() instanceof BlockStateMeta meta) {
                                if (meta.getBlockState() instanceof ShulkerBox shulker) {
                                    itemData.put("shulker_contents", shulker.getInventory().getContents());
                                }
                            }
                            break;

                        case AXOLOTL_BUCKET:
                        case PUFFERFISH_BUCKET:
                        case SALMON_BUCKET:
                        case COD_BUCKET:
                        case TROPICAL_FISH_BUCKET:
                        case TADPOLE_BUCKET:
                            if (item.hasItemMeta()) {
                                PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
                                Map<String, Object> bucketData = new HashMap<>();
                                // Save any custom NBT data
                                for (NamespacedKey nbtKey : container.getKeys()) {
                                    bucketData.put(nbtKey.toString(), container.get(nbtKey, PersistentDataType.STRING));
                                }
                                if (!bucketData.isEmpty()) {
                                    itemData.put("bucket_data", bucketData);
                                }
                            }
                            break;
                    }

                    // Save display name and lore if present
                    if (item.getItemMeta().hasDisplayName()) {
                        itemData.put("display_name", item.getItemMeta().getDisplayName());
                    }
                    if (item.getItemMeta().hasLore()) {
                        itemData.put("lore", item.getItemMeta().getLore());
                    }
                }

                shopData.put("item", itemData);
            }

            config.set(path + ".data", shopData);
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

                    ConfigurationSection dataSection = shopSection.getConfigurationSection("data");
                    if (dataSection == null) continue;

                    String owner = dataSection.getString("owner");
                    ChestShop shop = new ChestShop(owner, location);

                    shop.setBuyPrice(dataSection.getDouble("buyPrice", -1));
                    shop.setSellPrice(dataSection.getDouble("sellPrice", -1));
                    shop.setQuantity(dataSection.getInt("quantity", 0));
                    shop.setAdminShop(dataSection.getBoolean("isAdminShop", false));
                    shop.setPending(dataSection.getBoolean("isPending", false));

                    ConfigurationSection itemSection = dataSection.getConfigurationSection("item");
                    if (itemSection != null) {
                        Material type = Material.valueOf(itemSection.getString("type"));
                        int amount = itemSection.getInt("amount", 1);
                        ItemStack item = new ItemStack(type, amount);

                        // Handle special items
                        if (type != null) {
                            ItemMeta meta = item.getItemMeta();

                            // Handle display name and lore
                            if (itemSection.contains("display_name")) {
                                meta.setDisplayName(itemSection.getString("display_name"));
                            }
                            if (itemSection.contains("lore")) {
                                meta.setLore(itemSection.getStringList("lore"));
                            }

                            switch (type) {
                                case ENCHANTED_BOOK:
                                    if (meta instanceof EnchantmentStorageMeta enchantMeta) {
                                        ConfigurationSection enchants = itemSection.getConfigurationSection("enchantments");
                                        if (enchants != null) {
                                            for (String enchantKey : enchants.getKeys(false)) {
                                                NamespacedKey namespacedKey = NamespacedKey.fromString(enchantKey);
                                                if (namespacedKey != null) {
                                                    Enchantment enchantment = Enchantment.getByKey(namespacedKey);
                                                    if (enchantment != null) {
                                                        enchantMeta.addStoredEnchant(enchantment,
                                                                enchants.getInt(enchantKey), true);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break;

                                case SHULKER_BOX:
                                case BLACK_SHULKER_BOX:
                                case BLUE_SHULKER_BOX:
                                case BROWN_SHULKER_BOX:
                                case CYAN_SHULKER_BOX:
                                case GRAY_SHULKER_BOX:
                                case GREEN_SHULKER_BOX:
                                case LIGHT_BLUE_SHULKER_BOX:
                                case LIGHT_GRAY_SHULKER_BOX:
                                case LIME_SHULKER_BOX:
                                case MAGENTA_SHULKER_BOX:
                                case ORANGE_SHULKER_BOX:
                                case PINK_SHULKER_BOX:
                                case PURPLE_SHULKER_BOX:
                                case RED_SHULKER_BOX:
                                case WHITE_SHULKER_BOX:
                                case YELLOW_SHULKER_BOX:
                                    if (meta instanceof BlockStateMeta blockMeta) {
                                        if (blockMeta.getBlockState() instanceof ShulkerBox shulker) {
                                            ConfigurationSection contents =
                                                    itemSection.getConfigurationSection("shulker_contents");
                                            if (contents != null) {
                                                for (String slot : contents.getKeys(false)) {
                                                    ItemStack contentItem = contents.getItemStack(slot);
                                                    if (contentItem != null) {
                                                        shulker.getInventory().setItem(
                                                                Integer.parseInt(slot), contentItem);
                                                    }
                                                }
                                            }
                                            blockMeta.setBlockState(shulker);
                                        }
                                    }
                                    break;

                                case AXOLOTL_BUCKET:
                                case PUFFERFISH_BUCKET:
                                case SALMON_BUCKET:
                                case COD_BUCKET:
                                case TROPICAL_FISH_BUCKET:
                                case TADPOLE_BUCKET:
                                    if (meta != null && itemSection.contains("bucket_data")) {
                                        PersistentDataContainer container = meta.getPersistentDataContainer();
                                        ConfigurationSection bucketData = itemSection.getConfigurationSection("bucket_data");
                                        if (bucketData != null) {
                                            for (String dataKey : bucketData.getKeys(false)) {
                                                NamespacedKey nbtKey = NamespacedKey.fromString(dataKey);
                                                if (nbtKey != null) {
                                                    String value = bucketData.getString(dataKey);
                                                    if (value != null) {
                                                        container.set(nbtKey, PersistentDataType.STRING, value);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break;
                            }

                            item.setItemMeta(meta);
                        }

                        shop.setItem(item);
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
