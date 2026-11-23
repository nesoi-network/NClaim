package nesoi.aysihuniks.nclaim;

import lombok.Getter;
import nesoi.aysihuniks.nclaim.model.TimeLeftThreshold;
import nesoi.aysihuniks.nclaim.utils.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Typed view of config.yml. Values are re-read on every {@link #load()} (reload). */
@Getter
public class Config {

    /** Hard upper bound on chunks per claim (the land expansion menu shows an 11x11 grid minus corners). */
    public static final int ABSOLUTE_MAX_CHUNKS = 117;

    private final NClaim plugin;
    private YamlConfiguration config;

    private String defaultLanguage;
    private boolean checkForUpdates;
    private double claimBuyPrice;
    private double eachLandBuyPrice;
    private boolean enableTieredPricing;
    private int maxClaimChunks;
    private int claimExpiryDays;
    private int defaultMaxClaims;
    private int defaultMaxCoop;
    private long lastClaimTime;
    private List<String> blacklistedWorlds = List.of();
    private List<String> blacklistedRegions = List.of();
    private int claimDistanceChunks;
    private boolean claimDistanceCoopBypass;
    private boolean sellEnabled;
    private int autoSave;
    private String defaultClaimName;
    @lombok.Getter(lombok.AccessLevel.NONE)
    private List<NameRule> nameBlacklist = List.of();

    private boolean showHologramTitle;
    private boolean showHologramOwner;
    private boolean showHologramTimeLeft;
    private boolean showHologramCoopCount;
    private boolean showHologramTotalSize;
    private boolean showHologramEdit;

    private boolean databaseEnabled;
    private String databaseType;
    private String sqliteFile;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUser;
    private String mysqlPassword;
    private int maximumPoolSize;
    private int minimumIdle;
    private long idleTimeout;
    private long maxLifetime;
    private long connectionTimeout;

    private double timeExtensionPricePerMinute;
    private double timeExtensionPricePerHour;
    private double timeExtensionPricePerDay;
    private double timeExtensionTaxRate;

    private boolean webhookEnabled;
    private String webhookUrl;
    private boolean webhookUseEmbed;
    private String webhookContent;
    private String webhookEmbedTitle;
    private String webhookEmbedDescription;
    private String webhookEmbedColor;
    private String webhookEmbedFooter;
    private boolean webhookEmbedTimestamp;
    private String webhookEmbedImage;
    private String webhookEmbedThumbnail;
    private String webhookMention;

    private Material defaultClaimBlockType = Material.OBSIDIAN;
    private List<TimeLeftThreshold> timeLeftThresholds = List.of();

    public Config(@NotNull NClaim plugin) {
        this.plugin = plugin;
    }

    public YamlConfiguration get() {
        return config;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    public Config load() {
        File file = file();
        if (!file.exists()) plugin.saveResource("config.yml", false);
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        this.config = c;

        defaultLanguage = c.getString("lang_file", "en-US");
        checkForUpdates = c.getBoolean("check_for_updates", true);
        blacklistedWorlds = List.copyOf(c.getStringList("blacklisted_worlds"));
        blacklistedRegions = List.copyOf(c.getStringList("blacklisted_regions"));
        claimDistanceChunks = Math.max(0, c.getInt("claim_distance.chunks", 1));
        claimDistanceCoopBypass = c.getBoolean("claim_distance.bypass_coop_players", true);

        defaultMaxClaims = c.getInt("claim_settings.max_count", 3);
        claimBuyPrice = Math.max(0, c.getDouble("claim_settings.buy_price", 1500));
        eachLandBuyPrice = Math.max(0, c.getDouble("claim_settings.expand_price", 2000));
        enableTieredPricing = c.getBoolean("claim_settings.tiered_pricing.enable", false);
        maxClaimChunks = clamp(c.getInt("claim_settings.max_chunks", ABSOLUTE_MAX_CHUNKS), 1, ABSOLUTE_MAX_CHUNKS);
        defaultMaxCoop = c.getInt("claim_settings.max_coop_count.default", 3);
        claimExpiryDays = Math.max(1, c.getInt("claim_settings.expiry_days", 7));
        lastClaimTime = Math.max(0, c.getLong("claim_settings.last_claim_time", 5));
        sellEnabled = c.getBoolean("claim_settings.sell", true);
        defaultClaimName = c.getString("claim_settings.default_name", "");
        nesoi.aysihuniks.nclaim.utils.Numbers.configure(
                !"full".equalsIgnoreCase(c.getString("number_format.style", "compact")),
                c.getStringList("number_format.suffixes"));
        List<String> blacklist = new ArrayList<>(readBlacklistFile());
        blacklist.addAll(c.getStringList("claim_settings.name_blacklist"));
        nameBlacklist = compileBlacklist(blacklist);

        showHologramTitle = c.getBoolean("hologram_settings.show_title", true);
        showHologramOwner = c.getBoolean("hologram_settings.show_owner", true);
        showHologramTimeLeft = c.getBoolean("hologram_settings.show_time_left", true);
        showHologramCoopCount = c.getBoolean("hologram_settings.show_coop_count", true);
        showHologramTotalSize = c.getBoolean("hologram_settings.show_total_size", true);
        showHologramEdit = c.getBoolean("hologram_settings.show_edit", true);

        timeExtensionPricePerMinute = c.getDouble("time_extension.price_per_minute", 10);
        timeExtensionPricePerHour = c.getDouble("time_extension.price_per_hour", 500);
        timeExtensionPricePerDay = c.getDouble("time_extension.price_per_day", 10000);
        timeExtensionTaxRate = c.getDouble("time_extension.tax_rate", 0.1);

        autoSave = Math.max(1, c.getInt("auto_save", 30));

        databaseEnabled = c.getBoolean("database.enable", false);
        databaseType = c.getString("database.type", "sqlite");
        sqliteFile = c.getString("database.sqlite.file", "database.db");
        mysqlHost = c.getString("database.mysql.host", "localhost");
        mysqlPort = c.getInt("database.mysql.port", 3306);
        mysqlDatabase = c.getString("database.mysql.database", "nclaim");
        mysqlUser = c.getString("database.mysql.user", "root");
        mysqlPassword = c.getString("database.mysql.password", "");
        maximumPoolSize = c.getInt("database.mysql.maximum_pool_size", 10);
        minimumIdle = c.getInt("database.mysql.minimum_idle", 5);
        idleTimeout = c.getLong("database.mysql.idle_timeout", 300000);
        maxLifetime = c.getLong("database.mysql.max_lifetime", 1800000);
        connectionTimeout = c.getLong("database.mysql.connection_timeout", 30000);

        webhookEnabled = c.getBoolean("webhook_settings.enabled", false);
        webhookUrl = c.getString("webhook_settings.url", "");
        webhookUseEmbed = c.getBoolean("webhook_settings.use_embed", false);
        webhookContent = c.getString("webhook_settings.content", "");
        webhookEmbedTitle = c.getString("webhook_settings.embed.title", "");
        webhookEmbedDescription = c.getString("webhook_settings.embed.description", "");
        webhookEmbedColor = c.getString("webhook_settings.embed.color", "#FF0000");
        webhookEmbedFooter = c.getString("webhook_settings.embed.footer", "");
        webhookEmbedTimestamp = c.getBoolean("webhook_settings.embed.timestamp", true);
        webhookEmbedImage = c.getString("webhook_settings.embed.image", "");
        webhookEmbedThumbnail = c.getString("webhook_settings.embed.thumbnail", "");
        webhookMention = c.getString("webhook_settings.mention", "");

        Material block = Material.matchMaterial(c.getString("claim_settings.default_claim_block_type", "OBSIDIAN"));
        if (block == null || !block.isBlock() || !block.isSolid()) {
            Msg.log("&cclaim_settings.default_claim_block_type is not a solid block, using OBSIDIAN.");
            block = Material.OBSIDIAN;
        }
        defaultClaimBlockType = block;

        validateTierConfiguration();
        timeLeftThresholds = parseThresholds();
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /* ---------------- names (issue #13) ---------------- */

    /** Words shorter than this only match as a whole word, so "amk" does not hit "adam kadar". */
    private static final int SQUASH_MIN_LENGTH = 5;

    /**
     * One compiled blacklist entry. Entries are case-insensitive:
     * <ul>
     *   <li>Words of 5+ letters match at a word start, also when the name hides them with
     *       separators or look-alike characters ("N.1.g.g-3.r" matches "nigger").</li>
     *   <li>Shorter words match only as a whole word (look-alikes still count).</li>
     *   <li>{@code regex:...} entries are matched against the name as typed.</li>
     * </ul>
     */
    private record NameRule(@Nullable Pattern pattern, @Nullable String squashedWord) {
        boolean matches(String plain, String words, Squashed squashed) {
            if (squashedWord != null) return squashed.containsAtWordStart(squashedWord);
            // regex: entries see the name as typed; short words see the look-alike-mapped text
            return pattern.matcher(pattern.pattern().startsWith(WHOLE_WORD) ? words : plain).find();
        }
    }

    /**
     * The name's letters only, remembering which letters began a word. A long blacklisted word must
     * start at a word start: "n.i.g.g.e.r" and "orospular" are caught, but "Basement" (semen) or
     * "Therapist" (rapist) are not.
     */
    private record Squashed(String letters, boolean[] wordStart) {
        static Squashed of(String words) {
            StringBuilder letters = new StringBuilder(words.length());
            boolean[] starts = new boolean[words.length()];
            boolean previousLetter = false;
            for (char c : words.toCharArray()) {
                boolean letter = c >= 'a' && c <= 'z';
                if (letter) {
                    starts[letters.length()] = !previousLetter;
                    letters.append(c);
                }
                previousLetter = letter;
            }
            return new Squashed(letters.toString(), starts);
        }

        boolean containsAtWordStart(String word) {
            for (int i = letters.indexOf(word); i >= 0; i = letters.indexOf(word, i + 1)) {
                if (wordStart[i]) return true;
            }
            return false;
        }
    }

    private static final String WHOLE_WORD = "(?<![a-z])";

    private static final String BLACKLIST_FILE = "name_blacklist.txt";

    /**
     * Reads {@value #BLACKLIST_FILE}: one entry per line, {@code #} starts a comment. The file is
     * written on first start and never overwritten, so server owners can edit it freely.
     */
    private List<String> readBlacklistFile() {
        File file = new File(plugin.getDataFolder(), BLACKLIST_FILE);
        if (!file.exists() && plugin.getResource(BLACKLIST_FILE) != null) plugin.saveResource(BLACKLIST_FILE, false);
        if (!file.exists()) return List.of();
        try {
            List<String> entries = new ArrayList<>();
            for (String line : java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String entry = line.strip();
                if (!entry.isEmpty() && !entry.startsWith("#")) entries.add(entry);
            }
            return entries;
        } catch (java.io.IOException e) {
            Msg.log("&cCould not read " + BLACKLIST_FILE + ": " + e.getMessage());
            return List.of();
        }
    }

    private static List<NameRule> compileBlacklist(List<String> entries) {
        List<NameRule> rules = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            try {
                if (entry.startsWith("regex:")) {
                    rules.add(new NameRule(Pattern.compile(entry.substring(6), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), null));
                    continue;
                }
                String word = squash(entry);
                if (word.isEmpty()) continue;
                rules.add(word.length() >= SQUASH_MIN_LENGTH
                        ? new NameRule(null, word)
                        : new NameRule(Pattern.compile(WHOLE_WORD + Pattern.quote(word) + "(?![a-z])"), null));
            } catch (PatternSyntaxException e) {
                Msg.log("&cInvalid name_blacklist pattern '" + entry + "': " + e.getDescription());
            }
        }
        return Collections.unmodifiableList(rules);
    }

    public boolean isBlacklistedName(@NotNull String name) {
        String plain = nesoi.aysihuniks.nclaim.utils.Text.plain(name);
        String words = lookAlikes(plain);
        Squashed squashed = Squashed.of(words);
        for (NameRule rule : nameBlacklist) {
            if (rule.matches(plain, words, squashed)) return true;
        }
        return false;
    }

    /** Lower case, Turkish letters folded and common look-alike characters replaced (1 -> i, 3 -> e, @ -> a ...). */
    private static String lookAlikes(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (char c : input.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            out.append(switch (c) {
                case 'ı', 'i', 'í', 'ì', 'î', 'ï', '1', '!', '|' -> 'i';
                case 'ş', '$', '5' -> 's';
                case 'ğ', '9' -> 'g';
                case 'ü', 'ú', 'ù', 'û' -> 'u';
                case 'ö', 'ó', 'ò', 'ô', '0' -> 'o';
                case 'ç' -> 'c';
                case 'á', 'à', 'â', 'ä', '@', '4' -> 'a';
                case 'é', 'è', 'ê', 'ë', '3' -> 'e';
                case '7' -> 't';
                default -> c;
            });
        }
        return out.toString();
    }

    /** {@link #lookAlikes} with everything but letters removed. */
    private static String squash(String input) {
        return lookAlikes(input).replaceAll("[^a-z]", "");
    }

    /** Name given to a new claim when the player did not choose one; {@code null} means "use the claim id". */
    public String defaultClaimName(@NotNull String playerName, @NotNull String claimId) {
        if (defaultClaimName == null || defaultClaimName.isBlank()) return null;
        String name = defaultClaimName.replace("{player}", playerName);
        return name.length() > 24 ? name.substring(0, 24) : name;
    }

    /* ---------------- per-player limits ---------------- */

    public int getMaxCoopPlayers(@NotNull Player player) {
        if (player.hasPermission("nclaim.bypass.*") || player.hasPermission("nclaim.bypass.max_coop_count")) {
            return Integer.MAX_VALUE;
        }
        int max = defaultMaxCoop;
        ConfigurationSection section = config.getConfigurationSection("claim_settings.max_coop_count");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (!key.equals("default") && player.hasPermission("nclaim.maxcoop." + key)) {
                    max = Math.max(max, section.getInt(key));
                }
            }
        }
        return max;
    }

    public int getMaxClaimCount(@NotNull Player player) {
        if (player.hasPermission("nclaim.bypass.*") || player.hasPermission("nclaim.bypass.max_claim_count")) {
            return Integer.MAX_VALUE;
        }
        int max = defaultMaxClaims;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission();
            if (perm.startsWith("nclaim.maxclaim.")) {
                try {
                    max = Math.max(max, Integer.parseInt(perm.substring("nclaim.maxclaim.".length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    /* ---------------- pricing ---------------- */

    /** Price of the {@code chunkNumber}-th chunk of a claim, or {@code -1} above the limit. */
    public double getTieredPrice(int chunkNumber) {
        if (chunkNumber > maxClaimChunks) return -1;
        if (!enableTieredPricing) return eachLandBuyPrice;
        ConfigurationSection tiers = config.getConfigurationSection("claim_settings.tiered_pricing.tiers");
        if (tiers != null) {
            for (String key : tiers.getKeys(false)) {
                int min = tiers.getInt(key + ".min", 1);
                int max = tiers.getInt(key + ".max", 1);
                if (chunkNumber >= min && chunkNumber <= max) return Math.max(0, tiers.getDouble(key + ".price", 0));
            }
        }
        return eachLandBuyPrice;
    }

    private void validateTierConfiguration() {
        if (!enableTieredPricing) return;
        ConfigurationSection tiers = config.getConfigurationSection("claim_settings.tiered_pricing.tiers");
        if (tiers == null) return;
        for (String key : tiers.getKeys(false)) {
            int min = tiers.getInt(key + ".min", 1);
            int max = tiers.getInt(key + ".max", 1);
            if (min < 1 || min > max || max > ABSOLUTE_MAX_CHUNKS) {
                Msg.log("&cTier '" + key + "' is invalid (min=" + min + ", max=" + max + ", limit=" + ABSOLUTE_MAX_CHUNKS + ").");
            }
        }
    }

    /* ---------------- time left colours ---------------- */

    private List<TimeLeftThreshold> parseThresholds() {
        List<TimeLeftThreshold> out = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList("hologram_settings.time_left_thresholds")) {
            String[] parts = String.valueOf(map.get("threshold")).trim().split("\\s+");
            if (parts.length != 3) continue;
            try {
                TimeLeftThreshold t = new TimeLeftThreshold();
                t.unit = parts[0].toLowerCase(Locale.ROOT);
                t.operator = parts[1];
                t.value = Integer.parseInt(parts[2]);
                t.color = String.valueOf(map.get("color"));
                out.add(t);
            } catch (NumberFormatException e) {
                Msg.log("&cInvalid time_left_threshold: " + map.get("threshold"));
            }
        }
        return List.copyOf(out);
    }

    public static long getTimeUnitValue(long seconds, String unit) {
        return switch (unit) {
            case "day", "days" -> seconds / 86400;
            case "hour", "hours" -> seconds / 3600;
            case "minute", "minutes" -> seconds / 60;
            default -> seconds;
        };
    }

    public boolean isValidLanguage(String lang) {
        return new File(plugin.getDataFolder(), "lang/" + lang + ".yml").exists()
                || plugin.getResource("lang/" + lang + ".yml") != null;
    }

    /* ---------------- writing ---------------- */

    public void setBlacklistedWorlds(@NotNull List<String> worlds) {
        set("blacklisted_worlds", new ArrayList<>(worlds));
    }

    public void setBlacklistedRegions(@NotNull List<String> regions) {
        set("blacklisted_regions", new ArrayList<>(regions));
    }

    public void setDefaultLanguage(@NotNull String language) {
        set("lang_file", language);
    }

    /** Changes one value (admin menus / commands) and saves the file off-thread. */
    public void set(@NotNull String path, Object value) {
        config.set(path, value);
        save();
        load();
    }

    public void save() {
        String data = config.saveToString();
        File file = file();
        nesoi.aysihuniks.nclaim.utils.Scheduler.async(() -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), data, StandardCharsets.UTF_8);
            } catch (IOException e) {
                Msg.log("&cFailed to save config.yml: " + e.getMessage());
            }
        });
    }

    /**
     * Adds keys introduced by a new plugin version without touching existing values.
     * Runs only when {@code config_version} differs from the plugin version.
     */
    public Config updateConfig() {
        String version = plugin.getPluginMeta().getVersion();
        if (version.equals(config.getString("config_version"))) return this;
        InputStream defaults = plugin.getResource("config.yml");
        if (defaults == null) return this;
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : def.getKeys(true)) {
            if (def.isConfigurationSection(key) || config.contains(key, true)) continue;
            config.set(key, def.get(key));
            config.setComments(key, def.getComments(key));
            changed = true;
        }
        if (changed) backup();
        config.set("config_version", version);
        try {
            config.save(file());
            if (changed) Msg.log("&aAdded new settings to config.yml for version " + version + ".");
        } catch (IOException e) {
            Msg.log("&cCould not update config.yml: " + e.getMessage());
        }
        return load();
    }

    private void backup() {
        File dir = new File(plugin.getDataFolder(), "backups");
        if (!dir.exists() && !dir.mkdirs()) return;
        String date = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(new Date());
        try {
            java.nio.file.Files.copy(file().toPath(), new File(dir, "config_" + date + ".yml").toPath());
        } catch (IOException e) {
            Msg.log("&cFailed to back up config.yml: " + e.getMessage());
        }
    }
}
