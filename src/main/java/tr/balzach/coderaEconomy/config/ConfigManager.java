package tr.balzach.coderaEconomy.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Modern configuration manager with multi-language support
 * FULLY FIXED VERSION - Reload now works perfectly
 */
public class ConfigManager {

    private final CoderaEconomy plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private String language;

    // Database settings
    private boolean backupEnabled;
    private int backupInterval;
    private int keepBackups;

    // Baltop settings
    private boolean baltopEnabled;
    private int baltopEntriesPerPage;
    private int baltopUpdateInterval;

    private final Map<String, String> messageCache = new HashMap<>();

    public ConfigManager(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * FIXED: Complete reload implementation
     */
    public void reload() {
        plugin.getLogger().info("Reloading configuration...");

        // Step 1: Clear all caches FIRST
        messageCache.clear();

        // Step 2: Save and reload main config
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Step 3: Get language setting
        this.language = config.getString("language", "tr").toLowerCase();
        plugin.getLogger().info("Language set to: " + language);

        // Step 4: Reload messages file
        loadMessages();

        // Step 5: Reload all settings
        loadSettings();

        plugin.getLogger().info("Configuration reloaded successfully!");
    }

    /**
     * FIXED: Completely rewritten message loading system
     */
    private void loadMessages() {
        plugin.getLogger().info("Loading messages for language: " + language);

        String fileName = "messages-" + language + ".yml";
        File messagesFile = new File(plugin.getDataFolder(), fileName);

        // Create file if not exists
        if (!messagesFile.exists()) {
            plugin.saveResource(fileName, false);
            plugin.getLogger().info("Created new message file: " + fileName);
        }

        try {
            // Force reload from disk - don't use cached version
            this.messages = YamlConfiguration.loadConfiguration(messagesFile);

            // Load defaults from jar
            try (InputStream defaultStream = plugin.getResource(fileName)) {
                if (defaultStream != null) {
                    YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8)
                    );
                    messages.setDefaults(defaultMessages);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not load default messages", e);
            }

            // Cache all messages recursively
            cacheMessagesRecursive("", messages);

            plugin.getLogger().info("Loaded " + messageCache.size() + " messages from: " + fileName);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load messages file: " + fileName, e);
        }
    }

    /**
     * Recursively cache all messages with color codes applied
     */
    private void cacheMessagesRecursive(@NotNull String parentPath, @NotNull ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            Object value = section.get(key);

            if (value instanceof ConfigurationSection) {
                cacheMessagesRecursive(fullPath, (ConfigurationSection) value);
            } else if (value instanceof List<?>) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    String listValue = String.valueOf(list.get(i));
                    messageCache.put(fullPath + "." + i, ColorUtil.colorize(listValue));
                }
            } else if (value != null) {
                messageCache.put(fullPath, ColorUtil.colorize(String.valueOf(value)));
            }
        }
    }

    /**
     * Load all settings from config
     */
    private void loadSettings() {
        backupEnabled = config.getBoolean("database.backup.enabled", true);
        backupInterval = config.getInt("database.backup.interval", 3600);
        keepBackups = config.getInt("database.backup.keep-backups", 5);

        baltopEnabled = config.getBoolean("baltop.enabled", true);
        baltopEntriesPerPage = Math.max(1, config.getInt("baltop.entries-per-page", 10));
        baltopUpdateInterval = config.getInt("baltop.update-interval", 300);

        plugin.getLogger().info("Settings loaded - Baltop: " + baltopEnabled + ", Backup: " + backupEnabled);
    }

    @NotNull
    public String getMessage(@NotNull String path, boolean withPrefix) {
        String message = messageCache.getOrDefault(path, "[MISSING: " + path + "]");

        if (withPrefix && !path.equals("prefix")) {
            String prefix = messageCache.getOrDefault("prefix", "");
            return prefix + message;
        }

        return message;
    }

    @NotNull
    public String getMessage(@NotNull String path) {
        return getMessage(path, true);
    }

    @NotNull
    public String getMessage(@NotNull String path, @NotNull Map<String, String> placeholders) {
        return getMessage(path, placeholders, true);
    }

    @NotNull
    public String getMessage(@NotNull String path, @NotNull Map<String, String> placeholders, boolean withPrefix) {
        String message = getMessage(path, withPrefix);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return message;
    }

    @NotNull
    public String colorize(@NotNull String text) {
        return ColorUtil.colorize(text);
    }

    @NotNull
    public List<String> getPlayerHelpMessages() {
        return List.of(
                messageCache.getOrDefault("help.player.0", ""),
                messageCache.getOrDefault("help.player.1", ""),
                messageCache.getOrDefault("help.player.2", ""),
                messageCache.getOrDefault("help.player.3", "")
        );
    }

    @NotNull
    public List<String> getAdminHelpMessages() {
        return List.of(
                messageCache.getOrDefault("help.admin.0", ""),
                messageCache.getOrDefault("help.admin.1", ""),
                messageCache.getOrDefault("help.admin.2", ""),
                messageCache.getOrDefault("help.admin.3", ""),
                messageCache.getOrDefault("help.admin.4", ""),
                messageCache.getOrDefault("help.admin.5", "")
        );
    }

    /**
     * Gets decimal places from default currency config
     */
    public int getDecimalPlaces() {
        return plugin.getCurrencyManager().getDefaultCurrency().getDecimalPlaces();
    }

    /**
     * Formats currency with default currency
     */
    @NotNull
    public String formatCurrency(double amount) {
        return plugin.getCurrencyManager().getDefaultCurrency().format(amount);
    }

    /**
     * Gets plural currency name
     */
    @NotNull
    public String getCurrencyNamePlural() {
        return plugin.getCurrencyManager().getDefaultCurrency().getNamePlural();
    }

    /**
     * Gets singular currency name
     */
    @NotNull
    public String getCurrencyNameSingular() {
        return plugin.getCurrencyManager().getDefaultCurrency().getNameSingular();
    }

    /**
     * Validates balance for default currency
     */
    public boolean isValidBalance(double amount) {
        return plugin.getCurrencyManager().getDefaultCurrency().isValidBalance(amount);
    }

    // Getters
    @NotNull
    public String getLanguage() {
        return language;
    }

    public boolean isBackupEnabled() {
        return backupEnabled;
    }

    public int getBackupInterval() {
        return backupInterval;
    }

    public int getKeepBackups() {
        return keepBackups;
    }

    public boolean isBaltopEnabled() {
        return baltopEnabled;
    }

    public int getBaltopEntriesPerPage() {
        return baltopEntriesPerPage;
    }

    public int getBaltopUpdateInterval() {
        return baltopUpdateInterval;
    }
}