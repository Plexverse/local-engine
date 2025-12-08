# MessagingModule

Local implementation of Mineplex's MessagingModule for Plexverse Engine Bridge using Apache Kafka.

## Overview

The MessagingModule provides inter-server messaging capabilities, allowing different game servers and containers to communicate with each other. It implements the same API as the official Mineplex Studio Engine MessagingModule, allowing seamless migration between local and production environments. The local implementation uses Apache Kafka instead of external gRPC services.

## Features

- **Inter-Server Messaging**: Send messages between different game servers/containers
- **Kafka Backend**: Uses Apache Kafka for message queuing and distribution
- **Topic-Based Routing**: Each message key gets its own Kafka topic
- **Target-Based Routing**: Messages can be targeted by namespace, project, or pod
- **Event-Based Receiving**: Messages trigger `AsyncMineplexMessageReceivedEvent` events
- **Async Operations**: All send operations are asynchronous

## Local Implementation Details

### Kafka Storage

The local implementation uses Apache Kafka instead of the official gRPC MessagingClient. This provides:

- **No External Dependencies**: Works without external messaging services
- **Direct Kafka Access**: Direct connection to Kafka for better performance
- **Configurable**: Bootstrap servers, topic prefix, and consumer group can be configured

### Configuration

Add the following to your `config.yml` to configure Kafka:

```yaml
modules:
  messaging:
    # Kafka bootstrap servers
    # Default: kafka:9092 (for Docker Compose)
    # For local development: localhost:9092
    bootstrap-servers: "kafka:9092"
    # Topic prefix for all messages
    topic-prefix: "plexverse-messages"
    # Consumer group ID for this instance
    consumer-group-id: "plexverse-engine-bridge"
```

## Usage

### Getting the MessagingModule

```java
import net.plexverse.enginebridge.modules.ModuleManager;
import com.mineplex.studio.sdk.modules.messaging.MessagingModule;
import com.mineplex.studio.sdk.modules.messaging.target.MineplexMessageTarget;

// Get the MessagingModule instance
MessagingModule messagingModule = ModuleManager.getRegisteredModule(MessagingModule.class);
```

### Registering Message Keys

Before receiving messages, you need to register the keys you want to listen for:

```java
MessagingModule messagingModule = ModuleManager.getRegisteredModule(MessagingModule.class);

// Register a key to listen for messages
messagingModule.registerKey("lobby-queue");

// Unregister a key (returns true if key was registered)
boolean wasRegistered = messagingModule.unregisterKey("lobby-queue");
```

### Listening for Messages

Messages are received via the `AsyncMineplexMessageReceivedEvent`:

```java
import com.mineplex.studio.sdk.modules.messaging.event.AsyncMineplexMessageReceivedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@EventHandler
public void onMessageReceived(AsyncMineplexMessageReceivedEvent event) {
    // Check if this is the message type we're interested in
    if (!event.getKey().equals("lobby-queue")) {
        return;
    }
    
    // Get the message object
    Optional<GameQueueingMessage> message = event.getMessageIf(GameQueueingMessage.class);
    message.ifPresent(msg -> {
        // Handle the message
        System.out.println("Received queue message: " + msg.getGameType());
    });
}
```

### Sending Messages

#### Send to a Namespace

Send a message to all containers in a namespace:

```java
MessagingModule messagingModule = ModuleManager.getRegisteredModule(MessagingModule.class);

GameQueueingMessage message = GameQueueingMessage.builder()
    .gameType(GameType.SKYWARS)
    .gameId("game123")
    .requiredPlayers(8)
    .build();

MineplexMessageTarget target = MineplexMessageTarget.matchingNamespace("my-namespace");

messagingModule.sendMessage("lobby-queue", message, target)
    .thenAccept(v -> {
        System.out.println("Message sent successfully!");
    })
    .exceptionally(ex -> {
        System.err.println("Failed to send message: " + ex.getMessage());
        return null;
    });
```

#### Send to a Project

Send a message to all containers in a project:

```java
MineplexMessageTarget target = MineplexMessageTarget.matchingProject("my-project-id");
messagingModule.sendMessage("lobby-queue", message, target);
```

#### Send to a Specific Pod

Send a message to a specific container:

```java
MineplexMessageTarget target = MineplexMessageTarget.matchingPod("my-pod-id");
messagingModule.sendMessage("lobby-queue", message, target);
```

#### Send to Multiple Targets

Send the same message to multiple targets:

```java
Collection<MineplexMessageTarget> targets = Arrays.asList(
    MineplexMessageTarget.matchingNamespace("namespace1"),
    MineplexMessageTarget.matchingNamespace("namespace2")
);

messagingModule.sendMessage("lobby-queue", message, targets);
```

#### Send Multiple Messages

Send multiple messages to the same target:

```java
Collection<GameQueueingMessage> messages = Arrays.asList(
    message1,
    message2,
    message3
);

messagingModule.sendMessages("lobby-queue", messages, target);
```

## Differences from Official Implementation

### Message Backend

- **Official**: Uses gRPC MessagingClient with external service
- **Local**: Uses Apache Kafka directly with Kafka Producer/Consumer

### Dependencies

- **Official**: Requires external messaging service
- **Local**: Requires Kafka instance (can be local or remote)

### Performance

- **Official**: Message operations go through gRPC service calls
- **Local**: Direct Kafka operations (faster for local development)

### Message Format

- **Official**: Uses protobuf Message and MessageWrapper
- **Local**: Uses JSON serialization with MessageWrapper for class path tracking

## Kafka Topics

Messages are organized into Kafka topics based on the message key:

- Topic name format: `{topic-prefix}-{key}`
- Default prefix: `plexverse-messages`
- Example: Key `"lobby-queue"` → Topic `"plexverse-messages-lobby-queue"`

Each message is stored as JSON with a wrapper containing:
- `classPath`: The fully qualified class name of the message object
- `object`: The serialized message object as bytes

## Message Routing

Messages are routed using Kafka partition keys based on the `MineplexMessageTarget`:

- **Pod Target**: Uses pod ID as partition key
- **Project Target**: Uses project ID as partition key
- **Namespace Target**: Uses namespace ID as partition key
- **Default**: Uses "default" as partition key

## API Compatibility

The MessagingModule implementation maintains full API compatibility with the official Mineplex Studio Engine MessagingModule. All methods, interfaces, and event handling work identically, allowing code to work seamlessly between local and production environments.

## See Also

- [Mineplex Studio SDK MessagingModule Documentation](https://docs.mineplex.com/docs/sdk/features/messaging)
- [Main Engine Bridge README](../../../../../../README.md)

