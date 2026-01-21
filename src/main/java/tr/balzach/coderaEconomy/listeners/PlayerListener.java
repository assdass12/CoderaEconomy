package tr.balzach.coderaEconomy.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;

/**
 * Player event listener with multi-currency support
 */
public class PlayerListener implements Listener {

    private final CoderaEconomy plugin;

    public PlayerListener(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player join - creates account if new with all currencies
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        // Check if player has account
        if (!plugin.getDatabaseManager().hasAccount(event.getPlayer().getUniqueId())) {
            // New player - create account
            plugin.getDatabaseManager().createAccount(
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getName()
            );

            // Initialize all currencies with starter balance
            for (Currency currency : plugin.getCurrencyManager().getCurrencies()) {
                plugin.getDatabaseManager().setBalanceAsync(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getName(),
                        currency.getId(),
                        currency.getStarterBalance()
                ).thenAccept(success -> {
                    if (success) {
                        plugin.getDatabaseManager().recordTransaction(
                                null,
                                event.getPlayer().getUniqueId(),
                                currency.getId(),
                                currency.getStarterBalance(),
                                "STARTER"
                        );
                    }
                });
            }

            plugin.getLogger().info(String.format(
                    "Created new account for %s with %d currencies",
                    event.getPlayer().getName(),
                    plugin.getCurrencyManager().getCurrencies().size()
            ));
        } else {
            // Existing player - update username in case it changed
            for (Currency currency : plugin.getCurrencyManager().getCurrencies()) {
                double balance = plugin.getDatabaseManager().getBalance(
                        event.getPlayer().getUniqueId(),
                        currency.getId()
                );

                // If player doesn't have this currency yet (new currency added), initialize it
                if (balance == -1) {
                    plugin.getDatabaseManager().setBalance(
                            event.getPlayer().getUniqueId(),
                            event.getPlayer().getName(),
                            currency.getId(),
                            currency.getStarterBalance()
                    );

                    plugin.getDatabaseManager().recordTransaction(
                            null,
                            event.getPlayer().getUniqueId(),
                            currency.getId(),
                            currency.getStarterBalance(),
                            "NEW_CURRENCY"
                    );
                }
            }
        }
    }

    /**
     * Handles player quit - clears cache and cancels pending payments
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        // Clear player from cache to save memory
        plugin.getDatabaseManager().clearCache(event.getPlayer().getUniqueId());

        // Cancel any pending payments
        plugin.removePendingPayment(event.getPlayer().getUniqueId());
    }
}