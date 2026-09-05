package de.kintoun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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

    private static final double MAX_HEIGHT = 50.0;

    private static final long BOOST_COOLDOWN = 30_000L;
    private static final int BOOST_DURATION_TICKS = 20;
    private static final double BOOST_SPEED = 1.8;

    // Abstand zwischen den Goldblöcken
    private static final double TRAIL_DISTANCE = 1.0;

    // Maximal 7 Goldblöcke
    private static final int MAX_TRAIL_BLOCKS = 7;

    private NamespacedKey kintoUnKey;

    private final Map<UUID, FallingBlock> activeClouds = new HashMap<>();

    private final Map<UUID, Long> boostCooldowns = new HashMap<>();

    // Goldspuren der Spieler
    private final Map<UUID, List<FallingBlock>> goldTrails = new HashMap<>();

    // Letzte Position des Spielers
    private final Map<UUID, Location> lastTrailLocations = new HashMap<>();

    private int kintoUnCount;

    @Override
    public void onEnable() {

        kintoUnKey = new NamespacedKey(this, "kinto_un_item");

        saveDefaultConfig();

        kintoUnCount = getConfig().getInt("kinto-un-count", 0);

        registerKintoUnRecipe();

        getServer().getPluginManager().registerEvents(this, this);

        // Kinto-Un und Goldspur bewegen
        Bukkit.getScheduler().runTaskTimer(this, () -> {

            for (Map.Entry<UUID, FallingBlock> entry : activeClouds.entrySet()) {

                Player player = Bukkit.getPlayer(entry.getKey());
                FallingBlock cloud = entry.getValue();

                if (player == null || !player.isOnline() || cloud.isDead()) {
                    continue;
                }

                Location cloudLocation = player.getLocation().clone();

                cloudLocation.setY(player.getLocation().getY() - 1.2);

                cloud.teleport(cloudLocation);

                updateGoldTrail(player);
            }

        }, 1L, 1L);

        getLogger().info("KintoUn wurde gestartet!");
        getLogger().info("Kinto-Uns: " + kintoUnCount + "/10");
    }

    @Override
    public void onDisable() {

        for (FallingBlock cloud : activeClouds.values()) {

            if (!cloud.isDead()) {
                cloud.remove();
            }
        }

        for (List<FallingBlock> trail : goldTrails.values()) {

            for (FallingBlock block : trail) {

                if (!block.isDead()) {
                    block.remove();
                }
            }
        }

        activeClouds.clear();
        goldTrails.clear();
        lastTrailLocations.clear();
        boostCooldowns.clear();

        saveKintoUnCount();

        getLogger().info("KintoUn wurde beendet!");
    }

    // =========================================================
    // REZEPT
    // =========================================================

    private void registerKintoUnRecipe() {

        ItemStack kintoUn = createKintoUnItem();

        NamespacedKey recipeKey =
                new NamespacedKey(this, "kinto_un");

        ShapedRecipe recipe =
                new ShapedRecipe(recipeKey, kintoUn);

        recipe.shape("YGY", "NAN", "BBB");

        recipe.setIngredient('Y', Material.YELLOW_WOOL);
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        recipe.setIngredient('B', Material.BEACON);

        getServer().addRecipe(recipe);
    }

    // =========================================================
    // CRAFTING
    // =========================================================

    @EventHandler
    public void onCraft(CraftItemEvent event) {

        ItemStack result = event.getRecipe().getResult();

        if (!isKintoUn(result)) {
            return;
        }

        if (kintoUnCount >= 10) {

            event.setCancelled(true);

            if (event.getWhoClicked() instanceof Player player) {

                player.sendMessage(
                        "§cEs gibt bereits 10 Kinto-Uns auf dem Server!"
                );
            }

            return;
        }

        kintoUnCount++;

        saveKintoUnCount();

        if (event.getWhoClicked() instanceof Player player) {

            player.sendMessage(
                    "§6Kinto-Un hergestellt! §7(" +
                            kintoUnCount +
                            "/10)"
            );
        }
    }

    // =========================================================
    // KINTO-UN BENUTZEN
    // =========================================================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();

        if (!isKintoUn(item)) {
            return;
        }

        Player player = event.getPlayer();

        if (activeClouds.containsKey(player.getUniqueId())) {
            return;
        }

        Location location =
                player.getLocation().clone();

        location.setY(
                player.getLocation().getY() - 1.2
        );

        FallingBlock cloud =
                player.getWorld().spawnFallingBlock(
                        location,
                        Material.YELLOW_WOOL.createBlockData()
                );

        cloud.setGravity(false);
        cloud.setDropItem(false);
        cloud.setHurtEntities(false);

        cloud.addPassenger(player);

        activeClouds.put(
                player.getUniqueId(),
                cloud
        );

        player.setAllowFlight(true);
        player.setFlying(true);

        player.setFlySpeed(0.20f);

        player.getInventory().setItemInMainHand(null);

        event.setCancelled(true);

        // Neue leere Goldspur
        goldTrails.put(
                player.getUniqueId(),
                new ArrayList<>()
        );

        lastTrailLocations.put(
                player.getUniqueId(),
                player.getLocation().clone()
        );

        player.sendActionBar(
                "§6Drop Taste für Boost"
        );
    }

    // =========================================================
    // RECHTSKLICK AUF DIE EIGENE WOLKE
    // =========================================================

    @EventHandler
    public void onCloudRightClick(
            PlayerInteractEntityEvent event) {

        Player player = event.getPlayer();

        if (!(event.getRightClicked()
                instanceof FallingBlock cloud)) {
            return;
        }

        UUID uuid = player.getUniqueId();

        FallingBlock ownCloud =
                activeClouds.get(uuid);

        if (ownCloud == null ||
                ownCloud.getUniqueId()
                        != cloud.getUniqueId()) {
            return;
        }

        event.setCancelled(true);

        cloud.removePassenger(player);

        player.setFlying(false);
        player.setAllowFlight(false);

        player.setFlySpeed(0.10f);

        // Goldspur entfernen
        removeGoldTrail(uuid);

        // Kinto-Un zurück ins Inventar
        ItemStack kintoUn =
                createKintoUnItem();

        HashMap<Integer, ItemStack> leftover =
                player.getInventory().addItem(kintoUn);

        for (ItemStack item : leftover.values()) {

            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    item
            );
        }

        cloud.remove();

        activeClouds.remove(uuid);

        player.sendActionBar(
                "§6Kinto-Un eingepackt!"
        );
    }

    // =========================================================
    // DROP-TASTE = BOOST
    // =========================================================

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {

        Player player = event.getPlayer();

        if (!activeClouds.containsKey(
                player.getUniqueId())) {
            return;
        }

        // Kein Item darf gedroppt werden
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();

        long nextBoost =
                boostCooldowns.getOrDefault(
                        uuid,
                        0L
                );

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

    private void startBoost(Player player) {

        player.sendActionBar(
                "§6⚡ BOOST! §7| Drop Taste für Boost"
        );

        Bukkit.getScheduler().runTaskTimer(
                this,
                new Runnable() {

                    int ticks = 0;

                    @Override
                    public void run() {

                        if (!player.isOnline()) {
                            return;
                        }

                        if (!activeClouds.containsKey(
                                player.getUniqueId())) {
                            return;
                        }

                        if (ticks >= BOOST_DURATION_TICKS) {
                            return;
                        }

                        Location location =
                                player.getLocation();

                        Vector direction =
                                location.getDirection()
                                        .normalize();

                        Vector velocity =
                                direction.multiply(
                                        BOOST_SPEED
                                );

                        // Y=50 nicht überschreiten
                        if (location.getY()
                                >= MAX_HEIGHT - 1 &&
                                velocity.getY() > 0) {

                            velocity.setY(0);
                        }

                        player.setVelocity(velocity);

                        ticks++;
                    }
                },
                0L,
                1L
        );

        Bukkit.getScheduler().runTaskLater(
                this,
                () -> {

                    if (player.isOnline() &&
                            activeClouds.containsKey(
                                    player.getUniqueId())) {

                        player.sendActionBar(
                                "§6Drop Taste für Boost"
                        );
                    }

                },
                BOOST_DURATION_TICKS
        );
    }

    // =========================================================
    // GOLDSPUR
    // =========================================================

    private void updateGoldTrail(Player player) {

        UUID uuid = player.getUniqueId();

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

        // Wenn der Spieler praktisch steht,
        // wird die Goldspur langsam entfernt.
        if (current.distanceSquared(last) < 0.04) {

            removeLastTrailBlock(uuid);

            return;
        }

        // Spieler bewegt sich
        lastTrailLocations.put(
                uuid,
                current.clone()
        );

        List<FallingBlock> trail =
                goldTrails.get(uuid);

        if (trail == null) {

            trail = new ArrayList<>();

            goldTrails.put(uuid, trail);
        }

        Vector direction =
                current.toVector()
                        .subtract(last.toVector());

        if (direction.lengthSquared() == 0) {
            return;
        }

        direction.normalize();

        // Goldspur hinter dem Spieler
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

        FallingBlock gold =
                player.getWorld().spawnFallingBlock(
                        trailLocation,
                        Material.GOLD_BLOCK.createBlockData()
                );

        gold.setGravity(false);
        gold.setDropItem(false);
        gold.setHurtEntities(false);

        trail.add(gold);

        // Nur 7 Goldblöcke behalten
        while (trail.size() > MAX_TRAIL_BLOCKS) {

            FallingBlock oldest =
                    trail.remove(0);

            if (!oldest.isDead()) {
                oldest.remove();
            }
        }

        // Alle Goldblöcke bewegen sich mit
        updateTrailPositions(
                player,
                direction
        );
    }

    // =========================================================
    // GOLDSPUR POSITIONIEREN
    // =========================================================

    private void updateTrailPositions(
            Player player,
            Vector direction) {

        List<FallingBlock> trail =
                goldTrails.get(
                        player.getUniqueId()
                );

        if (trail == null || trail.isEmpty()) {
            return;
        }

        Location current =
                player.getLocation().clone();

        current.setY(
                player.getLocation().getY() - 1.2
        );

        for (int i = 0; i < trail.size(); i++) {

            FallingBlock gold =
                    trail.get(i);

            if (gold.isDead()) {
                continue;
            }

            double distance =
                    (i + 1) * TRAIL_DISTANCE;

            Location position =
                    current.clone()
                            .subtract(
                                    direction.multiply(
                                            distance
                                    )
                            );

            gold.teleport(position);
        }
    }

    // =========================================================
    // LETZTEN GOLD-BLOCK ENTFERNEN
    // =========================================================

    private void removeLastTrailBlock(
            UUID uuid) {

        List<FallingBlock> trail =
                goldTrails.get(uuid);

        if (trail == null ||
                trail.isEmpty()) {
            return;
        }

        FallingBlock last =
                trail.remove(0);

        if (!last.isDead()) {
            last.remove();
        }
    }

    // =========================================================
    // GANZE GOLDSPUR ENTFERNEN
    // =========================================================

    private void removeGoldTrail(UUID uuid) {

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
    // MAXIMALE HÖHE
    // =========================================================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (!activeClouds.containsKey(
                player.getUniqueId())) {
            return;
        }

        Location to = event.getTo();

        if (to == null) {
            return;
        }

        if (to.getY() > MAX_HEIGHT) {

            Location limited =
                    to.clone();

            limited.setY(MAX_HEIGHT);

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

        if (cloud != null) {

            if (!cloud.isDead()) {
                cloud.remove();
            }

            removeGoldTrail(uuid);

            if (kintoUnCount > 0) {

                kintoUnCount--;

                saveKintoUnCount();
            }

            boostCooldowns.remove(uuid);

            player.sendMessage(
                    "§6Deine Kinto-Un ist verschwunden."
            );

            player.sendMessage(
                    "§7Du kannst jetzt wieder eine neue craften."
            );
        }
    }

    // =========================================================
    // SERVER VERLASSEN
    // =========================================================

    @EventHandler
    public void onPlayerQuit(
            PlayerQuitEvent event) {

        Player player =
                event.getPlayer();

        UUID uuid =
                player.getUniqueId();

        FallingBlock cloud =
                activeClouds.remove(uuid);

        if (cloud != null &&
                !cloud.isDead()) {
            cloud.remove();
        }

        removeGoldTrail(uuid);

        boostCooldowns.remove(uuid);
    }

    // =========================================================
    // KINTO-UN ITEM
    // =========================================================

    private ItemStack createKintoUnItem() {

        ItemStack kintoUn =
                new ItemStack(
                        Material.YELLOW_WOOL
                );

        kintoUn.setAmount(1);

        ItemMeta meta =
                kintoUn.getItemMeta();

        meta.setDisplayName(
                "§6Kinto-Un"
        );

        meta.getPersistentDataContainer().set(
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

    private boolean isKintoUn(ItemStack item) {

        if (item == null) {
            return false;
        }

        if (item.getType() != Material.YELLOW_WOOL) {
            return false;
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return false;
        }

        Byte value =
                meta.getPersistentDataContainer().get(
                        kintoUnKey,
                        PersistentDataType.BYTE
                );

        return value != null &&
                value == (byte) 1;
    }

    // =========================================================
    // CONFIG SPEICHERN
    // =========================================================

    private void saveKintoUnCount() {

        getConfig().set(
                "kinto-un-count",
                kintoUnCount
        );

        saveConfig();
    }
}
