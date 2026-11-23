package nesoi.aysihuniks.nclaim.utils;

import nesoi.aysihuniks.nclaim.NClaim;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import nesoi.aysihuniks.nclaim.utils.Text;
import nesoi.aysihuniks.nclaim.utils.Msg;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class LangManager {

    private final @NotNull NClaim plugin;
    private final File folder;
    public final List<String> REGISTERED_LANGUAGES = new ArrayList<>();
    private final List<String> DEFAULT_LANGUAGES = Arrays.asList("en-US", "tr-TR", "fr-FR");
    private final String DEFAULT_LANGUAGE = "en-US";
    private FileConfiguration DEFAULT_LANGUAGE_CONFIG;
    private FileConfiguration SELECTED_LANGUAGE_CONFIG;

    public LangManager(@NotNull NClaim plugin, @NotNull String fileName) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.loadDefaultFiles();
        this.loadFiles(fileName);
    }

    private void loadFiles(@NotNull String searchingFor) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName().substring(0, file.getName().length() - 4);
                REGISTERED_LANGUAGES.add(fileName);
                if (fileName.equals(searchingFor)) {
                    this.SELECTED_LANGUAGE_CONFIG = (DEFAULT_LANGUAGES.contains(fileName)) ? updateLanguage(fileName) : YamlConfiguration.loadConfiguration(file);
                }
                if (fileName.equals(DEFAULT_LANGUAGE)) {
                    this.DEFAULT_LANGUAGE_CONFIG = updateLanguage(fileName);
                }
            }
        }
        if (this.SELECTED_LANGUAGE_CONFIG == null) {
            this.SELECTED_LANGUAGE_CONFIG = this.DEFAULT_LANGUAGE_CONFIG;
            Msg.log("&cLanguage " + searchingFor + " was not found. Using default language.");
        }
    }

    private void loadDefaultFiles() {
        for (String fileName : DEFAULT_LANGUAGES) {
            File file = new File(folder, fileName + ".yml");
            if (file.exists() || plugin.getResource("lang/" + fileName + ".yml") == null) continue;
            plugin.saveResource("lang/" + fileName + ".yml", false);
        }
    }

    public FileConfiguration updateLanguage(@NotNull String languageName) {
        File file = new File(folder, languageName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        String version = plugin.getPluginMeta().getVersion();
        String configVersion = config.getString("lang_version", "0");

        if (version.equals(configVersion)) return config;

        Msg.log("&eNew language version detected for " + languageName + " (" + version + "), updating...");

        FileConfiguration defConfig = getSourceConfiguration(languageName);
        if (defConfig == null) return config;

        saveBackupConfig(languageName, config);

        // Across a major version the old file only goes to backups/; otherwise the owner's edits are carried over.
        if (major(configVersion) >= major(version)) {
            for (String key : defConfig.getKeys(true)) {
                if (defConfig.isConfigurationSection(key)) continue;
                if (config.contains(key)) {
                    defConfig.set(key, config.get(key));
                }
            }
        } else {
            Msg.log("&e" + languageName + ".yml was replaced with the " + version + " messages; your old file is in backups/.");
        }

        defConfig.set("lang_version", version);
        try {
            defConfig.save(file);
            Msg.log("&aLanguage file '" + languageName + "' successfully updated to version " + version + "!");
        } catch (Exception e) {
            Msg.log("&cAn error occurred while saving the updated language file: " + languageName);
        }
        return defConfig;
    }

    /** Major part of a version such as "4.0.0"; 0 when missing or unreadable. */
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

    private FileConfiguration getSourceConfiguration(@NotNull String languageName) {
        InputStream defStream = plugin.getResource("lang/" + languageName + ".yml");
        if (defStream == null) {
            Msg.log("&cDefault '" + languageName + ".yml' was not found in plugin resources.");
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
    }

    private void saveBackupConfig(@NotNull String languageName, @NotNull FileConfiguration config) {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File backupFile = new File(backupDir, "lang_" + languageName + "_" + date + ".yml");
        try {
            config.save(backupFile);
            Msg.log("&aBacked up old language file: " + languageName);
        } catch (Exception e) {
            Msg.log("&cFailed to save backup file for: " + languageName);
        }
    }

    /** Sends a message that may start with CHAT=, ACTION_BAR= or TITLE=. */
    static public void sendSortedMessage(@NotNull Player player, String msg) {
        Msg.sendSorted(player, msg);
    }

    /** Human readable duration using the hologram time units (e.g. "2m, 5s"). */
    @NotNull
    public String formatDuration(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long days = s / 86400, hours = (s % 86400) / 3600, minutes = (s % 3600) / 60, seconds = s % 60;
        String d = getRaw("hologram.time_left.d", "d"), h = getRaw("hologram.time_left.h", "h");
        String m = getRaw("hologram.time_left.m", "m"), sec = getRaw("hologram.time_left.s", "s");
        if (days > 0) return days + d + ", " + hours + h;
        if (hours > 0) return hours + h + ", " + minutes + m;
        if (minutes > 0) return minutes + m + ", " + seconds + sec;
        return seconds + sec;
    }

    private String getRaw(String path, String fallback) {
        String value = SELECTED_LANGUAGE_CONFIG.getString(path, DEFAULT_LANGUAGE_CONFIG.getString(path));
        return value == null ? fallback : Text.legacy(value);
    }

    @Nullable
    public ConfigurationSection getSection(@NotNull String path) {
        ConfigurationSection section = SELECTED_LANGUAGE_CONFIG.getConfigurationSection(path);
        if (section != null) return section;
        return DEFAULT_LANGUAGE_CONFIG.getConfigurationSection(path);
    }

    @NotNull
    public String getString(@NotNull String path) {
        String str = SELECTED_LANGUAGE_CONFIG.contains(path)
                ? SELECTED_LANGUAGE_CONFIG.getString(path)
                : DEFAULT_LANGUAGE_CONFIG.getString(path);
        if (str == null) {
            Msg.log("&cNull message at path '" + path + "'");
            return "";
        }
        String prefix = NClaim.inst().getConfigManager().getString("prefix", "&8[<#fa8443>NClaim&8]&r");
        return Text.legacy(str.replace("{prefix}", prefix));
    }

    @NotNull
    public List<String> getStringList(@NotNull String path) {
        List<String> list = SELECTED_LANGUAGE_CONFIG.contains(path)
                ? SELECTED_LANGUAGE_CONFIG.getStringList(path)
                : DEFAULT_LANGUAGE_CONFIG.getStringList(path);
        String prefix = NClaim.inst().getConfigManager().getString("prefix", "&8[<#fa8443>NClaim&8]&r");
        return list.stream()
                .map(str -> Text.legacy(str.replace("{prefix}", prefix)))
                .collect(Collectors.toList());
    }

    @NotNull
    public Boolean getBoolean(@NotNull String path) {
        return SELECTED_LANGUAGE_CONFIG.contains(path)
                ? SELECTED_LANGUAGE_CONFIG.getBoolean(path)
                : DEFAULT_LANGUAGE_CONFIG.getBoolean(path);
    }

    @NotNull
    public String getString(@Nullable ConfigurationSection section, @NotNull String subPath) {
        final String currentPath = section == null ? "" : section.getCurrentPath();
        return getString(currentPath + "." + subPath);
    }

    @NotNull
    public List<String> getStringList(@Nullable ConfigurationSection section, @NotNull String subPath) {
        final String currentPath = section == null ? "" : section.getCurrentPath();
        return getStringList(currentPath + "." + subPath);
    }

    @NotNull
    public Boolean getBoolean(@Nullable ConfigurationSection section, @NotNull String subPath) {
        final String currentPath = section == null ? "" : section.getCurrentPath();
        return getBoolean(currentPath + "." + subPath);
    }
}
