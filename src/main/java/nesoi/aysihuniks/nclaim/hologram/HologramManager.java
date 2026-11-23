package nesoi.aysihuniks.nclaim.hologram;

import org.bukkit.event.world.EntitiesLoadEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import nesoi.aysihuniks.nclaim.Config;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.service.EconomyService;
import nesoi.aysihuniks.nclaim.utils.LangManager;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import nesoi.aysihuniks.nclaim.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim holograms on vanilla text displays. They are not persistent: spawned when the chunk loads
 * and gone with it, so no orphans. Updates run on the display's own scheduler (Folia).
 */
public final class HologramManager implements Listener {

    private static final long UPDATE_INTERVAL_TICKS = 20L;

    private final NClaim plugin;
    private final NamespacedKey tag;
    private final Map<String, TextDisplay> displays = new ConcurrentHashMap<>();
    private final Map<UUID, String> ownerNames = new ConcurrentHashMap<>();
    private ScheduledTask updateTask;

    public HologramManager(@NotNull NClaim plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "hologram");
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Claim claim : Claim.claims) spawn(claim);
        updateTask = Scheduler.globalTimer(this::updateAll, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    public void shutdown() {
        Scheduler.cancel(updateTask);
        HandlerList.unregisterAll(this);
        for (TextDisplay display : displays.values()) {
            // Not persistent, so the server discards them anyway; remove directly where allowed (plugin reloads).
            if (!Scheduler.isFolia() || Bukkit.isOwnedByCurrentRegion(display)) display.remove();
        }
        displays.clear();
    }

    /* ---------------- lifecycle ---------------- */

    private boolean enabled() {
        Config c = plugin.getNconfig();
        return c.isShowHologramTitle() || c.isShowHologramOwner() || c.isShowHologramTimeLeft()
                || c.isShowHologramCoopCount() || c.isShowHologramTotalSize() || c.isShowHologramEdit();
    }

    /** Creates the hologram if the claim's main chunk is loaded. Safe from any thread. */
    public void spawn(@NotNull Claim claim) {
        if (!enabled()) return;
        World world = claim.getWorld();
        if (world == null) return;
        Scheduler.at(world, claim.getChunkX(), claim.getChunkZ(), () -> spawnHere(claim));
    }

    private void spawnHere(Claim claim) {
        World world = claim.getWorld();
        if (world == null || claim.isRemoved() || !world.isChunkLoaded(claim.getChunkX(), claim.getChunkZ())) return;
        TextDisplay existing = displays.get(claim.getClaimId());
        if (existing != null && existing.isValid()) return;

        Location location = claim.getClaimBlockLocation().clone().add(0.5, 1.25, 0.5);
        TextDisplay display = world.spawn(location, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setViewRange(0.5f);
            d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, claim.getClaimId());
            d.text(Text.component(render(claim)));
        });
        displays.put(claim.getClaimId(), display);
    }

    public void remove(@NotNull Claim claim) {
        TextDisplay display = displays.remove(claim.getClaimId());
        if (display != null) Scheduler.entity(display, display::remove);
    }

    /** Re-renders a hologram now (after a rename, sale, new co-op ...). */
    public void refresh(@NotNull Claim claim) {
        TextDisplay display = displays.get(claim.getClaimId());
        if (display == null || !display.isValid()) {
            spawn(claim);
            return;
        }
        Scheduler.entity(display, () -> display.text(Text.component(render(claim))));
    }

    /** Called after a reload: settings or texts may have changed. */
    public void refreshAll() {
        ownerNames.clear();
        if (!enabled()) {
            new ArrayList<>(displays.keySet()).forEach(id -> {
                Claim claim = Claim.claims.getById(id);
                if (claim != null) remove(claim);
            });
            return;
        }
        for (Claim claim : Claim.claims) refresh(claim);
    }

    public void removeWorld(@NotNull String worldName) {
        displays.values().removeIf(display -> !display.isValid() || display.getWorld().getName().equals(worldName));
    }

    private void updateAll() {
        for (Map.Entry<String, TextDisplay> entry : displays.entrySet()) {
            TextDisplay display = entry.getValue();
            Claim claim = Claim.claims.getById(entry.getKey());
            if (claim == null || !display.isValid()) {
                displays.remove(entry.getKey(), display);
                continue;
            }
            Scheduler.entity(display, () -> display.text(Text.component(render(claim))));
        }
    }

    /* ---------------- events ---------------- */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Claim claim = Claim.claims.getAt(event.getChunk());
        if (claim != null && claim.isMainChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ())) {
            // Spawning during the load event itself is not allowed; do it on the next tick.
            Scheduler.atLater(claim.getClaimBlockLocation(), () -> spawnHere(claim), 1L);
        }
    }

    /** Removes displays left by an earlier plugin instance (for example after a hot reload). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof TextDisplay display)) continue;
            String id = display.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
            if (id != null && displays.get(id) != display) display.remove();
        }
    }

    /* ---------------- text ---------------- */

    private String ownerName(UUID owner) {
        return ownerNames.computeIfAbsent(owner, uuid ->
                Objects.requireNonNullElse(Bukkit.getOfflinePlayer(uuid).getName(), "?"));
    }

    private String render(Claim claim) {
        Config config = plugin.getNconfig();
        LangManager lang = plugin.getLangManager();
        List<String> lines = new ArrayList<>();

        if (config.isShowHologramTitle()) {
            String title = lang.getString("hologram.title").replace("{display_name}", claim.getDisplayName());
            if (claim.isForSale()) {
                String price = lang.getString("hologram.price_format").replace("{price}", EconomyService.format(claim.getSalePrice()));
                title += " " + lang.getString("hologram.title_sale_suffix").replace("{price}", price);
            }
            lines.add(title);
        }
        if (config.isShowHologramOwner()) {
            lines.add(lang.getString("hologram.owner").replace("{owner}", ownerName(claim.getOwner())));
        }
        if (config.isShowHologramTimeLeft()) {
            lines.add(lang.getString("hologram.time_left.text")
                    .replace("{time_left}", plugin.getClaimExpirationManager().getFormattedTimeLeft(claim)));
        }
        if (config.isShowHologramCoopCount()) {
            lines.add(lang.getString("hologram.coop_count").replace("{coop_count}", String.valueOf(claim.getCoopPlayers().size())));
        }
        if (config.isShowHologramTotalSize()) {
            lines.add(lang.getString("hologram.total_size").replace("{total_size}", String.valueOf(claim.getTotalChunkCount())));
        }
        if (config.isShowHologramEdit()) {
            if (lines.size() > 1) lines.add("");
            lines.add(lang.getString("hologram.edit"));
        }
        return String.join("\n", lines);
    }

    /** Owner names are cached; call when a claim changes hands. */
    public void forgetOwnerName(@NotNull UUID owner) {
        ownerNames.remove(owner);
    }
}
