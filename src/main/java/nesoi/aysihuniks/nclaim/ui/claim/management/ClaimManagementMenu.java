package nesoi.aysihuniks.nclaim.ui.claim.management;

import nesoi.aysihuniks.nclaim.utils.Numbers;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Permission;
import nesoi.aysihuniks.nclaim.enums.RemoveCause;
import nesoi.aysihuniks.nclaim.enums.SaleFilter;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.ClaimLevel;
import nesoi.aysihuniks.nclaim.service.ClaimBlockManager;
import nesoi.aysihuniks.nclaim.service.EconomyService;
import nesoi.aysihuniks.nclaim.ui.claim.admin.AdminAllClaimMenu;
import nesoi.aysihuniks.nclaim.ui.claim.coop.CoopListMenu;
import nesoi.aysihuniks.nclaim.ui.framework.Button;
import nesoi.aysihuniks.nclaim.ui.framework.ItemCreator;
import nesoi.aysihuniks.nclaim.ui.framework.MenuType;
import nesoi.aysihuniks.nclaim.ui.framework.TextInput;
import nesoi.aysihuniks.nclaim.ui.shared.BaseMenu;
import nesoi.aysihuniks.nclaim.ui.shared.ConfirmMenu;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import nesoi.aysihuniks.nclaim.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Main menu of one claim (5 rows, the background fills rows 0, 2 and 4).
 * <pre>
 *  row 1: . [info] . . [rename] . . [delete] .      (admin: [back] [info] at 10, 11)
 *  row 3: . [expand] [time] [co-ops] [settings] [claim block] [sell] [borders] .
 * </pre>
 * Texts and materials: guis.yml -> claim_management_menu.
 */
public class ClaimManagementMenu extends BaseMenu {

    // Admins get a back button at 10, so the info item moves one slot to the right.
    private static final int RENAME = 13, DELETE = 16;
    private static final int EXPAND = 28, TIME = 29, COOP = 30, SETTINGS = 31, CLAIM_BLOCK = 32, SELL = 33, BORDERS = 34;
    private static final int BACK = 10;
    /** How many of the most valuable blocks the info item lists. */
    private static final int TOP_BLOCKS = 3;

    private final @NotNull Claim claim;
    private final Player player;
    private final boolean admin;

    public ClaimManagementMenu(Player player, @NotNull Claim claim, boolean admin) {
        super("claim_management_menu");
        this.claim = claim;
        this.player = player;
        this.admin = admin;
        setupMenu();
        displayTo(player);
    }

    private void setupMenu() {
        createInventory(MenuType.CHEST_5_ROWS, getString("title"));

        addButton(button(admin ? 11 : 10, this::infoItem, (p, click) -> {
            if (NClaim.inst().getBlockValueManager().getBlockValues().isEmpty()) return; // no value system
            if (!ownerOrAdmin(p)) return;
            MessageType.MENU_REFRESH.playSound(p);
            p.closeInventory();
            NClaim.inst().getBlockValueManager().requestClaimCalculation(p.getUniqueId(), p.getName(), claim);
        }));

        addButton(button(RENAME, () -> item("rename", line -> line.replace("{name}", claim.getDisplayName())), (p, click) -> {
            if (!ownerOrAdmin(p)) return;
            TextInput.open(p, getString("enter_a_name_title"), text -> {
                if (NClaim.inst().getClaimService().renameClaim(p, claim, text)) MessageType.CONFIRM.playSound(p);
                else MessageType.FAIL.playSound(p);
                new ClaimManagementMenu(p, claim, admin);
            });
        }));

        addButton(button(DELETE, () -> item("delete"), (p, click) -> {
            if (!ownerOrAdmin(p)) return;
            new ConfirmMenu(p,
                    NClaim.inst().getGuiLangManager().getString("confirm_menu.children.delete_claim.display_name"),
                    NClaim.inst().getGuiLangManager().getStringList("confirm_menu.children.delete_claim.lore"),
                    result -> {
                        if ("confirmed".equals(result)) {
                            claim.remove(RemoveCause.UNCLAIM);
                            p.closeInventory();
                        } else if ("declined".equals(result)) {
                            new ClaimManagementMenu(p, claim, admin);
                        }
                    });
        }));

        addButton(button(EXPAND, this::expandItem, (p, click) -> {
            if (!allowed(p, Permission.EXPAND_CLAIM)) return;
            MessageType.MENU_FORWARD.playSound(p);
            new LandExpansionMenu(p, claim, admin);
        }));

        addButton(button(TIME, () -> item("time", line -> line.replace("{time_left}", timeLeft())), (p, click) -> {
            if (!allowed(p, Permission.EXTEND_EXPIRATION)) return;
            MessageType.MENU_FORWARD.playSound(p);
            new TimeManagementMenu(p, 0, 0, 0, 0, claim, admin);
        }));

        addButton(button(COOP, () -> item("coop", line -> line.replace("{coops}", String.valueOf(claim.getCoopPlayers().size()))), (p, click) -> {
            if (!allowed(p, Permission.ADD_COOP)) return;
            MessageType.MENU_FORWARD.playSound(p);
            new CoopListMenu(p, claim, admin);
        }));

        addButton(button(SETTINGS, () -> item("setting"), (p, click) -> {
            if (!allowed(p, Permission.MANAGE_SETTINGS)) return;
            MessageType.MENU_FORWARD.playSound(p);
            new ClaimSettingsMenu(p, claim, 0, admin);
        }));

        addButton(button(CLAIM_BLOCK, () -> item("claim_block", line -> line.replace("{block}", blockName(claim.getClaimBlockType()))),
                (p, click) -> {
                    MessageType.MENU_FORWARD.playSound(p);
                    new ManageClaimBlockMenu(claim, p, 0, admin);
                }));

        if (NClaim.inst().getNconfig().isSellEnabled()) {
            addButton(button(SELL, this::sellItem, (p, click) -> {
                if (!ownerOrAdmin(p)) return;
                if (click.isRightClick() && claim.isForSale()) cancelSale(p);
                else startSale(p);
            }));
        }

        addButton(button(BORDERS, () -> {
            boolean on = NClaim.inst().getClaimVisualizerService().isToggled(player);
            String status = getString(on ? "borders.status_on" : "borders.status_off");
            return item("borders", line -> line.replace("{status}", status));
        }, (p, click) -> {
            boolean on = NClaim.inst().getClaimVisualizerService().toggleBorders(p);
            Msg.send(p, NClaim.inst().getLangManager().getString(on ? "claim.border.enabled" : "claim.border.disabled"));
            (on ? MessageType.MENU_SELECT : MessageType.MENU_DESELECT).playSound(p);
            new ClaimManagementMenu(p, claim, admin);
        }));

        if (admin) {
            addButton(button(BACK, () -> ItemCreator.of(getMaterialFullPath("back"))
                    .name(NClaim.inst().getGuiLangManager().getString("back.display_name"))
                    .lore(NClaim.inst().getGuiLangManager().getStringList("back.lore"))
                    .get(), (p, click) -> {
                MessageType.MENU_BACK.playSound(p);
                new AdminAllClaimMenu(p, null, true, 0, new ArrayList<>(), SaleFilter.ALL);
            }));
        }
    }

    /* ---------------- sale ---------------- */

    private void startSale(Player p) {
        TextInput.open(p, getString("enter_a_price_title"), text -> {
            double price;
            try {
                price = text == null ? -1 : Double.parseDouble(text.trim());
            } catch (NumberFormatException e) {
                price = -1;
            }
            if (!(price >= 0) || Double.isInfinite(price)) { // also rejects NaN
                Msg.send(p, NClaim.inst().getLangManager().getString("command.enter_a_valid_number"));
                MessageType.FAIL.playSound(p);
                return;
            }
            claim.setForSale(true);
            claim.setSalePrice(price);
            claim.markDirty();
            refreshHologram();
            Msg.send(p, NClaim.inst().getLangManager().getString("claim.sell.started")
                    .replace("{price}", EconomyService.format(price))
                    .replace("{display_slug}", claim.getDisplayName()));
            MessageType.CONFIRM.playSound(p);
            new ClaimManagementMenu(p, claim, admin);
        });
    }

    private void cancelSale(Player p) {
        claim.setForSale(false);
        claim.setSalePrice(0);
        claim.markDirty();
        refreshHologram();
        Msg.send(p, NClaim.inst().getLangManager().getString("claim.sell.cancelled")
                .replace("{display_slug}", claim.getDisplayName()));
        MessageType.MENU_DESELECT.playSound(p);
        new ClaimManagementMenu(p, claim, admin);
    }

    private void refreshHologram() {
        if (NClaim.inst().getHologramManager() != null) NClaim.inst().getHologramManager().refresh(claim);
    }

    /* ---------------- permission checks ---------------- */

    private boolean allowed(Player p, Permission permission) {
        if (admin || claim.isOwner(p.getUniqueId())
                || NClaim.inst().getClaimCoopManager().hasPermission(p, claim, permission)) return true;
        denied(p);
        return false;
    }

    /** Only the owner (or an admin) may rename, sell, delete or recalculate; co-ops can open this menu too. */
    private boolean ownerOrAdmin(Player p) {
        if (admin || claim.isOwner(p.getUniqueId())) return true;
        denied(p);
        return false;
    }

    private static void denied(Player p) {
        Msg.send(p, NClaim.inst().getLangManager().getString("command.permission_denied"));
        MessageType.FAIL.playSound(p);
    }

    /* ---------------- items ---------------- */

    private ItemStack infoItem() {
        String owner = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        String sale = claim.isForSale()
                ? getString("info.for_sale").replace("{price}", EconomyService.format(claim.getSalePrice()))
                : getString("info.not_for_sale");

        // The value system only means something when block_levels.yml lists blocks; otherwise the
        // whole value section (and its "click to recalculate" hint) is left out.
        boolean valueSystem = !NClaim.inst().getBlockValueManager().getBlockValues().isEmpty();
        List<String> template = new ArrayList<>();
        for (String line : getStringList("info.lore")) {
            if (line != null && line.trim().equals("{value_section}")) {
                if (valueSystem) template.addAll(getStringList("info.value_section"));
            } else {
                template.add(line);
            }
        }

        List<String> lore = new ArrayList<>();
        for (String line : template) {
            if (line != null && line.contains("{valuable_blocks}")) {
                lore.addAll(valuableBlockLines(line));
                continue;
            }
            lore.add(line == null ? "" : line
                    .replace("{name}", claim.getDisplayName())
                    .replace("{owner}", owner == null ? "?" : owner)
                    .replace("{world}", claim.getWorldName())
                    .replace("{coordinates}", NClaim.getCoordinates(claim))
                    .replace("{size}", String.valueOf(claim.getAllChunkKeys().size()))
                    .replace("{max_size}", String.valueOf(NClaim.inst().getNconfig().getMaxClaimChunks()))
                    .replace("{coops}", String.valueOf(claim.getCoopPlayers().size()))
                    .replace("{value}", Numbers.format(claim.getClaimValue()))
                    .replace("{time_left}", timeLeft())
                    .replace("{sale}", sale));
        }
        return ItemCreator.of(claim.getClaimBlockType())
                .name(getString("info.display_name").replace("{name}", claim.getDisplayName()))
                .lore(lore)
                .flags(ItemFlag.values())
                .get();
    }

    /** One lore line per most valuable block (block_levels.yml), using the line as a template. */
    private List<String> valuableBlockLines(String template) {
        String format = getString("info.valuable_block_format");
        List<String> lines = new ArrayList<>();
        NClaim.inst().getBlockValueManager().getBlockValues().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Material, ClaimLevel> e) -> e.getValue().getValue()).reversed())
                .limit(TOP_BLOCKS)
                .forEach(e -> lines.add(template.replace("{valuable_blocks}", format
                        .replace("{block}", blockName(e.getKey()))
                        .replace("{value}", Numbers.format(e.getValue().getValue())))));
        return lines;
    }

    private ItemStack expandItem() {
        int size = claim.getAllChunkKeys().size();
        int max = NClaim.inst().getNconfig().getMaxClaimChunks();
        double price = NClaim.inst().getClaimService().calculateLandPrice(claim);
        String next = price < 0 ? getString("expand.max_reached") : "$" + EconomyService.format(price);
        return item("expand", line -> line
                .replace("{size}", String.valueOf(size))
                .replace("{max_size}", String.valueOf(max))
                .replace("{next_price}", next));
    }

    private ItemStack sellItem() {
        String key = claim.isForSale() ? "sell.lore_for_sale" : "sell.lore";
        String price = EconomyService.format(claim.getSalePrice());
        List<String> lore = new ArrayList<>(getStringList(key));
        if (lore.isEmpty()) lore = new ArrayList<>(getStringList("sell.lore")); // older guis.yml
        lore.replaceAll(line -> line == null ? "" : line.replace("{price}", price));
        return ItemCreator.of(getMaterial("sell"))
                .name(getString("sell.display_name"))
                .lore(lore)
                .flags(ItemFlag.values())
                .get();
    }

    private String timeLeft() {
        return NClaim.inst().getClaimExpirationManager().getFormattedTimeLeft(claim);
    }

    private static String blockName(@Nullable Material material) {
        if (material == null) return "?";
        ClaimBlockManager.ClaimBlockInfo info = NClaim.inst().getClaimBlockManager().getBlockInfo(material);
        if (info != null && info.displayName != null) return info.displayName;
        StringBuilder out = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private ItemStack item(String key) {
        return item(key, line -> line);
    }

    private ItemStack item(String key, UnaryOperator<String> placeholders) {
        List<String> lore = new ArrayList<>(getStringList(key + ".lore"));
        lore.replaceAll(line -> line == null ? "" : placeholders.apply(line));
        return ItemCreator.of(getMaterial(key))
                .name(placeholders.apply(getString(key + ".display_name")))
                .lore(lore)
                .flags(ItemFlag.values())
                .get();
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
