package net.plexverse.enginebridge.modules.world;

import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import com.mineplex.studio.sdk.modules.data.DataStorageModule;
import com.mineplex.studio.sdk.modules.world.MineplexWorld;
import com.mineplex.studio.sdk.modules.world.MineplexWorldModule;
import com.mineplex.studio.sdk.modules.world.config.MineplexWorldConfig;
import com.mineplex.studio.sdk.modules.world.config.PersistentWorldConfig;
import com.mineplex.studio.sdk.modules.world.config.WorldCreationConfig;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Local implementation of MineplexWorldModule.
 * Provides world creation and management using local file storage and DataStorageModule for persistence.
 * 
 * Note: This is a simplified implementation that stores worlds locally and uses DataStorageModule
 * for persistent world storage. For production use, use the official Studio Engine.
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(MineplexWorldModule.class)
public class WorldModuleImpl implements MineplexWorldModule {
    
    private static final Path WORLD_STORAGE_PATH = Paths.get("worlds");
    private static final Path TEMPLATE_PATH = Paths.get("assets", "world-templates");
    
    private final JavaPlugin plugin;
    private final Map<String, MineplexWorld> loadedWorlds = new HashMap<>();
    
    private DataStorageModule getDataStorageModule() {
        return MineplexModuleManager.getRegisteredModule(DataStorageModule.class);
    }
    
    private String createWorldName() {
        return "world-" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private File getWorldTemplateFile(final String templateName) {
        return TEMPLATE_PATH.resolve(templateName + ".zip").toFile();
    }
    
    private void unzipWorld(final File templateFile, final File worldDirectory) throws IOException {
        if (!templateFile.exists()) {
            throw new IOException("Template file does not exist: " + templateFile.getAbsolutePath());
        }
        
        if (!worldDirectory.exists()) {
            worldDirectory.mkdirs();
        }
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(templateFile.toPath()))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                final Path filePath = worldDirectory.toPath().resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath);
                }
                
                entry = zis.getNextEntry();
            }
        }
        
        // Cleanup files that could prevent world loading
        Files.deleteIfExists(worldDirectory.toPath().resolve("uid.dat"));
        Files.deleteIfExists(worldDirectory.toPath().resolve("session.lock"));
    }
    
    private void zipWorld(final File worldDirectory, final File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            final Path worldPath = worldDirectory.toPath();
            Files.walk(worldPath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            final String relativePath = worldPath.relativize(path).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(relativePath));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            log.error("Failed to zip file: " + path, e);
                        }
                    });
        }
    }
    
    @Override
    public void setup() {
        try {
            Files.createDirectories(WORLD_STORAGE_PATH);
            Files.createDirectories(TEMPLATE_PATH);
            log.info("WorldModule initialized with local storage at {}", WORLD_STORAGE_PATH);
        } catch (IOException e) {
            log.error("Failed to create world storage directories", e);
            throw new RuntimeException("Failed to initialize WorldModule", e);
        }
    }
    
    @Override
    public void teardown() {
        // Release all loaded worlds
        for (final MineplexWorld world : Set.copyOf(loadedWorlds.values())) {
            releaseWorld(world);
        }
        loadedWorlds.clear();
        log.info("WorldModule torn down");
    }
    
    @Override
    @NonNull
    public Optional<MineplexWorld> getLoadedMineplexWorld(@NonNull final String id) {
        return Optional.ofNullable(loadedWorlds.get(id));
    }
    
    @Override
    @NonNull
    public MineplexWorld createMineplexWorld(@NonNull final MineplexWorldConfig config, final String id) {
        final String worldId = id != null ? id : UUID.randomUUID().toString();
        final String worldName = createWorldName();
        final WorldCreator worldCreator = new WorldCreator(worldName);
        
        // Handle world template
        if (config.getWorldCreationConfig().getWorldTemplate() != null) {
            final File templateFile = getWorldTemplateFile(config.getWorldCreationConfig().getWorldTemplate());
            final File worldDirectory = new File(worldName);
            
            if (templateFile.exists()) {
                try {
                    unzipWorld(templateFile, worldDirectory);
                    log.info("Loaded world template {} into {}", config.getWorldCreationConfig().getWorldTemplate(), worldName);
                } catch (Exception e) {
                    log.error("Failed to unzip world template: {}", templateFile.getName(), e);
                }
            } else {
                log.warn("World template not found: {}", templateFile.getAbsolutePath());
            }
        }
        
        // Apply world creator decorator if provided
        if (config.getWorldCreationConfig().getWorldCreatorDecorator() != null) {
            config.getWorldCreationConfig().getWorldCreatorDecorator().accept(worldCreator);
        }
        
        // Create the world
        final World world = Bukkit.createWorld(worldCreator);
        if (world == null) {
            throw new RuntimeException("Failed to create world: " + worldCreator.name());
        }
        
        final MineplexWorld mineplexWorld = LocalMineplexWorld.builder()
                .id(worldId)
                .worldConfig(config)
                .minecraftWorld(world)
                .build();
        
        loadedWorlds.put(worldId, mineplexWorld);
        log.info("Created MineplexWorld {} with name {}", worldId, worldName);
        
        return mineplexWorld;
    }
    
    @Override
    @NonNull
    public CompletableFuture<Optional<MineplexWorld>> loadMineplexWorld(
            @NonNull final String worldBucket,
            @NonNull final String id,
            @NonNull final WorldCreationConfig worldCreationConfig) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                final DataStorageModule dataStorage = getDataStorageModule();
                if (dataStorage == null) {
                    log.warn("DataStorageModule not available, cannot load persistent world");
                    return Optional.<MineplexWorld>empty();
                }
                
                // Load world data from DataStorageModule
                final Optional<WorldBinaryData> worldDataOpt = dataStorage.loadBinaryData(WorldBinaryData.class, worldBucket + ":" + id);
                
                if (worldDataOpt.isEmpty()) {
                    log.debug("No persistent world found for bucket {} and id {}", worldBucket, id);
                    return Optional.<MineplexWorld>empty();
                }
                
                final WorldBinaryData worldData = worldDataOpt.get();
                final byte[] worldBytes = worldData.getData();
                
                if (worldBytes == null || worldBytes.length == 0) {
                    log.debug("World data is empty for bucket {} and id {}", worldBucket, id);
                    return Optional.<MineplexWorld>empty();
                }
                
                // Save zip to temporary file
                final File tempZip = File.createTempFile("world-" + id, ".zip");
                Files.write(tempZip.toPath(), worldBytes);
                
                // Unzip to world directory
                final String worldName = createWorldName();
                final File worldDirectory = new File(worldName);
                unzipWorld(tempZip, worldDirectory);
                
                // Delete temp file
                tempZip.delete();
                
                // Create world on main thread
                return CompletableFuture.supplyAsync(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        final WorldCreator worldCreator = new WorldCreator(worldName);
                        if (worldCreationConfig.getWorldCreatorDecorator() != null) {
                            worldCreationConfig.getWorldCreatorDecorator().accept(worldCreator);
                        }
                        
                        final World world = Bukkit.createWorld(worldCreator);
                        if (world == null) {
                            log.error("Failed to create world from persistent data: {}", worldName);
                            return;
                        }
                        
                        final MineplexWorldConfig worldConfig = MineplexWorldConfig.builder()
                                .persistentWorldConfig(PersistentWorldConfig.builder()
                                        .worldBucket(worldBucket)
                                        .build())
                                .worldCreationConfig(worldCreationConfig)
                                .worldRegionType(MineplexWorldConfig.WorldRegionFormatType.ANVIL)
                                .inMemoryOnly(false)
                                .build();
                        
                        final MineplexWorld mineplexWorld = LocalMineplexWorld.builder()
                                .id(id)
                                .worldConfig(worldConfig)
                                .minecraftWorld(world)
                                .build();
                        
                        loadedWorlds.put(id, mineplexWorld);
                        log.info("Loaded persistent world {} from bucket {}", id, worldBucket);
                    });
                    
                    // Wait a bit for world to be created
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    return Optional.ofNullable(loadedWorlds.get(id));
                }).join();
                
            } catch (Exception e) {
                log.error("Failed to load persistent world from bucket {} with id {}", worldBucket, id, e);
                return Optional.<MineplexWorld>empty();
            }
        });
    }
    
    @Override
    @NonNull
    public CompletableFuture<MineplexWorld> loadOrCreateMineplexWorld(
            @NonNull final String worldBucket,
            @NonNull final String id,
            @NonNull final MineplexWorldConfig config) {
        
        return loadMineplexWorld(worldBucket, id, config.getWorldCreationConfig())
                .thenApply(worldOptional -> worldOptional.orElseGet(() -> createMineplexWorld(config, id)));
    }
    
    @Override
    public void releaseWorld(@NonNull final MineplexWorld mineplexWorld) {
        final boolean save = mineplexWorld.getWorldConfig().getPersistentWorldConfig() != null;
        
        // Unload the world
        final World world = mineplexWorld.getMinecraftWorld();
        if (!Bukkit.unloadWorld(world, save)) {
            log.warn("Failed to unload world {} on first try", world.getName());
            // Try again asynchronously
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!Bukkit.unloadWorld(world, save)) {
                    log.error("Failed to unload world {} after retry", world.getName());
                }
            }, 20L);
        }
        
        loadedWorlds.remove(mineplexWorld.getId());
        
        // Save to persistent storage if needed
        if (save) {
            final PersistentWorldConfig persistentConfig = mineplexWorld.getWorldConfig().getPersistentWorldConfig();
            if (persistentConfig != null) {
                try {
                    final String worldDirectory = world.getName();
                    final File worldDir = new File(worldDirectory);
                    
                    if (worldDir.exists()) {
                        // Zip the world
                        final File zipFile = File.createTempFile("world-" + mineplexWorld.getId(), ".zip");
                        zipWorld(worldDir, zipFile);
                        
                        // Save to DataStorageModule
                        final DataStorageModule dataStorage = getDataStorageModule();
                        if (dataStorage != null) {
                            final byte[] worldData = Files.readAllBytes(zipFile.toPath());
                            final WorldBinaryData binaryData = new WorldBinaryData(persistentConfig.getWorldBucket(), mineplexWorld.getId());
                            binaryData.setData(worldData);
                            dataStorage.storeBinaryData(binaryData);
                            log.info("Saved persistent world {} to bucket {}", mineplexWorld.getId(), persistentConfig.getWorldBucket());
                        }
                        
                        // Cleanup
                        zipFile.delete();
                        deleteDirectory(worldDir);
                    }
                } catch (Exception e) {
                    log.error("Failed to save persistent world {}", mineplexWorld.getId(), e);
                }
            }
        } else {
            // Delete in-memory world directory
            final File worldDir = new File(world.getName());
            if (worldDir.exists()) {
                deleteDirectory(worldDir);
            }
        }
        
        log.info("Released world {}", mineplexWorld.getId());
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> deleteWorld(@NonNull final String worldBucket, @NonNull final String id) {
        return CompletableFuture.runAsync(() -> {
            // Unload if currently loaded
            getLoadedMineplexWorld(id).ifPresent(this::releaseWorld);
            
            // Delete from persistent storage
            final DataStorageModule dataStorage = getDataStorageModule();
            if (dataStorage != null) {
                dataStorage.deleteBinaryData(WorldBinaryData.class, worldBucket + ":" + id);
                log.info("Deleted persistent world {} from bucket {}", id, worldBucket);
            }
        });
    }
    
    private void deleteDirectory(final File directory) {
        if (directory.exists()) {
            try {
                Files.walk(directory.toPath())
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error("Failed to delete file: {}", path, e);
                            }
                        });
            } catch (IOException e) {
                log.error("Failed to delete world directory: {}", directory, e);
            }
        }
    }
}

