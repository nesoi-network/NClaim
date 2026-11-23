package nesoi.aysihuniks.nclaim.integrations;

import me.chancesd.pvpmanager.PvPManager;
import me.chancesd.pvpmanager.integration.BaseDependency;
import me.chancesd.pvpmanager.integration.Hook;
import me.chancesd.pvpmanager.integration.type.RegionDependency;
import me.chancesd.pvpmanager.manager.DependencyManager;
import me.chancesd.pvpmanager.manager.PlayerManager;
import me.chancesd.pvpmanager.player.CombatPlayer;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.enums.Setting;
import nesoi.aysihuniks.nclaim.model.Claim;
import nesoi.aysihuniks.nclaim.utils.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Tells PvPManager which claims are safe zones (PvP setting off), the same way its own
 * WorldGuard, Towny and HuskClaims hooks do. PvPManager's anti border hopping (push back,
 * barriers) then keeps combat tagged players out of those claims, using its own config.
 * PvPManager decides at enable whether to start its push back, and only if a region hook exists by
 * then, so NClaim registers from its own onLoad, which runs before any plugin enables.
 * PvPManager may call {@link #canAttackAt} off the main thread; the claim lookups used are thread safe.
 */
public final class PvPManagerIntegration extends BaseDependency implements RegionDependency {

    private static PvPManagerIntegration registered;

    private PvPManagerIntegration(Hook slot) {
        super(slot);
    }

    /** Call from onLoad. */
    public static void register() {
        try {
            DependencyManager dependencies = PvPManager.getInstance().getDependencyManager();
            Hook slot = freeSlot(dependencies);
            if (slot == null) {
                Msg.log("&cPvPManager integration skipped: no free hook slot.");
                return;
            }
            registered = new PvPManagerIntegration(slot);
            dependencies.registerDependency(registered);
            Msg.log("&aPvPManager integration enabled.");
        } catch (LinkageError | RuntimeException e) {
            Msg.log("&cThis PvPManager version is not supported (tested with 4.0 - 4.0.30): " + e.getMessage());
        }
    }

    public static void unregister() {
        if (registered == null) return;
        try {
            PvPManager.getInstance().getDependencyManager().unregisterDependency(registered);
        } catch (LinkageError | RuntimeException ignored) {
        }
        registered = null;
    }

    /**
     * PvPManager keys dependencies by its own Hook enum and has no entry for outside plugins, so
     * NClaim takes one whose plugin is not installed. WorldGuard and TAB are looked up by key
     * elsewhere in PvPManager and are never used.
     */
    @Nullable
    private static Hook freeSlot(DependencyManager dependencies) {
        Hook[] hooks = Hook.values();
        for (int i = hooks.length - 1; i >= 0; i--) {
            Hook hook = hooks[i];
            if (hook == Hook.WORLDGUARD || hook == Hook.TAB) continue;
            // At onLoad no plugin is enabled yet, so check which ones are installed.
            if (hook.getPlugin() == null && !dependencies.isDependencyEnabled(hook)) return hook;
        }
        return null;
    }

    /**
     * True while turning PvP off in this claim would let a fight escape: the player changing it is
     * combat tagged, or a tagged player stands in the claim. Only valid when PvPManager is installed.
     */
    public static boolean fightInProgress(Claim claim, Player changer) {
        if (registered == null) return false;
        try {
            PlayerManager players = PvPManager.getInstance().getPlayerManager();
            CombatPlayer self = players.get(changer);
            if (self != null && self.isInCombat()) return true;
            for (CombatPlayer tagged : players.getPlayersInCombat()) {
                Player player = tagged.getPlayer();
                if (player != null && claim.equals(Claim.claims.getAt(player.getLocation()))) return true;
            }
        } catch (LinkageError | RuntimeException ignored) {
        }
        return false;
    }

    @Override
    public boolean canAttackAt(@Nullable Player player, Location location) {
        Claim claim = Claim.claims.getAt(location);
        if (claim == null || NClaim.inst() == null) return true;
        if (player != null && (player.hasPermission("nclaim.bypass.*") || player.hasPermission("nclaim.bypass.pvp"))) return true;
        return NClaim.inst().getClaimSettingsManager().isSettingEnabled(claim, Setting.CLAIM_PVP);
    }

    @Override
    public String getName() {
        return "NClaim";
    }

    @Override
    public JavaPlugin getPlugin() {
        return JavaPlugin.getPlugin(NClaim.class);
    }

    @Override
    public String onEnableMessage() {
        return "NClaim claims with PvP off are safe zones";
    }
}
