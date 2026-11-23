package nesoi.aysihuniks.nclaim.ui.shared;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.service.ClaimBlockManager;
import nesoi.aysihuniks.nclaim.ui.framework.Button;
import nesoi.aysihuniks.nclaim.ui.framework.ItemCreator;
import nesoi.aysihuniks.nclaim.ui.framework.MenuType;
import nesoi.aysihuniks.nclaim.ui.framework.TextInput;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Picker for blocks usable as a claim block, 45 per page, with search.
 * <pre>
 *  rows 0-4: blocks
 *  row 5:    [back] . . [previous] [search] [next] . . .
 * </pre>
 */
public class BlockPickerMenu extends BaseMenu {

    private static final int PER_PAGE = 45;
    private static final int BACK = 45, PREVIOUS = 48, SEARCH = 49, NEXT = 50;

    /** Blocks that are solid but make no sense as a claim block. */
    private static final Set<String> TECHNICAL = Set.of(
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK",
            "STRUCTURE_VOID", "JIGSAW", "BARRIER", "LIGHT", "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
            "PISTON", "STICKY_PISTON", "MOVING_PISTON", "PISTON_HEAD");

    private static volatile List<Material> allBlocks;

    private final Player player;
    private final int page;
    private final @Nullable String search;
    private final Consumer<Material> onPick;
    private final Runnable onBack;

    public BlockPickerMenu(@NotNull Player player, int page, @Nullable String search,
                           @NotNull Consumer<Material> onPick, @NotNull Runnable onBack) {
        super("block_picker_menu");
        this.player = player;
        this.page = Math.max(0, page);
        this.search = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        this.onPick = onPick;
        this.onBack = onBack;
        setup();
        displayTo(player);
    }

    /** Every material that may be used as a claim block. */
    public static List<Material> claimBlockCandidates() {
        List<Material> blocks = allBlocks;
        if (blocks == null) {
            List<Material> found = new ArrayList<>();
            for (Material material : Material.values()) {
                if (isCandidate(material)) found.add(material);
            }
            allBlocks = blocks = List.copyOf(found);
        }
        return blocks;
    }

    @SuppressWarnings("deprecation") // isLegacy: legacy constants are still listed by Material.values()
    public static boolean isCandidate(@NotNull Material material) {
        if (material.isLegacy() || !material.isBlock() || !material.isItem() || !material.isSolid()) return false;
        if (material.hasGravity() || TECHNICAL.contains(material.name())) return false;
        // Two-block-tall or wall-mounted blocks cannot be placed as a single claim block.
        return !Tag.DOORS.isTagged(material) && !Tag.BEDS.isTagged(material)
                && !Tag.ALL_SIGNS.isTagged(material) && !Tag.BANNERS.isTagged(material);
    }

    private List<Material> matching() {
        if (search == null) return claimBlockCandidates();
        List<Material> result = new ArrayList<>();
        for (Material material : claimBlockCandidates()) {
            if (material.name().toLowerCase(Locale.ROOT).replace('_', ' ').contains(search)
                    || material.name().toLowerCase(Locale.ROOT).contains(search)) {
                result.add(material);
            }
        }
        return result;
    }

    private void setup() {
        List<Material> blocks = matching();
        int pages = Math.max(1, (blocks.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.min(page, pages - 1);
        String title = getString("title")
                .replace("{page}", String.valueOf(current + 1))
                .replace("{pages}", String.valueOf(pages));
        createInventory(MenuType.CHEST_6_ROWS, title);

        int start = current * PER_PAGE;
        for (int j = 0; j < PER_PAGE && start + j < blocks.size(); j++) {
            Material material = blocks.get(start + j);
            addButton(button(j, () -> ItemCreator.of(material)
                    .name(getString("block.display_name").replace("{block}", ClaimBlockManager.prettyName(material)))
                    .lore(getStringList("block.lore"))
                    .get(), (p, click) -> {
                MessageType.MENU_SELECT.playSound(p);
                onPick.accept(material);
            }));
        }

        addButton(button(BACK, () -> ItemCreator.of(getMaterialFullPath("back"))
                .name(NClaim.inst().getGuiLangManager().getString("back.display_name"))
                .get(), (p, click) -> {
            MessageType.MENU_BACK.playSound(p);
            onBack.run();
        }));

        if (current > 0) {
            addButton(button(PREVIOUS, () -> ItemCreator.of(getMaterialFullPath("previous_page"))
                    .name(NClaim.inst().getGuiLangManager().getString("previous_page.display_name"))
                    .get(), (p, click) -> new BlockPickerMenu(p, current - 1, search, onPick, onBack)));
        }
        if (current + 1 < pages) {
            addButton(button(NEXT, () -> ItemCreator.of(getMaterialFullPath("next_page"))
                    .name(NClaim.inst().getGuiLangManager().getString("next_page.display_name"))
                    .get(), (p, click) -> new BlockPickerMenu(p, current + 1, search, onPick, onBack)));
        }

        addButton(button(SEARCH, () -> {
            List<String> lore = new ArrayList<>(getStringList("search.lore"));
            String shown = search == null ? getString("search.none") : search;
            String count = String.valueOf(blocks.size());
            lore.replaceAll(line -> line == null ? "" : line.replace("{search}", shown).replace("{count}", count));
            return ItemCreator.of(getMaterial("search"))
                    .name(getString("search.display_name"))
                    .lore(lore)
                    .get();
        }, (p, click) -> {
            if (click.isRightClick()) {
                new BlockPickerMenu(p, 0, null, onPick, onBack);
                return;
            }
            TextInput.open(p, getString("search_title"), text -> new BlockPickerMenu(p, 0, text, onPick, onBack));
        }));
    }

    private static Button button(int slot, Supplier<ItemStack> item, BiConsumer<Player, ClickType> click) {
        return new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Set.of(slot);
            }

            @Override
            public ItemStack getItem() {
                return item.get();
            }

            @Override
            public void onClick(@NotNull Player p, @NotNull ClickType clickType) {
                click.accept(p, clickType);
            }
        };
    }
}
