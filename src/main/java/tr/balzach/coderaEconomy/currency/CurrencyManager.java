package tr.balzach.coderaEconomy.currency;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tr.balzach.coderaEconomy.CoderaEconomy;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages all currencies in the economy system
 * FIXED VERSION - Perfect reload support
 */
public class CurrencyManager {

    private final CoderaEconomy plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();
    private Currency defaultCurrency;

    public CurrencyManager(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
        loadCurrencies();
    }

    /**
     * FIXED: Perfect reload - clears old data and reloads everything
     */
    public void reload() {
        plugin.getLogger().info("Reloading currency system...");

        // Clear old data
        currencies.clear();
        defaultCurrency = null;

        // Reload from config
        loadCurrencies();

        plugin.getLogger().info("Currency system reloaded successfully!");
        plugin.getLogger().info("- Loaded currencies: " + currencies.size());
        plugin.getLogger().info("- Default currency: " + (defaultCurrency != null ? defaultCurrency.getId() : "NONE"));
    }

    public void loadCurrencies() {
        // Get fresh config reference
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection currenciesSection = config.getConfigurationSection("currencies");

        if (currenciesSection == null) {
            plugin.getLogger().warning("No currencies found in config! Creating default currency...");
            createDefaultCurrency();
            return;
        }

        boolean hasDefault = false;
        int loadedCount = 0;

        for (String id : currenciesSection.getKeys(false)) {
            try {
                Currency currency = loadCurrency(id, currenciesSection.getConfigurationSection(id));
                if (currency != null) {
                    currencies.put(id.toLowerCase(), currency);
                    loadedCount++;

                    if (currency.isDefault()) {
                        if (hasDefault) {
                            plugin.getLogger().warning("Multiple default currencies found! Using first one: " + defaultCurrency.getId());
                        } else {
                            defaultCurrency = currency;
                            hasDefault = true;
                        }
                    }

                    plugin.getLogger().info("Loaded currency: " + id + " (" + currency.getDisplayName() + ")" +
                            (currency.isDefault() ? " [DEFAULT]" : ""));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load currency: " + id, e);
            }
        }

        if (currencies.isEmpty()) {
            plugin.getLogger().warning("No valid currencies loaded! Creating default currency...");
            createDefaultCurrency();
            return;
        }

        if (defaultCurrency == null) {
            plugin.getLogger().warning("No default currency set! Using first currency as default.");
            if (!currencies.isEmpty()) {
                defaultCurrency = currencies.values().iterator().next();
                plugin.getLogger().info("Set default currency to: " + defaultCurrency.getId());
            } else {
                plugin.getLogger().severe("CRITICAL: No currencies available! Creating emergency default...");
                createDefaultCurrency();
            }
        }

        plugin.getLogger().info("Currency loading complete: " + loadedCount + " currencies loaded");
    }

    @Nullable
    private Currency loadCurrency(@NotNull String id, @Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String displayName = section.getString("display-name", id);
        String symbol = section.getString("symbol", "$");
        String nameSingular = section.getString("name-singular", "Dollar");
        String namePlural = section.getString("name-plural", "Dollars");
        String format = section.getString("format", "%amount% %symbol%");
        int decimalPlaces = section.getInt("decimal-places", 2);
        double starterBalance = section.getDouble("starter-balance", 1000.0);
        double minBalance = section.getDouble("min-balance", 0.0);
        double maxBalance = section.getDouble("max-balance", -1.0);
        boolean payEnabled = section.getBoolean("pay.enabled", true);
        double payMinAmount = section.getDouble("pay.min-amount", 1.0);
        double payMaxAmount = section.getDouble("pay.max-amount", -1.0);
        double payTaxPercentage = section.getDouble("pay.tax-percentage", 0.0);
        boolean isDefault = section.getBoolean("default", false);

        return new Currency(
                id,
                displayName,
                symbol,
                nameSingular,
                namePlural,
                format,
                decimalPlaces,
                starterBalance,
                minBalance,
                maxBalance,
                payEnabled,
                payMinAmount,
                payMaxAmount,
                payTaxPercentage,
                isDefault
        );
    }

    private void createDefaultCurrency() {
        Currency defaultCurr = new Currency(
                "lira",
                "Lira",
                "₺",
                "Lira",
                "Lira",
                "%amount% %symbol%",
                2,
                100.0,
                0.0,
                -1.0,
                true,
                1.0,
                -1.0,
                0.0,
                true
        );

        currencies.put("lira", defaultCurr);
        defaultCurrency = defaultCurr;

        plugin.getLogger().info("Created default currency: lira");
    }

    @Nullable
    public Currency getCurrency(@NotNull String id) {
        return currencies.get(id.toLowerCase());
    }

    @NotNull
    public Currency getDefaultCurrency() {
        if (defaultCurrency == null) {
            plugin.getLogger().severe("CRITICAL: Default currency is null! Creating emergency currency...");
            createDefaultCurrency();
        }
        return defaultCurrency;
    }

    @NotNull
    public Collection<Currency> getCurrencies() {
        return currencies.values();
    }

    @NotNull
    public Set<String> getCurrencyIds() {
        return currencies.keySet();
    }

    public boolean hasCurrency(@NotNull String id) {
        return currencies.containsKey(id.toLowerCase());
    }
}