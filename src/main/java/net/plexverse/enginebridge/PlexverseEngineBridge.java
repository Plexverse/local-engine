package net.plexverse.enginebridge;

import com.mineplex.studio.sdk.modules.MineplexModule;
import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.plexverse.enginebridge.modules.LocalModuleManagerImpl;
import net.plexverse.enginebridge.modules.game.LocalMineplexGameModuleImpl;
import net.plexverse.enginebridge.util.ModuleScanner;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

@Slf4j
public class PlexverseEngineBridge extends JavaPlugin {

    /**
     * The singleton instance of PlexverseEngineBridge.
     */
    @Getter
    private static PlexverseEngineBridge instance;

    /**
     * The MineplexModuleManager instance (always Local).
     */
    @Getter
    private static MineplexModuleManager moduleManager;

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
     * Initializes the plugin and loads local modules.
     */
    @Override
    public void onEnable() {
        log.info("PlexverseEngineBridge enabled");
        
        // Generate default config if it doesn't exist
        saveDefaultConfig();
        reloadConfig();
        
        // Always use local module manager
        final LocalModuleManagerImpl localManager = new LocalModuleManagerImpl(this);
        PlexverseEngineBridge.moduleManager = localManager;
        log.info("Initialized LocalModuleManagerImpl");
        
        // Register MineplexGameModule early to prevent PlexverseCommon from failing
        // GameManager may use this or register its own implementation
        try {
            final LocalMineplexGameModuleImpl gameModule = new LocalMineplexGameModuleImpl(this);
            moduleManager.registerModule(gameModule);
            log.info("Registered LocalMineplexGameModule to prevent PlexverseCommon initialization errors");
        } catch (Exception e) {
            log.warn("Failed to register LocalMineplexGameModule", e);
        }
        
        // Register local modules
        initializeLocalModules();
    }
    
    /**
     * Initializes and registers local module implementations using class scanning.
     */
    private void initializeLocalModules() {
        log.info("Scanning for local module implementations...");
        final List<Object> modules = ModuleScanner.scanAndInstantiateModules(this);
        
        for (final Object module : modules) {
            try {
                // Skip LocalMineplexGameModuleImpl as it's already registered manually
                if (module instanceof LocalMineplexGameModuleImpl) {
                    log.debug("Skipping LocalMineplexGameModuleImpl - already registered manually");
                    continue;
                }
                
                if (module instanceof MineplexModule) {
                    moduleManager.registerModule((MineplexModule) module);
                    log.info("Registered module: {}", module.getClass().getSimpleName());
                } else {
                    log.warn("Skipping module {} - not a MineplexModule", module.getClass().getSimpleName());
                }
            } catch (final Exception e) {
                log.error("Failed to register module {}: {}", module.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        
        log.info("Registered {} local module(s)", modules.size());
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

