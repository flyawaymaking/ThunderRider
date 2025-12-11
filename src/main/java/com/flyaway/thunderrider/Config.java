package com.flyaway.thunderrider;

import org.bukkit.configuration.file.FileConfiguration;

public class Config {
    private static FileConfiguration config;

    public static int checkInterval;
    public static double chance;
    public static int radius;
    public static int helpers;
    public static boolean showMessage;
    public static int existingRiderCheckRadius;
    public static boolean debug;

    public static double horseHpMultiplier;
    public static double riderHpMultiplier;
    public static double skeletonHpMultiplier;

    public static double riderAttackMultiplier;
    public static double skeletonAttackMultiplier;

    public static String horseName;
    public static String riderName;
    public static String skeletonName;

    public static String riderHelmet;
    public static String riderChest;
    public static String riderLegs;
    public static String riderBoots;
    public static String riderWeapon;

    public static String skeletonHelmet;
    public static String skeletonChest;
    public static String skeletonLegs;
    public static String skeletonBoots;
    public static String skeletonWeapon;

    public static void init(ThunderRider plugin) {
        Config.config = plugin.getConfig();

        checkInterval = config.getInt("check-interval", 1000); // тики (50 секунд)
        chance = config.getDouble("chance", 1.0); // шанс в процентах
        radius = config.getInt("spawn-radius", 15); // радиус в блоках
        helpers = config.getInt("helpers", 2);
        showMessage = config.getBoolean("show-message", true);
        existingRiderCheckRadius = config.getInt("existing-rider-check-radius", 50);
        debug = config.getBoolean("debug", false);

        horseHpMultiplier = config.getDouble("hp-multiplier.horse", 3.0);
        riderHpMultiplier = config.getDouble("hp-multiplier.rider", 3.0);
        skeletonHpMultiplier = config.getDouble("hp-multiplier.skeleton", 1.5);

        riderAttackMultiplier = config.getDouble("attack-multiplier.rider", 1.5);
        skeletonAttackMultiplier = config.getDouble("attack-multiplier.skeleton", 1.5);

        horseName = config.getString("names.horse", "");
        riderName = config.getString("names.rider", "<gold>Всадник бури");
        skeletonName = config.getString("names.skeleton", "");

        riderHelmet = config.getString("equipment.rider.helmet", "CHAINMAIL_HELMET");
        riderChest = config.getString("equipment.rider.chestplate", "");
        riderLegs = config.getString("equipment.rider.leggings", "");
        riderBoots = config.getString("equipment.rider.boots", "");
        riderWeapon = config.getString("equipment.rider.weapon", "BOW");

        skeletonHelmet = config.getString("equipment.skeleton.helmet", "CHAINMAIL_HELMET");
        skeletonChest = config.getString("equipment.skeleton.chestplate", "");
        skeletonLegs = config.getString("equipment.skeleton.leggings", "");
        skeletonBoots = config.getString("equipment.skeleton.boots", "");
        skeletonWeapon = config.getString("equipment.skeleton.weapon", "IRON_SWORD");
    }

    public static String getMessage(String key) {
        return config.getString("messages." + key, "<red>not found: messages." + key);
    }
}
