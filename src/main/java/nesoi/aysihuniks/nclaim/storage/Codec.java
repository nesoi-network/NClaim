package nesoi.aysihuniks.nclaim.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/** Serialisation helpers shared by the storage backends. Thread-safe. */
public final class Codec {

    private static final DateTimeFormatter YAML_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ROOT);

    private Codec() {
    }

    @Nullable
    public static String date(@Nullable Date date) {
        return date == null ? null : YAML_DATE.format(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    @Nullable
    public static Date date(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Date.from(LocalDateTime.parse(value.trim(), YAML_DATE).atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static String location(@Nullable Location location) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ();
    }

    /** Parses {@code world,x,y,z}; world names containing commas are supported. */
    @Nullable
    public static Location location(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split(",");
        if (parts.length < 4) return null;
        try {
            int n = parts.length;
            double x = Double.parseDouble(parts[n - 3]);
            double y = Double.parseDouble(parts[n - 2]);
            double z = Double.parseDouble(parts[n - 1]);
            String worldName = String.join(",", java.util.Arrays.copyOfRange(parts, 0, n - 3));
            World world = Bukkit.getWorld(worldName);
            return world == null ? null : new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public static String locationWorld(@Nullable String value) {
        if (value == null) return null;
        String[] parts = value.split(",");
        if (parts.length < 4) return null;
        return String.join(",", java.util.Arrays.copyOfRange(parts, 0, parts.length - 3));
    }

    @Nullable
    public static Material material(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        return Material.matchMaterial(name.trim());
    }
}
