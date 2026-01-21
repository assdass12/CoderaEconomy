package tr.balzach.coderaEconomy.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;
import tr.balzach.coderaEconomy.database.DatabaseManager.BalanceEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Baltop command with multi-currency support - /baltop, /balancetop, /zenginler
 * FIXED VERSION - Division by zero düzeltildi
 */
public class BaltopCommand implements CommandExecutor, TabCompleter {

    private final CoderaEconomy plugin;

    public BaltopCommand(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("coderaeconomy.balancetop")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (!plugin.getConfigManager().isBaltopEnabled()) {
            sender.sendMessage(plugin.getConfigManager().getMessage("baltop.disabled"));
            return true;
        }

        int page = 1;
        Currency currency = plugin.getCurrencyManager().getDefaultCurrency();

        for (String arg : args) {
            try {
                int parsed = Integer.parseInt(arg);
                if (parsed >= 1) {
                    page = parsed;
                }
            } catch (NumberFormatException e) {
                Currency parsed = plugin.getCurrencyManager().getCurrency(arg);
                if (parsed != null) {
                    currency = parsed;
                }
            }
        }

        final int finalPage = page;
        final Currency finalCurrency = currency;

        CompletableFuture.runAsync(() -> {
            int entriesPerPage = plugin.getConfigManager().getBaltopEntriesPerPage();
            int totalPlayers = plugin.getDatabaseManager().getTotalPlayers();

            // FIX #8: Division by zero kontrolü
            if (entriesPerPage <= 0) {
                entriesPerPage = 10; // Default value
                plugin.getLogger().warning("Invalid baltop entries per page, using default: 10");
            }

            int totalPages = totalPlayers == 0 ? 1 : (int) Math.ceil((double) totalPlayers / entriesPerPage);

            // Sayfa kontrolü async task dışında değil, içinde yapılmalı
            if (finalPage < 1) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getConfigManager().getMessage("baltop.invalid-page"))
                );
                return;
            }

            if (finalPage > totalPages && totalPlayers > 0) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getConfigManager().getMessage("baltop.invalid-page"))
                );
                return;
            }

            int offset = (finalPage - 1) * entriesPerPage;
            List<BalanceEntry> entries = plugin.getDatabaseManager().getTopBalances(finalCurrency.getId(), entriesPerPage, offset);

            if (entries.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getConfigManager().getMessage("baltop.no-data"))
                );
                return;
            }

            List<String> messages = new ArrayList<>();

            Map<String, String> headerPlaceholders = new HashMap<>();
            headerPlaceholders.put("page", String.valueOf(finalPage));
            headerPlaceholders.put("total", String.valueOf(totalPages));
            headerPlaceholders.put("currency", finalCurrency.getDisplayName());
            messages.add(plugin.getConfigManager().getMessage("baltop.header", headerPlaceholders, false));

            int position = offset + 1;
            for (BalanceEntry entry : entries) {
                Map<String, String> entryPlaceholders = new HashMap<>();
                entryPlaceholders.put("position", String.valueOf(position));
                entryPlaceholders.put("player", entry.username());
                entryPlaceholders.put("amount", finalCurrency.format(entry.balance()));

                messages.add(plugin.getConfigManager().getMessage("baltop.entry", entryPlaceholders, false));
                position++;
            }

            messages.add(plugin.getConfigManager().getMessage("baltop.footer", new HashMap<>(), false));

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (String message : messages) {
                    sender.sendMessage(message);
                }
            });
        });

        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            int totalPlayers = plugin.getDatabaseManager().getTotalPlayers();
            int entriesPerPage = plugin.getConfigManager().getBaltopEntriesPerPage();

            // FIX: Division by zero önleme
            if (entriesPerPage <= 0) {
                entriesPerPage = 10;
            }

            int maxPages = totalPlayers == 0 ? 1 : (int) Math.ceil((double) totalPlayers / entriesPerPage);

            for (int i = 1; i <= Math.min(5, maxPages); i++) {
                completions.add(String.valueOf(i));
            }

            for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                if (currencyId.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(currencyId);
                }
            }
        } else if (args.length == 2) {
            try {
                Integer.parseInt(args[0]);
                for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                    if (currencyId.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(currencyId);
                    }
                }
            } catch (NumberFormatException e) {
                int totalPlayers = plugin.getDatabaseManager().getTotalPlayers();
                int entriesPerPage = plugin.getConfigManager().getBaltopEntriesPerPage();

                if (entriesPerPage <= 0) {
                    entriesPerPage = 10;
                }

                int maxPages = totalPlayers == 0 ? 1 : (int) Math.ceil((double) totalPlayers / entriesPerPage);

                for (int i = 1; i <= Math.min(5, maxPages); i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }
}