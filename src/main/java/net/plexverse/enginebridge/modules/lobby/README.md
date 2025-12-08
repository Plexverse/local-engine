# LobbyModule

## Overview

The `LobbyModule` provides functionality for managing game lobbies. It handles lobby creation, player spawn management, and basic lobby protection features like damage prevention and boundary enforcement.

## Features

- **Active Lobby Management**: Set and manage the active lobby
- **Basic Lobby Creation**: Create basic lobbies with standard protection features
- **Spawn Management**: Automatic teleportation to spawn locations (supports data points)
- **Damage Prevention**: Players in lobby cannot take damage
- **Hunger Prevention**: Players in lobby cannot lose hunger
- **Item Pickup Prevention**: Players in lobby cannot pick up items
- **Boundary Enforcement**: Players too far from spawn are teleported back
- **Game State Integration**: Automatically teleports players to lobby when games end

## Local Implementation

This implementation provides a local lobby system that:
- Uses `MineplexWorld` for lobby worlds
- Supports spawn locations via world data points (key: "SPAWN")
- Integrates with `MineplexGameModule` to handle game state changes
- Provides basic protection features for lobby gameplay

### Differences from Official Implementation

- **No Remote Service**: Official implementation may use remote services for lobby management
- **Local World Management**: Uses local `MineplexWorldModule` for world management
- **Simplified Integration**: Basic integration with game state system

## Configuration

The LobbyModule does not require any configuration. It works automatically once a lobby is set via `setActiveLobby()`.

## Usage

### Creating and Setting a Lobby

```java
import com.mineplex.studio.sdk.modules.lobby.LobbyModule;
import com.mineplex.studio.sdk.modules.lobby.StudioLobby;
import com.mineplex.studio.sdk.modules.world.MineplexWorld;
import com.mineplex.studio.sdk.modules.world.MineplexWorldModule;
import net.plexverse.enginebridge.modules.ModuleManager;

// Get the modules
LobbyModule lobbyModule = ModuleManager.getInstance()
    .getRegisteredModule(LobbyModule.class);
MineplexWorldModule worldModule = ModuleManager.getInstance()
    .getRegisteredModule(MineplexWorldModule.class);

// Create or load a lobby world
MineplexWorld lobbyWorld = worldModule.createMineplexWorld(
    MineplexWorldConfig.builder()
        .inMemoryOnly(true)
        .worldCreationConfig(WorldCreationConfig.builder()
            .worldTemplate("lobby")
            .build())
        .build(),
    "lobby-world"
);

// Create a basic lobby
StudioLobby lobby = lobbyModule.createBasicLobby(lobbyWorld);

// Set it as the active lobby
lobbyModule.setActiveLobby(lobby);
```

### Checking if Player is in Lobby

```java
LobbyModule lobbyModule = ModuleManager.getInstance()
    .getRegisteredModule(LobbyModule.class);

Optional<StudioLobby> activeLobby = lobbyModule.getActiveLobby();
if (activeLobby.isPresent()) {
    StudioLobby lobby = activeLobby.get();
    boolean inLobby = lobby.isInLobby(player);
    
    if (inLobby) {
        // Player is in the lobby
    }
}
```

### Setting Spawn Locations

Spawn locations can be configured via world data points. Create a `dataPoints.yaml` or `dataPoints.json` file in your lobby world directory:

**dataPoints.yaml:**
```yaml
SPAWN:
  - x: 0.5
    y: 64
    z: 0.5
    yaw: 0
    pitch: 0
```

If no spawn data points are found, the lobby will use the world's default spawn location.

## API Compatibility

This implementation is fully compatible with the official `LobbyModule` interface:

- ✅ `getActiveLobby()` - Get the active lobby
- ✅ `setActiveLobby(StudioLobby)` - Set the active lobby
- ✅ `createBasicLobby(MineplexWorld)` - Create a basic lobby
- ✅ `setup()` - Initialize module
- ✅ `teardown()` - Cleanup module

## BasicStudioLobby Features

The `BasicStudioLobby` implementation provides:

- **Spawn Management**: Teleports players to spawn on join or when too far away
- **Damage Protection**: Cancels all damage events in the lobby
- **Hunger Protection**: Prevents hunger loss in the lobby
- **Item Pickup Prevention**: Prevents item pickup in the lobby
- **Game State Integration**: Automatically returns players to lobby when games end
- **Player Setup**: Sets players to adventure mode, full health, and clears inventory

## Requirements

- **MineplexWorldModule**: Required for creating and managing lobby worlds
- **MineplexGameModule**: Optional, but recommended for game state integration
- **World Data Points**: Optional, for custom spawn locations (key: "SPAWN")

## Troubleshooting

### Players Not Teleporting to Lobby

- **Check active lobby**: Ensure `setActiveLobby()` was called
- **Check world**: Verify the lobby world is loaded
- **Check spawn**: Ensure spawn location is accessible (check data points or world spawn)

### Lobby Protection Not Working

- **Check event registration**: Ensure the lobby was set via `setActiveLobby()` (events are registered automatically)
- **Check world**: Verify players are in the correct world
- **Check game state**: If `MineplexGameModule` is not available, lobby behavior may differ

### World Not Releasing

- **Check teardown**: Ensure `teardown()` is called on the lobby module
- **Check world module**: Verify `MineplexWorldModule` is available

## Links

- [Official LobbyModule Documentation](https://docs.mineplex.com/docs/sdk/features/lobby)

