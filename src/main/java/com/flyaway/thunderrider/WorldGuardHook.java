package com.flyaway.thunderrider;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class WorldGuardHook {

    private boolean enabled = false;

    public WorldGuardHook() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            enabled = true;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canSpawnMobs(Location loc) {
        if (!enabled) return true;

        RegionQuery query = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .createQuery();

        var weLoc = BukkitAdapter.adapt(loc);

        StateFlag.State state = query.queryValue(weLoc, null, Flags.MOB_SPAWNING);

        return state != StateFlag.State.DENY;
    }
}
