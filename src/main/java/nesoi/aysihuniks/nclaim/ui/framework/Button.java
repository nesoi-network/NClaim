package nesoi.aysihuniks.nclaim.ui.framework;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** A clickable item that may occupy several slots of a {@link Menu}. */
public abstract class Button {

    @NotNull
    protected abstract Set<Integer> getSlots();

    @Nullable
    public abstract ItemStack getItem();

    /** Called on the clicking player's thread, one tick after the click. */
    public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
    }

    final boolean occupies(int slot) {
        return getSlots().contains(slot);
    }

    final Set<Integer> slots() {
        return getSlots();
    }
}
