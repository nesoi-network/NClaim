package nesoi.aysihuniks.nclaim.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Thin wrapper around Paper's region-aware schedulers.
 * <p>
 * On Folia every task runs on the thread that owns the target (region, entity or the global region).
 * On regular Paper all of these collapse onto the main thread, so the same code works on both.
 */
public final class Scheduler {

    private static Plugin plugin;
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private Scheduler() {
    }

    public static void init(@NotNull Plugin owner) {
        plugin = owner;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /* ---------------- global region (server-wide state, no world access) ---------------- */

    public static void global(@NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static ScheduledTask globalLater(@NotNull Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
    }

    public static ScheduledTask globalTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    /* ---------------- region (blocks / chunks) ---------------- */

    public static void at(@NotNull Location location, @NotNull Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    public static void at(@NotNull World world, int chunkX, int chunkZ, @NotNull Runnable task) {
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
    }

    public static ScheduledTask atLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), Math.max(1, delayTicks));
    }

    /** Runs immediately when the current thread already owns the location, otherwise schedules it. */
    public static void ensureAt(@NotNull Location location, @NotNull Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
        } else {
            at(location, task);
        }
    }

    /** Runs {@code supplier} on the region owning the chunk and hands back its result. */
    public static <T> CompletableFuture<T> supplyAt(@NotNull World world, int chunkX, int chunkZ, @NotNull java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        at(world, chunkX, chunkZ, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /* ---------------- entity (players, follows the entity across regions) ---------------- */

    public static void entity(@NotNull Entity entity, @NotNull Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    public static void entityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1, delayTicks));
    }

    @Nullable
    public static ScheduledTask entityTimer(@NotNull Entity entity, @NotNull Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin, task, null, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    /** Runs immediately when the current thread owns the entity, otherwise schedules it. */
    public static void ensureEntity(@NotNull Entity entity, @NotNull Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
        } else {
            entity(entity, task);
        }
    }

    /* ---------------- async (I/O, HTTP, heavy maths without world access) ---------------- */

    public static void async(@NotNull Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    public static ScheduledTask asyncTimer(@NotNull Runnable task, long delay, long period, @NotNull TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delay), Math.max(1, period), unit);
    }

    public static void cancel(@Nullable ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public static void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
