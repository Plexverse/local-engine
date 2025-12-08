package net.plexverse.enginebridge.modules.datastore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.bson.types.Binary;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
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
    
    private ObjectMapper objectMapper = getDefaultObjectMapperBuilder().build();
    private final Cache<Class<?>, Field> keyFields = Caffeine.newBuilder().build();
    private final JavaPlugin plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    
    @Override
    public void setup() {
        final FileConfiguration config = plugin.getConfig();
        
        final String connectionString = config.getString("modules.datastore.connection-string", "mongodb://mongo:27017");
        final String databaseName = config.getString("modules.datastore.database", "mineplex");
        
        try {
            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(databaseName);
            log.info("Connected to MongoDB database: {}", databaseName);
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
        return JsonMapper.builder()
                .addModules(
                        new Jdk8Module(), 
                        new ParameterNamesModule(), 
                        new JavaTimeModule(), 
                        new MineplexJacksonModule())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
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
            
            final String serialized = objectMapper.writeValueAsString(data);
            final Document document = new Document("_id", key)
                    .append("data", serialized);
            
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            collection.replaceOne(
                    new Document("_id", key),
                    document,
                    new ReplaceOptions().upsert(true)
            );
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
        try {
            final String collectionName = getCollectionName(dataClass);
            final MongoCollection<Document> collection = database.getCollection(collectionName);
            
            final Document document = collection.find(new Document("_id", key)).first();
            if (document == null) {
                return Optional.empty();
            }
            
            final String serialized = document.getString("data");
            if (serialized == null) {
                return Optional.empty();
            }
            
            return Optional.ofNullable(objectMapper.readValue(serialized, dataClass));
        } catch (JsonProcessingException e) {
            log.warn("Failed to load structured data for key {} and class {}", key, dataClass, e);
            return Optional.empty();
        } catch (final Exception e) {
            log.error("Failed to load structured data", e);
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

