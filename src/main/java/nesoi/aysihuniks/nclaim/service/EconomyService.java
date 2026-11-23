package nesoi.aysihuniks.nclaim.service;

import nesoi.aysihuniks.nclaim.utils.Numbers;
import nesoi.aysihuniks.nclaim.enums.Balance;
import nesoi.aysihuniks.nclaim.model.User;
import nesoi.aysihuniks.nclaim.utils.Msg;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * One entry point for money: Vault when an economy provider exists, otherwise the plugin's own
 * per-player balance. Callers never need to branch on the balance system.
 */
public final class EconomyService {

    private final Balance system;
    private final @Nullable Economy vault;

    public EconomyService() {
        Economy found = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null) found = rsp.getProvider();
            } catch (Throwable t) {
                Msg.log("&cFailed to hook into Vault: " + t.getMessage());
            }
        }
        this.vault = found;
        this.system = found != null ? Balance.VAULT : Balance.PLAYERDATA;
        Msg.log(found != null
                ? "&aUsing Vault economy (" + found.getName() + ")."
                : "&eNo Vault economy found, using NClaim's internal balance.");
    }

    public @NotNull Balance getSystem() {
        return system;
    }

    public @Nullable Economy getVault() {
        return vault;
    }

    public double balance(@NotNull OfflinePlayer player) {
        if (vault != null) return vault.getBalance(player);
        return User.getOrLoad(player.getUniqueId()).getBalance();
    }

    public boolean has(@NotNull OfflinePlayer player, double amount) {
        return amount <= 0 || balance(player) >= amount;
    }

    /** Takes money; returns {@code false} (and takes nothing) if the player cannot afford it. */
    public boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        if (vault != null) {
            if (!vault.has(player, amount)) return false;
            return vault.withdrawPlayer(player, amount).transactionSuccess();
        }
        User user = User.getOrLoad(player.getUniqueId());
        boolean ok = user.withdraw(amount);
        if (ok) persistIfOffline(user, player);
        return ok;
    }

    public void deposit(@NotNull OfflinePlayer player, double amount) {
        if (amount <= 0) return;
        if (vault != null) {
            vault.depositPlayer(player, amount);
            return;
        }
        User user = User.getOrLoad(player.getUniqueId());
        user.addBalance(amount);
        persistIfOffline(user, player);
    }

    public void set(@NotNull OfflinePlayer player, double amount) {
        double target = Math.max(0, amount);
        if (vault != null) {
            double current = vault.getBalance(player);
            if (current > target) vault.withdrawPlayer(player, current - target);
            else if (target > current) vault.depositPlayer(player, target - current);
            return;
        }
        User user = User.getOrLoad(player.getUniqueId());
        user.setBalance(target);
        persistIfOffline(user, player);
    }

    /** Offline users are not part of the periodic save, so write them right away. */
    private static void persistIfOffline(User user, OfflinePlayer player) {
        if (!player.isOnline()) User.saveUser(user.getUuid());
    }

    /** Formats an amount for players; see {@link nesoi.aysihuniks.nclaim.utils.Numbers}. */
    public static @NotNull String format(double amount) {
        return nesoi.aysihuniks.nclaim.utils.Numbers.format(amount);
    }
}
