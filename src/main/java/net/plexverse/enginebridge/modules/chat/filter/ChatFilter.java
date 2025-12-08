package net.plexverse.enginebridge.modules.chat.filter;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Interface for chat message filtering.
 */
public interface ChatFilter {
    /**
     * Filters a chat message and returns the filtered version if changes were made.
     *
     * @param message the original message
     * @return Optional containing the filtered message if changes were made, empty otherwise
     * @throws Exception if filtering fails
     */
    @NotNull
    Optional<String> getFilteredMessage(@NotNull String message) throws Exception;
}

