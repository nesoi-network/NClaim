package nesoi.aysihuniks.nclaim.integrations;

import nesoi.aysihuniks.nclaim.utils.Numbers;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.ChunkKey;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.service.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlaceholderAPI expansion. Reads cached data only, so it is safe from any thread. Chunk
 * placeholders take an optional {@code _<world>_<x>_<z>} suffix (world names may contain
 * underscores), defaulting to the player's chunk.
 */
public class Expansion extends PlaceholderExpansion {

    /** {@code <prefix>_<world>_<chunkX>_<chunkZ>} */
    private static final Pattern LOCATED = Pattern.compile("^(.+?)_(.+)_(-?\\d+)_(-?\\d+)$");
    /** Config sections that must never be exposed through %nclaim_get_...%. */
    private static final List<String> HIDDEN_CONFIG = List.of("database", "webhook_settings");

    private final NClaim plugin;

    public Expansion(NClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "aysihuniks";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nclaim";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offline, @NotNull String params) {
        Player player = offline != null ? offline.getPlayer() : null;
        if (params.isEmpty()) return null;

        switch (params) {
            case "player_balance":
                return offline == null ? null : EconomyService.format(plugin.getEconomyService().balance(offline));
            case "owner": {
                Claim claim = here(player);
                if (claim == null) return lang("claim.no_owner");
                return lang("claim.owner").replace("{owner}", name(claim.getOwner()));
            }
            case "claim_expiry": {
                Claim claim = here(player);
                return claim == null ? "N/A" : plugin.getClaimExpirationManager().getFormattedTimeLeft(claim);
            }
            case "claim_count":
                return offline == null ? null : String.valueOf(countOwned(offline.getUniqueId()));
            case "coop_count":
                if (player == null) return null;
                return String.valueOf(countCoop(player.getUniqueId()));
            case "total_value":
                return offline == null ? null : Numbers.format(totalValue(offline.getUniqueId()));
            case "total_chunks":
                return offline == null ? null : String.valueOf(totalChunks(offline.getUniqueId()));
            case "total_value_raw":
                return offline == null ? null : String.valueOf(totalValue(offline.getUniqueId()));
            default:
                break;
        }

        if (params.startsWith("get_")) return config(params);
        if (params.startsWith("block_value_")) {
            Material material = Material.matchMaterial(params.substring("block_value_".length()));
            return material == null ? "Invalid material" : String.valueOf(plugin.getBlockValueManager().getBlockValue(material));
        }
        if (params.startsWith("claim_count_")) {
            return String.valueOf(countOwned(Bukkit.getOfflinePlayer(params.substring("claim_count_".length())).getUniqueId()));
        }
        if (params.startsWith("coop_count_") && !LOCATED.matcher(params).matches()) {
            return String.valueOf(countCoop(Bukkit.getOfflinePlayer(params.substring("coop_count_".length())).getUniqueId()));
        }

        for (String prefix : List.of("claim_main_value", "claim_total_value", "sale_status", "display_name",
                "expiration", "owner", "coop_count", "total_size")) {
            if (!params.equals(prefix) && !params.startsWith(prefix + "_")) continue;
            Target target = target(prefix, params, player);
            if (target.claim == null) return prefix.equals("sale_status") ? "" : "Claim not found";
            return claimValue(prefix, target.claim, target.key);
        }
        return null;
    }

    /* ---------------- claim placeholders ---------------- */

    private record Target(@Nullable Claim claim, @Nullable ChunkKey key) {
    }

    private Target target(String prefix, String params, @Nullable Player player) {
        if (params.equals(prefix)) {
            if (player == null) return new Target(null, null);
            ChunkKey key = ChunkKey.of(player.getLocation());
            return new Target(Claim.claims.getAt(player.getLocation()), key);
        }
        Matcher m = LOCATED.matcher(params);
        if (!m.matches() || !m.group(1).equals(prefix)) {
            // coop_count_<player> style placeholders are handled as a count of co-op claims
            return new Target(null, null);
        }
        try {
            ChunkKey key = new ChunkKey(m.group(2), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            return new Target(Claim.claims.getAt(key.world(), key.x(), key.z()), key);
        } catch (NumberFormatException e) {
            return new Target(null, null);
        }
    }

    private String claimValue(String prefix, Claim claim, @Nullable ChunkKey key) {
        return switch (prefix) {
            case "claim_main_value" -> Numbers.format(plugin.getBlockValueManager().getChunkValue(claim.getChunkKey()));
            case "claim_total_value" -> Numbers.format(claim.getClaimValue());
            case "display_name" -> claim.getDisplayName();
            case "expiration" -> plugin.getClaimExpirationManager().getFormattedTimeLeft(claim);
            case "owner" -> name(claim.getOwner());
            case "coop_count" -> String.valueOf(claim.getCoopPlayers().size());
            case "total_size" -> String.valueOf(claim.getTotalChunkCount());
            case "sale_status" -> saleStatus(claim);
            default -> "";
        };
    }

    private String saleStatus(Claim claim) {
        if (!plugin.getNconfig().isSellEnabled() || !claim.isForSale()) return "";
        String price = lang("hologram.price_format").replace("{price}", EconomyService.format(claim.getSalePrice()));
        return " " + lang("hologram.title_sale_suffix").replace("{price}", price);
    }

    /* ---------------- config placeholders ---------------- */

    /** {@code get_<string|int|boolean|list>_<path>[_<index>]}; the path may contain underscores. */
    private String config(String params) {
        String[] parts = params.split("_", 3);
        if (parts.length < 3) return "Invalid config placeholder";
        String type = parts[1];
        String path = parts[2];
        int index = -1;
        if (type.equals("list")) {
            int cut = path.lastIndexOf('_');
            if (cut < 0) return "Index required";
            try {
                index = Integer.parseInt(path.substring(cut + 1));
            } catch (NumberFormatException e) {
                return "Invalid index";
            }
            path = path.substring(0, cut);
        }
        String root = path.split("\\.", 2)[0].toLowerCase(Locale.ROOT);
        if (HIDDEN_CONFIG.contains(root)) return "Hidden";

        return switch (type) {
            case "string" -> plugin.getConfigManager().getString(path, "Null");
            case "int" -> String.valueOf(plugin.getConfigManager().getInt(path, 0));
            case "boolean" -> String.valueOf(plugin.getConfigManager().getBoolean(path, false));
            case "list" -> {
                List<String> list = plugin.getConfigManager().getStringList(path);
                yield index >= 0 && index < list.size() ? list.get(index) : "Invalid index";
            }
            default -> "Unknown type: " + type;
        };
    }

    /* ---------------- helpers ---------------- */

    @Nullable
    private static Claim here(@Nullable Player player) {
        return player == null ? null : Claim.claims.getAt(player.getLocation());
    }

    private static long countOwned(UUID uuid) {
        return Claim.claims.stream().filter(c -> c.isOwner(uuid)).count();
    }

    private static long countCoop(UUID uuid) {
        return Claim.claims.stream().filter(c -> c.getCoopPlayers().contains(uuid)).count();
    }

    private static long totalValue(UUID uuid) {
        return Claim.claims.stream().filter(c -> c.isOwner(uuid)).mapToLong(Claim::getClaimValue).sum();
    }

    private static int totalChunks(UUID uuid) {
        return Claim.claims.stream().filter(c -> c.isOwner(uuid)).mapToInt(Claim::getTotalChunkCount).sum();
    }

    private static String name(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "Unknown";
    }

    private String lang(String path) {
        return plugin.getLangManager().getString(path);
    }
}
