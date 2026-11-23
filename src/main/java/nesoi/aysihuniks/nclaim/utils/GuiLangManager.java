package nesoi.aysihuniks.nclaim.utils;

import lombok.Getter;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.integrations.CustomItems;
import nesoi.aysihuniks.nclaim.model.SettingCfg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** guis.yml: menu titles, item names/lore and item materials. */
public class GuiLangManager {

    @Getter
    private FileConfiguration guiConfig;

    private final HeadManager headManager;
    private final Map<Setting, SettingCfg> settingConfigs = new EnumMap<>(Setting.class);

    public GuiLangManager() {
        this.headManager = NClaim.inst().getHeadManager();
        load();
        updateConfig();
    }

    private File file() {
        return new File(NClaim.inst().getDataFolder(), "guis.yml");
    }

    public void load() {
        File file = file();
        if (!file.exists()) NClaim.inst().saveResource("guis.yml", false);
        this.guiConfig = YamlConfiguration.loadConfiguration(file);
        loadSettingConfigs();
    }

    /** Adds entries introduced by a new version; never overwrites what the server owner changed. */
    public GuiLangManager updateConfig() {
        String version = NClaim.inst().getPluginMeta().getVersion();
        String stored = guiConfig.getString("gui_version");
        if (version.equals(stored)) return this;
        // Layouts differ between major versions, so an older file is moved to backups/ and replaced, not merged.
        if (major(stored) < major(version)) {
            backup();
            NClaim.inst().saveResource("guis.yml", true);
            guiConfig = YamlConfiguration.loadConfiguration(file());
            guiConfig.set("gui_version", version);
            try {
                guiConfig.save(file());
            } catch (IOException e) {
                Msg.log("&cCould not update guis.yml: " + e.getMessage());
            }
            Msg.log("&eguis.yml was replaced with the " + version + " menus; your old file is in backups/.");
            loadSettingConfigs();
            return this;
        }
        InputStream defStream = NClaim.inst().getResource("guis.yml");
        if (defStream == null) return this;
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : def.getKeys(true)) {
            if (def.isConfigurationSection(key) || guiConfig.contains(key, true)) continue;
            guiConfig.set(key, def.get(key));
            changed = true;
        }
        if (changed) backup();
        guiConfig.set("gui_version", version);
        try {
            guiConfig.save(file());
            if (changed) Msg.log("&aAdded new entries to guis.yml for version " + version + ".");
        } catch (IOException e) {
            Msg.log("&cCould not update guis.yml: " + e.getMessage());
        }
        loadSettingConfigs();
        return this;
    }

    /** Major part of a version such as "4.0.0"; 0 when missing or unreadable (files from before 4.0). */
    private static int major(String version) {
        if (version == null) return 0;
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        try {
            return end == 0 ? 0 : Integer.parseInt(version.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void backup() {
        File dir = new File(NClaim.inst().getDataFolder(), "backups");
        if (!dir.exists() && !dir.mkdirs()) return;
        String date = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
        try {
            Files.copy(file().toPath(), new File(dir, "guis_" + date + ".yml").toPath());
        } catch (IOException e) {
            Msg.log("&cFailed to back up guis.yml: " + e.getMessage());
        }
    }

    private void loadSettingConfigs() {
        settingConfigs.clear();
        ConfigurationSection settings = guiConfig.getConfigurationSection("guis.claim_settings_menu.settings");
        if (settings == null) return;
        for (String key : settings.getKeys(false)) {
            ConfigurationSection s = settings.getConfigurationSection(key);
            Setting setting = settingByKey(key);
            if (s == null || setting == null) continue;
            settingConfigs.put(setting, new SettingCfg(
                    s.getBoolean("default_value", false),
                    s.getBoolean("changeable", true),
                    s.getString("permission", null),
                    s.getString("display_name", key),
                    s.getString("material", "DIRT"),
                    s.getStringList("lore")));
        }
    }

    private static Setting settingByKey(String key) {
        return switch (key) {
            case "pvp" -> Setting.CLAIM_PVP;
            case "tnt_explosions" -> Setting.TNT_DAMAGE;
            case "creeper_explosions" -> Setting.CREEPER_DAMAGE;
            case "mob_attacks" -> Setting.MOB_ATTACKING;
            case "monster_spawning" -> Setting.MONSTER_SPAWNING;
            case "animal_spawning" -> Setting.ANIMAL_SPAWNING;
            case "villager_interactions" -> Setting.VILLAGER_INTERACTION;
            case "fire_spread" -> Setting.FIRE_SPREAD;
            case "mob_griefing" -> Setting.MOB_GRIEFING;
            case "crop_trampling" -> Setting.CROP_TRAMPLING;
            case "other_explosions" -> Setting.OTHER_EXPLOSIONS;
            default -> Setting.byName(key);
        };
    }

    public SettingCfg getSettingConfig(Setting setting) {
        return settingConfigs.get(setting);
    }

    public String getString(String section, String path) {
        return guiConfig.getString("guis." + section + "." + path);
    }

    public String getString(String section, String path, String defaultValue) {
        return guiConfig.getString("guis." + section + "." + path, defaultValue);
    }

    public List<String> getStringList(String section, String path) {
        return guiConfig.getStringList("guis." + section + "." + path);
    }

    public String getString(String fullPath) {
        String value = guiConfig.getString("guis." + fullPath);
        if (value == null) Msg.log("&cguis.yml path not found: " + fullPath);
        return value;
    }

    public List<String> getStringList(String fullPath) {
        return guiConfig.getStringList("guis." + fullPath);
    }

    public ConfigurationSection getSection(String path) {
        return guiConfig.getConfigurationSection("guis." + path);
    }

    public Boolean getBoolean(String path) {
        return guiConfig.getBoolean("guis." + path);
    }

    public ItemStack getMaterial(String section, String path) {
        return getMaterial(section + "." + path);
    }

    public ItemStack getMaterial(String fullPath) {
        return resolveItemStack(guiConfig.getString("guis." + fullPath + ".material"), "guis." + fullPath);
    }

    /** The material name configured at a path, for comparing clicked items with menu state. */
    public String getMaterialName(String fullPath) {
        return guiConfig.getString("guis." + fullPath + ".material");
    }

    /**
     * Formats: {@code STONE}, {@code STONE:1001} (custom model data), {@code HEAD-<texture>},
     * {@code model:namespace:key} (item model), {@code itemsadder:namespace:id}, {@code nexo:id}, {@code oraxen:id}
     * (base64 or textures.minecraft.net id) and {@code HEAD-<texture>:1001}.
     */
    private ItemStack resolveItemStack(String raw, String logPath) {
        if (raw == null || raw.isBlank()) {
            Msg.log("&cMaterial not set at " + logPath + ", using DIRT.");
            return new ItemStack(Material.DIRT);
        }
        String spec = raw.trim();
        if (CustomItems.handles(spec)) {
            ItemStack custom = CustomItems.get(spec);
            if (custom != null) return custom;
            Msg.log("&cCustom item '" + spec + "' at " + logPath + " was not found (is the plugin installed and loaded?), using DIRT.");
            return new ItemStack(Material.DIRT);
        }
        if (spec.toLowerCase(Locale.ROOT).startsWith("model:")) {
            NamespacedKey model = NamespacedKey.fromString(spec.substring(6).toLowerCase(Locale.ROOT));
            ItemStack item = new ItemStack(Material.PAPER);
            if (model == null) {
                Msg.log("&cInvalid item model '" + spec + "' at " + logPath + ".");
                return item;
            }
            item.editMeta(meta -> meta.setItemModel(model));
            return item;
        }
        Integer customModelData = null;
        int colon = spec.lastIndexOf(':');
        if (colon > 0 && spec.substring(colon + 1).chars().allMatch(Character::isDigit) && colon < spec.length() - 1) {
            customModelData = Integer.parseInt(spec.substring(colon + 1));
            spec = spec.substring(0, colon);
        }

        ItemStack item;
        if (spec.toUpperCase(Locale.ROOT).startsWith("HEAD")) {
            String texture = spec.length() > 5 ? spec.substring(5) : "";
            item = headManager.createHeadWithTexture(texture);
        } else {
            Material material = Material.matchMaterial(spec);
            if (material == null || !material.isItem()) {
                Msg.log("&cInvalid material '" + spec + "' at " + logPath + ", using DIRT.");
                material = Material.DIRT;
            }
            item = new ItemStack(material);
        }

        if (customModelData != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                applyCustomModelData(meta, customModelData);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static void applyCustomModelData(ItemMeta meta, int value) {
        meta.setCustomModelData(value);
    }
}
