package nesoi.aysihuniks.nclaim.ui.claim.management;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Permission;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.service.ClaimBlockManager;
import nesoi.aysihuniks.nclaim.service.ClaimBlockManager.ClaimBlockInfo;
import nesoi.aysihuniks.nclaim.ui.framework.Button;
import nesoi.aysihuniks.nclaim.ui.framework.ItemCreator;
import nesoi.aysihuniks.nclaim.ui.framework.MenuType;
import nesoi.aysihuniks.nclaim.ui.framework.TextInput;
import nesoi.aysihuniks.nclaim.ui.shared.BaseMenu;
import nesoi.aysihuniks.nclaim.ui.shared.ConfirmMenu;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import nesoi.aysihuniks.nclaim.utils.Msg;
import nesoi.aysihuniks.nclaim.utils.Numbers;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import nesoi.aysihuniks.nclaim.utils.Sounds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
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

/**
 * Claim block menu (5 rows, 6 when a second row of blocks is needed).
 * <pre>
 *  row 1: . [back / previous page] . . [teleport] . [move] [next page] .
 *  row 3: . [block] x7 .
 *  row 4: . [block] x7 .     (6-row layout only)
 * </pre>
 * In admin mode, free row 3 slots open {@link AddClaimBlockMenu}.
 */
public class ManageClaimBlockMenu extends BaseMenu {

    private static final int BACK = 10, TELEPORT = 13, MOVE = 15, NEXT_PAGE = 16;
    private static final int FIRST_BLOCK_SLOT = 28, PER_ROW = 7, PER_PAGE = 14;

    private final @NotNull Claim claim;
    private final @NotNull Player player;
    private final ClaimBlockManager blockManager;
    private final int page;
    /** Opened from the admin menu: blocks are free, permissions are not checked, and blocks can be added or removed. */
    private final boolean admin;

    public ManageClaimBlockMenu(@NotNull Claim claim, @NotNull Player player, int page) {
        this(claim, player, page, false);
    }

    public ManageClaimBlockMenu(@NotNull Claim claim, @NotNull Player player, int page, boolean admin) {
        super("claim_block_management_menu");
        this.claim = claim;
        this.player = player;
        this.blockManager = NClaim.inst().getClaimBlockManager();
        this.page = Math.max(0, page);
        this.admin = admin;
        setup();
        displayTo(player);
    }

    /* ---------------- layout ---------------- */

    private void setup() {
        boolean showBlocks = can(Permission.MANAGE_CLAIM_BLOCK_TYPES);
        List<ClaimBlockInfo> blocks = showBlocks ? listedBlocks() : List.of();
        // Admins always get at least one free slot to add a block, so they may need an extra page.
        int entries = showBlocks ? blocks.size() + (admin ? 1 : 0) : 0;
        int pages = Math.max(1, (entries + PER_PAGE - 1) / PER_PAGE);
        int start = page * PER_PAGE;
        // One row of blocks fits in 5 rows; a second row (under the first) needs 6.
        int onPage = Math.max(0, Math.min(PER_PAGE, entries - start));
        int rows = onPage > PER_ROW ? 2 : 1;
        createInventory(rows == 2 ? MenuType.CHEST_6_ROWS : MenuType.CHEST_5_ROWS, getString("title"));

        // Slot 10: back on the first page, previous page after it.
        String backPath = page == 0 ? "back" : "previous_page";
        addButton(button(BACK, () -> ItemCreator.of(getMaterialFullPath(backPath))
                .name(NClaim.inst().getGuiLangManager().getString(backPath + ".display_name"))
                .lore(NClaim.inst().getGuiLangManager().getStringList(backPath + ".lore"))
                .get(), (p, click) -> {
            MessageType.MENU_BACK.playSound(p);
            if (page == 0) new ClaimManagementMenu(p, claim, admin);
            else new ManageClaimBlockMenu(claim, p, page - 1, admin);
        }));

        addButton(button(TELEPORT, () -> simpleItem("teleport"), (p, click) -> {
            p.closeInventory();
            NClaim.inst().getClaimService().teleportToClaim(p, claim);
        }));

        addButton(button(MOVE, () -> simpleItem("move_claim_block"), (p, click) -> moveClaimBlock(p)));

        if (!showBlocks) return;

        for (int j = 0; j < rows * PER_ROW; j++) {
            int slot = FIRST_BLOCK_SLOT + (j / PER_ROW) * 9 + j % PER_ROW;
            int index = start + j;
            if (index < blocks.size()) {
                ClaimBlockInfo info = blocks.get(index);
                addButton(button(slot, () -> blockItem(info), (p, click) -> clickBlock(p, info, click)));
            } else if (admin) {
                addButton(button(slot, () -> simpleItem("add_block"), (p, click) -> {
                    MessageType.MENU_FORWARD.playSound(p);
                    new AddClaimBlockMenu(p, claim, page);
                }));
            }
        }

        if (page + 1 < pages) {
            addButton(button(NEXT_PAGE, () -> ItemCreator.of(getMaterialFullPath("next_page"))
                    .name(NClaim.inst().getGuiLangManager().getString("next_page.display_name"))
                    .lore(NClaim.inst().getGuiLangManager().getStringList("next_page.lore"))
                    .get(), (p, click) -> {
                MessageType.MENU_FORWARD.playSound(p);
                new ManageClaimBlockMenu(claim, p, page + 1, admin);
            }));
        }
    }

    /**
     * The default claim block (always free, so players can switch back to it), the enabled blocks
     * of claim_blocks.yml, then blocks of this claim only: ones it owns and ones an admin offered it.
     */
    private List<ClaimBlockInfo> listedBlocks() {
        List<ClaimBlockInfo> result = new ArrayList<>();
        Material defaultBlock = defaultBlock();
        if (blockManager.getBlockInfo(defaultBlock) == null) {
            result.add(new ClaimBlockInfo(defaultBlock, ClaimBlockManager.prettyName(defaultBlock), 0, List.of(), null, true));
        }
        for (ClaimBlockInfo info : blockManager.getAllBlockInfos()) {
            if (info.enabled) result.add(info);
        }
        for (Material material : claim.getPurchasedBlockTypes()) {
            if (material != defaultBlock && blockManager.getBlockInfo(material) == null) result.add(claimOnly(material, 0));
        }
        claim.getBlockOffers().forEach((material, price) -> {
            if (material != defaultBlock && blockManager.getBlockInfo(material) == null
                    && !claim.getPurchasedBlockTypes().contains(material)) {
                result.add(claimOnly(material, price));
            }
        });
        return result;
    }

    private static ClaimBlockInfo claimOnly(Material material, double price) {
        return new ClaimBlockInfo(material, ClaimBlockManager.prettyName(material), price, List.of(), null, true);
    }

    private boolean isClaimOnly(ClaimBlockInfo info) {
        return info.material != defaultBlock() && blockManager.getBlockInfo(info.material) == null;
    }

    private static Material defaultBlock() {
        return NClaim.inst().getNconfig().getDefaultClaimBlockType();
    }

    private boolean isDefault(ClaimBlockInfo info) {
        return info.material == defaultBlock() && blockManager.getBlockInfo(info.material) == null;
    }

    /* ---------------- items ---------------- */

    private ItemStack blockItem(ClaimBlockInfo info) {
        // Texts: guis.yml -> claim_block_management_menu.block_item
        boolean claimOnly = isClaimOnly(info);
        String state;
        if (claim.getClaimBlockType() == info.material) state = "selected";
        else if (admin || isDefault(info)) state = "owned";
        else if (info.permission != null && !info.permission.isEmpty() && !player.hasPermission(info.permission)) state = "no_permission";
        else if (claim.getPurchasedBlockTypes().contains(info.material) || info.price <= 0) state = "owned";
        else state = "locked";

        double playerBalance = NClaim.inst().getEconomyService().balance(player);
        String balanceColor = getString(playerBalance >= info.price
                ? "block_item.enough_balance_color" : "block_item.not_enough_balance_color");
        String cost = Numbers.format(info.price);
        String balance = balanceColor + Numbers.format(playerBalance);
        String needBalance = balanceColor + Numbers.format(Math.min(playerBalance, info.price));

        // A custom lore in claim_blocks.yml is its purchase text, so it replaces only the locked one.
        List<String> lore = "locked".equals(state) && info.lore != null && !info.lore.isEmpty()
                ? new ArrayList<>(info.lore)
                : new ArrayList<>(getStringList("block_item." + state + ".lore"));
        if (claimOnly) lore.addAll(getStringList("block_item.claim_only_lore"));
        if (admin && !isDefault(info)) lore.addAll(getStringList(claimOnly ? "block_item.admin_claim_only_lore" : "block_item.admin_lore"));
        lore.replaceAll(line -> line == null ? "" : line
                .replace("{cost}", cost)
                .replace("{need_balance}", needBalance)
                .replace("{balance}", balance)
                .replace("{block}", info.displayName));

        ItemCreator creator = ItemCreator.of(info.material)
                .name(getString("block_item.display_name").replace("{block}", info.displayName))
                .lore(lore);
        if (claim.getClaimBlockType() == info.material) {
            creator.enchant(Enchantment.MENDING, 1);
            creator.flags(ItemFlag.values());
        }
        return creator.get();
    }

    private ItemStack simpleItem(String key) {
        return ItemCreator.of(getMaterial(key))
                .name(getString(key + ".display_name"))
                .lore(getStringList(key + ".lore"))
                .get();
    }

    /* ---------------- actions ---------------- */

    private void clickBlock(Player p, ClaimBlockInfo info, ClickType click) {
        if (admin && click.isRightClick()) {
            if (isDefault(info)) return; // the default block cannot be priced or removed here
            if (click.isShiftClick()) confirmRemove(p, info);
            else changePrice(p, info);
            return;
        }
        useBlock(p, info);
    }

    private void useBlock(Player p, ClaimBlockInfo info) {
        if (!admin && info.permission != null && !info.permission.isEmpty() && !p.hasPermission(info.permission)) {
            Msg.send(p, lang("command.permission_denied"));
            MessageType.FAIL.playSound(p);
            return;
        }

        // Admins change the block for free and do not buy it for the owner.
        if (!admin && !claim.getPurchasedBlockTypes().contains(info.material)) {
            if (!NClaim.inst().getEconomyService().withdraw(p, info.price)) {
                Msg.send(p, lang("command.balance.not_enough"));
                MessageType.FAIL.playSound(p);
                return;
            }
            claim.getPurchasedBlockTypes().add(info.material);
            claim.getBlockOffers().remove(info.material); // a claim-only offer is now bought
            claim.markDirty();
            if (info.price > 0) {
                Msg.send(p, lang("claim.block.purchased")
                        .replace("{block}", info.displayName)
                        .replace("{price}", Numbers.format(info.price)));
            }
        }

        if (claim.getClaimBlockType() == info.material) return;

        Material oldType = claim.getClaimBlockType();
        ClaimBlockInfo oldInfo = blockManager.getBlockInfo(oldType);
        String oldName = oldInfo != null ? oldInfo.displayName : ClaimBlockManager.prettyName(oldType);

        Sounds.play(p, "BLOCK_BEACON_POWER_SELECT", 0.5f, 0.7f);
        claim.setClaimBlockType(info.material);
        Location location = claim.getClaimBlockLocation();
        Scheduler.ensureAt(location, () -> {
            location.getBlock().setType(info.material);
            location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0.5, 1, 0.5), 20, 0.3, 0.5, 0.3, 0.1);
        });
        claim.markDirty();

        Msg.send(p, lang("claim.block.changed")
                .replace("{old_block}", oldName)
                .replace("{new_block}", info.displayName));
        p.closeInventory();
    }

    private void moveClaimBlock(Player p) {
        if (!can(Permission.MOVE_CLAIM_BLOCK)) {
            Msg.send(p, lang("command.permission_denied"));
            return;
        }
        Location target = p.getLocation().getBlock().getLocation();
        if (!claim.isMainChunk(target.getWorld().getName(), target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            Msg.send(p, lang("claim.move.outside_of_chunk"));
            return;
        }
        new ConfirmMenu(p,
                NClaim.inst().getGuiLangManager().getString("confirm_menu.children.move_claim_block.display_name"),
                NClaim.inst().getGuiLangManager().getStringList("confirm_menu.children.move_claim_block.lore"),
                result -> {
                    if ("confirmed".equals(result)) {
                        p.closeInventory();
                        Msg.send(p, lang(NClaim.inst().getClaimService().moveClaimBlock(claim, target)
                                ? "claim.move.success" : "claim.move.blocked"));
                    } else if ("declined".equals(result)) {
                        new ManageClaimBlockMenu(claim, p, page, admin);
                    }
                });
    }

    /* ---------------- admin editing ---------------- */

    /** Right click: global blocks change in claim_blocks.yml, claim-only blocks change their offer (until bought). */
    private void changePrice(Player p, ClaimBlockInfo info) {
        boolean claimOnly = isClaimOnly(info);
        if (claimOnly && claim.getPurchasedBlockTypes().contains(info.material)) {
            Msg.send(p, lang("claim.block.already_owned").replace("{block}", info.displayName));
            MessageType.FAIL.playSound(p);
            return;
        }
        askPrice(p, price -> {
            if (claimOnly) {
                claim.getBlockOffers().put(info.material, price);
                claim.markDirty();
            } else {
                blockManager.putBlock(info.material, price);
            }
            Msg.send(p, lang("claim.block.price_changed")
                    .replace("{block}", info.displayName)
                    .replace("{price}", Numbers.format(price)));
        });
    }

    private void askPrice(Player p, java.util.function.DoubleConsumer onValid) {
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
            } else {
                onValid.accept(price);
                MessageType.CONFIRM.playSound(p);
            }
            new ManageClaimBlockMenu(claim, p, page, admin);
        });
    }

    /** Shift + right click asks first: removing a block from every claim cannot be undone. */
    private void confirmRemove(Player p, ClaimBlockInfo info) {
        String path = isClaimOnly(info) ? "confirm_menu.children.remove_claim_block_claim" : "confirm_menu.children.remove_claim_block";
        List<String> lore = new ArrayList<>(NClaim.inst().getGuiLangManager().getStringList(path + ".lore"));
        lore.replaceAll(line -> line == null ? "" : line.replace("{block}", info.displayName)
                .replace("{default_block}", ClaimBlockManager.prettyName(defaultBlock())));
        new ConfirmMenu(p,
                NClaim.inst().getGuiLangManager().getString(path + ".display_name").replace("{block}", info.displayName),
                lore,
                result -> {
                    if ("confirmed".equals(result)) removeBlock(p, info);
                    else if ("declined".equals(result)) new ManageClaimBlockMenu(claim, p, page, admin);
                });
    }

    /**
     * Removes the block from claim_blocks.yml, or from this claim if it was only for this claim. A claim
     * using the removed block falls back to the default claim block.
     */
    private void removeBlock(Player p, ClaimBlockInfo info) {
        if (isClaimOnly(info)) {
            claim.getPurchasedBlockTypes().remove(info.material);
            claim.getBlockOffers().remove(info.material);
            claim.markDirty();
        } else {
            blockManager.removeBlock(info.material);
            // Removed for everyone: no claim keeps it, and claims using it fall back to the default block.
            for (Claim other : Claim.getClaims()) {
                if (other == null || other == claim) continue;
                boolean changed = other.getPurchasedBlockTypes().remove(info.material);
                changed |= other.getBlockOffers().remove(info.material) != null;
                if (other.getClaimBlockType() == info.material) {
                    switchToDefault(other);
                } else if (changed) {
                    other.markDirty();
                }
            }
            claim.getPurchasedBlockTypes().remove(info.material);
            claim.markDirty();
        }
        if (claim.getClaimBlockType() == info.material) switchToDefault(claim);
        Msg.send(p, lang("claim.block.removed").replace("{block}", info.displayName));
        MessageType.MENU_DESELECT.playSound(p);
        new ManageClaimBlockMenu(claim, p, page, admin);
    }

    private static void switchToDefault(Claim target) {
        Material fallback = defaultBlock();
        target.setClaimBlockType(fallback);
        target.getPurchasedBlockTypes().add(fallback);
        target.markDirty();
        Location location = target.getClaimBlockLocation();
        Scheduler.ensureAt(location, () -> location.getBlock().setType(fallback));
    }

    /* ---------------- helpers ---------------- */

    private boolean can(Permission permission) {
        return admin || NClaim.inst().getClaimCoopManager().hasPermission(player, claim, permission);
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
            public @Nullable ItemStack getItem() {
                return item.get();
            }

            @Override
            public void onClick(@NotNull Player p, @NotNull ClickType clickType) {
                click.accept(p, clickType);
            }
        };
    }
}
