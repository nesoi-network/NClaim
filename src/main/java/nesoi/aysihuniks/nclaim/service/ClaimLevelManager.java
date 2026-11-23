package nesoi.aysihuniks.nclaim.service;

import nesoi.aysihuniks.nclaim.utils.Numbers;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.api.events.ClaimCalculateLevelEvent;
import nesoi.aysihuniks.nclaim.model.ChunkKey;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.ClaimLevel;
import nesoi.aysihuniks.nclaim.utils.Msg;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim "level" (value of the blocks inside the claim).
 * <p>
 * Chunks are captured as {@link ChunkSnapshot}s on the thread that owns them and scanned
 * asynchronously, so a recalculation costs the server tick almost nothing.
 */
public class ClaimLevelManager {

    private static final int SURFACE_DEPTH = 15;
    private static final int UNDERGROUND_STEP = 2;
    private static final int UNDERGROUND_MIN_VALUE = 10;

    private final NClaim plugin;
    private final File file;
    private volatile Map<Material, ClaimLevel> blockValues = Collections.emptyMap();
    private final Set<UUID> calculating = ConcurrentHashMap.newKeySet();
    private final Set<String> recalculating = ConcurrentHashMap.newKeySet();
    /** Last calculated value of each chunk ("world,x,z"), for the claim_main_value placeholder. */
    private final Map<String, Long> chunkValues = new ConcurrentHashMap<>();

    public ClaimLevelManager(@NotNull NClaim plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "block_levels.yml");
    }

    /* ---------------- block values ---------------- */

    public void loadBlockValues() {
        if (!file.exists()) plugin.saveResource("block_levels.yml", false);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<Material, ClaimLevel> values = new EnumMap<>(Material.class);
        ConfigurationSection section = config.getConfigurationSection("blocks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                int value = section.getInt(key, 0);
                if (material == null) {
                    Msg.log("&cUnknown block '" + key + "' in block_levels.yml");
                } else if (value > 0) {
                    values.put(material, new ClaimLevel(material, value));
                }
            }
        }
        blockValues = values; // published atomically; readers never see a half-built map
        Msg.log("&aLoaded " + values.size() + " block values.");
    }

    public void reloadBlockValues() {
        loadBlockValues();
    }

    private void saveBlockValues() {
        Map<Material, ClaimLevel> snapshot = blockValues;
        Scheduler.async(() -> {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("blocks", null);
            ConfigurationSection section = config.createSection("blocks");
            snapshot.forEach((material, level) -> section.set(material.name(), level.getValue()));
            try {
                config.save(file);
            } catch (IOException e) {
                Msg.log("&cCould not save block_levels.yml: " + e.getMessage());
            }
        });
    }

    public @NotNull Map<Material, ClaimLevel> getBlockValues() {
        return blockValues;
    }

    public int getBlockValue(@Nullable Material material) {
        if (material == null) return 0;
        ClaimLevel level = blockValues.get(material);
        return level != null ? level.getValue() : 0;
    }

    public void setBlockValue(@Nullable Material material, int value) {
        if (material == null || value <= 0) return;
        Map<Material, ClaimLevel> copy = new EnumMap<>(Material.class);
        copy.putAll(blockValues);
        copy.put(material, new ClaimLevel(material, value));
        blockValues = copy;
        saveBlockValues();
    }

    public void removeBlockValue(@Nullable Material material) {
        if (material == null || !blockValues.containsKey(material)) return;
        Map<Material, ClaimLevel> copy = new EnumMap<>(Material.class);
        copy.putAll(blockValues);
        copy.remove(material);
        blockValues = copy;
        saveBlockValues();
    }

    /** Cached value of one chunk, or 0 before its first calculation. */
    public long getChunkValue(@NotNull ChunkKey key) {
        return chunkValues.getOrDefault(key.toString(), 0L);
    }

    public boolean isValidBlock(@Nullable Material material) {
        return material != null && blockValues.containsKey(material);
    }

    /* ---------------- calculation ---------------- */

    /**
     * Recalculates in the background using only chunks that are already loaded. When some chunk
     * of the claim is not loaded nothing changed there since the last calculation, so the
     * stored value is kept.
     */
    public void recalculate(@NotNull Claim claim) {
        if (!recalculating.add(claim.getClaimId())) return;
        calculate(claim, false).whenComplete((value, error) -> {
            recalculating.remove(claim.getClaimId());
            if (error == null && value != null) applyValue(claim, value);
        });
    }

    /** Recalculates every claim whose chunks are all loaded (auto-save). */
    public void recalculateLoaded() {
        for (Claim claim : Claim.claims) recalculate(claim);
    }

    /** {@code /nclaim level}; loads missing chunks asynchronously. */
    public void requestClaimCalculation(@NotNull UUID playerId, @NotNull String playerName, @NotNull Claim claim) {
        if (!calculating.add(playerId)) {
            send(playerId, plugin.getLangManager().getString("claim.level.your_already_in_queue"));
            return;
        }
        send(playerId, plugin.getLangManager().getString("claim.level.calculating_started"));
        long start = System.currentTimeMillis();
        calculate(claim, true).whenComplete((value, error) -> {
            calculating.remove(playerId);
            if (error != null || value == null) {
                Msg.log("&cClaim level calculation failed for " + claim.getClaimId() + ": " + (error == null ? "unknown" : error.getMessage()));
                return;
            }
            applyValue(claim, value);
            send(playerId, plugin.getLangManager().getString("command.level.info")
                    .replace("{value}", Numbers.format(value))
                    .replace("{chunks}", String.valueOf(claim.getTotalChunkCount()))
                    .replace("{time}", (System.currentTimeMillis() - start) + "ms"));
        });
    }

    public void checkQueueStatus(@NotNull UUID playerId) {
        if (calculating.isEmpty()) {
            send(playerId, plugin.getLangManager().getString("claim.level.queue_is_empty"));
        } else {
            send(playerId, plugin.getLangManager().getString("claim.level.calculating_in_queue")
                    .replace("{count}", String.valueOf(calculating.size())));
        }
    }

    /** Value of the claim, or {@code null} if a chunk was unavailable (only when not forcing). */
    private CompletableFuture<Long> calculate(Claim claim, boolean loadChunks) {
        List<CompletableFuture<ChunkSnapshot>> snapshots = new ArrayList<>();
        for (ChunkKey key : claim.getAllChunkKeys()) {
            World world = Bukkit.getWorld(key.world());
            if (world == null) continue;
            snapshots.add(snapshot(world, key.x(), key.z(), loadChunks));
        }
        Map<Material, ClaimLevel> values = blockValues;
        return CompletableFuture.allOf(snapshots.toArray(CompletableFuture[]::new)).thenApplyAsync(ignored -> {
            long total = 0;
            for (CompletableFuture<ChunkSnapshot> future : snapshots) {
                ChunkSnapshot snapshot = future.join();
                if (snapshot == null) return null;
                long chunkValue = scan(snapshot, values);
                chunkValues.put(snapshot.getWorldName() + "," + snapshot.getX() + "," + snapshot.getZ(), chunkValue);
                total += chunkValue;
            }
            return total;
        }, runnable -> Scheduler.async(runnable));
    }

    private CompletableFuture<ChunkSnapshot> snapshot(World world, int x, int z, boolean load) {
        if (load) {
            return world.getChunkAtAsync(x, z).thenApply(chunk -> chunk.getChunkSnapshot(true, false, false));
        }
        return Scheduler.supplyAt(world, x, z, () ->
                world.isChunkLoaded(x, z) ? world.getChunkAt(x, z).getChunkSnapshot(true, false, false) : null);
    }

    private static long scan(ChunkSnapshot snapshot, Map<Material, ClaimLevel> values) {
        if (values.isEmpty()) return 0;
        World world = Bukkit.getWorld(snapshot.getWorldName());
        int minY = world != null ? world.getMinHeight() : -64;
        long total = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int top = snapshot.getHighestBlockYAt(x, z);
                int surfaceStart = Math.max(minY, top - SURFACE_DEPTH);
                for (int y = surfaceStart; y <= top; y++) {
                    ClaimLevel level = values.get(snapshot.getBlockType(x, y, z));
                    if (level != null) total += level.getValue();
                }
                for (int y = minY; y < surfaceStart; y += UNDERGROUND_STEP) {
                    ClaimLevel level = values.get(snapshot.getBlockType(x, y, z));
                    if (level != null && level.getValue() > UNDERGROUND_MIN_VALUE) total += level.getValue();
                }
            }
        }
        return total;
    }

    /** Fires the (synchronous) API event on the global thread and stores the result. */
    private void applyValue(Claim claim, long newValue) {
        Scheduler.global(() -> {
            if (claim.isRemoved()) return;
            ClaimCalculateLevelEvent event = new ClaimCalculateLevelEvent(claim, claim.getClaimValue(), newValue);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) claim.setClaimValue(event.getNewLevel());
        });
    }

    private void send(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) Msg.send(player, message);
    }
}
