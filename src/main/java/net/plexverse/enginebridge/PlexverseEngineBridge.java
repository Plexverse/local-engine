package net.plexverse.enginebridge;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;

@Slf4j
public class PlexverseEngineBridge extends JavaPlugin {

    /**
     * The singleton instance of PlexverseEngineBridge.
     */
    @Getter
    private static PlexverseEngineBridge instance;

    /**
     * Called when the plugin is loaded.
     * Initializes the singleton instance.
     */
    @Override
    public void onLoad() {
        PlexverseEngineBridge.instance = this;
    }

    /**
     * Called when the plugin is enabled.
     * Initializes the plugin.
     */
    @Override
    public void onEnable() {
        log.info("PlexverseEngineBridge enabled");
    }

    /**
     * Called when the plugin is disabled.
     * Cleans up resources.
     */
    @Override
    public void onDisable() {
        log.info("PlexverseEngineBridge disabled");
    }
}

