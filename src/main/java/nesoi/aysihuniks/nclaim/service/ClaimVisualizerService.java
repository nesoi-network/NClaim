package nesoi.aysihuniks.nclaim.service;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.ChunkKey;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player claim border walls built from text displays (one per side of each wall).
 * Nearby mode: green = own, aqua = co-op, red = other. Preview mode: one chunk, yellow when free.
 * <p>
 * Folia: displays are spawned/removed on their own region; the refresh runs on the player's scheduler.
 */
public class ClaimVisualizerService implements Listener {

    private static final int PREVIEW_TICKS = 10 * 20;
    private static final long REFRESH_TICKS = 10L;
    /** Upper bound for the scanned radius, whatever the view distance is. */
    private static final int MAX_RADIUS_CHUNKS = 10;
    private static final int MIN_RADIUS_CHUNKS = 2;
    /** Walls are re-anchored in steps of this many blocks, so walking up a hill does not respawn them. */
    private static final int ANCHOR_STEP = 16;
    /** Large enough that walls stay visible up to the scanned radius. */
    private static final float VIEW_RANGE = 16.0f;

    // Geometry of a text display whose text is a single space:
    // at scale 1 its background is 1/8 block wide and 1/4 block high, offset 0.4 of its width.
    private static final float WIDTH = 16.0f;
    private static final float SCALE_X = 8.0f;
    private static final float SCALE_Y = 4.0f;
    private static final float BACKGROUND_LEFT = 0.4f;
    private static final float SURFACE_OFFSET = 0.01f;

    private enum Kind {
        // Alpha 120/255: see-through, but strong enough to keep most of its colour in front of
        // bright backgrounds such as lava or the sky.
        OWN(Color.fromARGB(120, 40, 230, 60)),
        COOP(Color.fromARGB(120, 40, 150, 255)),
        OTHER(Color.fromARGB(120, 235, 40, 40)),
        FREE(Color.fromARGB(120, 255, 220, 40));

        final Color fill;

        Kind(Color fill) {
            this.fill = fill;
        }
    }

    private enum Side { NORTH, SOUTH, WEST, EAST }

    /** One wall: two see-through faces, spawned asynchronously on the wall's region. */
    private static final class Wall {
        final int anchorY;
        final Kind kind;
        final List<Display> parts = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean removed;

        Wall(int anchorY, Kind kind) {
            this.anchorY = anchorY;
            this.kind = kind;
        }

        void remove() {
            removed = true;
            for (Display part : parts) Scheduler.entity(part, part::remove);
            parts.clear();
        }
    }

    /** What one player currently sees. */
    private static final class View {
        final Player player;
        /** Chunk to preview; {@code null} means "all claims nearby". */
        final @Nullable ChunkKey preview;
        final boolean permanent;
        final Map<Long, Wall> walls = new ConcurrentHashMap<>();
        String world = "";
        int remainingTicks = PREVIEW_TICKS;
        ScheduledTask task;

        View(Player player, @Nullable ChunkKey preview, boolean permanent) {
            this.player = player;
            this.preview = preview;
            this.permanent = permanent;
        }

        void clear() {
            walls.values().forEach(Wall::remove);
            walls.clear();
        }
    }

    private final NClaim plugin;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();

    public ClaimVisualizerService(@NotNull NClaim plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ---------------- public API ---------------- */

    /** Shows one chunk for a few seconds (land expansion preview); free chunks are yellow. */
    public void showClaimBorders(@NotNull Player player, @NotNull ChunkKey chunk) {
        start(player, chunk, false);
    }

    @Deprecated
    public void showClaimBorders(@NotNull Player player, @NotNull Chunk chunk) {
        showClaimBorders(player, ChunkKey.of(chunk));
    }

    /** Shows the claims around the player for a few seconds. */
    public void showClaimBorders(@NotNull Player player) {
        start(player, null, false);
    }

    /**
     * Turns the permanent walls of nearby claims on or off (issue #10).
     * @return the new state
     */
    public boolean toggleBorders(@NotNull Player player) {
        View existing = views.get(player.getUniqueId());
        if (existing != null && existing.permanent) {
            stop(player.getUniqueId());
            return false;
        }
        start(player, null, true);
        return true;
    }

    public boolean isToggled(@NotNull Player player) {
        View view = views.get(player.getUniqueId());
        return view != null && view.permanent;
    }

    public void cancelVisualization(@NotNull Player player) {
        stop(player.getUniqueId());
    }

    /** Removes every wall; called on plugin disable. */
    public void shutdown() {
        for (UUID uuid : new ArrayList<>(views.keySet())) {
            View view = views.remove(uuid);
            if (view == null) continue;
            Scheduler.cancel(view.task);
            // Displays are not persistent, so a server stop removes them anyway. On Paper they can
            // go right away; Folia forbids touching entities from the shutdown thread.
            if (!Scheduler.isFolia()) {
                for (Wall wall : view.walls.values()) {
                    for (Display part : wall.parts) part.remove();
                }
            }
            view.walls.clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    /* ---------------- lifecycle ---------------- */

    private void start(Player player, @Nullable ChunkKey preview, boolean permanent) {
        stop(player.getUniqueId());
        View view = new View(player, preview, permanent);
        views.put(player.getUniqueId(), view);
        view.task = Scheduler.entityTimer(player, self -> tick(view, self), 1L, REFRESH_TICKS);
        if (view.task == null) views.remove(player.getUniqueId(), view);
    }

    private void stop(UUID uuid) {
        View view = views.remove(uuid);
        if (view == null) return;
        Scheduler.cancel(view.task);
        view.clear();
    }

    /** Runs on the player's thread. */
    private void tick(View view, ScheduledTask self) {
        Player player = view.player;
        if (views.get(player.getUniqueId()) != view || !player.isOnline()) {
            self.cancel();
            view.clear();
            return;
        }
        if (!view.permanent) {
            view.remainingTicks -= (int) REFRESH_TICKS;
            if (view.remainingTicks <= 0) {
                stop(player.getUniqueId());
                return;
            }
        }

        World world = player.getWorld();
        if (!world.getName().equals(view.world)) { // changed world: nothing of the old one applies
            view.world = world.getName();
            view.clear();
        }

        Location location = player.getLocation();
        int anchorY = Math.clamp(Math.floorDiv(location.getBlockY(), ANCHOR_STEP) * ANCHOR_STEP,
                world.getMinHeight(), world.getMaxHeight() - 1);

        Map<Long, Edge> needed = view.preview != null
                ? previewEdges(player, view.preview)
                : nearbyEdges(player, world, location);

        for (Map.Entry<Long, Edge> entry : needed.entrySet()) {
            Edge edge = entry.getValue();
            Wall wall = view.walls.get(entry.getKey());
            if (wall != null && !wall.removed && wall.anchorY == anchorY && wall.kind == edge.kind) continue;
            if (wall != null) wall.remove();
            Wall fresh = new Wall(anchorY, edge.kind);
            view.walls.put(entry.getKey(), fresh);
            spawn(view, fresh, world, edge.chunk, edge.side);
        }
        view.walls.entrySet().removeIf(entry -> {
            if (needed.containsKey(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
    }

    /* ---------------- which edges ---------------- */

    private record Edge(ChunkKey chunk, Side side, Kind kind) {
    }

    /** Border edges of every claim around the player. Edges between two chunks of the same claim are skipped. */
    private static Map<Long, Edge> nearbyEdges(Player player, World world, Location location) {
        int radius = Math.clamp(Math.min(player.getViewDistance(), player.getClientViewDistance()),
                MIN_RADIUS_CHUNKS, MAX_RADIUS_CHUNKS);
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        String worldName = world.getName();

        Map<Long, Edge> edges = new HashMap<>();
        Map<Claim, Kind> kinds = new HashMap<>();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Claim claim = Claim.claims.getAt(worldName, x, z);
                if (claim == null) continue;
                Kind kind = kinds.computeIfAbsent(claim, c -> kindOf(player, c));
                ChunkKey key = new ChunkKey(worldName, x, z);
                for (Side side : Side.values()) {
                    ChunkKey next = neighbour(key, side);
                    if (Objects.equals(Claim.claims.getAt(worldName, next.x(), next.z()), claim)) continue;
                    edges.put(edgeKey(x, z, side), new Edge(key, side, kind));
                }
            }
        }
        return edges;
    }

    /** The four sides of a single chunk, coloured by whoever owns it (yellow when free). */
    private static Map<Long, Edge> previewEdges(Player player, ChunkKey chunk) {
        if (!chunk.world().equals(player.getWorld().getName())) return Map.of();
        Claim claim = Claim.claims.getAt(chunk.world(), chunk.x(), chunk.z());
        Kind kind = claim == null ? Kind.FREE : kindOf(player, claim);
        Map<Long, Edge> edges = new HashMap<>();
        for (Side side : Side.values()) edges.put(edgeKey(chunk.x(), chunk.z(), side), new Edge(chunk, side, kind));
        return edges;
    }

    /* ---------------- walls ---------------- */

    private void spawn(View view, Wall wall, World world, ChunkKey key, Side side) {
        double x = key.x() * 16.0;
        double z = key.z() * 16.0;
        Vector3f direction;
        switch (side) {
            case NORTH -> { z += SURFACE_OFFSET; direction = new Vector3f(1, 0, 0); }
            case SOUTH -> { z += 16.0 - SURFACE_OFFSET; direction = new Vector3f(1, 0, 0); }
            case WEST -> { x += SURFACE_OFFSET; direction = new Vector3f(0, 0, 1); }
            default -> { x += 16.0 - SURFACE_OFFSET; direction = new Vector3f(0, 0, 1); }
        }
        Location origin = new Location(world, x, wall.anchorY, z);

        Scheduler.at(origin, () -> {
            if (wall.removed || !view.player.isOnline()) return;
            // Never load chunks just to draw a wall; the viewer could not see it anyway.
            if (!world.isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) return;

            List<Display> parts = new ArrayList<>();
            parts.add(face(world, origin, transformation(world, direction, false, wall.anchorY), wall.kind));
            parts.add(face(world, origin, transformation(world, direction, true, wall.anchorY), wall.kind));
            if (wall.removed) { // removed while we were spawning
                parts.forEach(Display::remove);
                return;
            }
            wall.parts.addAll(parts);
            Scheduler.entity(view.player, () -> {
                for (Display part : parts) view.player.showEntity(plugin, part);
            });
        });
    }

    private static TextDisplay face(World world, Location origin, Transformation transformation, Kind kind) {
        return world.spawn(origin, TextDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.text(Component.text(" "));
            display.setBackgroundColor(kind.fill);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(transformation);
            display.setDisplayWidth(0);
            display.setDisplayHeight(0);
            display.setViewRange(VIEW_RANGE);
        });
    }

    /** A wall 16 wide that spans the whole world height, rotated to face along {@code direction}. */
    private static Transformation transformation(World world, Vector3f direction, boolean flipped, int anchorY) {
        float angle = (direction.z != 0 ? -(float) Math.PI / 2 : 0f) + (flipped ? (float) Math.PI : 0f);
        float shift = flipped ? WIDTH - BACKGROUND_LEFT * WIDTH : BACKGROUND_LEFT * WIDTH;
        float height = world.getMaxHeight() - world.getMinHeight();
        return new Transformation(
                new Vector3f(direction).mul(shift).add(0, world.getMinHeight() - anchorY, 0),
                new Quaternionf().rotationY(angle),
                new Vector3f(WIDTH * SCALE_X, height * SCALE_Y, 1f),
                new Quaternionf());
    }

    /* ---------------- helpers ---------------- */

    private static ChunkKey neighbour(ChunkKey key, Side side) {
        return switch (side) {
            case NORTH -> new ChunkKey(key.world(), key.x(), key.z() - 1);
            case SOUTH -> new ChunkKey(key.world(), key.x(), key.z() + 1);
            case WEST -> new ChunkKey(key.world(), key.x() - 1, key.z());
            case EAST -> new ChunkKey(key.world(), key.x() + 1, key.z());
        };
    }

    private static long edgeKey(int chunkX, int chunkZ, Side side) {
        return (((long) (chunkX & 0x1FFFFFF)) << 33) | (((long) (chunkZ & 0x1FFFFFF)) << 2) | side.ordinal();
    }

    private static Kind kindOf(Player player, Claim claim) {
        UUID uuid = player.getUniqueId();
        if (claim.isOwner(uuid)) return Kind.OWN;
        if (claim.getCoopPlayers().contains(uuid)) return Kind.COOP;
        return Kind.OTHER;
    }
}
