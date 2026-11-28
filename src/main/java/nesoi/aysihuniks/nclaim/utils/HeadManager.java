package nesoi.aysihuniks.nclaim.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import nesoi.aysihuniks.nclaim.model.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player and texture heads via Paper's profile API. Skins come from SkinsRestorer if installed,
 * otherwise from Paper's profile cache / Mojang.
 */
public class HeadManager {

    private static final long REFETCH_MS = 60 * 60 * 1000L;
    private static final long RETRY_MS = 5 * 60 * 1000L;

    private final boolean skinsRestorer;
    private final Map<UUID, ItemStack> headCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFetch = new ConcurrentHashMap<>();

    public HeadManager() {
        this.skinsRestorer = Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer");
        Msg.log("&aHead textures provided by " + (skinsRestorer ? "SkinsRestorer" : "Paper profiles") + ".");
    }

    public boolean isSkinRestorerHooked() {
        return skinsRestorer;
    }

    /* ---------------- heads ---------------- */

    /** Head for a player. Returns a cached or plain head immediately and fetches the skin in the background. */
    public ItemStack createHead(@NotNull OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        ItemStack cached = headCache.get(uuid);
        if (cached != null) return cached.clone();

        String stored = storedTexture(uuid);
        if (stored != null) {
            ItemStack head = createHeadWithTexture(stored);
            headCache.put(uuid, head);
            return head.clone();
        }
        Player online = Bukkit.getPlayer(uuid);
        String live = online != null ? texturesOf(online.getPlayerProfile()) : null;
        if (live != null) {
            ItemStack head = createHeadWithTexture(live);
            headCache.put(uuid, head);
            User user = User.getUser(uuid);
            if (user != null && !live.equals(user.getSkinTexture())) user.setSkinTexture(live);
            return head.clone();
        }

        fetchTexture(uuid);
        return createNamedHead(uuid, player.getName());
    }

    private static ItemStack createNamedHead(UUID uuid, @Nullable String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (name == null || name.isBlank()) return head;
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(Bukkit.createProfile(uuid, name));
            head.setItemMeta(meta);
        } catch (Exception ignored) {
        }
        return head;
    }

    public ItemStack createHeadFromCache(@NotNull UUID uuid) {
        return createHead(Bukkit.getOfflinePlayer(uuid));
    }

    public void preloadTexturesAsync(@NotNull Collection<UUID> uuids) {
        for (UUID uuid : uuids) {
            if (!headCache.containsKey(uuid)) fetchTexture(uuid);
        }
    }

    /**
     * Head with a texture. Accepts the base64 "textures" value, a textures.minecraft.net URL
     * or just the texture id.
     */
    public ItemStack createHeadWithTexture(@Nullable String texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (texture == null || texture.isBlank()) return head;
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), null);
            profile.setProperty(new ProfileProperty("textures", toBase64(texture.trim())));
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        } catch (Exception e) {
            Msg.log("&cCould not create head texture: " + e.getMessage());
        }
        return head;
    }

    private static String toBase64(String texture) {
        if (isBase64Json(texture)) return texture;
        String url = texture.startsWith("http") ? texture : "https://textures.minecraft.net/texture/" + texture;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBase64Json(String texture) {
        if (texture.startsWith("http") || texture.length() < 8) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(texture), StandardCharsets.UTF_8).trim();
            return decoded.startsWith("{");
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    /* ---------------- textures ---------------- */

    @Nullable
    private String storedTexture(UUID uuid) {
        User user = User.getUser(uuid);
        return user != null ? user.getSkinTexture() : null;
    }

    /** Cached texture value; fetches in the background when missing. */
    @Nullable
    public String getSkinTextureValue(@NotNull UUID uuid, boolean forceFetch) {
        String stored = storedTexture(uuid);
        if (stored != null && !forceFetch) return stored;
        fetchTexture(uuid);
        return stored;
    }

    private void fetchTexture(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastFetch.get(uuid);
        if (last != null && now - last < REFETCH_MS) return;
        lastFetch.put(uuid, now);

        resolveTexture(uuid).thenAccept(texture -> {
            if (texture == null) {
                lastFetch.put(uuid, System.currentTimeMillis() - REFETCH_MS + RETRY_MS);
                return;
            }
            headCache.put(uuid, createHeadWithTexture(texture));
            User user = User.getUser(uuid);
            if (user != null && !texture.equals(user.getSkinTexture())) user.setSkinTexture(texture);
        }).exceptionally(t -> null);
    }

    private CompletableFuture<String> resolveTexture(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (skinsRestorer) {
            String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            CompletableFuture<String> sr = CompletableFuture.supplyAsync(() -> SkinsRestorerHook.texture(uuid, name));
            return sr.thenCompose(t -> t != null ? CompletableFuture.completedFuture(t) : profileTexture(uuid, online));
        }
        return profileTexture(uuid, online);
    }

    private static CompletableFuture<String> profileTexture(UUID uuid, @Nullable Player online) {
        if (online != null) {
            String t = texturesOf(online.getPlayerProfile());
            if (t != null) return CompletableFuture.completedFuture(t);
        }
        String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();
        return Bukkit.createProfile(uuid).update()
                .thenApply(HeadManager::texturesOf)
                .exceptionally(t -> null)
                .thenCompose(t -> {
                    if (t != null || name == null) return CompletableFuture.completedFuture(t);
                    return Bukkit.createProfile(name).update()
                            .thenApply(HeadManager::texturesOf)
                            .exceptionally(e -> null);
                });
    }

    @Nullable
    private static String texturesOf(@Nullable PlayerProfile profile) {
        if (profile == null) return null;
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) return property.getValue();
        }
        return null;
    }

    /** Isolated so SkinsRestorer classes are only loaded when the plugin is present. */
    private static final class SkinsRestorerHook {
        @Nullable
        static String texture(UUID uuid, @Nullable String name) {
            try {
                net.skinsrestorer.api.SkinsRestorer api = net.skinsrestorer.api.SkinsRestorerProvider.get();
                Optional<net.skinsrestorer.api.property.SkinProperty> property =
                        api.getPlayerStorage().getSkinForPlayer(uuid, name);
                return property.map(net.skinsrestorer.api.property.SkinProperty::getValue).orElse(null);
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
