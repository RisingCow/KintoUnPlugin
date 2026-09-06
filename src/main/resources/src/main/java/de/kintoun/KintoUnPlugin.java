package de.kintoun;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.inventory.ItemCraftedEvent;

import org.bukkit.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Keyed;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KintoUnPlugin extends JavaPlugin implements Listener {

    private static final double MAX_HEIGHT = 50.0;

    private static final int MAX_KINTO_UNS = 10;

    private static final long BOOST_COOLDOWN = 30_000L;
    private static final int BOOST_DURATION_TICKS = 20;
    private static final double BOOST_SPEED = 1.8;

    private static final long CLOUD_RECHARGE = 30_000L;

    private static final double TRAIL_DISTANCE = 0.65;
    private static final int MAX_TRAIL_BLOCKS = 7;

    private static final float TRAIL_SIZE = 0.55f;

    /*
     * Größe des Blockbench-Modells als ItemDisplay.
     * Falls die Wolke später zu groß oder zu klein ist,
     * kann dieser Wert angepasst werden.
     */
    private static final float CLOUD_MODEL_SCALE = 2.0f;

    private NamespacedKey kintoUnKey;
    private NamespacedKey recipeKey;
    private NamespacedKey achievementKey;

    private final Map<UUID, ItemDisplay> activeClouds = new HashMap<>();

    private final Map<UUID, List<Interaction>> cloudHitboxes =
            new HashMap<>();

    private final Map<UUID, Long> boostCooldowns =
            new HashMap<>();

    private final Map<UUID, Long> cloudRechargeCooldowns =
            new HashMap<>();

    private final Map<UUID, List<ItemDisplay>> goldTrails =
            new HashMap<>();

    private final Map<UUID, Location> lastTrailLocations =
            new HashMap<>();

    private final Map<UUID, Integer> boostTasks =
            new HashMap<>();

    private int kintoUnCount;

    @Override
    public void onEnable() {

        kintoUnKey =
                new NamespacedKey(
                        this,
                        "kinto_un_item"
                );

        recipeKey =
                new NamespacedKey(
                        this,
                        "kinto_un"
                );

        achievementKey =
                new NamespacedKey(
                        this,
                        "kinto_un"
                );

        saveDefaultConfig();

        kintoUnCount =
                getConfig().getInt(
                        "kinto-un-count",
                        0
                );

        registerKintoUnRecipe();

        registerKintoUnAchievement();

        getServer()
                .getPluginManager()
                .registerEvents(
                        this,
                        this
                );

        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {

                    for (
                            Map.Entry<UUID, ItemDisplay> entry :
                            activeClouds.entrySet()
                    ) {

                        Player player =
                                Bukkit.getPlayer(
                                        entry.getKey()
                                );

                        ItemDisplay cloud =
                                entry.getValue();

                        if (
                                player == null
                                        || !player.isOnline()
                                        || cloud.isDead()
                        ) {
                            continue;
                        }

                        Location cloudLocation =
                                player.getLocation()
                                        .clone();

                        cloudLocation.setY(
                                player.getLocation().getY()
                                        - 1.2
                        );

                        cloud.teleport(
                                cloudLocation
                        );

                        updateCloudHitboxes(
                                player
                        );

                        updateGoldTrail(
                                player
                        );

                        player.sendActionBar(
                                "§6Drop Taste für Boost"
                        );
                    }

                },
                1L,
                1L
        );

        getLogger().info(
                "KintoUn wurde gestartet!"
        );

        getLogger().info(
                "Kinto-Uns: "
                        + kintoUnCount
                        + "/"
                        + MAX_KINTO_UNS
        );
    }

    @Override
    public void onDisable() {

        for (
                ItemDisplay cloud :
                activeClouds.values()
        ) {

            if (!cloud.isDead()) {
                cloud.remove();
            }
        }

        for (
                List<Interaction> hitboxes :
                cloudHitboxes.values()
        ) {

            for (
                    Interaction hitbox :
                    hitboxes
            ) {

                if (!hitbox.isDead()) {
                    hitbox.remove();
                }
            }
        }

        for (
                List<ItemDisplay> trail :
                goldTrails.values()
        ) {

            for (
                    ItemDisplay gold :
                    trail
            ) {

                if (!gold.isDead()) {
                    gold.remove();
                }
            }
        }

        for (
                Integer taskId :
                boostTasks.values()
        ) {

            Bukkit.getScheduler()
                    .cancelTask(taskId);
        }

        activeClouds.clear();
        cloudHitboxes.clear();
        goldTrails.clear();
        lastTrailLocations.clear();
        boostCooldowns.clear();
        cloudRechargeCooldowns.clear();
        boostTasks.clear();

        saveKintoUnCount();

        getLogger().info(
                "KintoUn wurde beendet!"
        );
    }

    private void registerKintoUnRecipe() {

        ItemStack kintoUn =
                createKintoUnItem();

        ShapedRecipe recipe =
                new ShapedRecipe(
                        recipeKey,
                        kintoUn
                );

        recipe.shape(
                "YGY",
                "NAN",
                "BBB"
        );

        recipe.setIngredient(
                'Y',
                Material.YELLOW_WOOL
        );

        recipe.setIngredient(
                'G',
                Material.GOLD_BLOCK
        );

        recipe.setIngredient(
                'N',
                Material.NETHER_STAR
        );

        recipe.setIngredient(
                'A',
                Material.ENCHANTED_GOLDEN_APPLE
        );

        recipe.setIngredient(
                'B',
                Material.BEACON
        );

        getServer().addRecipe(
                recipe
        );
    }

    private boolean isKintoUnRecipe(
            CraftItemEvent event
    ) {

        if (
                !(event.getRecipe()
                        instanceof Keyed keyed)
        ) {

            return false;
        }

        return recipeKey.equals(
                keyed.getKey()
        );
    }

    @SuppressWarnings("deprecation")
    private void registerKintoUnAchievement() {

        String advancementJson =
                """
                {
                  "display": {
                    "icon": {
                      "id": "minecraft:yellow_wool"
                    },
                    "title": {
                      "text": "Kinto-Un"
                    },
                    "description": {
                      "text": "Erhalte deine erste Kinto-Un."
                    },
                    "frame": "goal",
                    "show_toast": true,
                    "announce_to_chat": false,
                    "hidden": false
                  },
                  "criteria": {
                    "kinto_un": {
                      "trigger": "minecraft:impossible"
                    }
                  }
                }
                """;

        Advancement advancement =
                Bukkit.getUnsafe()
                        .loadAdvancement(
                                achievementKey,
                                advancementJson
                        );

        if (advancement == null) {

            getLogger().warning(
                    "Das Kinto-Un Achievement konnte nicht registriert werden!"
            );

        } else {

            getLogger().info(
                    "Kinto-Un Achievement wurde registriert!"
            );
        }
    }

    @EventHandler
    public void onCraft(
            CraftItemEvent event
    ) {

        if (!isKintoUnRecipe(event)) {
            return;
        }

        if (kintoUnCount >= MAX_KINTO_UNS) {

            event.setCancelled(true);

            if (
                    event.getWhoClicked()
                            instanceof Player player
            ) {

                player.sendMessage(
                        "§cEs gibt bereits 10 Kinto-Uns auf dem Server!"
                );
            }

            return;
        }

        if (event.isShiftClick()) {

            event.setCancelled(true);

            if (
                    event.getWhoClicked()
                            instanceof Player player
            ) {

                player.sendMessage(
                        "§cKinto-Un muss einzeln hergestellt werden!"
                );
            }
        }
    }

    @EventHandler
    public void onItemCrafted(
            ItemCraftedEvent event
    ) {

        ItemStack crafted =
                event.getCraftedItem();

        if (!isKintoUn(crafted)) {
            return;
        }

        if (kintoUnCount >= MAX_KINTO_UNS) {
            return;
        }

        kintoUnCount++;

        saveKintoUnCount();

        Player player =
                event.getPlayer();

        giveKintoUnAchievement(
                player
        );

        int remaining =
                MAX_KINTO_UNS
                        - kintoUnCount;

        if (remaining == 0) {

            Bukkit.broadcastMessage(
                    "§c"
                            + player.getName()
                            + " hat eine Kinto-Un erhalten! "
                            + "Keine Kinto-Uns mehr übrig."
            );

        } else if (remaining == 1) {

            Bukkit.broadcastMessage(
                    "§c"
                            + player.getName()
                            + " hat eine Kinto-Un erhalten! "
                            + "1 Kinto-Un übrig."
            );

        } else {

            Bukkit.broadcastMessage(
                    "§c"
                            + player.getName()
                            + " hat eine Kinto-Un erhalten! "
                            + remaining
                            + " Kinto-Uns übrig."
            );
        }

        player.sendMessage(
                "§6Kinto-Un hergestellt! §7("
                        + kintoUnCount
                        + "/"
                        + MAX_KINTO_UNS
                        + ")"
        );
    }

    private void giveKintoUnAchievement(
            Player player
    ) {

        Advancement advancement =
                Bukkit.getAdvancement(
                        achievementKey
                );

        if (advancement == null) {

            getLogger().warning(
                    "Kinto-Un Achievement nicht gefunden!"
            );

            return;
        }

        var progress =
                player.getAdvancementProgress(
                        advancement
                );

        if (progress.isDone()) {
            return;
        }

        for (
                String criterion :
                progress.getRemainingCriteria()
        ) {

            progress.awardCriteria(
                    criterion
            );
        }
    }

    @EventHandler
    public void onPlayerInteract(
            PlayerInteractEvent event
    ) {

        if (
                event.getHand()
                        != EquipmentSlot.HAND
        ) {
            return;
        }

        if (
                event.getAction()
                        != Action.RIGHT_CLICK_AIR
                        && event.getAction()
                        != Action.RIGHT_CLICK_BLOCK
        ) {

            return;
        }

        ItemStack item =
                event.getItem();

        if (!isKintoUn(item)) {
            return;
        }

        Player player =
                event.getPlayer();

        UUID uuid =
                player.getUniqueId();

        if (activeClouds.containsKey(uuid)) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long recharge =
                cloudRechargeCooldowns
                        .getOrDefault(
                                uuid,
                                0L
                        );

        if (now < recharge) {

            long remaining =
                    (recharge - now + 999)
                            / 1000;

            player.sendMessage(
                    "§cDeine Kinto-Un lädt noch "
                            + remaining
                            + " Sekunden."
            );

            event.setCancelled(true);

            return;
        }

        Location location =
                player.getLocation()
                        .clone();

        location.setY(
                player.getLocation().getY()
                        - 1.2
        );

        ItemDisplay cloud =
                player.getWorld()
                        .spawn(
                                location,
                                ItemDisplay.class
                        );

        cloud.setItemStack(
                createKintoUnItem()
        );

        cloud.setItemDisplayTransform(
                ItemDisplay.ItemDisplayTransform.FIXED
        );

        cloud.setTransformation(
                new Transformation(
                        new Vector3f(
                                0f,
                                0f,
                                0f
                        ),
                        new AxisAngle4f(
                                0f,
                                0f,
                                0f,
                                1f
                        ),
                        new Vector3f(
                                CLOUD_MODEL_SCALE,
                                CLOUD_MODEL_SCALE,
                                CLOUD_MODEL_SCALE
                        ),
                        new AxisAngle4f(
                                0f,
                                0f,
                                0f,
                                1f
                        )
                )
        );

        cloud.setInvulnerable(true);

        activeClouds.put(
                uuid,
                cloud
        );

        createCloudHitboxes(
                player
        );

        cloud.addPassenger(
                player
        );

        player.setAllowFlight(true);
        player.setFlying(true);

        player.setFlySpeed(
                0.20f
        );

        player.getInventory()
                .setItemInMainHand(null);

        event.setCancelled(true);

        goldTrails.put(
                uuid,
                new ArrayList<>()
        );

        lastTrailLocations.put(
                uuid,
                player.getLocation()
                        .clone()
        );

        player.sendActionBar(
                "§6Drop Taste für Boost"
        );
    }

    @EventHandler
    public void onCloudRightClick(
            PlayerInteractEntityEvent event
    ) {

        Player player =
                event.getPlayer();

        Entity clicked =
                event.getRightClicked();

        if (!(clicked instanceof Interaction hitbox)) {
            return;
        }

        UUID ownerUUID =
                findHitboxOwner(hitbox);

        if (ownerUUID == null) {
            return;
        }

        if (!ownerUUID.equals(
                player.getUniqueId()
        )) {

            return;
        }

        event.setCancelled(true);

        stopBoost(ownerUUID);

        ItemDisplay cloud =
                activeClouds.get(ownerUUID);

        if (cloud != null) {

            cloud.removePassenger(
                    player
            );
        }

        player.setFlying(false);
        player.setAllowFlight(false);

        player.setFlySpeed(
                0.10f
        );

        removeGoldTrail(
                ownerUUID
        );

        removeCloudHitboxes(
                ownerUUID
        );

        giveKintoUn(
                player
        );

        if (cloud != null
                && !cloud.isDead()) {

            cloud.remove();
        }

        activeClouds.remove(
                ownerUUID
        );

        player.sendActionBar(
                "§6Kinto-Un eingepackt!"
        );
    }

    @EventHandler
    public void onCloudDamage(
            EntityDamageByEntityEvent event
    ) {

        if (
                !(event.getEntity()
                        instanceof Interaction hitbox)
        ) {
            return;
        }

        UUID ownerUUID =
                findHitboxOwner(hitbox);

        if (ownerUUID == null) {
            return;
        }

        event.setCancelled(true);

        if (
                !(event.getDamager()
                        instanceof Player attacker)
        ) {
            return;
        }

        Player owner =
                Bukkit.getPlayer(
                        ownerUUID
                );

        if (owner == null) {

            removeCloudCompletely(
                    ownerUUID
            );

            return;
        }

        stopBoost(ownerUUID);

        removeGoldTrail(
                ownerUUID
        );

        ItemDisplay cloud =
                activeClouds.get(
                        ownerUUID
                );

        if (cloud != null) {

            cloud.removePassenger(
                    owner
            );

            if (!cloud.isDead()) {
                cloud.remove();
            }
        }

        owner.setFlying(false);
        owner.setAllowFlight(false);

        owner.setFlySpeed(
                0.10f
        );

        giveKintoUn(
                owner
        );

        activeClouds.remove(
                ownerUUID
        );

        removeCloudHitboxes(
                ownerUUID
        );

        cloudRechargeCooldowns.put(
                ownerUUID,
                System.currentTimeMillis()
                        + CLOUD_RECHARGE
        );

        owner.sendMessage(
                "§cDeine Kinto-Un wurde von "
                        + attacker.getName()
                        + " zerstört!"
        );

        owner.sendMessage(
                "§7Sie lädt 30 Sekunden auf."
        );

        owner.sendActionBar(
                "§cKinto-Un zerstört! §730s Cooldown"
        );
    }

    @EventHandler
    public void onDrop(
            PlayerDropItemEvent event
    ) {

        Player player =
                event.getPlayer();

        UUID uuid =
                player.getUniqueId();

        if (!activeClouds.containsKey(uuid)) {
            return;
        }

        event.setCancelled(true);

        long now =
                System.currentTimeMillis();

        long nextBoost =
                boostCooldowns.getOrDefault(
                        uuid,
                        0L
                );

        if (now < nextBoost) {

            long remaining =
                    (nextBoost - now + 999)
                            / 1000;

            player.sendActionBar(
                    "§cBoost lädt noch "
                            + remaining
                            + "s"
            );

            return;
        }

        boostCooldowns.put(
                uuid,
                now + BOOST_COOLDOWN
        );

        startBoost(
                player
        );
    }

    private void startBoost(
            Player player
    ) {

        UUID uuid =
                player.getUniqueId();

        stopBoost(uuid);

        player.sendActionBar(
                "§6⚡ BOOST!"
        );

        final int[] ticks =
                {0};

        int taskId =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                this,
                                () -> {

                                    if (
                                            !player.isOnline()
                                                    || !activeClouds
                                                    .containsKey(uuid)
                                    ) {

                                        stopBoost(uuid);
                                        return;
                                    }

                                    if (
                                            ticks[0]
                                                    >= BOOST_DURATION_TICKS
                                    ) {

                                        stopBoost(uuid);

                                        player.sendActionBar(
                                                "§6Drop Taste für Boost"
                                        );

                                        return;
                                    }

                                    Location location =
                                            player.getLocation();

                                    Vector direction =
                                            location
                                                    .getDirection()
                                                    .normalize();

                                    Vector velocity =
                                            direction.multiply(
                                                    BOOST_SPEED
                                            );

                                    if (
                                            location.getY()
                                                    >= MAX_HEIGHT - 1
                                                    && velocity.getY() > 0
                                    ) {

                                        velocity.setY(
                                                0
                                        );
                                    }

                                    player.setVelocity(
                                            velocity
                                    );

                                    ticks[0]++;

                                },
                                0L,
                                1L
                        )
                        .getTaskId();

        boostTasks.put(
                uuid,
                taskId
        );
    }

    private void stopBoost(
            UUID uuid
    ) {

        Integer taskId =
                boostTasks.remove(
                        uuid
                );

        if (taskId != null) {

            Bukkit.getScheduler()
                    .cancelTask(
                            taskId
                    );
        }
    }

    private void createCloudHitboxes(
            Player player
    ) {

        UUID uuid =
                player.getUniqueId();

        removeCloudHitboxes(
                uuid
        );

        List<Interaction> hitboxes =
                new ArrayList<>();

        Location base =
                player.getLocation()
                        .clone();

        base.setY(
                player.getLocation().getY()
                        - 1.2
        );

        /*
         * Mittlere Hitbox
         */
        createHitbox(
                player,
                hitboxes,
                base.clone(),
                1.8f,
                0.8f
        );

        /*
         * Linke Wolkenhälfte
         */
        createHitbox(
                player,
                hitboxes,
                base.clone().add(
                        -0.85,
                        0.05,
                        0
                ),
                0.9f,
                0.7f
        );

        /*
         * Rechte Wolkenhälfte
         */
        createHitbox(
                player,
                hitboxes,
                base.clone().add(
                        0.85,
                        0.05,
                        0
                ),
                0.9f,
                0.7f
        );

        /*
         * Vorderer Teil
         */
        createHitbox(
                player,
                hitboxes,
                base.clone().add(
                        0,
                        0.05,
                        0.65
                ),
                1.4f,
                0.65f
        );

        /*
         * Hinterer Teil
         */
        createHitbox(
                player,
                hitboxes,
                base.clone().add(
                        0,
                        0.05,
                        -0.65
                ),
                1.4f,
                0.65f
        );

        cloudHitboxes.put(
                uuid,
                hitboxes
        );
    }

    private void createHitbox(
            Player player,
            List<Interaction> hitboxes,
            Location location,
            float width,
            float height
    ) {

        Interaction hitbox =
                player.getWorld()
                        .spawn(
                                location,
                                Interaction.class
                        );

        hitbox.setInteractionWidth(
                width
        );

        hitbox.setInteractionHeight(
                height
        );

        hitbox.setResponsive(
                true
        );

        hitboxes.add(
                hitbox
        );
    }

    private void updateCloudHitboxes(
            Player player
    ) {

        UUID uuid =
                player.getUniqueId();

        List<Interaction> hitboxes =
                cloudHitboxes.get(uuid);

        if (
                hitboxes == null
                        || hitboxes.size() != 5
        ) {
            return;
        }

        Location base =
                player.getLocation()
                        .clone();

        base.setY(
                player.getLocation().getY()
                        - 1.2
        );

        hitboxes.get(0).teleport(
                base
        );

        hitboxes.get(1).teleport(
                base.clone().add(
                        -0.85,
                        0.05,
                        0
                )
        );

        hitboxes.get(2).teleport(
                base.clone().add(
                        0.85,
                        0.05,
                        0
                )
        );

        hitboxes.get(3).teleport(
                base.clone().add(
                        0,
                        0.05,
                        0.65
                )
        );

        hitboxes.get(4).teleport(
                base.clone().add(
                        0,
                        0.05,
                        -0.65
                )
        );
    }

    private UUID findHitboxOwner(
            Interaction hitbox
    ) {

        for (
                Map.Entry<UUID, List<Interaction>> entry :
                cloudHitboxes.entrySet()
        ) {

            for (
                    Interaction stored :
                    entry.getValue()
            ) {

                if (
                        stored.getUniqueId()
                                .equals(
                                        hitbox.getUniqueId()
                                )
                ) {

                    return entry.getKey();
                }
            }
        }

        return null;
    }

    private void removeCloudHitboxes(
            UUID uuid
    ) {

        List<Interaction> hitboxes =
                cloudHitboxes.remove(
                        uuid
                );

        if (hitboxes == null) {
            return;
        }

        for (
                Interaction hitbox :
                hitboxes
        ) {

            if (!hitbox.isDead()) {
                hitbox.remove();
            }
        }
    }

    private void updateGoldTrail(
            Player player
    ) {

        UUID uuid =
                player.getUniqueId();

        Location current =
                player.getLocation()
                        .clone();

        Location last =
                lastTrailLocations.get(
                        uuid
                );

        if (last == null) {

            lastTrailLocations.put(
                    uuid,
                    current
            );

            return;
        }

        if (
                current.distanceSquared(last)
                        < 0.04
        ) {

            removeLastTrailBlock(
                    uuid
            );

            return;
        }

        Vector direction =
                current.toVector()
                        .subtract(
                                last.toVector()
                        );

        if (
                direction.lengthSquared()
                        == 0
        ) {

            return;
        }

        direction.normalize();

        lastTrailLocations.put(
                uuid,
                current.clone()
        );

        List<ItemDisplay> trail =
                goldTrails.get(
                        uuid
                );

        if (trail == null) {

            trail =
                    new ArrayList<>();

            goldTrails.put(
                    uuid,
                    trail
            );
        }

        Location trailLocation =
                current.clone()
                        .subtract(
                                direction.multiply(
                                        TRAIL_DISTANCE
                                )
                        );

        trailLocation.setY(
                current.getY()
                        - 1.2
        );

        ItemDisplay gold =
                player.getWorld()
                        .spawn(
                                trailLocation,
                                ItemDisplay.class
                        );

        gold.setItemStack(
                new ItemStack(
                        Material.GOLD_BLOCK
                )
        );

        gold.setItemDisplayTransform(
                ItemDisplay.ItemDisplayTransform.FIXED
        );

        gold.setTransformation(
                new Transformation(
                        new Vector3f(
                                0f,
                                0f,
                                0f
                        ),
                        new AxisAngle4f(
                                0f,
                                0f,
                                0f,
                                1f
                        ),
                        new Vector3f(
                                TRAIL_SIZE,
                                TRAIL_SIZE,
                                TRAIL_SIZE
                        ),
                        new AxisAngle4f(
                                0f,
                                0f,
                                0f,
                                1f
                        )
                )
        );

        gold.setInvulnerable(
                true
        );

        trail.add(
                gold
        );

        while (
                trail.size()
                        > MAX_TRAIL_BLOCKS
        ) {

            ItemDisplay oldest =
                    trail.remove(0);

            if (!oldest.isDead()) {
                oldest.remove();
            }
        }

        updateTrailPositions(
                player,
                direction
        );
    }

    private void updateTrailPositions(
            Player player,
            Vector direction
    ) {

        List<ItemDisplay> trail =
                goldTrails.get(
                        player.getUniqueId()
                );

        if (
                trail == null
                        || trail.isEmpty()
        ) {

            return;
        }

        Location current =
                player.getLocation()
                        .clone();

        current.setY(
                player.getLocation().getY()
                        - 1.2
        );

        for (
                int i = 0;
                i < trail.size();
                i++
        ) {

            ItemDisplay gold =
                    trail.get(i);

            if (gold.isDead()) {
                continue;
            }

            double distance =
                    (i + 1)
                            * TRAIL_DISTANCE;

            Location position =
                    current.clone()
                            .subtract(
                                    direction.multiply(
                                            distance
                                    )
                            );

            gold.teleport(
                    position
            );
        }
    }

    private void removeLastTrailBlock(
            UUID uuid
    ) {

        List<ItemDisplay> trail =
                goldTrails.get(
                        uuid
                );

        if (
                trail == null
                        || trail.isEmpty()
        ) {

            return;
        }

        ItemDisplay gold =
                trail.remove(0);

        if (!gold.isDead()) {
            gold.remove();
        }
    }

    private void removeGoldTrail(
            UUID uuid
    ) {

        List<ItemDisplay> trail =
                goldTrails.remove(
                        uuid
                );

        if (trail != null) {

            for (
                    ItemDisplay gold :
                    trail
            ) {

                if (!gold.isDead()) {
                    gold.remove();
                }
            }
        }

        lastTrailLocations.remove(
                uuid
        );
    }

    @EventHandler
    public void onPlayerMove(
            PlayerMoveEvent event
    ) {

        Player player =
                event.getPlayer();

        UUID uuid =
                player.getUniqueId();

        if (!activeClouds.containsKey(uuid)) {
            return;
        }

        Location to =
                event.getTo();

        if (to == null) {
            return;
        }

        if (to.getY() > MAX_HEIGHT) {

            Location limited =
                    to.clone();

            limited.setY(
                    MAX_HEIGHT
            );

            event.setTo(
                    limited
            );
        }
    }

    @EventHandler
    public void onPlayerDeath(
            PlayerDeathEvent event
    ) {

        Player player =
                event.getEntity();

        UUID uuid =
                player.getUniqueId();

        ItemDisplay cloud =
                activeClouds.remove(
                        uuid
                );

        boolean wasRiding =
                cloud != null;

        boolean wasHolding =
                hasKintoUnInInventory(
                        player
                );

        if (!wasRiding && !wasHolding) {
            return;
        }

        stopBoost(uuid);

        removeGoldTrail(uuid);

        removeCloudHitboxes(uuid);

        if (
                cloud != null
                        && !cloud.isDead()
        ) {

            cloud.remove();
        }

        /*
         * Wenn der Spieler nur eine Kinto-Un
         * im Inventar hatte, wird diese entfernt.
         */
        if (!wasRiding && wasHolding) {

            removeOneKintoUnFromInventory(
                    player
            );
        }

        if (kintoUnCount > 0) {

            kintoUnCount--;

            saveKintoUnCount();

            int remaining =
                    MAX_KINTO_UNS
                            - kintoUnCount;

            Bukkit.broadcastMessage(
                    "§c"
                            + player.getName()
                            + " ist mit seiner Kinto-Un gestorben!"
            );

            Bukkit.broadcastMessage(
                    "§cNew Kinto-Un Is Craftable: "
                            + remaining
                            + " Left"
            );
        }

        boostCooldowns.remove(
                uuid
        );

        cloudRechargeCooldowns.remove(
                uuid
        );

        player.setFlying(false);
        player.setAllowFlight(false);

        player.sendMessage(
                "§6Deine Kinto-Un ist verschwunden."
        );

        player.sendMessage(
                "§7Du kannst jetzt wieder eine neue craften."
        );
    }

    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event
    ) {

        UUID uuid =
                event.getPlayer()
                        .getUniqueId();

        ItemDisplay cloud =
                activeClouds.remove(
                        uuid
                );

        if (
                cloud != null
                        && !cloud.isDead()
        ) {

            cloud.remove();
        }

        stopBoost(uuid);

        removeGoldTrail(uuid);

        removeCloudHitboxes(uuid);

        boostCooldowns.remove(
                uuid
        );

        cloudRechargeCooldowns.remove(
                uuid
        );
    }

    private void removeCloudCompletely(
            UUID uuid
    ) {

        ItemDisplay cloud =
                activeClouds.remove(
                        uuid
                );

        if (
                cloud != null
                        && !cloud.isDead()
        ) {

            cloud.remove();
        }

        stopBoost(uuid);

        removeGoldTrail(uuid);

        removeCloudHitboxes(uuid);
    }

    private void giveKintoUn(
            Player player
    ) {

        ItemStack kintoUn =
                createKintoUnItem();

        HashMap<Integer, ItemStack> leftover =
                player.getInventory()
                        .addItem(
                                kintoUn
                        );

        for (
                ItemStack item :
                leftover.values()
        ) {

            player.getWorld()
                    .dropItemNaturally(
                            player.getLocation(),
                            item
                    );
        }
    }

    private ItemStack createKintoUnItem() {

        ItemStack kintoUn =
                new ItemStack(
                        Material.YELLOW_WOOL
                );

        kintoUn.setAmount(
                1
        );

        kintoUn.setData(
                DataComponentTypes.MAX_STACK_SIZE,
                1
        );

        /*
         * Das ist das neue Minecraft/Paper-26.2
         * Item-Model-System.
         *
         * minecraft:kinto_un
         * -> assets/minecraft/items/kinto_un.json
         * -> minecraft:item/kinto_un
         * -> assets/minecraft/models/item/kinto_un.json
         */
        kintoUn.setData(
                DataComponentTypes.ITEM_MODEL,
                NamespacedKey.minecraft(
                        "kinto_un"
                )
        );

        ItemMeta meta =
                kintoUn.getItemMeta();

        meta.setDisplayName(
                "§6Kinto-Un"
        );

        meta.getPersistentDataContainer()
                .set(
                        kintoUnKey,
                        PersistentDataType.BYTE,
                        (byte) 1
                );

        kintoUn.setItemMeta(
                meta
        );

        return kintoUn;
    }

    private boolean isKintoUn(
            ItemStack item
    ) {

        if (item == null) {
            return false;
        }

        if (
                item.getType()
                        != Material.YELLOW_WOOL
        ) {

            return false;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return false;
        }

        Byte value =
                meta.getPersistentDataContainer()
                        .get(
                                kintoUnKey,
                                PersistentDataType.BYTE
                        );

        return value != null
                && value == (byte) 1;
    }

    private boolean hasKintoUnInInventory(
            Player player
    ) {

        for (
                ItemStack item :
                player.getInventory()
                        .getContents()
        ) {

            if (isKintoUn(item)) {
                return true;
            }
        }

        return false;
    }

    private void removeOneKintoUnFromInventory(
            Player player
    ) {

        for (
                int slot = 0;
                slot < player.getInventory()
                        .getSize();
                slot++
        ) {

            ItemStack item =
                    player.getInventory()
                            .getItem(slot);

            if (!isKintoUn(item)) {
                continue;
            }

            player.getInventory()
                    .setItem(
                            slot,
                            null
                    );

            return;
        }
    }

    private void saveKintoUnCount() {

        getConfig().set(
                "kinto-un-count",
                kintoUnCount
        );

        saveConfig();
    }
}
