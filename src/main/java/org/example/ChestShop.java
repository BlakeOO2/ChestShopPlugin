package org.example;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.HashMap;
import java.util.Map;

public class ChestShop {
    private String ownerName;
    private Location location;
    private ItemStack item;
    private double buyPrice;
    private double sellPrice;
    private int quantity;
    private boolean isPending;
    private boolean isAdminShop;
    static ChestShopPlugin plugin;
    private String adminShopDisplayName; // Default name

    public ChestShop(String ownerName, Location location) {
        this.ownerName = ownerName;
        this.location = location.clone(); // Clone location to prevent external modification
        this.isPending = false;
        this.buyPrice = -1;
        this.sellPrice = -1;
        this.quantity = 0;
        plugin = ChestShopPlugin.getInstance();
        this.adminShopDisplayName = plugin.getServerName();
    }

    // Getters with defensive copying
    public String getOwnerName() { return ownerName; }
    public Location getLocation() { return location.clone(); }
    public ItemStack getItem() { return item != null ? item.clone() : null; }
    public double getBuyPrice() { return buyPrice; }
    public double getSellPrice() { return sellPrice; }
    public int getQuantity() { return quantity; }
    public boolean isPending() { return isPending; }

    // Setters with validation
    public void setItem(ItemStack item) {
        this.item = item != null ? item.clone() : null;
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = Math.max(-1, buyPrice);
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = Math.max(-1, sellPrice);
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(0, quantity);
    }

    public void setPending(boolean pending) {
        this.isPending = pending;
    }

    // Helper methods
    public boolean isBuying() {
        return buyPrice > 0;
    }

    public boolean isSelling() {
        return sellPrice > 0;
    }

    public boolean isValid() {
        return !isPending && item != null && quantity > 0 && (buyPrice > 0 || sellPrice > 0);
    }

    public ChestShop(String ownerName, Location location, boolean isAdminShop) {
        this(ownerName, location);
        this.isAdminShop = isAdminShop;
    }

    // Serialization
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("owner", ownerName);
        data.put("buyPrice", buyPrice);
        data.put("sellPrice", sellPrice);
        data.put("quantity", quantity);
        data.put("isAdminShop", isAdminShop);
        data.put("isPending", isPending);

        // Special handling for items that need special serialization
        if (item != null) {
            Map<String, Object> itemData = item.serialize();

            // Store full serialized form for special items to preserve all data
            if (isSpecialItem(item.getType())) {
                // Save the complete serialized form for these special items
                itemData.put("full_serialized", item.serialize());
            }

            // Additional specific handling for enchanted books
            if (item.getType() == Material.ENCHANTED_BOOK && item.hasItemMeta()) {
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
                Map<String, Integer> enchants = new HashMap<>();
                for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
                    enchants.put(entry.getKey().getKey().toString(), entry.getValue());
                }
                itemData.put("stored-enchants", enchants);
            }

            data.put("item", itemData);
        }

        return data;
    }



    public static ChestShop deserialize(Map<String, Object> data) {
        try {
            String owner = (String) data.get("owner");
            Location location = Location.deserialize((Map<String, Object>) data.get("location"));

            ChestShop shop = new ChestShop(owner, location);

            if (data.containsKey("buyPrice")) {
                shop.setBuyPrice(((Number) data.get("buyPrice")).doubleValue());
            }
            if (data.containsKey("sellPrice")) {
                shop.setSellPrice(((Number) data.get("sellPrice")).doubleValue());
            }
            if (data.containsKey("quantity")) {
                shop.setQuantity(((Number) data.get("quantity")).intValue());
            }
            if (data.containsKey("isAdminShop")) {
                shop.setAdminShop((Boolean) data.get("isAdminShop"));
            }
            if (data.containsKey("isPending")) {
                shop.setPending((Boolean) data.get("isPending"));
            }

            if (data.containsKey("item")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemData = (Map<String, Object>) data.get("item");
                ItemStack item = null;

                // Try to load from full serialized form first (for special items)
                if (itemData.containsKey("full_serialized")) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fullSerialized = (Map<String, Object>) itemData.get("full_serialized");
                        item = ItemStack.deserialize(fullSerialized);
                    } catch (Exception e) {
                        // If loading from full serialization fails, we'll fall back to normal deserialization
                    }
                }

                // If item is still null, load using standard deserialization
                if (item == null) {
                    item = ItemStack.deserialize(itemData);

                    // Handle enchanted books specially if needed
                    if (item.getType() == Material.ENCHANTED_BOOK && itemData.containsKey("stored-enchants")) {
                        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
                        @SuppressWarnings("unchecked")
                        Map<String, Integer> enchants = (Map<String, Integer>) itemData.get("stored-enchants");

                        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                            NamespacedKey key = NamespacedKey.fromString(entry.getKey());
                            if (key != null) {
                                Enchantment ench = Enchantment.getByKey(key);
                                if (ench != null) {
                                    meta.addStoredEnchant(ench, entry.getValue(), true);
                                }
                            }
                        }
                        item.setItemMeta(meta);
                    }
                }

                shop.setItem(item);
            }

            return shop;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid shop data: " + e.getMessage(), e);
        }
    }



    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }


    public boolean isAdminShop() { return isAdminShop; }
    public void setAdminShop(boolean adminShop) { isAdminShop = adminShop; }
    public static void setAdminShopDisplayName(String name) {
        plugin.getConfig().set("serverName", name);
        plugin.saveConfig();
    }
    public static String getAdminShopDisplayName() {
        return plugin.getConfig().getString("serverName");
    }

    /**
     * Determines if an item type needs special serialization handling
     * @param material The material to check
     * @return True if the material needs special handling
     */
    private boolean isSpecialItem(Material material) {
        return material == Material.WRITTEN_BOOK || 
               material == Material.FILLED_MAP || 
               material == Material.ENCHANTED_BOOK || 
               (material.name().endsWith("_BUCKET") && !material.equals(Material.BUCKET));
    }


}
