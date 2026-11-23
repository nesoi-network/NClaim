package nesoi.aysihuniks.nclaim.integrations.farmer;

import nesoi.aysihuniks.nclaim.api.events.ClaimCoopAddEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimCoopRemoveEvent;
import nesoi.aysihuniks.nclaim.api.events.ClaimRemoveEvent;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import xyz.geik.farmer.api.FarmerAPI;
import xyz.geik.farmer.api.handlers.FarmerBoughtEvent;
import xyz.geik.farmer.api.managers.FarmerManager;
import xyz.geik.farmer.model.Farmer;
import xyz.geik.farmer.model.user.FarmerPerm;

import java.util.UUID;

/** Keeps a claim's farmer users in sync with the claim's co-op members. */
class FarmerListener implements Listener {

    @EventHandler
    public void onFarmerBought(FarmerBoughtEvent event) {
        Claim claim = Claim.claims.getById(event.getFarmer().getRegionID());
        if (claim == null) return;
        for (UUID member : claim.getCoopPlayers()) {
            event.getFarmer().addUser(member, Bukkit.getOfflinePlayer(member).getName(), FarmerPerm.COOP);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCoopAdd(ClaimCoopAddEvent event) {
        Farmer farmer = FarmerManager.getFarmers().get(event.getClaim().getClaimId());
        if (farmer == null) return;
        farmer.addUser(event.getCoopPlayer().getUniqueId(), event.getCoopPlayer().getName(), FarmerPerm.COOP);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCoopRemove(ClaimCoopRemoveEvent event) {
        Farmer farmer = FarmerManager.getFarmers().get(event.getClaim().getClaimId());
        if (farmer == null) return;
        farmer.getUsersWithoutOwner().stream()
                .filter(user -> user.getUuid().equals(event.getCoopPlayerUUID()))
                .findFirst()
                .ifPresent(farmer::removeUser);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimRemove(ClaimRemoveEvent event) {
        Claim claim = event.getClaim();
        if (FarmerManager.getFarmers().containsKey(claim.getClaimId())) {
            FarmerAPI.getFarmerManager().removeFarmer(claim.getClaimId(), claim.getOwner());
        }
    }
}
