package net.plexverse.enginebridge.modules.game.mechanics.legacy;

import com.mineplex.studio.sdk.modules.game.GameState;
import com.mineplex.studio.sdk.modules.game.MineplexGame;
import com.mineplex.studio.sdk.modules.game.mechanics.GameMechanic;
import com.mineplex.studio.sdk.modules.game.mechanics.legacy.LegacyArmorMechanic;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/**
 * Local implementation of LegacyArmorMechanic for 1.21.8 compatibility.
 * This is a simplified version that doesn't use Entity.isGhost() which doesn't exist in 1.21.8.
 */
@Slf4j
public class LocalLegacyArmorMechanic implements GameMechanic<MineplexGame>, LegacyArmorMechanic {
    
    @Getter
    @Setter
    private MineplexGame game;
    
    private Predicate<LivingEntity> targetSelectorPredicate = entity -> true;
    private Predicate<GameState> gameStatePredicate = gameState -> true;
    
    @Override
    public void setup(@NonNull final MineplexGame mineplexGame) {
        this.game = mineplexGame;
        // Initialize target selector to target all living entities (avoiding Entity.isGhost())
        // Use a robust check that doesn't rely on SDK methods that use isGhost()
        this.targetSelectorPredicate = entity -> {
            if (entity == null) {
                return false;
            }
            // For players, also check if they're online
            if (entity instanceof Player) {
                final Player player = (Player) entity;
                if (!player.isOnline()) {
                    return false;
                }
            }
            // Simple check: entity is alive if they have health > 0
            return entity.getHealth() > 0 && !entity.isDead();
        };
        log.debug("LocalLegacyArmorMechanic setup for game: {}", mineplexGame.getName());
    }
    
    @Override
    public void teardown() {
        log.debug("LocalLegacyArmorMechanic teardown");
    }
    
    private boolean legacyBlocking = false;
    private boolean legacyArmor = false;
    
    @Override
    public void setLegacyBlocking(final boolean legacyBlocking) {
        this.legacyBlocking = legacyBlocking;
        log.debug("setLegacyBlocking called with: {}", legacyBlocking);
    }
    
    @Override
    public boolean isLegacyBlocking() {
        return legacyBlocking;
    }
    
    @Override
    public void setLegacyArmor(final boolean legacyArmor) {
        this.legacyArmor = legacyArmor;
        log.debug("setLegacyArmor called with: {}", legacyArmor);
    }
    
    @Override
    public boolean isLegacyArmor() {
        return legacyArmor;
    }
    
    @Override
    @NonNull
    public Predicate<LivingEntity> getTargetSelector() {
        return targetSelectorPredicate;
    }
    
    @Override
    public void setTargetSelector(@NonNull final Predicate<LivingEntity> targetSelector) {
        this.targetSelectorPredicate = entity -> {
            if (entity == null) {
                return false;
            }
            // For players, also check if they're online
            if (entity instanceof Player) {
                final Player player = (Player) entity;
                if (!player.isOnline()) {
                    return false;
                }
            }
            // Apply the user's target selector
            return targetSelector.test(entity);
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

