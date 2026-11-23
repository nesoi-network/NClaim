package nesoi.aysihuniks.nclaim.integrations.farmer;

import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.utils.Msg;
import nesoi.aysihuniks.nclaim.utils.Scheduler;
import org.bukkit.Location;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.integrations.Integrations;

import java.util.UUID;

/**
 * Farmer (Geik) region hook: a farmer belongs to a claim, identified by its claim id.
 * Farmer has no registration API, so NClaim sets itself as Farmer's integration.
 */
public class FarmerIntegration extends Integrations {

    private FarmerIntegration() {
        super(new FarmerListener());
    }

    /** Farmer picks its own integration one tick after it enables; ours is set after that. */
    public static void register() {
        Scheduler.globalLater(() -> {
            try {
                Integrations current = Main.getIntegration();
                if (current != null) {
                    Msg.log("&eFarmer already uses " + current.getClass().getSimpleName() + ", NClaim integration skipped.");
                    return;
                }
                Main.setIntegration(new FarmerIntegration());
                Msg.log("&aFarmer integration enabled.");
            } catch (LinkageError e) {
                Msg.log("&cThis Farmer version is not supported (tested with v6-b114): " + e.getMessage());
            }
        }, 20L);
    }

    @Override
    public UUID getOwnerUUID(String regionID) {
        Claim claim = Claim.claims.getById(regionID);
        return claim == null ? null : claim.getOwner();
    }

    @Override
    public UUID getOwnerUUID(Location location) {
        Claim claim = Claim.claims.getAt(location);
        return claim == null ? null : claim.getOwner();
    }

    @Override
    public String getRegionID(Location location) {
        Claim claim = Claim.claims.getAt(location);
        return claim == null ? null : claim.getClaimId();
    }
}
