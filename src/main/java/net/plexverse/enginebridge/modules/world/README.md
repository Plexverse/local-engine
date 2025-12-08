# WorldModule

## Overview

The `WorldModule` provides functionality for creating, loading, and managing Minecraft worlds. It supports both in-memory worlds (temporary) and persistent worlds (saved to storage), as well as world templates and data points.

## Features

- **World Creation**: Create worlds from templates or custom generation
- **Persistent Worlds**: Save and load worlds from DataStorageModule
- **World Templates**: Load worlds from template files in `assets/world-templates/`
- **Data Points**: Support for world data points (locations) via YAML/JSON files
- **World Management**: Track loaded worlds and release them when no longer needed
- **World Deletion**: Delete persistent worlds from storage

## Local Implementation

This implementation uses:
- **Local File Storage**: World templates stored in `assets/world-templates/`
- **DataStorageModule**: Persistent world storage using MongoDB
- **Local Data Points**: Reads data points from YAML/JSON files in world directories
- **World Templates**: Supports `.zip` world template files

### Differences from Official Implementation

- **Storage Backend**: Official uses CustomDataStoreClient with external service
- **Local**: Uses DataStorageModule (MongoDB) for persistent storage
- **No Remote Service**: Does not require external world storage service
- **Simplified Data Points**: Basic YAML/JSON parsing (no complex format support)

## Configuration

The WorldModule does not require any configuration. It uses:
- `assets/world-templates/` for world template files
- `DataStorageModule` for persistent world storage (configured separately)

## Usage

### Creating an In-Memory World

```java
import com.mineplex.studio.sdk.modules.world.MineplexWorldModule;
import com.mineplex.studio.sdk.modules.world.MineplexWorld;
import com.mineplex.studio.sdk.modules.world.config.MineplexWorldConfig;
import com.mineplex.studio.sdk.modules.world.config.WorldCreationConfig;
import net.plexverse.enginebridge.modules.ModuleManager;

MineplexWorldModule worldModule = ModuleManager.getInstance()
    .getRegisteredModule(MineplexWorldModule.class);

// Create an in-memory world from a template
MineplexWorld world = worldModule.createMineplexWorld(
    MineplexWorldConfig.builder()
        .inMemoryOnly(true)
        .worldCreationConfig(WorldCreationConfig.builder()
            .worldTemplate("my-template")
            .build())
        .build(),
    "my-world-id"
);

// Use the world
World bukkitWorld = world.getMinecraftWorld();
```

### Loading a Persistent World

```java
MineplexWorldModule worldModule = ModuleManager.getInstance()
    .getRegisteredModule(MineplexWorldModule.class);

// Load a persistent world
CompletableFuture<Optional<MineplexWorld>> future = worldModule.loadMineplexWorld(
    "world-bucket",
    "world-id",
    WorldCreationConfig.builder()
        .worldTemplate("default")
        .build()
);

future.thenAccept(worldOpt -> {
    if (worldOpt.isPresent()) {
        MineplexWorld world = worldOpt.get();
        // Use the world
    }
});
```

### Loading or Creating a World

```java
// Load if exists, otherwise create
CompletableFuture<MineplexWorld> future = worldModule.loadOrCreateMineplexWorld(
    "world-bucket",
    "world-id",
    MineplexWorldConfig.builder()
        .persistentWorldConfig(PersistentWorldConfig.builder()
            .worldBucket("world-bucket")
            .build())
        .worldCreationConfig(WorldCreationConfig.builder()
            .worldTemplate("default")
            .build())
        .build()
);

future.thenAccept(world -> {
    // World is loaded or created
});
```

### Releasing a World

```java
// Release a world (saves if persistent, unloads if in-memory)
worldModule.releaseWorld(world);
```

### Using World Data Points

Data points are automatically loaded from `dataPoints.yaml` or `dataPoints.json` in the world directory:

**dataPoints.yaml:**
```yaml
SPAWN:
  - x: 0.5
    y: 64
    z: 0.5
    yaw: 0
    pitch: 0
TELEPORT:
  - x: 100.5
    y: 70
    z: 200.5
    yaw: 90
    pitch: 0
```

```java
// Get all data points
Map<String, List<Location>> allPoints = world.getDataPoints();

// Get specific data points
List<Location> spawns = world.getDataPoints("SPAWN");
if (!spawns.isEmpty()) {
    Location spawn = spawns.get(0);
    player.teleport(spawn);
}
```

## API Compatibility

This implementation is fully compatible with the official `MineplexWorldModule` interface:

- ✅ `getLoadedMineplexWorld(String id)` - Get a loaded world by ID
- ✅ `createMineplexWorld(MineplexWorldConfig, String id)` - Create a new world
- ✅ `loadMineplexWorld(String bucket, String id, WorldCreationConfig)` - Load a persistent world
- ✅ `loadOrCreateMineplexWorld(String bucket, String id, MineplexWorldConfig)` - Load or create
- ✅ `releaseWorld(MineplexWorld)` - Release a world (save if persistent)
- ✅ `deleteWorld(String bucket, String id)` - Delete a persistent world
- ✅ `setup()` - Initialize module
- ✅ `teardown()` - Cleanup module

## Requirements

- **World Templates**: Place `.zip` world files in `assets/world-templates/`
- **DataStorageModule**: Required for persistent world storage (optional for in-memory only)
- **Data Points Files**: Optional YAML/JSON files in world directories for data points

## World Templates

World templates should be `.zip` files containing a complete Minecraft world directory structure:

```
assets/world-templates/
  ├── my-template.zip
  ├── lobby.zip
  └── ...
```

The zip file should contain:
- `region/` directory (for Anvil format worlds)
- `level.dat` file
- Other world files as needed

## Data Points Format

Data points can be defined in YAML or JSON format:

**YAML (dataPoints.yaml):**
```yaml
SPAWN:
  - x: 0.5
    y: 64
    z: 0.5
    yaw: 0
    pitch: 0
TELEPORT:
  - x: 100.5
    y: 70
    z: 200.5
```

**JSON (dataPoints.json):**
```json
{
  "SPAWN": [
    {
      "x": 0.5,
      "y": 64,
      "z": 0.5,
      "yaw": 0,
      "pitch": 0
    }
  ]
}
```

## Troubleshooting

### World Not Creating

- **Check template**: Ensure template file exists in `assets/world-templates/`
- **Check template format**: Verify the zip file contains a valid world structure
- **Check logs**: Look for errors during world creation

### Persistent World Not Loading

- **Check DataStorageModule**: Ensure DataStorageModule is loaded and configured
- **Check world exists**: Verify the world was previously saved
- **Check bucket/ID**: Ensure correct bucket and ID are used

### Data Points Not Loading

- **Check file format**: Ensure `dataPoints.yaml` or `dataPoints.json` exists in world directory
- **Check file syntax**: Verify YAML/JSON syntax is correct
- **Check file location**: File must be in the world's root directory

### World Not Releasing

- **Check teardown**: Ensure `teardown()` is called on the module
- **Check world state**: Verify world is not in use by other systems
- **Check DataStorageModule**: For persistent worlds, ensure DataStorageModule is available

## Links

- [Official WorldModule Documentation](https://docs.mineplex.com/docs/sdk/features/world)

