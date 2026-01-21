package tr.balzach.coderaEconomy.currency;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a currency in the economy system
 */
public class Currency {

    private final String id;
    private final String displayName;
    private final String symbol;
    private final String nameSingular;
    private final String namePlural;
    private final String format;
    private final int decimalPlaces;
    private final double starterBalance;
    private final double minBalance;
    private final double maxBalance;
    private final boolean payEnabled;
    private final double payMinAmount;
    private final double payMaxAmount;
    private final double payTaxPercentage;
    private final boolean isDefault;

    public Currency(
            @NotNull String id,
            @NotNull String displayName,
            @NotNull String symbol,
            @NotNull String nameSingular,
            @NotNull String namePlural,
            @NotNull String format,
            int decimalPlaces,
            double starterBalance,
            double minBalance,
            double maxBalance,
            boolean payEnabled,
            double payMinAmount,
            double payMaxAmount,
            double payTaxPercentage,
            boolean isDefault
    ) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.nameSingular = nameSingular;
        this.namePlural = namePlural;
        this.format = format;
        this.decimalPlaces = decimalPlaces;
        this.starterBalance = starterBalance;
        this.minBalance = minBalance;
        this.maxBalance = maxBalance;
        this.payEnabled = payEnabled;
        this.payMinAmount = payMinAmount;
        this.payMaxAmount = payMaxAmount;
        this.payTaxPercentage = payTaxPercentage;
        this.isDefault = isDefault;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public String getSymbol() {
        return symbol;
    }

    @NotNull
    public String getNameSingular() {
        return nameSingular;
    }

    @NotNull
    public String getNamePlural() {
        return namePlural;
    }

    @NotNull
    public String getFormat() {
        return format;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public double getStarterBalance() {
        return starterBalance;
    }

    public double getMinBalance() {
        return minBalance;
    }

    public double getMaxBalance() {
        return maxBalance;
    }

    public boolean isPayEnabled() {
        return payEnabled;
    }

    public double getPayMinAmount() {
        return payMinAmount;
    }

    public double getPayMaxAmount() {
        return payMaxAmount;
    }

    public double getPayTaxPercentage() {
        return payTaxPercentage;
    }

    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Formats an amount with this currency's settings
     */
    @NotNull
    public String format(double amount) {
        String formatted = String.format("%." + decimalPlaces + "f", amount);
        return format
                .replace("%amount%", formatted)
                .replace("%symbol%", symbol);
    }

    /**
     * Checks if a balance is valid for this currency
     */
    public boolean isValidBalance(double amount) {
        if (amount < minBalance) {
            return false;
        }
        return maxBalance == -1 || amount <= maxBalance;
    }

    @Override
    public String toString() {
        return "Currency{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", symbol='" + symbol + '\'' +
                ", isDefault=" + isDefault +
                '}';
    }
}