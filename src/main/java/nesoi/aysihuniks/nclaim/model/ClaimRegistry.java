package nesoi.aysihuniks.nclaim.model;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every loaded claim plus a per-world chunk index for O(1) "who owns this chunk" lookups.
 * Concurrent because Folia calls into this from many region threads.
 */
public final class ClaimRegistry extends AbstractCollection<Claim> {

    private final Map<String, Claim> byId = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Claim>> byChunk = new ConcurrentHashMap<>();

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /* ---------------- lookups ---------------- */

    @Nullable
    public Claim getById(@Nullable String claimId) {
        return claimId == null ? null : byId.get(claimId);
    }

    @Nullable
    public Claim getAt(@NotNull String world, int chunkX, int chunkZ) {
        Map<Long, Claim> index = byChunk.get(world);
        return index == null ? null : index.get(key(chunkX, chunkZ));
    }

    @Nullable
    public Claim getAt(@NotNull World world, int chunkX, int chunkZ) {
        return getAt(world.getName(), chunkX, chunkZ);
    }

    @Nullable
    public Claim getAt(@Nullable Location location) {
        if (location == null || location.getWorld() == null) return null;
        return getAt(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Nullable
    public Claim getAt(@Nullable Block block) {
        if (block == null) return null;
        return getAt(block.getWorld().getName(), block.getX() >> 4, block.getZ() >> 4);
    }

    @Nullable
    public Claim getAt(@Nullable Chunk chunk) {
        if (chunk == null) return null;
        return getAt(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    /* ---------------- index maintenance ---------------- */

    void index(@NotNull Claim claim, @NotNull String world, int chunkX, int chunkZ) {
        byChunk.computeIfAbsent(world, w -> new ConcurrentHashMap<>()).put(key(chunkX, chunkZ), claim);
    }

    void unindex(@NotNull Claim claim, @NotNull String world, int chunkX, int chunkZ) {
        Map<Long, Claim> index = byChunk.get(world);
        if (index != null) index.remove(key(chunkX, chunkZ), claim);
    }

    private void indexAll(Claim claim) {
        index(claim, claim.getWorldName(), claim.getChunkX(), claim.getChunkZ());
        for (String land : claim.getLands()) {
            ChunkKey k = ChunkKey.parse(land);
            if (k != null) index(claim, k.world(), k.x(), k.z());
        }
    }

    private void unindexAll(Claim claim) {
        unindex(claim, claim.getWorldName(), claim.getChunkX(), claim.getChunkZ());
        for (String land : claim.getLands()) {
            ChunkKey k = ChunkKey.parse(land);
            if (k != null) unindex(claim, k.world(), k.x(), k.z());
        }
    }

    boolean isRegistered(@NotNull Claim claim) {
        return byId.get(claim.getClaimId()) == claim;
    }

    /* ---------------- Collection ---------------- */

    @Override
    public boolean add(@NotNull Claim claim) {
        Claim previous = byId.put(claim.getClaimId(), claim);
        if (previous == claim) return false;
        if (previous != null) unindexAll(previous);
        indexAll(claim);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (!(o instanceof Claim claim)) return false;
        if (!byId.remove(claim.getClaimId(), claim)) return false;
        unindexAll(claim);
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return o instanceof Claim claim && isRegistered(claim);
    }

    @Override
    public void clear() {
        byId.clear();
        byChunk.clear();
    }

    @Override
    public @NotNull Iterator<Claim> iterator() {
        Iterator<Claim> it = byId.values().iterator();
        return new Iterator<>() {
            private Claim current;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Claim next() {
                current = it.next();
                return current;
            }

            @Override
            public void remove() {
                if (current == null) throw new IllegalStateException();
                ClaimRegistry.this.remove(current);
                current = null;
            }
        };
    }

    @Override
    public int size() {
        return byId.size();
    }
}
