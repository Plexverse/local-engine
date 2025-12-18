package net.plexverse.enginebridge.modules.manageddb;

import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.manageddb.ManagedDBModule;
import com.mineplex.studio.sdk.modules.manageddb.models.MongoDatabaseConnectionInfo;
import com.mineplex.studio.sdk.modules.manageddb.models.MySQLDatabaseConnectionInfo;
import com.mineplex.studio.sdk.modules.manageddb.models.PostgreSQLDatabaseConnectionInfo;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

/**
 * Local implementation of ManagedDBModule for engine-bridge.
 * Provides managed database connections with automatic connection string generation.
 * 
 * <p>Connection strings follow the pattern: [type]-[dbName]
 * Examples: mongo-mydb, postgres-mydb, mysql-mydb
 * 
 * <p>Username and password are always "local-engine" for all connections.
 */
@Slf4j
@RequiredArgsConstructor
@MineplexModuleImplementation(ManagedDBModule.class)
public class ManagedDBModuleImpl implements ManagedDBModule {
    
    private static final String USERNAME = "local-engine";
    private static final String PASSWORD = "local-engine";
    private static final int BASE_MONGO_PORT = 27018;  // 27017 is reserved for main mongodb service
    private static final int BASE_POSTGRES_PORT = 5433;  // 5432 is reserved for main postgres service
    private static final int BASE_MYSQL_PORT = 3307;  // 3306 is reserved for main mysql service
    private static final int PORT_RANGE = 100;  // Ports will be BASE_PORT + (hash % PORT_RANGE)
    
    /**
     * Calculate predictable port for a database based on its name.
     * Uses MD5 hash to ensure consistent port assignment matching the Python script.
     */
    private static int getMongoPort(String databaseName) {
        return BASE_MONGO_PORT + (getMd5Hash(databaseName) % PORT_RANGE);
    }
    
    private static int getPostgresPort(String databaseName) {
        return BASE_POSTGRES_PORT + (getMd5Hash(databaseName) % PORT_RANGE);
    }
    
    private static int getMysqlPort(String databaseName) {
        return BASE_MYSQL_PORT + (getMd5Hash(databaseName) % PORT_RANGE);
    }
    
    /**
     * Calculate MD5 hash of a string and return modulo 100.
     * Matches the Python script's hashlib.md5() behavior: int(hashlib.md5(...).hexdigest(), 16) % 100
     */
    private static int getMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());
            // Convert to BigInteger (treating as unsigned, like Python's int(hexdigest(), 16))
            BigInteger bigInt = new BigInteger(1, hashBytes);
            // Take modulo 100 to match Python: hash_val % 100
            return bigInt.mod(BigInteger.valueOf(100)).intValue();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to hashCode if MD5 is unavailable (shouldn't happen)
            log.warn("MD5 algorithm not available, falling back to hashCode", e);
            return Math.abs(input.hashCode()) % PORT_RANGE;
        }
    }
    
    private final Map<String, MongoDatabaseConnectionInfo> mongoDatabaseConnectionInfos = new ConcurrentHashMap<>(4);
    private final Map<String, MySQLDatabaseConnectionInfo> mySQLDatabaseConnectionInfos = new ConcurrentHashMap<>(4);
    private final Map<String, PostgreSQLDatabaseConnectionInfo> postgreSQLDatabaseConnectionInfos = new ConcurrentHashMap<>(4);
    private final Map<String, HikariDataSource> existingHikariPools = new ConcurrentHashMap<>(4);
    private final Map<String, MongoClient> existingMongoClients = new ConcurrentHashMap<>(4);
    private final Map<String, MongoDatabase> existingMongoPools = new ConcurrentHashMap<>(4);
    private final JavaPlugin plugin;
    
    @Override
    public void setup() {
        log.info("ManagedDBModule initialized");
    }
    
    @Override
    public void teardown() {
        mongoDatabaseConnectionInfos.clear();
        mySQLDatabaseConnectionInfos.clear();
        postgreSQLDatabaseConnectionInfos.clear();
        existingHikariPools.values().forEach(HikariDataSource::close);
        existingHikariPools.clear();
        existingMongoClients.values().forEach(MongoClient::close);
        existingMongoClients.clear();
        existingMongoPools.clear();
        log.info("ManagedDBModule torn down");
    }
    
    @Override
    @NonNull
    public CompletableFuture<MongoDatabaseConnectionInfo> getMongoDatabaseConnectionInfo(@NonNull final String databaseName) {
        Preconditions.checkNotNull(databaseName, "databaseName is marked non-null but is null");
        return CompletableFuture.supplyAsync(() -> {
            return mongoDatabaseConnectionInfos.computeIfAbsent(databaseName, (k) -> {
                // Build MongoDB connection info for local implementation
                // Use service name for Docker network communication (internal port 27017)
                // Published port is calculated predictably but only used for external access
                return MongoDatabaseConnectionInfo.builder()
                    .id(databaseName)
                    .databaseName(databaseName)
                    .namespaceId(databaseName)
                    .connectionUri(String.format("mongodb://%s:%s@mongo-%s:27017/%s?authSource=admin", USERNAME, PASSWORD, databaseName, databaseName))
                    .schemaName(databaseName)
                    .build();
            });
        }, ForkJoinPool.commonPool());
    }
    
    @Override
    @NonNull
    public CompletableFuture<MySQLDatabaseConnectionInfo> getMySQLDatabaseConnectionInfo(@NonNull final String databaseName) {
        Preconditions.checkNotNull(databaseName, "databaseName is marked non-null but is null");
        return CompletableFuture.supplyAsync(() -> {
            return mySQLDatabaseConnectionInfos.computeIfAbsent(databaseName, (k) -> {
                // Build MySQL connection info for local implementation
                // Use service name for Docker network communication (internal port 3306)
                return MySQLDatabaseConnectionInfo.builder()
                    .connectionUri(String.format("jdbc:mysql://mysql-%s:3306/%s", databaseName, databaseName))
                    .username(USERNAME)
                    .password(PASSWORD)
                    .schemaName(databaseName)
                    .build();
            });
        }, ForkJoinPool.commonPool());
    }
    
    @Override
    @NonNull
    public CompletableFuture<PostgreSQLDatabaseConnectionInfo> getPostgreSQLDatabaseConnectionInfo(@NonNull final String databaseName) {
        Preconditions.checkNotNull(databaseName, "databaseName is marked non-null but is null");
        return CompletableFuture.supplyAsync(() -> {
            return postgreSQLDatabaseConnectionInfos.computeIfAbsent(databaseName, (k) -> {
                // Build PostgreSQL connection info for local implementation
                // Use service name for Docker network communication (internal port 5432)
                return PostgreSQLDatabaseConnectionInfo.builder()
                    .connectionUri(String.format("jdbc:postgresql://postgres-%s:5432/%s", databaseName, databaseName))
                    .username(USERNAME)
                    .password(PASSWORD)
                    .schemaName(databaseName)
                    .build();
            });
        }, ForkJoinPool.commonPool());
    }
    
    @Override
    @NonNull
    public MongoDatabase openManagedMongoClient(@NonNull final String databaseName) {
        Preconditions.checkNotNull(databaseName, "databaseName is marked non-null but is null");
        return existingMongoPools.computeIfAbsent(databaseName, (k) -> {
            MongoDatabaseConnectionInfo mongoDatabaseConnectionInfo = getMongoDatabaseConnectionInfo(databaseName).join();
            String connectionURI = mongoDatabaseConnectionInfo.getConnectionUri();
            // Cache MongoClient per database to reuse connections
            MongoClient mongoClient = existingMongoClients.computeIfAbsent(databaseName, (key) -> {
                return MongoClients.create(connectionURI);
            });
            return mongoClient.getDatabase(mongoDatabaseConnectionInfo.getSchemaName());
        });
    }
    
    @Override
    @NonNull
    public Connection openManagedMySQLConnection(@NonNull final String databaseName) throws SQLException {
        Preconditions.checkNotNull(databaseName, "databaseName is marked non-null but is null");
        return existingHikariPools.computeIfAbsent("MySQL:" + databaseName, (k) -> {
            MySQLDatabaseConnectionInfo mySQLDatabaseConnectionInfo = getMySQLDatabaseConnectionInfo(databaseName).join();
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(mySQLDatabaseConnectionInfo.getConnectionUri());
            hikariConfig.setUsername(mySQLDatabaseConnectionInfo.getUsername());
            hikariConfig.setPassword(mySQLDatabaseConnectionInfo.getPassword());
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            return new HikariDataSource(hikariConfig);
        }).getConnection();
    }
    
    @Override
    @NonNull
    public Connection openManagedPostgreSQLConnection(@NonNull final String databaseName) throws SQLException {
        Preconditions.checkNotNull(databaseName, "databaseName is marked non-null but is null");
        return existingHikariPools.computeIfAbsent("PostgreSQL:" + databaseName, (k) -> {
            PostgreSQLDatabaseConnectionInfo postgreSQLDatabaseConnectionInfo = getPostgreSQLDatabaseConnectionInfo(databaseName).join();
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setDriverClassName("org.postgresql.Driver");
            hikariConfig.setJdbcUrl(postgreSQLDatabaseConnectionInfo.getConnectionUri());
            hikariConfig.setUsername(postgreSQLDatabaseConnectionInfo.getUsername());
            hikariConfig.setPassword(postgreSQLDatabaseConnectionInfo.getPassword());
            return new HikariDataSource(hikariConfig);
        }).getConnection();
    }
}
