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
import me.clip.placeholderapi.PlaceholderAPI;
import nesoi.aysihuniks.nclaim.utils.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
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
    private boolean placeholderApi;

    public HologramManager(@NotNull NClaim plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "hologram");
    }

    public void start() {
        placeholderApi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
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
        return !plugin.getNconfig().getHologramLines().isEmpty();
    }

    private Location locationOf(Claim claim) {
        return claim.getClaimBlockLocation().clone().add(0.5, plugin.getNconfig().getHologramHeight(), 0.5);
    }

    private void applyStyle(TextDisplay display) {
        Config config = plugin.getNconfig();
        display.setShadowed(config.isHologramShadow());
        float scale = config.getHologramScale();
        Transformation current = display.getTransformation();
        display.setTransformation(new Transformation(current.getTranslation(), current.getLeftRotation(),
                new Vector3f(scale, scale, scale), current.getRightRotation()));
        String background = config.getHologramBackground() == null ? "default" : config.getHologramBackground().trim();
        if (background.equalsIgnoreCase("default")) {
            display.setDefaultBackground(true);
            display.setBackgroundColor(null);
        } else if (background.equalsIgnoreCase("none")) {
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        } else {
            Color color = parseColor(background);
            if (color != null) {
                display.setDefaultBackground(false);
                display.setBackgroundColor(color);
            }
        }
    }

    /** "#RRGGBB" or "#AARRGGBB". */
    private static Color parseColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (h.length() == 6) return Color.fromARGB(0xFF000000 | Integer.parseInt(h, 16));
            if (h.length() == 8) return Color.fromARGB((int) Long.parseLong(h, 16));
        } catch (NumberFormatException ignored) {
        }
        return null;
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

        Location location = locationOf(claim);
        TextDisplay display = world.spawn(location, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(0.5f);
            d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, claim.getClaimId());
            applyStyle(d);
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
        for (Claim claim : Claim.claims) {
            TextDisplay display = displays.get(claim.getClaimId());
            if (display == null || !display.isValid()) {
                spawn(claim);
                continue;
            }
            Scheduler.entity(display, () -> {
                applyStyle(display);
                display.teleportAsync(locationOf(claim));
                display.text(Text.component(render(claim)));
            });
        }
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
        LangManager lang = plugin.getLangManager();
        List<String> lines = new ArrayList<>();
        for (String line : plugin.getNconfig().getHologramLines()) {
            String key = line.trim().toLowerCase(java.util.Locale.ROOT);
            String text = switch (key) {
                case "title" -> {
                    String title = lang.getString("hologram.title");
                    if (claim.isForSale()) {
                        String price = lang.getString("hologram.price_format").replace("{price}", EconomyService.format(claim.getSalePrice()));
                        title += " " + lang.getString("hologram.title_sale_suffix").replace("{price}", price);
                    }
                    yield title;
                }
                case "owner", "coop_count", "total_size", "value", "edit" -> lang.getString("hologram." + key);
                case "time_left" -> lang.getString("hologram.time_left.text");
                default -> line;
            };
            lines.add(fill(claim, text));
        }
        return String.join("\n", lines);
    }

    private String fill(Claim claim, String text) {
        if (text.indexOf('{') >= 0) {
            text = text.replace("{display_name}", claim.getDisplayName())
                    .replace("{owner}", ownerName(claim.getOwner()))
                    .replace("{time_left}", plugin.getClaimExpirationManager().getFormattedTimeLeft(claim))
                    .replace("{coop_count}", String.valueOf(claim.getCoopPlayers().size()))
                    .replace("{total_size}", String.valueOf(claim.getTotalChunkCount()))
                    .replace("{value}", Numbers.format(claim.getClaimValue()))
                    .replace("{claim_id}", claim.getClaimId());
        }
        if (placeholderApi && text.indexOf('%') >= 0) {
            text = PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(claim.getOwner()), text);
        }
        return text;
    }

    /** Owner names are cached; call when a claim changes hands. */
    public void forgetOwnerName(@NotNull UUID owner) {
        ownerNames.remove(owner);
    }
}
