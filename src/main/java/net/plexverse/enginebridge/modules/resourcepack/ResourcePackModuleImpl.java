package net.plexverse.enginebridge.modules.resourcepack;

import com.google.common.collect.ImmutableMap;
import com.mineplex.studio.sdk.modules.resourcepack.ResourcePack;
import com.mineplex.studio.sdk.modules.resourcepack.ResourcePackModule;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Local implementation of ResourcePackModule.
 * Serves resource packs from a local directory via HTTP server.
 * 
 * Note: This implementation uses a simple HTTP server to serve resource packs locally.
 * For production use, use the official Studio Engine.
 */
@Slf4j
@RequiredArgsConstructor
public class ResourcePackModuleImpl implements ResourcePackModule {
    
    private static final Path RESOURCE_PACK_DIR = Paths.get("config", "resource-packs");
    private static final String DEFAULT_HTTP_BIND_HOST = "0.0.0.0";
    private static final String DEFAULT_HTTP_URL_HOST = "localhost";
    private static final int DEFAULT_HTTP_PORT = 8080;
    
    private final JavaPlugin plugin;
    private final Map<String, ResourcePack> resourcePacks = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private LocalResourcePackHttpServer httpServer;
    private String httpBindHost;
    private String httpUrlHost;
    private int httpPort;
    
    private void loadResourcePacks() {
        resourcePacks.clear();
        
        if (!RESOURCE_PACK_DIR.toFile().exists()) {
            try {
                Files.createDirectories(RESOURCE_PACK_DIR);
                log.info("Created resource pack directory: {}", RESOURCE_PACK_DIR);
            } catch (IOException e) {
                log.error("Failed to create resource pack directory", e);
                return;
            }
        }
        
        final File[] packFiles = RESOURCE_PACK_DIR.toFile().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".zip"));
        
        if (packFiles == null) {
            log.warn("Resource pack directory does not exist or is not accessible: {}", RESOURCE_PACK_DIR);
            return;
        }
        
        for (final File packFile : packFiles) {
            try {
                final String packName = packFile.getName().replace(".zip", "");
                final byte[] packData = Files.readAllBytes(packFile.toPath());
                final String sha1 = calculateSHA1(packData);
                final UUID packUUID = UUID.nameUUIDFromBytes(packName.getBytes());
                
                // Generate URL pointing to local HTTP server
                final String url = String.format("http://%s:%d/resourcepacks/%s", httpUrlHost, httpPort, packFile.getName());
                
                final ResourcePack resourcePack = ResourcePack.builder()
                        .name(packName)
                        .url(url)
                        .uuid(packUUID)
                        .sha1(sha1)
                        .build();
                
                resourcePacks.put(packName, resourcePack);
                log.info("Loaded resource pack: {} (SHA1: {})", packName, sha1);
            } catch (Exception e) {
                log.error("Failed to load resource pack: {}", packFile.getName(), e);
            }
        }
    }
    
    private String calculateSHA1(final byte[] data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            final byte[] hash = digest.digest(data);
            final StringBuilder hexString = new StringBuilder();
            for (final byte b : hash) {
                final String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate SHA1 hash", e);
            return "";
        }
    }
    
    @Override
    public void setup() {
        final FileConfiguration config = plugin.getConfig();
        httpBindHost = config.getString("modules.resourcepack.http-bind-host", DEFAULT_HTTP_BIND_HOST);
        httpUrlHost = config.getString("modules.resourcepack.http-url-host", DEFAULT_HTTP_URL_HOST);
        httpPort = config.getInt("modules.resourcepack.http-port", DEFAULT_HTTP_PORT);
        
        try {
            // Start HTTP server to serve resource packs
            // Bind to httpBindHost (0.0.0.0 for Docker) but use httpUrlHost in URLs
            httpServer = new LocalResourcePackHttpServer(new InetSocketAddress(httpBindHost, httpPort), RESOURCE_PACK_DIR);
            httpServer.start();
            log.info("Started resource pack HTTP server on {}:{} (URLs will use {}:{})", httpBindHost, httpPort, httpUrlHost, httpPort);
            
            // Load resource packs from directory
            loadResourcePacks();
            
            log.info("Loaded {} resource pack(s)", resourcePacks.size());
        } catch (Exception e) {
            log.error("Failed to start resource pack HTTP server", e);
        }
    }
    
    @Override
    public void teardown() {
        if (httpServer != null) {
            try {
                httpServer.stop(0);
                log.info("Stopped resource pack HTTP server");
            } catch (Exception e) {
                log.error("Failed to stop resource pack HTTP server", e);
            }
            httpServer = null;
        }
        
        resourcePacks.clear();
    }
    
    @Override
    @NonNull
    public Optional<ResourcePack> get(@NonNull final String name) {
        return Optional.ofNullable(resourcePacks.get(name));
    }
    
    @Override
    @NonNull
    public ImmutableMap<String, ResourcePack> getAll() {
        return ImmutableMap.copyOf(resourcePacks);
    }
}

