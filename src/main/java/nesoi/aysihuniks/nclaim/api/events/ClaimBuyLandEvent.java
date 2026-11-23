package nesoi.aysihuniks.nclaim.api.events;

import nesoi.aysihuniks.nclaim.model.ChunkKey;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ClaimBuyLandEvent extends ClaimBuyEvent {

    private static final HandlerList handlers = new HandlerList();

    private final @NotNull ChunkKey landKey;

    public ClaimBuyLandEvent(@NotNull Player sender, @NotNull Claim claim, @NotNull ChunkKey land) {
        super(sender, claim);
        this.landKey = land;
    }

    /** The chunk being added, without loading it. */
    public @NotNull ChunkKey getLandKey() {
        return landKey;
    }

    /** @deprecated loads the chunk; use {@link #getLandKey()}. */
    @Deprecated
    public @NotNull Chunk getLand() {
        World world = Bukkit.getWorld(landKey.world());
        if (world == null) throw new IllegalStateException("World " + landKey.world() + " is not loaded");
        return world.getChunkAt(landKey.x(), landKey.z());
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
