package com.flyaway.thunderrider;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.lang.reflect.*;

public class ThunderRider extends JavaPlugin {

    private BukkitRunnable task;
    private final Random random = new Random();
    private Config config;
    private boolean hasEssentials = false;

    private static boolean wgInitialized = false;
    private static boolean wgAvailable = false;

    // Кэшированные классы и методы
    private static Class<?> wgClass, bukkitAdapterClass, flagsClass, regionAssociableClass, flagClass, stateEnumClass;
    private static Method getInstanceMethod, getPlatformMethod, getRegionContainerMethod, createQueryMethod,
            adaptMethod, getApplicableRegionsMethod, queryValueMethod;
    private static Field mobSpawningFlagField;
    private static Object denyStateValue;

    @Override
    public void onEnable() {
        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();
        config = new Config(this);

        // Проверяем наличие Essentials
        hasEssentials = getServer().getPluginManager().getPlugin("Essentials") != null;
        if (hasEssentials) {
            getLogger().info("Essentials обнаружен, AFK проверка активирована");
        } else {
            getLogger().info("Essentials не обнаружен, AFK проверка отключена");
        }

        initWorldGuardReflection();

        // Регистрируем команду
        getCommand("thunderider").setExecutor(new CommandHandler(this));

        // Запускаем задачу
        startTask();

        getLogger().info("ThunderRider включен!");
    }

    @Override
    public void onDisable() {
        stopTask();
        getLogger().info("ThunderRider выключен!");
    }

    // Инициализация WorldGuard рефлексии (один раз)
    private void initWorldGuardReflection() {
        if (wgInitialized) return;
        wgInitialized = true;

        try {
            wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            regionAssociableClass = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
            stateEnumClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");

            getInstanceMethod = wgClass.getMethod("getInstance");
            getPlatformMethod = wgClass.getMethod("getPlatform");
            getRegionContainerMethod = getPlatformMethod.getReturnType().getMethod("getRegionContainer");
            createQueryMethod = getRegionContainerMethod.getReturnType().getMethod("createQuery");
            adaptMethod = bukkitAdapterClass.getMethod("adapt", Location.class);

            // Для получения applicable regions
            Class<?> regionQueryClass = createQueryMethod.getReturnType();
            Class<?> weLocationClass = adaptMethod.getReturnType();
            getApplicableRegionsMethod = regionQueryClass.getMethod("getApplicableRegions", weLocationClass);

            // Для проверки флага MOB_SPAWNING
            mobSpawningFlagField = flagsClass.getField("MOB_SPAWNING");

            // Для вызова queryValue
            Class<?> regionResultSetClass = getApplicableRegionsMethod.getReturnType();
            queryValueMethod = regionResultSetClass.getMethod("queryValue", regionAssociableClass, flagClass);

            // Значение StateFlag.State.DENY
            denyStateValue = Enum.valueOf((Class<Enum>) stateEnumClass.asSubclass(Enum.class), "DENY");

            wgAvailable = true;

        } catch (Throwable e) {
            wgAvailable = false;
            getLogger().warning("[ThunderRider] WorldGuard не найден или структура изменилась, защита отключена: " + e.getMessage());
        }
    }

    public void startTask() {
        stopTask(); // Останавливаем предыдущую задачу если есть

        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Проверяем шанс
                    if (random.nextDouble() * 100 > config.chance) continue;

                    // ПРОВЕРЯЕМ ПЕРМИШЕН - если у игрока есть право thunderrider.ignore, пропускаем
                    if (player.hasPermission("thunderrider.ignore")) {
                        if (config.debug) {
                            getLogger().info("Игрок " + player.getName() + " имеет право thunderrider.ignore, пропускаем");
                        }
                        continue;
                    }

                    // Проверяем, что игрок на поверхности и в верхнем мире
                    if (!isOnSurface(player)) continue;

                    // Проверяем AFK через Essentials
                    if (isAFK(player)) continue;

                    // Проверяем, нет ли уже всадников рядом
                    if (hasRidersNearby(player.getLocation())) continue;

                    // Проверяем разрешение на спавн мобов через WorldGuard (если есть)
                    if (!canSpawnMobsAtLocation(player.getLocation())) continue;

                    // Находим случайное место рядом с игроком
                    Location spawnLocation = getRandomLocationNearPlayer(player);
                    if (spawnLocation == null) continue;

                    // Проверяем разрешение на спавн в конкретной точке
                    if (!canSpawnMobsAtLocation(spawnLocation)) continue;

                    // Запускаем событие в основном потоке
                    Bukkit.getScheduler().runTask(ThunderRider.this, () -> {
                        spawnEvent(player, spawnLocation);
                    });
                }
            }
        };

        // Запускаем задачу с интервалом из конфига (в тиках)
        task.runTaskTimer(this, 0L, config.checkInterval);
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        config = new Config(this);
        // Перепроверяем наличие Essentials при перезагрузке
        hasEssentials = getServer().getPluginManager().getPlugin("Essentials") != null;
        initWorldGuardReflection();
        startTask();
    }

    private boolean isOnSurface(Player player) {
        Location loc = player.getLocation();

        // Проверяем, что игрок в верхнем мире
        if (loc.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        // Проверяем, что игрок выше уровня моря и под ним есть твердый блок
        return loc.getBlockY() >= loc.getWorld().getSeaLevel() &&
               loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }

    private boolean isAFK(Player player) {
        // Если Essentials не установлен, пропускаем проверку AFK
        if (!hasEssentials) {
            return false;
        }

        // Проверка через Essentials API
        try {
            Class<?> essentialsClass = Class.forName("com.earth2me.essentials.Essentials");
            Object essentials = getServer().getPluginManager().getPlugin("Essentials");

            if (essentials != null) {
                Object user = essentialsClass.getMethod("getUser", Player.class).invoke(essentials, player);
                boolean isAfk = (boolean) user.getClass().getMethod("isAfk").invoke(user);

                if (config.debug && isAfk) {
                    getLogger().info("Игрок " + player.getName() + " в AFK, пропускаем спавн");
                }

                return isAfk;
            }
        } catch (Exception e) {
            // В случае ошибки считаем, что игрок не в AFK
            if (config.debug) {
                getLogger().warning("Ошибка при проверке AFK для " + player.getName() + ": " + e.getMessage());
            }
        }

        return false;
    }

    private boolean hasRidersNearby(Location location) {
        int radius = config.existingRiderCheckRadius;

        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof SkeletonHorse) {
                SkeletonHorse horse = (SkeletonHorse) entity;
                // Проверяем, есть ли на лошади всадник-скелет
                if (!horse.getPassengers().isEmpty()) {
                    for (Entity passenger : horse.getPassengers()) {
                        if (passenger instanceof Skeleton) {
                            // Проверяем, что скелет в кольчужном шлеме
                            Skeleton skeleton = (Skeleton) passenger;
                            if (skeleton.getEquipment().getHelmet() != null &&
                                skeleton.getEquipment().getHelmet().getType() == Material.CHAINMAIL_HELMET) {
                                if (config.debug) {
                                    getLogger().info("Найден существующий всадник в радиусе " + radius + " блоков");
                                }
                                return true; // Нашли существующего всадника
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

   private boolean canSpawnMobsAtLocation(Location location) {
       if (!isWorldGuardEnabled()) {
           return true;
       }

       if (!wgInitialized) {
           initWorldGuardReflection();
       }

       if (!wgAvailable) {
           return true;
       }

       try {
           // WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
           Object wgInstance = getInstanceMethod.invoke(null);
           Object platform = getPlatformMethod.invoke(wgInstance);
           Object regionContainer = getRegionContainerMethod.invoke(platform);
           Object query = createQueryMethod.invoke(regionContainer);

           // BukkitAdapter.adapt(location)
           Object weLocation = adaptMethod.invoke(null, location);

           // query.getApplicableRegions(weLocation)
           Object regionSet = getApplicableRegionsMethod.invoke(query, weLocation);

           // Flags.MOB_SPAWNING
           Object mobSpawningFlag = mobSpawningFlagField.get(null);

           // regionSet.queryValue(null, MOB_SPAWNING)
           Object state = queryValueMethod.invoke(regionSet, null, mobSpawningFlag);

           // Проверяем, что не DENY
           boolean canSpawn = (state == null) || !state.equals(denyStateValue);

           if (config.debug && !canSpawn) {
               getLogger().info("WorldGuard запретил спавн мобов в " + location);
           }

           return canSpawn;

       } catch (Throwable e) {
           if (config.debug) {
               getLogger().warning("Ошибка при проверке WorldGuard (через рефлексию): " + e.getMessage());
           }
           return true; // если ошибка — не блокируем
       }
   }

    private boolean isWorldGuardEnabled() {
        return getServer().getPluginManager().getPlugin("WorldGuard") != null;
    }

    private boolean canLightningStrike(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        // Получаем высоту самого верхнего блока в этой точке
        int highestY = world.getHighestBlockYAt(x, z);

        // Если высота точки спавна ниже высоты самого верхнего блока - значит есть препятствие
        if (location.getBlockY() < highestY) {
            if (config.debug) {
                getLogger().info("Молния не может ударить: точка " + location + " ниже поверхности (" + highestY + ")");
            }
            return false;
        }

        // Дополнительная проверка: нет ли блоков прямо над точкой удара
        Location checkLoc = location.clone().add(0, 1, 0);
        if (checkLoc.getBlock().getType().isSolid()) {
            if (config.debug) {
                getLogger().info("Молния не может ударить: блок над точкой " + checkLoc.getBlock().getType());
            }
            return false;
        }

        return true;
    }

    private Location getRandomLocationNearPlayer(Player player) {
        Location playerLoc = player.getLocation();

        for (int i = 0; i < 10; i++) {
            int x = playerLoc.getBlockX() + random.nextInt(config.radius * 2) - config.radius;
            int z = playerLoc.getBlockZ() + random.nextInt(config.radius * 2) - config.radius;

            // Находим высоту для спавна (самый верхний блок в этой точке)
            World world = playerLoc.getWorld();
            int surfaceY = world.getHighestBlockYAt(x, z);
            Location testLoc = new Location(world, x, surfaceY, z);

            // Проверяем, что место безопасное (не в воде/лаве)
            Material blockType = testLoc.getBlock().getType();
            if (blockType == Material.WATER || blockType == Material.LAVA) {
                continue;
            }

            // Проверяем, что молния может ударить в эту точку
            if (!canLightningStrike(testLoc)) {
                continue;
            }

            Location spawnLoc = testLoc.add(0, 1, 0); // Спавним на блок выше поверхности

            // Дополнительная проверка через WorldGuard для точки спавна
            if (!canSpawnMobsAtLocation(spawnLoc)) {
                continue;
            }

            return spawnLoc;
        }

        if (config.debug) {
            getLogger().info("Не найдено подходящее место для спавна рядом с " + player.getName());
        }

        return null;
    }

    private void spawnEvent(Player player, Location location) {
        // Проверяем еще раз, что молния может ударить (на случай параллельных изменений)
        if (!canLightningStrike(location)) {
            if (config.debug) {
                getLogger().info("Отмена спавна: молния не может ударить в " + location);
            }
            return;
        }

        World world = location.getWorld();

        // Бьем молнией (без урона по игрокам)
        world.strikeLightningEffect(location);

        // Звуковые эффекты
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        world.playSound(location, Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1.0f, 0.8f);

        // Частицы
        world.spawnParticle(Particle.SMOKE, location, 20, 1, 1, 1, 0.1);

        // Создаем лошадь-скелета
        SkeletonHorse horse = (SkeletonHorse) world.spawnEntity(location, EntityType.SKELETON_HORSE);
        horse.setTamed(true);
        horse.setAdult();
        horse.setAI(true);

        // УВЕЛИЧИВАЕМ ЗДОРОВЬЕ ЛОШАДИ В 3 РАЗА
        double baseHealth = horse.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHealth * 3);
        horse.setHealth(baseHealth * 3);

        // Создаем всадника-скелета
        Skeleton rider = (Skeleton) world.spawnEntity(location, EntityType.SKELETON);

        // Настраиваем снаряжение всадника
        ItemStack helmet = new ItemStack(Material.CHAINMAIL_HELMET);
        ItemMeta meta = helmet.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + "Шлем Нежити");
            helmet.setItemMeta(meta);
        }

        // Экипируем скелета
        rider.getEquipment().setHelmet(helmet);
        rider.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));

        // Делаем скелета всадником
        horse.addPassenger(rider);

        // Создаем дополнительных скелетов
        for (int i = 0; i < config.additionalSkeletons; i++) {
            Location skeletonLoc = location.clone().add(
                random.nextDouble() * 3 - 1.5,
                0,
                random.nextDouble() * 3 - 1.5
            );

            // Проверяем, что точка для дополнительного скелета валидна
            if (!canSpawnMobsAtLocation(skeletonLoc)) {
                continue;
            }

            Skeleton additionalSkeleton = (Skeleton) world.spawnEntity(skeletonLoc, EntityType.SKELETON);
            additionalSkeleton.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            additionalSkeleton.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));

            // Настраиваем здоровье и урон
            if (config.buffSkeletons) {
                additionalSkeleton.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0);
                additionalSkeleton.setHealth(30.0);
            }
        }

        // Сообщение игроку
        if (config.showMessage) {
            player.sendMessage(ChatColor.RED + "⚡ На вас охотятся Всадники Бури!");
        }

        // Логируем для отладки
        if (config.debug) {
            getLogger().info("Всадник призван для игрока " + player.getName() + " в " + location);
        }
    }

    // Класс для работы с конфигом
    public static class Config {
        public final int checkInterval;
        public final double chance;
        public final int radius;
        public final int additionalSkeletons;
        public final boolean buffSkeletons;
        public final boolean showMessage;
        public final int existingRiderCheckRadius;
        public final boolean debug;

        public Config(ThunderRider plugin) {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();

            checkInterval = cfg.getInt("check-interval", 200); // тики (10 секунд)
            chance = cfg.getDouble("chance", 1.0); // шанс в процентах
            radius = cfg.getInt("spawn-radius", 15); // радиус в блоках
            additionalSkeletons = cfg.getInt("additional-skeletons", 2);
            buffSkeletons = cfg.getBoolean("buff-skeletons", true);
            showMessage = cfg.getBoolean("show-message", true);
            existingRiderCheckRadius = cfg.getInt("existing-rider-check-radius", 50);
            debug = cfg.getBoolean("debug", false);
        }
    }
}
