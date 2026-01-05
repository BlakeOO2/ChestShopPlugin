package org.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.Bukkit;
import net.milkbowl.vault.economy.Economy;
import org.example.ShopSignHandler.PriceInfo;

import java.util.HashMap;


public class ShopListener implements Listener {
    private final ChestShopPlugin plugin;

    public ShopListener(ChestShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignEdit(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Sign)) {
            return;
        }

        // Only check for shop if it's attached to a container
        if (block.getBlockData() instanceof WallSign) {
            WallSign wallSign = (WallSign) block.getBlockData();
            Block attachedBlock = block.getRelative(wallSign.getFacing().getOppositeFace());

            // Check if it's a valid container (only Chest or Barrel)
            if (attachedBlock.getState() instanceof Chest ||
                    attachedBlock.getState() instanceof Barrel) {
                // Only process if this appears to be a shop sign (check format)
                if (isShopSign(event.getLines())) {
                    createNewShop(event);
                }
                // If it's not a shop sign, allow normal sign placement
            }
        }
    }

    private boolean isShopSign(String[] lines) {
        // Check if the sign follows shop format (has B/S price format on line 2)
        return lines[1] != null &&
                (lines[1].startsWith("B") || lines[1].startsWith("S")) &&
                lines[1].matches("^[BS]\\d+(?::[BS]\\d+)?$");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onContainerAccess(PlayerInteractEvent event) {
        plugin.debug("Container access event: " + event.getAction());
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        plugin.debug("Block clicked: " + event.getClickedBlock().getType());
        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Container)) {
            return;
        }

        // Check if this container is part of a shop
        plugin.debug("Block state: " + block.getState().getClass().getSimpleName());
        Block signBlock = findAttachedSign(block);
        if (signBlock == null) {
            plugin.debug("No attached sign found!");
            return;
        }
        plugin.debug("Attached sign: " + signBlock.getType());

        ChestShop shop = plugin.getShop(signBlock.getLocation());
        plugin.debug("Shop: " + shop);
        if (shop == null) {
            plugin.debug("Shop not found!");
            return;
        }

        Player player = event.getPlayer();

        // Allow access if:
        // 1. Player is the owner
        // 2. Player has chest bypass enabled and permission
        // 3. It's an admin shop and player has admin shop access permission
        plugin.debug("Shop owner: " + shop.getOwnerName() + ", player: " + player.getName() + ", bypass mode:" + plugin.isInChestBypassMode(player));
        plugin.debug("Admin shop access: " + player.hasPermission("chestshop.admin.access"));
        if (shop.getOwnerName().equals(player.getName()) || (player.hasPermission("chestshop.admin.chestbypass") && plugin.isInChestBypassMode(player)) ||
                (shop.isAdminShop() && player.hasPermission("chestshop.admin.access"))) {
            plugin.debug("Access granted!");
            return;
        }

        // Deny access
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        player.sendMessage("§cYou don't have permission to access this shop container!");
    }

    private Block findAttachedSign(Block chestBlock) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {

            Block relative = chestBlock.getRelative(face);

            if (!(relative.getBlockData() instanceof WallSign sign)) continue;

            Block attachedTo = relative.getRelative(sign.getFacing().getOppositeFace());

            if (attachedTo.equals(chestBlock)) {
                return relative;
            }
        }

        return null;
    }



    private void createNewShop(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if sign is attached to a chest
        if (!(block.getBlockData() instanceof WallSign)) {
            return;
        }

        WallSign sign = (WallSign) block.getBlockData();
        Block attachedBlock = block.getRelative(sign.getFacing().getOppositeFace());

        if (!(attachedBlock.getState() instanceof Chest ||
                attachedBlock.getState() instanceof Barrel)) {
            return;
        }

        // Check permission
        if (!player.hasPermission("chestshop.create")) {
            player.sendMessage("§cYou don't have permission to create a shop!");
            event.setCancelled(true);
            return;
        }

        // Check for admin shop
        boolean isAdminShop = event.getLine(0).equalsIgnoreCase("admin");
        if (isAdminShop) {
            if (!player.hasPermission("chestshop.admin.create")) {
                player.sendMessage("§cYou don't have permission to create admin shops!");
                event.setCancelled(true);
                return;
            }
            event.setLine(0, ChestShop.getAdminShopDisplayName());
        } else {
            event.setLine(0, player.getName());
        }

        // Parse prices using the handler
        String priceLine = event.getLine(1);
        ShopSignHandler.PriceInfo prices = plugin.getSignHandler().parsePriceLine(priceLine);

        if (!prices.isValid()) {
            player.sendMessage("§cInvalid price format! Use BX:SX or BX or SX (X = price)");
            event.setCancelled(true);
            return;
        }

        // Parse quantity
        String quantityLine = event.getLine(2);
        int quantity;
        try {
            quantity = Integer.parseInt(quantityLine);
            if (quantity <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid quantity! Must be a positive number.");
            event.setCancelled(true);
            return;
        }

        // Create shop
        ChestShop shop = new ChestShop(player.getName(), block.getLocation(), isAdminShop);
        shop.setQuantity(quantity);
        shop.setBuyPrice(prices.getBuyPrice());
        shop.setSellPrice(prices.getSellPrice());

        // Handle item selection
        String itemLine = event.getLine(3);
        if (itemLine != null && itemLine.equals("?")) {
            shop.setPending(true);
            player.sendMessage("§aRight-click the sign with the item you want to sell!");
        } else if (itemLine != null && !itemLine.isEmpty()) {
            Material material = Material.matchMaterial(itemLine);
            if (material == null) {
                player.sendMessage("§cInvalid item name!");
                event.setCancelled(true);
                return;
            }
            ItemStack shopItem = new ItemStack(material);
            shop.setItem(shopItem);

            // Update sign text with hover information
            if (block.getState() instanceof Sign) {
                plugin.getSignHandler().updateSignText((Sign)block.getState(), shopItem,
                        prices.getBuyPrice(), prices.getSellPrice());
            }
        }

        plugin.addShop(block.getLocation(), shop);
        player.sendMessage("§aShop created successfully!");
    }





    @EventHandler(priority = EventPriority.HIGH)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }

        Block block = event.getClickedBlock();
        ChestShop shop = plugin.getShop(block.getLocation());

        if (shop == null) {
            return;
        }

        Player player = event.getPlayer();

        // Check if shop is broken (has no item but is not pending)
        if (shop.getItem() == null && !shop.isPending()) {
            // Auto-repair the shop
            plugin.repairShop(block.getLocation());
            player.sendMessage("§cThis shop appears to be broken and has been marked for repair.");

            // If player is the owner, tell them how to fix it
            if (shop.getOwnerName().equals(player.getName())) {
                player.sendMessage("§eRight-click the sign with the item you want to sell to fix your shop.");
            }
            event.setCancelled(true);
            return;
        }

        // Handle pending shop item selection
        if (shop.isPending() && shop.getOwnerName().equals(player.getName())) {
            event.setCancelled(true);
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                player.sendMessage("§cHold the item you want to sell in your main hand!");
                return;
            }

            shop.setItem(item.clone());
            shop.setPending(false);
            plugin.addShop(block.getLocation(), shop);

            Sign sign = (Sign) block.getState();
            sign.setLine(3, item.getType().name());
            sign.update();

            player.sendMessage("§aShop item set to: " + item.getType().name());
            return;
        }

        event.setCancelled(true); // Cancel the interaction to prevent sign editing


        // Handle buying/selling based on click type
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleBuying(player, shop);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleSelling(player, shop);
        }
    }
    private void handleBuying(Player player, ChestShop shop) {
        // Check if the shop has a valid item
        if (shop.getItem() == null) {
            player.sendMessage("§cThis shop has no item set! Please contact the shop owner.");
            plugin.getLogger().warning("Shop at " + shop.getLocation() + " owned by " + shop.getOwnerName() + " has no item set!");
            return;
        }

        if (shop.getBuyPrice() < 0) {
            player.sendMessage("§cThis shop is not selling items!");
            return;
        }

        Economy economy = plugin.getEconomy();
        double price = shop.getBuyPrice();
        int quantity = shop.getQuantity();

        // Check if player has enough inventory space
        ItemStack checkItem = shop.getItem().clone();
        checkItem.setAmount(quantity);
        if (!hasEnoughSpace(player, checkItem)) {
            player.sendMessage("§cYour inventory is full! Free up some space first.");
            return;
        }

        // Check if player has enough money (skip if free)
        if (price > 0 && !economy.has(player, price)) {
            player.sendMessage("§cYou don't have enough money! You need: $" + price);
            return;
        }

        // Get the attached container
        Block signBlock = shop.getLocation().getBlock();
        Container container = getAttachedContainer(signBlock);
        if (container == null) {
            player.sendMessage("§cShop container not found!");
            return;
        }

        // Check if shop has enough items
        if (!hasEnoughItems(container, shop.getItem(), quantity, shop)) {
            player.sendMessage("§cShop is out of stock!");
            return;
        }

        // Determine what item to give to the player
        ItemStack itemToGive;
        if (!shop.isAdminShop() && isSpecialItem(shop.getItem().getType())) {
            // For special items in non-admin shops, find the actual item in the container
            itemToGive = findMatchingSpecialItemInContainer(container, shop.getItem().getType(), quantity);
            if (itemToGive == null) {
                // Fallback to a new item if no matching special item found
                itemToGive = new ItemStack(shop.getItem().getType(), quantity);
            }
        } else {
            // For regular items or admin shops, use the shop's item
            itemToGive = shop.getItem().clone();
            itemToGive.setAmount(quantity);
        }

        // Process transaction
        if (price > 0) {
            economy.withdrawPlayer(player, price);
            if (!shop.isAdminShop()) {
                economy.depositPlayer(Bukkit.getOfflinePlayer(shop.getOwnerName()), price);
            }
        }

        // Remove items from container for non-admin shops
        if (!shop.isAdminShop()) {
            removeItemsFromContainer(container, shop.getItem(), quantity);
        }

        // Give items to player
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemToGive);

        // Extra safety check - if somehow items couldn't be added, refund and return items
        if (!leftover.isEmpty()) {
            // Refund the player if they paid
            if (price > 0) {
                economy.depositPlayer(player, price);
            }
            // Return items to container if not admin shop
            if (!shop.isAdminShop()) {
                addItemsToContainer(container, shop.getItem(), quantity);
            }
            player.sendMessage("§cError processing transaction - you have been refunded.");
            return;
        }

        // Success message
        String priceText = price > 0 ? String.format(" for $%.2f", price) : " for FREE";
        player.sendMessage(String.format("§aSuccessfully bought %dx %s%s",
                quantity,
                shop.getItem().getType().name().toLowerCase().replace("_", " "),
                priceText));

        // Notify shop owner if they're online
        if (!shop.isAdminShop()) {
            Player owner = Bukkit.getPlayer(shop.getOwnerName());
            if (owner != null && owner.isOnline()) {
                String notification = String.format(
                        "§a%s bought %dx %s%s",
                        player.getName(),
                        quantity,
                        shop.getItem().getType().name().toLowerCase().replace("_", " "),
                        priceText
                );
                plugin.getNotificationManager().sendNotification(owner, notification);
            }
        }
    }



    private void handleSelling(Player player, ChestShop shop) {
        // Check if the shop has a valid item
        if (shop.getItem() == null) {
            player.sendMessage("§cThis shop has no item set! Please contact the shop owner.");
            plugin.getLogger().warning("Shop at " + shop.getLocation() + " owned by " + shop.getOwnerName() + " has no item set!");
            return;
        }

        if (shop.getSellPrice() < 0) {
            player.sendMessage("§cThis shop is not buying items!");
            return;
        }

        Economy economy = plugin.getEconomy();
        double price = shop.getSellPrice();
        int quantity = shop.getQuantity();

        // Check if player has enough items
        if (!hasEnoughItems(player, shop.getItem(), quantity)) {
            player.sendMessage("§cYou don't have enough items to sell!");
            return;
        }

        // Check if shop owner has enough money (skip if free)
        if (price > 0 && !shop.isAdminShop()) {
            if (!economy.has(Bukkit.getOfflinePlayer(shop.getOwnerName()), price)) {
                player.sendMessage("§cShop owner doesn't have enough money!");
                return;
            }
        }

        // Get the attached container
        Block signBlock = shop.getLocation().getBlock();
        Container container = getAttachedContainer(signBlock);
        if (container == null) {
            player.sendMessage("§cShop container not found!");
            return;
        }

        // Check if container has enough space
        if (!hasEnoughSpaceInContainer(container, shop.getItem(), quantity, shop)) {
            player.sendMessage("§cShop container is full!");
            return;
        }

        // Process transaction
        if (price > 0) {
            if (!shop.isAdminShop()) {
                economy.withdrawPlayer(Bukkit.getOfflinePlayer(shop.getOwnerName()), price);
            }
            economy.depositPlayer(player, price);
        }

        // Handle item transfer
        if (isSpecialItem(shop.getItem().getType())) {
            // For special items, find the actual item in player inventory
            ItemStack actualItem = findFirstSpecialItemInInventory(player, shop.getItem().getType());
            if (actualItem != null) {
                // Remove the specific items from player
                removeSpecificItemsFromPlayer(player, actualItem.getType(), quantity);

                // Add the specific item to container (if not admin shop)
                if (!shop.isAdminShop()) {
                    ItemStack itemToAdd = actualItem.clone();
                    itemToAdd.setAmount(quantity);
                    container.getInventory().addItem(itemToAdd);
                }
            } else {
                // Fallback to standard removal
                removeItemsFromPlayer(player, shop.getItem(), quantity);
                if (!shop.isAdminShop()) {
                    addItemsToContainer(container, shop.getItem(), quantity);
                }
            }
        } else {
            // Standard item handling
            removeItemsFromPlayer(player, shop.getItem(), quantity);
            if (!shop.isAdminShop()) {
                addItemsToContainer(container, shop.getItem(), quantity);
            }
        }

        // Get proper item name
        String itemName = shop.getItem().hasItemMeta() && shop.getItem().getItemMeta().hasDisplayName()
                ? shop.getItem().getItemMeta().getDisplayName()
                : shop.getItem().getType().name().toLowerCase().replace("_", " ");

        // Success message with price text
        String priceText = price > 0 ? String.format(" for $%.2f", price) : " for FREE";
        player.sendMessage(String.format("§aSuccessfully sold %dx %s%s",
                quantity, itemName, priceText));

        // Notify shop owner if they're online and notifications are enabled
        if (!shop.isAdminShop()) {
            Player owner = Bukkit.getPlayer(shop.getOwnerName());
            if (owner != null && owner.isOnline()) {
                String notification = String.format(
                        "§a%s sold %dx %s%s",
                        player.getName(),
                        quantity,
                        itemName,
                        priceText
                );
                plugin.getNotificationManager().sendNotification(owner, notification);
            }
        }
    }



    private ItemStack findMatchingSpecialItemInContainer(Container container, Material specialType, int quantity) {
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && item.getType() == specialType) {
                ItemStack result = item.clone();
                result.setAmount(quantity);
                return result;
            }
        }
        return null;
    }

    private ItemStack findFirstSpecialItemInInventory(Player player, Material specialType) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == specialType) {
                return item.clone();
            }
        }
        return null;
    }

    private void removeSpecificItemsFromPlayer(Player player, Material itemType, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == itemType) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
        player.updateInventory();
    }

    private boolean hasEnoughSpaceInContainer(Container container, ItemStack item, int quantity, ChestShop shop) {
        if (shop.isAdminShop()) {
            return true;
        }

        Material itemType = item.getType();
        boolean isSpecialItem = isSpecialItem(itemType);

        int freeSpace = 0;
        for (ItemStack stack : container.getInventory().getContents()) {
            if (stack == null) {
                freeSpace += item.getMaxStackSize();
            } else if (isSpecialItem) {
                // For special items, only check material type
                if (stack.getType() == itemType) {
                    freeSpace += item.getMaxStackSize() - stack.getAmount();
                }
            } else {
                // For other items, use normal comparison
                if (stack.isSimilar(item)) {
                    freeSpace += item.getMaxStackSize() - stack.getAmount();
                }
            }
        }
        return freeSpace >= quantity;
    }


    private boolean hasEnoughItems(Player player, ItemStack item, int quantity) {
        Material itemType = item.getType();
        boolean isSpecialItem = isSpecialItem(itemType);

        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null) {
                if (isSpecialItem) {
                    // For special items, only check material type
                    if (stack.getType() == itemType) {
                        count += stack.getAmount();
                    }
                } else {
                    // For other items, use normal comparison
                    if (stack.isSimilar(item)) {
                        count += stack.getAmount();
                    }
                }
            }
        }
        return count >= quantity;
    }


    private boolean hasEnoughItems(Container container, ItemStack item, int quantity, ChestShop shop) {
        if (shop.isAdminShop()) {
            return true;
        }

        Material itemType = item.getType();
        boolean isSpecialItem = isSpecialItem(itemType);

        int count = 0;
        for (ItemStack stack : container.getInventory().getContents()) {
            if (stack != null) {
                if (isSpecialItem) {
                    // For special items, only check material type
                    if (stack.getType() == itemType) {
                        count += stack.getAmount();
                    }
                } else {
                    // For other items, use normal comparison
                    if (stack.isSimilar(item)) {
                        count += stack.getAmount();
                    }
                }
            }
        }
        return count >= quantity;
    }

    private boolean hasEnoughSpace(Player player, ItemStack itemToAdd) {
        // Clone the inventory to simulate adding items
        ItemStack[] contents = player.getInventory().getStorageContents().clone();
        ItemStack remaining = itemToAdd.clone();

        // Try to fit the item into existing stacks first
        for (int i = 0; i < contents.length && remaining.getAmount() > 0; i++) {
            ItemStack slot = contents[i];
            if (slot == null) {
                // Empty slot - can fit a full stack
                int toAdd = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                remaining.setAmount(remaining.getAmount() - toAdd);
            } else if (slot.isSimilar(remaining)) {
                // Similar item - check remaining stack space
                int canAdd = slot.getMaxStackSize() - slot.getAmount();
                if (canAdd > 0) {
                    int toAdd = Math.min(remaining.getAmount(), canAdd);
                    remaining.setAmount(remaining.getAmount() - toAdd);
                }
            }
        }

        // If remaining is 0, we found space for everything
        return remaining.getAmount() == 0;
    }

    private boolean hasEnoughSpace(Player player, ItemStack item, int quantity) {
        int freeSpace = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                freeSpace += item.getMaxStackSize();
            } else if (stack.isSimilar(item)) {
                freeSpace += item.getMaxStackSize() - stack.getAmount();
            }
        }
        return freeSpace >= quantity;
    }

    private void removeItemsFromPlayer(Player player, ItemStack itemToRemove, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        Material itemType = itemToRemove.getType();
        boolean isSpecialItem = isSpecialItem(itemType);

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            boolean matches;
            if (isSpecialItem) {
                // For special items, only check material type
                matches = (item.getType() == itemType);
            } else {
                // For other items, use normal comparison
                matches = item.isSimilar(itemToRemove);
            }

            if (matches) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
        player.updateInventory();
    }

    private void removeItemsFromContainer(Container container, ItemStack itemToRemove, int amount) {
        int remaining = amount;
        ItemStack[] contents = container.getInventory().getContents();

        Material itemType = itemToRemove.getType();
        boolean isSpecialItem = isSpecialItem(itemType);

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            boolean matches;
            if (isSpecialItem) {
                // For special items, only check material type
                matches = (item.getType() == itemType);
            } else {
                // For other items, use normal comparison
                matches = item.isSimilar(itemToRemove);
            }

            if (matches) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    container.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }


    // Helper methods
    private boolean isSpecialBucket(Material material) {
        return material.name().endsWith("_BUCKET") && !material.equals(Material.BUCKET);
    }

    /**
     * Determines if an item type needs special handling for shop operations
     * @param material The material to check
     * @return True if the material needs special handling
     */
    private boolean isSpecialItem(Material material) {
        return material == Material.WRITTEN_BOOK || 
               material == Material.FILLED_MAP || 
               material == Material.ENCHANTED_BOOK ||
                (material.name().endsWith("_BUCKET") && !material.equals(Material.BUCKET)) ||
                (material.name().endsWith("_BOOTS")) ||
                (material.name().endsWith("_LEGGINGS")) ||
                (material.name().endsWith("_CHESTPLATE")) ||
                (material.name().endsWith("_HELMET")) ||
                (material.name().endsWith("_SHOVEL")) ||
                (material.name().endsWith("_PICKAXE")) ||
                (material.name().endsWith("_AXE")) ||
                (material.name().endsWith("_HOE")) ||
                material == Material.FISHING_ROD ||
                (material.name().endsWith("_SWORD")) ||
                (material.name().endsWith("_SHIELD")) ||
                (material.name().endsWith("_ELYTRA")) ||
                material == Material.TRIDENT ||
                (material.name().endsWith("_HORSE_ARMOR")) ||
                (material.name().endsWith("_CROSSBOW")) ||
                (material.name().endsWith("_BOW")) ||
                material == Material.MACE ||
                (material.name().startsWith("POTION")) ||
                material == Material.BUNDLE ||
                material == Material.OMINOUS_BOTTLE ||
                material == Material.ELYTRA;


    }

    private void addItemsToContainer(Container container, ItemStack item, int amount) {
        ItemStack toAdd = item.clone();
        toAdd.setAmount(amount);
        container.getInventory().addItem(toAdd);
    }

    private Container getAttachedContainer(Block signBlock) {
        if (!(signBlock.getBlockData() instanceof WallSign)) {
            return null;
        }
        WallSign sign = (WallSign) signBlock.getBlockData();
        Block attachedBlock = signBlock.getRelative(sign.getFacing().getOppositeFace());

        if (attachedBlock.getState() instanceof Container) {
            return (Container) attachedBlock.getState();
        }
        return null;
    }


    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Sign)) {
            return;
        }

        ChestShop shop = plugin.getShop(block.getLocation());
        if (shop == null) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player can remove the shop
        if (!shop.getOwnerName().equals(player.getName()) &&
                !player.hasPermission("chestshop.admin.remove")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to remove this shop!");
            return;
        }

        // Remove the shop
        plugin.removeShop(block.getLocation());
        player.sendMessage("§aShop removed successfully!");
    }





}



