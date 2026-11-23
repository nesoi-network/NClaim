package nesoi.aysihuniks.nclaim.model;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** World name + chunk coordinates, serialised as {@code world,x,z} (the format used in storage). */
public record ChunkKey(@NotNull String world, int x, int z) {

    @Nullable
    public static ChunkKey parse(@Nullable String serialized) {
        if (serialized == null) return null;
        int second = serialized.lastIndexOf(',');
        int first = second > 0 ? serialized.lastIndexOf(',', second - 1) : -1;
        if (first <= 0) return null;
        try {
            return new ChunkKey(serialized.substring(0, first),
                    Integer.parseInt(serialized.substring(first + 1, second).trim()),
                    Integer.parseInt(serialized.substring(second + 1).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NotNull
    public static ChunkKey of(@NotNull Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    @NotNull
    public static ChunkKey of(@NotNull Location location) {
        return new ChunkKey(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /** Block coordinates of the chunk centre. */
    public int centerX() {
        return (x << 4) + 8;
    }

    public int centerZ() {
        return (z << 4) + 8;
    }

    @Override
    public @NotNull String toString() {
        return world + "," + x + "," + z;
    }
}
