package net.plexverse.enginebridge.modules.level;

import com.mineplex.studio.sdk.modules.level.experience.MineplexPlayerExperience;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Factory class for creating MineplexPlayerExperience instances using reflection.
 * Since MineplexPlayerExperience is a final class with unknown constructors, we use reflection.
 */
@Slf4j
public class LocalPlayerExperience {
    
    /**
     * Creates a MineplexPlayerExperience instance from the given data using reflection.
     */
    public static MineplexPlayerExperience create(
            final UUID playerId,
            final int level,
            final long experience,
            final long experienceInCurrentLevel,
            final long experienceNeededForNextLevel) {
        try {
            // Try various constructor signatures
            final Constructor<?>[] constructors = MineplexPlayerExperience.class.getDeclaredConstructors();
            
            for (final Constructor<?> constructor : constructors) {
                constructor.setAccessible(true);
                final Class<?>[] paramTypes = constructor.getParameterTypes();
                
                try {
                    if (paramTypes.length == 5) {
                        return (MineplexPlayerExperience) constructor.newInstance(
                                playerId, level, experience, experienceInCurrentLevel, experienceNeededForNextLevel);
                    } else if (paramTypes.length == 3) {
                        return (MineplexPlayerExperience) constructor.newInstance(playerId, level, experience);
                    } else if (paramTypes.length == 2) {
                        if (paramTypes[0] == UUID.class && paramTypes[1] == long.class) {
                            return (MineplexPlayerExperience) constructor.newInstance(playerId, experience);
                        } else if (paramTypes[0] == UUID.class && paramTypes[1] == int.class) {
                            return (MineplexPlayerExperience) constructor.newInstance(playerId, level);
                        }
                    }
                } catch (final Exception e) {
                    // Try next constructor
                    continue;
                }
            }
            
            // If no constructor worked, try no-arg and set fields via reflection
            final MineplexPlayerExperience instance = (MineplexPlayerExperience) 
                    MineplexPlayerExperience.class.getDeclaredConstructor().newInstance();
            
            // Set fields via reflection
            setField(instance, "playerId", playerId);
            setField(instance, "level", level);
            setField(instance, "experience", experience);
            setField(instance, "experienceInCurrentLevel", experienceInCurrentLevel);
            setField(instance, "experienceNeededForNextLevel", experienceNeededForNextLevel);
            
            return instance;
        } catch (final Exception e) {
            log.error("Failed to create MineplexPlayerExperience for player: " + playerId, e);
            throw new RuntimeException("Failed to create player experience object", e);
        }
    }
    
    private static void setField(final Object instance, final String fieldName, final Object value) {
        try {
            final java.lang.reflect.Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (final Exception e) {
            // Field might not exist or be accessible, that's okay
            log.debug("Could not set field {} on MineplexPlayerExperience", fieldName);
        }
    }
}

