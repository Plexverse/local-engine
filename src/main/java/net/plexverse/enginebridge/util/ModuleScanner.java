package net.plexverse.enginebridge.util;

import com.mineplex.studio.sdk.modules.MineplexModule;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility class for scanning and instantiating local module implementations.
 * Automatically discovers classes in the modules package that implement MineplexModule interfaces
 * and have a constructor with JavaPlugin as the only argument.
 */
@Slf4j
public class ModuleScanner {
    
    private static final String MODULES_PACKAGE = "net.plexverse.enginebridge.modules";
    
    /**
     * Scans for and instantiates all local module implementations.
     *
     * @param plugin the JavaPlugin instance to pass to module constructors
     * @return list of instantiated module instances
     */
    @NotNull
    public static List<Object> scanAndInstantiateModules(@NotNull final JavaPlugin plugin) {
        final List<Object> modules = new ArrayList<>();
        
        try {
            final List<Class<?>> moduleClasses = findModuleClasses();
            
            for (final Class<?> moduleClass : moduleClasses) {
                try {
                    final Object instance = instantiateModule(moduleClass, plugin);
                    if (instance != null) {
                        modules.add(instance);
                        log.info("Discovered and instantiated module: {}", moduleClass.getSimpleName());
                    }
                } catch (final Exception e) {
                    log.warn("Failed to instantiate module {}: {}", moduleClass.getSimpleName(), e.getMessage());
                }
            }
        } catch (final Exception e) {
            log.error("Failed to scan for modules", e);
        }
        
        return modules;
    }
    
    /**
     * Finds all classes in the modules package that implement MineplexModule interfaces.
     *
     * @return list of module classes
     */
    @NotNull
    private static List<Class<?>> findModuleClasses() throws IOException, ClassNotFoundException {
        final List<Class<?>> classes = new ArrayList<>();
        final ClassLoader classLoader = ModuleScanner.class.getClassLoader();
        final String packagePath = MODULES_PACKAGE.replace('.', '/');
        
        // Get the resource URL for the package
        final Enumeration<URL> resources = classLoader.getResources(packagePath);
        
        while (resources.hasMoreElements()) {
            final URL resource = resources.nextElement();
            
            if (resource.getProtocol().equals("jar")) {
                // Handle JAR files
                final JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
                try (final JarFile jar = jarConnection.getJarFile()) {
                    final Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        final JarEntry entry = entries.nextElement();
                        final String name = entry.getName();
                        
                        if (name.startsWith(packagePath) && name.endsWith(".class") && !name.contains("$")) {
                            final String className = name.replace('/', '.').substring(0, name.length() - 6);
                            try {
                                final Class<?> clazz = Class.forName(className, false, classLoader);
                                
                                if (isModuleClass(clazz)) {
                                    classes.add(clazz);
                                }
                            } catch (final ClassNotFoundException | NoClassDefFoundError e) {
                                log.debug("Could not load class: {}", className, e);
                            }
                        }
                    }
                }
            } else {
                // Handle file system (development)
                final String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                final File directory = new File(filePath);
                if (directory.exists() && directory.isDirectory()) {
                    scanDirectory(directory, MODULES_PACKAGE, classes, classLoader);
                }
            }
        }
        
        return classes;
    }
    
    /**
     * Recursively scans a directory for class files.
     */
    private static void scanDirectory(
            @NotNull final File directory, 
            @NotNull final String packageName, 
            @NotNull final List<Class<?>> classes,
            @NotNull final ClassLoader classLoader) {
        final File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (final File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes, classLoader);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                try {
                    final String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                    final Class<?> clazz = Class.forName(className, false, classLoader);
                    
                    if (isModuleClass(clazz)) {
                        classes.add(clazz);
                    }
                } catch (final ClassNotFoundException | NoClassDefFoundError e) {
                    log.debug("Could not load class from file: {}", file.getName(), e);
                }
            }
        }
    }
    
    /**
     * Checks if a class is a module implementation.
     * A module class must:
     * 1. Not be an interface or abstract class
     * 2. Implement at least one interface that extends MineplexModule
     * 3. Have a constructor that takes JavaPlugin as the only argument
     */
    private static boolean isModuleClass(@NotNull final Class<?> clazz) {
        // Skip interfaces, abstract classes, and inner classes
        if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
            return false;
        }
        
        // Check if it implements any interface that extends MineplexModule
        boolean implementsModuleInterface = false;
        for (final Class<?> iface : clazz.getInterfaces()) {
            if (MineplexModule.class.isAssignableFrom(iface)) {
                implementsModuleInterface = true;
                break;
            }
        }
        
        if (!implementsModuleInterface) {
            return false;
        }
        
        // Check if it has a constructor with JavaPlugin as the only argument
        try {
            final Constructor<?> constructor = clazz.getConstructor(JavaPlugin.class);
            return constructor != null;
        } catch (final NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * Instantiates a module class using its JavaPlugin constructor.
     *
     * @param moduleClass the module class to instantiate
     * @param plugin the JavaPlugin instance to pass to the constructor
     * @return the instantiated module
     * @throws RuntimeException if instantiation fails
     */
    @NotNull
    private static Object instantiateModule(@NotNull final Class<?> moduleClass, @NotNull final JavaPlugin plugin) {
        try {
            final Constructor<?> constructor = moduleClass.getConstructor(JavaPlugin.class);
            return constructor.newInstance(plugin);
        } catch (final Exception e) {
            log.error("Failed to instantiate module {}: {}", moduleClass.getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Failed to instantiate module: " + moduleClass.getSimpleName(), e);
        }
    }
}

