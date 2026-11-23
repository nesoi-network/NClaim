package nesoi.aysihuniks.nclaim.commands.admin;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.BaseCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import nesoi.aysihuniks.nclaim.utils.Msg;

public class ReloadCommand extends BaseCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        CommandSender player = sender; // works from the console too

        if (!player.hasPermission("nclaim.reload") && !player.hasPermission("nclaim.admin")) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
            return true;
        }

        try {
            NClaim.inst().reloadPlugin();
            Msg.send(player, NClaim.inst().getLangManager().getString("command.reload.success"));
        } catch (Exception e) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.reload.failed"));
        }

        return true;
    }
}
