package org.example;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.Sign;
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

                // Handle item meta
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();

                    // Save display name if present
                    if (meta.hasDisplayName()) {
                        itemData.put("display_name", meta.getDisplayName());
                    }

                    // Save lore if present
                    if (meta.hasLore()) {
                        itemData.put("lore", meta.getLore());
                    }

                    // Handle special items
                    if (item.getType() == Material.ENCHANTED_BOOK) {
                        EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) meta;
                        Map<String, Integer> enchants = new HashMap<>();
                        for (Map.Entry<Enchantment, Integer> ench : enchantMeta.getStoredEnchants().entrySet()) {
                            enchants.put(ench.getKey().getKey().toString(), ench.getValue());
                        }
                        itemData.put("enchantments", enchants);
                    }
                    // Special handling for written books and maps
                    else if (item.getType() == Material.WRITTEN_BOOK || item.getType() == Material.FILLED_MAP) {
                        // For written books and maps, we'll save the full serialized form
                        itemData.put("full_serialized", item.serialize());
                    }
                    // Handle shulker boxes
                    else if (isShulkerBox(item.getType())) {
                        try {
                            BlockStateMeta blockMeta = (BlockStateMeta) meta;
                            if (blockMeta.hasBlockState() && blockMeta.getBlockState() instanceof ShulkerBox shulkerBox) {
                                // Save shulker contents
                                Map<String, Map<String, Object>> contents = new HashMap<>();
                                ItemStack[] shulkerContents = shulkerBox.getInventory().getContents();

                                for (int slot = 0; slot < shulkerContents.length; slot++) {
                                    ItemStack slotItem = shulkerContents[slot];
                                    if (slotItem != null && slotItem.getType() != Material.AIR) {
                                        contents.put(String.valueOf(slot), serializeItemStack(slotItem));
                                    }
                                }

                                if (!contents.isEmpty()) {
                                    itemData.put("shulker_contents", contents);
                                    getLogger().info("Saved shulker box with " + contents.size() + " items");
                                }
                            }
                        } catch (Exception e) {
                            getLogger().warning("Error saving shulker box: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    // Handle buckets with entities
                    else if (item.getType().name().endsWith("_BUCKET") && !item.getType().equals(Material.BUCKET)) {
                        // Save bucket data using NBT tags if available
                        PersistentDataContainer container = meta.getPersistentDataContainer();
                        if (!container.isEmpty()) {
                            Map<String, String> bucketData = new HashMap<>();
                            for (NamespacedKey nbtKey : container.getKeys()) {
                                String value = container.get(nbtKey, PersistentDataType.STRING);
                                if (value != null) {
                                    bucketData.put(nbtKey.toString(), value);
                                }
                            }
                            if (!bucketData.isEmpty()) {
                                itemData.put("bucket_data", bucketData);
                            }
                        }

                        // Also save the item's complete serialized form as backup
                        itemData.put("full_serialized", item.serialize());
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

    public void repairShop(Location location) {
        ChestShop shop = shops.get(location);
        if (shop != null) {
            shop.setPending(true);

            // Update the sign to indicate it needs repair
            Block block = location.getBlock();
            if (block.getState() instanceof Sign sign) {
                sign.setLine(3, "?"); //TODO might need to change this to i 4?
                sign.update();
            }

            saveData();
            getLogger().info("Shop at " + location + " marked for repair");
        }
    }


    private Map<String, Object> serializeItemStack(ItemStack item) {
        Map<String, Object> serialized = item.serialize();

        // Handle special items within the serialized map
        if (item.hasItemMeta()) {
            if (item.getItemMeta() instanceof BlockStateMeta blockMeta &&
                    blockMeta.hasBlockState() &&
                    blockMeta.getBlockState() instanceof ShulkerBox nestedShulker) {

                // Handle nested shulker boxes
                Map<String, Map<String, Object>> nestedContents = new HashMap<>();
                ItemStack[] nestedItems = nestedShulker.getInventory().getContents();

                for (int slot = 0; slot < nestedItems.length; slot++) {
                    if (nestedItems[slot] != null && nestedItems[slot].getType() != Material.AIR) {
                        nestedContents.put(String.valueOf(slot), nestedItems[slot].serialize());
                    }
                }

                if (!nestedContents.isEmpty()) {
                    serialized.put("nested_shulker_contents", nestedContents);
                }
            }
        }

        return serialized;
    }

    public void saveShopData() {
        saveData();
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

                    // Load item
                    ConfigurationSection itemSection = dataSection.getConfigurationSection("item");
                    if (itemSection != null) {
                        ItemStack item = null;

                        // Try to load from full serialized form first (for buckets)
                        if (itemSection.contains("full_serialized")) {
                            try {
                                Map<String, Object> fullSerialized = itemSection.getConfigurationSection("full_serialized").getValues(true);
                                item = ItemStack.deserialize(fullSerialized);
                                getLogger().info("Loaded item from full serialization: " + item.getType());
                            } catch (Exception e) {
                                getLogger().warning("Failed to load from full serialization: " + e.getMessage());
                            }
                        }

                        // If that failed or wasn't available, load normally
                        if (item == null) {
                            Material type = Material.valueOf(itemSection.getString("type"));
                            int amount = itemSection.getInt("amount", 1);
                            item = new ItemStack(type, amount);

                            ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                // Load display name
                                if (itemSection.contains("display_name")) {
                                    meta.setDisplayName(itemSection.getString("display_name"));
                                }

                                // Load lore
                                if (itemSection.contains("lore")) {
                                    meta.setLore(itemSection.getStringList("lore"));
                                }

                                // Handle special items
                                if (type == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta enchantMeta) {
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
                                    item.setItemMeta(enchantMeta);
                                }
                                // Handle written books and maps
                                else if (type == Material.WRITTEN_BOOK || type == Material.FILLED_MAP) {
                                    // Try to use the full serialized form if available
                                    if (itemSection.contains("full_serialized")) {
                                        try {
                                            ConfigurationSection fullSerialized = itemSection.getConfigurationSection("full_serialized");
                                            if (fullSerialized != null) {
                                                item = ItemStack.deserialize(fullSerialized.getValues(true));
                                            }
                                        } catch (Exception e) {
                                            getLogger().warning("Failed to load special item (" + type + ") from full serialization: " + e.getMessage());
                                        }
                                    }
                                }
                                // Handle shulker boxes
                                else if (isShulkerBox(type) && meta instanceof BlockStateMeta blockMeta) {
                                    try {
                                        ShulkerBox shulkerBox = (ShulkerBox) blockMeta.getBlockState();
                                        ConfigurationSection contents = itemSection.getConfigurationSection("shulker_contents");

                                        if (contents != null) {
                                            for (String slotStr : contents.getKeys(false)) {
                                                int slot = Integer.parseInt(slotStr);
                                                Map<String, Object> slotItemMap = contents.getConfigurationSection(slotStr).getValues(true);
                                                ItemStack slotItem = deserializeItemStack(slotItemMap);
                                                shulkerBox.getInventory().setItem(slot, slotItem);
                                            }
                                            blockMeta.setBlockState(shulkerBox);
                                            item.setItemMeta(blockMeta);
                                            getLogger().info("Loaded shulker box with contents");
                                        }
                                    } catch (Exception e) {
                                        getLogger().warning("Error loading shulker box: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                }
                                // Handle buckets
                                else if (type.name().endsWith("_BUCKET") && !type.equals(Material.BUCKET)) {
                                    if (itemSection.contains("bucket_data")) {
                                        ConfigurationSection bucketData = itemSection.getConfigurationSection("bucket_data");
                                        if (bucketData != null) {
                                            PersistentDataContainer container = meta.getPersistentDataContainer();
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
                                    item.setItemMeta(meta);
                                } else {
                                    item.setItemMeta(meta);
                                }
                            }
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

    private ItemStack deserializeItemStack(Map<String, Object> serialized) {
        ItemStack item = ItemStack.deserialize(serialized);

        // Handle nested shulker boxes
        if (serialized.containsKey("nested_shulker_contents") &&
                item.getItemMeta() instanceof BlockStateMeta blockMeta &&
                blockMeta.getBlockState() instanceof ShulkerBox shulkerBox) {

            try {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> nestedContents =
                        (Map<String, Map<String, Object>>) serialized.get("nested_shulker_contents");

                for (Map.Entry<String, Map<String, Object>> entry : nestedContents.entrySet()) {
                    int slot = Integer.parseInt(entry.getKey());
                    ItemStack nestedItem = ItemStack.deserialize(entry.getValue());
                    shulkerBox.getInventory().setItem(slot, nestedItem);
                }

                blockMeta.setBlockState(shulkerBox);
                item.setItemMeta(blockMeta);
            } catch (Exception e) {
                getLogger().warning("Error deserializing nested shulker box: " + e.getMessage());
            }
        }

        return item;
    }

    private boolean isShulkerBox(Material material) {
        return material.name().endsWith("SHULKER_BOX");
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
