package com.flyaway.thunderrider;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.lang.reflect.*;

public class ThunderRider extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BukkitRunnable task;
    private final Random random = new Random();
    private EssentialsHook essentialsHook;
    private WorldGuardHook wgHook;
    private final NamespacedKey RIDER_KEY = new NamespacedKey(this, "thunderrider_rider");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Config.init(this);

        essentialsHook = new EssentialsHook();
        if (essentialsHook.isEnabled()) {
            getLogger().info("EssentialsHook initialized.");
        } else {
            getLogger().info("EssentialsHook not initialized.");
        }

        wgHook = new WorldGuardHook();
        if (wgHook.isEnabled()) {
            getLogger().info("WorldGuardHook initialized.");
        } else {
            getLogger().info("WorldGuardHook not initialized.");
        }

        getCommand("thunderider").setExecutor(new CommandHandler(this));

        startTask();

        getLogger().info("ThunderRider enabled!");
    }

    @Override
    public void onDisable() {
        stopTask();
        getLogger().info("ThunderRider disabled!");
    }

    public void startTask() {
        stopTask();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (random.nextDouble() * 100 > Config.chance) continue;

                    if (player.hasPermission("thunderrider.ignore")) {
                        if (Config.debug) {
                            getLogger().info("Player skipped: " + player.getName() + " has perm thunderrider.ignore");
                        }
                        continue;
                    }

                    if (!isOnSurface(player)) continue;

                    if (essentialsHook.isAFK(player)) continue;

                    if (hasRidersNearby(player.getLocation())) continue;

                    if (!wgHook.canSpawnMobs(player.getLocation())) continue;

                    Location spawnLocation = getRandomLocationNearPlayer(player);
                    if (spawnLocation == null) continue;

                    if (!wgHook.canSpawnMobs(spawnLocation)) continue;

                    Bukkit.getScheduler().runTask(ThunderRider.this, () -> {
                        spawnEvent(spawnLocation, player);
                    });
                }
            }
        };

        task.runTaskTimer(this, 0L, Config.checkInterval);
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        Config.init(this);
        essentialsHook = new EssentialsHook();
        wgHook = new WorldGuardHook();
        startTask();
    }

    private boolean isOnSurface(Player player) {
        Location loc = player.getLocation();

        if (loc.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        return loc.getBlockY() >= loc.getWorld().getSeaLevel() &&
                loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }

    private boolean hasRidersNearby(Location location) {
        int radius = Config.existingRiderCheckRadius;

        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity.getPersistentDataContainer().has(RIDER_KEY)) {
                return true;
            }
        }
        return false;
    }

    private boolean canLightningStrike(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        int highestY = world.getHighestBlockYAt(x, z);

        if (location.getBlockY() < highestY) {
            if (Config.debug) {
                getLogger().info("Lightning can't strike: location " + location + " below the surface (" + highestY + ")");
            }
            return false;
        }

        return true;
    }

    private Location getRandomLocationNearPlayer(Player player) {
        Location playerLoc = player.getLocation();

        for (int i = 0; i < 10; i++) {
            int x = playerLoc.getBlockX() + random.nextInt(Config.radius * 2) - Config.radius;
            int z = playerLoc.getBlockZ() + random.nextInt(Config.radius * 2) - Config.radius;

            World world = playerLoc.getWorld();
            int surfaceY = world.getHighestBlockYAt(x, z);
            Location testLoc = new Location(world, x, surfaceY, z);

            Material blockType = testLoc.getBlock().getType();
            if (blockType == Material.WATER || blockType == Material.LAVA) {
                continue;
            }

            if (!canLightningStrike(testLoc)) {
                continue;
            }

            Location spawnLoc = testLoc.add(0, 1, 0);

            if (!wgHook.canSpawnMobs(spawnLoc)) {
                continue;
            }

            return spawnLoc;
        }

        if (Config.debug) {
            getLogger().info("Couldn't find a suitable spawn location near the " + player.getName());
        }

        return null;
    }

    public boolean spawnEvent(Location location, Player player) {
        if (!canLightningStrike(location)) {
            if (Config.debug) {
                getLogger().info("Cancel spawn: Lightning cannot strike the " + location);
            }
            return false;
        }

        // lightning
        World world = location.getWorld();
        world.strikeLightningEffect(location);
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        world.playSound(location, Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1.0f, 0.8f);
        world.spawnParticle(Particle.SMOKE, location, 20, 1, 1, 1, 0.1);

        // horse
        SkeletonHorse horse = (SkeletonHorse) world.spawnEntity(location, EntityType.SKELETON_HORSE);
        horse.setTamed(true);
        horse.setAdult();
        horse.setAI(true);

        double baseHealth = horse.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHealth * Config.horseHpMultiplier);
        horse.setHealth(baseHealth * Config.horseHpMultiplier);

        if (!Config.horseName.isEmpty()) {
            horse.customName(miniMessage.deserialize(Config.horseName));
            horse.setCustomNameVisible(true);
        }

        // rider
        Skeleton rider = (Skeleton) world.spawnEntity(location, EntityType.SKELETON);
        rider.getPersistentDataContainer().set(RIDER_KEY, PersistentDataType.BYTE, (byte) 1);

        double baseRiderHp = rider.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        rider.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseRiderHp * Config.riderHpMultiplier);
        rider.setHealth(baseRiderHp * Config.riderHpMultiplier);

        rider.getAttribute(Attribute.ATTACK_DAMAGE)
                .setBaseValue(rider.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue() * Config.riderAttackMultiplier);

        if (!Config.riderName.isEmpty()) {
            rider.customName(miniMessage.deserialize(Config.riderName));
            rider.setCustomNameVisible(true);
        }

        rider.getEquipment().setHelmet(makeItem(Config.riderHelmet));
        rider.getEquipment().setChestplate(makeItem(Config.riderChest));
        rider.getEquipment().setLeggings(makeItem(Config.riderLegs));
        rider.getEquipment().setBoots(makeItem(Config.riderBoots));
        rider.getEquipment().setItemInMainHand(makeItem(Config.riderWeapon));

        horse.addPassenger(rider);

        // helpers
        for (int i = 0; i < Config.helpers; i++) {
            Location skeletonLoc = location.clone().add(
                    random.nextDouble() * 3 - 1.5,
                    0,
                    random.nextDouble() * 3 - 1.5
            );

            if (!wgHook.canSpawnMobs(skeletonLoc)) {
                continue;
            }

            Skeleton additionalSkeleton = (Skeleton) world.spawnEntity(skeletonLoc, EntityType.SKELETON);

            double baseSkHp = additionalSkeleton.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
            additionalSkeleton.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseSkHp * Config.skeletonHpMultiplier);
            additionalSkeleton.setHealth(baseSkHp * Config.skeletonHpMultiplier);

            additionalSkeleton.getAttribute(Attribute.ATTACK_DAMAGE)
                    .setBaseValue(additionalSkeleton.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue() * Config.skeletonAttackMultiplier);

            additionalSkeleton.getEquipment().setHelmet(makeItem(Config.skeletonHelmet));
            additionalSkeleton.getEquipment().setChestplate(makeItem(Config.skeletonChest));
            additionalSkeleton.getEquipment().setLeggings(makeItem(Config.skeletonLegs));
            additionalSkeleton.getEquipment().setBoots(makeItem(Config.skeletonBoots));
            additionalSkeleton.getEquipment().setItemInMainHand(makeItem(Config.skeletonWeapon));

            if (!Config.skeletonName.isEmpty()) {
                additionalSkeleton.customName(miniMessage.deserialize(Config.skeletonName));
                additionalSkeleton.setCustomNameVisible(true);
            }
        }

        if (Config.showMessage) {
            player.sendMessage(miniMessage.deserialize(Config.getMessage("hunting")));
        }

        if (Config.debug) {
            getLogger().info("The rider is called for the player " + player.getName() + " in " + location);
        }
        return true;
    }

    private ItemStack makeItem(String name) {
        if (name == null || name.isEmpty()) return null;

        try {
            Material mat = Material.valueOf(name.toUpperCase());
            return new ItemStack(mat);
        } catch (Exception e) {
            return null;
        }
    }

}
