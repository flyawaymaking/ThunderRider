package com.flyaway.thunderrider;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandHandler implements CommandExecutor {

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
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(miniMessage.deserialize(Config.getMessage("reloaded")));
                break;

            case "start":
                plugin.startTask();
                sender.sendMessage(miniMessage.deserialize(Config.getMessage("started")));
                break;

            case "stop":
                plugin.stopTask();
                sender.sendMessage(miniMessage.deserialize(Config.getMessage("stopped")));
                break;

            case "spawn":
                if (sender instanceof Player player) {
                    plugin.spawnEvent(player.getLocation(), player);
                }
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize(Config.getMessage("help")));
    }
}
