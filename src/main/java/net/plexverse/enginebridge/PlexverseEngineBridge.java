package net.plexverse.enginebridge;

import com.mineplex.studio.sdk.modules.MineplexModule;
import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import com.mineplex.studio.sdk.modules.game.mechanics.afk.PlayerAFKMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.legacy.LegacyArmorMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.legacy.LegacyCriticalHitMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.legacy.LegacyEnchantmentsMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.legacy.LegacyEnchantmentTableMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.legacy.LegacyEnderPearlMechanic;
import com.mineplex.studio.sdk.modules.gamestatehelper.GameStateHelperModule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.plexverse.enginebridge.modules.LocalModuleManagerImpl;
import net.plexverse.enginebridge.modules.game.LocalGameMechanicFactoryImpl;
import net.plexverse.enginebridge.modules.game.LocalMineplexGameModuleImpl;
import net.plexverse.enginebridge.modules.game.mechanics.afk.LocalPlayerAFKMechanic;
import net.plexverse.enginebridge.modules.game.mechanics.legacy.LocalLegacyArmorMechanic;
import net.plexverse.enginebridge.modules.game.mechanics.legacy.LocalLegacyCriticalHitMechanic;
import net.plexverse.enginebridge.modules.game.mechanics.legacy.LocalLegacyEnchantmentsMechanic;
import net.plexverse.enginebridge.modules.game.mechanics.legacy.LocalLegacyEnchantmentTableMechanic;
import net.plexverse.enginebridge.modules.game.mechanics.legacy.LocalLegacyEnderPearlMechanic;
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
        
        // Register GameMechanicFactory early to prevent game setup failures
        try {
            final LocalGameMechanicFactoryImpl mechanicFactory = new LocalGameMechanicFactoryImpl(this);
            moduleManager.registerModule(mechanicFactory);
            log.info("Registered LocalGameMechanicFactory to prevent game setup errors");
            
            // Register local mechanic providers for 1.21.8 compatibility
            registerLocalMechanics(mechanicFactory);
        } catch (Exception e) {
            log.warn("Failed to register LocalGameMechanicFactory", e);
        }
        
        // Register SDK's GameStateHelperModule
        try {
            final GameStateHelperModule gameStateHelperModule = new GameStateHelperModule(this);
            moduleManager.registerModule(gameStateHelperModule);
            log.info("Registered GameStateHelperModule from SDK");
        } catch (Exception e) {
            log.warn("Failed to register GameStateHelperModule", e);
        }
        
        // Register local modules
        initializeLocalModules();
    }
    
    /**
     * Registers local mechanic providers to avoid SDK version compatibility issues.
     * These mechanics use Entity.isGhost() which doesn't exist in 1.21.8.
     */
    private void registerLocalMechanics(final LocalGameMechanicFactoryImpl mechanicFactory) {
        // Register LocalPlayerAFKMechanic as a provider
        // The SDK's PlayerAFKMechanicImpl uses Entity.isGhost() which doesn't exist in 1.21.8
        mechanicFactory.register(
            PlayerAFKMechanic.class,
            LocalPlayerAFKMechanic::new
        );
        log.info("Registered LocalPlayerAFKMechanic provider for 1.21.8 compatibility");
        
        // Register LocalLegacyArmorMechanic as a provider
        // The SDK's LegacyArmorMechanicImpl uses Entity.isGhost() which doesn't exist in 1.21.8
        mechanicFactory.register(
            LegacyArmorMechanic.class,
            LocalLegacyArmorMechanic::new
        );
        log.info("Registered LocalLegacyArmorMechanic provider for 1.21.8 compatibility");
        
        // Register LocalLegacyCriticalHitMechanic as a provider
        // The SDK's LegacyCriticalHitMechanicImpl uses Entity.isGhost() which doesn't exist in 1.21.8
        mechanicFactory.register(
            LegacyCriticalHitMechanic.class,
            LocalLegacyCriticalHitMechanic::new
        );
        log.info("Registered LocalLegacyCriticalHitMechanic provider for 1.21.8 compatibility");
        
        // Register LocalLegacyEnchantmentsMechanic as a provider
        // The SDK's LegacyEnchantmentsMechanicImpl uses Entity.isGhost() which doesn't exist in 1.21.8
        mechanicFactory.register(
            LegacyEnchantmentsMechanic.class,
            LocalLegacyEnchantmentsMechanic::new
        );
        log.info("Registered LocalLegacyEnchantmentsMechanic provider for 1.21.8 compatibility");
        
        // Register LocalLegacyEnchantmentTableMechanic as a provider
        // The SDK's LegacyEnchantmentTableMechanicImpl uses Entity.isGhost() which doesn't exist in 1.21.8
        mechanicFactory.register(
            LegacyEnchantmentTableMechanic.class,
            LocalLegacyEnchantmentTableMechanic::new
        );
        log.info("Registered LocalLegacyEnchantmentTableMechanic provider for 1.21.8 compatibility");
        
        // Register LocalLegacyEnderPearlMechanic as a provider
        // The SDK's LegacyEnderPearlMechanicImpl uses Entity.isGhost() which doesn't exist in 1.21.8
        mechanicFactory.register(
            LegacyEnderPearlMechanic.class,
            LocalLegacyEnderPearlMechanic::new
        );
        log.info("Registered LocalLegacyEnderPearlMechanic provider for 1.21.8 compatibility");
    }
    
    /**
     * Initializes and registers local module implementations using class scanning.
     */
    private void initializeLocalModules() {
        log.info("Scanning for local module implementations...");
        final List<Object> modules = ModuleScanner.scanAndInstantiateModules(this);
        
        for (final Object module : modules) {
            try {
                // Skip modules that are already registered manually
                if (module instanceof LocalMineplexGameModuleImpl || 
                    module instanceof LocalGameMechanicFactoryImpl) {
                    log.debug("Skipping {} - already registered manually", module.getClass().getSimpleName());
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

