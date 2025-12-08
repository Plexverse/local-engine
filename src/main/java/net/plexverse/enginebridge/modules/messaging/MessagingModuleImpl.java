package net.plexverse.enginebridge.modules.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.messaging.MessagingModule;
import com.mineplex.studio.sdk.modules.messaging.event.AsyncMineplexMessageReceivedEvent;
import com.mineplex.studio.sdk.modules.messaging.target.MineplexMessageTarget;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

/**
 * Local implementation of MessagingModule using Kafka.
 * Provides message sending and receiving without external gRPC dependencies.
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(MessagingModule.class)
public class MessagingModuleImpl implements MessagingModule {
    
    private static final int POLLING_TICKS = 20;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JavaPlugin plugin;
    
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private BukkitTask pollingTask;
    private final Set<String> registeredKeys = ConcurrentHashMap.newKeySet();
    private String consumerGroupId;
    
    @Override
    public void setup() {
        final FileConfiguration config = plugin.getConfig();
        
        final String bootstrapServers = config.getString("modules.messaging.bootstrap-servers", "kafka:9092");
        final String topicPrefix = config.getString("modules.messaging.topic-prefix", "plexverse-messages");
        consumerGroupId = config.getString("modules.messaging.consumer-group-id", "plexverse-engine-bridge");
        
        try {
            // Create producer
            final Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
            producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);
            producer = new KafkaProducer<>(producerProps);
            
            // Create consumer
            final Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
            consumer = new KafkaConsumer<>(consumerProps);
            
            log.info("Connected to Kafka at {}", bootstrapServers);
            startMessagePolling();
        } catch (Exception e) {
            log.error("Failed to connect to Kafka", e);
            throw new RuntimeException("Failed to initialize Kafka connection", e);
        }
    }
    
    @Override
    public void teardown() {
        if (pollingTask != null) {
            pollingTask.cancel();
            pollingTask = null;
        }
        
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
        
        if (producer != null) {
            producer.close();
            producer = null;
        }
        
        log.info("Disconnected from Kafka");
    }
    
    private void startMessagePolling() {
        pollingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::pollMessages,
            POLLING_TICKS,
            POLLING_TICKS
        );
    }
    
    private void pollMessages() {
        if (consumer == null || registeredKeys.isEmpty()) {
            return;
        }
        
        try {
            // Subscribe to topics for all registered keys
            final List<String> topics = registeredKeys.stream()
                .map(key -> getTopicName(key))
                .toList();
            
            if (!topics.isEmpty()) {
                consumer.subscribe(topics);
                
                final ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                
                for (final ConsumerRecord<String, String> record : records) {
                    processMessage(record.topic(), record.value());
                }
            }
        } catch (Exception e) {
            log.error("Error during message polling", e);
        }
    }
    
    private void processMessage(final String topic, final String messageJson) {
        try {
            final MessageWrapper wrapper = objectMapper.readValue(messageJson, MessageWrapper.class);
            final String key = extractKeyFromTopic(topic);
            
            Class<?> clazz;
            try {
                clazz = findClass(wrapper.getClassPath());
                if (clazz == null) {
                    throw new ClassNotFoundException("Cannot find class " + wrapper.getClassPath());
                }
            } catch (final ClassNotFoundException e) {
                log.error("Error parsing incoming message, falling back to JsonNode", e);
                clazz = JsonNode.class;
            }
            
            final Object object;
            try {
                object = objectMapper.readValue(wrapper.getObject(), clazz);
            } catch (final IOException e) {
                log.error("Error parsing incoming message", e);
                return;
            }
            
            // Fire event asynchronously
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                final AsyncMineplexMessageReceivedEvent event = new AsyncMineplexMessageReceivedEvent(key, object);
                event.callEvent();
            });
        } catch (final Exception e) {
            log.error("Error processing message from topic {}", topic, e);
        }
    }
    
    private Class<?> findClass(final String classPath) throws ClassNotFoundException {
        for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            try {
                return Class.forName(classPath, true, plugin.getClass().getClassLoader());
            } catch (ClassNotFoundException ignored) {
                // Try next plugin
            }
        }
        return null;
    }
    
    private String getTopicName(final String key) {
        final FileConfiguration config = plugin.getConfig();
        final String prefix = config.getString("modules.messaging.topic-prefix", "plexverse-messages");
        return prefix + "-" + key;
    }
    
    private String extractKeyFromTopic(final String topic) {
        final FileConfiguration config = plugin.getConfig();
        final String prefix = config.getString("modules.messaging.topic-prefix", "plexverse-messages");
        if (topic.startsWith(prefix + "-")) {
            return topic.substring(prefix.length() + 1);
        }
        return topic;
    }
    
    @Override
    public void registerKey(@NonNull final String key) {
        Preconditions.checkArgument(!key.isEmpty(), "Key cannot be empty");
        registeredKeys.add(key);
        log.debug("Registered key {} for messaging", key);
    }
    
    @Override
    public boolean unregisterKey(@NonNull final String key) {
        Preconditions.checkArgument(!key.isEmpty(), "Key cannot be empty");
        final boolean removed = registeredKeys.remove(key);
        log.debug("Unregistered key {} from messaging", key);
        return removed;
    }
    
    private void validateTarget(final MineplexMessageTarget target) {
        Preconditions.checkArgument(
            target.getNamespaceId() == null || !target.getNamespaceId().isEmpty(),
            "Namespace ID is blank"
        );
        Preconditions.checkArgument(
            target.getPodId() == null || !target.getPodId().isEmpty(),
            "Pod ID is blank"
        );
        Preconditions.checkArgument(
            target.getProjectId() == null || !target.getProjectId().isEmpty(),
            "Project ID is blank"
        );
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> sendMessage(
            @NonNull final String key,
            @NonNull final Object message,
            @NonNull final MineplexMessageTarget target) {
        
        Preconditions.checkArgument(!key.isEmpty(), "Key cannot be empty");
        Preconditions.checkArgument(
            producer != null,
            "Module must be setup first! Call MessagingModule#setup"
        );
        validateTarget(target);
        
        return CompletableFuture.runAsync(() -> {
            try {
                final byte[] contentBytes = objectMapper.writeValueAsBytes(message);
                
                final MessageWrapper wrapper = MessageWrapper.builder()
                    .classPath(message.getClass().getName())
                    .object(contentBytes)
                    .build();
                
                final String wrapperJson = objectMapper.writeValueAsString(wrapper);
                final String topic = getTopicName(key);
                
                // Create partition key based on target (for routing)
                final String partitionKey = buildPartitionKey(target);
                
                final ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic,
                    partitionKey,
                    wrapperJson
                );
                
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.error("Failed to send message to topic {}", topic, exception);
                    } else {
                        log.debug("Sent message to topic {} partition {}", topic, metadata.partition());
                    }
                });
            } catch (final Exception e) {
                log.error("Error sending message", e);
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }
    
    private String buildPartitionKey(final MineplexMessageTarget target) {
        // Build a partition key from target information for routing
        if (target.getPodId() != null) {
            return target.getPodId();
        }
        if (target.getProjectId() != null) {
            return target.getProjectId();
        }
        if (target.getNamespaceId() != null) {
            return target.getNamespaceId();
        }
        return "default";
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> sendMessage(
            @NonNull final String key,
            @NonNull final Object message,
            @NonNull final Collection<@NonNull MineplexMessageTarget> targets) {
        
        final List<CompletableFuture<Void>> futures = targets.stream()
            .map(target -> sendMessage(key, message, target))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    @Override
    @NonNull
    public CompletableFuture<Void> sendMessages(
            @NonNull final String key,
            @NonNull final Collection<@NonNull Object> messages,
            @NonNull final MineplexMessageTarget target) {
        
        final List<CompletableFuture<Void>> futures = messages.stream()
            .map(message -> sendMessage(key, message, target))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    @Override
    public <T> boolean registerReceiveListener(@NonNull final com.mineplex.studio.sdk.modules.messaging.listener.MessageReceiveListener<T> listener) {
        // Local implementation: listeners are handled via AsyncMineplexMessageReceivedEvent
        // This method is a no-op for local implementation as we use event-based listening
        log.debug("registerReceiveListener called (local implementation uses events)");
        return true;
    }
    
    @Override
    public boolean unregisterReceiveListener(@NonNull final com.mineplex.studio.sdk.modules.messaging.listener.MessageReceiveListener<?> listener) {
        // Local implementation: listeners are handled via AsyncMineplexMessageReceivedEvent
        // This method is a no-op for local implementation as we use event-based listening
        log.debug("unregisterReceiveListener called (local implementation uses events)");
        return true;
    }
}

