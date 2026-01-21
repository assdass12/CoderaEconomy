package tr.balzach.coderaEconomy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import tr.balzach.coderaEconomy.api.CoderaEconomyAPI;
import tr.balzach.coderaEconomy.commands.*;
import tr.balzach.coderaEconomy.config.ConfigManager;
import tr.balzach.coderaEconomy.currency.CurrencyManager;
import tr.balzach.coderaEconomy.database.DatabaseManager;
import tr.balzach.coderaEconomy.integrations.CoderaPlaceholderExpansion;
import tr.balzach.coderaEconomy.listeners.PlayerListener;
import tr.balzach.coderaEconomy.vault.VaultHook;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CoderaEconomy v2.5.1 - Modern Multi-Currency Economy Plugin
 * FULLY OPTIMIZED & BUG-FREE VERSION WITH PERFECT RELOAD
 */
public final class CoderaEconomy extends JavaPlugin {

    private ConfigManager configManager;
    private CurrencyManager currencyManager;
    private DatabaseManager databaseManager;
    private VaultHook vaultHook;
    private boolean placeholderAPIEnabled = false;

    // Thread-safe pending payments
    private final ConcurrentHashMap<UUID, PendingPayment> pendingPayments = new ConcurrentHashMap<>();

    // Scheduled executor for cleanup tasks
    private ScheduledExecutorService executorService;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        printBanner();

        getLogger().info("Initializing configuration...");
        this.configManager = new ConfigManager(this);

        getLogger().info("Initializing currency system...");
        this.currencyManager = new CurrencyManager(this);

        getLogger().info("Initializing database...");
        this.databaseManager = new DatabaseManager(this);

        if (!setupVault()) {
            getLogger().severe("Vault not found! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (setupPlaceholderAPI()) {
            getLogger().info("PlaceholderAPI integration enabled!");
            placeholderAPIEnabled = true;
        }

        registerCommands();
        registerListeners();
        startCleanupTask();

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info(String.format("Successfully enabled in %dms!", loadTime));
        printLoadedInfo();
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down...");

        // Shutdown executor service properly
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        pendingPayments.clear();

        if (databaseManager != null) {
            getLogger().info("Creating final backup...");
            databaseManager.createBackup();
            databaseManager.close();
        }

        if (vaultHook != null) {
            getServer().getServicesManager().unregister(Economy.class, vaultHook);
        }

        CoderaEconomyAPI.reset();

        getLogger().info("Successfully disabled!");
    }

    private boolean setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        vaultHook = new VaultHook(this);
        getServer().getServicesManager().register(
                Economy.class,
                vaultHook,
                this,
                ServicePriority.Highest
        );

        getLogger().info("Vault integration enabled!");
        return true;
    }

    private boolean setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return false;
        }

        new CoderaPlaceholderExpansion(this).register();
        return true;
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");

        EconomyCommand economyCommand = new EconomyCommand(this);
        Objects.requireNonNull(getCommand("economy")).setExecutor(economyCommand);
        Objects.requireNonNull(getCommand("economy")).setTabCompleter(economyCommand);

        BalanceCommand balanceCommand = new BalanceCommand(this);
        Objects.requireNonNull(getCommand("balance")).setExecutor(balanceCommand);
        Objects.requireNonNull(getCommand("balance")).setTabCompleter(balanceCommand);

        PayCommand payCommand = new PayCommand(this);
        Objects.requireNonNull(getCommand("pay")).setExecutor(payCommand);
        Objects.requireNonNull(getCommand("pay")).setTabCompleter(payCommand);
        Objects.requireNonNull(getCommand("payconfirm")).setExecutor(payCommand);

        BaltopCommand baltopCommand = new BaltopCommand(this);
        Objects.requireNonNull(getCommand("balancetop")).setExecutor(baltopCommand);
        Objects.requireNonNull(getCommand("balancetop")).setTabCompleter(baltopCommand);

        CurrencyCommand currencyCommand = new CurrencyCommand(this);
        Objects.requireNonNull(getCommand("currency")).setExecutor(currencyCommand);
        Objects.requireNonNull(getCommand("currency")).setTabCompleter(currencyCommand);

        getLogger().info("Commands registered successfully!");
    }

    private void registerListeners() {
        getLogger().info("Registering listeners...");
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getLogger().info("Listeners registered successfully!");
    }

    private void startCleanupTask() {
        this.executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CoderaEconomy-Cleanup");
            thread.setDaemon(true);
            return thread;
        });

        executorService.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            int removed = 0;

            for (UUID uuid : pendingPayments.keySet()) {
                PendingPayment payment = pendingPayments.get(uuid);
                if (payment != null && now - payment.timestamp > 60000) {
                    if (pendingPayments.remove(uuid, payment)) {
                        removed++;
                    }
                }
            }

            if (removed > 0) {
                getLogger().fine("Cleaned up " + removed + " expired payment confirmations");
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * FIXED: PERFECT RELOAD - Reloads EVERYTHING properly
     */
    public void reloadPlugin() {
        getLogger().info("═══════════════════════════════════════");
        getLogger().info("Starting complete plugin reload...");
        getLogger().info("═══════════════════════════════════════");

        try {
            // Step 1: Clear all pending payments
            getLogger().info("[1/5] Clearing pending payments...");
            pendingPayments.clear();

            // Step 2: Clear database cache
            getLogger().info("[2/5] Clearing database cache...");
            databaseManager.clearAllCache();

            // Step 3: Reload configuration (this also reloads messages)
            getLogger().info("[3/5] Reloading configuration & messages...");
            configManager.reload();

            // Step 4: Reload currencies
            getLogger().info("[4/5] Reloading currency system...");
            currencyManager.reload();

            // Step 5: Create backup (optional but recommended)
            getLogger().info("[5/5] Creating backup...");
            if (configManager.isBackupEnabled()) {
                databaseManager.createBackup();
            }

            getLogger().info("═══════════════════════════════════════");
            getLogger().info("Plugin reload completed successfully!");
            getLogger().info("═══════════════════════════════════════");
            printReloadInfo();

        } catch (Exception e) {
            getLogger().severe("═══════════════════════════════════════");
            getLogger().severe("RELOAD FAILED!");
            getLogger().severe("Error: " + e.getMessage());
            getLogger().severe("═══════════════════════════════════════");
            e.printStackTrace();
            throw new RuntimeException("Reload failed", e);
        }
    }

    private void printBanner() {
        getLogger().info("╔═══════════════════════════════════════════════════════════╗");
        getLogger().info("║     CoderaEconomy v2.5.1 - Loading...                    ║");
        getLogger().info("║          Multi-Currency Economy System                    ║");
        getLogger().info("║              by Balzach                                   ║");
        getLogger().info("╚═══════════════════════════════════════════════════════════╝");
    }

    private void printLoadedInfo() {
        getLogger().info("╔═══════════════════════════════════════════════════════════╗");
        getLogger().info("║         CoderaEconomy v2.5.1 Loaded                       ║");
        getLogger().info("╠═══════════════════════════════════════════════════════════╣");
        getLogger().info("║ Features:");
        getLogger().info("║  ✓ Multi-Currency System");
        getLogger().info("║  ✓ Pay Confirmation");
        getLogger().info("║  ✓ Bulk Operations");
        getLogger().info("║  ✓ Multi-Language (TR/EN)");
        getLogger().info("║  ✓ Hex Color Support");
        getLogger().info("║  ✓ SQLite Database + WAL Mode");
        getLogger().info("║  ✓ Auto Backup System");
        getLogger().info("║  ✓ Vault Integration");
        getLogger().info("║  " + (placeholderAPIEnabled ? "✓" : "✗") + " PlaceholderAPI Support");
        getLogger().info("║  ✓ Public API");
        getLogger().info("║  ✓ Transaction Logging");
        getLogger().info("║  ✓ Thread-Safe Operations");
        getLogger().info("║  ✓ PERFECT RELOAD SYSTEM");
        getLogger().info("╠═══════════════════════════════════════════════════════════╣");
        getLogger().info(String.format("║ Language: %s", configManager.getLanguage().toUpperCase()));
        getLogger().info(String.format("║ Currencies Loaded: %d", currencyManager.getCurrencies().size()));
        getLogger().info(String.format("║ Default Currency: %s", currencyManager.getDefaultCurrency().getId()));
        getLogger().info("╚═══════════════════════════════════════════════════════════╝");
    }

    private void printReloadInfo() {
        getLogger().info("Current Status:");
        getLogger().info("- Language: " + configManager.getLanguage());
        getLogger().info("- Currencies: " + currencyManager.getCurrencies().size());
        getLogger().info("- Default Currency: " + currencyManager.getDefaultCurrency().getId());
        getLogger().info("- Baltop Enabled: " + configManager.isBaltopEnabled());
        getLogger().info("- Backup Enabled: " + configManager.isBackupEnabled());
        getLogger().info("- Database Cache: Cleared");
        getLogger().info("- Pending Payments: Cleared");
    }

    @NotNull
    public ConfigManager getConfigManager() {
        return configManager;
    }

    @NotNull
    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    @NotNull
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    // Payment confirmation system
    public void addPendingPayment(@NotNull UUID player, @NotNull PendingPayment payment) {
        pendingPayments.put(player, payment);
    }

    public PendingPayment getPendingPayment(@NotNull UUID player) {
        return pendingPayments.get(player);
    }

    public void removePendingPayment(@NotNull UUID player) {
        pendingPayments.remove(player);
    }

    public static class PendingPayment {
        public final UUID target;
        public final String currencyId;
        public final double amount;
        public final double tax;
        public final long timestamp;

        public PendingPayment(UUID target, String currencyId, double amount, double tax) {
            this.target = target;
            this.currencyId = currencyId;
            this.amount = amount;
            this.tax = tax;
            this.timestamp = System.currentTimeMillis();
        }
    }
}