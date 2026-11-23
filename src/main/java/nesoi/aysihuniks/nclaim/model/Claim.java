package nesoi.aysihuniks.nclaim.model;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.RemoveCause;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A claimed area: one main chunk (holding the claim block) plus optional extra chunks ("lands").
 * <p>
 * The claim only stores the world name and chunk coordinates, so nothing here forces a chunk to
 * load. All mutable state is safe to read from other threads (Folia regions, async saving).
 */
public class Claim {

    /** Every loaded claim, indexed by chunk. */
    public static final ClaimRegistry claims = new ClaimRegistry();

    private final String claimId;
    private final String worldName;
    private final int chunkX;
    private final int chunkZ;
    private final Date createdAt;

    private volatile String displayName;
    private volatile String slug;
    private volatile Date expiredAt;
    private volatile UUID owner;
    private volatile Location claimBlockLocation;
    private volatile long claimValue;
    private volatile Material claimBlockType;
    private volatile boolean forSale;
    private volatile double salePrice;

    private final LandSet lands;
    private final List<UUID> coopPlayers = new CopyOnWriteArrayList<>();
    private final Map<UUID, Date> coopPlayerJoinDate = new ConcurrentHashMap<>();
    private final Map<UUID, CoopPermission> coopPermissions = new ConcurrentHashMap<>();
    private final ClaimSetting settings;
    private final Set<Material> purchasedBlockTypes = new CopyOnWriteArraySet<>();
    /** Blocks an admin offered to this claim only, with their price; moved to purchasedBlockTypes once bought. */
    private final Map<Material, Double> blockOffers = new ConcurrentHashMap<>();

    private volatile boolean removed;

    public Claim(@NotNull String claimId,
                 @Nullable String displayName,
                 @Nullable String slug,
                 @NotNull String worldName,
                 int chunkX,
                 int chunkZ,
                 @NotNull Date createdAt,
                 @NotNull Date expiredAt,
                 @NotNull UUID owner,
                 @NotNull Location claimBlockLocation,
                 long claimValue,
                 @Nullable Material claimBlockType,
                 @Nullable Collection<String> lands,
                 @Nullable Collection<UUID> coopPlayers,
                 @Nullable Map<UUID, Date> coopPlayerJoinDate,
                 @Nullable Map<UUID, CoopPermission> coopPermissions,
                 @Nullable ClaimSetting settings,
                 @Nullable Set<Material> purchasedBlockTypes,
                 boolean forSale,
                 double salePrice) {
        this.claimId = claimId;
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.createdAt = createdAt;
        this.displayName = displayName == null || displayName.isBlank() ? null : displayName;
        this.slug = slug == null || slug.isEmpty() ? toSlug(getDisplayName()) : toSlug(slug);
        this.expiredAt = expiredAt;
        this.owner = owner;
        this.claimBlockLocation = claimBlockLocation.clone();
        this.claimValue = claimValue;
        this.claimBlockType = claimBlockType == null ? Material.OBSIDIAN : claimBlockType;
        this.lands = new LandSet(this, lands);
        this.settings = settings == null ? new ClaimSetting() : settings;
        this.forSale = forSale;
        this.salePrice = salePrice;
        if (coopPlayers != null) {
            for (UUID uuid : coopPlayers) {
                if (uuid == null || uuid.equals(owner) || this.coopPlayers.contains(uuid)) continue;
                this.coopPlayers.add(uuid);
                Date joined = coopPlayerJoinDate == null ? null : coopPlayerJoinDate.get(uuid);
                this.coopPlayerJoinDate.put(uuid, joined == null ? new Date() : joined);
                CoopPermission perm = coopPermissions == null ? null : coopPermissions.get(uuid);
                this.coopPermissions.put(uuid, perm == null ? new CoopPermission() : perm);
            }
        }
        if (purchasedBlockTypes != null) this.purchasedBlockTypes.addAll(purchasedBlockTypes);
        this.purchasedBlockTypes.add(this.claimBlockType);
    }

    /* ---------------- identity / location ---------------- */

    public @NotNull String getClaimId() {
        return claimId;
    }

    public @NotNull String getWorldName() {
        return worldName;
    }

    public @Nullable World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public @NotNull ChunkKey getChunkKey() {
        return new ChunkKey(worldName, chunkX, chunkZ);
    }

    /**
     * The main chunk. <b>Loads the chunk</b> and must be called on the thread owning it;
     * prefer {@link #getChunkX()}, {@link #getChunkZ()} and {@link #getWorldName()}.
     */
    public @NotNull Chunk getChunk() {
        World world = getWorld();
        if (world == null) throw new IllegalStateException("World " + worldName + " is not loaded");
        return world.getChunkAt(chunkX, chunkZ);
    }

    public boolean isMainChunk(@NotNull String world, int x, int z) {
        return chunkX == x && chunkZ == z && worldName.equals(world);
    }

    /** All chunks of the claim (main chunk first) as keys; never loads anything. */
    public @NotNull List<ChunkKey> getAllChunkKeys() {
        List<ChunkKey> keys = new ArrayList<>(lands.size() + 1);
        keys.add(getChunkKey());
        for (String land : lands) {
            ChunkKey key = ChunkKey.parse(land);
            if (key != null) keys.add(key);
        }
        return keys;
    }

    public int getTotalChunkCount() {
        return 1 + lands.size();
    }

    /** Block coordinates of the main chunk centre, as shown to players. */
    public int getCenterX() {
        return (chunkX << 4) + 8;
    }

    public int getCenterZ() {
        return (chunkZ << 4) + 8;
    }

    /* ---------------- data ---------------- */

    public @NotNull String getDisplayName() {
        return displayName != null ? displayName : claimId;
    }

    public @Nullable String getRawDisplayName() {
        return displayName;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? null : displayName;
        markDirty();
    }

    public @NotNull String getSlug() {
        return slug;
    }

    public void setSlug(@NotNull String slug) {
        this.slug = slug;
        markDirty();
    }

    public @NotNull Date getCreatedAt() {
        return createdAt;
    }

    public @NotNull Date getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(@NotNull Date expiredAt) {
        this.expiredAt = expiredAt;
        markDirty();
    }

    public boolean isExpired() {
        return expiredAt.getTime() <= System.currentTimeMillis();
    }

    public @NotNull UUID getOwner() {
        return owner;
    }

    /** Changes the owner. The new owner is removed from the co-op list. */
    public void setOwner(@NotNull UUID newOwner) {
        this.owner = newOwner;
        removeCoop(newOwner);
        markDirty();
    }

    public boolean isOwner(@Nullable UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isMember(@Nullable UUID uuid) {
        return uuid != null && (owner.equals(uuid) || coopPlayers.contains(uuid));
    }

    public Optional<Player> getOwnerPlayer() {
        return Optional.ofNullable(Bukkit.getPlayer(owner));
    }

    public @NotNull Location getClaimBlockLocation() {
        return claimBlockLocation.clone();
    }

    public void setClaimBlockLocation(@NotNull Location location) {
        this.claimBlockLocation = location.clone();
        markDirty();
    }

    public long getClaimValue() {
        return claimValue;
    }

    public void setClaimValue(long claimValue) {
        if (this.claimValue != claimValue) {
            this.claimValue = claimValue;
            markDirty();
        }
    }

    public @NotNull Material getClaimBlockType() {
        return claimBlockType;
    }

    public void setClaimBlockType(@NotNull Material claimBlockType) {
        this.claimBlockType = claimBlockType;
        markDirty();
    }

    /** Extra chunks as {@code world,x,z}. Adding/removing updates the chunk index. */
    public @NotNull Collection<String> getLands() {
        return lands;
    }

    public @NotNull List<UUID> getCoopPlayers() {
        return coopPlayers;
    }

    public @NotNull Map<UUID, Date> getCoopPlayerJoinDate() {
        return coopPlayerJoinDate;
    }

    public @NotNull Map<UUID, CoopPermission> getCoopPermissions() {
        return coopPermissions;
    }

    public @NotNull CoopPermission getCoopPermission(@NotNull UUID uuid) {
        return coopPermissions.computeIfAbsent(uuid, u -> new CoopPermission());
    }

    public void addCoop(@NotNull UUID uuid) {
        if (owner.equals(uuid) || coopPlayers.contains(uuid)) return;
        coopPlayers.add(uuid);
        coopPlayerJoinDate.put(uuid, new Date());
        coopPermissions.put(uuid, new CoopPermission());
        markDirty();
    }

    public void removeCoop(@NotNull UUID uuid) {
        boolean changed = coopPlayers.remove(uuid);
        coopPlayerJoinDate.remove(uuid);
        changed |= coopPermissions.remove(uuid) != null;
        if (changed) markDirty();
    }

    public void clearCoops() {
        coopPlayers.clear();
        coopPlayerJoinDate.clear();
        coopPermissions.clear();
        markDirty();
    }

    public @NotNull ClaimSetting getSettings() {
        return settings;
    }

    public @NotNull Set<Material> getPurchasedBlockTypes() {
        return purchasedBlockTypes;
    }

    public @NotNull Map<Material, Double> getBlockOffers() {
        return blockOffers;
    }

    public boolean isForSale() {
        return forSale;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
        markDirty();
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = salePrice;
        markDirty();
    }

    public boolean isRemoved() {
        return removed;
    }

    void setRemoved() {
        this.removed = true;
    }

    /** Queues this claim for the next asynchronous save. */
    public void markDirty() {
        NClaim plugin = NClaim.inst();
        if (plugin != null && plugin.getStorage() != null && claims.isRegistered(this)) {
            plugin.getStorage().markDirty(this);
        }
    }

    /* ---------------- actions ---------------- */

    /** Removes the claim (claim block, hologram, storage). Safe to call from any thread. */
    public void remove(@NotNull RemoveCause cause) {
        NClaim.inst().getClaimService().removeClaim(this, cause);
    }

    /** Internal: called by the claim service once removal is final. */
    public void markRemoved() {
        setRemoved();
    }

    /** @deprecated reads blocks; only call on the claim block's region thread. */
    @Deprecated
    public boolean isSafeToTeleport() {
        Location loc = getClaimBlockLocation();
        return loc.clone().add(0, 1, 0).getBlock().isPassable()
                && loc.clone().add(0, 2, 0).getBlock().isPassable();
    }

    /** Folia-safe teleport to the claim block; see {@link nesoi.aysihuniks.nclaim.service.ClaimService#teleportToClaim}. */
    public void teleport(@NotNull Player teleporter) {
        NClaim.inst().getClaimService().teleportToClaim(teleporter, this);
    }

    /* ---------------- static helpers ---------------- */

    @Nullable
    public static Claim getClaim(@Nullable Chunk chunk) {
        return claims.getAt(chunk);
    }

    @Nullable
    public static Claim getClaim(@Nullable Location location) {
        return claims.getAt(location);
    }

    public static Collection<Claim> getClaims() {
        return claims;
    }

    public static @NotNull String toSlug(@NotNull String input) {
        String s = input.trim().toLowerCase(Locale.ROOT)
                .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
                .replace("ş", "s").replace("ö", "o").replace("ç", "c")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.length() > 24) s = s.substring(0, 24).replaceAll("-+$", "");
        return s.isEmpty() ? "claim" : s;
    }

    @Nullable
    public static Claim getClaimByIdentifier(@Nullable UUID owner, @Nullable String identifier) {
        if (owner == null || identifier == null || identifier.isBlank()) return null;
        String id = identifier.trim();
        Claim byId = null;
        for (Claim c : claims) {
            if (!owner.equals(c.getOwner())) continue;
            if (c.getSlug().equalsIgnoreCase(id)) return c;
            if (byId == null && c.getClaimId().equalsIgnoreCase(id)) byId = c;
        }
        return byId;
    }

    @Nullable
    public static Claim getClaimByName(@Nullable UUID owner, @Nullable String name) {
        if (owner == null || name == null || name.isBlank()) return null;
        String n = name.trim();
        for (Claim c : claims) {
            if (owner.equals(c.getOwner()) && c.getDisplayName().equalsIgnoreCase(n)) return c;
        }
        return null;
    }

    /** Makes {@code base} unique among the owner's claims by appending {@code -2}, {@code -3}, ... */
    @NotNull
    public static String uniqueSlug(@NotNull UUID owner, @NotNull String base, @Nullable String ignoreClaimId) {
        String candidate = base;
        for (int i = 2; ; i++) {
            final String check = candidate;
            boolean taken = claims.stream().anyMatch(c -> owner.equals(c.getOwner())
                    && c.getSlug().equalsIgnoreCase(check)
                    && (ignoreClaimId == null || !c.getClaimId().equalsIgnoreCase(ignoreClaimId)));
            if (!taken) return candidate;
            String suffix = "-" + i;
            int max = 24 - suffix.length();
            candidate = (base.length() > max ? base.substring(0, Math.max(1, max)) : base) + suffix;
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Claim other && claimId.equals(other.claimId));
    }

    @Override
    public int hashCode() {
        return claimId.hashCode();
    }
}
