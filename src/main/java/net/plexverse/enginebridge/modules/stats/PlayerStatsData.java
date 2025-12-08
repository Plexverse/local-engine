package net.plexverse.enginebridge.modules.stats;

import com.mineplex.studio.sdk.modules.data.StorableStructuredData;
import com.mineplex.studio.sdk.modules.data.annotation.DataCollection;
import com.mineplex.studio.sdk.modules.data.annotation.DataKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Data class for storing player statistics in MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DataCollection(name = "player_stats")
public class PlayerStatsData implements StorableStructuredData {
    
    @DataKey
    private String playerId;
    
    @Builder.Default
    private Map<String, Long> stats = new HashMap<>();
}

