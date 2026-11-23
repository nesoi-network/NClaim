package nesoi.aysihuniks.nclaim.commands.admin;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.BaseCommand;
import nesoi.aysihuniks.nclaim.utils.LangManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import nesoi.aysihuniks.nclaim.utils.Msg;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SetCommand extends BaseCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        LangManager langManager = NClaim.inst().getLangManager();

        CommandSender player = sender; // console allowed: these commands never need a location

        if (!player.hasPermission("nclaim.set") && !player.hasPermission("nclaim.admin")) {
            Msg.send(player, langManager.getString("command.permission_denied"));
            return true;
        }

        if (args.length < 3) {
            Msg.send(player, langManager.getString("command.wrong_usage"));
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "balance":
                handleBalanceSet(player, args);
                break;
            case "blockvalue":
                handleBlockValueSet(player, args);
                break;
            default:
                Msg.send(player, langManager.getString("command.wrong_usage"));
                break;
        }

        return true;
    }

    private void handleBalanceSet(CommandSender player, String[] args) {
        if (args.length < 4) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.wrong_usage"));
            return;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            String targetName = args[3];
            Player targetPlayer = NClaim.inst().getServer().getPlayer(targetName);

            if (targetPlayer == null) {
                Msg.send(player, NClaim.inst().getLangManager().getString("command.player.not_found")
                        .replace("{target}", targetName));
                return;
            }

            if (amount < 0) {
                Msg.send(player, NClaim.inst().getLangManager().getString("command.enter_a_valid_number"));
                return;
            }
            NClaim.inst().getEconomyService().set(targetPlayer, amount);

            Msg.send(targetPlayer, NClaim.inst().getLangManager().getString("command.set.target_balance_set")
                    .replace("{balance}", nesoi.aysihuniks.nclaim.utils.Numbers.full(amount)));
            Msg.send(player, NClaim.inst().getLangManager().getString("command.set.player_balance_set")
                    .replace("{amount}", nesoi.aysihuniks.nclaim.utils.Numbers.full(amount))
                    .replace("{target}", targetName));
        } catch (NumberFormatException e) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.enter_a_valid_number"));
        }
    }

    private void handleBlockValueSet(CommandSender player, String[] args) {
        if (args.length < 4) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.wrong_usage"));
            return;
        }

        try {
            String materialName = args[2].toUpperCase();
            int value = Integer.parseInt(args[3]);
        
        if (value <= 0) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.enter_a_valid_number"));
            return;
        }

        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.invalid_material")
                    .replace("{material}", materialName));
            return;
        }

        if (!materialName.endsWith("_BLOCK") && !materialName.contains("ORE")) {
            Msg.send(player, NClaim.inst().getLangManager().getString("command.invalid_block_type")
                    .replace("{material}", materialName));
            return;
        }

        NClaim.inst().getBlockValueManager().setBlockValue(material, value);
        
        Msg.send(player, NClaim.inst().getLangManager().getString("command.set.block_value_set")
                .replace("{block}", material.name())
                .replace("{value}", String.valueOf(value)));

    } catch (NumberFormatException e) {
        Msg.send(player, NClaim.inst().getLangManager().getString("command.enter_a_valid_number"));
    }
}

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if ((!sender.hasPermission("nclaim.set") && !sender.hasPermission("nclaim.admin"))) {
            return null;
        }

        if (args.length == 2) {
            return Arrays.asList("balance", "blockvalue");
        }

        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("blockvalue")) {
                return Arrays.stream(Material.values())
                        .map(Material::name)
                        .filter(name -> name.endsWith("_BLOCK") || name.contains("ORE"))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("balance")) {
            return NClaim.inst().getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }

        return null;
    }
}
