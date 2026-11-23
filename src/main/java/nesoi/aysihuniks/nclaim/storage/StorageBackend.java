package nesoi.aysihuniks.nclaim.storage;

import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * A place claims and users are persisted to. Implementations are only ever called from the
 * single storage thread (or synchronously during enable/disable), never from game threads.
 */
public interface StorageBackend {

    @NotNull String name();

    /** Loads the claims of {@code world}, or of every loaded world when {@code null}. */
    @NotNull List<Claim> loadClaims(@Nullable String world) throws Exception;

    /** Persists the given claims (insert or update). */
    void saveClaims(@NotNull Collection<Claim> claims) throws Exception;

    void deleteClaims(@NotNull Collection<String> claimIds) throws Exception;

    /** Loads a user, or returns a fresh one when nothing is stored yet. */
    @NotNull User loadUser(@NotNull UUID uuid) throws Exception;

    void saveUsers(@NotNull Collection<User> users) throws Exception;

    /** Number of stored claims, used to decide whether a one-time migration is needed. */
    int countClaims() throws Exception;

    void close();
}
