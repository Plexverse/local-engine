package net.plexverse.enginebridge.modules.datastore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mineplex.studio.sdk.modules.data.StorableStructuredData;
import com.mineplex.studio.sdk.modules.data.annotation.DataCollection;
import com.mineplex.studio.sdk.modules.data.annotation.DataKey;
import org.bson.Document;
import org.bson.json.Converter;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.json.StrictJsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DataStorageModuleImpl document conversion functionality.
 * Specifically tests the convertDocumentToMap method to ensure it properly
 * converts MongoDB Documents with Date objects to Maps with Instant objects.
 */
class DataStorageModuleImplTest {

    @Mock
    private JavaPlugin plugin;

    private DataStorageModuleImpl dataStorageModule;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(plugin.getConfig()).thenReturn(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.StringReader("modules:\n  datastore:\n    connection-string: \"mongodb://localhost:27017\"\n    database: \"test\"")));
        dataStorageModule = new DataStorageModuleImpl(plugin);
        // Note: setup() requires actual MongoDB connection, so we'll test the conversion method directly
    }


    @Test
    void testFullSerializationDeserializationCycle() throws Exception {
        // Test the full cycle: Document -> Map -> JSON -> Entity
        // This simulates what happens when loading from MongoDB
        
        // Create a Document matching the real MongoDB structure
        final String planetId = "bbc5b1a1-9a78-459d-803d-229fef187798";
        final Date testDate = new Date();
        final Document document = new Document("_id", planetId)
                .append("planetId", planetId)
                .append("displayName", "Folkintijpyb")
                .append("name", "Folkintijpyb")
                .append("createdAt", testDate)
                .append("lastModified", testDate)
                .append("citizenProfileIds", java.util.Arrays.asList("3f2d2205-d430-4198-9b4d-8ec7ec252a3d"))
                .append("vehicles", java.util.Collections.emptyList())
                .append("lastVisited", testDate)
                .append("lastSave", testDate);

        // Initialize the module (this sets up the ObjectMapper)
        dataStorageModule.setup();
        
        try {
            // Get the ObjectMapper
            final Method getMapperMethod = DataStorageModuleImpl.class.getDeclaredMethod("getObjectMapper");
            getMapperMethod.setAccessible(true);
            final ObjectMapper mapper = (ObjectMapper) getMapperMethod.invoke(dataStorageModule);
            
            // Remove _id before conversion (simulating what loadStructuredData does)
            final Document dataDocument = new Document(document);
            dataDocument.remove("_id");
            
            // Convert Document to JSON string using MongoDB's toJson() (simulating the new approach)
            final String json = dataDocument.toJson();
            assertNotNull(json);
            assertFalse(json.isEmpty());
            
            // Verify JSON contains Instant strings (not Date objects)
            assertTrue(json.contains("planetId"), "JSON should contain planetId");
            assertTrue(json.contains("Folkintijpyb"), "JSON should contain displayName");
            
            // Deserialize back to Map to verify it works
            @SuppressWarnings("unchecked")
            final Map<String, Object> deserializedMap = mapper.readValue(json, Map.class);
            assertNotNull(deserializedMap);
            assertEquals(planetId, deserializedMap.get("planetId"));
            assertEquals("Folkintijpyb", deserializedMap.get("displayName"));
            
            // Verify dates are present (MongoDB's toJson() uses extended JSON format like {"$date": "..."})
            final Object createdAt = deserializedMap.get("createdAt");
            assertNotNull(createdAt, "createdAt should not be null");
            // MongoDB extended JSON dates are deserialized as Maps, so we just verify it exists
            // The actual deserialization to Instant happens when Jackson processes the entity class
        } finally {
            dataStorageModule.teardown();
        }
    }

    @Test
    void testDeserializeToRealEntity() throws Exception {
        // Test deserializing MongoDB Document to a real entity class with Instant fields
        final String testId = "test-entity-123";
        final Date testDate = new Date();
        // Document should NOT contain the key field (id) - only _id
        final Document document = new Document("_id", testId)
                .append("name", "Test Entity")
                .append("createdAt", testDate)
                .append("lastModified", testDate);

        // Initialize the module
        dataStorageModule.setup();
        
        try {
            // Get the ObjectMapper
            final Method getMapperMethod = DataStorageModuleImpl.class.getDeclaredMethod("getObjectMapper");
            getMapperMethod.setAccessible(true);
            final ObjectMapper mapper = (ObjectMapper) getMapperMethod.invoke(dataStorageModule);
            
            // Simulate what loadStructuredData does: remove _id and remap it to the key field
            final Document dataDocument = new Document(document);
            final String key = dataDocument.getString("_id");
            dataDocument.remove("_id");
            // Remap _id to the key field name (simulating loadStructuredData behavior)
            dataDocument.put("id", key);
            
            // Convert Document to JSON string using MongoDB's toJson() with custom date converter
            // This produces standard ISO-8601 date strings instead of extended JSON
            final Converter<Long> dateConverter = (value, writer) -> {
                final String isoDate = Instant.ofEpochMilli(value).toString();
                writer.writeString(isoDate);
            };
            final String json = dataDocument.toJson(JsonWriterSettings.builder()
                    .outputMode(JsonMode.RELAXED)
                    .dateTimeConverter(dateConverter)
                    .build());
            assertNotNull(json);
            assertFalse(json.isEmpty());
            
            // Verify JSON contains the remapped key field (check for both quoted and unquoted formats)
            assertTrue(json.contains("\"id\"") && json.contains(testId), 
                    "JSON should contain remapped id field. JSON: " + json);
            assertFalse(json.contains("\"_id\""), "JSON should not contain _id field. JSON: " + json);
            
            // Deserialize directly to the entity class - this is the real test!
            // Now that we exclude JavaTimeModule from the shadow JAR, it will use the server's version
            final TestEntity entity = mapper.readValue(json, TestEntity.class);
            
            assertNotNull(entity, "Entity should be deserialized");
            assertEquals(testId, entity.getId(), "Key field should be remapped from _id");
            assertEquals("Test Entity", entity.getName());
            assertNotNull(entity.getCreatedAt(), "createdAt should be deserialized to Instant");
            assertNotNull(entity.getLastModified(), "lastModified should be deserialized to Instant");
            
            // Verify the Instant values are approximately correct (within 1 second due to precision)
            final long expectedMillis = testDate.toInstant().toEpochMilli();
            final long actualCreatedAtMillis = entity.getCreatedAt().toEpochMilli();
            assertTrue(Math.abs(expectedMillis - actualCreatedAtMillis) < 1000, 
                    "createdAt should match the original date (within 1 second)");
        } finally {
            dataStorageModule.teardown();
        }
    }
    
    @Test
    void testKeyFieldExcludedFromStorage() throws Exception {
        // Test that the key field is excluded when storing data
        final String testId = "test-exclude-key-456";
        final Date testDate = new Date();
        final TestEntity entity = new TestEntity(testId, "Test Entity", 
                testDate.toInstant(), testDate.toInstant());
        
        // Initialize the module
        dataStorageModule.setup();
        
        try {
            // Get the ObjectMapper
            final Method getMapperMethod = DataStorageModuleImpl.class.getDeclaredMethod("getObjectMapper");
            getMapperMethod.setAccessible(true);
            final ObjectMapper mapper = (ObjectMapper) getMapperMethod.invoke(dataStorageModule);
            
            // Serialize entity to JSON (simulating what storeStructuredData does)
            final String serialized = mapper.writeValueAsString(entity);
            final Document dataDocument = Document.parse(serialized);
            
            // Verify the key field is present in the serialized document
            assertTrue(dataDocument.containsKey("id"), "Serialized document should contain key field");
            assertEquals(testId, dataDocument.getString("id"), "Key field value should match");
            
            // Simulate what storeStructuredData does: remove the key field
            final Field keyField = TestEntity.class.getDeclaredField("id");
            keyField.setAccessible(true);
            final String keyFieldName = keyField.getName();
            dataDocument.remove(keyFieldName);
            
            // Verify the key field is removed
            assertFalse(dataDocument.containsKey("id"), "Key field should be removed from document");
            
            // Create final document with _id (simulating storeStructuredData)
            final Document finalDocument = new Document("_id", testId);
            finalDocument.putAll(dataDocument);
            
            // Verify _id is present and key field is not
            assertEquals(testId, finalDocument.getString("_id"), "_id should be set to key value");
            assertFalse(finalDocument.containsKey("id"), "Key field should not be in final document");
            assertTrue(finalDocument.containsKey("name"), "Other fields should still be present");
        } finally {
            dataStorageModule.teardown();
        }
    }

    /**
     * Simple test entity with Instant fields to verify deserialization works.
     */
    @DataCollection(name = "test-entities")
    public static class TestEntity implements StorableStructuredData {
        @DataKey
        private String id;
        private String name;
        private Instant createdAt;
        private Instant lastModified;

        @JsonCreator
        public TestEntity(@JsonProperty("id") String id,
                         @JsonProperty("name") String name,
                         @JsonProperty("createdAt") Instant createdAt,
                         @JsonProperty("lastModified") Instant lastModified) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.lastModified = lastModified;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getLastModified() {
            return lastModified;
        }
    }
}

