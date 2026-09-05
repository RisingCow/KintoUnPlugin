package de.kintoun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
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

    private NamespacedKey kintoUnKey;

    private final Map<UUID, FallingBlock> activeClouds = new HashMap<>();

    // Anzahl der insgesamt vergebenen Kinto-Uns
    private int kintoUnCount;

    @Override
    public void onEnable() {

        kintoUnKey = new NamespacedKey(this, "kinto_un_item");

        // Gespeicherte Anzahl laden
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
                location.add(0, -1.2, 0);

                cloud.teleport(location);

                Vector velocity = player.getVelocity();
                cloud.setVelocity(velocity);
            }

        }, 1L, 1L);

        getLogger().info("KintoUn wurde gestartet!");
        getLogger().info("Kinto-Uns auf dem Server: " + kintoUnCount + "/10");
    }

    @Override
    public void onDisable() {

        for (FallingBlock cloud : activeClouds.values()) {
            if (!cloud.isDead()) {
                cloud.remove();
            }
        }

        activeClouds.clear();

        saveKintoUnCount();

        getLogger().info("KintoUn wurde beendet!");
    }

    private void registerKintoUnRecipe() {

        // Kinto-Un = gelbe Wolle
        ItemStack kintoUn = new ItemStack(Material.YELLOW_WOOL);

        // Nur 1 Kinto-Un pro Stack
        kintoUn.setAmount(1);

        ItemMeta meta = kintoUn.getItemMeta();

        // Name im Inventar
        meta.setDisplayName("§6Kinto-Un");

        // Eindeutige Kennzeichnung
        meta.getPersistentDataContainer().set(
                kintoUnKey,
                PersistentDataType.BYTE,
                (byte) 1
        );

        kintoUn.setItemMeta(meta);

        // Crafting-Rezept
        NamespacedKey recipeKey = new NamespacedKey(this, "kinto_un");

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, kintoUn);

        recipe.shape("YGY", "NAN", "BBB");

        recipe.setIngredient('Y', Material.YELLOW_WOOL);
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        recipe.setIngredient('B', Material.BEACON);

        getServer().addRecipe(recipe);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {

        ItemStack result = event.getRecipe().getResult();

        // Nur Kinto-Un
        if (!isKintoUn(result)) {
            return;
        }

        // Sind bereits 10 Kinto-Uns vorhanden?
        if (kintoUnCount >= 10) {

            event.setCancelled(true);

            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage("§cEs gibt bereits 10 Kinto-Uns auf dem Server!");
            }

            return;
        }

        // Eine neue Kinto-Un wurde hergestellt
        kintoUnCount++;

        saveKintoUnCount();

        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(
                    "§6Kinto-Un hergestellt! §7(" + kintoUnCount + "/10)"
            );
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!event.getAction().isRightClick()) {
            return;
        }

        ItemStack item = event.getItem();

        // Nur echte Kinto-Un
        if (!isKintoUn(item)) {
            return;
        }

        Player player = event.getPlayer();

        // Bereits eine aktive Kinto-Un?
        if (activeClouds.containsKey(player.getUniqueId())) {
            return;
        }

        // Wolke erstellen
        Location location = player.getLocation().clone();
        location.add(0, -1.2, 0);

        FallingBlock cloud = player.getWorld().spawnFallingBlock(
                location,
                Material.YELLOW_WOOL.createBlockData()
        );

        cloud.setGravity(false);
        cloud.setDropItem(false);
        cloud.setHurtEntities(false);

        // Spieler auf die Wolke setzen
        cloud.addPassenger(player);

        activeClouds.put(player.getUniqueId(), cloud);

        // Fliegen erlauben
        player.setAllowFlight(true);
        player.setFlying(true);

        // Kinto-Un aus der Hand entfernen
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();

        FallingBlock cloud = activeClouds.remove(player.getUniqueId());

        // Nur wenn tatsächlich eine Kinto-Un aktiv war
        if (cloud != null) {

            if (!cloud.isDead()) {
                cloud.remove();
            }

            // Eine Kinto-Un ist verloren gegangen.
            // Dadurch wird wieder ein Platz frei.
            if (kintoUnCount > 0) {
                kintoUnCount--;
                saveKintoUnCount();
            }

            player.sendMessage(
                    "§6Deine Kinto-Un ist verschwunden. §7Eine neue kann wieder gecraftet werden."
            );
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        FallingBlock cloud = activeClouds.remove(player.getUniqueId());

        if (cloud != null && !cloud.isDead()) {
            cloud.remove();
        }
    }

    private boolean isKintoUn(ItemStack item) {

        if (item == null || item.getType() != Material.YELLOW_WOOL) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return false;
        }

        Byte value = meta.getPersistentDataContainer().get(
                kintoUnKey,
                PersistentDataType.BYTE
        );

        return value != null && value == (byte) 1;
    }

    private void saveKintoUnCount() {

        FileConfiguration config = getConfig();

        config.set("kinto-un-count", kintoUnCount);

        saveConfig();
    }
}
