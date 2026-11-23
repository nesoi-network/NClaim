package nesoi.aysihuniks.nclaim.commands.root;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.BaseCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import nesoi.aysihuniks.nclaim.utils.Text;
import nesoi.aysihuniks.nclaim.utils.Msg;

public class HelpCommand extends BaseCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            Msg.send(sender, NClaim.inst().getLangManager().getString("command.must_be_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("nclaim.help") && !player.hasPermission("nclaim.use")) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
            return true;
        }

        String[] helpMessage = {
            "",
            "{BROWN}NClaim All General Commands",
            " {YELLOW}/nclaim balance {GRAY}- {WHITE}Show the player balance.",
            " {YELLOW}/nclaim about {GRAY}- {WHITE}Show information about this plugin.",
            " {YELLOW}/nclaim admin {GRAY}- {WHITE}Show all admin commands.",
            " {YELLOW}/nclaim level {GRAY}- {WHITE}Show the level of the claim.",
            " {YELLOW}/nclaim list {GRAY}- {WHITE}List your claims.",
            " {YELLOW}/nclaim name <claim|here> <name|off> {GRAY}- {WHITE}Rename a claim (off = reset).",
            " {YELLOW}/nclaim border {GRAY}- {WHITE}Toggle the particle outline of the claim you are in.",
            " {YELLOW}/nclaim help/? {GRAY}- {WHITE}Send this help messages.",
            " {YELLOW}/nclaim {GRAY}- {WHITE}Open the Claim menu.",
            ""
        };

        for (String line : helpMessage) {
            Msg.send(player, Text.legacy(line));
        }

        return true;
    }
}
