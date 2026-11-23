package nesoi.aysihuniks.nclaim.integrations;

import com.artillexstudios.axsellwands.api.events.AxSellwandsSellEvent;
import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import nesoi.aysihuniks.nclaim.utils.Msg;

public class AxSellWand implements Listener {

    private final NClaim plugin;

    public AxSellWand(NClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAxsellwandSell(AxSellwandsSellEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("nclaim.bypass.axsellwand") || player.hasPermission("nclaim.bypass.*")) return;

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) return;

        Material type = targetBlock.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return;

        Claim claim = Claim.claims.getAt(targetBlock);
        if (claim != null && !plugin.getClaimCoopManager().hasPermission(player, claim, nesoi.aysihuniks.nclaim.enums.Permission.USE_CHEST)) {
            event.setCancelled(true);
            Msg.send(player, plugin.getLangManager().getString("command.permission_denied"));
        }
    }
}
