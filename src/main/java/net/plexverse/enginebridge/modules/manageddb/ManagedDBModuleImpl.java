package net.plexverse.enginebridge.modules.manageddb;

import com.mineplex.studio.sdk.modules.MineplexModuleImplementation;
import com.mineplex.studio.sdk.modules.manageddb.ManagedDBModule;
import com.mineplex.studio.sdk.modules.manageddb.models.MongoDatabaseConnectionInfo;
import com.mineplex.studio.sdk.modules.manageddb.models.MySQLDatabaseConnectionInfo;
import com.mineplex.studio.sdk.modules.manageddb.models.PostgreSQLDatabaseConnectionInfo;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.plugin.java.JavaPlugin;

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
    private static final int DEFAULT_MONGO_PORT = 27017;
    private static final int DEFAULT_POSTGRES_PORT = 5432;
    private static final int DEFAULT_MYSQL_PORT = 3306;
    
    private final Map<String, MongoDatabaseConnectionInfo> mongoDatabaseConnectionInfos = new ConcurrentHashMap<>(4);
    private final Map<String, MySQLDatabaseConnectionInfo> mySQLDatabaseConnectionInfos = new ConcurrentHashMap<>(4);
    private final Map<String, PostgreSQLDatabaseConnectionInfo> postgreSQLDatabaseConnectionInfos = new ConcurrentHashMap<>(4);
    private final Map<String, HikariDataSource> existingHikariPools = new ConcurrentHashMap<>(4);
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
                return MongoDatabaseConnectionInfo.builder()
                    .connectionUri(String.format("mongodb://%s:%s@mongo-%s:%d", USERNAME, PASSWORD, databaseName, DEFAULT_MONGO_PORT))
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
                return MySQLDatabaseConnectionInfo.builder()
                    .connectionUri(String.format("jdbc:mysql://mysql-%s:%d/%s", databaseName, DEFAULT_MYSQL_PORT, databaseName))
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
                return PostgreSQLDatabaseConnectionInfo.builder()
                    .connectionUri(String.format("jdbc:postgresql://postgres-%s:%d/%s", databaseName, DEFAULT_POSTGRES_PORT, databaseName))
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
            return MongoClients.create(connectionURI).getDatabase(mongoDatabaseConnectionInfo.getSchemaName());
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
