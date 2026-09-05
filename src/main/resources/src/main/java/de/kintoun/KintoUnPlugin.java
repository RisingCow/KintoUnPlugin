package de.kintoun;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
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
        ItemStack cloud = new ItemStack(Material.YELLOW_WOOL);

        ItemMeta meta = cloud.getItemMeta();
        meta.setDisplayName("§6Goldene Kinto-Un");
        cloud.setItemMeta(meta);

        NamespacedKey key = new NamespacedKey(this, "goldene_kinto_un");
        ShapedRecipe recipe = new ShapedRecipe(key, cloud);

        recipe.shape("YGY", "NAN", "BBB");

        recipe.setIngredient('Y', Material.YELLOW_WOOL);
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        recipe.setIngredient('B', Material.BEACON);

        getServer().addRecipe(recipe);
    }
}
