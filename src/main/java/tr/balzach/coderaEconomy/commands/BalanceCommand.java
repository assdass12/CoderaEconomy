package tr.balzach.coderaEconomy.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Balance command with multi-currency support - /balance, /para, /bakiye
 */
public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final CoderaEconomy plugin;

    public BalanceCommand(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        // Check if checking own balance
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getConfigManager().getMessage("console-not-allowed"));
                return true;
            }

            if (!sender.hasPermission("coderaeconomy.balance")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }

            showBalance(sender, player, plugin.getCurrencyManager().getDefaultCurrency());
            return true;
        }

        // /balance [player] [currency]
        if (args.length >= 1) {
            // First check if it's a currency name
            Currency currency = plugin.getCurrencyManager().getCurrency(args[0]);

            if (currency != null && sender instanceof Player) {
                // It's a currency, show own balance for that currency
                if (!sender.hasPermission("coderaeconomy.balance")) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                showBalance(sender, (Player) sender, currency);
                return true;
            }

            // It's a player name
            if (!sender.hasPermission("coderaeconomy.balance.others")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

            if (!plugin.getDatabaseManager().hasAccount(target.getUniqueId())) {
                sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                return true;
            }

            // Check for currency in second argument
            Currency targetCurrency = plugin.getCurrencyManager().getDefaultCurrency();
            if (args.length >= 2) {
                Currency parsedCurrency = plugin.getCurrencyManager().getCurrency(args[1]);
                if (parsedCurrency != null) {
                    targetCurrency = parsedCurrency;
                }
            }

            showBalance(sender, target, targetCurrency);
            return true;
        }

        return false;
    }

    /**
     * Shows balance to command sender
     */
    private void showBalance(@NotNull CommandSender sender, @NotNull OfflinePlayer target, @NotNull Currency currency) {
        plugin.getDatabaseManager().getBalanceAsync(target.getUniqueId(), currency.getId()).thenAccept(balance -> {
            if (balance == -1) {
                balance = currency.getStarterBalance();
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName() != null ? target.getName() : "Unknown");
            placeholders.put("amount", currency.format(balance));
            placeholders.put("currency", currency.getDisplayName());

            String message;
            if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                message = plugin.getConfigManager().getMessage("balance.self", placeholders);
            } else {
                message = plugin.getConfigManager().getMessage("balance.other", placeholders);
            }

            // Send message on main thread
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        });
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Add online players if has permission
            if (sender.hasPermission("coderaeconomy.balance.others")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }

            // Add currencies
            for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                if (currencyId.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(currencyId);
                }
            }
        } else if (args.length == 2) {
            // Second argument is currency
            for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                if (currencyId.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(currencyId);
                }
            }
        }

        return completions;
    }
}