package net.plexverse.enginebridge.modules.world;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mineplex.studio.sdk.modules.world.MineplexWorld;
import com.mineplex.studio.sdk.modules.world.config.MineplexWorldConfig;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Local implementation of MineplexWorld.
 * Supports data points loading from YAML/JSON files in the world directory.
 */
@Data
@Builder
@Slf4j
public class LocalMineplexWorld implements MineplexWorld {
    
    private final String id;
    private final World minecraftWorld;
    private final MineplexWorldConfig worldConfig;
    
    @Getter(lazy = true)
    private final Map<String, List<Location>> dataPoints = this.getDataPointsFromFile();
    
    private Path getWorldDirectory() {
        final World world = this.getMinecraftWorld();
        return Paths.get(Bukkit.getServer().getWorldContainer().getAbsolutePath(), world.getName());
    }
    
    private Map<String, List<Location>> getDataPointsFromFile() {
        final Path worldDirectory = this.getWorldDirectory();
        
        return LocalWorldDataPointsFormat.getDataPointsFile(worldDirectory)
                .map(fileInfo -> {
                    try {
                        log.info("Found world data points config {} with type {}.", fileInfo.getFile().getName(), fileInfo.getFormat());
                        
                        final LocalWorldDataPointsParser dataPointsParser =
                                new LocalWorldDataPointsParser(this.getMinecraftWorld(), fileInfo.getFormat());
                        final Map<String, List<Location>> loadedPoints = dataPointsParser.read(fileInfo.getFile());
                        return ImmutableMap.copyOf(loadedPoints);
                    } catch (final IOException exception) {
                        log.error("Failed to read data points from file!", exception);
                        throw new IllegalStateException("Failed to load data points!", exception);
                    }
                })
                .orElseGet(ImmutableMap::of);
    }
    
    @Override
    public List<Location> getDataPoints(final String key) {
        final List<Location> locations = this.getDataPoints().get(key);
        return locations == null ? Collections.emptyList() : ImmutableList.copyOf(locations);
    }
}

