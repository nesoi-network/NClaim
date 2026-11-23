package nesoi.aysihuniks.nclaim.commands.admin;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.BaseCommand;
import nesoi.aysihuniks.nclaim.enums.SaleFilter;
import nesoi.aysihuniks.nclaim.ui.claim.admin.AdminAllClaimMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import nesoi.aysihuniks.nclaim.utils.Msg;

import java.util.ArrayList;

public class MenuCommand extends BaseCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            Msg.send(sender, NClaim.inst().getLangManager().getString("command.must_be_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("nclaim.adminmenu") && !player.hasPermission("nclaim.admin")) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
            return true;
        }

        new AdminAllClaimMenu(player, null, true, 0, new ArrayList<>(), SaleFilter.ALL);
        return true;
    }
}
