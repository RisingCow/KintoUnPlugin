package de.kintoun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
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
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KintoUnPlugin extends JavaPlugin implements Listener {

    // =========================================================
    // EINSTELLUNGEN
    // =========================================================

    private static final double MAX_HEIGHT = 50.0;

    // Boost
    private static final long BOOST_COOLDOWN = 30_000L;
    private static final int BOOST_DURATION_TICKS = 20;
    private static final double BOOST_SPEED = 1.8;

    // Nach Zerstörung
    private static final long CLOUD_RECHARGE = 30_000L;

    // Goldspur
    private static final double TRAIL_DISTANCE = 0.65;
    private static final int MAX_TRAIL_BLOCKS = 7;

    // Größe der kleinen Goldwürfel
    private static final float TRAIL_SIZE = 0.55f;

    // =========================================================
    // DATEN
    // =========================================================

    private NamespacedKey kintoUnKey;

    private final Map<UUID, FallingBlock> activeClouds =
            new HashMap<>();

    private final Map<UUID, Long> boostCooldowns =
            new HashMap<>();

    private final Map<UUID, Long> cloudRechargeCooldowns =
            new HashMap<>();

    private final Map<UUID, List<FallingBlock>> goldTrails =
            new HashMap<>();

    private final Map<UUID, Location> lastTrailLocations =
            new HashMap<>();

    private final Map<UUID, Integer> boostTasks =
            new HashMap<>();

    private int kintoUnCount;

    // =========================================================
    // START
    // =========================================================

    @Override
    public void onEnable() {

        kintoUnKey =
                new NamespacedKey(this, "kinto_un_item");

        saveDefaultConfig();

        kintoUnCount =
                getConfig().getInt("kinto-un-count", 0);

        registerKintoUnRecipe();

        getServer()
                .getPluginManager()
                .registerEvents(this, this);

        // Haupt-Loop
        Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {

                    for (Map.Entry<UUID, FallingBlock> entry
                            : activeClouds.entrySet()) {

                        Player player =
                                Bukkit.getPlayer(entry.getKey());

                        FallingBlock cloud =
                                entry.getValue();

                        if (player == null ||
                                !player.isOnline() ||
                                cloud.isDead()) {
                            continue;
                        }

                        // Wolke unter dem Spieler
                        Location cloudLocation =
                                player.getLocation().clone();

                        cloudLocation.setY(
                                player.getLocation().getY() - 1.2
                        );

                        cloud.teleport(cloudLocation);

                        // Goldspur
                        updateGoldTrail(player);

                        // Actionbar
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
                "Kinto-Uns: " +
                        kintoUnCount +
                        "/10"
        );
    }

    // =========================================================
    // STOP
    // =========================================================

    @Override
    public void onDisable() {

        for (FallingBlock cloud
                : activeClouds.values()) {

            if (!cloud.isDead()) {
                cloud.remove();
            }
        }

        for (List<FallingBlock> trail
                : goldTrails.values()) {

            for (FallingBlock gold : trail) {

                if (!gold.isDead()) {
                    gold.remove();
                }
            }
        }

        for (Integer taskId : boostTasks.values()) {

            Bukkit.getScheduler()
                    .cancelTask(taskId);
        }

        activeClouds.clear();
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

    // =========================================================
    // REZEPT
    // =========================================================

    private void registerKintoUnRecipe() {

        ItemStack kintoUn =
                createKintoUnItem();

        NamespacedKey recipeKey =
                new NamespacedKey(
                        this,
                        "kinto_un"
                );

        ShapedRecipe recipe =
                new ShapedRecipe(
                        recipeKey,
                        kintoUn
                );

        /*
         *
         * Y G Y
         * N A N
         * B B B
         *
         */

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

        getServer().addRecipe(recipe);
    }

    // =========================================================
    // CRAFTING
    // =========================================================

    @EventHandler
    public void onCraft(CraftItemEvent event) {

        ItemStack result =
                event.getRecipe().getResult();

        // Nur unser Kinto-Un-Rezept
        if (!isKintoUn(result)) {
            return;
        }

        // Maximal 10
        if (kintoUnCount >= 10) {

            event.setCancelled(true);

            if (event.getWhoClicked()
                    instanceof Player player) {

                player.sendMessage(
                        "§cEs gibt bereits 10 Kinto-Uns auf dem Server!"
                );
            }

            return;
        }

        /*
         * Shift-Klick wird verhindert.
         *
         * Dadurch kann man nicht mehrere Kinto-Uns
         * mit einem einzigen Shift-Klick herstellen.
         */
        if (event.isShiftClick()) {

            event.setCancelled(true);

            if (event.getWhoClicked()
                    instanceof Player player) {

                player.sendMessage(
                        "§cKinto-Un muss einzeln hergestellt werden!"
                );
            }

            return;
        }

        kintoUnCount++;

        saveKintoUnCount();

        if (event.getWhoClicked()
                instanceof Player player) {

            player.sendMessage(
                    "§6Kinto-Un hergestellt! §7(" +
                            kintoUnCount +
                            "/10)"
            );
        }
    }

    // =========================================================
    // KINTO-UN STARTEN
    // =========================================================

    @EventHandler
    public void onPlayerInteract(
            PlayerInteractEvent event) {

        if (event.getHand()
                != EquipmentSlot.HAND) {

            return;
        }

        if (event.getAction()
                != Action.RIGHT_CLICK_AIR &&
                event.getAction()
                != Action.RIGHT_CLICK_BLOCK) {

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

        // Schon auf einer Wolke
        if (activeClouds.containsKey(uuid)) {
            return;
        }

        // Cooldown nach Zerstörung
        long now =
                System.currentTimeMillis();

        long recharge =
                cloudRechargeCooldowns
                        .getOrDefault(uuid, 0L);

        if (now < recharge) {

            long remaining =
                    (recharge - now + 999) / 1000;

            player.sendMessage(
                    "§cDeine Kinto-Un lädt noch " +
                            remaining +
                            " Sekunden."
            );

            event.setCancelled(true);

            return;
        }

        // Wolke erstellen
        Location location =
                player.getLocation().clone();

        location.setY(
                player.getLocation().getY() - 1.2
        );

        FallingBlock cloud =
                player.getWorld()
                        .spawnFallingBlock(
                                location,
                                Material.YELLOW_WOOL
                                        .createBlockData()
                        );

        cloud.setGravity(false);
        cloud.setDropItem(false);
        cloud.setHurtEntities(false);

        // Spieler auf die Wolke
        cloud.addPassenger(player);

        activeClouds.put(
                uuid,
                cloud
        );

        // Fliegen
        player.setAllowFlight(true);
        player.setFlying(true);

        // Fluggeschwindigkeit ungefähr Elytra-Gefühl
        player.setFlySpeed(0.20f);

        // Item aus der Hand entfernen
        player.getInventory()
                .setItemInMainHand(null);

        event.setCancelled(true);

        // Goldspur vorbereiten
        goldTrails.put(
                uuid,
                new ArrayList<>()
        );

        lastTrailLocations.put(
                uuid,
                player.getLocation().clone()
        );

        player.sendActionBar(
                "§6Drop Taste für Boost"
        );
    }

    // =========================================================
    // RECHTSKLICK AUF EIGENE WOLKE
    // =========================================================

    @EventHandler
    public void onCloudRightClick(
            PlayerInteractEntityEvent event) {

        Player player =
                event.getPlayer();

        Entity clicked =
                event.getRightClicked();

        if (!(clicked instanceof FallingBlock cloud)) {
            return;
        }

        UUID uuid =
                player.getUniqueId();

        FallingBlock ownCloud =
                activeClouds.get(uuid);

        if (ownCloud == null) {
            return;
        }

        if (!ownCloud.getUniqueId()
                .equals(cloud.getUniqueId())) {

            return;
        }

        event.setCancelled(true);

        stopBoost(uuid);

        cloud.removePassenger(player);

        player.setFlying(false);
        player.setAllowFlight(false);

        player.setFlySpeed(0.10f);

        removeGoldTrail(uuid);

        // Kinto-Un zurückgeben
        giveKintoUn(player);

        cloud.remove();

        activeClouds.remove(uuid);

        player.sendActionBar(
                "§6Kinto-Un eingepackt!"
        );
    }

    // =========================================================
    // KINTO-UN WIRD ANGEGRIFFEN
    // =========================================================

    @EventHandler
    public void onCloudDamage(
            EntityDamageByEntityEvent event) {

        if (!(event.getEntity()
                instanceof FallingBlock cloud)) {

            return;
        }

        UUID ownerUUID =
                findCloudOwner(cloud);

        if (ownerUUID == null) {
            return;
        }

        // Nur Spieler können sie zerstören
        if (!(event.getDamager()
                instanceof Player attacker)) {

            event.setCancelled(true);

            return;
        }

        // Schaden selbst verhindern
        event.setCancelled(true);

        Player owner =
                Bukkit.getPlayer(ownerUUID);

        if (owner == null) {

            removeCloudCompletely(ownerUUID);

            return;
        }

        // Boost beenden
        stopBoost(ownerUUID);

        // Goldspur löschen
        removeGoldTrail(ownerUUID);

        // Spieler von der Wolke
        cloud.removePassenger(owner);

        owner.setFlying(false);
        owner.setAllowFlight(false);
        owner.setFlySpeed(0.10f);

        // Kinto-Un zurückgeben
        giveKintoUn(owner);

        // Wolke entfernen
        cloud.remove();

        activeClouds.remove(ownerUUID);

        // 30 Sekunden Cooldown
        cloudRechargeCooldowns.put(
                ownerUUID,
                System.currentTimeMillis()
                        + CLOUD_RECHARGE
        );

        owner.sendMessage(
                "§cDeine Kinto-Un wurde von " +
                        attacker.getName() +
                        " zerstört!"
        );

        owner.sendMessage(
                "§7Sie lädt 30 Sekunden auf."
        );

        owner.sendActionBar(
                "§cKinto-Un zerstört! §730s Cooldown"
        );
    }

    // =========================================================
    // DROP-TASTE
    // =========================================================

    @EventHandler
    public void onDrop(
            PlayerDropItemEvent event) {

        Player player =
                event.getPlayer();

        UUID uuid =
                player.getUniqueId();

        // Nur auf Kinto-Un
        if (!activeClouds.containsKey(uuid)) {
            return;
        }

        // Item darf nicht gedroppt werden
        event.setCancelled(true);

        long now =
                System.currentTimeMillis();

        long nextBoost =
                boostCooldowns
                        .getOrDefault(uuid, 0L);

        if (now < nextBoost) {

            long remaining =
                    (nextBoost - now + 999) / 1000;

            player.sendActionBar(
                    "§cBoost lädt noch " +
                            remaining +
                            "s"
            );

            return;
        }

        boostCooldowns.put(
                uuid,
                now + BOOST_COOLDOWN
        );

        startBoost(player);
    }

    // =========================================================
    // BOOST
    // =========================================================

    private void startBoost(
            Player player) {

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

                                    if (!player.isOnline()
                                            ||
                                            !activeClouds
                                                    .containsKey(uuid)) {

                                        stopBoost(uuid);
                                        return;
                                    }

                                    if (ticks[0]
                                            >= BOOST_DURATION_TICKS) {

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
                                            direction
                                                    .multiply(
                                                            BOOST_SPEED
                                                    );

                                    // Niemals über Y=50
                                    if (location.getY()
                                            >= MAX_HEIGHT - 1
                                            &&
                                            velocity.getY() > 0) {

                                        velocity.setY(0);
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

    private void stopBoost(UUID uuid) {

        Integer taskId =
                boostTasks.remove(uuid);

        if (taskId != null) {

            Bukkit.getScheduler()
                    .cancelTask(taskId);
        }
    }

    // =========================================================
    // GOLDSPUR
    // =========================================================

    private void updateGoldTrail(
            Player player) {

        UUID uuid =
                player.getUniqueId();

        Location current =
                player.getLocation().clone();

        Location last =
                lastTrailLocations.get(uuid);

        if (last == null) {

            lastTrailLocations.put(
                    uuid,
                    current
            );

            return;
        }

        // Spieler steht still
        if (current.distanceSquared(last)
                < 0.04) {

            removeLastTrailBlock(uuid);

            return;
        }

        // Bewegungsrichtung
        Vector direction =
                current.toVector()
                        .subtract(
                                last.toVector()
                        );

        if (direction.lengthSquared()
                == 0) {

            return;
        }

        direction.normalize();

        lastTrailLocations.put(
                uuid,
                current.clone()
        );

        List<FallingBlock> trail =
                goldTrails.get(uuid);

        if (trail == null) {

            trail =
                    new ArrayList<>();

            goldTrails.put(
                    uuid,
                    trail
            );
        }

        // Position hinter dem Spieler
        Location trailLocation =
                current.clone()
                        .subtract(
                                direction.multiply(
                                        TRAIL_DISTANCE
                                )
                        );

        trailLocation.setY(
                current.getY() - 1.2
        );

        /*
         * GOLD BLOCK
         *
         * Die Spur benutzt einen kleinen Goldwürfel.
         * Er ist nur ca. 55 % so groß wie ein normaler Block.
         */
        FallingBlock gold =
                player.getWorld()
                        .spawnFallingBlock(
                                trailLocation,
                                Material.GOLD_BLOCK
                                        .createBlockData()
                        );

        gold.setGravity(false);
        gold.setDropItem(false);
        gold.setHurtEntities(false);

        /*
         * Hitbox/Größe kleiner machen.
         *
         * Die tatsächliche Darstellung wird durch
         * die Entity-Größe begrenzt.
         */
        gold.setVelocity(new Vector(0, 0, 0));

        trail.add(gold);

        // Maximal 7 Goldwürfel
        while (trail.size()
                > MAX_TRAIL_BLOCKS) {

            FallingBlock oldest =
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
            Vector direction) {

        List<FallingBlock> trail =
                goldTrails.get(
                        player.getUniqueId()
                );

        if (trail == null ||
                trail.isEmpty()) {

            return;
        }

        Location current =
                player.getLocation().clone();

        current.setY(
                player.getLocation().getY()
                        - 1.2
        );

        for (int i = 0;
             i < trail.size();
             i++) {

            FallingBlock gold =
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
                                    direction
                                            .multiply(
                                                    distance
                                            )
                            );

            gold.teleport(position);
        }
    }

    private void removeLastTrailBlock(
            UUID uuid) {

        List<FallingBlock> trail =
                goldTrails.get(uuid);

        if (trail == null ||
                trail.isEmpty()) {

            return;
        }

        FallingBlock gold =
                trail.remove(0);

        if (!gold.isDead()) {
            gold.remove();
        }
    }

    private void removeGoldTrail(
            UUID uuid) {

        List<FallingBlock> trail =
                goldTrails.remove(uuid);

        if (trail != null) {

            for (FallingBlock gold : trail) {

                if (!gold.isDead()) {
                    gold.remove();
                }
            }
        }

        lastTrailLocations.remove(uuid);
    }

    // =========================================================
    // HÖHENLIMIT
    // =========================================================

    @EventHandler
    public void onPlayerMove(
            PlayerMoveEvent event) {

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

            event.setTo(limited);
        }
    }

    // =========================================================
    // TOD
    // =========================================================

    @EventHandler
    public void onPlayerDeath(
            PlayerDeathEvent event) {

        Player player =
                event.getEntity();

        UUID uuid =
                player.getUniqueId();

        FallingBlock cloud =
                activeClouds.remove(uuid);

        if (cloud == null) {
            return;
        }

        stopBoost(uuid);

        removeGoldTrail(uuid);

        if (!cloud.isDead()) {
            cloud.remove();
        }

        /*
         * Die Kinto-Un ist beim Tod verloren.
         *
         * Dadurch wird wieder ein Platz frei.
         */
        if (kintoUnCount > 0) {

            kintoUnCount--;

            saveKintoUnCount();
        }

        boostCooldowns.remove(uuid);
        cloudRechargeCooldowns.remove(uuid);

        player.setFlying(false);
        player.setAllowFlight(false);

        player.sendMessage(
                "§6Deine Kinto-Un ist verschwunden."
        );

        player.sendMessage(
                "§7Du kannst jetzt wieder eine neue craften."
        );
    }

    // =========================================================
    // SPIELER VERLÄSST SERVER
    // =========================================================

    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event) {

        UUID uuid =
                event.getPlayer()
                        .getUniqueId();

        FallingBlock cloud =
                activeClouds.remove(uuid);

        if (cloud != null &&
                !cloud.isDead()) {

            cloud.remove();
        }

        stopBoost(uuid);

        removeGoldTrail(uuid);

        boostCooldowns.remove(uuid);
        cloudRechargeCooldowns.remove(uuid);
    }

    // =========================================================
    // BESITZER DER WOLKE FINDEN
    // =========================================================

    private UUID findCloudOwner(
            FallingBlock cloud) {

        for (Map.Entry<UUID, FallingBlock> entry
                : activeClouds.entrySet()) {

            if (entry.getValue()
                    .getUniqueId()
                    .equals(cloud.getUniqueId())) {

                return entry.getKey();
            }
        }

        return null;
    }

    // =========================================================
    // WOLKE KOMPLETT ENTFERNEN
    // =========================================================

    private void removeCloudCompletely(
            UUID uuid) {

        FallingBlock cloud =
                activeClouds.remove(uuid);

        if (cloud != null &&
                !cloud.isDead()) {

            cloud.remove();
        }

        stopBoost(uuid);

        removeGoldTrail(uuid);
    }

    // =========================================================
    // KINTO-UN GEBEN
    // =========================================================

    private void giveKintoUn(
            Player player) {

        ItemStack kintoUn =
                createKintoUnItem();

        HashMap<Integer, ItemStack> leftover =
                player.getInventory()
                        .addItem(kintoUn);

        // Bei vollem Inventar auf den Boden
        for (ItemStack item
                : leftover.values()) {

            player.getWorld()
                    .dropItemNaturally(
                            player.getLocation(),
                            item
                    );
        }
    }

    // =========================================================
    // KINTO-UN ERSTELLEN
    // =========================================================

    private ItemStack createKintoUnItem() {

        ItemStack kintoUn =
                new ItemStack(
                        Material.YELLOW_WOOL
                );

        // Niemals stapelbar
        kintoUn.setAmount(1);

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

        kintoUn.setItemMeta(meta);

        return kintoUn;
    }

    // =========================================================
    // KINTO-UN ERKENNEN
    // =========================================================

    private boolean isKintoUn(
            ItemStack item) {

        if (item == null) {
            return false;
        }

        if (item.getType()
                != Material.YELLOW_WOOL) {

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

        return value != null &&
                value == (byte) 1;
    }

    // =========================================================
    // CONFIG
    // =========================================================

    private void saveKintoUnCount() {

        getConfig().set(
                "kinto-un-count",
                kintoUnCount
        );

        saveConfig();
    }
}
