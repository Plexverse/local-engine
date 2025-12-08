package net.plexverse.enginebridge.modules.lobby;

import com.mineplex.studio.sdk.modules.lobby.LobbyModule;
import com.mineplex.studio.sdk.modules.lobby.StudioLobby;
import com.mineplex.studio.sdk.modules.world.MineplexWorld;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Local implementation of LobbyModule.
 * Manages the active lobby and provides basic lobby creation functionality.
 */
@Slf4j
@RequiredArgsConstructor
public class LobbyModuleImpl implements LobbyModule {
    
    private final JavaPlugin plugin;
    private StudioLobby activeLobby;
    
    @Override
    public void setup() {
        log.info("LobbyModule initialized");
    }
    
    @Override
    public void teardown() {
        if (activeLobby != null) {
            activeLobby.teardown();
            HandlerList.unregisterAll(activeLobby);
            activeLobby = null;
            log.info("LobbyModule torn down");
        }
    }
    
    @Override
    @NonNull
    public Optional<StudioLobby> getActiveLobby() {
        return Optional.ofNullable(activeLobby);
    }
    
    @Override
    public void setActiveLobby(@Nullable final StudioLobby studioLobby) {
        if (activeLobby != null) {
            activeLobby.teardown();
            HandlerList.unregisterAll(activeLobby);
        }
        
        activeLobby = studioLobby;
        
        if (activeLobby != null) {
            activeLobby.setup();
            Bukkit.getPluginManager().registerEvents(activeLobby, plugin);
            log.info("Set active lobby: {}", studioLobby.getClass().getSimpleName());
        } else {
            log.info("Cleared active lobby");
        }
    }
    
    @Override
    @NonNull
    public StudioLobby createBasicLobby(@NonNull final MineplexWorld mineplexWorld) {
        return new BasicStudioLobby(mineplexWorld);
    }
}

