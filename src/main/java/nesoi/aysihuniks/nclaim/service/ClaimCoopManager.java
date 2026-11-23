package nesoi.aysihuniks.nclaim.service;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.api.events.ClaimCoopAddEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimCoopPermissionCategoryToggleEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimCoopPermissionToggleEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimCoopRemoveEvent;
import nesoi.aysihuniks.nclaim.enums.Permission;
import nesoi.aysihuniks.nclaim.enums.PermissionCategory;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.model.CoopPermission;
import nesoi.aysihuniks.nclaim.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Co-op membership and per-member permissions. */
public class ClaimCoopManager {

    private final NClaim plugin;

    public ClaimCoopManager(@NotNull NClaim plugin) {
        this.plugin = plugin;
    }

    /** Owner and admins with the interact bypass may do everything; co-ops need the permission. */
    public boolean hasPermission(@NotNull Player player, @NotNull Claim claim, @NotNull Permission permission) {
        if (isClaimOwner(claim, player) || player.hasPermission("nclaim.bypass.*")) return true;
        UUID uuid = player.getUniqueId();
        return claim.getCoopPlayers().contains(uuid) && claim.getCoopPermission(uuid).isEnabled(permission);
    }

    public void addCoopPlayer(@NotNull Claim claim, @NotNull Player actor, @NotNull Player coopPlayer, boolean isAdmin) {
        if (!canAddCoop(claim, actor, coopPlayer, isAdmin)) return;

        ClaimCoopAddEvent event = new ClaimCoopAddEvent(actor, coopPlayer, claim);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Msg.send(actor, lang("claim.coop.add_cancelled"));
            return;
        }

        claim.addCoop(coopPlayer.getUniqueId());
        refreshHologram(claim);
        Msg.send(actor, lang("claim.coop.added").replace("{coop}", coopPlayer.getName()));
        Msg.send(coopPlayer, lang("claim.coop.joined").replace("{owner}", actor.getName()));
    }

    public void removeCoopPlayer(@NotNull Claim claim, @NotNull Player actor, @NotNull UUID coopUUID) {
        if (!isClaimOwner(claim, actor) && !actor.hasPermission("nclaim.admin")) {
            Msg.send(actor, lang("command.permission_denied"));
            return;
        }

        ClaimCoopRemoveEvent event = new ClaimCoopRemoveEvent(actor, coopUUID, claim);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            Msg.send(actor, lang("claim.coop.remove_cancelled"));
            return;
        }

        claim.removeCoop(coopUUID);
        refreshHologram(claim);
        Player coopPlayer = Bukkit.getPlayer(coopUUID);
        String coopName = coopPlayer != null ? coopPlayer.getName() : String.valueOf(Bukkit.getOfflinePlayer(coopUUID).getName());
        if (coopPlayer != null) Msg.send(coopPlayer, lang("claim.coop.kicked").replace("{owner}", actor.getName()));
        Msg.send(actor, lang("claim.coop.removed").replace("{coop}", coopName));
    }

    public void toggleCoopPermission(@NotNull Claim claim, @NotNull UUID player, @NotNull Permission permission) {
        CoopPermission permissions = claim.getCoopPermission(player);
        boolean newState = !permissions.isEnabled(permission);
        ClaimCoopPermissionToggleEvent event = new ClaimCoopPermissionToggleEvent(player, claim, permission, newState);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            claim.getOwnerPlayer().ifPresent(owner -> Msg.send(owner, lang("claim.coop.permission_toggle_cancelled")));
            return;
        }
        permissions.setEnabled(permission, newState);
        claim.markDirty();
    }

    public void toggleCoopPermissionCategory(@NotNull Claim claim, @NotNull UUID player, @NotNull PermissionCategory category) {
        CoopPermission permissions = claim.getCoopPermission(player);
        boolean newState = !permissions.hasAllPermissionsInCategory(category);
        ClaimCoopPermissionCategoryToggleEvent event = new ClaimCoopPermissionCategoryToggleEvent(player, claim, category, newState);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            claim.getOwnerPlayer().ifPresent(owner -> Msg.send(owner, lang("claim.coop.permission_category_toggle_cancelled")));
            return;
        }
        permissions.setAllPermissionsInCategory(category, newState);
        claim.markDirty();
    }

    private boolean canAddCoop(Claim claim, Player actor, Player coopPlayer, boolean isAdmin) {
        UUID coopUUID = coopPlayer.getUniqueId();
        if (claim.isOwner(coopUUID)) {
            Msg.send(actor, lang("command.player.cant_add_self"));
            return false;
        }
        if (claim.getCoopPlayers().contains(coopUUID)) {
            Msg.send(actor, lang("claim.coop.already_added").replace("{coop}", coopPlayer.getName()));
            return false;
        }
        if (isAdmin) return true;
        if (!hasPermission(actor, claim, Permission.ADD_COOP)) {
            Msg.send(actor, lang("command.permission_denied"));
            return false;
        }
        // The limit belongs to the owner's rank; use the actor's only while the owner is offline.
        Player limitHolder = claim.getOwnerPlayer().orElse(actor);
        if (claim.getCoopPlayers().size() >= plugin.getNconfig().getMaxCoopPlayers(limitHolder)) {
            Msg.send(actor, lang("claim.coop.limit_reached"));
            return false;
        }
        return true;
    }

    public boolean isClaimOwner(@NotNull Claim claim, @NotNull Player player) {
        return claim.isOwner(player.getUniqueId());
    }

    public boolean getCoopPermissionState(@NotNull Claim claim, @NotNull UUID player, @NotNull Permission permission) {
        return claim.getCoopPlayers().contains(player) && claim.getCoopPermission(player).isEnabled(permission);
    }

    private void refreshHologram(Claim claim) {
        if (plugin.getHologramManager() != null) plugin.getHologramManager().refresh(claim);
    }

    private String lang(String path) {
        return plugin.getLangManager().getString(path);
    }
}
