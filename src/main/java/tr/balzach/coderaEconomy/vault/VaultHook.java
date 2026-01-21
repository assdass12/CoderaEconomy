package tr.balzach.coderaEconomy.vault;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;

import java.util.Collections;
import java.util.List;

/**
 * Vault economy provider implementation
 * FIXED VERSION - Starter balance hatası düzeltildi
 */
public class VaultHook extends AbstractEconomy {

    private final CoderaEconomy plugin;

    public VaultHook(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    @NotNull
    public String getName() {
        return "CoderaEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return getDefaultCurrency().getDecimalPlaces();
    }

    @Override
    @NotNull
    public String format(double amount) {
        return getDefaultCurrency().format(amount);
    }

    @Override
    @NotNull
    public String currencyNamePlural() {
        return getDefaultCurrency().getNamePlural();
    }

    @Override
    @NotNull
    public String currencyNameSingular() {
        return getDefaultCurrency().getNameSingular();
    }

    @NotNull
    private Currency getDefaultCurrency() {
        return plugin.getCurrencyManager().getDefaultCurrency();
    }

    @NotNull
    private String getDefaultCurrencyId() {
        return getDefaultCurrency().getId();
    }

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player) {
        return plugin.getDatabaseManager().hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player) {
        String name = player.getName() != null ? player.getName() : "Unknown";

        // Hesap zaten varsa true dön
        if (plugin.getDatabaseManager().hasAccount(player.getUniqueId())) {
            return true;
        }

        // FIX #29: Yeni hesap oluştur VE starter balance ekle
        boolean accountCreated = plugin.getDatabaseManager().createAccount(player.getUniqueId(), name);

        if (accountCreated) {
            // Tüm currency'ler için starter balance ayarla
            for (Currency currency : plugin.getCurrencyManager().getCurrencies()) {
                plugin.getDatabaseManager().setBalance(
                        player.getUniqueId(),
                        name,
                        currency.getId(),
                        currency.getStarterBalance()
                );

                plugin.getDatabaseManager().recordTransaction(
                        null,
                        player.getUniqueId(),
                        currency.getId(),
                        currency.getStarterBalance(),
                        "ACCOUNT_CREATED"
                );
            }
        }

        return accountCreated;
    }

    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // FIX #5: Starter balance hatası düzeltildi
    @Override
    public double getBalance(@NotNull OfflinePlayer player) {
        double balance = plugin.getDatabaseManager().getBalance(player.getUniqueId(), getDefaultCurrencyId());

        // Balance -1 ise hesap yok demektir
        // Vault'un beklediği davranış: hesap yoksa otomatik oluştur
        if (balance == -1) {
            createPlayerAccount(player);
            // Şimdi tekrar dene
            balance = plugin.getDatabaseManager().getBalance(player.getUniqueId(), getDefaultCurrencyId());

            // Hala -1 ise (ki olmamalı), starter balance dön
            if (balance == -1) {
                return getDefaultCurrency().getStarterBalance();
            }
        }

        return balance;
    }

    @Override
    public double getBalance(@NotNull OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @NotNull
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Amount cannot be negative");
        }

        double current = getBalance(player);
        if (current < amount) {
            return new EconomyResponse(0, current, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }

        double newBalance = current - amount;

        if (!getDefaultCurrency().isValidBalance(newBalance)) {
            return new EconomyResponse(0, current, EconomyResponse.ResponseType.FAILURE, "Balance would be below minimum");
        }

        String name = player.getName() != null ? player.getName() : "Unknown";
        boolean success = plugin.getDatabaseManager().setBalance(
                player.getUniqueId(),
                name,
                getDefaultCurrencyId(),
                newBalance
        );

        if (success) {
            plugin.getDatabaseManager().recordTransaction(
                    null,
                    player.getUniqueId(),
                    getDefaultCurrencyId(),
                    -amount,
                    "WITHDRAW"
            );
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
        }

        return new EconomyResponse(0, current, EconomyResponse.ResponseType.FAILURE, "Database error");
    }

    @Override
    @NotNull
    public EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @NotNull
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Amount cannot be negative");
        }

        double current = getBalance(player);
        double newBalance = current + amount;

        if (!getDefaultCurrency().isValidBalance(newBalance)) {
            return new EconomyResponse(0, current, EconomyResponse.ResponseType.FAILURE, "Balance would exceed maximum");
        }

        String name = player.getName() != null ? player.getName() : "Unknown";
        boolean success = plugin.getDatabaseManager().setBalance(
                player.getUniqueId(),
                name,
                getDefaultCurrencyId(),
                newBalance
        );

        if (success) {
            plugin.getDatabaseManager().recordTransaction(
                    null,
                    player.getUniqueId(),
                    getDefaultCurrencyId(),
                    amount,
                    "DEPOSIT"
            );
            return new EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
        }

        return new EconomyResponse(0, current, EconomyResponse.ResponseType.FAILURE, "Database error");
    }

    @Override
    @NotNull
    public EconomyResponse depositPlayer(@NotNull OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // Legacy String-based Methods
    @Override
    public boolean hasAccount(@NotNull String playerName) {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean hasAccount(@NotNull String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public double getBalance(@NotNull String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(@NotNull String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(@NotNull String playerName, double amount) {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public boolean has(@NotNull String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    @NotNull
    public EconomyResponse withdrawPlayer(@NotNull String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @NotNull
    public EconomyResponse withdrawPlayer(@NotNull String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    @NotNull
    public EconomyResponse depositPlayer(@NotNull String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    @NotNull
    public EconomyResponse depositPlayer(@NotNull String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(@NotNull String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    // Bank Methods (Not Supported)
    @Override
    public EconomyResponse createBank(@NotNull String name, @NotNull String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse createBank(@NotNull String name, @NotNull OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse deleteBank(@NotNull String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankBalance(@NotNull String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankHas(@NotNull String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankWithdraw(@NotNull String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse bankDeposit(@NotNull String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String name, @NotNull String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankOwner(@NotNull String name, @NotNull OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankMember(@NotNull String name, @NotNull String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    public EconomyResponse isBankMember(@NotNull String name, @NotNull OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override
    @NotNull
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}