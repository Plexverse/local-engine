package net.plexverse.enginebridge.modules.readystate;

import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import com.mineplex.studio.sdk.modules.game.GameState;
import com.mineplex.studio.sdk.modules.game.event.PostMineplexGameStateChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.plexverse.enginebridge.PlexverseEngineBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Module that blocks player login until the game state becomes ready.
 * 
 * <p>This module ensures that players cannot join the server until a game state transition
 * occurs where the new state's {@link GameState#isReady()} method returns {@code true}.
 * This is typically used in Agones/Kubernetes environments where the game server must
 * signal readiness before accepting player connections.</p>
 * 
 * <p><strong>Behavior:</strong></p>
 * <ul>
 *   <li>On initialization, the module blocks all player login attempts</li>
 *   <li>Listens for {@link PostMineplexGameStateChangeEvent} events</li>
 *   <li>When a state with {@code isReady() == true} is reached, the module:
 *     <ul>
 *       <li>Allows future player logins</li>
 *       <li>Automatically unregisters itself via {@link MineplexModuleManager#destroyModule(Class)}</li>
 *     </ul>
 *   </li>
 *   <li>Once unregistered, the module will not block logins again (one-time check)</li>
 * </ul>
 * 
 * 
 * <p><strong>Auto-Discovery:</strong></p>
 * <p>This module is automatically discovered and registered by the {@link net.plexverse.enginebridge.util.ModuleScanner}
 * during plugin initialization. No manual registration is required.</p>
 * 
 * @author Plexverse Engine Bridge
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(ReadyStateModuleInterface.class)
public class ReadyStateModule implements ReadyStateModuleInterface, Listener {
    
    /**
     * The plugin instance that owns this module.
     */
    private final JavaPlugin plugin;
    
    /**
     * Called when the module is set up.
     * Logs that the module is initialized and blocking player logins.
     */
    @Override
    public void setup() {
        log.info("ReadyStateModule initialized - blocking player logins until game state is ready");
    }
    
    /**
     * Called when the module is torn down.
     * Note: Event listeners are automatically unregistered by {@link MineplexModuleManager#destroyModule(Class)}.
     */
    @Override
    public void teardown() {
        log.info("ReadyStateModule torn down");
    }
    
    /**
     * Listens for game state change events and checks if the new state indicates readiness.
     * 
     * <p>When a state with {@link GameState#isReady()} returning {@code true} is detected,
     * this method unregisters this module via the module manager. The module manager's
     * {@code destroyModule()} method will automatically unregister all event listeners,
     * preventing this handler from being called again.</p>
     * 
     * @param event the game state change event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameStateChange(final PostMineplexGameStateChangeEvent event) {
        final GameState toState = event.getToState();
        if (toState != null && toState.isReady()) {
            log.info("Game state is now ready (isReady() = true). Allowing player logins and unregistering ReadyStateModule.");
            
            // Unregister this module - the module manager will automatically unregister event listeners
            final MineplexModuleManager moduleManager = PlexverseEngineBridge.getModuleManager();
            if (moduleManager != null) {
                moduleManager.destroyModule(ReadyStateModuleInterface.class);
            }
        }
    }
    
    /**
     * Blocks player login attempts until the game state is ready.
     * 
     * <p>This handler runs at {@link EventPriority#HIGHEST} priority to ensure it executes
     * before other login handlers. Players attempting to join before the server is ready
     * will be kicked with a message indicating the server is not ready yet.</p>
     * 
     * <p>Once the module is unregistered (after ready state is detected), this handler
     * will be automatically removed by the module manager and will no longer block logins.</p>
     * 
     * @param event the player pre-login event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(final PlayerPreLoginEvent event) {
        event.disallow(
            PlayerPreLoginEvent.Result.KICK_OTHER,
            Component.text("§cServer is not ready. Change game state to one where GameState#isReady() is true to allow logins.")
        );
        log.debug("Blocked player {} from joining - server not ready yet", event.getName());
    }
}

