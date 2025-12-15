package com.flyaway.thunderrider;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final ThunderRider plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CommandHandler(ThunderRider plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("thunderrider.admin")) {
            sender.sendMessage(miniMessage.deserialize(Config.getMessage("no-permissions")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(miniMessage.deserialize(Config.getMessage("reloaded")));
            }
            case "start" -> {
                plugin.startTask();
                sender.sendMessage(miniMessage.deserialize(Config.getMessage("started")));
            }
            case "stop" -> {
                plugin.stopTask();
                sender.sendMessage(miniMessage.deserialize(Config.getMessage("stopped")));
            }
            case "spawn" -> {
                if (sender instanceof Player player) {
                    plugin.spawnEvent(player.getLocation(), player);
                }
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("thunderrider.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("reload", "start", "stop", "spawn").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize(Config.getMessage("help")));
    }
}
