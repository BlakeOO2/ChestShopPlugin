package org.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
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
import org.bukkit.block.BlockFace;
import org.example.ShopSignHandler.PriceInfo;


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

        // Only check for shop if it's attached to a chest
        if (block.getBlockData() instanceof WallSign) {
            WallSign wallSign = (WallSign) block.getBlockData();
            Block attachedBlock = block.getRelative(wallSign.getFacing().getOppositeFace());

            // If sign is attached to a chest, handle it as a potential shop
            if (attachedBlock.getState() instanceof Chest) {
                Location signLoc = block.getLocation();
                ChestShop shop = plugin.getShop(signLoc);

                // If this is an existing shop sign
                if (shop != null) {
                    Player player = event.getPlayer();
                    // Only allow owner or admin in bypass mode to edit
                    if (!shop.getOwnerName().equals(player.getName()) &&
                            !(player.hasPermission("chestshop.admin.bypass") && plugin.isInBypassMode(player))) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot edit this shop sign!");
                        return;
                    }
                }

                // Handle new shop creation
                createNewShop(event);
            }
        }
        // If we get here, it's a regular sign - allow normal editing
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChestAccess(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Chest)) {
            return;
        }

        // Check if this chest is part of a shop
        Block signBlock = findAttachedSign(block);
        if (signBlock == null) return;

        ChestShop shop = plugin.getShop(signBlock.getLocation());
        if (shop == null) {
            return;
        }

        Player player = event.getPlayer();

        // Allow access if:
        // 1. Player is the owner
        // 2. Player has chest bypass enabled and permission
        // 3. It's an admin shop and player has admin shop access permission
        if (shop.getOwnerName().equals(player.getName()) ||
                (player.hasPermission("chestshop.admin.chestbypass") && plugin.isInChestBypassMode(player)) ||
                (shop.isAdminShop() && player.hasPermission("chestshop.admin.access"))) {
            return;
        }

        // Deny access
        event.setCancelled(true);
        player.sendMessage("§cYou don't have permission to access this shop chest!");
    }

    private Block findAttachedSign(Block chestBlock) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = chestBlock.getRelative(face);
            if (relative.getState() instanceof Sign && relative.getBlockData() instanceof WallSign) {
                WallSign sign = (WallSign) relative.getBlockData();
                if (sign.getFacing().getOppositeFace() == face) {
                    return relative;
                }
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

        if (!(attachedBlock.getState() instanceof Chest)) {
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
        if (shop.getBuyPrice() <= 0) {
            player.sendMessage("§cThis shop is not selling items!");
            return;
        }

        Economy economy = plugin.getEconomy();
        double price = shop.getBuyPrice();
        int quantity = shop.getQuantity();

        // Check if player has enough money
        if (!economy.has(player, price)) {
            player.sendMessage("§cYou don't have enough money! You need: " + price);
            return;
        }

        // Get the attached chest
        Block signBlock = shop.getLocation().getBlock();
        Block chestBlock = getAttachedChest(signBlock);
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest)) {
            player.sendMessage("§cShop chest not found!");
            return;
        }

        Chest chest = (Chest) chestBlock.getState();

        // Check if shop has enough items
        if (!hasEnoughItems(chest, shop.getItem(), quantity, shop)) {
            player.sendMessage("§cShop is out of stock!");
            return;
        }

        // Check if player has enough inventory space
        if (!hasEnoughSpace(player, shop.getItem(), quantity)) {
            player.sendMessage("§cYour inventory is full!");
            return;
        }

        // Process transaction
        economy.withdrawPlayer(player, price);
        if (!shop.isAdminShop()) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(shop.getOwnerName()), price);
        }
        // Remove items from chest
        removeItemsFromChest(chest, shop.getItem(), quantity);

        // Give items to player
        ItemStack boughtItems = shop.getItem().clone();
        boughtItems.setAmount(quantity);
        player.getInventory().addItem(boughtItems);

        player.sendMessage("§aSuccessfully bought " + quantity + " items for " + price);

        // Notify shop owner if they're online
        Player owner = Bukkit.getPlayer(shop.getOwnerName());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage("§a" + player.getName() + " bought " + quantity + " items from your shop for " + price);
        }
    }

    private void handleSelling(Player player, ChestShop shop) {
        if (shop.getSellPrice() <= 0) {
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

        // Check if shop owner has enough money
        if (!shop.isAdminShop()) {
            // Check if shop owner has enough money
            if (!economy.has(Bukkit.getOfflinePlayer(shop.getOwnerName()), price)) {
                player.sendMessage("§cShop owner doesn't have enough money!");
                return;
            }
        }

        // Get the attached chest
        Block signBlock = shop.getLocation().getBlock();
        Block chestBlock = getAttachedChest(signBlock);
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest)) {
            player.sendMessage("§cShop chest not found!");
            return;
        }

        Chest chest = (Chest) chestBlock.getState();

        // Check if chest has enough space
        if (!hasEnoughSpace(chest, shop.getItem(), quantity, shop)) {
            player.sendMessage("§cShop chest is full!");
            return;
        }

        // Process transaction
        if (!shop.isAdminShop()) {
            economy.withdrawPlayer(Bukkit.getOfflinePlayer(shop.getOwnerName()), price);
        }
        economy.depositPlayer(player, price); // Player always gets money, even from admin shops

        // Remove items from player
        removeItemsFromPlayer(player, shop.getItem(), quantity);

        // Add items to chest
        addItemsToChest(chest, shop.getItem(), quantity);

        player.sendMessage("§aSuccessfully sold " + quantity + " items for " + price);

        // Notify shop owner if they're online
        if (!shop.isAdminShop()) {  // Only notify for non-admin shops
            Player owner = Bukkit.getPlayer(shop.getOwnerName());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§a" + player.getName() + " sold " + quantity + " items to your shop for " + price);
            }
        }
    }


    private boolean hasEnoughItems(Player player, ItemStack item, int quantity) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(item)) {
                count += stack.getAmount();
            }
        }
        return count >= quantity;
    }

    private boolean hasEnoughItems(Chest chest, ItemStack item, int quantity, ChestShop shop) {
        if (shop.isAdminShop()) {
            return true; // Admin shops have unlimited stock
        }
        int count = 0;
        for (ItemStack stack : chest.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(item)) {
                count += stack.getAmount();
            }
        }
        return count >= quantity;
    }

    private boolean hasEnoughSpace(Chest chest, ItemStack item, int quantity, ChestShop shop) {
        if (shop.isAdminShop()) {
            return true; // Admin shops have unlimited space
        }
        int freeSpace = 0;
        for (ItemStack stack : chest.getInventory().getContents()) {
            if (stack == null) {
                freeSpace += item.getMaxStackSize();
            } else if (stack.isSimilar(item)) {
                freeSpace += item.getMaxStackSize() - stack.getAmount();
            }
        }
        return freeSpace >= quantity;
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

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(itemToRemove)) {
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

    private void removeItemsFromChest(Chest chest, ItemStack itemToRemove, int amount) {
        int remaining = amount;
        ItemStack[] contents = chest.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(itemToRemove)) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    chest.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    private void addItemsToChest(Chest chest, ItemStack item, int amount) {
        ItemStack toAdd = item.clone();
        toAdd.setAmount(amount);
        chest.getInventory().addItem(toAdd);
    }

    private Block getAttachedChest(Block signBlock) {
        if (!(signBlock.getBlockData() instanceof WallSign)) {
            return null;
        }
        WallSign sign = (WallSign) signBlock.getBlockData();
        return signBlock.getRelative(sign.getFacing().getOppositeFace());
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



