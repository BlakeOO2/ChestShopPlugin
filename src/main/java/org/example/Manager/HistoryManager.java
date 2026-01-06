package org.example.Manager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.example.ChestShopPlugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryManager {
    private final ChestShopPlugin plugin;
    private final File historyFolder;

    public HistoryManager(ChestShopPlugin plugin) {
        this.plugin = plugin;
        this.historyFolder = new File(plugin.getDataFolder(), "history");

        if (!historyFolder.exists()) {
            historyFolder.mkdirs();
        }
    }

    public void addTransaction(
            UUID owner,
            boolean buy,
            UUID customer,
            int quantity,
            double price,
            Material material,
            boolean timestamp

    ) {
        File file = new File(historyFolder, owner.toString() + ".txt");

        String time = timestamp ? new SimpleDateFormat("HH:mm MM/dd/yyyy").format(new Date()) : "";

        String customerName = Bukkit.getOfflinePlayer(customer).getName();


        String line = String.format(
                "%s%s %s %d %s for %.2f",
                time,
                customerName == null ? " " + customer.toString() : " " + customerName,
                buy ? "bought" : "sold",
                quantity,
                material.name().toLowerCase(),
                price
        );

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line + "\n");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write history file: " + e.getMessage());
        }
    }

    public List<String> getHistory(UUID owner) {

        File file = new File(historyFolder, owner.toString() + ".txt");
        List<String> result = new ArrayList<>();

        if (!file.exists()) {
            return result;
        }

        // Keeps insertion order while counting duplicates
        Map<String, Integer> counts = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                counts.put(line, counts.getOrDefault(line, 0) + 1);
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read history file: " + e.getMessage());
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String line = entry.getKey();
            int count = entry.getValue();

            if (count > 1) {
                result.add(count + " times " + line);
            } else {
                result.add(line);
            }
        }

        return result;
    }

}
