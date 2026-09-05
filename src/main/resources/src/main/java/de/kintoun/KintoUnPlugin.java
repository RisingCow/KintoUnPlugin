package de.kintoun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    @Override
    public void onEnable() {
        kintoUnKey = new NamespacedKey(this, "kinto_un_item");

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

                // Geschwindigkeit des Spielers übernehmen
                Vector velocity = player.getVelocity();
                cloud.setVelocity(velocity);
            }

        }, 1L, 1L);

        getLogger().info("KintoUn wurde gestartet!");
    }

    @Override
    public void onDisable() {

        for (FallingBlock cloud : activeClouds.values()) {
            if (!cloud.isDead()) {
                cloud.remove();
            }
        }

        activeClouds.clear();

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
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!event.getAction().isRightClick()) {
            return;
        }

        ItemStack item = event.getItem();

        if (!isKintoUn(item)) {
            return;
        }

        Player player = event.getPlayer();

        // Falls bereits eine Kinto-Un aktiv ist
        if (activeClouds.containsKey(player.getUniqueId())) {
            return;
        }

        // Gelben Wollblock als Wolke spawnen
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

        // Flug aktivieren
        player.setAllowFlight(true);
        player.setFlying(true);

        // Kinto-Un aus der Hand nehmen
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        event.setCancelled(true);
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();

        FallingBlock cloud = activeClouds.remove(player.getUniqueId());

        if (cloud != null && !cloud.isDead()) {
            cloud.remove();
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
}
