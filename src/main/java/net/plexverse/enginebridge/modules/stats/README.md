# StatsModule

Local implementation of Mineplex's StatsModule for Plexverse Engine Bridge using DataStorageModule.

## Overview

The StatsModule provides player statistics management, allowing you to track, retrieve, award, and modify player stats. It implements the same API as the official Mineplex Studio Engine StatsModule, allowing seamless migration between local and production environments. The local implementation uses DataStorageModule (MongoDB) instead of external gRPC services.

## Features

- **Player Statistics Management**: Store and retrieve player statistics
- **Stat Operations**: Get, set, award (increment), and delete player stats
- **Async Support**: All operations have both synchronous and asynchronous variants
- **MongoDB Backend**: Uses DataStorageModule to store stats in MongoDB
- **Full API Compatibility**: Matches the official StatsModule interface exactly

## Local Implementation Details

### DataStorageModule Dependency

The local implementation depends on DataStorageModule to store player statistics. Stats are stored in MongoDB using the `player_stats` collection. The module retrieves DataStorageModule on-demand, ensuring it works regardless of module registration order.

### Storage Format

Player statistics are stored as structured data in MongoDB:
- **Collection**: `player_stats`
- **Key**: Player UUID (as string)
- **Data**: `PlayerStatsData` object containing a map of stat names to values

## Usage

### Getting the StatsModule

```java
import net.plexverse.enginebridge.modules.ModuleManager;
import com.mineplex.studio.sdk.modules.stats.StatsModule;

// Get the StatsModule instance
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);
```

### Getting Player Stats

#### Synchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

// Get stats by player ID
Map<String, Long> stats = statsModule.getPlayerStats("player-uuid-here");

// Get stats by Player object
Map<String, Long> stats = statsModule.getPlayerStats(player);

// Access individual stats
Long kills = stats.get("kills");
Long deaths = stats.get("deaths");
```

#### Asynchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

// Get stats asynchronously
CompletableFuture<Map<String, Long>> future = statsModule.getPlayerStatsAsync("player-uuid-here");

future.thenAccept(stats -> {
    Long kills = stats.get("kills");
    System.out.println("Player has " + kills + " kills");
});
```

### Awarding Stats (Incrementing)

Award stats to increment existing values or create new ones:

#### Synchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

// Award stats by player ID
Map<String, Long> increments = new HashMap<>();
increments.put("kills", 1L);
increments.put("wins", 1L);

Map<String, Long> finalStats = statsModule.awardPlayerStats("player-uuid-here", increments);

// Award stats by Player object
Map<String, Long> finalStats = statsModule.awardPlayerStats(player, increments);
```

#### Asynchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

Map<String, Long> increments = new HashMap<>();
increments.put("kills", 1L);

CompletableFuture<Map<String, Long>> future = statsModule.awardPlayerStatsAsync("player-uuid-here", increments);

future.thenAccept(finalStats -> {
    System.out.println("Player now has " + finalStats.get("kills") + " kills");
});
```

### Setting Stats (Absolute Values)

Set stats to specific values (replaces existing values):

#### Synchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

Map<String, Long> stats = new HashMap<>();
stats.put("level", 10L);
stats.put("experience", 5000L);

// Set stats by player ID
statsModule.setPlayerStats("player-uuid-here", stats);

// Set stats by Player object
statsModule.setPlayerStats(player, stats);
```

#### Asynchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

Map<String, Long> stats = new HashMap<>();
stats.put("level", 10L);

CompletableFuture<Void> future = statsModule.setPlayerStatsAsync("player-uuid-here", stats);

future.thenRun(() -> {
    System.out.println("Stats updated successfully");
});
```

### Deleting Stats

Remove specific stats from a player:

#### Synchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

List<String> statsToDelete = Arrays.asList("old_stat", "temporary_stat");

// Delete stats by player ID
statsModule.deletePlayerStats("player-uuid-here", statsToDelete);

// Delete stats by Player object
statsModule.deletePlayerStats(player, statsToDelete);
```

#### Asynchronous

```java
StatsModule statsModule = ModuleManager.getRegisteredModule(StatsModule.class);

List<String> statsToDelete = Arrays.asList("old_stat");

CompletableFuture<Void> future = statsModule.deletePlayerStatsAsync("player-uuid-here", statsToDelete);

future.thenRun(() -> {
    System.out.println("Stats deleted successfully");
});
```

## Complete Example

```java
import net.plexverse.enginebridge.modules.ModuleManager;
import com.mineplex.studio.sdk.modules.stats.StatsModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class StatsTracker implements Listener {
    
    private final StatsModule statsModule;
    
    public StatsTracker() {
        this.statsModule = ModuleManager.getRegisteredModule(StatsModule.class);
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null) {
            // Award kill to killer
            Map<String, Long> increments = new HashMap<>();
            increments.put("kills", 1L);
            statsModule.awardPlayerStats(killer, increments);
            
            // Award death to victim
            increments.clear();
            increments.put("deaths", 1L);
            statsModule.awardPlayerStats(victim, increments);
        }
    }
    
    public void displayStats(Player player) {
        Map<String, Long> stats = statsModule.getPlayerStats(player);
        
        player.sendMessage("Your Stats:");
        player.sendMessage("Kills: " + stats.getOrDefault("kills", 0L));
        player.sendMessage("Deaths: " + stats.getOrDefault("deaths", 0L));
        
        if (stats.containsKey("kills") && stats.containsKey("deaths")) {
            long kills = stats.get("kills");
            long deaths = stats.get("deaths");
            double kdr = deaths > 0 ? (double) kills / deaths : kills;
            player.sendMessage("K/D Ratio: " + String.format("%.2f", kdr));
        }
    }
}
```

## Differences from Official Implementation

### Storage Backend

- **Official**: Uses gRPC PlayerStatsClient with external service
- **Local**: Uses DataStorageModule (MongoDB) for storage

### Dependencies

- **Official**: Requires external player stats service
- **Local**: Requires DataStorageModule (which requires MongoDB)

### Performance

- **Official**: Stats operations go through gRPC service calls
- **Local**: Direct MongoDB operations (faster for local development)

### Data Format

- **Official**: Uses protobuf contracts for data transfer
- **Local**: Uses JSON serialization via DataStorageModule

## API Compatibility

The StatsModule implementation maintains full API compatibility with the official Mineplex Studio Engine StatsModule. All methods, return types, and behavior work identically, allowing code to work seamlessly between local and production environments.

## Requirements

- **DataStorageModule**: StatsModule requires DataStorageModule to be registered
- **MongoDB**: DataStorageModule requires a MongoDB instance (see [DataStoreModule README](../datastore/README.md))

## See Also

- [Mineplex Studio SDK StatsModule Documentation](https://docs.mineplex.com/docs/sdk/features/stats)
- [DataStoreModule README](../datastore/README.md) - Required dependency
- [Main Engine Bridge README](../../../../../../README.md)

