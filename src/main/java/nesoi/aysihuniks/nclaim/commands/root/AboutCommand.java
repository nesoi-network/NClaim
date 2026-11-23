package nesoi.aysihuniks.nclaim.commands.root;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.BaseCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import nesoi.aysihuniks.nclaim.utils.Text;
import nesoi.aysihuniks.nclaim.utils.Msg;

public class AboutCommand extends BaseCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            Msg.send(sender, NClaim.inst().getLangManager().getString("command.must_be_player"));
            return true;
        }

        var descriptionFile = NClaim.inst().getPluginMeta();
        String pluginName = descriptionFile.getName();
        String pluginVersion = descriptionFile.getVersion();
        String pluginAuthor = String.join(", ", descriptionFile.getAuthors());
        String resourceLink = "https://www.spigotmc.org/resources/nclaim-advanced-claim-system.122527/";

        Msg.send(sender, Text.legacy("{WHITE}This server is running {ORANGE}" + pluginName + " " + pluginVersion + " {WHITE}by {ORANGE}" + pluginAuthor));
        Msg.send(sender, Text.legacy("{GRAY}" + resourceLink));

        return true;
    }
}
