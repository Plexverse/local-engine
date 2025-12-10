package net.plexverse.enginebridge.modules.readystate;

import com.mineplex.studio.sdk.modules.MineplexModule;

/**
 * Interface for the ReadyStateModule.
 * 
 * <p>This module blocks player login until the game state becomes ready (when a game state
 * with {@link com.mineplex.studio.sdk.modules.game.GameState#isReady()} returns true).
 * Once a ready state is reached, the module automatically unregisters itself and allows
 * players to join normally.</p>
 * 
 * <p>The module listens for {@link com.mineplex.studio.sdk.modules.game.event.PostMineplexGameStateChangeEvent}
 * events and checks if the new state indicates the server is ready. This is typically used
 * in Agones/Kubernetes environments where the game server needs to signal readiness before
 * accepting player connections.</p>
 */
public interface ReadyStateModuleInterface extends MineplexModule {
}

