package nesoi.aysihuniks.nclaim.service;

import nesoi.aysihuniks.nclaim.NClaim;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import nesoi.aysihuniks.nclaim.utils.Msg;

import java.io.File;
import java.util.*;

public class ClaimBlockManager {

    // Replaced as a whole on (re)load, so readers on other threads never see a half-filled map.
    private volatile Map<Material, ClaimBlockInfo> claimBlocks = Map.of();

    public ClaimBlockManager() {
        loadClaimBlocks();
    }

    private void loadClaimBlocks() {
        File claimBlocksFile = new File(NClaim.inst().getDataFolder(), "claim_blocks.yml");
        if (!claimBlocksFile.exists()) {
            NClaim.inst().saveResource("claim_blocks.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(claimBlocksFile);

        Map<Material, ClaimBlockInfo> loaded = new LinkedHashMap<>();

        ConfigurationSection section = config.getConfigurationSection("claim_blocks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material mat = Material.matchMaterial(key);

                if (mat == null || !mat.isBlock()) {
                    Msg.log("Invalid claim block material in config: " + key);
                    continue;
                }

                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) continue;
                String displayName = entry.getString("display_name", mat.name());
                double price = entry.getDouble("price", 0);
                List<String> lore = entry.getStringList("lore");
                String permission = entry.getString("permission", null);
                boolean enabled = entry.getBoolean("enabled", true);

                loaded.put(mat, new ClaimBlockInfo(mat, displayName, price, lore, permission, enabled));
            }
        }
        claimBlocks = java.util.Collections.unmodifiableMap(loaded);
    }


    /* ---------------- admin editing (writes claim_blocks.yml) ---------------- */

    /** Adds a block for every claim, or updates its price when it is already listed. */
    public synchronized void putBlock(Material material, double price) {
        putBlock(material, null, price);
    }

    /** Same, with a display name; {@code null} keeps the current name (or uses the material name for a new block). */
    public synchronized void putBlock(Material material, @org.jetbrains.annotations.Nullable String displayName, double price) {
        edit(section -> {
            ConfigurationSection entry = section.getConfigurationSection(material.name());
            if (entry == null) entry = section.createSection(material.name());
            if (displayName != null) entry.set("display_name", displayName);
            else if (!entry.contains("display_name")) entry.set("display_name", prettyName(material));
            entry.set("price", price);
            entry.set("enabled", true);
        });
    }

    public synchronized void removeBlock(Material material) {
        edit(section -> section.set(material.name(), null));
    }

    private void edit(java.util.function.Consumer<ConfigurationSection> change) {
        File file = new File(NClaim.inst().getDataFolder(), "claim_blocks.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("claim_blocks");
        if (section == null) section = config.createSection("claim_blocks");
        change.accept(section);
        try {
            config.save(file);
        } catch (java.io.IOException e) {
            Msg.log("&cCould not save claim_blocks.yml: " + e.getMessage());
        }
        loadClaimBlocks();
    }

    /** "DIAMOND_BLOCK" -> "Diamond Block". */
    public static String prettyName(Material material) {
        StringBuilder out = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    public List<Material> getAllowedBlocks() {
        List<Material> list = new ArrayList<>();
        for (ClaimBlockInfo info : claimBlocks.values()) {
            if (info.enabled) list.add(info.material);
        }
        return list;
    }

    public ClaimBlockInfo getBlockInfo(Material mat) {
        return claimBlocks.get(mat);
    }

    public Collection<ClaimBlockInfo> getAllBlockInfos() {
        return claimBlocks.values();
    }

    public static class ClaimBlockInfo {
        public final Material material;
        public final String displayName;
        public final double price;
        public final List<String> lore;
        public final String permission;
        public final boolean enabled;

        public ClaimBlockInfo(Material material, String displayName, double price, List<String> lore, String permission, boolean enabled) {
            this.material = material;
            this.displayName = displayName;
            this.price = price;
            this.lore = lore;
            this.permission = permission;
            this.enabled = enabled;
        }
    }
}
