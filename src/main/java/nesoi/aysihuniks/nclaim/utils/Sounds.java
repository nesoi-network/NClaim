package nesoi.aysihuniks.nclaim.utils;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config sound names: {@code BLOCK_NOTE_BLOCK_CHIME} or {@code minecraft:block.note_block.chime};
 * {@code NONE} / empty disables. Played through Adventure because {@code org.bukkit.Sound} is not
 * an enum on newer Paper versions.
 */
public final class Sounds {

    private static final Map<String, Key> CONSTANTS = new ConcurrentHashMap<>();
    private static final Map<String, Key> CACHE = new ConcurrentHashMap<>();
    private static final Key NONE = Key.key("nclaim", "none");

    static {
        // "block.note_block.chime" is exposed as BLOCK_NOTE_BLOCK_CHIME, so the constant name can be derived from the key.
        var registry = io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.SOUND_EVENT);
        registry.stream().forEach(sound -> {
            org.bukkit.NamespacedKey key = registry.getKey(sound);
            if (key != null) CONSTANTS.put(key.getKey().toUpperCase(Locale.ROOT).replace('.', '_'), key);
        });
    }

    private Sounds() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    @Nullable
    public static Key resolve(@Nullable String name) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("NONE")) return null;
        Key key = CACHE.computeIfAbsent(name, Sounds::lookup);
        return key == NONE ? null : key;
    }

    private static Key lookup(String name) {
        String trimmed = name.trim();
        Key constant = CONSTANTS.get(trimmed.toUpperCase(Locale.ROOT));
        if (constant != null) return constant;
        try {
            return Key.key(trimmed.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            Msg.log("&cUnknown sound '" + name + "' in config.");
            return NONE;
        }
    }

    /** Plays a configured sound ({@code sounds.<path>} in config.yml) to one player. */
    public static void play(@NotNull Player player, @NotNull String configPath, @NotNull String fallback, float volume, float pitch) {
        play(player, Config.sound(configPath, fallback), volume, pitch);
    }

    public static void play(@NotNull Player player, @Nullable String soundName, float volume, float pitch) {
        Key key = resolve(soundName);
        if (key != null) player.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch), Sound.Emitter.self());
    }

    /** Plays a sound at a location for everyone nearby. Must run on the location's region thread. */
    public static void playAt(@NotNull Location location, @Nullable String soundName, float volume, float pitch) {
        Key key = resolve(soundName);
        World world = location.getWorld();
        if (key != null && world != null) {
            world.playSound(Sound.sound(key, Sound.Source.BLOCK, volume, pitch), location.getX(), location.getY(), location.getZ());
        }
    }

    /** Small indirection so this class does not depend on the plugin instance directly. */
    static final class Config {
        static String sound(String path, String fallback) {
            nesoi.aysihuniks.nclaim.NClaim plugin = nesoi.aysihuniks.nclaim.NClaim.inst();
            if (plugin == null || plugin.getConfigManager() == null) return fallback;
            return plugin.getConfigManager().getString("sounds." + path, fallback);
        }
    }
}
