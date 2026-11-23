package nesoi.aysihuniks.nclaim.ui.framework;

import nesoi.aysihuniks.nclaim.utils.Scheduler;
import nesoi.aysihuniks.nclaim.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Chest menu backed by an {@link InventoryHolder}, so the listener can recognise it without
 * player metadata. Subclasses call {@link #createInventory}, add buttons and {@link #displayTo}.
 */
public abstract class Menu implements InventoryHolder {

    private final List<Button> buttons = new ArrayList<>();
    private Inventory inventory;

    protected void createInventory(@NotNull MenuType type, @Nullable String title) {
        this.inventory = Bukkit.createInventory(this, type.getSize(), Text.component(title == null ? "" : title));
        this.buttons.clear();
    }

    protected void addButton(@NotNull Button button) {
        buttons.add(button);
    }

    /** Background filler for empty slots; {@code null} leaves the slot empty. */
    @Nullable
    public SingleSlotButton backgroundButton(int slot) {
        return null;
    }

    public void onClose(@NotNull Player player, @NotNull InventoryCloseEvent.Reason reason) {
    }

    @Nullable
    public ItemStack getInvItem(int slot) {
        return inventory == null ? null : inventory.getItem(slot);
    }

    @Nullable
    Button getButton(int slot) {
        // Later buttons win, so a menu can override a slot by adding another button.
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button button = buttons.get(i);
            if (button.occupies(slot)) return button;
        }
        SingleSlotButton background = backgroundButton(slot);
        return background != null && inventory != null && isEmptySlot(slot) ? background : null;
    }

    private boolean isEmptySlot(int slot) {
        for (Button button : buttons) {
            if (button.occupies(slot)) return false;
        }
        return true;
    }

    private void render() {
        if (inventory == null) throw new IllegalStateException("createInventory() was not called");
        inventory.clear();
        for (Button button : buttons) {
            ItemStack item = button.getItem();
            if (item == null) continue;
            for (int slot : button.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
            }
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) != null) continue;
            SingleSlotButton background = backgroundButton(slot);
            if (background != null) inventory.setItem(slot, background.getItem());
        }
    }

    /** Renders the menu and opens it on the player's own thread. */
    protected void displayTo(@NotNull Player player) {
        render();
        Scheduler.ensureEntity(player, () -> player.openInventory(inventory));
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("createInventory() was not called");
        return inventory;
    }
}
