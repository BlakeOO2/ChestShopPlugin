package org.example;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NotificationManager {
    private final ChestShopPlugin plugin;
    private final Set<UUID> disabledNotifications;
    private final File configFile;
    private final YamlConfiguration config;

    public NotificationManager(ChestShopPlugin plugin) {
        this.plugin = plugin;
        this.disabledNotifications = new HashSet<>();
        this.configFile = new File(plugin.getDataFolder(), "notifications.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);
        loadData();
    }

    public void toggleNotifications(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabledNotifications.contains(uuid)) {
            disabledNotifications.remove(uuid);
            player.sendMessage("§aShop notifications enabled!");
        } else {
            disabledNotifications.add(uuid);
            player.sendMessage("§cShop notifications disabled!");
        }
        saveData();
    }

    public boolean hasNotificationsEnabled(UUID uuid) {
        return !disabledNotifications.contains(uuid);
    }

    public void sendNotification(Player player, String message) {
        if (player != null && player.isOnline() && hasNotificationsEnabled(player.getUniqueId())) {
            player.sendMessage(message);
        }
    }

    private void loadData() {
        disabledNotifications.clear();
        if (config.contains("disabled-notifications")) {
            config.getStringList("disabled-notifications")
                    .forEach(uuid -> disabledNotifications.add(UUID.fromString(uuid)));
        }
    }

    private void saveData() {
        config.set("disabled-notifications",
                disabledNotifications.stream()
                        .map(UUID::toString)
                        .toList());
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save notification preferences: " + e.getMessage());
        }
    }
}
