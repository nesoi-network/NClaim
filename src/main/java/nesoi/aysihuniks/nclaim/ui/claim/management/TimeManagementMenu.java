package nesoi.aysihuniks.nclaim.ui.claim.management;

import nesoi.aysihuniks.nclaim.utils.Numbers;
import com.google.common.collect.Sets;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Balance;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.User;
import nesoi.aysihuniks.nclaim.ui.shared.BaseMenu;
import nesoi.aysihuniks.nclaim.utils.MessageType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import nesoi.aysihuniks.nclaim.ui.framework.ItemCreator;
import nesoi.aysihuniks.nclaim.ui.framework.Button;
import nesoi.aysihuniks.nclaim.ui.framework.MenuType;
import nesoi.aysihuniks.nclaim.utils.Msg;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TimeManagementMenu extends BaseMenu {
    private int days;
    private int hours;
    private int minutes;
    private int timeUnit;
    private final @NotNull Claim claim;
    private final boolean admin;

    public TimeManagementMenu(@NotNull Player player, int days, int hours, int minutes, int timeUnit, @NotNull Claim claim, boolean admin) {
        super("claim_time_management_menu");
        this.claim = claim;
        this.days = days;
        this.hours = hours;
        this.minutes = minutes;
        this.timeUnit = timeUnit;
        this.admin = admin;

        setupMenu();
        displayTo(player);
    }

    private void setupMenu() {
        createInventory(MenuType.CHEST_5_ROWS, getString("title"));

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(13);
            }

            @Override
            public ItemStack getItem() {
                List<String> lore = new ArrayList<>(getStringList("claim_info.lore"));
                lore.replaceAll(s -> s.replace("{time_left}", NClaim.inst().getClaimExpirationManager().getFormattedTimeLeft(claim))
                        .replace("{expires_at}", NClaim.serializeDate(claim.getExpiredAt())));
                return ItemCreator.of(getMaterial("claim_info"))
                        .name(getString("claim_info.display_name"))
                        .lore(lore)
                        .get();
            }
        });

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(31);
            }

            @Override
            public ItemStack getItem() {
                double totalPrice = calculateTotalPrice();
                double tax = totalPrice * NClaim.inst().getNconfig().getTimeExtensionTaxRate();
                double finalPrice = totalPrice + tax;

                List<String> lore = new ArrayList<>(getStringList("confirm.lore"));
                String priceStr = admin
                        ? NClaim.inst().getGuiLangManager().getString("free")
                        : "$" + Numbers.format(finalPrice);
                String taxStr = admin ? "0" : "$" + Numbers.format(tax);

                lore.replaceAll(s -> s.replace("{price}", priceStr)
                        .replace("{tax}", taxStr)
                        .replace("{d}", String.valueOf(days))
                        .replace("{h}", String.valueOf(hours))
                        .replace("{m}", String.valueOf(minutes)));

                return ItemCreator.of(getMaterial("confirm"))
                        .name(getString("confirm.display_name"))
                        .lore(lore)
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                if (days == 0 && hours == 0 && minutes == 0) {
                    Msg.send(player, NClaim.inst().getLangManager().getString("claim.time.no_time_selected"));
                    MessageType.FAIL.playSound(player);
                    return;
                }

                if (!admin) {
                    double totalPrice = calculateTotalPrice();
                    double tax = totalPrice * NClaim.inst().getNconfig().getTimeExtensionTaxRate();
                    double finalPrice = totalPrice + tax;

                    if (!NClaim.inst().getEconomyService().withdraw(player, finalPrice)) {
                        Msg.send(player, NClaim.inst().getLangManager().getString("command.balance.not_enough"));
                        MessageType.FAIL.playSound(player);
                        return;
                    }
                }

                NClaim.inst().getClaimExpirationManager().extendClaimExpiration(claim, days, hours, minutes);

                String priceStr = admin
                        ? NClaim.inst().getGuiLangManager().getString("free")
                        : "$" + Numbers.format(calculateTotalPrice() + calculateTotalPrice() * NClaim.inst().getNconfig().getTimeExtensionTaxRate());

                Msg.send(player, NClaim.inst().getLangManager().getString("command.expiration_extended")
                        .replace("{d}", String.valueOf(days))
                        .replace("{h}", String.valueOf(hours))
                        .replace("{m}", String.valueOf(minutes))
                        .replace("{price}", priceStr));

                MessageType.CONFIRM.playSound(player);
                player.closeInventory();
            }
        });

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(10);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterialFullPath("back"))
                        .name(NClaim.inst().getGuiLangManager().getString("back.display_name"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                MessageType.MENU_BACK.playSound(player);
                new ClaimManagementMenu(player, claim, admin);
            }
        });

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(16);
            }

            @Override
            public ItemStack getItem() {
                List<String> lore = new ArrayList<>(getStringList("select_time_unit.lore"));
                lore.replaceAll(s -> s.replace("{days_status}", timeUnit == 0 ? "&e" + getString("days_status") : "&7" + getString("days_status"))
                        .replace("{hours_status}", timeUnit == 1 ? "&e" + getString("hours_status") : "&7" + getString("hours_status"))
                        .replace("{minutes_status}", timeUnit == 2 ? "&e" + getString("minutes_status") : "&7" + getString("minutes_status")));
                return ItemCreator.of(java.util.Optional.ofNullable(Material.matchMaterial("IRON_CHAIN")).orElse(Material.matchMaterial("CHAIN")))
                        .name(getString("select_time_unit.display_name"))
                        .lore(lore)
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                timeUnit = (timeUnit + 1) % 3;
                MessageType.MENU_REFRESH.playSound(player);
                new TimeManagementMenu(player, days, hours, minutes, timeUnit, claim, admin);
            }
        });

        addTimeButtons();
    }

    private double calculateTotalPrice() {
        return (days * NClaim.inst().getNconfig().getTimeExtensionPricePerDay()) +
               (hours * NClaim.inst().getNconfig().getTimeExtensionPricePerHour()) +
               (minutes * NClaim.inst().getNconfig().getTimeExtensionPricePerMinute());
    }

    private void addTimeButtons() {
        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(28);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterial("add_one"))
                        .name(getString("add_one.display_name")
                                .replace("{unit}", getTimeUnitString()))
                        .lore(getStringList("add_one.lore"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                adjustTime(1, admin);
                MessageType.VALUE_INCREASE.playSound(player);
                new TimeManagementMenu(player, days, hours, minutes, timeUnit, claim, admin);
            }
        });

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(29);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterial("add_six"))
                        .name(getString("add_six.display_name")
                                .replace("{unit}", getTimeUnitString()))
                        .lore(getStringList("add_six.lore"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                adjustTime(6, admin);
                MessageType.VALUE_INCREASE.playSound(player);
                new TimeManagementMenu(player, days, hours, minutes, timeUnit, claim, admin);
            }
        });

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(33);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterial("subtract_one"))
                        .name(getString("subtract_one.display_name")
                                .replace("{unit}", getTimeUnitString()))
                        .lore(getStringList("subtract_one.lore"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                adjustTime(-1, admin);
                MessageType.VALUE_DECREASE.playSound(player);
                new TimeManagementMenu(player, days, hours, minutes, timeUnit, claim, admin);
            }
        });

        addButton(new Button() {
            @Override
            public @NotNull Set<Integer> getSlots() {
                return Sets.newHashSet(34);
            }

            @Override
            public ItemStack getItem() {
                return ItemCreator.of(getMaterial("subtract_six"))
                        .name(getString("subtract_six.display_name")
                                .replace("{unit}", getTimeUnitString()))
                        .lore(getStringList("subtract_six.lore"))
                        .get();
            }

            @Override
            public void onClick(@NotNull Player player, @NotNull ClickType clickType) {
                adjustTime(-6, admin);
                MessageType.VALUE_DECREASE.playSound(player);
                new TimeManagementMenu(player, days, hours, minutes, timeUnit, claim, admin);
            }
        });
    }

    private String getTimeUnitString() {
        switch (timeUnit) {
            case 1:
                return getString("hours_status");
            case 2:
                return getString("minutes_status");
            default:
                return getString("days_status");
        }
    }

    private void adjustTime(int amount, boolean admin) {
        switch (timeUnit) {
            case 0:
                days = admin ? days + amount : Math.max(0, days + amount);
                break;
            case 1:
                hours = admin ? hours + amount : Math.max(0, hours + amount);
                break;
            case 2:
                minutes = admin ? minutes + amount : Math.max(0, minutes + amount);
                break;
        }
    }
}
