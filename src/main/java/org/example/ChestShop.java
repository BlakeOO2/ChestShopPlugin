package org.example;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

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
    private static String adminShopDisplayName = "Admin Shop"; // Default name

    public ChestShop(String ownerName, Location location) {
        this.ownerName = ownerName;
        this.location = location.clone(); // Clone location to prevent external modification
        this.isPending = false;
        this.buyPrice = -1;
        this.sellPrice = -1;
        this.quantity = 0;
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
        // Don't serialize the location here as it's stored separately
        data.put("buyPrice", buyPrice);
        data.put("sellPrice", sellPrice);
        data.put("quantity", quantity);
        data.put("isAdminShop", isAdminShop);
        if (item != null) {
            data.put("item", item.serialize());
        }
        data.put("isPending", isPending);
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
                Object itemData = data.get("item");
                if (itemData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) itemData;
                    shop.setItem(ItemStack.deserialize(itemMap));
                }
            }

            return shop;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid shop data: " + e.getMessage(), e);
        }
    }


    public boolean isAdminShop() { return isAdminShop; }
    public void setAdminShop(boolean adminShop) { isAdminShop = adminShop; }
    public static void setAdminShopDisplayName(String name) { adminShopDisplayName = name; }
    public static String getAdminShopDisplayName() { return adminShopDisplayName; }

}
