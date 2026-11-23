package nesoi.aysihuniks.nclaim.ui.claim.management;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.service.ClaimBlockManager;
import nesoi.aysihuniks.nclaim.ui.framework.Button;
import nesoi.aysihuniks.nclaim.ui.framework.ItemCreator;
import nesoi.aysihuniks.nclaim.ui.framework.MenuType;
import nesoi.aysihuniks.nclaim.ui.framework.TextInput;
import nesoi.aysihuniks.nclaim.ui.shared.BaseMenu;
import nesoi.aysihuniks.nclaim.ui.shared.BlockPickerMenu;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import nesoi.aysihuniks.nclaim.utils.Msg;
import nesoi.aysihuniks.nclaim.utils.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Admin editor for a new claim block type (opened from an empty slot of {@link ManageClaimBlockMenu}).
 * <pre>
 *  row 1: . [back] . [block] [name] [price] . [scope] .
 *  row 3: . . . . [save] . . . .
 * </pre>
 * "Every claim" writes the block with its name and price to claim_blocks.yml; "this claim only"
 * offers it to this claim alone at the given price (0 = free).
 */
public class AddClaimBlockMenu extends BaseMenu {

    private static final int BACK = 10, BLOCK = 12, NAME = 13, PRICE = 14, SCOPE = 16, SAVE = 31;

    /** Everything entered so far; the menu is reopened with a new draft after each change. */
    public record Draft(@Nullable Material material, @Nullable String name, double price, boolean everyone) {
        static Draft empty() {
            return new Draft(null, null, 0, true);
        }

        String displayName() {
            if (name != null) return name;
            return material == null ? "?" : ClaimBlockManager.prettyName(material);
        }
    }

    private final @NotNull Claim claim;
    private final int returnPage;
    private final Draft draft;

    public AddClaimBlockMenu(@NotNull Player player, @NotNull Claim claim, int returnPage) {
        this(player, claim, returnPage, Draft.empty());
    }

    private AddClaimBlockMenu(@NotNull Player player, @NotNull Claim claim, int returnPage, @NotNull Draft draft) {
        super("claim_block_add_menu");
        this.claim = claim;
        this.returnPage = returnPage;
        this.draft = draft;
        setup();
        displayTo(player);
    }

    private void reopen(Player p, Draft next) {
        new AddClaimBlockMenu(p, claim, returnPage, next);
    }

    private void setup() {
        createInventory(MenuType.CHEST_5_ROWS, getString("title"));

        addButton(button(BACK, () -> ItemCreator.of(getMaterialFullPath("back"))
                .name(NClaim.inst().getGuiLangManager().getString("back.display_name"))
                .get(), (p, click) -> {
            MessageType.MENU_BACK.playSound(p);
            new ManageClaimBlockMenu(claim, p, returnPage, true);
        }));

        addButton(button(BLOCK, () -> {
            if (draft.material() == null) return item("block.empty", null, line -> line);
            return item("block.selected", draft.material(), line -> line.replace("{block}", ClaimBlockManager.prettyName(draft.material())));
        }, (p, click) -> new BlockPickerMenu(p, 0, null,
                material -> reopen(p, new Draft(material, null, draft.price(), draft.everyone())),
                () -> reopen(p, draft))));

        addButton(button(NAME, () -> item("name", null, line -> line.replace("{name}", draft.displayName())), (p, click) -> {
            if (draft.material() == null) {
                needBlock(p);
                return;
            }
            TextInput.open(p, getString("enter_a_name_title"), text -> {
                String name = text == null || text.isBlank() ? null : text.trim();
                reopen(p, new Draft(draft.material(), name, draft.price(), draft.everyone()));
            });
        }));

        addButton(button(PRICE, () -> item("price", null, line -> line.replace("{price}", Numbers.format(draft.price()))), (p, click) -> {
            TextInput.open(p, getString("enter_a_price_title"), text -> {
                double price;
                try {
                    price = text == null ? -1 : Double.parseDouble(text.trim());
                } catch (NumberFormatException e) {
                    price = -1;
                }
                if (!(price >= 0) || Double.isInfinite(price)) { // also rejects NaN
                    Msg.send(p, lang("command.enter_a_valid_number"));
                    MessageType.FAIL.playSound(p);
                    price = draft.price();
                }
                reopen(p, new Draft(draft.material(), draft.name(), price, draft.everyone()));
            });
        }));

        addButton(button(SCOPE, () -> item(draft.everyone() ? "scope.everyone" : "scope.this_claim", null, line -> line),
                (p, click) -> {
                    MessageType.VALUE_INCREASE.playSound(p);
                    reopen(p, new Draft(draft.material(), draft.name(), draft.price(), !draft.everyone()));
                }));

        addButton(button(SAVE, () -> item("save", null, line -> line
                .replace("{block}", draft.displayName())
                .replace("{price}", Numbers.format(draft.price()))
                .replace("{scope}", getString(draft.everyone() ? "scope.everyone.display_name" : "scope.this_claim.display_name"))), this::save));
    }

    private void save(Player p, ClickType click) {
        Material material = draft.material();
        if (material == null) {
            needBlock(p);
            return;
        }
        ClaimBlockManager blocks = NClaim.inst().getClaimBlockManager();
        String name = draft.displayName();
        if (blocks.getBlockInfo(material) != null
                || material == NClaim.inst().getNconfig().getDefaultClaimBlockType()
                || claim.getPurchasedBlockTypes().contains(material)
                || claim.getBlockOffers().containsKey(material)) {
            Msg.send(p, lang("claim.block.already_listed").replace("{block}", name));
            MessageType.FAIL.playSound(p);
            return;
        }

        if (draft.everyone()) {
            blocks.putBlock(material, name, draft.price());
            Msg.send(p, lang("claim.block.added_global")
                    .replace("{block}", name)
                    .replace("{price}", Numbers.format(draft.price())));
        } else {
            claim.getBlockOffers().put(material, draft.price());
            claim.markDirty();
            Msg.send(p, lang("claim.block.added_claim")
                    .replace("{block}", name)
                    .replace("{price}", Numbers.format(draft.price())));
        }
        MessageType.CONFIRM.playSound(p);
        new ManageClaimBlockMenu(claim, p, returnPage, true);
    }

    private void needBlock(Player p) {
        Msg.send(p, lang("claim.block.choose_a_block"));
        MessageType.FAIL.playSound(p);
    }

    /* ---------------- helpers ---------------- */

    private ItemStack item(String key, @Nullable Material material, UnaryOperator<String> placeholders) {
        List<String> lore = new ArrayList<>(getStringList(key + ".lore"));
        lore.replaceAll(line -> line == null ? "" : placeholders.apply(line));
        return (material != null ? ItemCreator.of(material) : ItemCreator.of(getMaterial(key)))
                .name(placeholders.apply(getString(key + ".display_name")))
                .lore(lore)
                .flags(ItemFlag.values())
                .get();
    }

    private static String lang(String key) {
        return NClaim.inst().getLangManager().getString(key);
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
