package net.plexverse.enginebridge.modules.game.mechanics.afk;

import com.mineplex.studio.sdk.modules.game.GameState;
import com.mineplex.studio.sdk.modules.game.MineplexGame;
import com.mineplex.studio.sdk.modules.game.PlayerState;
import com.mineplex.studio.sdk.modules.game.mechanics.GameMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.afk.PlayerAFKMechanic;
import com.mineplex.studio.sdk.util.selector.PlayerTargetSelector;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Local implementation of PlayerAFKMechanic for 1.21.8 compatibility.
 * This is a simplified version that doesn't use Entity.isGhost() which doesn't exist in 1.21.8.
 */
@Slf4j
public class LocalPlayerAFKMechanic implements GameMechanic<MineplexGame>, PlayerAFKMechanic {
    
    @Getter
    @Setter
    private MineplexGame game;
    
    @NonNull
    private PlayerTargetSelector playerTargetSelector;
    
    @Getter
    @Setter
    private Duration maximumTime = Duration.ofMinutes(2);
    
    @Getter
    @Setter
    private boolean automatedHandling = true;
    
    private final ConcurrentMap<Player, Long> lastActionTime = new ConcurrentHashMap<>();
    private Predicate<Player> targetSelectorPredicate = player -> true;
    private Predicate<GameState> gameStatePredicate = gameState -> true;
    
    @Override
    public void setup(@NonNull final MineplexGame mineplexGame) {
        this.game = mineplexGame;
        // Create a simple PlayerTargetSelector that doesn't use Entity.isGhost()
        this.playerTargetSelector = new PlayerTargetSelector() {
            @Override
            public boolean canTarget(@NonNull final Player player) {
                if (game == null || !player.isOnline()) {
                    return false;
                }
                // Simple check: player is alive if they have health > 0
                return player.getHealth() > 0 && !player.isDead();
            }
            
            @Override
            @NonNull
            public Predicate<Player> getTargetSelector() {
                return player -> canTarget(player);
            }
            
            @Override
            public void setTargetSelector(@NonNull final Predicate<Player> targetSelector) {
                // Not used in this simple implementation
            }
        };
        this.targetSelectorPredicate = player -> playerTargetSelector.canTarget(player);
        log.debug("LocalPlayerAFKMechanic setup for game: {}", mineplexGame.getName());
    }
    
    @Override
    @NonNull
    public Predicate<Player> getTargetSelector() {
        return targetSelectorPredicate;
    }
    
    @Override
    public void setTargetSelector(@NonNull final Predicate<Player> targetSelector) {
        this.targetSelectorPredicate = targetSelector;
        // Update the PlayerTargetSelector to match
        this.playerTargetSelector = new PlayerTargetSelector() {
            @Override
            public boolean canTarget(@NonNull final Player player) {
                return targetSelector.test(player);
            }
            
            @Override
            @NonNull
            public Predicate<Player> getTargetSelector() {
                return targetSelector;
            }
            
            @Override
            public void setTargetSelector(@NonNull final Predicate<Player> targetSelector) {
                // Not used in this simple implementation
            }
        };
    }
    
    @Override
    @NonNull
    public Predicate<GameState> getGameStatePredicate() {
        return gameStatePredicate;
    }
    
    @Override
    public void setGameStatePredicate(@NonNull final Predicate<GameState> gameStatePredicate) {
        this.gameStatePredicate = gameStatePredicate;
    }
    
    @Override
    public void teardown() {
        lastActionTime.clear();
        log.debug("LocalPlayerAFKMechanic teardown");
    }
    
    @Override
    public boolean isPlayerAfk(@NonNull final Player player, @NonNull final Duration duration) {
        final Long lastAction = lastActionTime.get(player);
        if (lastAction == null) {
            return false;
        }
        final long timeSinceLastAction = System.currentTimeMillis() - lastAction;
        return timeSinceLastAction >= duration.toMillis();
    }
    
    @Override
    public void setMaximumTime(@NonNull final Duration maximumTime) {
        this.maximumTime = maximumTime;
    }
    
    /**
     * Updates the last action time for a player.
     * This should be called when the player performs an action.
     */
    public void updateLastAction(@NonNull final Player player) {
        lastActionTime.put(player, System.currentTimeMillis());
    }
}

