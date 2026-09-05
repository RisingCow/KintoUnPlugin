package de.kintoun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KintoUnPlugin extends JavaPlugin implements Listener {

    private static final double MAX_HEIGHT = 50.0;

    // Boost-Einstellungen
    private static final long BOOST_COOLDOWN = 30_000L;
    private static final int BOOST_DURATION_TICKS = 20;
    private static final double BOOST_SPEED = 1.8;

    private NamespacedKey kintoUnKey;

    private final Map<UUID, FallingBlock> activeClouds = new HashMap<>();
    private final Map<UUID, Long> boostCooldowns = new HashMap<>();

    private int kintoUnCount;

    @Override
    public void onEnable() {

        kintoUnKey = new NamespacedKey(this, "kinto_un_item");

        saveDefaultConfig();

        kintoUnCount = getConfig().getInt("kinto-un-count", 0);

        registerKintoUnRecipe();

        getServer().getPluginManager().registerEvents(this, this);

        // Wolke folgt dem Spieler
        Bukkit.getScheduler().runTaskTimer(this, () -> {

            for (Map.Entry<UUID, FallingBlock> entry : activeClouds.entrySet()) {

                Player player = Bukkit.getPlayer(entry.getKey());
                FallingBlock cloud = entry.getValue();

                if (player == null || !player.isOnline() || cloud.isDead()) {
                    continue;
                }

                Location location = player.getLocation().clone();

                // Wolke unter dem Spieler
                location.setY(player.getLocation().getY() - 1.2);

                cloud.teleport(location);
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

        activeClouds.clear();
        boostCooldowns.clear();

        saveKintoUnCount();

        getLogger().info("KintoUn wurde beendet!");
    }

    // =========================================================
    // REZEPT
    // =========================================================

    private void registerKintoUnRecipe() {

        ItemStack kintoUn = new ItemStack(Material.YELLOW_WOOL);

        // Nicht stapelbar
        kintoUn.setAmount(1);

        ItemMeta meta = kintoUn.getItemMeta();

        meta.setDisplayName("§6Kinto-Un");

        meta.getPersistentDataContainer().set(
                kintoUnKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        kintoUn.setItemMeta(meta);

        NamespacedKey recipeKey = new NamespacedKey(this, "kinto_un");

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, kintoUn);

        /*
         * Y G Y
         * N A N
         * B B B
         */

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

        // Maximal 10 Kinto-Uns
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
    // KINTO-UN AUS INVENTAR BENUTZEN
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

        // Bereits auf einer Kinto-Un
        if (activeClouds.containsKey(player.getUniqueId())) {
            return;
        }

        Location location = player.getLocation().clone();

        location.setY(player.getLocation().getY() - 1.2);

        FallingBlock cloud = player.getWorld().spawnFallingBlock(
                location,
                Material.YELLOW_WOOL.createBlockData()
        );

        cloud.setGravity(false);
        cloud.setDropItem(false);
        cloud.setHurtEntities(false);

        // Spieler auf die Wolke setzen
        cloud.addPassenger(player);

        activeClouds.put(
                player.getUniqueId(),
                cloud
        );

        // Fliegen aktivieren
        player.setAllowFlight(true);
        player.setFlying(true);

        // Normale Fluggeschwindigkeit
        player.setFlySpeed(0.20f);

        // Kinto-Un aus der Hand entfernen
        player.getInventory().setItemInMainHand(null);

        event.setCancelled(true);

        player.sendActionBar(
                "§6Drop Taste für Boost"
        );
    }

    // =========================================================
    // RECHTSKLICK AUF DIE WOLKE
    // =========================================================

    @EventHandler
    public void onCloudRightClick(PlayerInteractEntityEvent event) {

        Player player = event.getPlayer();

        if (!(event.getRightClicked() instanceof FallingBlock cloud)) {
            return;
        }

        UUID playerUUID = player.getUniqueId();

        FallingBlock ownCloud = activeClouds.get(playerUUID);

        // Ist es nicht seine eigene Wolke?
        if (ownCloud == null || ownCloud.getUniqueId() != cloud.getUniqueId()) {
            return;
        }

        event.setCancelled(true);

        // Spieler von der Wolke entfernen
        cloud.removePassenger(player);

        // Flug ausschalten
        player.setFlying(false);
        player.setAllowFlight(false);

        // Fluggeschwindigkeit zurücksetzen
        player.setFlySpeed(0.10f);

        // Kinto-Un wieder ins Inventar
        ItemStack kintoUn = createKintoUnItem();

        HashMap<Integer, ItemStack> leftover =
                player.getInventory().addItem(kintoUn);

        // Falls Inventar voll ist
        for (ItemStack item : leftover.values()) {

            player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    item
            );
        }

        // Wolke entfernen
        if (!cloud.isDead()) {
            cloud.remove();
        }

        activeClouds.remove(playerUUID);

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

        // Nur während man auf einer Kinto-Un sitzt
        if (!activeClouds.containsKey(player.getUniqueId())) {
            return;
        }

        // NICHTS darf gedroppt werden
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();

        long nextBoost =
                boostCooldowns.getOrDefault(uuid, 0L);

        // Noch Cooldown
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

        // Boost starten
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
                                location.getDirection().normalize();

                        Vector velocity =
                                direction.multiply(BOOST_SPEED);

                        // Nicht über Y=50 fliegen
                        if (location.getY() >= MAX_HEIGHT - 1 &&
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

        // Nach dem Boost wieder normale Anzeige
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
    // MAXIMALE HÖHE
    // =========================================================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (!activeClouds.containsKey(player.getUniqueId())) {
            return;
        }

        Location to = event.getTo();

        if (to == null) {
            return;
        }

        if (to.getY() > MAX_HEIGHT) {

            Location limited = to.clone();

            limited.setY(MAX_HEIGHT);

            event.setTo(limited);
        }
    }

    // =========================================================
    // TOD
    // =========================================================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();

        FallingBlock cloud =
                activeClouds.remove(
                        player.getUniqueId()
                );

        if (cloud != null) {

            if (!cloud.isDead()) {
                cloud.remove();
            }

            // Kinto-Un verloren
            if (kintoUnCount > 0) {

                kintoUnCount--;

                saveKintoUnCount();
            }

            boostCooldowns.remove(
                    player.getUniqueId()
            );

            player.sendMessage(
                    "§6Deine Kinto-Un ist verschwunden."
            );

            player.sendMessage(
                    "§7Du kannst jetzt wieder eine neue craften."
            );
        }
    }

    // =========================================================
    // SPIELER VERLÄSST SERVER
    // =========================================================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        FallingBlock cloud =
                activeClouds.remove(
                        player.getUniqueId()
                );

        if (cloud != null && !cloud.isDead()) {
            cloud.remove();
        }

        boostCooldowns.remove(
                player.getUniqueId()
        );
    }

    // =========================================================
    // KINTO-UN ITEM ERSTELLEN
    // =========================================================

    private ItemStack createKintoUnItem() {

        ItemStack kintoUn =
                new ItemStack(Material.YELLOW_WOOL);

        kintoUn.setAmount(1);

        ItemMeta meta =
                kintoUn.getItemMeta();

        meta.setDisplayName("§6Kinto-Un");

        meta.getPersistentDataContainer().set(
                kintoUnKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        kintoUn.setItemMeta(meta);

        return kintoUn;
    }

    // =========================================================
    // IST ES EINE KINTO-UN?
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
