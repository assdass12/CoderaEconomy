package tr.balzach.coderaEconomy.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;
import tr.balzach.coderaEconomy.database.DatabaseManager.BalanceEntry;

import java.util.List;

/**
 * PlaceholderAPI expansion for CoderaEconomy with multi-currency support
 *
 * Placeholders:
 * Basic (uses default currency):
 * - %coderaeconomy_balance% - Raw balance (1234.56)
 * - %coderaeconomy_balance_formatted% - Formatted balance (1,234.56 ₺)
 * - %coderaeconomy_balance_rounded% - Rounded balance (1235)
 * - %coderaeconomy_balance_short% - Short format (1.2K, 5.6M)
 * - %coderaeconomy_rank% - Player's rank position (#5)
 *
 * Currency-specific:
 * - %coderaeconomy_balance_<currency>% - Raw balance for currency
 * - %coderaeconomy_balance_<currency>_formatted% - Formatted balance for currency
 * - %coderaeconomy_balance_<currency>_rounded% - Rounded balance for currency
 * - %coderaeconomy_balance_<currency>_short% - Short format for currency
 * - %coderaeconomy_rank_<currency>% - Rank for specific currency
 *
 * Baltop (requires currency):
 * - %coderaeconomy_baltop_<currency>_<position>_player% - Player name at position
 * - %coderaeconomy_baltop_<currency>_<position>_balance% - Formatted balance at position
 * - %coderaeconomy_baltop_<currency>_<position>_balance_raw% - Raw balance at position
 */
public class CoderaPlaceholderExpansion extends PlaceholderExpansion {

    private final CoderaEconomy plugin;

    public CoderaPlaceholderExpansion(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "coderaeconomy";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onRequest(@NotNull OfflinePlayer player, @NotNull String params) {
        Currency defaultCurrency = plugin.getCurrencyManager().getDefaultCurrency();

        // Basic balance placeholders (default currency)
        if (params.equalsIgnoreCase("balance")) {
            return String.format("%.2f", getPlayerBalance(player, defaultCurrency.getId()));
        }

        if (params.equalsIgnoreCase("balance_formatted")) {
            return defaultCurrency.format(getPlayerBalance(player, defaultCurrency.getId()));
        }

        if (params.equalsIgnoreCase("balance_rounded")) {
            return String.valueOf(Math.round(getPlayerBalance(player, defaultCurrency.getId())));
        }

        if (params.equalsIgnoreCase("balance_short")) {
            return formatShort(getPlayerBalance(player, defaultCurrency.getId()));
        }

        // Rank position (default currency)
        if (params.equalsIgnoreCase("rank")) {
            int position = getPlayerPosition(player, defaultCurrency.getId());
            return position == -1 ? "N/A" : "#" + position;
        }

        // Currency-specific balance: balance_<currency>
        if (params.startsWith("balance_") && !params.contains("baltop")) {
            String[] parts = params.split("_");

            if (parts.length >= 2) {
                String currencyId = parts[1];
                Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

                if (currency == null) {
                    return "Invalid Currency";
                }

                if (parts.length == 2) {
                    // balance_<currency>
                    return String.format("%.2f", getPlayerBalance(player, currency.getId()));
                } else if (parts.length == 3) {
                    // balance_<currency>_formatted/rounded/short
                    String type = parts[2];

                    return switch (type) {
                        case "formatted" -> currency.format(getPlayerBalance(player, currency.getId()));
                        case "rounded" -> String.valueOf(Math.round(getPlayerBalance(player, currency.getId())));
                        case "short" -> formatShort(getPlayerBalance(player, currency.getId()));
                        default -> "Unknown Type";
                    };
                }
            }
        }

        // Rank for specific currency: rank_<currency>
        if (params.startsWith("rank_")) {
            String currencyId = params.substring(5);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);

            if (currency == null) {
                return "Invalid Currency";
            }

            int position = getPlayerPosition(player, currency.getId());
            return position == -1 ? "N/A" : "#" + position;
        }

        // Baltop: baltop_<currency>_<position>_player/balance/balance_raw
        if (params.startsWith("baltop_")) {
            try {
                String[] parts = params.split("_");

                if (parts.length < 4) {
                    return "Invalid Format";
                }

                String currencyId = parts[1];
                int position = Integer.parseInt(parts[2]);
                String type = parts[3];

                Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
                if (currency == null) {
                    return "Invalid Currency";
                }

                List<BalanceEntry> entries = plugin.getDatabaseManager().getTopBalances(
                        currency.getId(),
                        position,
                        0
                );

                if (position > 0 && position <= entries.size()) {
                    BalanceEntry entry = entries.get(position - 1);

                    return switch (type) {
                        case "player" -> entry.username();
                        case "balance" -> currency.format(entry.balance());
                        case "raw" -> String.format("%.2f", entry.balance());
                        default -> "Unknown Type";
                    };
                }

            } catch (Exception ignored) {}
            return "N/A";
        }

        return null;
    }

    private double getPlayerBalance(@NotNull OfflinePlayer player, @NotNull String currencyId) {
        double balance = plugin.getDatabaseManager().getBalance(player.getUniqueId(), currencyId);
        if (balance == -1) {
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            return currency != null ? currency.getStarterBalance() : 0;
        }
        return balance;
    }

    private int getPlayerPosition(@NotNull OfflinePlayer player, @NotNull String currencyId) {
        List<BalanceEntry> allEntries = plugin.getDatabaseManager().getTopBalances(
                currencyId,
                plugin.getDatabaseManager().getTotalPlayers(),
                0
        );

        for (int i = 0; i < allEntries.size(); i++) {
            if (allEntries.get(i).uuid().equals(player.getUniqueId())) {
                return i + 1;
            }
        }
        return -1;
    }

    private String formatShort(double amount) {
        if (amount >= 1_000_000_000) {
            return String.format("%.1fB", amount / 1_000_000_000);
        } else if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000);
        }
        return String.format("%.0f", amount);
    }
}