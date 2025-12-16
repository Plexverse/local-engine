package net.plexverse.enginebridge.modules.datastore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.mineplex.studio.jackson.MineplexJacksonModule;
import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.data.DataStorageModule;
import com.mineplex.studio.sdk.modules.data.StorableBinaryData;
import com.mineplex.studio.sdk.modules.data.StorableStructuredData;
import com.mineplex.studio.sdk.modules.data.annotation.DataCollection;
import com.mineplex.studio.sdk.modules.data.annotation.DataKey;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.json.Converter;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.json.StrictJsonWriter;
import org.bson.types.Binary;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Local implementation of DataStorageModule using MongoDB.
 * Provides structured and binary data storage without external gRPC dependencies.
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(DataStorageModule.class)
public class DataStorageModuleImpl implements DataStorageModule {
    
    private ObjectMapper objectMapper;
    private final Cache<Class<?>, Field> keyFields = Caffeine.newBuilder().build();
    private final JavaPlugin plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    
    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = getDefaultObjectMapperBuilder().build();
        }
        return objectMapper;
    }
    
    @Override
    public void setup() {
        final FileConfiguration config = plugin.getConfig();
        
        final String connectionString = config.getString("modules.datastore.connection-string", "mongodb://mongo:27017");
        final String databaseName = config.getString("modules.datastore.database", "mineplex");
        
        try {
            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(databaseName);
            log.info("Connected to MongoDB database: {}", databaseName);
            
            // Initialize ObjectMapper after MongoDB connection is established
            objectMapper = getDefaultObjectMapperBuilder().build();
        } catch (Exception e) {
            log.error("Failed to connect to MongoDB", e);
            throw new RuntimeException("Failed to initialize MongoDB connection", e);
        }
    }
    
    @Override
    public void teardown() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
            database = null;
            log.info("Disconnected from MongoDB");
        }
    }
    
    private static JsonMapper.Builder getDefaultObjectMapperBuilder() {
        final JsonMapper.Builder builder = JsonMapper.builder()
                .addModules(
                        new Jdk8Module(), 
                        new ParameterNamesModule(), 
                        new MineplexJacksonModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                // Disable problematic features that cause version incompatibility
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
        
        // Try to add JavaTimeModule if available (from server's classpath)
        // We don't bundle it to avoid version incompatibility
        try {
            final Class<?> javaTimeModuleClass = Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
            final Object javaTimeModule = javaTimeModuleClass.getDeclaredConstructor().newInstance();
            builder.addModule((Module) javaTimeModule);
            log.info("JavaTimeModule loaded from server classpath");
        } catch (ClassNotFoundException e) {
            // JavaTimeModule not available - add custom Instant deserializer as fallback
            log.warn("JavaTimeModule not available on classpath, using custom Instant deserializer", e);
            builder.addModule(new Module() {
                @Override
                public String getModuleName() {
                    return "CustomInstantModule";
                }
                
                @Override
                public Version version() {
                    return Version.unknownVersion();
                }
                
                @Override
                public void setupModule(final Module.SetupContext context) {
                    // Add serializer for Instant
                    context.addSerializers(new Serializers.Base() {
                        @Override
                        public JsonSerializer<?> findSerializer(
                                final SerializationConfig config,
                                final JavaType type,
                                final BeanDescription beanDesc) {
                            if (type.getRawClass() == Instant.class) {
                                return new JsonSerializer<Instant>() {
                                    @Override
                                    public void serialize(final Instant value, final JsonGenerator gen, final SerializerProvider serializers) throws java.io.IOException {
                                        if (value == null) {
                                            gen.writeNull();
                                        } else {
                                            gen.writeString(value.toString());
                                        }
                                    }
                                };
                            }
                            return null;
                        }
                    });
                    
                    // Add deserializer for Instant
                    context.addDeserializers(new Deserializers.Base() {
                        @Override
                        public JsonDeserializer<?> findBeanDeserializer(
                                final JavaType type,
                                final DeserializationConfig config,
                                final BeanDescription beanDesc) {
                            if (type.getRawClass() == Instant.class) {
                                return new JsonDeserializer<Instant>() {
                                    @Override
                                    public Instant deserialize(final JsonParser p, final DeserializationContext ctxt) throws java.io.IOException {
                                        final String text = p.getText();
                                        if (text == null || text.isEmpty()) {
                                            return null;
                                        }
                                        try {
                                            return Instant.parse(text);
                                        } catch (Exception ex) {
                                            throw new InvalidFormatException(
                                                    p, "Cannot deserialize value of type `java.time.Instant` from String \"" + text + "\": " + ex.getMessage(), 
                                                    p.getCurrentValue(), Instant.class);
                                        }
                                    }
                                };
                            }
                            return null;
                        }
                    });
                }
                
                @Override
                public int hashCode() {
                    return getModuleName().hashCode();
                }
                
                @Override
                public boolean equals(final Object o) {
                    return o instanceof Module && 
                           ((Module) o).getModuleName().equals(getModuleName());
                }
            });
        } catch (Exception e) {
            log.error("Failed to load JavaTimeModule or register custom Instant serializer/deserializer", e);
        }
        
        return builder;
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Field getKeyField(final Class<?> clazz) throws ExecutionException {
        if (!clazz.isAnnotationPresent(DataCollection.class)) {
            throw new IllegalArgumentException(clazz + " is not a storable data class.");
        }
        
        return keyFields.get(clazz, cacheClass -> {
            for (final Field f : cacheClass.getDeclaredFields()) {
                if (f.isAnnotationPresent(DataKey.class)) {
                    return f;
                }
            }
            
            for (final Field f : cacheClass.getFields()) {
                if (f.isAnnotationPresent(DataKey.class)) {
                    return f;
                }
            }
            
            throw new IllegalArgumentException(cacheClass + " has no key field.");
        });
    }
    
    @SuppressWarnings("rawtypes")
    private String getKey(final Class<?> clazz, final Object instance) throws IllegalAccessException, ExecutionException {
        final Field field = getKeyField(clazz);
        field.setAccessible(true);
        return (String) Preconditions.checkNotNull(field.get(instance), "Key field is null.");
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String getCollectionName(final Class<?> clazz) {
        if (!clazz.isAnnotationPresent(DataCollection.class)) {
            throw new IllegalArgumentException(clazz + " is not a storable data class.");
        }
        
        final DataCollection dataCollection = clazz.getAnnotation(DataCollection.class);
        return dataCollection.name();
    }
    
    @Override
    public <T extends StorableStructuredData> void storeStructuredData(@NonNull final T data) {
        try {
            final String collectionName = getCollectionName(data.getClass());
            final String key = getKey(data.getClass(), data);
            
            // Serialize to JSON string, then parse to Document to get all fields
            final String serialized = getObjectMapper().writeValueAsString(data);
            final Document dataDocument = Document.parse(serialized);
            
            // Remove the key field from the document (we use _id instead)
            final Field keyField = getKeyField(data.getClass());
            keyField.setAccessible(true);
            final String keyFieldName = keyField.getName();
            dataDocument.remove(keyFieldName);
            
            // Create document with _id and all data fields (excluding key field)
            final Document document = new Document("_id", key);
            document.putAll(dataDocument);
            
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            collection.replaceOne(
                    new Document("_id", key),
                    document,
                    new ReplaceOptions().upsert(true)
            );
            log.info("[DataStorage] Stored structured data: collection={}, key={}", collectionName, key);
        } catch (final Exception e) {
            log.error("Failed to store structured data", e);
        }
    }
    
    @Override
    @Synchronized
    public void resetObjectMapper(@NonNull final Consumer<JsonMapper.Builder> mapperCustomizer) {
        final JsonMapper.Builder builder = getDefaultObjectMapperBuilder();
        mapperCustomizer.accept(builder);
        objectMapper = builder.build();
    }
    
    @Override
    public void resetObjectMapper() {
        resetObjectMapper(builder -> {});
    }
    
    @Override
    @NonNull
    public <T extends StorableStructuredData> CompletableFuture<Void> storeStructuredDataAsync(@NonNull final T data) {
        return CompletableFuture.runAsync(
                () -> storeStructuredData(data),
                ForkJoinPool.commonPool()
        );
    }
    
    @Override
    public <T extends StorableBinaryData> void storeBinaryData(@NonNull final T data) {
        try {
            final String collectionName = getCollectionName(data.getClass());
            final String key = getKey(data.getClass(), data);
            
            // Read binary data into byte array
            final InputStream inputStream = data.open();
            final byte[] bytes = inputStream.readAllBytes();
            inputStream.close();
            
            final Document document = new Document("_id", key)
                    .append("data", new Binary(bytes));
            
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            collection.replaceOne(
                    new Document("_id", key),
                    document,
                    new ReplaceOptions().upsert(true)
            );
        } catch (final Exception e) {
            log.error("Failed to store binary data", e);
        }
    }
    
    @Override
    @NonNull
    public <T extends StorableBinaryData> CompletableFuture<Void> storeBinaryDataAsync(@NonNull final T data) {
        return CompletableFuture.runAsync(
                () -> storeBinaryData(data),
                ForkJoinPool.commonPool()
        );
    }
    
    @Override
    @NonNull
    public <T extends StorableStructuredData> Optional<T> loadStructuredData(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        String json = null;
        try {
            final String collectionName = getCollectionName(dataClass);
            log.info("[DataStorage] Loading structured data: collection={}, key={}, class={}", 
                    collectionName, key, dataClass.getSimpleName());
            
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            
            final Document document = collection.find(new Document("_id", key)).first();
            if (document == null) {
                log.info("[DataStorage] Document not found: collection={}, key={}", collectionName, key);
                return Optional.empty();
            }
            
            log.info("[DataStorage] Document found: collection={}, key={}, document keys={}", 
                    collectionName, key, document.keySet());
            
            // Create a copy of the document for deserialization
            final Document dataDocument = new Document(document);
            
            // Map _id to the key field name for deserialization
            final Field keyField = getKeyField(dataClass);
            keyField.setAccessible(true);
            final String keyFieldName = keyField.getName();
            dataDocument.remove("_id");
            dataDocument.put(keyFieldName, key);
            
            // Convert entire MongoDB Document to JSON string, then deserialize
            // Use custom date converter to produce standard ISO-8601 date strings instead of extended JSON
            // This avoids JavaTimeModule version incompatibility by letting MongoDB handle date conversion
            log.info("[DataStorage] Converting document to entity via JSON serialization: collection={}, key={}", 
                    collectionName, key);
            final Converter<Long> dateConverter = (value, writer) -> {
                final String isoDate = Instant.ofEpochMilli(value).toString();
                writer.writeString(isoDate);
            };
            json = dataDocument.toJson(JsonWriterSettings.builder()
                    .outputMode(JsonMode.RELAXED)
                    .dateTimeConverter(dateConverter)
                    .build());
            log.debug("[DataStorage] Generated JSON (first 500 chars): {}", json != null ? json.substring(0, Math.min(500, json.length())) : "null");
            // Now we can use the full ObjectMapper - JavaTimeModule will use the server's version
            // which is compatible with the server's jackson-annotations
            log.debug("[DataStorage] Attempting to deserialize JSON to class: {}", dataClass.getSimpleName());
            final T result = getObjectMapper().readValue(json, dataClass);
            if (result == null) {
                log.warn("[DataStorage] Deserialization returned null: collection={}, key={}", collectionName, key);
                return Optional.empty();
            }
            
            log.info("[DataStorage] Successfully loaded: collection={}, key={}, class={}", 
                    collectionName, key, dataClass.getSimpleName());
            return Optional.of(result);
        } catch (JsonProcessingException e) {
            log.error("[DataStorage] Failed to serialize/deserialize document for key {} and class {}: {}", 
                    key, dataClass.getSimpleName(), e.getMessage(), e);
            log.error("[DataStorage] JSON that failed to deserialize: {}", json != null ? json.substring(0, Math.min(500, json.length())) : "null");
            return Optional.empty();
        } catch (final Exception e) {
            log.error("[DataStorage] Failed to load structured data: collection={}, key={}, class={}", 
                    getCollectionName(dataClass), key, dataClass.getSimpleName(), e);
            return Optional.empty();
        }
    }
    
    @Override
    @NonNull
    public <T extends StorableStructuredData> CompletableFuture<Optional<T>> loadStructuredDataAsync(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        return CompletableFuture.supplyAsync(
                () -> loadStructuredData(dataClass, key),
                ForkJoinPool.commonPool()
        );
    }
    
    @Override
    @NonNull
    public <T extends StorableBinaryData> Optional<T> loadBinaryData(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        try {
            final String collectionName = getCollectionName(dataClass);
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            
            final Document document = collection.find(new Document("_id", key)).first();
            if (document == null) {
                return Optional.empty();
            }
            
            final Object dataObj = document.get("data");
            byte[] bytes = null;
            
            if (dataObj instanceof Binary) {
                bytes = ((Binary) dataObj).getData();
            } else if (dataObj instanceof byte[]) {
                bytes = (byte[]) dataObj;
            }
            
            if (bytes == null) {
                return Optional.empty();
            }
            
            final T binaryObject = Preconditions.checkNotNull(
                    dataClass.getDeclaredConstructor().newInstance(),
                    "No declared constructor in the data class"
            );
            binaryObject.load(new ByteArrayInputStream(bytes));
            
            return Optional.of(binaryObject);
        } catch (final Exception e) {
            log.error("Failed to load binary data", e);
            return Optional.empty();
        }
    }
    
    @Override
    @NonNull
    public <T extends StorableBinaryData> CompletableFuture<Optional<T>> loadBinaryDataAsync(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        return CompletableFuture.supplyAsync(
                () -> loadBinaryData(dataClass, key),
                ForkJoinPool.commonPool()
        );
    }
    
    @Override
    public <T extends StorableStructuredData> boolean structuredDataExists(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        try {
            final String collectionName = getCollectionName(dataClass);
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            
            final long count = collection.countDocuments(new Document("_id", key));
            return count > 0;
        } catch (final Exception e) {
            log.error("Failed to check if structured data exists", e);
            return false;
        }
    }
    
    @Override
    @NonNull
    public <T extends StorableStructuredData> CompletableFuture<Boolean> structuredDataExistsAsync(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        return CompletableFuture.supplyAsync(
                () -> structuredDataExists(dataClass, key),
                ForkJoinPool.commonPool()
        );
    }
    
    @Override
    public <T extends StorableBinaryData> boolean binaryDataExists(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        try {
            final String collectionName = getCollectionName(dataClass);
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            
            final long count = collection.countDocuments(new Document("_id", key));
            return count > 0;
        } catch (final Exception e) {
            log.error("Failed to check if binary data exists", e);
            return false;
        }
    }
    
    @Override
    @NonNull
    public <T extends StorableBinaryData> CompletableFuture<Boolean> binaryDataExistsAsync(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        return CompletableFuture.supplyAsync(
                () -> binaryDataExists(dataClass, key),
                ForkJoinPool.commonPool()
        );
    }
    
    @Override
    public <T extends StorableStructuredData> void deleteStructuredData(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        try {
            final String collectionName = getCollectionName(dataClass);
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            collection.deleteOne(new Document("_id", key));
        } catch (final Exception e) {
            log.error("Failed to delete structured data", e);
        }
    }
    
    @Override
    @NonNull
    public <T extends StorableStructuredData> CompletableFuture<Void> deleteStructuredDataAsync(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        return CompletableFuture.runAsync(
                () -> deleteStructuredData(dataClass, key),
                ForkJoinPool.commonPool()
        );
    }
    
    @Override
    public <T extends StorableBinaryData> void deleteBinaryData(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        try {
            final String collectionName = getCollectionName(dataClass);
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            collection.deleteOne(new Document("_id", key));
        } catch (final Exception e) {
            log.error("Failed to delete binary data", e);
        }
    }
    
    @Override
    @NonNull
    public <T extends StorableBinaryData> CompletableFuture<Void> deleteBinaryDataAsync(
            @NonNull final Class<T> dataClass, @NonNull final String key) {
        return CompletableFuture.runAsync(
                () -> deleteBinaryData(dataClass, key),
                ForkJoinPool.commonPool()
        );
    }
    
}

