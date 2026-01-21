package tr.balzach.coderaEconomy.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;
import tr.balzach.coderaEconomy.database.DatabaseManager.BalanceEntry;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for CoderaEconomy with multi-currency support
 *
 * Usage:
 * <pre>
 * CoderaEconomyAPI api = CoderaEconomyAPI.getInstance();
 * if (api != null) {
 *     // Get balance for default currency
 *     double balance = api.getBalance(player.getUniqueId());
 *
 *     // Get balance for specific currency
 *     double goldBalance = api.getBalance(player.getUniqueId(), "gold");
 *
 *     // Add money
 *     api.addBalance(player.getUniqueId(), player.getName(), "lira", 100);
 *
 *     // Get all currencies
 *     Collection<Currency> currencies = api.getCurrencies();
 * }
 * </pre>
 */
public class CoderaEconomyAPI {

    private static CoderaEconomyAPI instance;
    private final CoderaEconomy plugin;

    private CoderaEconomyAPI(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets API instance
     */
    @Nullable
    public static CoderaEconomyAPI getInstance() {
        if (instance == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("CoderaEconomy");
            if (plugin instanceof CoderaEconomy) {
                instance = new CoderaEconomyAPI((CoderaEconomy) plugin);
            }
        }
        return instance;
    }

    /**
     * Resets API instance (internal use only)
     */
    public static void reset() {
        instance = null;
    }

    // ═══════════════════ BALANCE OPERATIONS (DEFAULT CURRENCY) ═══════════════════

    /**
     * Gets balance for default currency
     */
    public double getBalance(@NotNull UUID uuid) {
        return getBalance(uuid, plugin.getCurrencyManager().getDefaultCurrency().getId());
    }

    @NotNull
    public CompletableFuture<Double> getBalanceAsync(@NotNull UUID uuid) {
        return getBalanceAsync(uuid, plugin.getCurrencyManager().getDefaultCurrency().getId());
    }

    public boolean setBalance(@NotNull UUID uuid, @NotNull String username, double amount) {
        return setBalance(uuid, username, plugin.getCurrencyManager().getDefaultCurrency().getId(), amount);
    }

    @NotNull
    public CompletableFuture<Boolean> setBalanceAsync(@NotNull UUID uuid, @NotNull String username, double amount) {
        return setBalanceAsync(uuid, username, plugin.getCurrencyManager().getDefaultCurrency().getId(), amount);
    }

    public boolean addBalance(@NotNull UUID uuid, @NotNull String username, double amount) {
        return addBalance(uuid, username, plugin.getCurrencyManager().getDefaultCurrency().getId(), amount);
    }

    public boolean removeBalance(@NotNull UUID uuid, @NotNull String username, double amount) {
        return removeBalance(uuid, username, plugin.getCurrencyManager().getDefaultCurrency().getId(), amount);
    }

    // ═══════════════════ BALANCE OPERATIONS (SPECIFIC CURRENCY) ═══════════════════

    /**
     * Gets balance for specific currency
     */
    public double getBalance(@NotNull UUID uuid, @NotNull String currencyId) {
        return plugin.getDatabaseManager().getBalance(uuid, currencyId);
    }

    @NotNull
    public CompletableFuture<Double> getBalanceAsync(@NotNull UUID uuid, @NotNull String currencyId) {
        return plugin.getDatabaseManager().getBalanceAsync(uuid, currencyId);
    }

    public boolean setBalance(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        return plugin.getDatabaseManager().setBalance(uuid, username, currencyId, amount);
    }

    @NotNull
    public CompletableFuture<Boolean> setBalanceAsync(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        return plugin.getDatabaseManager().setBalanceAsync(uuid, username, currencyId, amount);
    }

    public boolean addBalance(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        return plugin.getDatabaseManager().addBalance(uuid, username, currencyId, amount);
    }

    public boolean removeBalance(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        return plugin.getDatabaseManager().removeBalance(uuid, username, currencyId, amount);
    }

    // ═══════════════════ ACCOUNT MANAGEMENT ═══════════════════

    public boolean hasAccount(@NotNull UUID uuid) {
        return plugin.getDatabaseManager().hasAccount(uuid);
    }

    public boolean createAccount(@NotNull UUID uuid, @NotNull String username) {
        return plugin.getDatabaseManager().createAccount(uuid, username);
    }

    // ═══════════════════ CURRENCY MANAGEMENT ═══════════════════

    /**
     * Gets all available currencies
     */
    @NotNull
    public Collection<Currency> getCurrencies() {
        return plugin.getCurrencyManager().getCurrencies();
    }

    /**
     * Gets a specific currency
     */
    @Nullable
    public Currency getCurrency(@NotNull String id) {
        return plugin.getCurrencyManager().getCurrency(id);
    }

    /**
     * Gets the default currency
     */
    @NotNull
    public Currency getDefaultCurrency() {
        return plugin.getCurrencyManager().getDefaultCurrency();
    }

    /**
     * Checks if a currency exists
     */
    public boolean hasCurrency(@NotNull String id) {
        return plugin.getCurrencyManager().hasCurrency(id);
    }

    // ═══════════════════ LEADERBOARD ═══════════════════

    /**
     * Gets top balances for default currency
     */
    @NotNull
    public List<BalanceEntry> getTopBalances(int limit, int offset) {
        return getTopBalances(plugin.getCurrencyManager().getDefaultCurrency().getId(), limit, offset);
    }

    /**
     * Gets top balances for specific currency
     */
    @NotNull
    public List<BalanceEntry> getTopBalances(@NotNull String currencyId, int limit, int offset) {
        return plugin.getDatabaseManager().getTopBalances(currencyId, limit, offset);
    }

    public int getTotalPlayers() {
        return plugin.getDatabaseManager().getTotalPlayers();
    }

    // ═══════════════════ FORMATTING ═══════════════════

    /**
     * Formats amount with default currency
     */
    @NotNull
    public String formatCurrency(double amount) {
        return plugin.getCurrencyManager().getDefaultCurrency().format(amount);
    }

    /**
     * Formats amount with specific currency
     */
    @NotNull
    public String formatCurrency(double amount, @NotNull String currencyId) {
        Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
        return currency != null ? currency.format(amount) : String.valueOf(amount);
    }

    // ═══════════════════ VALIDATION ═══════════════════

    /**
     * Validates balance for default currency
     */
    public boolean isValidBalance(double amount) {
        return plugin.getCurrencyManager().getDefaultCurrency().isValidBalance(amount);
    }

    /**
     * Validates balance for specific currency
     */
    public boolean isValidBalance(double amount, @NotNull String currencyId) {
        Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
        return currency != null && currency.isValidBalance(amount);
    }
}