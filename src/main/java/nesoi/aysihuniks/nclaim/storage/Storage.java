package nesoi.aysihuniks.nclaim.storage;

import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.User;
import nesoi.aysihuniks.nclaim.utils.Msg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Write-behind persistence: game code marks claims dirty and one background thread flushes them
 * every few seconds, so no I/O happens on tick threads. A single thread also keeps writes ordered
 * (a save can never overtake a later delete).
 */
public final class Storage {

    private static final long FLUSH_INTERVAL_SECONDS = 5;

    private final StorageBackend backend;
    private final ScheduledExecutorService executor;
    private final Set<String> dirtyClaims = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedClaims = ConcurrentHashMap.newKeySet();

    public Storage(@NotNull StorageBackend backend) {
        this.backend = backend;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NClaim-Storage");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::flushSafely, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public @NotNull StorageBackend getBackend() {
        return backend;
    }

    public void markDirty(@NotNull Claim claim) {
        deletedClaims.remove(claim.getClaimId());
        dirtyClaims.add(claim.getClaimId());
    }

    public void markDeleted(@NotNull String claimId) {
        dirtyClaims.remove(claimId);
        deletedClaims.add(claimId);
    }

    /* ---------------- loading (called during enable / world load) ---------------- */

    public @NotNull List<Claim> loadClaims(@Nullable String world) {
        try {
            return backend.loadClaims(world);
        } catch (Exception e) {
            Msg.log("&cFailed to load claims from " + backend.name() + ": " + e.getMessage());
            return List.of();
        }
    }

    /** Blocking user load. Called from the async pre-login thread or as a rare fallback. */
    public @NotNull User loadUser(@NotNull UUID uuid) {
        try {
            return backend.loadUser(uuid);
        } catch (Exception e) {
            Msg.log("&cFailed to load user " + uuid + ": " + e.getMessage());
            return new User(uuid, 0, null);
        }
    }

    public void saveUserAsync(@NotNull User user) {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            try {
                user.clearDirty();
                backend.saveUsers(List.of(user));
            } catch (Exception e) {
                user.markDirty();
                Msg.log("&cFailed to save user " + user.getUuid() + ": " + e.getMessage());
            }
        });
    }

    /** Saves every claim (used for backend migrations). */
    public void saveAllClaimsNow() throws Exception {
        backend.saveClaims(new ArrayList<>(Claim.claims));
    }

    /* ---------------- flushing ---------------- */

    private void flushSafely() {
        try {
            flush();
        } catch (Throwable t) {
            Msg.log("&cAuto-save to " + backend.name() + " failed: " + t.getMessage());
        }
    }

    private void flush() throws Exception {
        if (!deletedClaims.isEmpty()) {
            List<String> ids = drain(deletedClaims);
            try {
                backend.deleteClaims(ids);
            } catch (Exception e) {
                deletedClaims.addAll(ids); // retry next time
                throw e;
            }
        }
        if (!dirtyClaims.isEmpty()) {
            List<String> ids = drain(dirtyClaims);
            List<Claim> claims = new ArrayList<>(ids.size());
            for (String id : ids) {
                Claim claim = Claim.claims.getById(id);
                if (claim != null) claims.add(claim);
            }
            try {
                backend.saveClaims(claims);
            } catch (Exception e) {
                dirtyClaims.addAll(ids);
                throw e;
            }
        }
        List<User> users = new ArrayList<>();
        for (User user : User.getLoadedUsers()) if (user.isDirty()) users.add(user);
        if (!users.isEmpty()) {
            // Cleared before writing: a change made while saving marks the user dirty again.
            users.forEach(User::clearDirty);
            try {
                backend.saveUsers(users);
            } catch (Exception e) {
                users.forEach(User::markDirty);
                throw e;
            }
        }
    }

    private static List<String> drain(Set<String> set) {
        List<String> out = new ArrayList<>(set);
        out.forEach(set::remove);
        return out;
    }

    /** Runs a pending flush now and waits for it (used by /reload and the auto-save message). */
    public void flushNow() {
        if (executor.isShutdown()) return;
        Future<?> f = executor.submit(this::flushSafely);
        try {
            f.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Msg.log("&cTimed out waiting for storage flush: " + e.getMessage());
        }
    }

    /** Final flush and shutdown; blocks the calling thread (plugin disable). */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                Msg.log("&cStorage thread did not stop in time.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Everything still queued is written synchronously here.
        flushSafely();
        backend.close();
    }
}
