package nesoi.aysihuniks.nclaim.commands.root;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.BaseCommand;
import nesoi.aysihuniks.nclaim.model.Claim;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import nesoi.aysihuniks.nclaim.utils.Msg;

import java.util.*;

public class NameCommand extends BaseCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            Msg.send(sender, NClaim.inst().getLangManager().getString("command.must_be_player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("nclaim.name") && !player.hasPermission("nclaim.use")) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
            return true;
        }

        if (args.length < 3) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.wrong_usage"));
            return true;
        }

        String identifier = args[1];
        String displayNameRaw = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();

        Claim claim;
        if ("here".equalsIgnoreCase(identifier)) {
            claim = Claim.claims.getAt(player.getLocation());
        } else {
            claim = Claim.getClaimByIdentifier(player.getUniqueId(), identifier);
        }

        if (claim == null) {
            Msg.send(player, NClaim.inst().getLangManager().getString("claim.not_found"));
            return true;
        }

        NClaim.inst().getClaimService().renameClaim(player, claim, displayNameRaw);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player player = (Player) sender;

        if (!player.hasPermission("nclaim.use") && !player.hasPermission("nclaim.admin")) {
            return null;
        }

        if (args.length == 2) {
            Set<String> suggestions = new LinkedHashSet<>();
            suggestions.add("here");

            boolean isAdmin = player.hasPermission("nclaim.admin");

            for (Claim c : Claim.claims) {
                if (!isAdmin && !player.getUniqueId().equals(c.getOwner())) {
                    continue;
                }

                if (!c.getSlug().isEmpty()) {
                    suggestions.add(c.getSlug());
                }
            }

            return new ArrayList<>(suggestions);
        }

        if (args.length == 3) {
            return Arrays.asList("off", "none", "<display_name>");
        }

        return null;
    }
}
