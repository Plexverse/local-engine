package net.plexverse.enginebridge.modules;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local implementation of ModuleManager for running without Studio Engine.
 * Uses Bukkit's ServicesManager to register and manage modules locally.
 */
@Slf4j
@RequiredArgsConstructor
public class LocalModuleManagerImpl implements ModuleManager {

    private static final ServicePriority DEFAULT_PRIORITY = ServicePriority.Normal;

    private final JavaPlugin plugin;
    private final ServicesManager servicesManager;
    private final Map<Class<?>, ServicePriority> registeredModules = new HashMap<>();

    public LocalModuleManagerImpl(@NonNull final JavaPlugin plugin) {
        this.plugin = plugin;
        this.servicesManager = Bukkit.getServicesManager();
        this.register(ModuleManager.class, this, DEFAULT_PRIORITY);
    }

    @Override
    @Nullable
    public <T> T getRegisteredModule(@NotNull final Class<T> moduleClass) {
        final RegisteredServiceProvider<T> provider = servicesManager.getRegistration(moduleClass);
        return provider != null ? provider.getProvider() : null;
    }

    @Override
    @NotNull
    public <T> ModuleManager registerModule(@NotNull final T instance) {
        @SuppressWarnings("unchecked")
        final Class<T> moduleClass = (Class<T>) instance.getClass();
        final ServicePriority priority = registeredModules.getOrDefault(moduleClass, DEFAULT_PRIORITY);
        
        if (hasModule(moduleClass, priority)) {
            log.warn("A registered module already exists for {}!", moduleClass.getSimpleName());
            throw new IllegalArgumentException(
                    "A registered module already exists for " + moduleClass.getSimpleName() + " with the same priority!");
        }

        log.info("Register module {} for {}.", instance.getClass().getSimpleName(), moduleClass.getSimpleName());
        
        // Call setup if the instance has a setup method
        try {
            instance.getClass().getMethod("setup").invoke(instance);
        } catch (Exception e) {
            // No setup method, that's okay
        }
        
        // Register as event listener if it implements Listener
        if (instance instanceof Listener) {
            Bukkit.getPluginManager().registerEvents((Listener) instance, plugin);
            log.info("Registered event listeners for module {}.", instance.getClass().getSimpleName());
        }
        
        register(moduleClass, instance, priority);
        registeredModules.put(moduleClass, priority);
        
        return this;
    }

    @Override
    public void destroyModule(@NotNull final Class<?> module) {
        destroyModule(module, DEFAULT_PRIORITY);
    }

    private void destroyModule(@NotNull final Class<?> module, @NotNull final ServicePriority priority) {
        final List<? extends RegisteredServiceProvider<?>> providers = getRegistrations(module, priority);
        if (providers.isEmpty()) {
            log.error("Can't load registered module {} for destruction!", module.getSimpleName());
            return;
        }

        for (final RegisteredServiceProvider<?> provider : providers) {
            try {
                log.info("Removing module {}.", provider.getService().getSimpleName());

                final Object instance = provider.getProvider();
                servicesManager.unregister(instance);
                if (instance instanceof Listener) {
                    HandlerList.unregisterAll((Listener) instance);
                    log.info("Unregistered event listeners for module {}.", instance.getClass().getSimpleName());
                }
                
                // Call teardown if the instance has a teardown method
                try {
                    instance.getClass().getMethod("teardown").invoke(instance);
                } catch (Exception e) {
                    // No teardown method, that's okay
                }
                
                registeredModules.remove(module);
            } catch (final Exception exception) {
                log.error("Failed to destroy module {}!", module.getSimpleName(), exception);
            }
        }
    }

    private <T> void register(
            @NonNull final Class<T> moduleClass, @NonNull final T instance, @NonNull final ServicePriority priority) {
        servicesManager.register(moduleClass, instance, plugin, priority);
    }

    private List<? extends RegisteredServiceProvider<?>> getRegistrations(
            final Class<?> moduleClass, final ServicePriority priority) {
        return servicesManager.getRegistrations(moduleClass).stream()
                .filter(provider -> provider.getPriority() == priority)
                .toList();
    }

    private boolean hasModule(final Class<?> moduleClass, final ServicePriority priority) {
        return !getRegistrations(moduleClass, priority).isEmpty();
    }

    /**
     * Tears down all registered modules.
     */
    public void teardown() {
        log.info("Tearing down ModuleManager.");

        // Create a copy to avoid concurrent modification
        final Map<Class<?>, ServicePriority> modulesToDestroy = new HashMap<>(registeredModules);
        
        // Destroy all modules
        for (final Map.Entry<Class<?>, ServicePriority> entry : modulesToDestroy.entrySet()) {
            destroyModule(entry.getKey(), entry.getValue());
        }

        servicesManager.unregister(this);
        registeredModules.clear();
    }
}

