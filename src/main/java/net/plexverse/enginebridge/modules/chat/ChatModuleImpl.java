package net.plexverse.enginebridge.modules.chat;

import io.papermc.paper.chat.ChatRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.plexverse.enginebridge.PlexverseEngineBridge;
import net.plexverse.enginebridge.modules.chat.filter.ChatFilter;
import net.plexverse.enginebridge.modules.chat.filter.LocalChatFilter;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.mineplex.studio.sdk.modules.chat.BuiltInChatChannel;
import com.mineplex.studio.sdk.modules.chat.ChatChannel;
import com.mineplex.studio.sdk.modules.chat.ChatModule;
import com.mineplex.studio.sdk.modules.chat.event.StudioChatEvent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Local implementation of ChatModule for Engine Bridge.
 * Provides chat channel management, filtering, and rendering without external dependencies.
 */
@Slf4j
@RequiredArgsConstructor
public class ChatModuleImpl implements ChatModule, Listener {
    
    private final Set<ChatChannel> silencedChannels = Collections.synchronizedSet(new HashSet<>());
    private final Map<ChatChannel, Supplier<ChatRenderer>> renderers = Collections.synchronizedMap(new HashMap<>());
    private final Map<ChatChannel, Function<Player, Set<Audience>>> audienceFunctions = Collections.synchronizedMap(new HashMap<>());
    private final Map<Player, ChatChannel> playerChannels = Collections.synchronizedMap(new HashMap<>());
    
    private final JavaPlugin plugin;
    private ChatFilter chatFilter;
    
    /**
     * Creates the default chat renderer for messages.
     */
    private Supplier<ChatRenderer> createDefaultRenderer() {
        return () -> new ChatRenderer() {
            @Override
            public @NotNull Component render(
                    @NotNull final Player source,
                    @NotNull final Component sourceDisplayName,
                    @NotNull final Component message,
                    @NotNull final Audience viewer) {
                return Component.translatable("chat.type.text", sourceDisplayName, message);
            }
        };
    }
    
    /**
     * Converts a Component message to plain text.
     */
    private String toPlainText(final Component message) {
        return PlainTextComponentSerializer.plainText().serialize(message);
    }
    
    /**
     * Determines the target channel and processes the message prefix.
     */
    private ChannelResult determineChannel(final Player player, final Component message) {
        final String plainText = toPlainText(message);
        
        // Check for built-in channel prefixes
        for (final BuiltInChatChannel channel : EnumSet.allOf(BuiltInChatChannel.class)) {
            final Optional<String> prefix = channel.getMessagePrefix().map(String::valueOf);
            if (prefix.isPresent() && plainText.startsWith(prefix.get())) {
                final Component processedMessage = message.replaceText(
                    TextReplacementConfig.builder()
                        .matchLiteral(prefix.get())
                        .once()
                        .replacement("")
                        .build()
                );
                return new ChannelResult(channel, processedMessage);
            }
        }
        
        // Use player's current channel or default to GLOBAL
        final ChatChannel channel = playerChannels.getOrDefault(player, BuiltInChatChannel.GLOBAL);
        return new ChannelResult(channel, message);
    }
    
    /**
     * Processes and adjusts a message for a specific channel.
     */
    private Optional<ProcessedMessage> processMessage(
            final Player sender, 
            final ChatChannel channel, 
            final Component message) {
        
        // Check if channel is silenced
        if (silencedChannels.contains(channel)) {
            return Optional.empty();
        }
        
        // Filter the message
        final Component filteredMessage;
        try {
            filteredMessage = applyFilter(message);
        } catch (final Exception e) {
            log.error("Failed to filter message", e);
            sender.sendMessage(Component.text("Chat filter error occurred"));
            return Optional.empty();
        }
        
        // Get renderer and audience
        final Supplier<ChatRenderer> rendererSupplier = renderers.getOrDefault(
            channel, createDefaultRenderer()
        );
        final Function<Player, Set<Audience>> audienceFunction = audienceFunctions.get(channel);
        
        if (audienceFunction == null) {
            log.warn("No audience function for channel: {}", channel.getInternalIdentifier());
            return Optional.empty();
        }
        
        final ChatRenderer renderer = rendererSupplier.get();
        final Set<Audience> audiences = audienceFunction.apply(sender);
        
        // Create and call chat event
        final StudioChatEvent event = new StudioChatEvent(sender, channel, filteredMessage, audiences, true);
        if (!event.callEvent()) {
            return Optional.empty();
        }
        
        // Re-filter if message was modified
        final Component finalMessage = event.getMessage();
        final Component finalFiltered;
        try {
            finalFiltered = toPlainText(finalMessage).equals(toPlainText(filteredMessage))
                ? filteredMessage
                : applyFilter(finalMessage);
        } catch (Exception e) {
            log.error("Failed to re-filter message", e);
            return Optional.empty();
        }
        
        return Optional.of(new ProcessedMessage(renderer, event.getViewers(), finalFiltered));
    }
    
    /**
     * Applies chat filtering to a message.
     */
    private Component applyFilter(final Component message) throws Exception {
        if (chatFilter == null) {
            return message;
        }
        
        final String plainText = toPlainText(message);
        return chatFilter.getFilteredMessage(plainText)
            .map(filtered -> message.replaceText(
                TextReplacementConfig.builder()
                    .matchLiteral(plainText)
                    .replacement(filtered)
                    .build()
            ))
            .orElse(message);
    }
    
    /**
     * Sends a message to a chat channel asynchronously.
     */
    private void sendToChannelInternal(
            final ChatChannel channel, 
            final Player sender, 
            final Component message) {
        processMessage(sender, channel, message).ifPresent(processed -> {
            final ChatRenderer renderer = processed.renderer();
            final Component msg = processed.message();
            
            for (final Audience audience : processed.audiences()) {
                final Component rendered = renderer.render(
                    sender, 
                    sender.displayName(), 
                    msg, 
                    audience
                );
                audience.sendMessage((ComponentLike) rendered);
            }
        });
    }
    
    @Override
    public void setup() {
        this.chatFilter = new LocalChatFilter(plugin);
        
        // Set up default GLOBAL channel
        renderers.put(BuiltInChatChannel.GLOBAL, createDefaultRenderer());
        audienceFunctions.put(BuiltInChatChannel.GLOBAL, sender -> Set.of(Bukkit.getServer()));
        
        log.info("ChatModule initialized");
    }
    
    @Override
    public void teardown() {
        audienceFunctions.clear();
        renderers.clear();
        silencedChannels.clear();
        playerChannels.clear();
        log.info("ChatModule torn down");
    }
    
    /**
     * Filters a Component message. This is an internal helper method.
     */
    private Component filterMessage(@NotNull final Component message) throws Exception {
        return applyFilter(message);
    }
    
    @Override
    public Optional<String> getFilteredMessage(String message) {
        if (chatFilter == null) {
            return Optional.empty();
        }
        try {
            return chatFilter.getFilteredMessage(message);
        } catch (Exception e) {
            log.error("Failed to filter message", e);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean isFiltered(@NotNull final String message) {
        if (chatFilter == null) {
            return false;
        }
        try {
            return chatFilter.getFilteredMessage(message).isPresent();
        } catch (final Exception e) {
            log.error("Failed to check filter for message: {}", message, e);
            return true;
        }
    }
    
    @Override
    public @NotNull CompletableFuture<Boolean> isFilteredAsync(@NotNull final String message) {
        return CompletableFuture.supplyAsync(
            () -> isFiltered(message),
            ForkJoinPool.commonPool()
        );
    }
    
    @Override
    public boolean isChatSilenced(@NotNull final ChatChannel channel) {
        return silencedChannels.contains(channel);
    }
    
    @Override
    public void setChatSilence(@NotNull final ChatChannel channel, final boolean silenced) {
        if (silenced) {
            silencedChannels.add(channel);
        } else {
            silencedChannels.remove(channel);
        }
    }
    
    @Override
    public void setChatRenderer(
            @NotNull final ChatChannel channel, 
            final Supplier<ChatRenderer> rendererSupplier) {
        renderers.put(
            channel, 
            rendererSupplier == null ? createDefaultRenderer() : rendererSupplier
        );
    }
    
    @Override
    public void setAudienceFunction(
            @NotNull final ChatChannel channel, 
            final Function<Player, Set<Audience>> audienceFunction) {
        audienceFunctions.put(channel, audienceFunction);
    }
    
    @Override
    public void setChatChannel(@NotNull final Player player, @NotNull final ChatChannel channel) {
        playerChannels.put(player, channel);
    }
    
    @Override
    public void sendToChatChannel(
            @NotNull final ChatChannel channel, 
            @NotNull final Player sender, 
            @NotNull final Component message) {
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin, 
            () -> sendToChannelInternal(channel, sender, message)
        );
    }
    
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        event.setCancelled(true);
        
        if (!event.isAsynchronous()) {
            throw new IllegalStateException("AsyncChatEvent must be on async thread");
        }
        
        try {
            final ChannelResult result = determineChannel(event.getPlayer(), event.message());
            processMessage(event.getPlayer(), result.channel(), result.message())
                .ifPresent(processed -> {
                    event.renderer(processed.renderer());
                    event.message(processed.message());
                    event.viewers().clear();
                    event.viewers().addAll(processed.audiences());
                    event.setCancelled(false);
                });
        } catch (final Exception e) {
            log.error("Failed to process chat message", e);
        }
    }
    
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        playerChannels.remove(event.getPlayer());
    }
    
    /**
     * Result of channel determination.
     */
    private record ChannelResult(ChatChannel channel, Component message) {}
    
    /**
     * Processed message with renderer and audiences.
     */
    private record ProcessedMessage(
        ChatRenderer renderer, 
        Set<Audience> audiences, 
        Component message
    ) {}
}

