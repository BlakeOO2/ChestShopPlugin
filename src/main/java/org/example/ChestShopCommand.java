package org.example;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

public class ChestShopCommand implements CommandExecutor, TabCompleter {
    private final ChestShopPlugin plugin;

    public ChestShopCommand(ChestShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6=== ChestShop Commands ===");
            sender.sendMessage("§7/cs info §f- Get item name for shop creation");
            sender.sendMessage("§7/cs help §f- Show this help message");
            if (sender.hasPermission("chestshop.admin.bypass")) {
                sender.sendMessage("§7/cs bypass §f- Toggle shop sign edit bypass mode");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                return handleInfoCommand(sender);
            case "help":
                return handleHelpCommand(sender);
            case "bypass":
                return handleBypassCommand(sender);
            case "admin":
                return handleAdminCommand(sender, args);
            case "chestbypass":
                return handleChestBypassCommand(sender);
            case "notifications":
            case "notify":
                return handleNotificationCommand(sender);
            case "setowner":
                return handleSetOwnerCommand(sender, args);
            case "change":
                return handleChangeCommand(sender, args);
            case "deleteshop":
                return removeShopCommand(sender, args);
            default:
                sender.sendMessage("§cUnknown command. Type /cs help for help.");
                return true;
        }
    }
    private boolean handleNotificationCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        plugin.getNotificationManager().toggleNotifications(player);
        return true;
    }

    private boolean handleChestBypassCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!sender.hasPermission("chestshop.admin.chestbypass")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        Player player = (Player) sender;
        boolean bypassing = plugin.toggleChestBypass(player);
        player.sendMessage(bypassing ?
                "§aChest protection bypass mode §2ENABLED" :
                "§cChest protection bypass mode §4DISABLED");
        return true;
    }


    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chestshop.admin.create")) {
            sender.sendMessage("§cYou don't have permission to manage admin shops!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /cs admin <displayname>");
            return true;
        }

        // Combine remaining args for the name
        String displayName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ChestShop.setAdminShopDisplayName(displayName);
        sender.sendMessage("§aAdmin shop display name set to: §f" + displayName);
        return true;
    }

    private boolean handleInfoCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            player.sendMessage("§cHold an item in your main hand!");
            return true;
        }

        player.sendMessage("§6=== Item Info ===");
        player.sendMessage("§7Item name: §f" + item.getType().name());
        return true;
    }

    private boolean handleHelpCommand(CommandSender sender) {
        sender.sendMessage("§6=== ChestShop Help ===");
        sender.sendMessage("§7Create a shop:");
        sender.sendMessage("§f1. Place a chest or barrel");
        sender.sendMessage("§f2. Attach a sign to the container");
        sender.sendMessage("§f3. Line 1: §7Automatically set to your name");
        sender.sendMessage("§f4. Line 2: §7B<price> or B<price>:S<price>");
        sender.sendMessage("§f5. Line 3: §7Quantity of items");
        sender.sendMessage("§f6. Line 4: §7Item name or ? to set later");
        sender.sendMessage("§7Commands:");
        sender.sendMessage("§f/cs info §7- Get item name for shop creation");
        sender.sendMessage("§f/cs notifications §7- Toggle shop notifications");
        if (sender.hasPermission("chestshop.admin.bypass")) {
            sender.sendMessage("§f/cs bypass §7- Toggle sign edit bypass mode");
        }
        sender.sendMessage("§7Usage:");
        sender.sendMessage("§f- Right-click sign to buy");
        sender.sendMessage("§f- Left-click sign to sell");
        return true;
    }

    private boolean handleBypassCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!sender.hasPermission("chestshop.admin.bypass")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        Player player = (Player) sender;
        boolean bypassing = plugin.toggleBypass(player);
        player.sendMessage(bypassing ?
                "§aShop sign edit bypass mode §2ENABLED" :
                "§cShop sign edit bypass mode §4DISABLED");
        return true;
    }


    private boolean removeShopCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return false;


        if (args.length < 1) {
            sender.sendMessage("§cUsage: /cs deleteshop");
            return true;
        }



        // Get the sign the player is looking at
        Block targetBlock = player.getTargetBlock(null, 5);
        if (!(targetBlock.getState() instanceof Sign sign)) {
            player.sendMessage("§cYou must be looking at a shop sign!");
            return true;
        }

        // Check if it's a shop
        ChestShop shop = plugin.getShop(targetBlock.getLocation());
        if (shop == null) {
            player.sendMessage("§cThis is not a shop sign!");
            return true;
        }

        // Check ownership based on the first line of the sign
        String ownerName = sign.getLine(0);
        if (( !ownerName.equals(player.getName()) && !player.hasPermission("chestshop.admin.setowner")) || player.hasPermission("chestshop.delete.others")) {
            player.sendMessage("§cYou can only delete your own shops!");
            return true;
        }

        plugin.removeShop(targetBlock.getLocation());
        targetBlock.breakNaturally();

        player.sendMessage("§aShop has been removed successfully!");
        return true;


    }





    private boolean handleSetOwnerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (!sender.hasPermission("chestshop.admin.setowner")) {
            sender.sendMessage("§cYou don't have permission to set shop owners!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /cs setowner <player name>");
            return true;
        }

        // Get the sign the player is looking at
        Block targetBlock = player.getTargetBlock(null, 5);
        if (!(targetBlock.getState() instanceof Sign sign)) {
            player.sendMessage("§cYou must be looking at a shop sign!");
            return true;
        }

        // Check if it's a shop
        ChestShop shop = plugin.getShop(targetBlock.getLocation());
        if (shop == null) {
            player.sendMessage("§cThis is not a shop sign!");
            return true;
        }

        // Set the new owner
        String newOwner = args[1];

        // Update the shop object
        shop.setOwnerName(newOwner);

        // Update the sign
        sign.setLine(0, newOwner);
        sign.update();

        // Save the updated shop to the plugin's data
        plugin.addShop(targetBlock.getLocation(), shop);

        // Force a data save
        plugin.saveShopData(); // We'll need to add this method to ChestShopPlugin

        player.sendMessage("§aShop owner has been set to: " + newOwner);
        return true;
    }


    private boolean handleChangeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /cs change <line: 2|3|4> <text>");
            return true;
        }

        // Parse line number
        int line;
        try {
            line = Integer.parseInt(args[1]);
            if (line < 2 || line > 4) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid line number! Must be 2, 3, or 4");
            return true;
        }

        // Get the text (combine remaining args)
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // Get the sign the player is looking at
        Block targetBlock = player.getTargetBlock(null, 5);
        if (!(targetBlock.getState() instanceof Sign sign)) {
            player.sendMessage("§cYou must be looking at a shop sign!");
            return true;
        }

        // Check if it's a shop
        ChestShop shop = plugin.getShop(targetBlock.getLocation());
        if (shop == null) {
            player.sendMessage("§cThis is not a shop sign!");
            return true;
        }

        // Check ownership based on the first line of the sign
        String ownerName = sign.getLine(0);
        if (!ownerName.equals(player.getName()) && !player.hasPermission("chestshop.admin.setowner")) {
            player.sendMessage("§cYou can only modify your own shops!");
            return true;
        }

        // Validate the new text based on the line number
        boolean isValid = switch (line) {
            case 2 -> validatePriceLine(text);
            case 3 -> validateQuantityLine(text);
            case 4 -> validateItemLine(text);
            default -> false;
        };

        if (!isValid) {
            switch (line) {
                case 2 -> player.sendMessage("§cInvalid price format! Use BX:SX, BFREE:SX, BX:SFREE, or similar");
                case 3 -> player.sendMessage("§cInvalid quantity! Must be a positive number");
                case 4 -> player.sendMessage("§cInvalid item name! Use a valid item name or ?");
            }
            return true;
        }

        // Update the shop based on the line
        updateShopLine(shop, sign, line, text);
        sign.update();
        plugin.addShop(targetBlock.getLocation(), shop);

        player.sendMessage("§aShop has been updated successfully!");
        return true;
    }

    private boolean validatePriceLine(String text) {
        // First check if it's a valid format
        if (!text.matches("^B(?:FREE|\\d+(?:\\.\\d+)?)?(?::S(?:FREE|\\d+(?:\\.\\d+)?)?)?$") &&
                !text.matches("^S(?:FREE|\\d+(?:\\.\\d+)?)?(?::B(?:FREE|\\d+(?:\\.\\d+)?)?)?$")) {
            return false;
        }

        return plugin.getSignHandler().parsePriceLine(text).isValid();
    }

    private boolean validateQuantityLine(String text) {
        try {
            int quantity = Integer.parseInt(text);
            return quantity > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validateItemLine(String text) {
        return text.equals("?") || Material.matchMaterial(text) != null;
    }

    private void updateShopLine(ChestShop shop, Sign sign, int line, String text) {
        switch (line) {
            case 2 -> {
                ShopSignHandler.PriceInfo prices = plugin.getSignHandler().parsePriceLine(text);
                shop.setBuyPrice(prices.getBuyPrice());
                shop.setSellPrice(prices.getSellPrice());
                sign.setLine(1, text);
            }
            case 3 -> {
                shop.setQuantity(Integer.parseInt(text));
                sign.setLine(2, text);
            }
            case 4 -> {
                if (text.equals("?")) {
                    shop.setPending(true);
                    shop.setItem(null);
                } else {
                    Material material = Material.matchMaterial(text);
                    if (material != null) {
                        shop.setItem(new ItemStack(material));
                        shop.setPending(false);
                    }
                }
                sign.setLine(3, text);
            }
        }
    }
    private void sendChangeUsage(Player player) {
        player.sendMessage("§6=== ChestShop Change Command ===");
        player.sendMessage("§7Usage: /cs change <line> <text>");
        player.sendMessage("§7Line 2: §fBX:SX, BFREE:SX, BX:SFREE (prices)");
        player.sendMessage("§7Line 3: §fQuantity (positive number)");
        player.sendMessage("§7Line 4: §fItem name or ?");
        player.sendMessage("§7Examples:");
        player.sendMessage("§f/cs change 2 B10:S5");
        player.sendMessage("§f/cs change 2 BFREE:S10");
        player.sendMessage("§f/cs change 3 64");
        player.sendMessage("§f/cs change 4 DIAMOND");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "info", "help", "notifications", "notify", "change", "deleteshop"
            ));
            if (sender.hasPermission("chestshop.admin.bypass")) {
                completions.add("bypass");
            }
            if (sender.hasPermission("chestshop.admin.setowner")) {
                completions.add("setowner");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("change")) {
            return Arrays.asList("2", "3", "4");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("change")) {
            switch (args[1]) {
                case "2":
                    return Arrays.asList("B<price>:S<price>", "BFREE:S<price>", "B<price>:SFREE", "BFREE", "SFREE");
                case "3":
                    return Arrays.asList("1", "16", "32", "64");
                case "4":
                    return Arrays.asList("?", "DIAMOND", "IRON_INGOT", "GOLD_INGOT");
            }
        }
        return new ArrayList<>();
    }
}

