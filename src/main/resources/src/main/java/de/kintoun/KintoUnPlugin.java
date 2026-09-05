package de.kintoun;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class KintoUnPlugin extends JavaPlugin implements Listener {

    private NamespacedKey kintoUnKey;

    @Override
    public void onEnable() {
        kintoUnKey = new NamespacedKey(this, "kinto_un_item");

        registerKintoUnRecipe();
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("KintoUn wurde gestartet!");
    }

    @Override
    public void onDisable() {
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

        // Nur einmal pro Hand ausführen
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Nur Rechtsklick
        if (!event.getAction().isRightClick()) {
            return;
        }

        ItemStack item = event.getItem();

        // Prüfen, ob es die Kinto-Un ist
        if (!isKintoUn(item)) {
            return;
        }

        Player player = event.getPlayer();

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
}
