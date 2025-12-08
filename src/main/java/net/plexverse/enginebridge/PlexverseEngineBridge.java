package net.plexverse.enginebridge;

import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.plexverse.enginebridge.modules.LocalModuleManagerImpl;
import net.plexverse.enginebridge.modules.ModuleManager;
import net.plexverse.enginebridge.modules.RemoteModuleManagerImpl;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

@Slf4j
public class PlexverseEngineBridge extends JavaPlugin {

    /**
     * The singleton instance of PlexverseEngineBridge.
     */
    @Getter
    private static PlexverseEngineBridge instance;

    /**
     * The ModuleManager instance (either Remote or Local).
     */
    @Getter
    private static ModuleManager moduleManager;

    /**
     * Checks if the ModuleManager is using the remote implementation (StudioEngine).
     *
     * @return true if using RemoteModuleManagerImpl, false if using LocalModuleManagerImpl
     */
    public static boolean isUsingRemote() {
        return moduleManager instanceof RemoteModuleManagerImpl;
    }

    /**
     * Checks if the ModuleManager is using the local implementation.
     *
     * @return true if using LocalModuleManagerImpl, false if using RemoteModuleManagerImpl
     */
    public static boolean isUsingLocal() {
        return moduleManager instanceof LocalModuleManagerImpl;
    }

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
     * Initializes the plugin and determines which ModuleManager to use.
     */
    @Override
    public void onEnable() {
        log.info("PlexverseEngineBridge enabled");
        
        // Check if StudioEngine is present
        final Plugin studioEngine = Bukkit.getPluginManager().getPlugin("StudioEngine");
        if (studioEngine != null && studioEngine.isEnabled()) {
            log.info("StudioEngine detected, using RemoteModuleManagerImpl");
            try {
                final MineplexModuleManager mineplexModuleManager = MineplexModuleManager.getInstance();
                PlexverseEngineBridge.moduleManager = new RemoteModuleManagerImpl(mineplexModuleManager);
                log.info("Successfully initialized RemoteModuleManagerImpl");
            } catch (final Exception e) {
                log.warn("Failed to get MineplexModuleManager from StudioEngine, falling back to LocalModuleManagerImpl: {}", e.getMessage());
                PlexverseEngineBridge.moduleManager = new LocalModuleManagerImpl(this);
                log.info("Initialized LocalModuleManagerImpl as fallback");
            }
        } else {
            log.info("StudioEngine not detected, using LocalModuleManagerImpl");
            PlexverseEngineBridge.moduleManager = new LocalModuleManagerImpl(this);
            log.info("Successfully initialized LocalModuleManagerImpl");
        }
    }

    /**
     * Called when the plugin is disabled.
     * Cleans up resources.
     */
    @Override
    public void onDisable() {
        if (PlexverseEngineBridge.moduleManager instanceof LocalModuleManagerImpl) {
            ((LocalModuleManagerImpl) PlexverseEngineBridge.moduleManager).teardown();
        }
        log.info("PlexverseEngineBridge disabled");
    }
}

