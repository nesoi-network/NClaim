package nesoi.aysihuniks.nclaim.storage;

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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * claims.yml + players/&lt;uuid&gt;.yml storage.
 * <p>
 * Claims whose world is not loaded are kept verbatim and written back untouched, so a world
 * that loads late (or not at all this session) never loses its claims.
 */
public final class YamlBackend implements StorageBackend {

    private static final String ROOT = "chunks_claimed";
    private static final Pattern LEGACY_ID = Pattern.compile("(.+)_(-?\\d+)_(-?\\d+)");

    private final File claimsFile;
    private final File playersFolder;
    /** Raw sections of claims whose world is not loaded (id -> section values). */
    private final Map<String, Map<String, Object>> pending = new ConcurrentHashMap<>();
    /** Ids deleted this session, so pending copies are not resurrected. */
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();

    public YamlBackend(@NotNull File dataFolder) {
        this.claimsFile = new File(dataFolder, "claims.yml");
        this.playersFolder = new File(dataFolder, "players");
    }

    @Override
    public @NotNull String name() {
        return "YAML";
    }

    /* ---------------- claims ---------------- */

    @Override
    public @NotNull List<Claim> loadClaims(@Nullable String world) {
        List<Claim> out = new ArrayList<>();
        if (!claimsFile.exists()) return out;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(claimsFile);
        ConfigurationSection root = config.getConfigurationSection(ROOT);
        if (root == null) return out;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null || deleted.contains(id)) continue;
            String claimWorld = worldOf(id, section);
            if (claimWorld == null) {
                Msg.log("&cSkipping claim '" + id + "': cannot determine its world.");
                continue;
            }
            if (world != null && !world.equals(claimWorld)) continue;
            if (Bukkit.getWorld(claimWorld) == null) {
                pending.put(id, section.getValues(true));
                continue;
            }
            try {
                Claim claim = readClaim(id, claimWorld, section);
                if (claim != null) {
                    out.add(claim);
                    pending.remove(id);
                }
            } catch (Exception e) {
                Msg.log("&cFailed to load claim '" + id + "': " + e.getMessage());
                pending.put(id, section.getValues(true)); // keep it on disk
            }
        }
        return out;
    }

    @Nullable
    private static String worldOf(String id, ConfigurationSection section) {
        String world = section.getString("world");
        if (world != null) return world;
        String fromBlock = Codec.locationWorld(section.getString("claim_block_location"));
        if (fromBlock != null) return fromBlock;
        Matcher m = LEGACY_ID.matcher(id);
        return m.matches() ? m.group(1) : null;
    }

    @Nullable
    private Claim readClaim(String id, String world, ConfigurationSection s) {
        int chunkX;
        int chunkZ;
        if (s.contains("chunk_x") && s.contains("chunk_z")) {
            chunkX = s.getInt("chunk_x");
            chunkZ = s.getInt("chunk_z");
        } else {
            Matcher m = LEGACY_ID.matcher(id);
            if (m.matches()) {
                chunkX = Integer.parseInt(m.group(2));
                chunkZ = Integer.parseInt(m.group(3));
            } else {
                Location loc = Codec.location(s.getString("claim_block_location"));
                if (loc == null) return null;
                chunkX = loc.getBlockX() >> 4;
                chunkZ = loc.getBlockZ() >> 4;
            }
        }

        Date createdAt = Codec.date(s.getString("created_at"));
        Date expiredAt = Codec.date(s.getString("expired_at"));
        Location block = Codec.location(s.getString("claim_block_location"));
        String ownerRaw = s.getString("owner");
        if (createdAt == null || expiredAt == null || block == null || ownerRaw == null) {
            Msg.log("&cClaim '" + id + "' is missing required data, skipping.");
            return null;
        }

        Material blockType = Codec.material(s.getString("claim_block_type"));
        Set<Material> purchased = new HashSet<>();
        for (String name : s.getStringList("purchased_blocks")) {
            Material m = Codec.material(name);
            if (m != null) purchased.add(m);
        }

        List<UUID> coops = new ArrayList<>();
        Map<UUID, Date> joinDates = new HashMap<>();
        Map<UUID, CoopPermission> perms = new HashMap<>();
        ConfigurationSection coopSection = s.getConfigurationSection("coops");
        if (coopSection != null) {
            for (String raw : coopSection.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(raw);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                coops.add(uuid);
                joinDates.put(uuid, Codec.date(coopSection.getString(raw + ".joined_at")));
                CoopPermission permission = new CoopPermission();
                ConfigurationSection permSection = coopSection.getConfigurationSection(raw + ".permissions");
                if (permSection != null) {
                    for (String key : permSection.getKeys(false)) {
                        Permission p = Permission.byName(key);
                        if (p != null) permission.setEnabled(p, permSection.getBoolean(key));
                    }
                }
                perms.put(uuid, permission);
            }
        }

        ClaimSetting settings = new ClaimSetting();
        ConfigurationSection settingSection = s.getConfigurationSection("settings");
        if (settingSection != null) {
            for (String key : settingSection.getKeys(false)) {
                Setting setting = Setting.byName(key);
                if (setting != null) settings.set(setting, settingSection.getBoolean(key));
            }
        }

        Claim claim = new Claim(id, s.getString("name"), s.getString("slug"), world, chunkX, chunkZ,
                createdAt, expiredAt, UUID.fromString(ownerRaw), block, s.getLong("value", 0),
                blockType, s.getStringList("lands"), coops, joinDates, perms, settings, purchased,
                s.getBoolean("for_sale", false), s.getDouble("sale_price", 0));
        ConfigurationSection offers = s.getConfigurationSection("block_offers");
        if (offers != null) {
            for (String key : offers.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null) claim.getBlockOffers().put(m, Math.max(0, offers.getDouble(key)));
            }
        }
        return claim;
    }

    @Override
    public synchronized void saveClaims(@NotNull Collection<Claim> ignored) throws IOException {
        // YAML is a single document, so every save rewrites the whole file from memory.
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Map<String, Object>> entry : pending.entrySet()) {
            if (deleted.contains(entry.getKey())) continue;
            for (Map.Entry<String, Object> value : entry.getValue().entrySet()) {
                if (!(value.getValue() instanceof ConfigurationSection)) {
                    config.set(ROOT + "." + entry.getKey() + "." + value.getKey(), value.getValue());
                }
            }
        }
        for (Claim claim : Claim.claims) {
            write(config, ROOT + "." + claim.getClaimId(), claim);
        }
        writeAtomically(config, claimsFile);
    }

    private static void write(YamlConfiguration config, String ns, Claim claim) {
        config.set(ns + ".world", claim.getWorldName());
        config.set(ns + ".chunk_x", claim.getChunkX());
        config.set(ns + ".chunk_z", claim.getChunkZ());
        config.set(ns + ".name", claim.getRawDisplayName());
        config.set(ns + ".slug", claim.getSlug());
        config.set(ns + ".created_at", Codec.date(claim.getCreatedAt()));
        config.set(ns + ".expired_at", Codec.date(claim.getExpiredAt()));
        config.set(ns + ".owner", claim.getOwner().toString());
        config.set(ns + ".claim_block_location", Codec.location(claim.getClaimBlockLocation()));
        config.set(ns + ".lands", new ArrayList<>(claim.getLands()));
        config.set(ns + ".value", claim.getClaimValue());
        config.set(ns + ".claim_block_type", claim.getClaimBlockType().name());
        config.set(ns + ".for_sale", claim.isForSale());
        config.set(ns + ".sale_price", claim.getSalePrice());
        config.set(ns + ".purchased_blocks", claim.getPurchasedBlockTypes().stream().map(Material::name).sorted().toList());
        config.set(ns + ".block_offers", null);
        claim.getBlockOffers().forEach((material, price) -> config.set(ns + ".block_offers." + material.name(), price));

        for (UUID coop : claim.getCoopPlayers()) {
            String path = ns + ".coops." + coop;
            config.set(path + ".joined_at", Codec.date(claim.getCoopPlayerJoinDate().get(coop)));
            CoopPermission permission = claim.getCoopPermission(coop);
            for (Permission p : Permission.values()) {
                config.set(path + ".permissions." + p.name(), permission.isEnabled(p));
            }
        }
        for (Setting setting : Setting.values()) {
            config.set(ns + ".settings." + setting.name(), claim.getSettings().isEnabled(setting));
        }
    }

    @Override
    public void deleteClaims(@NotNull Collection<String> claimIds) throws IOException {
        deleted.addAll(claimIds);
        claimIds.forEach(pending::remove);
        saveClaims(List.of());
    }

    @Override
    public int countClaims() {
        if (!claimsFile.exists()) return 0;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(claimsFile).getConfigurationSection(ROOT);
        return root == null ? 0 : root.getKeys(false).size();
    }

    /* ---------------- users ---------------- */

    @Override
    public @NotNull User loadUser(@NotNull UUID uuid) {
        File file = new File(playersFolder, uuid + ".yml");
        if (!file.exists()) return new User(uuid, 0, null);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return new User(uuid, config.getDouble("balance", 0), config.getString("skinTexture"));
    }

    @Override
    public void saveUsers(@NotNull Collection<User> users) throws IOException {
        if (!playersFolder.exists() && !playersFolder.mkdirs()) {
            throw new IOException("Cannot create " + playersFolder);
        }
        for (User user : users) {
            YamlConfiguration config = new YamlConfiguration();
            config.set("balance", user.getBalance());
            config.set("skinTexture", user.getSkinTexture());
            writeAtomically(config, new File(playersFolder, user.getUuid() + ".yml"));
        }
    }

    /** Writes to a temp file first so a crash mid-write never corrupts the real file. */
    private static void writeAtomically(YamlConfiguration config, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File tmp = new File(parent, target.getName() + ".tmp");
        config.save(tmp);
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
    }
}
