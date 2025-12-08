package net.plexverse.enginebridge.modules.matchmaker;

import com.mineplex.studio.sdk.modules.queuing.QueuingModule;
import com.mineplex.studio.sdk.modules.queuing.GetQueueStatusResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local implementation of MatchmakerModule.
 * Provides a basic in-memory queue system for local development.
 * 
 * Note: This is a simplified implementation that does not support actual matchmaking
 * or proxy connections. For production use, use the official Studio Engine.
 */
@Slf4j
@RequiredArgsConstructor
public class MatchmakerModuleImpl implements QueuingModule {
    
    private final JavaPlugin plugin;
    
    // In-memory queue storage: playerId -> gameId
    private final Map<UUID, String> playerQueues = new ConcurrentHashMap<>();
    // Track when players joined the queue
    private final Map<UUID, Instant> queueJoinTimes = new ConcurrentHashMap<>();
    
    @Override
    public void setup() {
        log.info("MatchmakerModule initialized with local in-memory queue (no proxy support)");
        log.warn("MatchmakerModule is using a simplified local implementation. " +
                "Actual matchmaking and proxy connections are not supported.");
    }
    
    @Override
    public void teardown() {
        playerQueues.clear();
        queueJoinTimes.clear();
        log.info("MatchmakerModule torn down");
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> enqueuePlayer(@NonNull final Player player, @NonNull final String gameId) {
        return CompletableFuture.runAsync(() -> {
            final UUID playerId = player.getUniqueId();
            
            // Remove from old queue if exists
            if (playerQueues.containsKey(playerId)) {
                playerQueues.remove(playerId);
                queueJoinTimes.remove(playerId);
            }
            
            // Add to queue
            playerQueues.put(playerId, gameId);
            queueJoinTimes.put(playerId, Instant.now());
            log.info("Player {} queued for game {} (local queue)", player.getName(), gameId);
        });
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> enqueuePlayer(@NonNull final Player player, @NonNull final String gameId, @NonNull final String commonName) {
        // For local implementation, commonName is ignored
        return enqueuePlayer(player, gameId);
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> enqueuePlayerForTag(@NonNull final Player player, @NonNull final String tag) {
        // Local implementation: treat tag as gameId
        return CompletableFuture.runAsync(() -> {
            final UUID playerId = player.getUniqueId();
            final String gameId = "tag:" + tag;
            
            if (playerQueues.containsKey(playerId)) {
                playerQueues.remove(playerId);
                queueJoinTimes.remove(playerId);
            }
            
            playerQueues.put(playerId, gameId);
            queueJoinTimes.put(playerId, Instant.now());
            log.info("Player {} queued for tag {} (local queue)", player.getName(), tag);
        });
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> enqueuePlayerSpectate(@NonNull final Player player, @NonNull final String gameId, @NonNull final String commonName) {
        // Local implementation: treat spectate as regular queue
        return enqueuePlayer(player, gameId, commonName);
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> dequeuePlayer(@NonNull final Player player) {
        return CompletableFuture.runAsync(() -> {
            final UUID playerId = player.getUniqueId();
            final String removedGameId = playerQueues.remove(playerId);
            queueJoinTimes.remove(playerId);
            
            if (removedGameId != null) {
                log.info("Player {} removed from queue for game {} (local queue)", player.getName(), removedGameId);
            } else {
                log.debug("Player {} was not in any queue", player.getName());
            }
        });
    }
    
    @Override
    public boolean joinedThroughTag(@NonNull final Player player) {
        // Local implementation: check if player is queued for a tag
        final UUID playerId = player.getUniqueId();
        final String gameId = playerQueues.get(playerId);
        return gameId != null && gameId.startsWith("tag:");
    }
    
    @Override
    public boolean joinedThroughTag(@NonNull final Player player, @NonNull final String tag) {
        // Local implementation: check if player is queued for the specific tag
        final UUID playerId = player.getUniqueId();
        final String gameId = playerQueues.get(playerId);
        return gameId != null && gameId.equals("tag:" + tag);
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> requeuePlayer(@NonNull final Player player) {
        // Local implementation: requeue by getting current queue and re-queuing
        return CompletableFuture.runAsync(() -> {
            final UUID playerId = player.getUniqueId();
            final String gameId = playerQueues.get(playerId);
            
            if (gameId != null) {
                // Remove and re-add to queue (reset join time)
                playerQueues.remove(playerId);
                queueJoinTimes.remove(playerId);
                playerQueues.put(playerId, gameId);
                queueJoinTimes.put(playerId, Instant.now());
                log.info("Player {} requeued for game {} (local queue)", player.getName(), gameId);
            } else {
                log.debug("Player {} was not in any queue to requeue", player.getName());
            }
        });
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> retainPlayer(@NonNull final Player player) {
        // Local implementation: retain player in queue (no-op for local implementation)
        return CompletableFuture.runAsync(() -> {
            final UUID playerId = player.getUniqueId();
            if (playerQueues.containsKey(playerId)) {
                log.debug("Player {} retained in queue (local implementation)", player.getName());
            } else {
                log.debug("Player {} was not in any queue to retain", player.getName());
            }
        });
    }
    
    @Override
    public void returnLobby(@NonNull final Player player) {
        // Local implementation: return player to lobby (just dequeue them)
        final UUID playerId = player.getUniqueId();
        final String removedGameId = playerQueues.remove(playerId);
        queueJoinTimes.remove(playerId);
        
        if (removedGameId != null) {
            log.info("Player {} returned to lobby (removed from queue for game {})", player.getName(), removedGameId);
        } else {
            log.debug("Player {} returned to lobby (was not in any queue)", player.getName());
        }
    }
    
    @Override
    @NonNull
    public CompletableFuture<@NonNull GetQueueStatusResponse> getPlayerQueueStatus(@NonNull final Player player) {
        return CompletableFuture.supplyAsync(() -> {
            final UUID playerId = player.getUniqueId();
            final String gameId = playerQueues.get(playerId);
            final Instant joinTime = queueJoinTimes.get(playerId);
            final boolean isQueued = gameId != null;
            
            // Calculate time in queue
            Duration timeInQueue = null;
            if (isQueued && joinTime != null) {
                timeInQueue = Duration.between(joinTime, Instant.now());
            }
            
            // Build response using builder
            return GetQueueStatusResponse.builder()
                    .isQueued(isQueued)
                    .queuedGameId(gameId)
                    .timeInQueue(timeInQueue)
                    .queueState(null) // Local implementation doesn't track queue state
                    .lastStateChangeTime(joinTime)
                    .build();
        });
    }
}

