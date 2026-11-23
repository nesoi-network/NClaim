package nesoi.aysihuniks.nclaim.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nesoi.aysihuniks.nclaim.Config;
import nesoi.aysihuniks.nclaim.enums.Permission;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.ClaimSetting;
import nesoi.aysihuniks.nclaim.model.CoopPermission;
import nesoi.aysihuniks.nclaim.model.User;
import nesoi.aysihuniks.nclaim.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * MySQL / SQLite storage. The schema is the same one earlier NClaim versions created,
 * so existing databases keep working without migration.
 */
public final class SqlBackend implements StorageBackend {

    public enum Dialect { MYSQL, SQLITE }

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST = new TypeToken<List<String>>() { }.getType();
    private static final Type STRING_DOUBLE_MAP = new TypeToken<Map<String, Double>>() { }.getType();
    private static final Type BOOL_MAP = new TypeToken<Map<String, Boolean>>() { }.getType();

    private final Dialect dialect;
    private final HikariDataSource dataSource;

    public SqlBackend(@NotNull Dialect dialect, @NotNull Config config, @NotNull File dataFolder) throws SQLException {
        this.dialect = dialect;
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("NClaim-" + dialect.name());
        if (dialect == Dialect.MYSQL) {
            hikari.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort() + "/"
                    + config.getMysqlDatabase() + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    + "&createDatabaseIfNotExist=true&rewriteBatchedStatements=true&characterEncoding=utf8");
            hikari.setUsername(config.getMysqlUser());
            hikari.setPassword(config.getMysqlPassword());
            hikari.setMaximumPoolSize(Math.max(2, config.getMaximumPoolSize()));
            hikari.setMinimumIdle(Math.max(1, Math.min(config.getMinimumIdle(), config.getMaximumPoolSize())));
            hikari.setIdleTimeout(config.getIdleTimeout());
            hikari.setMaxLifetime(config.getMaxLifetime());
            hikari.setConnectionTimeout(config.getConnectionTimeout());
        } else {
            File file = new File(dataFolder, config.getSqliteFile());
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            // SQLite allows a single writer; one connection avoids "database is locked".
            hikari.setMaximumPoolSize(1);
            hikari.setMinimumIdle(1);
            // sqlite-jdbc reads these driver properties as PRAGMAs when the connection opens.
            hikari.addDataSourceProperty("journal_mode", "WAL");
            hikari.addDataSourceProperty("synchronous", "NORMAL");
            hikari.addDataSourceProperty("busy_timeout", "5000");
        }
        this.dataSource = new HikariDataSource(hikari);
        createSchema();
    }

    @Override
    public @NotNull String name() {
        return dialect.name();
    }

    private Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createSchema() throws SQLException {
        boolean my = dialect == Dialect.MYSQL;
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.executeUpdate(my
                    ? "CREATE TABLE IF NOT EXISTS users (uuid VARCHAR(36) NOT NULL PRIMARY KEY, balance DOUBLE DEFAULT 0, skinTexture TEXT)"
                    : "CREATE TABLE IF NOT EXISTS users (uuid TEXT PRIMARY KEY, balance REAL DEFAULT 0, skinTexture TEXT)");
            st.executeUpdate(my
                    ? "CREATE TABLE IF NOT EXISTS claims (claim_id VARCHAR(100) PRIMARY KEY, name VARCHAR(24) NULL, slug VARCHAR(24) NULL, "
                    + "chunk_world VARCHAR(100), chunk_x INT, chunk_z INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "expired_at TIMESTAMP NULL, owner VARCHAR(36), claim_block_location TEXT, lands TEXT, claim_value BIGINT DEFAULT 0, "
                    + "claim_block_type VARCHAR(50), purchased_blocks TEXT, for_sale BOOLEAN DEFAULT FALSE, sale_price DOUBLE DEFAULT 0, "
                    + "block_offers TEXT)"
                    : "CREATE TABLE IF NOT EXISTS claims (claim_id TEXT PRIMARY KEY, name TEXT, slug TEXT, chunk_world TEXT, chunk_x INTEGER, "
                    + "chunk_z INTEGER, created_at TEXT DEFAULT CURRENT_TIMESTAMP, expired_at TEXT NULL, owner TEXT, claim_block_location TEXT, "
                    + "lands TEXT, claim_value BIGINT DEFAULT 0, claim_block_type TEXT, purchased_blocks TEXT, for_sale INTEGER DEFAULT 0, "
                    + "sale_price REAL DEFAULT 0, block_offers TEXT)");
            st.executeUpdate(my
                    ? "CREATE TABLE IF NOT EXISTS claim_coops (claim_id VARCHAR(100), player_uuid VARCHAR(36), joined_at TIMESTAMP NULL, "
                    + "permissions TEXT, PRIMARY KEY (claim_id, player_uuid))"
                    : "CREATE TABLE IF NOT EXISTS claim_coops (claim_id TEXT, player_uuid TEXT, joined_at TEXT, permissions TEXT, "
                    + "PRIMARY KEY (claim_id, player_uuid))");
            st.executeUpdate(my
                    ? "CREATE TABLE IF NOT EXISTS claim_settings (claim_id VARCHAR(100) PRIMARY KEY, settings TEXT)"
                    : "CREATE TABLE IF NOT EXISTS claim_settings (claim_id TEXT PRIMARY KEY, settings TEXT)");
            // Columns added by later versions; ignore "duplicate column" errors.
            for (String column : List.of("name VARCHAR(24) NULL", "slug VARCHAR(24) NULL",
                    "for_sale BOOLEAN DEFAULT FALSE", "sale_price DOUBLE DEFAULT 0", "block_offers TEXT")) {
                try {
                    st.executeUpdate("ALTER TABLE claims ADD COLUMN " + column);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /* ---------------- timestamps (SQLite stores text, MySQL native TIMESTAMP) ---------------- */

    private void setTime(PreparedStatement ps, int index, @Nullable Date date) throws SQLException {
        if (date == null) {
            ps.setNull(index, dialect == Dialect.MYSQL ? Types.TIMESTAMP : Types.VARCHAR);
        } else if (dialect == Dialect.MYSQL) {
            ps.setTimestamp(index, new Timestamp(date.getTime()));
        } else {
            ps.setString(index, new Timestamp(date.getTime()).toString());
        }
    }

    @Nullable
    private Date getTime(ResultSet rs, String column) throws SQLException {
        if (dialect == Dialect.MYSQL) {
            Timestamp ts = rs.getTimestamp(column);
            return ts == null ? null : new Date(ts.getTime());
        }
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) return null;
        try {
            return new Date(Timestamp.valueOf(raw.trim()).getTime());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /* ---------------- claims ---------------- */

    @Override
    public @NotNull List<Claim> loadClaims(@Nullable String world) throws SQLException {
        List<Claim> out = new ArrayList<>();
        String sql = world == null ? "SELECT * FROM claims" : "SELECT * FROM claims WHERE chunk_world = ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (world != null) ps.setString(1, world);
            Map<String, ClaimRow> rows = new LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String w = rs.getString("chunk_world");
                    if (w == null || Bukkit.getWorld(w) == null) continue; // loaded once the world loads
                    ClaimRow row = readRow(rs);
                    if (row != null) rows.put(row.id, row);
                }
            }
            if (rows.isEmpty()) return out;
            loadCoops(c, rows);
            loadSettings(c, rows);
            for (ClaimRow row : rows.values()) {
                Claim claim = new Claim(row.id, row.name, row.slug, row.world, row.x, row.z, row.createdAt, row.expiredAt,
                        row.owner, row.block, row.value, row.blockType, row.lands, row.coops, row.joinDates, row.perms,
                        row.settings, row.purchased, row.forSale, row.salePrice);
                claim.getBlockOffers().putAll(row.offers);
                out.add(claim);
            }
        }
        return out;
    }

    private static final class ClaimRow {
        String id, name, slug, world;
        int x, z;
        Date createdAt, expiredAt;
        UUID owner;
        Location block;
        long value;
        Material blockType;
        List<String> lands = new ArrayList<>();
        Set<Material> purchased = new HashSet<>();
        final Map<Material, Double> offers = new HashMap<>();
        boolean forSale;
        double salePrice;
        final List<UUID> coops = new ArrayList<>();
        final Map<UUID, Date> joinDates = new HashMap<>();
        final Map<UUID, CoopPermission> perms = new HashMap<>();
        final ClaimSetting settings = new ClaimSetting();
    }

    @Nullable
    private ClaimRow readRow(ResultSet rs) throws SQLException {
        ClaimRow row = new ClaimRow();
        row.id = rs.getString("claim_id");
        try {
            row.world = rs.getString("chunk_world");
            row.x = rs.getInt("chunk_x");
            row.z = rs.getInt("chunk_z");
            row.name = rs.getString("name");
            row.slug = rs.getString("slug");
            row.createdAt = getTime(rs, "created_at");
            row.expiredAt = getTime(rs, "expired_at");
            row.owner = UUID.fromString(rs.getString("owner"));
            row.block = Codec.location(rs.getString("claim_block_location"));
            row.value = rs.getLong("claim_value");
            row.blockType = Codec.material(rs.getString("claim_block_type"));
            List<String> lands = GSON.fromJson(rs.getString("lands"), STRING_LIST);
            if (lands != null) row.lands = lands;
            List<String> purchased = GSON.fromJson(rs.getString("purchased_blocks"), STRING_LIST);
            if (purchased != null) {
                for (String name : purchased) {
                    Material m = Codec.material(name);
                    if (m != null) row.purchased.add(m);
                }
            }
            row.forSale = rs.getBoolean("for_sale");
            row.salePrice = rs.getDouble("sale_price");
            Map<String, Double> offers = GSON.fromJson(rs.getString("block_offers"), STRING_DOUBLE_MAP);
            if (offers != null) {
                offers.forEach((name, price) -> {
                    Material m = Codec.material(name);
                    if (m != null && price != null) row.offers.put(m, Math.max(0, price));
                });
            }
        } catch (Exception e) {
            Msg.log("&cSkipping broken claim row '" + row.id + "': " + e.getMessage());
            return null;
        }
        if (row.createdAt == null) row.createdAt = new Date();
        if (row.expiredAt == null || row.block == null) {
            Msg.log("&cSkipping claim '" + row.id + "': missing expiry date or claim block location.");
            return null;
        }
        return row;
    }

    private void loadCoops(Connection c, Map<String, ClaimRow> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM claim_coops"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ClaimRow row = rows.get(rs.getString("claim_id"));
                if (row == null) continue;
                UUID uuid;
                try {
                    uuid = UUID.fromString(rs.getString("player_uuid"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                row.coops.add(uuid);
                row.joinDates.put(uuid, getTime(rs, "joined_at"));
                CoopPermission permission = new CoopPermission();
                Map<String, Boolean> map = GSON.fromJson(rs.getString("permissions"), BOOL_MAP);
                if (map != null) {
                    map.forEach((key, value) -> {
                        Permission p = Permission.byName(key);
                        if (p != null) permission.setEnabled(p, Boolean.TRUE.equals(value));
                    });
                }
                row.perms.put(uuid, permission);
            }
        }
    }

    private void loadSettings(Connection c, Map<String, ClaimRow> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM claim_settings"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ClaimRow row = rows.get(rs.getString("claim_id"));
                if (row == null) continue;
                Map<String, Boolean> map = GSON.fromJson(rs.getString("settings"), BOOL_MAP);
                if (map == null) continue;
                map.forEach((key, value) -> {
                    Setting s = Setting.byName(key);
                    if (s != null) row.settings.set(s, Boolean.TRUE.equals(value));
                });
            }
        }
    }

    @Override
    public void saveClaims(@NotNull Collection<Claim> claims) throws SQLException {
        if (claims.isEmpty()) return;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement claim = c.prepareStatement(
                    "REPLACE INTO claims (claim_id, name, slug, chunk_world, chunk_x, chunk_z, created_at, expired_at, owner, "
                            + "claim_block_location, lands, claim_value, claim_block_type, purchased_blocks, for_sale, sale_price, block_offers) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement deleteCoops = c.prepareStatement("DELETE FROM claim_coops WHERE claim_id = ?");
                 PreparedStatement coop = c.prepareStatement(
                         "INSERT INTO claim_coops (claim_id, player_uuid, joined_at, permissions) VALUES (?, ?, ?, ?)");
                 PreparedStatement settings = c.prepareStatement("REPLACE INTO claim_settings (claim_id, settings) VALUES (?, ?)")) {

                for (Claim cl : claims) {
                    claim.setString(1, cl.getClaimId());
                    claim.setString(2, cl.getRawDisplayName());
                    claim.setString(3, cl.getSlug());
                    claim.setString(4, cl.getWorldName());
                    claim.setInt(5, cl.getChunkX());
                    claim.setInt(6, cl.getChunkZ());
                    setTime(claim, 7, cl.getCreatedAt());
                    setTime(claim, 8, cl.getExpiredAt());
                    claim.setString(9, cl.getOwner().toString());
                    claim.setString(10, Codec.location(cl.getClaimBlockLocation()));
                    claim.setString(11, GSON.toJson(new ArrayList<>(cl.getLands())));
                    claim.setLong(12, cl.getClaimValue());
                    claim.setString(13, cl.getClaimBlockType().name());
                    claim.setString(14, GSON.toJson(cl.getPurchasedBlockTypes().stream().map(Material::name).sorted().toList()));
                    claim.setBoolean(15, cl.isForSale());
                    claim.setDouble(16, cl.getSalePrice());
                    Map<String, Double> offers = new java.util.TreeMap<>();
                    cl.getBlockOffers().forEach((material, price) -> offers.put(material.name(), price));
                    claim.setString(17, GSON.toJson(offers));
                    claim.addBatch();

                    deleteCoops.setString(1, cl.getClaimId());
                    deleteCoops.addBatch();
                    for (UUID uuid : cl.getCoopPlayers()) {
                        Map<String, Boolean> perms = new LinkedHashMap<>();
                        CoopPermission permission = cl.getCoopPermission(uuid);
                        for (Permission p : Permission.values()) perms.put(p.name(), permission.isEnabled(p));
                        coop.setString(1, cl.getClaimId());
                        coop.setString(2, uuid.toString());
                        setTime(coop, 3, cl.getCoopPlayerJoinDate().get(uuid));
                        coop.setString(4, GSON.toJson(perms));
                        coop.addBatch();
                    }

                    Map<String, Boolean> settingMap = new LinkedHashMap<>();
                    for (Setting s : Setting.values()) settingMap.put(s.name(), cl.getSettings().isEnabled(s));
                    settings.setString(1, cl.getClaimId());
                    settings.setString(2, GSON.toJson(settingMap));
                    settings.addBatch();
                }
                claim.executeBatch();
                deleteCoops.executeBatch();
                coop.executeBatch();
                settings.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void deleteClaims(@NotNull Collection<String> claimIds) throws SQLException {
        if (claimIds.isEmpty()) return;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                for (String table : List.of("claim_coops", "claim_settings", "claims")) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE claim_id = ?")) {
                        for (String id : claimIds) {
                            ps.setString(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public int countClaims() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM claims")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /* ---------------- users ---------------- */

    @Override
    public @NotNull User loadUser(@NotNull UUID uuid) throws SQLException {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT balance, skinTexture FROM users WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new User(uuid, rs.getDouble("balance"), rs.getString("skinTexture"));
            }
        }
        return new User(uuid, 0, null);
    }

    @Override
    public void saveUsers(@NotNull Collection<User> users) throws SQLException {
        if (users.isEmpty()) return;
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(
                "REPLACE INTO users (uuid, balance, skinTexture) VALUES (?, ?, ?)")) {
            for (User user : users) {
                ps.setString(1, user.getUuid().toString());
                ps.setDouble(2, user.getBalance());
                ps.setString(3, user.getSkinTexture());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) dataSource.close();
    }
}
