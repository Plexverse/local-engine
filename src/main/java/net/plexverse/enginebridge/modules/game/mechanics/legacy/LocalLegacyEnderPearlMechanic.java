package net.plexverse.enginebridge.modules.game.mechanics.legacy;

import com.mineplex.studio.sdk.modules.game.GameState;
import com.mineplex.studio.sdk.modules.game.MineplexGame;
import com.mineplex.studio.sdk.modules.game.mechanics.GameMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.legacy.LegacyEnderPearlMechanic;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/**
 * Local implementation of LegacyEnderPearlMechanic for 1.21.8 compatibility.
 * This is a simplified version that doesn't use Entity.isGhost() which doesn't exist in 1.21.8.
 */
@Slf4j
public class LocalLegacyEnderPearlMechanic implements GameMechanic<MineplexGame>, LegacyEnderPearlMechanic {
    
    @Getter
    @Setter
    private MineplexGame game;
    
    private Predicate<Player> targetSelectorPredicate = player -> true;
    private Predicate<GameState> gameStatePredicate = gameState -> true;
    
    @Override
    public void setup(@NonNull final MineplexGame mineplexGame) {
        this.game = mineplexGame;
        // Initialize target selector to target all players (avoiding Entity.isGhost())
        // Use a robust check that doesn't rely on SDK methods that use isGhost()
        this.targetSelectorPredicate = player -> {
            if (player == null || !player.isOnline()) {
                return false;
            }
            // Simple check: player is alive if they have health > 0
            return player.getHealth() > 0 && !player.isDead();
        };
        log.debug("LocalLegacyEnderPearlMechanic setup for game: {}", mineplexGame.getName());
    }
    
    @Override
    public void teardown() {
        log.debug("LocalLegacyEnderPearlMechanic teardown");
    }
    
    @Override
    @NonNull
    public Predicate<Player> getTargetSelector() {
        return targetSelectorPredicate;
    }
    
    @Override
    public void setTargetSelector(@NonNull final Predicate<Player> targetSelector) {
        this.targetSelectorPredicate = player -> {
            if (player == null || !player.isOnline()) {
                return false;
            }
            // Apply the user's target selector
            return targetSelector.test(player);
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
}

