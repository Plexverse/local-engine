package net.plexverse.enginebridge.modules.level;

import com.mineplex.studio.sdk.modules.data.StorableStructuredData;
import com.mineplex.studio.sdk.modules.data.annotation.DataCollection;
import com.mineplex.studio.sdk.modules.data.annotation.DataKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data class for storing player level and experience data in MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DataCollection(name = "player_levels")
public class PlayerLevelData implements StorableStructuredData {
    
    @DataKey
    private String playerId;
    
    @Builder.Default
    private long experience = 0L;
}

