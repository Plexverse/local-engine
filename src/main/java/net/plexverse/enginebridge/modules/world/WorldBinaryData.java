package net.plexverse.enginebridge.modules.world;

import com.mineplex.studio.sdk.modules.data.StorableBinaryData;
import com.mineplex.studio.sdk.modules.data.annotation.DataCollection;
import com.mineplex.studio.sdk.modules.data.annotation.DataKey;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Wrapper class for storing world binary data (zipped world files) in DataStorageModule.
 */
@Data
@RequiredArgsConstructor
@DataCollection(name = "worlds")
public class WorldBinaryData implements StorableBinaryData {
    
    @DataKey
    @NonNull
    private final String key; // Format: "worldBucket:worldId"
    
    private byte[] data;
    
    public WorldBinaryData(@NonNull final String worldBucket, @NonNull final String worldId) {
        this.key = worldBucket + ":" + worldId;
    }
    
    @Override
    public InputStream open() {
        if (data == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        return new ByteArrayInputStream(data);
    }
    
    @Override
    public void load(InputStream inputStream) {
        try {
            this.data = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load world binary data", e);
        }
    }
    
    @Override
    public long sizeInBytes() {
        return data != null ? data.length : 0;
    }
}

