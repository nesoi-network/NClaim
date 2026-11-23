package nesoi.aysihuniks.nclaim.model;

import nesoi.aysihuniks.nclaim.utils.Numbers;
import nesoi.aysihuniks.nclaim.NClaim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player data (internal balance and cached skin texture).
 * <p>
 * Users are loaded asynchronously before the player joins and unloaded after they quit.
 * Claim lists are derived from {@link Claim#claims} on demand, so they can never go stale.
 */
public class User {

    private static final Map<UUID, User> USERS = new ConcurrentHashMap<>();

    private final UUID uuid;
    private double balance;
    private volatile String skinTexture;
    private volatile boolean dirty;

    public User(@NotNull UUID uuid, double balance, @Nullable String skinTexture) {
        this.uuid = uuid;
        this.balance = balance;
        this.skinTexture = skinTexture;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized void setBalance(double balance) {
        this.balance = Math.max(0, balance);
        dirty = true;
    }

    public synchronized void addBalance(double amount) {
        setBalance(balance + amount);
    }

    /** Atomically withdraws {@code amount}; returns {@code false} when the balance is too low. */
    public synchronized boolean withdraw(double amount) {
        if (amount < 0 || balance < amount) return false;
        balance -= amount;
        dirty = true;
        return true;
    }

    public String getFormattedBalance() {
        return Numbers.format(getBalance());
    }

    public @Nullable String getSkinTexture() {
        return skinTexture;
    }

    public void setSkinTexture(@Nullable String skinTexture) {
        this.skinTexture = skinTexture;
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    public @NotNull Collection<Claim> getPlayerClaims() {
        List<Claim> list = new ArrayList<>();
        for (Claim c : Claim.claims) if (c.isOwner(uuid)) list.add(c);
        return list;
    }

    public @NotNull Collection<Claim> getCoopClaims() {
        List<Claim> list = new ArrayList<>();
        for (Claim c : Claim.claims) if (c.getCoopPlayers().contains(uuid)) list.add(c);
        return list;
    }

    /* ---------------- registry ---------------- */

    public static @Nullable User getUser(@Nullable UUID uuid) {
        return uuid == null ? null : USERS.get(uuid);
    }

    /** Returns the cached user or loads it from storage (blocking I/O; avoid on hot paths). */
    public static @NotNull User getOrLoad(@NotNull UUID uuid) {
        User user = USERS.get(uuid);
        if (user != null) return user;
        User loaded = NClaim.inst().getStorage().loadUser(uuid);
        User existing = USERS.putIfAbsent(uuid, loaded);
        return existing != null ? existing : loaded;
    }

    public static void loadUser(@NotNull UUID uuid) {
        getOrLoad(uuid);
    }

    public static void put(@NotNull User user) {
        USERS.putIfAbsent(user.getUuid(), user);
    }

    public static @Nullable User unload(@NotNull UUID uuid) {
        return USERS.remove(uuid);
    }

    public static @NotNull Collection<User> getLoadedUsers() {
        return USERS.values();
    }

    /** Queues the user for saving (no I/O on the calling thread). */
    public static void saveUser(@NotNull UUID uuid) {
        User user = USERS.get(uuid);
        if (user != null) NClaim.inst().getStorage().saveUserAsync(user);
    }
}
