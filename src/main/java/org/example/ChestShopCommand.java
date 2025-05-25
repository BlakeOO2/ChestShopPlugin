package org.example;

import org.bukkit.Material;
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "info",
                    "help",
                    "notifications",
                    "notify"
            ));
            if (sender.hasPermission("chestshop.admin.bypass")) {
                completions.add("bypass");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
