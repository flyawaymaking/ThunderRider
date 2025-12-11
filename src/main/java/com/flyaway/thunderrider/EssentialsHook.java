package com.flyaway.thunderrider;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EssentialsHook {

    private boolean enabled = false;
    private Essentials essentials;

    public EssentialsHook() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (plugin != null) {
            enabled = true;
            essentials = (Essentials) plugin;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAFK(Player player) {
        if (!enabled) return false;
        User user = essentials.getUser(player);

        if (user == null) return false;

        return user.isAfk();
    }
}
