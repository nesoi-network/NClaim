package nesoi.aysihuniks.nclaim.utils;

import nesoi.aysihuniks.nclaim.Config;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Raw key access to config.yml. Always reads the currently loaded file, so it is reload-safe. */
public class ConfigManager {

    private final Config config;

    public ConfigManager(@NotNull Config config) {
        this.config = config;
    }

    public void set(String key, Object value) {
        config.get().set(key, value);
    }

    public Object get(String key, Object defaultValue) {
        return config.get().get(key, defaultValue);
    }

    public String getString(String key, String defaultValue) {
        Object value = config.get().get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        return config.get().getInt(key, defaultValue);
    }

    public long getLong(String key, long defaultValue) {
        return config.get().getLong(key, defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return config.get().getDouble(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return config.get().getBoolean(key, defaultValue);
    }

    public ConfigurationSection getConfigurationSection(String key) {
        return config.get().getConfigurationSection(key);
    }

    public List<String> getStringList(String key) {
        return config.get().getStringList(key);
    }

    public String getSound(String key, String defaultValue) {
        return getString("sounds." + key, defaultValue);
    }

    public void saveConfig() {
        config.save();
    }
}
