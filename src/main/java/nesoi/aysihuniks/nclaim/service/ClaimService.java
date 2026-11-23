package nesoi.aysihuniks.nclaim.service;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import nesoi.aysihuniks.nclaim.Config;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.api.events.ClaimBuyLandEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimCreateEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimRemoveEvent;
import nesoi.aysihuniks.nclaim.enums.RemoveCause;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.model.ChunkKey;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.ClaimSetting;
import nesoi.aysihuniks.nclaim.model.SettingCfg;
import nesoi.aysihuniks.nclaim.utils.LangManager;
import nesoi.aysihuniks.nclaim.utils.Msg;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import nesoi.aysihuniks.nclaim.utils.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Creating, expanding, shrinking, selling and removing claims. */
public class ClaimService {

    public static final int MAX_NAME_LENGTH = 24;

    private final NClaim plugin;
    private final Map<UUID, Long> lastClaimTime = new ConcurrentHashMap<>();

    public ClaimService(@NotNull NClaim plugin) {
        this.plugin = plugin;
    }

    private LangManager lang() {
        return plugin.getLangManager();
    }

    private Config config() {
        return plugin.getNconfig();
    }

    private static boolean bypass(Player player, String node) {
        return player.hasPermission("nclaim.bypass.*") || player.hasPermission("nclaim.bypass." + node);
    }

    /* ---------------- buying a claim ---------------- */

    /** Claims the chunk the player stands in. Must run on the player's thread. */
    public void buyNewClaim(@NotNull Player player, @Nullable String name) {
        long cooldownMillis = config().getLastClaimTime() * 60_000L;
        long now = System.currentTimeMillis();
        Long last = lastClaimTime.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis && !bypass(player, "buy_cooldown")) {
            long secondsLeft = Math.max(0, (cooldownMillis - (now - last)) / 1000);
            Msg.send(player, lang().getString("claim.cooldown").replace("{time}", lang().formatDuration(secondsLeft)));
            return;
        }

        String displayName = name == null || name.isBlank() ? null : name.trim();
        if (displayName != null && displayName.length() > MAX_NAME_LENGTH) {
            displayName = displayName.substring(0, MAX_NAME_LENGTH);
        }
        if (displayName != null && config().isBlacklistedName(displayName)) {
            Msg.send(player, lang().getString("claim.name_blacklisted"));
            return;
        }

        Location location = player.getLocation();
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;

        if (!canCreateClaim(player, world, chunkX, chunkZ, location)) return;

        double price = bypass(player, "claim_buy_price") ? 0 : config().getClaimBuyPrice();
        if (!plugin.getEconomyService().has(player, price)) {
            Msg.send(player, lang().getString("command.balance.not_enough"));
            return;
        }

        String claimId = world.getName() + "_" + chunkX + "_" + chunkZ;
        if (displayName == null) displayName = config().defaultClaimName(player.getName(), claimId);
        // No name and no default: the slug falls back to the id (e.g. world-12-4).
        String slug = Claim.uniqueSlug(player.getUniqueId(), Claim.toSlug(displayName != null ? displayName : claimId), null);

        Block block = location.getBlock();
        Material blockType = config().getDefaultClaimBlockType();
        Claim claim = new Claim(claimId, displayName, slug, world.getName(), chunkX, chunkZ,
                new Date(), expiryFromNow(), player.getUniqueId(), block.getLocation(),
                0, blockType, null, null, null, null, defaultSettings(), Set.of(blockType), false, 0);

        ClaimCreateEvent event = new ClaimCreateEvent(player, claim);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Msg.send(player, lang().getString("claim.buy_cancelled"));
            return;
        }

        // Charge only once everything else has succeeded.
        if (!plugin.getEconomyService().withdraw(player, price)) {
            Msg.send(player, lang().getString("command.balance.not_enough"));
            return;
        }

        block.setType(blockType);
        Claim.claims.add(claim);
        claim.markDirty();
        lastClaimTime.put(player.getUniqueId(), now);

        if (plugin.getHologramManager() != null) plugin.getHologramManager().spawn(claim);
        plugin.getBlockValueManager().recalculate(claim);
        Sounds.playAt(block.getLocation().add(0.5, 0.5, 0.5), plugin.getConfigManager().getString("sounds.claim_create", "BLOCK_BEACON_ACTIVATE"), 1f, 1f);
        Msg.send(player, lang().getString("claim.received"));
    }

    private boolean canCreateClaim(Player player, World world, int chunkX, int chunkZ, Location location) {
        if (config().getBlacklistedWorlds().contains(world.getName()) && !bypass(player, "blacklisted_worlds")) {
            Msg.send(player, lang().getString("claim.in_blacklisted_region_or_world"));
            return false;
        }
        if (isInBlacklistedRegion(location) && !bypass(player, "blacklisted_regions")) {
            Msg.send(player, lang().getString("claim.in_blacklisted_region_or_world"));
            return false;
        }
        if (Claim.claims.getAt(world, chunkX, chunkZ) != null) {
            Msg.send(player, lang().getString("claim.already_claimed"));
            return false;
        }
        if (!player.hasPermission("nclaim.buy")) {
            Msg.send(player, lang().getString("command.permission_denied"));
            return false;
        }
        long owned = Claim.claims.stream().filter(c -> c.isOwner(player.getUniqueId())).count();
        if (owned >= config().getMaxClaimCount(player)) {
            Msg.send(player, lang().getString("claim.max_reached"));
            return false;
        }
        if (isTooCloseToOthers(player, world.getName(), chunkX, chunkZ, null)) {
            Msg.send(player, lang().getString("claim.too_close_to_other_claim"));
            return false;
        }
        return true;
    }

    private ClaimSetting defaultSettings() {
        ClaimSetting settings = new ClaimSetting();
        for (Setting setting : Setting.values()) {
            SettingCfg cfg = plugin.getGuiLangManager().getSettingConfig(setting);
            if (cfg != null) settings.set(setting, cfg.isDefaultValue());
        }
        return settings;
    }

    private Date expiryFromNow() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, config().getClaimExpiryDays());
        return calendar.getTime();
    }

    /**
     * Whether a chunk is within the configured distance of a claim the player is not allowed to
     * border. Uses the chunk index only, so no chunk is loaded.
     */
    public boolean isTooCloseToOthers(@NotNull Player player, @NotNull String world, int chunkX, int chunkZ, @Nullable Claim ownClaim) {
        if (bypass(player, "claim_nearby_others")) return false;
        int distance = Math.max(0, config().getClaimDistanceChunks());
        boolean coopBypass = config().isClaimDistanceCoopBypass();
        for (int x = chunkX - distance; x <= chunkX + distance; x++) {
            for (int z = chunkZ - distance; z <= chunkZ + distance; z++) {
                Claim nearby = Claim.claims.getAt(world, x, z);
                if (nearby == null || nearby == ownClaim || nearby.isOwner(player.getUniqueId())) continue;
                if (coopBypass && nearby.getCoopPlayers().contains(player.getUniqueId())) continue;
                return true;
            }
        }
        return false;
    }

    /* ---------------- lands ---------------- */

    /** Price of the next chunk, or {@code -1} when the claim reached the maximum size. */
    public double calculateLandPrice(@NotNull Claim claim) {
        int next = claim.getTotalChunkCount() + 1;
        if (next > config().getMaxClaimChunks()) return -1;
        return config().getTieredPrice(next);
    }

    public static boolean isAdjacent(@NotNull Claim claim, @NotNull ChunkKey target) {
        for (ChunkKey key : claim.getAllChunkKeys()) {
            if (!key.world().equals(target.world())) continue;
            int dx = Math.abs(key.x() - target.x());
            int dz = Math.abs(key.z() - target.z());
            if (dx + dz == 1) return true;
        }
        return false;
    }

    public void buyLand(@NotNull Claim claim, @NotNull Player player, @NotNull ChunkKey target, boolean isAdmin) {
        if (claim.isMainChunk(target.world(), target.x(), target.z())) {
            Msg.send(player, lang().getString("claim.land.already_own_chunk"));
            return;
        }
        if (claim.getLands().contains(target.toString())) {
            Msg.send(player, lang().getString("claim.land.already_own_land"));
            return;
        }
        if (!isAdmin && !isAdjacent(claim, target)) {
            Msg.send(player, lang().getString("claim.land.not_adjacent"));
            return;
        }
        if (Claim.claims.getAt(target.world(), target.x(), target.z()) != null) {
            Msg.send(player, lang().getString("claim.already_claimed"));
            return;
        }
        if (!isAdmin && isTooCloseToOthers(player, target.world(), target.x(), target.z(), claim)) {
            Msg.send(player, lang().getString("claim.too_close_to_other_claim"));
            return;
        }
        double price = calculateLandPrice(claim);
        if (price < 0 && !isAdmin) {
            Msg.send(player, lang().getString("claim.land.max_reached")
                    .replace("{max}", String.valueOf(config().getMaxClaimChunks())));
            return;
        }

        World world = Bukkit.getWorld(target.world());
        if (world == null) return;
        ClaimBuyLandEvent event = new ClaimBuyLandEvent(player, claim, target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Msg.send(player, lang().getString("claim.land.buy_cancelled"));
            return;
        }

        boolean free = isAdmin || bypass(player, "land_buy_price");
        if (!free && !plugin.getEconomyService().withdraw(player, price)) {
            Msg.send(player, lang().getString("command.balance.not_enough"));
            return;
        }

        claim.getLands().add(target.toString());
        plugin.getBlockValueManager().recalculate(claim);
        Sounds.play(player, "land_expand", "ENTITY_PLAYER_LEVELUP", 0.8f, 1.2f);
        Msg.send(player, lang().getString("claim.land.expanded"));
    }

    public void unClaimLand(@NotNull Claim claim, @NotNull Player player, @NotNull ChunkKey target) {
        if (claim.isMainChunk(target.world(), target.x(), target.z())) return;
        if (!claim.getLands().remove(target.toString())) return;
        plugin.getBlockValueManager().recalculate(claim);
        Sounds.play(player, "land_unclaim", "BLOCK_GRAVEL_BREAK", 0.8f, 0.8f);
        Msg.send(player, lang().getString("claim.land.unclaimed"));
    }

    /* ---------------- claim block ---------------- */

    /**
     * Moves the claim block inside the main chunk. The target must be empty (air or a
     * replaceable block such as grass), so nothing is destroyed. Must run on the chunk's thread.
     */
    public boolean moveClaimBlock(@NotNull Claim claim, @NotNull Location target) {
        Block newBlock = target.getBlock();
        if (!claim.isMainChunk(target.getWorld().getName(), newBlock.getX() >> 4, newBlock.getZ() >> 4)) return false;
        if (!newBlock.getType().isAir() && !newBlock.isReplaceable()) return false;

        Block oldBlock = claim.getClaimBlockLocation().getBlock();
        org.bukkit.block.data.BlockData data = oldBlock.getType() == claim.getClaimBlockType()
                ? oldBlock.getBlockData().clone()
                : claim.getClaimBlockType().createBlockData();
        if (oldBlock.getType() == claim.getClaimBlockType()) oldBlock.setType(Material.AIR);
        newBlock.setBlockData(data, false);

        if (plugin.getHologramManager() != null) plugin.getHologramManager().remove(claim);
        claim.setClaimBlockLocation(newBlock.getLocation());
        if (plugin.getHologramManager() != null) plugin.getHologramManager().spawn(claim);
        return true;
    }

    /* ---------------- teleport ---------------- */

    /**
     * Teleports a player on top of the claim block (or the first free spot above it). The chunk is
     * loaded asynchronously and inspected on its own region thread, so this is Folia-safe.
     */
    public void teleportToClaim(@NotNull Player player, @NotNull Claim claim) {
        Location base = claim.getClaimBlockLocation();
        World world = base.getWorld();
        if (world == null) return;
        world.getChunkAtAsync(base).thenAccept(chunk -> Scheduler.at(base, () -> {
            Location safe = findSafeSpot(base);
            Scheduler.entity(player, () -> {
                if (safe == null) {
                    Msg.send(player, lang().getString("claim.teleport.failed"));
                    nesoi.aysihuniks.nclaim.utils.MessageType.FAIL.playSound(player);
                    return;
                }
                player.teleportAsync(safe).thenAccept(ok -> {
                    if (ok) {
                        nesoi.aysihuniks.nclaim.utils.MessageType.TELEPORT.playSound(player);
                        Msg.send(player, lang().getString("claim.teleport.success"));
                    }
                });
            });
        }));
    }

    @Nullable
    private static Location findSafeSpot(Location base) {
        World world = base.getWorld();
        int x = base.getBlockX(), z = base.getBlockZ();
        for (int y = base.getBlockY() + 1; y < world.getMaxHeight() - 1; y++) {
            Block feet = world.getBlockAt(x, y, z);
            if (feet.isPassable() && !feet.isLiquid() && world.getBlockAt(x, y + 1, z).isPassable()
                    && world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                return new Location(world, x + 0.5, y, z + 0.5, base.getYaw(), base.getPitch());
            }
        }
        return null;
    }

    /* ---------------- selling ---------------- */

    /** Transfers a claim that is for sale to {@code buyer}. Returns {@code true} on success. */
    public boolean buyClaimFromPlayer(@NotNull Claim claim, @NotNull Player buyer) {
        if (!claim.isForSale() || claim.isRemoved()) {
            Msg.send(buyer, lang().getString("claim.sell.not_for_sale"));
            return false;
        }
        if (claim.isOwner(buyer.getUniqueId())) return false;
        long owned = Claim.claims.stream().filter(c -> c.isOwner(buyer.getUniqueId())).count();
        if (owned >= config().getMaxClaimCount(buyer)) {
            Msg.send(buyer, lang().getString("claim.max_reached"));
            return false;
        }

        double price = claim.getSalePrice();
        UUID sellerId = claim.getOwner();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
        // Mark sold first so a second, concurrent confirmation cannot buy it again.
        claim.setForSale(false);
        if (!plugin.getEconomyService().withdraw(buyer, price)) {
            claim.setForSale(true);
            Msg.send(buyer, lang().getString("command.balance.not_enough"));
            return false;
        }
        plugin.getEconomyService().deposit(seller, price);

        claim.setOwner(buyer.getUniqueId());
        claim.clearCoops();
        ClaimSettingsManager.resetToDefaults(claim);

        String sellerName = seller.getName() != null ? seller.getName() : sellerId.toString();
        String priceText = EconomyService.format(price);
        Msg.send(buyer, lang().getString("claim.sell.bought_buyer")
                .replace("{display_slug}", claim.getDisplayName())
                .replace("{player}", sellerName)
                .replace("{price}", priceText));
        Player online = seller.getPlayer();
        if (online != null) {
            Msg.send(online, lang().getString("claim.sell.bought_seller")
                    .replace("{player}", buyer.getName())
                    .replace("{display_slug}", claim.getDisplayName())
                    .replace("{price}", priceText));
        }
        if (plugin.getHologramManager() != null) plugin.getHologramManager().refresh(claim);
        return true;
    }

    /* ---------------- renaming ---------------- */

    /**
     * Renames a claim (used by {@code /nclaim name} and the management menu). {@code null}, empty,
     * "off" or "none" clears the name. Only the owner or an admin may rename.
     * @return whether the name changed
     */
    public boolean renameClaim(@NotNull Player player, @NotNull Claim claim, @Nullable String rawName) {
        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("nclaim.admin")) {
            Msg.send(player, lang().getString("command.permission_denied"));
            return false;
        }

        String name = rawName == null ? "" : rawName.trim();
        String oldSlug = claim.getSlug();
        boolean clear = name.isEmpty() || name.equalsIgnoreCase("off") || name.equalsIgnoreCase("none");

        if (clear) {
            claim.setDisplayName(null);
            claim.setSlug(Claim.uniqueSlug(claim.getOwner(), Claim.toSlug(claim.getClaimId()), claim.getClaimId()));
        } else {
            if (name.length() > MAX_NAME_LENGTH) {
                Msg.send(player, lang().getString("claim.name_too_long")
                        .replace("{max}", String.valueOf(MAX_NAME_LENGTH))
                        .replace("{current}", String.valueOf(name.length())));
                return false;
            }
            // Same filter as buying, otherwise a claim could be bought with a clean name and renamed later.
            if (config().isBlacklistedName(name)) {
                Msg.send(player, lang().getString("claim.name_blacklisted"));
                return false;
            }
            claim.setDisplayName(name);
            claim.setSlug(Claim.uniqueSlug(claim.getOwner(), Claim.toSlug(name), claim.getClaimId()));
        }

        claim.markDirty();
        if (plugin.getHologramManager() != null) plugin.getHologramManager().refresh(claim);

        String newSlug = claim.getSlug();
        if (clear) {
            Msg.send(player, lang().getString("claim.name_cleared").replace("{slug}", newSlug));
        } else if (oldSlug.equalsIgnoreCase(newSlug)) {
            Msg.send(player, lang().getString("claim.name_changed_same_slug").replace("{slug}", newSlug));
        } else {
            Msg.send(player, lang().getString("claim.name_changed_new_slug")
                    .replace("{old_slug}", oldSlug).replace("{new_slug}", newSlug));
        }
        return true;
    }

    /* ---------------- removal ---------------- */

    /** Removes a claim. Safe to call from any thread; the work runs on the claim block's region. */
    public void removeClaim(@NotNull Claim claim, @NotNull RemoveCause cause) {
        if (claim.isRemoved()) return;
        Location location = claim.getClaimBlockLocation();
        if (location.getWorld() == null || Bukkit.getWorld(claim.getWorldName()) == null) {
            finishRemoval(claim, cause);
            return;
        }
        Scheduler.ensureAt(location, () -> doRemove(claim, cause, location));
    }

    private void doRemove(Claim claim, RemoveCause cause, Location location) {
        if (claim.isRemoved()) return;
        ClaimRemoveEvent event = new ClaimRemoveEvent(claim, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            claim.getOwnerPlayer().ifPresent(owner -> Msg.send(owner, lang().getString("claim.remove_cancelled")));
            return;
        }

        Block block = location.getBlock();
        if (block.getType() == claim.getClaimBlockType()) block.setType(Material.AIR);
        if (plugin.getHologramManager() != null) plugin.getHologramManager().remove(claim);

        World world = location.getWorld();
        Location center = location.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 10, 0.3, 0.3, 0.3, 0.02);
        Sounds.playAt(center, plugin.getConfigManager().getString("sounds.claim_remove", "ENTITY_GENERIC_EXPLODE"), 1f, 1f);

        finishRemoval(claim, cause);

        String message = lang().getString(cause == RemoveCause.REMOVED ? "claim.expired" : "claim.removed")
                .replace("{x}", String.valueOf(claim.getCenterX()))
                .replace("{z}", String.valueOf(claim.getCenterZ()))
                .replace("{display_name}", claim.getDisplayName());
        List<UUID> notify = new ArrayList<>(claim.getCoopPlayers());
        notify.add(claim.getOwner());
        for (UUID uuid : new HashSet<>(notify)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) Msg.send(p, message);
        }

        plugin.getWebhookService().claimRemoved(claim);
    }

    private void finishRemoval(Claim claim, RemoveCause cause) {
        // Leaves a trace in the console, so a vanished claim can always be explained.
        Msg.log("&eClaim '" + claim.getClaimId() + "' (" + claim.getDisplayName() + ") of "
                + Bukkit.getOfflinePlayer(claim.getOwner()).getName() + " was removed: " + cause.name());
        claim.markRemoved();
        Claim.claims.remove(claim);
        plugin.getStorage().markDeleted(claim.getClaimId());
    }

    /* ---------------- WorldGuard ---------------- */

    public boolean isInBlacklistedRegion(@NotNull Location location) {
        if (!plugin.isWorldGuardEnabled()) return false;
        List<String> blacklisted = config().getBlacklistedRegions();
        if (blacklisted == null || blacklisted.isEmpty()) return false;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            ApplicableRegionSet set = container.createQuery().getApplicableRegions(BukkitAdapter.adapt(location));
            for (ProtectedRegion region : set) {
                for (String name : blacklisted) {
                    if (name.equalsIgnoreCase(region.getId())) return true;
                }
            }
        } catch (Throwable t) {
            Msg.log("&cWorldGuard region check failed: " + t.getMessage());
        }
        return false;
    }

    /** Kept for API compatibility. */
    @Deprecated
    public void buyLand(@NotNull Claim claim, @NotNull Player player, @NotNull Chunk chunk, boolean isAdmin) {
        buyLand(claim, player, ChunkKey.of(chunk), isAdmin);
    }
}
