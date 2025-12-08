package net.plexverse.enginebridge.modules.world;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local parser for world data points files.
 * Supports YAML and JSON formats.
 */
@Slf4j
public class LocalWorldDataPointsParser {
    private final ObjectMapper objectMapper;
    private final World world;

    public LocalWorldDataPointsParser(final World world, final LocalWorldDataPointsFormat format) {
        this.world = world;
        this.objectMapper = switch (format) {
            case JSON -> new ObjectMapper();
            case YAML -> new ObjectMapper(new YAMLFactory());
        };
    }

    public Map<String, List<Location>> read(final File file) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return this.read(inputStream);
        }
    }

    public Map<String, List<Location>> read(final InputStream inputStream) throws IOException {
        final byte[] bytes = inputStream.readAllBytes();
        if (bytes.length == 0) {
            return new HashMap<>();
        }

        // Parse as Map<String, List<Map<String, Object>>>
        final TypeReference<Map<String, List<Map<String, Object>>>> typeRef = new TypeReference<>() {};
        final Map<String, List<Map<String, Object>>> rawData = objectMapper.readValue(bytes, typeRef);
        
        // Convert to Map<String, List<Location>>
        final Map<String, List<Location>> result = new HashMap<>();
        for (final Map.Entry<String, List<Map<String, Object>>> entry : rawData.entrySet()) {
            final List<Location> locations = entry.getValue().stream()
                    .map(this::parseLocation)
                    .filter(loc -> loc != null)
                    .toList();
            result.put(entry.getKey(), locations);
        }
        
        return result;
    }
    
    private Location parseLocation(final Map<String, Object> locationMap) {
        try {
            final double x = getDouble(locationMap, "x");
            final double y = getDouble(locationMap, "y");
            final double z = getDouble(locationMap, "z");
            final float yaw = getFloat(locationMap, "yaw", 0.0f);
            final float pitch = getFloat(locationMap, "pitch", 0.0f);
            
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            log.warn("Failed to parse location from map: {}", locationMap, e);
            return null;
        }
    }
    
    private double getDouble(final Map<String, Object> map, final String key) {
        final Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Missing or invalid double value for key: " + key);
    }
    
    private float getFloat(final Map<String, Object> map, final String key, final float defaultValue) {
        final Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }
}

