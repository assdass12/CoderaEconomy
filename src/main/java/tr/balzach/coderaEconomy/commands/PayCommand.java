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

import java.util.*;

/**
 * Pay command with confirmation system - /pay, /payconfirm
 * FIXED VERSION - Transaction support & offline player check
 */
public class PayCommand implements CommandExecutor, TabCompleter {

    private final CoderaEconomy plugin;

    public PayCommand(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (command.getName().equalsIgnoreCase("payconfirm")) {
            return handleConfirmation(sender);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("console-not-allowed"));
            return true;
        }

        if (!sender.hasPermission("coderaeconomy.pay")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(plugin.getConfigManager().getMessage("prefix", false) +
                    plugin.getConfigManager().colorize("<#FFB347>Kullanım: <#FFFFFF>/" + label + " <oyuncu> <miktar> [para birimi]"));
            return true;
        }

        Currency currency;
        if (args.length == 3) {
            currency = plugin.getCurrencyManager().getCurrency(args[2]);
            if (currency == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("currency", args[2]);
                sender.sendMessage(plugin.getConfigManager().getMessage("currency.not-found", placeholders));
                return true;
            }
        } else {
            currency = plugin.getCurrencyManager().getDefaultCurrency();
        }

        if (!currency.isPayEnabled()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("currency", currency.getDisplayName());
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.disabled-currency", placeholders));
            return true;
        }

        // FIX #21: Offline player kontrolü - gerçek oyuncu mu?
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        // Eğer hasPlayedBefore() false ise, bu hiç giriş yapmamış bir oyuncu
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return true;
        }

        if (!plugin.getDatabaseManager().hasAccount(target.getUniqueId())) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return true;
        }

        if (player.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.self-pay"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return true;
        }

        double minAmount = currency.getPayMinAmount();
        double maxAmount = currency.getPayMaxAmount();

        if (amount < minAmount) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", currency.format(minAmount));
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.min-amount", placeholders));
            return true;
        }

        if (maxAmount != -1 && amount > maxAmount) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", currency.format(maxAmount));
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.max-amount", placeholders));
            return true;
        }

        // FIX #22: Double overflow kontrolü
        double taxPercentage = currency.getPayTaxPercentage();
        double tax = amount * taxPercentage;
        double totalRequired = amount + tax;

        // Infinity veya çok büyük değer kontrolü
        if (Double.isInfinite(totalRequired) || totalRequired > Double.MAX_VALUE / 2) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return true;
        }

        double senderBalance = plugin.getDatabaseManager().getBalance(player.getUniqueId(), currency.getId());
        if (senderBalance == -1) {
            senderBalance = currency.getStarterBalance();
        }

        if (senderBalance < totalRequired) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", currency.format(totalRequired));
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.insufficient-funds", placeholders));
            return true;
        }

        plugin.addPendingPayment(player.getUniqueId(),
                new CoderaEconomy.PendingPayment(target.getUniqueId(), currency.getId(), amount, tax));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName() != null ? target.getName() : "Unknown");
        placeholders.put("amount", currency.format(amount));
        placeholders.put("currency", currency.getDisplayName());

        sender.sendMessage(plugin.getConfigManager().getMessage("pay.confirmation.header", placeholders, false));
        sender.sendMessage(plugin.getConfigManager().getMessage("pay.confirmation.amount", placeholders, false));

        if (tax > 0) {
            Map<String, String> taxPlaceholders = new HashMap<>();
            taxPlaceholders.put("tax", currency.format(tax));
            taxPlaceholders.put("total", currency.format(totalRequired));
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.confirmation.tax", taxPlaceholders, false));
        }

        sender.sendMessage(plugin.getConfigManager().getMessage("pay.confirmation.confirm", new HashMap<>(), false));
        sender.sendMessage(plugin.getConfigManager().getMessage("pay.confirmation.timeout", new HashMap<>(), false));

        return true;
    }

    private boolean handleConfirmation(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("console-not-allowed"));
            return true;
        }

        CoderaEconomy.PendingPayment pending = plugin.getPendingPayment(player.getUniqueId());

        if (pending == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.no-pending"));
            return true;
        }

        if (System.currentTimeMillis() - pending.timestamp > 60000) {
            plugin.removePendingPayment(player.getUniqueId());
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.expired"));
            return true;
        }

        Currency currency = plugin.getCurrencyManager().getCurrency(pending.currencyId);
        if (currency == null) {
            plugin.removePendingPayment(player.getUniqueId());
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.error"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(pending.target);
        double totalRequired = pending.amount + pending.tax;

        double senderBalance = plugin.getDatabaseManager().getBalance(player.getUniqueId(), currency.getId());
        if (senderBalance < totalRequired) {
            plugin.removePendingPayment(player.getUniqueId());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", currency.format(totalRequired));
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.insufficient-funds", placeholders));
            return true;
        }

        // FIX #34: Atomik transaction kullan (rollback desteği)
        boolean success = plugin.getDatabaseManager().transferBalance(
                player.getUniqueId(),
                player.getName(),
                target.getUniqueId(),
                target.getName() != null ? target.getName() : "Unknown",
                currency.getId(),
                totalRequired,
                "PAY"
        );

        plugin.removePendingPayment(player.getUniqueId());

        if (!success) {
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.error"));
            return true;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName() != null ? target.getName() : "Unknown");
        placeholders.put("amount", currency.format(pending.amount));
        placeholders.put("currency", currency.getDisplayName());

        sender.sendMessage(plugin.getConfigManager().getMessage("pay.success-sender", placeholders));

        if (pending.tax > 0) {
            Map<String, String> taxPlaceholders = new HashMap<>();
            taxPlaceholders.put("tax", currency.format(pending.tax));
            sender.sendMessage(plugin.getConfigManager().getMessage("pay.tax-applied", taxPlaceholders));
        }

        Player targetPlayer = target.getPlayer();
        if (targetPlayer != null && targetPlayer.isOnline()) {
            Map<String, String> receiverPlaceholders = new HashMap<>();
            receiverPlaceholders.put("player", player.getName());
            receiverPlaceholders.put("amount", currency.format(pending.amount));
            receiverPlaceholders.put("currency", currency.getDisplayName());
            targetPlayer.sendMessage(plugin.getConfigManager().getMessage("pay.success-receiver", receiverPlaceholders));
        }

        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("payconfirm")) {
            return completions;
        }

        if (args.length == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    if (sender instanceof Player && !player.getUniqueId().equals(((Player) sender).getUniqueId())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 2) {
            completions.add("100");
            completions.add("500");
            completions.add("1000");
        } else if (args.length == 3) {
            for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                if (currencyId.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(currencyId);
                }
            }
        }

        return completions;
    }
}