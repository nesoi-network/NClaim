package nesoi.aysihuniks.nclaim.commands;

import nesoi.aysihuniks.nclaim.NClaim;
import nesoi.aysihuniks.nclaim.commands.admin.*;
import nesoi.aysihuniks.nclaim.commands.root.*;
import nesoi.aysihuniks.nclaim.commands.root.HelpCommand;
import nesoi.aysihuniks.nclaim.ui.claim.ClaimMainMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import nesoi.aysihuniks.nclaim.utils.Msg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class AllCommandExecutor implements CommandExecutor, TabCompleter {
    private final HashMap<String, BaseCommand> commands = new HashMap<>();
    private final HashMap<String, BaseCommand> adminCommands = new HashMap<>();

    public AllCommandExecutor() {
        commands.put("about", new AboutCommand());
        commands.put("balance", new BalanceCommand());
        commands.put("help", new HelpCommand());
        commands.put("level", new LevelCommand());
        commands.put("list", new ListCommand());
        commands.put("name", new NameCommand());

        adminCommands.put("add", new AddCommand());
        adminCommands.put("change", new ChangeCommand());
        adminCommands.put("menu", new MenuCommand());
        adminCommands.put("remove", new RemoveCommand());
        adminCommands.put("reload", new ReloadCommand());
        adminCommands.put("set", new SetCommand());
        adminCommands.put("help", new nesoi.aysihuniks.nclaim.commands.admin.HelpCommand());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length > 0 && args[0].equals("?")) args[0] = "help";

        if (!(sender instanceof Player)) {
            // The console may run reload and the add/remove/set admin commands; everything else needs a player.
            BaseCommand consoleCmd = args.length >= 2 && args[0].equalsIgnoreCase("admin")
                    ? adminCommands.get(args[1].toLowerCase()) : null;
            if (consoleCmd instanceof ReloadCommand || consoleCmd instanceof AddCommand
                    || consoleCmd instanceof RemoveCommand || consoleCmd instanceof SetCommand) {
                return consoleCmd.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
            }
            Msg.send(sender, NClaim.inst().getLangManager().getString("command.must_be_player"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("border")) {
            boolean on = NClaim.inst().getClaimVisualizerService().toggleBorders(player);
            Msg.send(player, NClaim.inst().getLangManager().getString(on ? "claim.border.enabled" : "claim.border.disabled"));
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("nclaim.use")) {
                Msg.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
                return true;
            }
            new ClaimMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("admin")) {
            if (!player.hasPermission("nclaim.admin")) {
                Msg.send(player, NClaim.inst().getLangManager().getString("command.permission_denied"));
                return true;
            }

            if (args.length == 1) {
                adminCommands.get("help").onCommand(sender, command, label, args);
                return true;
            }

            String adminSubCommand = args[1].toLowerCase();
            BaseCommand adminCmd = adminCommands.get(adminSubCommand);

            if (adminCmd != null) {
                return adminCmd.onCommand(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
            } else {
                Msg.send(player, NClaim.inst().getLangManager().getString("command.wrong_usage"));
                return true;
            }
        }

        BaseCommand cmd = commands.get(subCommand);
        if (cmd != null) {
            return cmd.onCommand(sender, command, label, args);
        }

        Msg.send(player, NClaim.inst().getLangManager().getString("command.wrong_usage"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(commands.keySet());
            completions.add("border");
            if (sender.hasPermission("nclaim.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission("nclaim.admin")) {
                completions.addAll(adminCommands.keySet());
            }
        } else if (args.length > 2 && args[0].equalsIgnoreCase("admin")) {
            BaseCommand adminCmd = adminCommands.get(args[1].toLowerCase());
            if (adminCmd != null) {
                List<String> subCompletions = adminCmd.onTabComplete(sender, command, label, Arrays.copyOfRange(args, 1, args.length));
                if (subCompletions != null) {
                    completions.addAll(subCompletions);
                }
            }
        } else if (args.length >= 2) {
            BaseCommand cmd = commands.get(args[0].toLowerCase());
            if (cmd != null) {
                List<String> subCompletions = cmd.onTabComplete(sender, command, label, args);
                if (subCompletions != null) {
                    completions.addAll(subCompletions);
                }
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
