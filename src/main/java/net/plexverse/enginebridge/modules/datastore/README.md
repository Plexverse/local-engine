# DataStoreModule

Local implementation of Mineplex's DataStorageModule for Plexverse Engine Bridge using MongoDB.

## Overview

The DataStoreModule provides persistent data storage for structured and binary data. It implements the same API as the official Mineplex Studio Engine DataStorageModule, allowing seamless migration between local and production environments. The local implementation uses MongoDB instead of external gRPC services.

## Features

- **Structured Data Storage**: Store and retrieve JSON-serialized objects
- **Binary Data Storage**: Store and retrieve binary data (files, images, etc.)
- **MongoDB Backend**: Uses MongoDB for persistent storage
- **Async Operations**: All operations support both synchronous and asynchronous execution
- **Automatic Serialization**: Uses Jackson ObjectMapper for JSON serialization/deserialization
- **Collection Management**: Automatically organizes data by collection names from `@DataCollection` annotations

## Local Implementation Details

### MongoDB Storage

The local implementation uses MongoDB instead of the official gRPC CustomDataStoreClient. This provides:

- **No External Dependencies**: Works without external data store services
- **Direct Database Access**: Direct connection to MongoDB for better performance
- **Configurable**: Connection string and database name can be configured

### Configuration

Add the following to your `config.yml` to configure MongoDB:

```yaml
modules:
  datastore:
    # MongoDB connection string
    # Default: mongodb://mongo:27017 (for Docker Compose)
    # For local development: mongodb://localhost:27017
    connection-string: "mongodb://mongo:27017"
    # Database name
    database: "mineplex"
```

## Usage

### Getting the DataStorageModule

```java
import net.plexverse.enginebridge.modules.ModuleManager;
import com.mineplex.studio.sdk.modules.data.DataStorageModule;
import com.mineplex.studio.sdk.modules.data.StorableStructuredData;
import com.mineplex.studio.sdk.modules.data.annotation.DataCollection;
import com.mineplex.studio.sdk.modules.data.annotation.DataKey;

// Get the DataStorageModule instance
DataStorageModule dataStorage = ModuleManager.getRegisteredModule(DataStorageModule.class);
```

### Defining Storable Data Classes

To store data, your class must:
1. Implement `StorableStructuredData` or `StorableBinaryData`
2. Be annotated with `@DataCollection` to specify the collection name
3. Have a field annotated with `@DataKey` to specify the unique key

**Example:**
```java
@DataCollection(name = "players")
public class PlayerData implements StorableStructuredData {
    @DataKey
    private String playerId;
    
    private String name;
    private int level;
    private double balance;
    
    // Getters and setters...
}
```

### Storing Structured Data

```java
DataStorageModule dataStorage = ModuleManager.getRegisteredModule(DataStorageModule.class);

// Create and store data synchronously
PlayerData playerData = new PlayerData();
playerData.setPlayerId("player123");
playerData.setName("John");
playerData.setLevel(10);
playerData.setBalance(1000.0);

dataStorage.storeStructuredData(playerData);

// Store asynchronously
CompletableFuture<Void> future = dataStorage.storeStructuredDataAsync(playerData);
future.thenRun(() -> {
    System.out.println("Data stored successfully!");
});
```

### Loading Structured Data

```java
DataStorageModule dataStorage = ModuleManager.getRegisteredModule(DataStorageModule.class);

// Load data synchronously
Optional<PlayerData> playerData = dataStorage.loadStructuredData(PlayerData.class, "player123");
if (playerData.isPresent()) {
    PlayerData data = playerData.get();
    System.out.println("Player: " + data.getName());
}

// Load asynchronously
CompletableFuture<Optional<PlayerData>> future = dataStorage.loadStructuredDataAsync(PlayerData.class, "player123");
future.thenAccept(optional -> {
    optional.ifPresent(data -> {
        System.out.println("Player: " + data.getName());
    });
});
```

### Checking if Data Exists

```java
DataStorageModule dataStorage = ModuleManager.getRegisteredModule(DataStorageModule.class);

// Check synchronously
boolean exists = dataStorage.structuredDataExists(PlayerData.class, "player123");

// Check asynchronously
CompletableFuture<Boolean> future = dataStorage.structuredDataExistsAsync(PlayerData.class, "player123");
future.thenAccept(exists -> {
    if (exists) {
        System.out.println("Data exists!");
    }
});
```

### Deleting Structured Data

```java
DataStorageModule dataStorage = ModuleManager.getRegisteredModule(DataStorageModule.class);

// Delete synchronously
dataStorage.deleteStructuredData(PlayerData.class, "player123");

// Delete asynchronously
CompletableFuture<Void> future = dataStorage.deleteStructuredDataAsync(PlayerData.class, "player123");
future.thenRun(() -> {
    System.out.println("Data deleted!");
});
```

### Binary Data Storage

Binary data works similarly to structured data, but implements `StorableBinaryData` instead:

```java
@DataCollection(name = "avatars")
public class AvatarData implements StorableBinaryData {
    @DataKey
    private String playerId;
    
    @Override
    public InputStream open() {
        // Return InputStream for binary data
        return new FileInputStream("avatar.png");
    }
    
    @Override
    public void load(InputStream inputStream) {
        // Load binary data from InputStream
        // Save to file or process as needed
    }
    
    @Override
    public long sizeInBytes() {
        return 1024; // Return size in bytes
    }
}
```

## Differences from Official Implementation

### Storage Backend

- **Official**: Uses gRPC CustomDataStoreClient with external service
- **Local**: Uses MongoDB directly with MongoDB Java Driver

### Dependencies

- **Official**: Requires external data store service
- **Local**: Requires MongoDB instance (can be local or remote)

### Performance

- **Official**: Data operations go through gRPC service calls
- **Local**: Direct database operations (faster for local development)

### Data Format

- **Official**: Uses StructuredDataObject and BinaryObject protobuf messages
- **Local**: Stores JSON strings for structured data and BSON Binary for binary data

## MongoDB Collections

Data is organized into MongoDB collections based on the `@DataCollection` annotation:

- Collection name comes from `@DataCollection(name = "...")`
- Each document uses the `@DataKey` field value as the `_id`
- Structured data is stored as JSON in a `data` field
- Binary data is stored as BSON Binary in a `data` field

## API Compatibility

The DataStorageModule implementation maintains full API compatibility with the official Mineplex Studio Engine DataStorageModule. All methods, interfaces, and annotations work identically, allowing code to work seamlessly between local and production environments.

## See Also

- [Mineplex Studio SDK DataStorageModule Documentation](https://docs.mineplex.com/docs/sdk/features/data)
- [Main Engine Bridge README](../../../../../../README.md)

