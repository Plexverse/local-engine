package net.plexverse.enginebridge.modules.lobby;

import com.mineplex.studio.sdk.modules.game.MineplexGameModule;
import com.mineplex.studio.sdk.modules.game.event.PostMineplexGameStateChangeEvent;
import com.mineplex.studio.sdk.modules.lobby.StudioLobby;
import com.mineplex.studio.sdk.modules.world.MineplexWorld;
import com.mineplex.studio.sdk.modules.world.MineplexWorldModule;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mineplex.studio.sdk.modules.MineplexModuleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Basic implementation of StudioLobby.
 * Provides lobby functionality including spawn management, damage prevention, and game state handling.
 */
@Slf4j
@RequiredArgsConstructor
public class BasicStudioLobby implements StudioLobby {
    
    @Getter
    private final MineplexWorld lobbyWorld;
    
    private MineplexGameModule gameModule;
    
    @Override
    public void setup() {
        gameModule = MineplexModuleManager.getRegisteredModule(MineplexGameModule.class);
        log.info("BasicStudioLobby setup for world: {}", lobbyWorld.getMinecraftWorld().getName());
    }
    
    @Override
    public void teardown() {
        final MineplexWorldModule worldModule = MineplexModuleManager.getRegisteredModule(MineplexWorldModule.class);
        if (worldModule != null) {
            worldModule.releaseWorld(lobbyWorld);
        }
        log.info("BasicStudioLobby torn down");
    }
    
    @Override
    public boolean isInLobby(@NonNull final LivingEntity livingEntity) {
        return lobbyWorld.getMinecraftWorld().equals(livingEntity.getWorld());
    }
    
    private Location getSpawnLocation() {
        final var spawns = lobbyWorld.getDataPoints("SPAWN");
        
        if (spawns.isEmpty()) {
            return lobbyWorld.getMinecraftWorld().getSpawnLocation();
        } else {
            return spawns.get(0);
        }
    }
    
    private void addToLobby(final Player player) {
        player.teleport(getSpawnLocation());
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.getInventory().clear();
        player.clearActivePotionEffects();
    }
    
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (gameModule == null) {
            addToLobby(event.getPlayer());
            return;
        }
        
        final var currentGame = gameModule.getCurrentGame();
        if (currentGame.isEmpty() || !currentGame.get().getGameState().isInProgress()) {
            addToLobby(event.getPlayer());
        }
    }
    
    @EventHandler
    public void onMove(final PlayerMoveEvent event) {
        if (!isInLobby(event.getPlayer())) {
            return;
        }
        
        if (getSpawnLocation().distanceSquared(event.getTo()) <= 10_000) {
            return;
        }
        
        event.getPlayer().teleport(getSpawnLocation());
    }
    
    @EventHandler
    public void onHurt(final EntityDamageEvent event) {
        if (event.getEntity() instanceof final LivingEntity livingEntity) {
            if (isInLobby(livingEntity)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onHunger(final FoodLevelChangeEvent event) {
        if (isInLobby(event.getEntity())
                && event.getFoodLevel() < event.getEntity().getFoodLevel()) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPickup(final PlayerAttemptPickupItemEvent event) {
        if (isInLobby(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStateChange(final PostMineplexGameStateChangeEvent event) {
        if (event.getToState().isEnded()) {
            Bukkit.getOnlinePlayers().forEach(this::addToLobby);
        }
    }
}

