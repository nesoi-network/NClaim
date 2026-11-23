package nesoi.aysihuniks.nclaim.ui.framework;

import nesoi.aysihuniks.nclaim.utils.Text;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Small fluent item builder. Names and lore accept the colour formats handled by {@link Text}. */
public final class ItemCreator {

    private final ItemStack item;

    private ItemCreator(@NotNull ItemStack item) {
        this.item = item;
    }

    @NotNull
    public static ItemCreator of(@Nullable Material material) {
        Material mat = material == null || material.isAir() || !material.isItem() ? Material.BARRIER : material;
        return new ItemCreator(new ItemStack(mat));
    }

    @NotNull
    public static ItemCreator of(@Nullable ItemStack base) {
        if (base == null || base.getType().isAir()) return of(Material.BARRIER);
        return new ItemCreator(base.clone());
    }

    @NotNull
    public ItemCreator name(@Nullable String name) {
        if (name == null) return this;
        return edit(meta -> meta.displayName(Text.item(name)));
    }

    @NotNull
    public ItemCreator lore(@Nullable List<String> lore) {
        if (lore == null) return this;
        return edit(meta -> meta.lore(Text.itemLore(lore)));
    }

    @NotNull
    public ItemCreator amount(int amount) {
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return this;
    }

    /** Adds the enchantment glint. The enchantment argument is kept for source compatibility. */
    @NotNull
    public ItemCreator enchant(@Nullable Enchantment ignored, int level) {
        return glow(true);
    }

    @NotNull
    public ItemCreator glow(boolean glow) {
        return edit(meta -> meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null));
    }

    @NotNull
    public ItemCreator flags(@NotNull ItemFlag... flags) {
        return edit(meta -> meta.addItemFlags(flags));
    }

    @NotNull
    public ItemStack get() {
        return item;
    }

    private ItemCreator edit(@NotNull java.util.function.Consumer<ItemMeta> editor) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            editor.accept(meta);
            item.setItemMeta(meta);
        }
        return this;
    }
}
