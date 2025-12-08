package net.plexverse.enginebridge.modules.game;

import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.game.GameCycle;
import com.mineplex.studio.sdk.modules.game.MineplexGame;
import com.mineplex.studio.sdk.modules.game.MineplexGameModule;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Local implementation of MineplexGameModule for local development.
 * Based on Studio Engine's MineplexGameModuleImpl but without mechanics.
 * Manages the current game instance and game lifecycle.
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(MineplexGameModule.class)
public class LocalMineplexGameModuleImpl implements MineplexGameModule {
    
    private final JavaPlugin plugin;
    
    @Setter
    private GameCycle gameCycle = null;
    
    private MineplexGame currentGame = null;
    
    @Override
    public void setup() {
        log.info("LocalMineplexGameModule setup");
    }
    
    @Override
    public void teardown() {
        log.info("LocalMineplexGameModule teardown");
        this.stopCurrentGame();
    }
    
    @Override
    @NonNull
    public Optional<GameCycle> getGameCycle() {
        return Optional.ofNullable(this.gameCycle);
    }
    
    @Override
    @NonNull
    public Optional<MineplexGame> getCurrentGame() {
        return Optional.ofNullable(this.currentGame);
    }
    
    @Override
    public void setCurrentGame(@Nullable final MineplexGame mineplexGame) {
        log.info("Setting current game to {}", (mineplexGame == null ? "null" : mineplexGame.getName()));
        
        // Ensure that the game is stopped before setting the new game
        this.stopCurrentGame();
        
        this.currentGame = mineplexGame;
        
        // We delay the activation of the next game to escape the current chain of events,
        // similar to Studio Engine's implementation
        this.getCurrentGame().ifPresent(game -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            // Verify that we still have the same game
            if (!game.equals(this.currentGame)) {
                log.error(
                        "Game changed a tick after being set! {} -> {}.",
                        game.getName(),
                        (this.currentGame == null ? "null" : this.currentGame.getName()));
                return;
            }
            
            try {
                game.setup();
            } catch (Exception e) {
                log.error("Exception while setting up game {}", game.getName(), e);
            }
        }));
    }
    
    @Override
    public void stopCurrentGame() {
        final Optional<MineplexGame> gameOpt = this.getCurrentGame();
        if (gameOpt.isEmpty()) {
            return;
        }
        
        final MineplexGame game = gameOpt.get();
        log.info("Stopping current game: {}", game.getName());
        
        try {
            game.teardown();
        } catch (final Exception exception) {
            log.error("Exception while stopping current game.", exception);
        }
        
        this.currentGame = null;
    }
    
    @Override
    public void startNextGame() {
        log.info("Starting next game...");
        this.stopCurrentGame();
        this.getGameCycle().ifPresent(cycle -> {
            try {
                final MineplexGame nextGame = cycle.createNextGame();
                this.setCurrentGame(nextGame);
            } catch (Exception e) {
                log.error("Exception while creating next game from cycle", e);
            }
        });
        
        if (this.gameCycle == null) {
            log.warn("No GameCycle set, cannot start next game");
        }
    }
}

