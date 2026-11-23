package nesoi.aysihuniks.nclaim;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import nesoi.aysihuniks.nclaim.commands.AllCommandExecutor;
import nesoi.aysihuniks.nclaim.enums.Balance;
import nesoi.aysihuniks.nclaim.hologram.HologramManager;
import nesoi.aysihuniks.nclaim.integrations.AxSellWand;
import nesoi.aysihuniks.nclaim.integrations.Expansion;
import nesoi.aysihuniks.nclaim.integrations.farmer.FarmerIntegration;
import nesoi.aysihuniks.nclaim.integrations.Metrics;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.User;
import nesoi.aysihuniks.nclaim.model.UserManager;
import nesoi.aysihuniks.nclaim.service.*;
import nesoi.aysihuniks.nclaim.storage.SqlBackend;
import nesoi.aysihuniks.nclaim.storage.Storage;
import nesoi.aysihuniks.nclaim.storage.StorageBackend;
import nesoi.aysihuniks.nclaim.storage.YamlBackend;
import nesoi.aysihuniks.nclaim.ui.framework.MenuListener;
import nesoi.aysihuniks.nclaim.ui.framework.TextInput;
import nesoi.aysihuniks.nclaim.utils.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
public final class NClaim extends JavaPlugin {

    private static NClaim instance;

    private Config nconfig;
    private ConfigManager configManager;
    private LangManager langManager;
    private GuiLangManager guiLangManager;
    private HeadManager headManager;

    private Storage storage;
    private EconomyService economyService;
    private WebhookService webhookService;
    private ClaimService claimService;
    private ClaimExpirationManager claimExpirationManager;
    private ClaimCoopManager claimCoopManager;
    private ClaimBlockManager claimBlockManager;
    private ClaimVisualizerService claimVisualizerService;
    private ClaimSettingsManager claimSettingsManager;
    private ClaimLevelManager blockValueManager;
    private HologramManager hologramManager;

    private boolean worldGuardEnabled;
    private ScheduledTask autoSaveTask;

    public static NClaim inst() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Scheduler.init(this);
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create the plugin folder.");
        }

        nconfig = new Config(this).load().updateConfig();
        configManager = new ConfigManager(nconfig);
        Text.setPrefix(configManager.getString("prefix", "&8[<#fa8443>NClaim&8]&r"));

        headManager = new HeadManager();
        langManager = new LangManager(this, nconfig.getDefaultLanguage());
        guiLangManager = new GuiLangManager();

        storage = new Storage(createBackend());
        economyService = new EconomyService();
        webhookService = new WebhookService(this);
        blockValueManager = new ClaimLevelManager(this);
        blockValueManager.loadBlockValues();
        claimService = new ClaimService(this);
        claimExpirationManager = new ClaimExpirationManager(this);
        claimCoopManager = new ClaimCoopManager(this);
        claimVisualizerService = new ClaimVisualizerService(this);
        claimSettingsManager = new ClaimSettingsManager(this);
        claimBlockManager = new ClaimBlockManager();
        hologramManager = new HologramManager(this);

        loadClaims(null);
        for (Player player : Bukkit.getOnlinePlayers()) User.put(storage.loadUser(player.getUniqueId()));

        registerListeners();
        registerCommands();
        setupIntegrations();
        hologramManager.start();
        startTasks();

        new Metrics(this, 24693);
        if (nconfig.isCheckForUpdates()) new UpdateChecker(this, 122527).check();
    }

    @Override
    public void onDisable() {
        stopTasks();
        Scheduler.cancelAll();
        if (hologramManager != null) hologramManager.shutdown();
        if (claimVisualizerService != null) claimVisualizerService.shutdown();
        for (Player player : Bukkit.getOnlinePlayers()) player.closeInventory();
        if (storage != null) {
            storage.shutdown();
            Msg.log("&aSaved all claims and players.");
        }
        Claim.claims.clear();
        instance = null;
    }

    /* ---------------- storage ---------------- */

    private StorageBackend createBackend() {
        YamlBackend yaml = new YamlBackend(getDataFolder());
        if (!nconfig.isDatabaseEnabled()) return yaml;

        String type = nconfig.getDatabaseType().toLowerCase(Locale.ROOT);
        SqlBackend.Dialect dialect = type.equals("mysql") ? SqlBackend.Dialect.MYSQL : SqlBackend.Dialect.SQLITE;
        try {
            SqlBackend sql = new SqlBackend(dialect, nconfig, getDataFolder());
            Msg.log("&aConnected to " + dialect.name() + " database.");
            migrateFromYaml(yaml, sql);
            return sql;
        } catch (Exception e) {
            Msg.log("&cCould not connect to the " + type + " database, using YAML storage instead: " + e.getMessage());
            return yaml;
        }
    }

    /** Copies YAML data into an empty database once; the YAML files are kept as a backup. */
    private void migrateFromYaml(YamlBackend yaml, SqlBackend sql) {
        try {
            if (sql.countClaims() > 0 || yaml.countClaims() == 0) return;
            List<Claim> claims = yaml.loadClaims(null);
            sql.saveClaims(claims);
            File players = new File(getDataFolder(), "players");
            File[] files = players.listFiles((dir, name) -> name.endsWith(".yml"));
            int users = 0;
            if (files != null) {
                for (File file : files) {
                    try {
                        UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                        sql.saveUsers(List.of(yaml.loadUser(uuid)));
                        users++;
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            Msg.log("&aMigrated " + claims.size() + " claims and " + users + " players from YAML to the database.");
        } catch (Exception e) {
            Msg.log("&cYAML to database migration failed: " + e.getMessage());
        }
    }

    /** Loads claims of one world (or of every loaded world when {@code world} is null). */
    private void loadClaims(String world) {
        int added = 0;
        for (Claim claim : storage.loadClaims(world)) {
            if (Claim.claims.getById(claim.getClaimId()) != null) continue; // keep the live (possibly unsaved) copy
            if (Claim.claims.add(claim)) added++;
        }
        if (added > 0 || world == null) {
            Msg.log("&aLoaded " + added + " claims" + (world == null ? "" : " in world " + world) + " (" + storage.getBackend().name() + ").");
        }
    }

    /* ---------------- wiring ---------------- */

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new TextInput(), this);
        pm.registerEvents(new UserManager(this), this);
        pm.registerEvents(new ClaimManager(this, claimCoopManager), this);
        pm.registerEvents(new WorldListener(), this);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("nclaim");
        if (command != null) {
            AllCommandExecutor executor = new AllCommandExecutor();
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    private void setupIntegrations() {
        var pm = getServer().getPluginManager();
        worldGuardEnabled = pm.isPluginEnabled("WorldGuard");
        if (worldGuardEnabled) Msg.log("&aWorldGuard found, blacklisted regions are active.");
        if (pm.isPluginEnabled("PlaceholderAPI")) {
            new Expansion(this).register();
            Msg.log("&aPlaceholderAPI expansion registered.");
        }
        if (pm.isPluginEnabled("AxSellwands")) {
            pm.registerEvents(new AxSellWand(this), this);
            Msg.log("&aAxSellwands integration enabled.");
        }
        if (pm.isPluginEnabled("Farmer")) {
            FarmerIntegration.register();
        }
    }

    private void startTasks() {
        claimExpirationManager.startExpirationChecker();
        long minutes = nconfig.getAutoSave();
        // Claim values are recalculated from chunk snapshots off-thread; saving itself is continuous.
        autoSaveTask = Scheduler.asyncTimer(() -> {
            blockValueManager.recalculateLoaded();
            Msg.log("&7Auto-save: recalculating claim values (" + Claim.claims.size() + " claims).");
        }, minutes, minutes, TimeUnit.MINUTES);
    }

    private void stopTasks() {
        if (claimExpirationManager != null) claimExpirationManager.stopExpirationChecker();
        Scheduler.cancel(autoSaveTask);
        autoSaveTask = null;
    }

    public void reloadPlugin() {
        stopTasks();
        boolean wasDatabase = nconfig.isDatabaseEnabled();
        String oldType = nconfig.getDatabaseType();

        nconfig.load().updateConfig();
        Text.setPrefix(configManager.getString("prefix", "&8[<#fa8443>NClaim&8]&r"));
        Sounds.clearCache();
        langManager = new LangManager(this, nconfig.getDefaultLanguage());
        guiLangManager = new GuiLangManager();
        claimBlockManager = new ClaimBlockManager();
        blockValueManager.reloadBlockValues();
        storage.flushNow();
        hologramManager.refreshAll();
        startTasks();

        if (wasDatabase != nconfig.isDatabaseEnabled() || !oldType.equalsIgnoreCase(nconfig.getDatabaseType())) {
            Msg.log("&eStorage settings changed; restart the server to switch storage.");
        }
        Msg.log("&aReload complete.");
    }

    /* ---------------- compatibility helpers ---------------- */

    public @NotNull Balance getBalanceSystem() {
        return economyService.getSystem();
    }

    /** @deprecated use {@link #getEconomyService()}; this is {@code null} without Vault. */
    @Deprecated
    public Economy getEconomy() {
        return economyService.getVault();
    }

    public static String serializeDate(Date date) {
        return nesoi.aysihuniks.nclaim.storage.Codec.date(date);
    }

    public static String getCoordinates(@NotNull Claim claim) {
        return claim.getCenterX() + "," + claim.getCenterZ();
    }

    /** Loads claims of worlds that load after startup (multiverse etc.) and drops them on unload. */
    private final class WorldListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onWorldLoad(WorldLoadEvent event) {
            String name = event.getWorld().getName();
            Scheduler.async(() -> {
                List<Claim> loaded = storage.loadClaims(name);
                Scheduler.global(() -> {
                    int added = 0;
                    for (Claim claim : loaded) {
                        if (Claim.claims.getById(claim.getClaimId()) == null && Claim.claims.add(claim)) {
                            added++;
                            hologramManager.spawn(claim);
                        }
                    }
                    if (added > 0) Msg.log("&aLoaded " + added + " claims in world " + name + ".");
                });
            });
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onWorldUnload(WorldUnloadEvent event) {
            if (event.isCancelled()) return;
            // Claims stay registered (and saved); only the display entities go away with the world.
            World world = event.getWorld();
            hologramManager.removeWorld(world.getName());
        }
    }
}
