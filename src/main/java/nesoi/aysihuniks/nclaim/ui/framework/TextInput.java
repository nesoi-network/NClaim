package nesoi.aysihuniks.nclaim.ui.framework;

import nesoi.aysihuniks.nclaim.utils.Scheduler;
import nesoi.aysihuniks.nclaim.utils.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Anvil text prompt on the plain Bukkit/Paper API (no NMS). The callback gets the text when the
 * result slot is clicked; closing the anvil cancels.
 */
public final class TextInput implements Listener {

    private record Session(AnvilView view, Consumer<String> callback) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    public static void open(@NotNull Player player, @NotNull String title, @NotNull Consumer<String> callback) {
        Scheduler.ensureEntity(player, () -> {
            AnvilView view = org.bukkit.inventory.MenuType.ANVIL.create(player, Text.component(title));
            view.getTopInventory().setItem(0, inputItem(""));
            // Registered first: opening fires PrepareAnvilEvent right away.
            SESSIONS.put(player.getUniqueId(), new Session(view, callback));
            player.openInventory(view);
            if (player.getOpenInventory().getTopInventory() != view.getTopInventory()) {
                SESSIONS.remove(player.getUniqueId()); // another plugin cancelled the open
            }
        });
    }

    private static ItemStack inputItem(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.isEmpty() ? Component.empty() : Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private static Session session(InventoryView view, Player player) {
        Session session = SESSIONS.get(player.getUniqueId());
        return session != null && session.view().getTopInventory() == view.getTopInventory() ? session : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (session(event.getView(), player) == null) return;
        String text = event.getView().getRenameText();
        event.setResult(inputItem(text == null ? "" : text));
        event.getView().setRepairCost(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = session(event.getView(), player);
        if (session == null) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return;

        String text = session.view().getRenameText();
        SESSIONS.remove(player.getUniqueId());
        session.view().getTopInventory().clear();
        Scheduler.entity(player, () -> {
            player.closeInventory();
            session.callback().accept(text == null ? "" : text.trim());
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && session(event.getView(), player) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = session(event.getView(), player);
        if (session == null) return;
        // Clear the input slots so the anvil never hands the paper to the player.
        session.view().getTopInventory().clear();
        SESSIONS.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }
}
