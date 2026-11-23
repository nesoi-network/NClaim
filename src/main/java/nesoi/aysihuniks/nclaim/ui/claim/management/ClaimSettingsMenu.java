package nesoi.aysihuniks.nclaim.ui.claim.management;

import com.google.common.collect.Sets;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.model.SettingData;
import nesoi.aysihuniks.nclaim.ui.shared.BaseMenu;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import nesoi.aysihuniks.nclaim.ui.framework.Button;
import nesoi.aysihuniks.nclaim.ui.framework.ItemCreator;
import nesoi.aysihuniks.nclaim.ui.framework.MenuType;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ClaimSettingsMenu extends BaseMenu {
    private final Claim claim;
    private final int page;
    private final boolean admin;

    private static final int[] settingSlots = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final List<SettingData> settings = Arrays.asList(
            new SettingData(Setting.CLAIM_PVP, "pvp", getMaterial("settings.pvp")),
            new SettingData(Setting.TNT_DAMAGE, "tnt_explosions", getMaterial("settings.tnt_explosions")),
            new SettingData(Setting.CREEPER_DAMAGE, "creeper_explosions", getMaterial("settings.creeper_explosions")),
            new SettingData(Setting.MOB_ATTACKING, "mob_attacks", getMaterial("settings.mob_attacks")),
            new SettingData(Setting.MONSTER_SPAWNING, "monster_spawning", getMaterial("settings.monster_spawning")),
            new SettingData(Setting.ANIMAL_SPAWNING, "animal_spawning", getMaterial("settings.animal_spawning")),
            new SettingData(Setting.VILLAGER_INTERACTION, "villager_interactions", getMaterial("settings.villager_interactions")),
            new SettingData(Setting.FIRE_SPREAD, "fire_spread", getMaterial("settings.fire_spread")),
            new SettingData(Setting.MOB_GRIEFING, "mob_griefing", getMaterial("settings.mob_griefing")),
            new SettingData(Setting.CROP_TRAMPLING, "crop_trampling", getMaterial("settings.crop_trampling")),
            new SettingData(Setting.OTHER_EXPLOSIONS, "other_explosions", getMaterial("settings.other_explosions"))
    );

    public ClaimSettingsMenu(Player player, Claim claim, int page, boolean admin) {
        super("claim_settings_menu");
        this.claim = claim;
        this.page = page;
        this.admin = admin;

        setupMenu();
        displayTo(player);
    }

    private void setupMenu() {
        // One row of settings (up to 7) fits in 5 rows; a second row under it needs 6.
        int onPage = Math.max(0, Math.min(settingSlots.length, settings.size() - page * settingSlots.length));
        createInventory(onPage > 7 ? MenuType.CHEST_6_ROWS : MenuType.CHEST_5_ROWS, getString("title"));
        addNavigationButton();
        addInfoButton();
        addResetButton();
        addSettingButtons();
        
        if (hasNextPage()) {
            addNextPageButton();
        }
    }

    private void addNavigationButton() {
        addButton(new Button() {
            final String buttonPath = page == 0 ? "back" : "previous_page";

            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(10);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterialFullPath(buttonPath))
                        .name(NClaim.inst().getGuiLangManager().getString(buttonPath + ".display_name"))
                        .lore(NClaim.inst().getGuiLangManager().getStringList(buttonPath + ".lore"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                MessageType.MENU_BACK.playSound(player);
                if (page == 0) {
                    new ClaimManagementMenu(player, claim, admin);
                } else {
                    new ClaimSettingsMenu(player, claim, page - 1, admin);
                }
            }
        });
    }

    /** Explains what claim settings are, next to the back button. */
    private void addInfoButton() {
        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(12);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterial("info"))
                        .name(getString("info.display_name"))
                        .lore(getStringList("info.lore"))
                        .flags(ItemFlag.values())
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
            }
        });
    }

    /** Owner or admin: puts every setting back to its default_value (asks first). */
    private void addResetButton() {
        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(14);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterial("reset"))
                        .name(getString("reset.display_name"))
                        .lore(getStringList("reset.lore"))
                        .flags(ItemFlag.values())
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                if (!admin && !claim.isOwner(player.getUniqueId())) {
                    nesoi.aysihuniks.nclaim.utils.Msg.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
                    MessageType.FAIL.playSound(player);
                    return;
                }
                new nesoi.aysihuniks.nclaim.ui.shared.ConfirmMenu(player,
                        NClaim.inst().getGuiLangManager().getString("confirm_menu.children.reset_settings.display_name"),
                        NClaim.inst().getGuiLangManager().getStringList("confirm_menu.children.reset_settings.lore"),
                        result -> {
                            if ("confirmed".equals(result)) {
                                nesoi.aysihuniks.nclaim.service.ClaimSettingsManager.resetToDefaults(claim);
                                MessageType.CONFIRM.playSound(player);
                                new ClaimSettingsMenu(player, claim, 0, admin);
                            } else if ("declined".equals(result)) {
                                new ClaimSettingsMenu(player, claim, page, admin);
                            }
                        });
            }
        });
    }

    private void addNextPageButton() {
        addButton(new Button() {

            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(16);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterialFullPath("next_page"))
                        .name(NClaim.inst().getGuiLangManager().getString("next_page.display_name"))
                        .lore(NClaim.inst().getGuiLangManager().getStringList("next_page.lore"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                MessageType.MENU_FORWARD.playSound(player);
                new ClaimSettingsMenu(player, claim, page + 1, admin);
            }
        });
    }

    private void addSettingButtons() {
        int startIndex = page * settingSlots.length;
        int endIndex = Math.min(startIndex + settingSlots.length, settings.size());

        for (int i = startIndex, slotIndex = 0; i < endIndex; i++, slotIndex++) {
            SettingData settingData = settings.get(i);
            addSettingButton(settingData, slotIndex);
        }
    }

    private void addSettingButton(SettingData settingData, int slotIndex) {
        addButton(new Button() {
            final String buttonPath = "settings." + settingData.getConfigKey();

            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(settingSlots[slotIndex]);
            }

            @Override
            public ItemStack getItem() {
                boolean isEnabled = claim.getSettings().isEnabled(settingData.getSetting());
                String status = guiLangManager.getString((isEnabled ? "enabled" : "disabled") + ".display_name");
                List<String> lore = getStringList(buttonPath + ".lore");
                lore.replaceAll(l -> l.replace("{status}", status));

                return ItemCreator.of(settingData.getMaterial())
                        .name(getString(buttonPath + ".display_name"))
                        .lore(lore)
                        .flags(ItemFlag.values())
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                MessageType.CONFIRM.playSound(player);
                NClaim.inst().getClaimSettingsManager().toggleSetting(claim, player, settingData.getSetting());
                new ClaimSettingsMenu(player, claim, page, admin);
            }
        });
    }

    private boolean hasNextPage() {
        return (page + 1) * settingSlots.length < settings.size();
    }
}
