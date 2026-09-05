package de.kintoun;

import org.bukkit.plugin.java.JavaPlugin;

public class KintoUnPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("KintoUn wurde gestartet!");
    }

    @Override
    public void onDisable() {
        getLogger().info("KintoUn wurde beendet!");
    }
}
