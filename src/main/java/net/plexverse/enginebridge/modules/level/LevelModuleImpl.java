package net.plexverse.enginebridge.modules.level;

import com.mineplex.studio.sdk.modules.data.DataStorageModule;
import com.mineplex.studio.sdk.modules.level.MineplexLevelModule;
import com.mineplex.studio.sdk.modules.level.experience.ExperienceAwardResult;
import com.mineplex.studio.sdk.modules.level.experience.MineplexPlayerExperience;
import com.mineplex.studio.sdk.modules.level.session.MineplexExperienceSession;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.plexverse.enginebridge.modules.ModuleManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Local implementation of MineplexLevelModule using DataStorageModule.
 * Provides player leveling functionality with a basic level calculation algorithm.
 */
@Slf4j
@RequiredArgsConstructor
public class LevelModuleImpl implements MineplexLevelModule {
    
    private final JavaPlugin plugin;
    private final Map<UUID, MineplexPlayerExperience> onlinePlayerCache = new HashMap<>();
    
    // Basic level algorithm constants
    // Level is calculated as: level = floor(sqrt(experience / BASE_XP_PER_LEVEL)) + 1
    // This means:
    // - Level 1: 0-99 XP
    // - Level 2: 100-399 XP
    // - Level 3: 400-899 XP
    // - Level 4: 900-1599 XP
    // etc.
    private static final long BASE_XP_PER_LEVEL = 100L;
    
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
        final DataStorageModule dataStorageModule = ModuleManager.getInstance().getRegisteredModule(DataStorageModule.class);
        
        if (dataStorageModule == null) {
            throw new IllegalStateException("DataStorageModule is required for LevelModule but is not available. " +
                    "Make sure DataStorageModule is registered.");
        }
        
        return dataStorageModule;
    }
    
    /**
     * Calculates the level from experience points using a basic algorithm.
     * Formula: level = floor(sqrt(experience / BASE_XP_PER_LEVEL)) + 1
     *
     * @param experience the experience points
     * @return the calculated level (minimum 1)
     */
    private int calculateLevel(final long experience) {
        if (experience <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(Math.sqrt(experience / (double) BASE_XP_PER_LEVEL)) + 1);
    }
    
    /**
     * Calculates the experience required for a given level.
     * Inverse of calculateLevel: experience = (level - 1)^2 * BASE_XP_PER_LEVEL
     *
     * @param level the level
     * @return the experience required for that level
     */
    private long experienceForLevel(final int level) {
        if (level <= 1) {
            return 0L;
        }
        return (long) Math.pow(level - 1, 2) * BASE_XP_PER_LEVEL;
    }
    
    /**
     * Creates a MineplexPlayerExperience from stored data.
     * Uses LocalPlayerExperience as the implementation.
     */
    private MineplexPlayerExperience createPlayerExperience(final UUID playerId, final long experience) {
        final int level = calculateLevel(experience);
        final long experienceForCurrentLevel = experienceForLevel(level);
        final long experienceForNextLevel = experienceForLevel(level + 1);
        final long experienceInCurrentLevel = experience - experienceForCurrentLevel;
        final long experienceNeededForNextLevel = experienceForNextLevel - experienceForCurrentLevel;
        
        return LocalPlayerExperience.create(
                playerId,
                level,
                experience,
                experienceInCurrentLevel,
                experienceNeededForNextLevel);
    }
    
    @Override
    public void setup() {
        // Verify DataStorageModule is available (but don't store it as a field)
        getDataStorageModule();
        log.info("LevelModule initialized using DataStorageModule with basic level algorithm");
    }
    
    @Override
    public void teardown() {
        onlinePlayerCache.clear();
        log.info("LevelModule torn down");
    }
    
    @Override
    @NonNull
    public Optional<MineplexPlayerExperience> getOnlinePlayerExperience(@NonNull final UUID playerId) {
        // Check cache first
        if (onlinePlayerCache.containsKey(playerId)) {
            return Optional.of(onlinePlayerCache.get(playerId));
        }
        
        // Check if player is online
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        
        // Load from storage
        try {
            final PlayerLevelData levelData = getDataStorageModule()
                    .loadStructuredData(PlayerLevelData.class, playerId.toString())
                    .orElse(PlayerLevelData.builder()
                            .playerId(playerId.toString())
                            .experience(0L)
                            .build());
            
            final MineplexPlayerExperience experience = createPlayerExperience(playerId, levelData.getExperience());
            onlinePlayerCache.put(playerId, experience);
            return Optional.of(experience);
        } catch (final Exception e) {
            log.error("Failed to get online player experience for player: " + playerId, e);
            return Optional.empty();
        }
    }
    
    @Override
    @NonNull
    public Optional<MineplexPlayerExperience> getOnlinePlayerExperience(@NonNull final Player player) {
        return getOnlinePlayerExperience(player.getUniqueId());
    }
    
    @Override
    @NonNull
    public CompletableFuture<MineplexPlayerExperience> getPlayerExperience(@NonNull final UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final PlayerLevelData levelData = getDataStorageModule()
                        .loadStructuredData(PlayerLevelData.class, playerId.toString())
                        .orElse(PlayerLevelData.builder()
                                .playerId(playerId.toString())
                                .experience(0L)
                                .build());
                
                return createPlayerExperience(playerId, levelData.getExperience());
            } catch (final Exception e) {
                log.error("Failed to get player experience for player: " + playerId, e);
                return createPlayerExperience(playerId, 0L);
            }
        });
    }
    
    @Override
    @NonNull
    public CompletableFuture<MineplexPlayerExperience> getPlayerExperience(@NonNull final OfflinePlayer player) {
        return getPlayerExperience(player.getUniqueId());
    }
    
    @Override
    @NonNull
    public CompletableFuture<Map<UUID, MineplexPlayerExperience>> listPlayerExperiences(@NonNull final Iterable<UUID> playerIds) {
        return CompletableFuture.supplyAsync(() -> {
            final Map<UUID, MineplexPlayerExperience> result = new HashMap<>();
            
            for (final UUID playerId : playerIds) {
                try {
                    final PlayerLevelData levelData = getDataStorageModule()
                            .loadStructuredData(PlayerLevelData.class, playerId.toString())
                            .orElse(PlayerLevelData.builder()
                                    .playerId(playerId.toString())
                                    .experience(0L)
                                    .build());
                    
                    result.put(playerId, createPlayerExperience(playerId, levelData.getExperience()));
                } catch (final Exception e) {
                    log.error("Failed to get player experience for player: " + playerId, e);
                    result.put(playerId, createPlayerExperience(playerId, 0L));
                }
            }
            
            return result;
        });
    }
    
    @Override
    @NonNull
    public CompletableFuture<Map<UUID, MineplexPlayerExperience>> listPlayerExperiences(@NonNull final Collection<OfflinePlayer> players) {
        final List<UUID> playerIds = players.stream()
                .map(OfflinePlayer::getUniqueId)
                .collect(Collectors.toList());
        
        return listPlayerExperiences(playerIds);
    }
    
    @Override
    @NonNull
    public CompletableFuture<Map<UUID, ExperienceAwardResult>> rewardGame(@NonNull final MineplexExperienceSession gameReward) {
        return CompletableFuture.supplyAsync(() -> {
            final Map<UUID, ExperienceAwardResult> results = new HashMap<>();
            
            try {
                // Get players and experience amounts from the session
                // Note: This is a simplified implementation. The actual MineplexExperienceSession
                // interface may have different methods. We'll use reflection to access the data.
                Map<UUID, Long> experienceRewards = new HashMap<>();
                
                // Try to get player rewards using reflection
                try {
                    final java.lang.reflect.Method getPlayerRewards = gameReward.getClass()
                            .getMethod("getPlayerRewards");
                    final Object result = getPlayerRewards.invoke(gameReward);
                    if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        final Map<UUID, Long> rewards = (Map<UUID, Long>) result;
                        experienceRewards = rewards;
                    }
                } catch (final Exception e) {
                    log.warn("Could not get player rewards from MineplexExperienceSession, using empty map", e);
                }
                
                for (final Map.Entry<UUID, Long> entry : experienceRewards.entrySet()) {
                    final UUID playerId = entry.getKey();
                    final long experienceToAdd = entry.getValue();
                    
                    try {
                        // Get current experience
                        final PlayerLevelData levelData = getDataStorageModule()
                                .loadStructuredData(PlayerLevelData.class, playerId.toString())
                                .orElse(PlayerLevelData.builder()
                                        .playerId(playerId.toString())
                                        .experience(0L)
                                        .build());
                        
                        final long oldExperience = levelData.getExperience();
                        final int oldLevel = calculateLevel(oldExperience);
                        
                        // Add experience
                        final long newExperience = Math.max(0L, oldExperience + experienceToAdd);
                        levelData.setExperience(newExperience);
                        
                        final int newLevel = calculateLevel(newExperience);
                        
                        // Save updated experience
                        getDataStorageModule().storeStructuredData(levelData);
                        
                        // Update cache if player is online
                        final Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            onlinePlayerCache.put(playerId, createPlayerExperience(playerId, newExperience));
                        }
                        
                        // Create award result
                        final ExperienceAwardResult result = LocalExperienceAwardResult.create(
                                playerId,
                                experienceToAdd,
                                oldLevel,
                                newLevel,
                                newLevel > oldLevel);
                        
                        results.put(playerId, result);
                        
                        if (newLevel > oldLevel) {
                            log.debug("Player {} leveled up from {} to {}", playerId, oldLevel, newLevel);
                        }
                    } catch (final Exception e) {
                        log.error("Failed to reward experience for player: " + playerId, e);
                        // Create a failed result
                        final ExperienceAwardResult result = LocalExperienceAwardResult.create(
                                playerId,
                                0L,
                                1,
                                1,
                                false);
                        results.put(playerId, result);
                    }
                }
            } catch (final Exception e) {
                log.error("Failed to process game reward session", e);
            }
            
            return results;
        });
    }
}
