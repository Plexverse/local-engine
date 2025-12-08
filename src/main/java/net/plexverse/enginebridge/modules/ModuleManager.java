package net.plexverse.enginebridge.modules;

import net.plexverse.enginebridge.PlexverseEngineBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for managing modules in the Engine Bridge.
 * Provides methods to register, retrieve, and destroy modules.
 */
public interface ModuleManager {

    /**
     * Gets a registered module by its class type.
     *
     * @param moduleClass the class of the module to retrieve
     * @param <T> the type of the module
     * @return the registered module, or null if not found
     */
    @Nullable
    <T> T getRegisteredModule(@NotNull Class<T> moduleClass);

    /**
     * Registers a module with the module manager.
     *
     * @param instance the module instance to register
     * @param <T> the type of the module
     * @return this ModuleManager instance for method chaining
     */
    @NotNull
    <T> ModuleManager registerModule(@NotNull T instance);

    /**
     * Destroys a registered module by its class type.
     *
     * @param module the class of the module to destroy
     */
    void destroyModule(@NotNull Class<?> module);

    /**
     * Gets the singleton instance of the ModuleManager.
     *
     * @return the ModuleManager instance
     * @throws IllegalStateException if the ModuleManager has not been initialized
     */
    @NotNull
    static ModuleManager getInstance() {
        final ModuleManager manager = PlexverseEngineBridge.getModuleManager();
        if (manager == null) {
            throw new IllegalStateException("ModuleManager has not been initialized. Make sure PlexverseEngineBridge is enabled.");
        }
        return manager;
    }

}

