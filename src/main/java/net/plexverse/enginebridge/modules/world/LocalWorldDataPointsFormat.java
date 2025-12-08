package net.plexverse.enginebridge.modules.world;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;

public enum LocalWorldDataPointsFormat {
    YAML("yaml"),
    JSON("json");

    private static final String BASE_DATA_POINTS_FILE_NAME = "dataPoints.";
    
    private final String fileExtension;

    LocalWorldDataPointsFormat(final String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileName() {
        return BASE_DATA_POINTS_FILE_NAME + this.fileExtension;
    }

    public File resolveFile(final Path directory) {
        return directory.resolve(this.getFileName()).toFile();
    }

    public static Optional<DataPointsFileInfo> getDataPointsFile(final Path worldDirectory) {
        for (final LocalWorldDataPointsFormat format : EnumSet.allOf(LocalWorldDataPointsFormat.class)) {
            final File dataPointsFile = format.resolveFile(worldDirectory);
            if (dataPointsFile.exists()) {
                return Optional.of(new DataPointsFileInfo(dataPointsFile, format));
            }
        }
        return Optional.empty();
    }
    
    public static class DataPointsFileInfo {
        private final File file;
        private final LocalWorldDataPointsFormat format;
        
        public DataPointsFileInfo(final File file, final LocalWorldDataPointsFormat format) {
            this.file = file;
            this.format = format;
        }
        
        public File getFile() {
            return file;
        }
        
        public LocalWorldDataPointsFormat getFormat() {
            return format;
        }
    }
}

