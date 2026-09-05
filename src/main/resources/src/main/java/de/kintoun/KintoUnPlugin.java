package de.kintoun;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class KintoUnPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        registerKintoUnRecipe();
        getLogger().info("KintoUn wurde gestartet!");
    }

    @Override
    public void onDisable() {
        getLogger().info("KintoUn wurde beendet!");
    }

    private void registerKintoUnRecipe() {

        // Kinto-Un: momentan gelbe Wolle als Platzhalter
        ItemStack kintoUn = new ItemStack(Material.YELLOW_WOOL);

        // Immer nur 1 Kinto-Un
        kintoUn.setAmount(1);

        ItemMeta meta = kintoUn.getItemMeta();

        // Name im Inventar
        meta.setDisplayName("§6Kinto-Un");

        // Eindeutige Kennzeichnung für die Kinto-Un
        NamespacedKey itemKey = new NamespacedKey(this, "kinto_un_item");
        meta.getPersistentDataContainer().set(
                itemKey,
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
}
