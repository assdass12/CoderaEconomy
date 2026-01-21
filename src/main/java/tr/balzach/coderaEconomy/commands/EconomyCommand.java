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
 * Main economy command - /economy, /eco, /ekonomi, /ce
 * FIXED: Now includes currency subcommand + proper reload
 */
public class EconomyCommand implements CommandExecutor, TabCompleter {

    private final CoderaEconomy plugin;
    private final CurrencyCommand currencyCommand;

    public EconomyCommand(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
        this.currencyCommand = new CurrencyCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help", "yardim" -> showHelp(sender);
            case "reload", "yenile" -> handleReload(sender);
            case "give", "ver" -> handleGive(sender, args);
            case "set", "ayarla" -> handleSet(sender, args);
            case "remove", "al", "take" -> handleRemove(sender, args);
            case "reset", "sifirla" -> handleReset(sender, args);

            // FIXED: Currency subcommand integration
            case "currency", "currencies", "parabirimi", "para" -> {
                // Remove first argument (currency) and pass the rest
                String[] currencyArgs = Arrays.copyOfRange(args, 1, args.length);
                currencyCommand.onCommand(sender, command, label, currencyArgs);
            }

            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(@NotNull CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getMessage("help.header", false));

        for (String line : plugin.getConfigManager().getPlayerHelpMessages()) {
            if (!line.isEmpty()) {
                sender.sendMessage(line);
            }
        }

        if (sender.hasPermission("coderaeconomy.admin")) {
            for (String line : plugin.getConfigManager().getAdminHelpMessages()) {
                if (!line.isEmpty()) {
                    sender.sendMessage(line);
                }
            }
        }
    }

    /**
     * FIXED: Proper reload implementation
     */
    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("coderaeconomy.admin.reload")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        try {
            // Call the main plugin reload method
            plugin.reloadPlugin();
            sender.sendMessage(plugin.getConfigManager().getMessage("config-reloaded"));
        } catch (Exception e) {
            sender.sendMessage(plugin.getConfigManager().colorize("<#FF4444>Reload failed: " + e.getMessage()));
            plugin.getLogger().severe("Reload error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * /eco give <player|all> <amount> [currency]
     */
    private void handleGive(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("coderaeconomy.admin.give")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.usage.give"));
            return;
        }

        String targetName = args[1];
        double amount;
        Currency currency;

        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return;
        }

        if (args.length >= 4) {
            currency = plugin.getCurrencyManager().getCurrency(args[3]);
            if (currency == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("currency", args[3]);
                sender.sendMessage(plugin.getConfigManager().getMessage("currency.not-found", placeholders));
                return;
            }
        } else {
            currency = plugin.getCurrencyManager().getDefaultCurrency();
        }

        if (targetName.equalsIgnoreCase("all")) {
            handleBulkGive(sender, amount, currency);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String finalName = target.getName() != null ? target.getName() : targetName;

        boolean success = plugin.getDatabaseManager().addBalance(
                target.getUniqueId(),
                finalName,
                currency.getId(),
                amount
        );

        if (success) {
            plugin.getDatabaseManager().recordTransaction(
                    null,
                    target.getUniqueId(),
                    currency.getId(),
                    amount,
                    "ADMIN_GIVE"
            );

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", finalName);
            placeholders.put("amount", currency.format(amount));
            placeholders.put("currency", currency.getDisplayName());

            sender.sendMessage(plugin.getConfigManager().getMessage("admin.give.success", placeholders));

            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                Map<String, String> notifyPlaceholders = new HashMap<>();
                notifyPlaceholders.put("sender", sender.getName());
                notifyPlaceholders.put("amount", currency.format(amount));
                notifyPlaceholders.put("currency", currency.getDisplayName());
                targetPlayer.sendMessage(plugin.getConfigManager().getMessage("admin.give.notify", notifyPlaceholders));
            }
        } else {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.error"));
        }
    }

    private void handleBulkGive(@NotNull CommandSender sender, double amount, @NotNull Currency currency) {
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.processing", new HashMap<>(), false));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> allPlayers = plugin.getDatabaseManager().getAllPlayerUUIDs();
            int successCount = 0;
            int failCount = 0;

            for (UUID uuid : allPlayers) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String name = player.getName() != null ? player.getName() : "Unknown";

                boolean success = plugin.getDatabaseManager().addBalance(uuid, name, currency.getId(), amount);

                if (success) {
                    plugin.getDatabaseManager().recordTransaction(null, uuid, currency.getId(), amount, "ADMIN_GIVE_ALL");
                    successCount++;
                } else {
                    failCount++;
                }
            }

            int finalSuccess = successCount;
            int finalFail = failCount;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("success", String.valueOf(finalSuccess));
                placeholders.put("fail", String.valueOf(finalFail));
                placeholders.put("amount", currency.format(amount));
                placeholders.put("currency", currency.getDisplayName());
                sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.give-complete", placeholders));
            });
        });
    }

    /**
     * /eco set <player|all> <amount> [currency]
     */
    private void handleSet(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("coderaeconomy.admin.set")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.usage.set"));
            return;
        }

        String targetName = args[1];
        double amount;
        Currency currency;

        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return;
        }

        if (amount < 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return;
        }

        if (args.length >= 4) {
            currency = plugin.getCurrencyManager().getCurrency(args[3]);
            if (currency == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("currency", args[3]);
                sender.sendMessage(plugin.getConfigManager().getMessage("currency.not-found", placeholders));
                return;
            }
        } else {
            currency = plugin.getCurrencyManager().getDefaultCurrency();
        }

        if (!currency.isValidBalance(amount)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.invalid-balance"));
            return;
        }

        if (targetName.equalsIgnoreCase("all")) {
            handleBulkSet(sender, amount, currency);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String finalName = target.getName() != null ? target.getName() : targetName;

        boolean success = plugin.getDatabaseManager().setBalance(
                target.getUniqueId(),
                finalName,
                currency.getId(),
                amount
        );

        if (success) {
            plugin.getDatabaseManager().recordTransaction(null, target.getUniqueId(), currency.getId(), amount, "ADMIN_SET");

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", finalName);
            placeholders.put("amount", currency.format(amount));
            placeholders.put("currency", currency.getDisplayName());

            sender.sendMessage(plugin.getConfigManager().getMessage("admin.set.success", placeholders));

            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                Map<String, String> notifyPlaceholders = new HashMap<>();
                notifyPlaceholders.put("sender", sender.getName());
                notifyPlaceholders.put("amount", currency.format(amount));
                notifyPlaceholders.put("currency", currency.getDisplayName());
                targetPlayer.sendMessage(plugin.getConfigManager().getMessage("admin.set.notify", notifyPlaceholders));
            }
        } else {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.error"));
        }
    }

    private void handleBulkSet(@NotNull CommandSender sender, double amount, @NotNull Currency currency) {
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.processing", new HashMap<>(), false));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> allPlayers = plugin.getDatabaseManager().getAllPlayerUUIDs();
            int successCount = 0;
            int failCount = 0;

            for (UUID uuid : allPlayers) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String name = player.getName() != null ? player.getName() : "Unknown";

                boolean success = plugin.getDatabaseManager().setBalance(uuid, name, currency.getId(), amount);

                if (success) {
                    plugin.getDatabaseManager().recordTransaction(null, uuid, currency.getId(), amount, "ADMIN_SET_ALL");
                    successCount++;
                } else {
                    failCount++;
                }
            }

            int finalSuccess = successCount;
            int finalFail = failCount;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("success", String.valueOf(finalSuccess));
                placeholders.put("fail", String.valueOf(finalFail));
                placeholders.put("amount", currency.format(amount));
                placeholders.put("currency", currency.getDisplayName());
                sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.set-complete", placeholders));
            });
        });
    }

    /**
     * /eco remove <player|all> <amount> [currency]
     */
    private void handleRemove(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("coderaeconomy.admin.remove")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.usage.remove"));
            return;
        }

        String targetName = args[1];
        double amount;
        Currency currency;

        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid-amount"));
            return;
        }

        if (args.length >= 4) {
            currency = plugin.getCurrencyManager().getCurrency(args[3]);
            if (currency == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("currency", args[3]);
                sender.sendMessage(plugin.getConfigManager().getMessage("currency.not-found", placeholders));
                return;
            }
        } else {
            currency = plugin.getCurrencyManager().getDefaultCurrency();
        }

        if (targetName.equalsIgnoreCase("all")) {
            handleBulkRemove(sender, amount, currency);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String finalName = target.getName() != null ? target.getName() : targetName;

        boolean success = plugin.getDatabaseManager().removeBalance(
                target.getUniqueId(),
                finalName,
                currency.getId(),
                amount
        );

        if (success) {
            plugin.getDatabaseManager().recordTransaction(null, target.getUniqueId(), currency.getId(), -amount, "ADMIN_REMOVE");

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", finalName);
            placeholders.put("amount", currency.format(amount));
            placeholders.put("currency", currency.getDisplayName());

            sender.sendMessage(plugin.getConfigManager().getMessage("admin.remove.success", placeholders));

            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                Map<String, String> notifyPlaceholders = new HashMap<>();
                notifyPlaceholders.put("sender", sender.getName());
                notifyPlaceholders.put("amount", currency.format(amount));
                notifyPlaceholders.put("currency", currency.getDisplayName());
                targetPlayer.sendMessage(plugin.getConfigManager().getMessage("admin.remove.notify", notifyPlaceholders));
            }
        } else {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.insufficient"));
        }
    }

    private void handleBulkRemove(@NotNull CommandSender sender, double amount, @NotNull Currency currency) {
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.processing", new HashMap<>(), false));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> allPlayers = plugin.getDatabaseManager().getAllPlayerUUIDs();
            int successCount = 0;
            int failCount = 0;

            for (UUID uuid : allPlayers) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String name = player.getName() != null ? player.getName() : "Unknown";

                boolean success = plugin.getDatabaseManager().removeBalance(uuid, name, currency.getId(), amount);

                if (success) {
                    plugin.getDatabaseManager().recordTransaction(null, uuid, currency.getId(), -amount, "ADMIN_REMOVE_ALL");
                    successCount++;
                } else {
                    failCount++;
                }
            }

            int finalSuccess = successCount;
            int finalFail = failCount;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("success", String.valueOf(finalSuccess));
                placeholders.put("fail", String.valueOf(finalFail));
                placeholders.put("amount", currency.format(amount));
                placeholders.put("currency", currency.getDisplayName());
                sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.remove-complete", placeholders));
            });
        });
    }

    /**
     * /eco reset <player|all> [currency]
     */
    private void handleReset(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("coderaeconomy.admin.reset")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.usage.reset"));
            return;
        }

        String targetName = args[1];
        Currency currency;

        if (args.length >= 3) {
            currency = plugin.getCurrencyManager().getCurrency(args[2]);
            if (currency == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("currency", args[2]);
                sender.sendMessage(plugin.getConfigManager().getMessage("currency.not-found", placeholders));
                return;
            }
        } else {
            currency = plugin.getCurrencyManager().getDefaultCurrency();
        }

        if (targetName.equalsIgnoreCase("all")) {
            handleBulkReset(sender, currency);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String finalName = target.getName() != null ? target.getName() : targetName;
        double starterBalance = currency.getStarterBalance();

        boolean success = plugin.getDatabaseManager().setBalance(
                target.getUniqueId(),
                finalName,
                currency.getId(),
                starterBalance
        );

        if (success) {
            plugin.getDatabaseManager().recordTransaction(null, target.getUniqueId(), currency.getId(), 0, "ADMIN_RESET");

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", finalName);
            placeholders.put("currency", currency.getDisplayName());

            sender.sendMessage(plugin.getConfigManager().getMessage("admin.reset.success", placeholders));

            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null && targetPlayer.isOnline()) {
                Map<String, String> notifyPlaceholders = new HashMap<>();
                notifyPlaceholders.put("sender", sender.getName());
                notifyPlaceholders.put("currency", currency.getDisplayName());
                targetPlayer.sendMessage(plugin.getConfigManager().getMessage("admin.reset.notify", notifyPlaceholders));
            }
        } else {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.error"));
        }
    }

    private void handleBulkReset(@NotNull CommandSender sender, @NotNull Currency currency) {
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.processing", new HashMap<>(), false));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> allPlayers = plugin.getDatabaseManager().getAllPlayerUUIDs();
            int successCount = 0;
            int failCount = 0;
            double starterBalance = currency.getStarterBalance();

            for (UUID uuid : allPlayers) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                String name = player.getName() != null ? player.getName() : "Unknown";

                boolean success = plugin.getDatabaseManager().setBalance(uuid, name, currency.getId(), starterBalance);

                if (success) {
                    plugin.getDatabaseManager().recordTransaction(null, uuid, currency.getId(), 0, "ADMIN_RESET_ALL");
                    successCount++;
                } else {
                    failCount++;
                }
            }

            int finalSuccess = successCount;
            int finalFail = failCount;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("success", String.valueOf(finalSuccess));
                placeholders.put("fail", String.valueOf(finalFail));
                placeholders.put("currency", currency.getDisplayName());
                sender.sendMessage(plugin.getConfigManager().getMessage("admin.bulk.reset-complete", placeholders));
            });
        });
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("help");
            subCommands.add("currency"); // FIXED: Added currency to tab completion

            if (sender.hasPermission("coderaeconomy.admin")) {
                subCommands.addAll(Arrays.asList("reload", "give", "set", "remove", "reset"));
            }

            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            // FIXED: Currency tab completion
            if (subCommand.equals("currency") || subCommand.equals("currencies")) {
                completions.add("list");
                completions.add("info");
                return completions;
            }

            if (sender.hasPermission("coderaeconomy.admin")) {
                if (subCommand.equals("give") || subCommand.equals("set") ||
                        subCommand.equals("remove") || subCommand.equals("reset")) {

                    completions.add("all");

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            // FIXED: Currency info tab completion
            if (subCommand.equals("currency") && args[1].equalsIgnoreCase("info")) {
                for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                    if (currencyId.toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(currencyId);
                    }
                }
                return completions;
            }

            if (sender.hasPermission("coderaeconomy.admin")) {
                if (subCommand.equals("give") || subCommand.equals("set") || subCommand.equals("remove")) {
                    completions.addAll(Arrays.asList("100", "500", "1000", "5000", "10000"));
                }
            }
        } else if (args.length == 4 && sender.hasPermission("coderaeconomy.admin")) {
            for (String currencyId : plugin.getCurrencyManager().getCurrencyIds()) {
                if (currencyId.toLowerCase().startsWith(args[3].toLowerCase())) {
                    completions.add(currencyId);
                }
            }
        }

        return completions;
    }
}