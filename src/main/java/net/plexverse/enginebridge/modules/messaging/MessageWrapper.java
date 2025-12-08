package net.plexverse.enginebridge.modules.messaging;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Wrapper for messages sent through the messaging system.
 * Contains the class path and serialized object data.
 */
@Jacksonized
@Value
@Builder
public class MessageWrapper {
    @NonNull
    String classPath;
    
    byte @NonNull [] object;
}

