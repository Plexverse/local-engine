package net.plexverse.enginebridge.modules.stats;

import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.data.DataStorageModule;
import com.mineplex.studio.sdk.modules.stats.StatsModule;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Local implementation of StatsModule using DataStorageModule.
 * Stores player statistics in MongoDB instead of using external gRPC services.
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(StatsModule.class)
public class StatsModuleImpl implements StatsModule {
    
    private final JavaPlugin plugin;
    
    /**
     * Gets the DataStorageModule from ModuleManager.
     * This is called on-demand to ensure DataStorageModule is available,
     * regardless of module registration order.
     *
     * @return the DataStorageModule instance
     * @throws IllegalStateException if DataStorageModule is not available
     */
    @NonNull
    private DataStorageModule getDataStorageModule() {
        final DataStorageModule dataStorageModule = MineplexModuleManager.getRegisteredModule(DataStorageModule.class);
        
        if (dataStorageModule == null) {
            throw new IllegalStateException("DataStorageModule is required for StatsModule but is not available. " +
                    "Make sure DataStorageModule is registered.");
        }
        
        return dataStorageModule;
    }
    
    @Override
    public void setup() {
        // Verify DataStorageModule is available (but don't store it as a field)
        getDataStorageModule();
        log.info("StatsModule initialized using DataStorageModule");
    }
    
    @Override
    public void teardown() {
        log.info("StatsModule torn down");
    }
    
    @Override
    @NonNull
    public Map<String, Long> getPlayerStats(@NonNull final String playerId) {
        try {
            final PlayerStatsData statsData = getDataStorageModule().loadStructuredData(PlayerStatsData.class, playerId)
                    .orElse(PlayerStatsData.builder()
                            .playerId(playerId)
                            .stats(new HashMap<>())
                            .build());
            
            return new HashMap<>(statsData.getStats());
        } catch (final Exception e) {
            log.error("Failed to get player stats for player: " + playerId, e);
            return new HashMap<>();
        }
    }
    
    @Override
    @NonNull
    public Map<String, Long> getPlayerStats(@NonNull final Player player) {
        return getPlayerStats(player.getUniqueId().toString());
    }
    
    @Override
    @NonNull
    public CompletableFuture<Map<String, Long>> getPlayerStatsAsync(@NonNull final String playerId) {
        return CompletableFuture.supplyAsync(() -> getPlayerStats(playerId));
    }
    
    @Override
    @NonNull
    public CompletableFuture<Map<String, Long>> getPlayerStatsAsync(@NonNull final Player player) {
        return CompletableFuture.supplyAsync(() -> getPlayerStats(player));
    }
    
    @Override
    @NonNull
    public Map<String, Long> awardPlayerStats(
            @NonNull final String playerId, 
            @NonNull final Map<String, Long> statIncrements) {
        try {
            // Get current stats
            final PlayerStatsData statsData = getDataStorageModule().loadStructuredData(PlayerStatsData.class, playerId)
                    .orElse(PlayerStatsData.builder()
                            .playerId(playerId)
                            .stats(new HashMap<>())
                            .build());
            
            // Apply increments
            final Map<String, Long> currentStats = statsData.getStats();
            for (final Map.Entry<String, Long> entry : statIncrements.entrySet()) {
                currentStats.put(
                        entry.getKey(),
                        currentStats.getOrDefault(entry.getKey(), 0L) + entry.getValue()
                );
            }
            
            // Save updated stats
            statsData.setStats(currentStats);
            getDataStorageModule().storeStructuredData(statsData);
            
            return new HashMap<>(currentStats);
        } catch (final Exception e) {
            log.error("Failed to award player stats for player: " + playerId, e);
            return new HashMap<>();
        }
    }
    
    @Override
    @NonNull
    public Map<String, Long> awardPlayerStats(
            @NonNull final Player player, 
            @NonNull final Map<String, Long> statIncrements) {
        return awardPlayerStats(player.getUniqueId().toString(), statIncrements);
    }
    
    @Override
    @NonNull
    public CompletableFuture<Map<String, Long>> awardPlayerStatsAsync(
            @NonNull final String playerId, 
            @NonNull final Map<String, Long> statIncrements) {
        return CompletableFuture.supplyAsync(() -> awardPlayerStats(playerId, statIncrements));
    }
    
    @Override
    @NonNull
    public CompletableFuture<Map<String, Long>> awardPlayerStatsAsync(
            @NonNull final Player player, 
            @NonNull final Map<String, Long> statIncrements) {
        return CompletableFuture.supplyAsync(() -> awardPlayerStats(player, statIncrements));
    }
    
    @Override
    public void setPlayerStats(@NonNull final String playerId, @NonNull final Map<String, Long> stats) {
        try {
            final PlayerStatsData statsData = PlayerStatsData.builder()
                    .playerId(playerId)
                    .stats(new HashMap<>(stats))
                    .build();
            
            getDataStorageModule().storeStructuredData(statsData);
        } catch (final Exception e) {
            log.error("Failed to set player stats for player: " + playerId, e);
        }
    }
    
    @Override
    public void setPlayerStats(@NonNull final Player player, @NonNull final Map<String, Long> stats) {
        setPlayerStats(player.getUniqueId().toString(), stats);
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> setPlayerStatsAsync(
            @NonNull final String playerId, 
            @NonNull final Map<String, Long> stats) {
        return CompletableFuture.runAsync(() -> setPlayerStats(playerId, stats));
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> setPlayerStatsAsync(
            @NonNull final Player player, 
            @NonNull final Map<String, Long> stats) {
        return CompletableFuture.runAsync(() -> setPlayerStats(player, stats));
    }
    
    @Override
    public void deletePlayerStats(@NonNull final String playerId, @NonNull final List<String> stats) {
        try {
            final PlayerStatsData statsData = getDataStorageModule().loadStructuredData(PlayerStatsData.class, playerId)
                    .orElse(null);
            
            if (statsData == null) {
                return; // Nothing to delete
            }
            
            // Remove specified stats
            final Map<String, Long> currentStats = statsData.getStats();
            for (final String statKey : stats) {
                currentStats.remove(statKey);
            }
            
            // Save updated stats
            statsData.setStats(currentStats);
            getDataStorageModule().storeStructuredData(statsData);
        } catch (final Exception e) {
            log.error("Failed to delete player stats for player: " + playerId, e);
        }
    }
    
    @Override
    public void deletePlayerStats(@NonNull final Player player, @NonNull final List<String> stats) {
        deletePlayerStats(player.getUniqueId().toString(), stats);
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> deletePlayerStatsAsync(
            @NonNull final String playerId, 
            @NonNull final List<String> stats) {
        return CompletableFuture.runAsync(() -> deletePlayerStats(playerId, stats));
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> deletePlayerStatsAsync(
            @NonNull final Player player, 
            @NonNull final List<String> stats) {
        return CompletableFuture.runAsync(() -> deletePlayerStats(player, stats));
    }
}

