package tr.balzach.coderaEconomy.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import tr.balzach.coderaEconomy.CoderaEconomy;
import tr.balzach.coderaEconomy.currency.Currency;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Modern database manager with connection pooling and multi-currency support
 * FULLY OPTIMIZED VERSION - Proper WAL mode handling + improved caching
 */
public class DatabaseManager {

    private final CoderaEconomy plugin;
    private HikariDataSource dataSource;
    private final File databaseFile;

    // Improved cache with better memory management
    private final Map<UUID, Map<String, Double>> cache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    public DatabaseManager(@NotNull CoderaEconomy plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "economy.db");

        initialize();
        startBackupTask();
    }

    private void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                if (!plugin.getDataFolder().mkdirs()) {
                    throw new IOException("Failed to create plugin folder");
                }
            }

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + databaseFile.getPath());

            // OPTIMIZED: Better pool settings
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setLeakDetectionThreshold(60000);

            // OPTIMIZED: SQLite performance settings
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            this.dataSource = new HikariDataSource(hikariConfig);

            // IMPORTANT: WAL mode setup
            // economy.db-wal and economy.db-shm files are NORMAL for WAL mode
            // They improve performance and crash recovery
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL"); // WAL creates .db-wal and .db-shm files
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA temp_store = MEMORY");
                stmt.execute("PRAGMA mmap_size = 30000000000");
                stmt.execute("PRAGMA page_size = 4096");
                stmt.execute("PRAGMA cache_size = -64000"); // 64MB cache
            }

            createTables();
            plugin.getLogger().info("Database initialized with WAL mode (economy.db-wal and .db-shm are normal)");

        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createTables() throws SQLException {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY NOT NULL,
                username TEXT NOT NULL,
                last_updated INTEGER NOT NULL,
                UNIQUE(uuid)
            )
            """;

        String createBalancesTable = """
            CREATE TABLE IF NOT EXISTS balances (
                uuid TEXT NOT NULL,
                currency TEXT NOT NULL,
                balance REAL NOT NULL DEFAULT 0,
                PRIMARY KEY(uuid, currency),
                FOREIGN KEY(uuid) REFERENCES players(uuid) ON DELETE CASCADE
            )
            """;

        String createTransactionsTable = """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                from_uuid TEXT,
                to_uuid TEXT NOT NULL,
                currency TEXT NOT NULL,
                amount REAL NOT NULL,
                type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(from_uuid) REFERENCES players(uuid),
                FOREIGN KEY(to_uuid) REFERENCES players(uuid)
            )
            """;

        String createIndexes = """
            CREATE INDEX IF NOT EXISTS idx_balances_currency ON balances(currency, balance DESC);
            CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON transactions(timestamp DESC);
            CREATE INDEX IF NOT EXISTS idx_players_username ON players(username);
            """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createBalancesTable);
            stmt.execute(createTransactionsTable);
            stmt.execute(createIndexes);
        }
    }

    public double getBalance(@NotNull UUID uuid, @NotNull String currencyId) {
        // Check cache first
        if (cache.containsKey(uuid) && cache.get(uuid).containsKey(currencyId)) {
            return cache.get(uuid).get(currencyId);
        }

        String query = "SELECT balance FROM balances WHERE uuid = ? AND currency = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currencyId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double balance = rs.getDouble("balance");

                    // OPTIMIZED: Cache with size limit
                    if (cache.size() < MAX_CACHE_SIZE) {
                        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(currencyId, balance);
                    }

                    return balance;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get balance for " + uuid, e);
        }

        return -1;
    }

    public double getBalance(@NotNull UUID uuid) {
        return getBalance(uuid, plugin.getCurrencyManager().getDefaultCurrency().getId());
    }

    @NotNull
    public CompletableFuture<Double> getBalanceAsync(@NotNull UUID uuid, @NotNull String currencyId) {
        return CompletableFuture.supplyAsync(() -> getBalance(uuid, currencyId));
    }

    public boolean setBalance(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        if (!ensurePlayerExists(uuid, username)) {
            return false;
        }

        String query = """
            INSERT INTO balances (uuid, currency, balance)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid, currency) DO UPDATE SET balance = excluded.balance
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currencyId);
            ps.setDouble(3, amount);

            int result = ps.executeUpdate();

            if (result > 0) {
                // Update cache
                cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(currencyId, amount);
                return true;
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set balance for " + uuid, e);
        }

        return false;
    }

    public boolean setBalance(@NotNull UUID uuid, @NotNull String username, double amount) {
        return setBalance(uuid, username, plugin.getCurrencyManager().getDefaultCurrency().getId(), amount);
    }

    @NotNull
    public CompletableFuture<Boolean> setBalanceAsync(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        return CompletableFuture.supplyAsync(() -> setBalance(uuid, username, currencyId, amount));
    }

    private boolean ensurePlayerExists(@NotNull UUID uuid, @NotNull String username) {
        String query = """
            INSERT INTO players (uuid, username, last_updated)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                username = excluded.username,
                last_updated = excluded.last_updated
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, System.currentTimeMillis());

            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to ensure player exists: " + uuid, e);
            return false;
        }
    }

    public boolean addBalance(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        double current = getBalance(uuid, currencyId);

        Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
        if (currency == null) {
            plugin.getLogger().warning("Currency not found: " + currencyId);
            return false;
        }

        if (current == -1) {
            current = currency.getStarterBalance();
        }

        return setBalance(uuid, username, currencyId, current + amount);
    }

    public boolean removeBalance(@NotNull UUID uuid, @NotNull String username, @NotNull String currencyId, double amount) {
        double current = getBalance(uuid, currencyId);
        if (current == -1 || current < amount) {
            return false;
        }

        return setBalance(uuid, username, currencyId, current - amount);
    }

    /**
     * ATOMIC transaction with proper rollback support
     */
    public boolean transferBalance(@NotNull UUID fromUuid, @NotNull String fromUsername,
                                   @NotNull UUID toUuid, @NotNull String toUsername,
                                   @NotNull String currencyId, double amount,
                                   @NotNull String transactionType) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // Withdraw from sender
            double fromBalance = getBalance(fromUuid, currencyId);
            if (fromBalance < amount) {
                conn.rollback();
                return false;
            }

            if (!setBalanceInTransaction(conn, fromUuid, fromUsername, currencyId, fromBalance - amount)) {
                conn.rollback();
                return false;
            }

            // Deposit to receiver
            double toBalance = getBalance(toUuid, currencyId);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            if (currency == null) {
                conn.rollback();
                return false;
            }

            if (toBalance == -1) {
                toBalance = currency.getStarterBalance();
            }

            if (!setBalanceInTransaction(conn, toUuid, toUsername, currencyId, toBalance + amount)) {
                conn.rollback();
                return false;
            }

            // Record transaction
            recordTransactionInConnection(conn, fromUuid, toUuid, currencyId, amount, transactionType);

            conn.commit();

            // Update cache
            cache.computeIfAbsent(fromUuid, k -> new ConcurrentHashMap<>()).put(currencyId, fromBalance - amount);
            cache.computeIfAbsent(toUuid, k -> new ConcurrentHashMap<>()).put(currencyId, toBalance + amount);

            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
                }
            }
            plugin.getLogger().log(Level.SEVERE, "Transfer failed", e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to close connection", e);
                }
            }
        }
    }

    private boolean setBalanceInTransaction(Connection conn, UUID uuid, String username, String currencyId, double amount) throws SQLException {
        if (!ensurePlayerExistsInTransaction(conn, uuid, username)) {
            return false;
        }

        String query = """
            INSERT INTO balances (uuid, currency, balance)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid, currency) DO UPDATE SET balance = excluded.balance
            """;

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, currencyId);
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        }
    }

    private boolean ensurePlayerExistsInTransaction(Connection conn, UUID uuid, String username) throws SQLException {
        String query = """
            INSERT INTO players (uuid, username, last_updated)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                username = excluded.username,
                last_updated = excluded.last_updated
            """;

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        }
    }

    private void recordTransactionInConnection(Connection conn, UUID fromUuid, UUID toUuid, String currencyId, double amount, String type) throws SQLException {
        String query = "INSERT INTO transactions (from_uuid, to_uuid, currency, amount, type, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, fromUuid != null ? fromUuid.toString() : null);
            ps.setString(2, toUuid.toString());
            ps.setString(3, currencyId);
            ps.setDouble(4, amount);
            ps.setString(5, type);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public boolean hasAccount(@NotNull UUID uuid) {
        String query = "SELECT 1 FROM players WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check account for " + uuid, e);
        }

        return false;
    }

    public boolean createAccount(@NotNull UUID uuid, @NotNull String username) {
        if (hasAccount(uuid)) {
            return true;
        }

        return ensurePlayerExists(uuid, username);
    }

    public void recordTransaction(@org.jetbrains.annotations.Nullable UUID fromUuid, @NotNull UUID toUuid, @NotNull String currencyId, double amount, @NotNull String type) {
        String query = "INSERT INTO transactions (from_uuid, to_uuid, currency, amount, type, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, fromUuid != null ? fromUuid.toString() : null);
            ps.setString(2, toUuid.toString());
            ps.setString(3, currencyId);
            ps.setDouble(4, amount);
            ps.setString(5, type);
            ps.setLong(6, System.currentTimeMillis());

            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record transaction", e);
        }
    }

    @NotNull
    public List<BalanceEntry> getTopBalances(@NotNull String currencyId, int limit, int offset) {
        List<BalanceEntry> entries = new ArrayList<>();
        String query = """
            SELECT p.uuid, p.username, b.balance
            FROM balances b
            JOIN players p ON b.uuid = p.uuid
            WHERE b.currency = ?
            ORDER BY b.balance DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, currencyId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String username = rs.getString("username");
                    double balance = rs.getDouble("balance");

                    entries.add(new BalanceEntry(uuid, username, balance));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get top balances", e);
        }

        return entries;
    }

    public int getTotalPlayers() {
        String query = "SELECT COUNT(*) as count FROM players";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt("count");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get total players", e);
        }

        return 0;
    }

    @NotNull
    public List<UUID> getAllPlayerUUIDs() {
        List<UUID> uuids = new ArrayList<>();
        String query = "SELECT uuid FROM players";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                uuids.add(UUID.fromString(rs.getString("uuid")));
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get all player UUIDs", e);
        }

        return uuids;
    }

    public void clearCache(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

    public void clearAllCache() {
        cache.clear();
        plugin.getLogger().info("Cleared all database cache");
    }

    public void createBackup() {
        if (!plugin.getConfigManager().isBackupEnabled()) {
            return;
        }

        try {
            // IMPORTANT: Checkpoint WAL before backup
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }

            File backupFolder = new File(plugin.getDataFolder(), "backups");
            if (!backupFolder.exists()) {
                if (!backupFolder.mkdirs()) {
                    throw new IOException("Failed to create backup folder");
                }
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File backupFile = new File(backupFolder, "economy_" + timestamp + ".db");

            Files.copy(databaseFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("Database backup created: " + backupFile.getName());

            cleanOldBackups(backupFolder);

        } catch (IOException | SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create database backup", e);
        }
    }

    private void cleanOldBackups(@NotNull File backupFolder) {
        File[] backups = backupFolder.listFiles((dir, name) -> name.startsWith("economy_") && name.endsWith(".db"));

        if (backups == null || backups.length <= plugin.getConfigManager().getKeepBackups()) {
            return;
        }

        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());

        for (int i = plugin.getConfigManager().getKeepBackups(); i < backups.length; i++) {
            if (backups[i].delete()) {
                plugin.getLogger().info("Deleted old backup: " + backups[i].getName());
            }
        }
    }

    private void startBackupTask() {
        if (!plugin.getConfigManager().isBackupEnabled()) {
            return;
        }

        int interval = plugin.getConfigManager().getBackupInterval();

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::createBackup,
                interval * 20L,
                interval * 20L
        );
    }

    public void close() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                // IMPORTANT: Checkpoint and close WAL properly
                try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                    stmt.execute("PRAGMA optimize");
                }

                dataSource.close();
                plugin.getLogger().info("Database connection pool closed (WAL checkpointed)");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database", e);
        }
    }

    public record BalanceEntry(@NotNull UUID uuid, @NotNull String username, double balance) {}
}