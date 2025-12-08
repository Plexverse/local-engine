# ResourcePackModule

## Overview

The `ResourcePackModule` provides functionality for managing and serving resource packs to players. This local implementation serves resource packs from a local directory via an HTTP server, making it suitable for local development and testing.

## Features

- **Local Resource Pack Serving**: Serves resource packs from the `config/resource-packs/` directory
- **HTTP Server**: Built-in HTTP server for serving resource packs over HTTP
- **SHA1 Hashing**: Automatically calculates SHA1 hashes for resource pack validation
- **Docker Support**: Configurable bind host and URL host for Docker environments
- **Automatic Discovery**: Automatically discovers and loads all `.zip` files in the resource pack directory

## Local Implementation

This implementation uses a simple HTTP server (Java's built-in `HttpServer`) to serve resource packs locally. Resource packs are loaded from the `config/resource-packs/` directory and served at `http://{http-url-host}:{http-port}/resourcepacks/{filename}`.

### Differences from Official Implementation

- **Storage**: Official implementation fetches resource packs from a remote gRPC service
- **Local**: Loads resource packs from local file system and serves via HTTP
- **No Remote Service**: Does not require external resource pack service

## Configuration

The ResourcePackModule can be configured in `config.yml`:

```yaml
modules:
  resourcepack:
    # Enable the built-in HTTP server for serving resource packs
    # Default: false
    # Set to true to enable automatic resource pack serving
    http-server-enabled: false
    
    # HTTP bind host - where the server listens for connections
    # Default: 0.0.0.0 (all interfaces, good for Docker)
    # Use 0.0.0.0 to accept connections from outside the container
    http-bind-host: "0.0.0.0"
    
    # HTTP URL host - the hostname/IP used in resource pack URLs sent to clients
    # Default: localhost
    # For Docker: set this to your container's hostname or external IP
    # Examples: "localhost", "192.168.1.100", "minecraft.example.com"
    http-url-host: "localhost"
    
    # HTTP port for serving resource packs locally
    # Default: 8080
    http-port: 8080
```

### HTTP Server Disabled

When `http-server-enabled: false` (default), the built-in HTTP server will not start. Resource packs will still be loaded and registered, but you'll need to serve them via an external HTTP server. The URLs generated will use the configured `http-url-host` and `http-port`, so ensure your external server matches these settings or manually update resource pack URLs in your code.

### Docker Configuration

For Docker environments, configure as follows:

```yaml
modules:
  resourcepack:
    http-bind-host: "0.0.0.0"  # Listen on all interfaces
    http-url-host: "minecraft.example.com"  # Your Docker hostname or external IP
    http-port: 8080
```

## Usage

### Setting Up Resource Packs

1. Create the resource pack directory:
   ```
   config/resource-packs/
   ```

2. Place your resource pack `.zip` files in the directory:
   ```
   config/resource-packs/
     ├── my-pack.zip
     ├── custom-textures.zip
     └── ...
   ```

3. Restart the server or reload the plugin. The module will automatically:
   - Discover all `.zip` files
   - Calculate SHA1 hashes
   - Generate URLs pointing to the HTTP server
   - Make them available via the `ResourcePackModule` API

### Using the Module

```java
import com.mineplex.studio.sdk.modules.resourcepack.ResourcePackModule;
import com.mineplex.studio.sdk.modules.resourcepack.ResourcePack;
import net.plexverse.enginebridge.modules.ModuleManager;

// Get the ResourcePackModule
ResourcePackModule resourcePackModule = ModuleManager.getInstance()
    .getRegisteredModule(ResourcePackModule.class);

// Get a specific resource pack by name
Optional<ResourcePack> pack = resourcePackModule.get("my-pack");
if (pack.isPresent()) {
    ResourcePack resourcePack = pack.get();
    String url = resourcePack.getUrl();
    String sha1 = resourcePack.getSha1();
    UUID uuid = resourcePack.getUuid();
    
    // Send to player
    player.setResourcePack(url, sha1);
}

// Get all resource packs
ImmutableMap<String, ResourcePack> allPacks = resourcePackModule.getAll();
```

### Sending Resource Packs to Players

```java
ResourcePackModule resourcePackModule = ModuleManager.getInstance()
    .getRegisteredModule(ResourcePackModule.class);

Optional<ResourcePack> pack = resourcePackModule.get("my-pack");
if (pack.isPresent()) {
    ResourcePack resourcePack = pack.get();
    player.setResourcePack(
        resourcePack.getUrl(),
        resourcePack.getSha1()
    );
}
```

## API Compatibility

This implementation is fully compatible with the official `ResourcePackModule` interface:

- ✅ `get(String name)` - Get resource pack by name
- ✅ `getAll()` - Get all resource packs
- ✅ `setup()` - Initialize module and start HTTP server
- ✅ `teardown()` - Stop HTTP server and cleanup

## Requirements

- **Resource Pack Files**: Place `.zip` resource pack files in `config/resource-packs/`
- **HTTP Port**: Ensure the configured port is available and not blocked by firewall
- **Network Access**: For Docker, ensure the HTTP server port is exposed

## Security Considerations

- The HTTP server includes basic security measures to prevent directory traversal attacks
- Only files in the `config/resource-packs/` directory can be served
- File names are validated to prevent path manipulation

## Troubleshooting

### Resource Packs Not Loading

- **Check directory**: Ensure `config/resource-packs/` exists and contains `.zip` files
- **Check logs**: Look for errors during resource pack loading
- **Check HTTP server**: Verify the HTTP server started successfully in logs
- **Check port**: Ensure the configured port is not in use by another service

### Docker Issues

- **Can't access from outside container**: Set `http-bind-host: "0.0.0.0"`
- **Wrong URL in resource pack**: Set `http-url-host` to the correct hostname/IP that clients can reach
- **Port not accessible**: Ensure the port is exposed in your Docker configuration

### SHA1 Hash Issues

- If SHA1 calculation fails, check the logs for errors
- Ensure resource pack files are valid `.zip` files

## Links

- [Official ResourcePackModule Documentation](https://docs.mineplex.com/docs/sdk/features/resourcepack)

