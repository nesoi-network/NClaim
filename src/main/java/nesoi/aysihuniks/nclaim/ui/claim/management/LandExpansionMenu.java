package nesoi.aysihuniks.nclaim.ui.claim.management;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.ChunkKey;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.service.ClaimService;
import nesoi.aysihuniks.nclaim.service.EconomyService;
import nesoi.aysihuniks.nclaim.ui.framework.Button;
import nesoi.aysihuniks.nclaim.ui.framework.ItemCreator;
import nesoi.aysihuniks.nclaim.ui.framework.MenuType;
import nesoi.aysihuniks.nclaim.ui.shared.BaseMenu;
import nesoi.aysihuniks.nclaim.ui.shared.ConfirmMenu;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chunk map around a claim; uses coordinates only, so it never loads chunks. Clicks go by cell
 * state, not item material, so guis.yml can use any material (issue #14).
 */
public class LandExpansionMenu extends BaseMenu {

    /** State of a grid cell; the name is also the guis.yml key of its item. */
    private enum CellState {
        EXPAND("expand"),
        CLAIMED("claimed"),
        CLAIMED_ANOTHER_PLAYER("claimed_another_player"),
        NOT_ADJACENT("not_adjacent");

        final String path;

        CellState(String path) {
            this.path = path;
        }
    }

    private enum Direction {
        LEFT(46, +1), RIGHT(52, -1), UP(50, +9), DOWN(48, -9);

        final int slot;
        final int slotAddition;

        Direction(int slot, int slotAddition) {
            this.slot = slot;
            this.slotAddition = slotAddition;
        }
    }

    private final @NotNull Claim claim;
    private final boolean admin;
    private final int baseSlot;

    private LandExpansionMenu(@NotNull Player player, @NotNull Claim claim, boolean admin, int baseSlot) {
        super("claim_expand_menu");
        this.claim = claim;
        this.admin = admin;
        this.baseSlot = baseSlot;
        setupMenu(player);
    }

    public LandExpansionMenu(@NotNull Player player, @NotNull Claim claim, boolean admin) {
        this(player, claim, admin, 22);
    }

    private void setupMenu(Player player) {
        createInventory(MenuType.CHEST_6_ROWS, getString("title"));

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Set.of(baseSlot);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(claim.getClaimBlockType())
                        .name(getString("center.display_name"))
                        .lore(getStringList("center.lore"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                MessageType.MENU_BACK.playSound(player);
                new ClaimManagementMenu(player, claim, admin);
            }
        });

        for (Direction direction : Direction.values()) {
            addButton(new Button() {
                @Override
                protected @NotNull Set<Integer> getSlots() {
                    return Set.of(direction.slot);
                }

                @Override
                public ItemStack getItem() {
                    String localized = NClaim.inst().getGuiLangManager().getString("scroll_button.directions." + direction.name());
                    return ItemCreator.of(Material.OAK_BUTTON)
                            .name(getString("scroll_button.display_name").replace("{direction}", localized == null ? direction.name() : localized))
                            .get();
                }

                @Override
                public void onClick(@NotNull Player p, @NotNull ClickType clickType) {
                    if (!canScroll(direction)) return;
                    new LandExpansionMenu(p, claim, admin, baseSlot + direction.slotAddition);
                }
            });
        }

        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        for (int row = 0; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                int slot = row * 9 + col;
                if (slot != baseSlot) addChunkButton(slot, chunkFromSlot(slot, yaw));
            }
        }
        displayTo(player);
    }

    private CellState stateOf(ChunkKey key) {
        Claim there = Claim.claims.getAt(key.world(), key.x(), key.z());
        if (there != null) return there.equals(claim) ? CellState.CLAIMED : CellState.CLAIMED_ANOTHER_PLAYER;
        // Same rule as ClaimService#buyLand, so the menu never offers a chunk that cannot be bought.
        if (!admin && !ClaimService.isAdjacent(claim, key)) return CellState.NOT_ADJACENT;
        return CellState.EXPAND;
    }

    private void addChunkButton(int slot, ChunkKey key) {
        CellState state = stateOf(key);
        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Set.of(slot);
            }

            @Override
            public ItemStack getItem() {
                List<String> lore = new ArrayList<>(getStringList(state.path + ".lore"));
                if (state == CellState.EXPAND) {
                    String price = priceText();
                    lore.replaceAll(s -> s.replace("{price}", price));
                }
                return ItemCreator.of(getMaterial(state.path))
                        .name(getString(state.path + ".display_name"))
                        .lore(lore)
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                switch (state) {
                    case EXPAND -> {
                        if (clickType.isLeftClick()) confirmBuy(player, key);
                        else if (clickType.isRightClick()) {
                            player.closeInventory();
                            NClaim.inst().getClaimVisualizerService().showClaimBorders(player, key);
                        }
                    }
                    case CLAIMED -> {
                        if (clickType.isRightClick() && !claim.isMainChunk(key.world(), key.x(), key.z())) confirmUnclaim(player, key);
                    }
                    case CLAIMED_ANOTHER_PLAYER, NOT_ADJACENT -> MessageType.FAIL.playSound(player);
                }
            }
        });
    }

    private String priceText() {
        if (admin) return NClaim.inst().getGuiLangManager().getString("free");
        double price = NClaim.inst().getClaimService().calculateLandPrice(claim);
        return price < 0 ? "N/A" : EconomyService.format(price);
    }

    private void confirmBuy(Player player, ChunkKey key) {
        String price = priceText();
        new ConfirmMenu(player,
                NClaim.inst().getGuiLangManager().getString("confirm_menu.children.claim_expand.display_name"),
                NClaim.inst().getGuiLangManager().getStringList("confirm_menu.children.claim_expand.lore")
                        .stream().map(s -> s.replace("{price}", price)).toList(),
                result -> {
                    if ("confirmed".equals(result)) NClaim.inst().getClaimService().buyLand(claim, player, key, admin);
                    new LandExpansionMenu(player, claim, admin, baseSlot);
                });
    }

    private void confirmUnclaim(Player player, ChunkKey key) {
        new ConfirmMenu(player,
                NClaim.inst().getGuiLangManager().getString("confirm_menu.children.land_unclaim.display_name"),
                NClaim.inst().getGuiLangManager().getStringList("confirm_menu.children.land_unclaim.lore"),
                result -> {
                    if ("confirmed".equals(result)) NClaim.inst().getClaimService().unClaimLand(claim, player, key);
                    new LandExpansionMenu(player, claim, admin, baseSlot);
                });
    }

    /** Grid cell -> chunk, rotated so "up" in the menu is the direction the player faces. */
    private ChunkKey chunkFromSlot(int slot, float yaw) {
        int deltaX = slot % 9 - baseSlot % 9;
        int deltaZ = slot / 9 - baseSlot / 9;
        int dx, dz;
        if (yaw >= 315 || yaw < 45) {        // facing south
            dx = -deltaX;
            dz = -deltaZ;
        } else if (yaw < 135) {              // facing west
            dx = deltaZ;
            dz = -deltaX;
        } else if (yaw < 225) {              // facing north
            dx = deltaX;
            dz = deltaZ;
        } else {                             // facing east
            dx = -deltaZ;
            dz = deltaX;
        }
        return new ChunkKey(claim.getWorldName(), claim.getChunkX() + dx, claim.getChunkZ() + dz);
    }

    private boolean canScroll(Direction direction) {
        return switch (direction) {
            case LEFT -> baseSlot % 9 < 7;
            case RIGHT -> baseSlot % 9 > 1;
            case UP -> baseSlot / 9 < 4;
            case DOWN -> baseSlot / 9 > 0;
        };
    }
}
