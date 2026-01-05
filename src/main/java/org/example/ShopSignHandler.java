package org.example;


import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShopSignHandler {
    private final ChestShopPlugin plugin;

    public ShopSignHandler(ChestShopPlugin plugin) {
        this.plugin = plugin;
    }

    public PriceInfo parsePriceLine(String priceLine) {
        double buyPrice = -1;
        double sellPrice = -1;
        plugin.debug("1 BuyPrice: " + buyPrice + " SellPrice: " + sellPrice);


        if (priceLine == null || priceLine.isEmpty()) {
            return new PriceInfo(-1, -1);
        }

        plugin.debug("2 BuyPrice: " + buyPrice + " SellPrice: " + sellPrice);

        try {
            // Format: BX:SX or B$X:S$X or BX or B$X or SX or S$X or BFREE:SX or BX:SFREE or BFREE or SFREE
            if (priceLine.contains(":")) {
                // Both buy and sell prices
                String[] parts = priceLine.split(":");
                if (parts[0].startsWith("B")) {
                    // Handle buy price
                    String priceStr = parts[0].substring(1).replace("$", "");
                    if (priceStr.equalsIgnoreCase("FREE")) {
                        buyPrice = 0;
                    } else {
                        buyPrice = parseNumberWithSuffix(priceStr);
                    }
                }
                if (parts[1].startsWith("S")) {
                    // Handle sell price
                    String priceStr = parts[1].substring(1).replace("$", "");
                    if (priceStr.equalsIgnoreCase("FREE")) {
                        sellPrice = 0;
                    } else {
                        sellPrice = parseNumberWithSuffix(priceStr);
                    }
                }
            } else if (priceLine.startsWith("B")) {
                // Buy only
                String priceStr = priceLine.substring(1).replace("$", "");
                if (priceStr.equalsIgnoreCase("FREE")) {
                    buyPrice = 0;
                } else {
                    buyPrice = parseNumberWithSuffix(priceStr);
                }
            } else if (priceLine.startsWith("S")) {
                // Sell only
                String priceStr = priceLine.substring(1).replace("$", "");
                if (priceStr.equalsIgnoreCase("FREE")) {
                    sellPrice = 0;
                } else {
                    sellPrice = parseNumberWithSuffix(priceStr);
                }
            }
        } catch (NumberFormatException e) {
            return new PriceInfo(-1, -1);
        }

        plugin.debug("3 BuyPrice: " + buyPrice + " SellPrice: " + sellPrice);
        return new PriceInfo(buyPrice, sellPrice);
    }


    public void updateSignText(Sign sign, ItemStack item, double buyPrice, double sellPrice) {
        if (!(sign instanceof org.bukkit.block.Sign)) return;

        // Add item name
        String itemName = getItemDisplayName(item);

        // Build item info
        StringBuilder info = new StringBuilder();
        info.append("§6").append(itemName);

        // Add enchantment info for enchanted books
        if (item.getType() == Material.ENCHANTED_BOOK) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null) {
                for (Map.Entry<Enchantment, Integer> ench : meta.getStoredEnchants().entrySet()) {
                    info.append("\n§7").append(formatEnchantmentName(ench.getKey())).append(" ").append(ench.getValue());
                }
            }
        }

        // Add prices
        if (buyPrice > 0) info.append("\n§aBuy: $").append(buyPrice);
        if (sellPrice > 0) info.append("\n§aSell: $").append(sellPrice);

        // Set the visible text (shortened if needed)
        String visibleText = itemName.length() > 15 ? itemName.substring(0, 12) + "..." : itemName;
        sign.setLine(3, visibleText);

        // Update the sign
        sign.update();
    }


    private double parseNumberWithSuffix(String text) {
        text = text.trim().toUpperCase();

        double multiplier = 1;

        if (text.endsWith("K")) {
            multiplier = 1_000;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("M")) {
            multiplier = 1_000_000;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("B")) {
            multiplier = 1_000_000_000;
            text = text.substring(0, text.length() - 1);
        }

        return Double.parseDouble(text) * multiplier;
    }


    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return formatItemName(item.getType().name());
    }

    private String formatItemName(String name) {
        return Arrays.stream(name.toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }




    private String formatEnchantmentName(Enchantment enchantment) {
        return formatItemName(enchantment.getKey().getKey());
    }

    public static class PriceInfo {
        private final double buyPrice;
        private final double sellPrice;

        public PriceInfo(double buyPrice, double sellPrice) {
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }

        public double getBuyPrice() { return buyPrice; }
        public double getSellPrice() { return sellPrice; }
        public boolean isValid() { return buyPrice > 0 || sellPrice > 0; }
    }
}
