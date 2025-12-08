package net.plexverse.enginebridge.modules.level;

import com.mineplex.studio.sdk.modules.level.experience.ExperienceAwardResult;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Factory class for creating ExperienceAwardResult instances using reflection.
 * Since ExperienceAwardResult is likely a final class with unknown constructors, we use reflection.
 */
@Slf4j
public class LocalExperienceAwardResult {
    
    /**
     * Creates an ExperienceAwardResult instance from the given data using reflection.
     */
    public static ExperienceAwardResult create(
            final UUID playerId,
            final long experienceAwarded,
            final int oldLevel,
            final int newLevel,
            final boolean leveledUp) {
        try {
            // Try various constructor signatures
            final Constructor<?>[] constructors = ExperienceAwardResult.class.getDeclaredConstructors();
            
            for (final Constructor<?> constructor : constructors) {
                constructor.setAccessible(true);
                final Class<?>[] paramTypes = constructor.getParameterTypes();
                
                try {
                    if (paramTypes.length == 5) {
                        return (ExperienceAwardResult) constructor.newInstance(
                                playerId, experienceAwarded, oldLevel, newLevel, leveledUp);
                    } else if (paramTypes.length == 3) {
                        return (ExperienceAwardResult) constructor.newInstance(playerId, experienceAwarded, leveledUp);
                    } else if (paramTypes.length == 2) {
                        if (paramTypes[0] == UUID.class && paramTypes[1] == long.class) {
                            return (ExperienceAwardResult) constructor.newInstance(playerId, experienceAwarded);
                        }
                    }
                } catch (final Exception e) {
                    // Try next constructor
                    continue;
                }
            }
            
            // If no constructor worked, try no-arg and set fields via reflection
            final ExperienceAwardResult instance = (ExperienceAwardResult) 
                    ExperienceAwardResult.class.getDeclaredConstructor().newInstance();
            
            // Set fields via reflection
            setField(instance, "playerId", playerId);
            setField(instance, "experienceAwarded", experienceAwarded);
            setField(instance, "oldLevel", oldLevel);
            setField(instance, "newLevel", newLevel);
            setField(instance, "leveledUp", leveledUp);
            
            return instance;
        } catch (final Exception e) {
            log.error("Failed to create ExperienceAwardResult for player: " + playerId, e);
            throw new RuntimeException("Failed to create experience award result object", e);
        }
    }
    
    private static void setField(final Object instance, final String fieldName, final Object value) {
        try {
            final java.lang.reflect.Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (final Exception e) {
            // Field might not exist or be accessible, that's okay
            log.debug("Could not set field {} on ExperienceAwardResult", fieldName);
        }
    }
}

