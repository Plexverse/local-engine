package net.plexverse.enginebridge.modules.chat.filter;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Local chat filter implementation for Engine Bridge.
 * Provides basic profanity filtering without external dependencies.
 */
@Slf4j
public class LocalChatFilter implements ChatFilter {
    
    // Basic pattern for common profanity (simplified for local use)
    private static final Pattern PROFANITY_PATTERN = Pattern.compile(
        "(?i)\\b(bad|word|list)\\b", Pattern.CASE_INSENSITIVE
    );
    
    private final Plugin plugin;
    private final boolean enabled;
    
    public LocalChatFilter(@NotNull final Plugin plugin) {
        this.plugin = plugin;
        // For local development, filtering can be disabled or simplified
        this.enabled = plugin.getConfig().getBoolean("modules.chat.filter.enabled", false);
        log.info("LocalChatFilter initialized (enabled: {})", enabled);
    }
    
    @Override
    public @NotNull Optional<String> getFilteredMessage(@NotNull final String message) throws Exception {
        if (!enabled) {
            return Optional.empty();
        }
        
        // Simple local filtering - replace profanity with asterisks
        if (PROFANITY_PATTERN.matcher(message).find()) {
            final String filtered = PROFANITY_PATTERN.matcher(message).replaceAll("****");
            log.debug("Filtered message: '{}' -> '{}'", message, filtered);
            return Optional.of(filtered);
        }
        
        return Optional.empty();
    }
}

