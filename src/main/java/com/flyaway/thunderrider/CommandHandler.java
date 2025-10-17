package com.flyaway.thunderrider;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandHandler implements CommandExecutor {

    private final ThunderRider plugin;

    public CommandHandler(ThunderRider plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("thunderrider.admin")) {
            sender.sendMessage(ChatColor.RED + "Недостаточно прав!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(ChatColor.GREEN + "ThunderRider перезагружен!");
                break;

            case "start":
                plugin.startTask();
                sender.sendMessage(ChatColor.GREEN + "ThunderRider запущен!");
                break;

            case "stop":
                plugin.stopTask();
                sender.sendMessage(ChatColor.YELLOW + "ThunderRider остановлен!");
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ThunderRider Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/thunderider reload" + ChatColor.WHITE + " - Перезагрузить конфиг");
        sender.sendMessage(ChatColor.YELLOW + "/thunderider start" + ChatColor.WHITE + " - Запустить плагин");
        sender.sendMessage(ChatColor.YELLOW + "/thunderider stop" + ChatColor.WHITE + " - Остановить плагин");
    }
}
