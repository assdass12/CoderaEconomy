package tr.balzach.coderaEconomy.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;

import java.util.*;

/**
 * Currency management command - /currency
 */
public class CurrencyCommand implements CommandExecutor, TabCompleter {

    private final CoderaEconomy plugin;

    public CurrencyCommand(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            showCurrencies(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list", "liste" -> showCurrencies(sender);
            case "info", "bilgi" -> showCurrencyInfo(sender, args);
            default -> showCurrencies(sender);
        }

        return true;
    }

    /**
     * Shows all available currencies
     */
    private void showCurrencies(@NotNull CommandSender sender) {
        Collection<Currency> currencies = plugin.getCurrencyManager().getCurrencies();

        sender.sendMessage(plugin.getConfigManager().getMessage("currency.list.header", false));

        for (Currency currency : currencies) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("id", currency.getId());
            placeholders.put("name", currency.getDisplayName());
            placeholders.put("symbol", currency.getSymbol());
            placeholders.put("default", currency.isDefault() ? "✓" : "");

            sender.sendMessage(plugin.getConfigManager().getMessage("currency.list.entry", placeholders, false));
        }

        sender.sendMessage(plugin.getConfigManager().getMessage("currency.list.footer", false));
    }

    /**
     * Shows detailed info about a currency
     */
    private void showCurrencyInfo(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.usage"));
            return;
        }

        Currency currency = plugin.getCurrencyManager().getCurrency(args[1]);

        if (currency == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("currency", args[1]);
            sender.sendMessage(plugin.getConfigManager().getMessage("currency.not-found", placeholders));
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", currency.getId());
        placeholders.put("name", currency.getDisplayName());
        placeholders.put("symbol", currency.getSymbol());
        placeholders.put("singular", currency.getNameSingular());
        placeholders.put("plural", currency.getNamePlural());
        placeholders.put("format", currency.format(1000.50));
        placeholders.put("decimals", String.valueOf(currency.getDecimalPlaces()));
        placeholders.put("starter", currency.format(currency.getStarterBalance()));
        placeholders.put("min", String.valueOf(currency.getMinBalance()));
        placeholders.put("max", currency.getMaxBalance() == -1 ? "Unlimited" : String.valueOf(currency.getMaxBalance()));
        placeholders.put("pay-enabled", currency.isPayEnabled() ? "Yes" : "No");
        placeholders.put("default", currency.isDefault() ? "Yes" : "No");

        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.header", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.id", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.name", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.symbol", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.format", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.starter", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.limits", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.pay", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("currency.info.default", placeholders, false));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("list");
            completions.add("info");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                if (currencyId.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(currencyId);
                }
            }
        }

        return completions;
    }
}