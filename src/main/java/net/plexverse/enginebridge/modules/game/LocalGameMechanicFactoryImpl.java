package net.plexverse.enginebridge.modules.game;

import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.game.MineplexGameMechanicFactory;
import com.mineplex.studio.sdk.modules.game.mechanics.GameMechanic;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Local stub implementation of MineplexGameMechanicFactory.
 * Provides basic mechanic construction for local development.
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(MineplexGameMechanicFactory.class)
public class LocalGameMechanicFactoryImpl implements MineplexGameMechanicFactory {
    
    public static final ServicePriority DEFAULT_PRIORITY = ServicePriority.Normal;
    
    private final JavaPlugin plugin;
    private final Map<Class<? extends GameMechanic<?>>, NavigableSet<Provider<?>>> providers =
            new ConcurrentHashMap<>();
    
    @Override
    public void setup() {
        log.info("LocalGameMechanicFactory setup");
    }
    
    @Override
    public void teardown() {
        providers.clear();
        log.info("LocalGameMechanicFactory teardown");
    }
    
    @Override
    public <M extends GameMechanic<?>> @NonNull MineplexGameMechanicFactory register(
            @NonNull final Class<M> gameMechanic, @NonNull final Supplier<M> mechanicSupplier) {
        return register(gameMechanic, mechanicSupplier, DEFAULT_PRIORITY);
    }
    
    @Override
    public <M extends GameMechanic<?>> @NonNull MineplexGameMechanicFactory register(
            @NonNull final Class<M> gameMechanic,
            @NonNull final Supplier<M> mechanicSupplier,
            @NonNull final ServicePriority priority) {
        log.debug("Registering {} with priority {}", gameMechanic.getSimpleName(), priority);
        final Provider<M> provider = new Provider<>(gameMechanic, mechanicSupplier, priority);
        final NavigableSet<Provider<?>> registered = providers.computeIfAbsent(gameMechanic, k -> new TreeSet<>());
        registered.add(provider);
        return this;
    }
    
    @Override
    public boolean contains(@NonNull final Class<? extends GameMechanic<?>> gameMechanic) {
        return providers.containsKey(gameMechanic);
    }
    
    @Override
    public <M extends GameMechanic<?>> @NonNull M construct(@NonNull final Class<M> gameMechanic) {
        final NavigableSet<Provider<?>> providerSet = providers.get(gameMechanic);
        if (providerSet == null || providerSet.isEmpty()) {
            log.warn("No provider for {}, attempting to find Impl class", gameMechanic.getName());
            
            // Try to find and instantiate an "Impl" class
            final M instance = tryFindAndInstantiateImpl(gameMechanic);
            if (instance != null) {
                return instance;
            }
            
            // Fallback to default constructor on the original class
            try {
                return gameMechanic.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to construct mechanic {}", gameMechanic.getSimpleName(), e);
                final String errorMsg = String.format(
                    "No provider for %s and default constructor failed. " +
                    "Please register a provider or contribute an implementation at https://github.com/Plexverse/local-engine",
                    gameMechanic.getName()
                );
                throw new IllegalArgumentException(errorMsg, e);
            }
        }
        
        final Provider<?> provider = providerSet.last();
        @SuppressWarnings("unchecked")
        final M instance = (M) provider.supplier.get();
        return instance;
    }
    
    @SuppressWarnings("unchecked")
    private <M extends GameMechanic<?>> M tryFindAndInstantiateImpl(@NonNull final Class<M> gameMechanic) {
        final String className = gameMechanic.getName();
        final String implClassName = className + "Impl";
        
        log.debug("Attempting to find Impl class: {}", implClassName);
        
        // Try multiple classloaders and package locations
        final ClassLoader[] classLoaders = {
            plugin.getClass().getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
            gameMechanic.getClassLoader(),
            ClassLoader.getSystemClassLoader()
        };
        
        for (final ClassLoader classLoader : classLoaders) {
            if (classLoader == null) {
                continue;
            }
            
            try {
                // Try to find the Impl class in the same package
                final Class<?> implClass = Class.forName(implClassName, false, classLoader);
                log.info("Found Impl class {} using classloader {}", implClassName, classLoader.getClass().getSimpleName());
                
                // Check if it implements the mechanic interface
                if (!gameMechanic.isAssignableFrom(implClass)) {
                    log.warn("Found {} but it doesn't implement {}", implClassName, className);
                    continue;
                }
                
                // Try multiple constructor options
                // 1. No-args constructor
                try {
                    final Constructor<?> constructor = implClass.getDeclaredConstructor();
                    constructor.setAccessible(true); // Make sure we can access it even if it's private
                    final Object instance = constructor.newInstance();
                    log.info("Instantiated {} using no-args constructor", implClassName);
                    return (M) instance;
                } catch (NoSuchMethodException e) {
                    log.debug("No no-args constructor found for {}, trying JavaPlugin constructor", implClassName);
                    // 2. Try with JavaPlugin as argument
                    try {
                        final Constructor<?> constructor = implClass.getDeclaredConstructor(JavaPlugin.class);
                        constructor.setAccessible(true);
                        final Object instance = constructor.newInstance(plugin);
                        log.info("Instantiated {} using JavaPlugin constructor", implClassName);
                        return (M) instance;
                    } catch (NoSuchMethodException e2) {
                        log.debug("No JavaPlugin constructor found for {}", implClassName);
                        continue;
                    } catch (Exception e2) {
                        final Throwable cause = e2.getCause() != null ? e2.getCause() : e2;
                        log.warn("Failed to instantiate {} with JavaPlugin constructor: {} - {}", implClassName, 
                            cause.getClass().getSimpleName(), cause.getMessage() != null ? cause.getMessage() : "null");
                        log.debug("Full exception:", e2);
                        continue;
                    }
                } catch (java.lang.reflect.InvocationTargetException e) {
                    // Constructor threw an exception - unwrap it
                    final Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.warn("Constructor of {} threw an exception: {} - {}", implClassName, 
                        cause.getClass().getSimpleName(), cause.getMessage() != null ? cause.getMessage() : "null");
                    log.debug("Full exception:", e);
                    continue;
                } catch (Exception e) {
                    final Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.warn("Failed to instantiate {} with no-args constructor: {} - {}", implClassName, 
                        cause.getClass().getSimpleName(), cause.getMessage() != null ? cause.getMessage() : "null");
                    log.debug("Full exception:", e);
                    continue;
                }
            } catch (ClassNotFoundException e) {
                log.debug("Could not find {} in classloader {}", implClassName, classLoader.getClass().getSimpleName());
                continue;
            } catch (Exception e) {
                log.debug("Error trying to instantiate {} with classloader {}: {}", implClassName, classLoader.getClass().getSimpleName(), e.getMessage());
                continue;
            }
        }
        
        log.warn("Could not find or instantiate Impl class: {} (tried {} classloaders)", implClassName, classLoaders.length);
        return null;
    }
    
    private record Provider<T extends GameMechanic<?>>(
            Class<T> gameMechanic, Supplier<T> supplier, ServicePriority priority) implements Comparable<Provider<?>> {
        @Override
        public int compareTo(@NonNull final Provider<?> other) {
            return Integer.compare(this.priority.ordinal(), other.priority.ordinal());
        }
    }
}
