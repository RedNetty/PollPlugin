package com.rednetty.poll;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.rednetty.PollPlugin;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PollStorage {
    private final PollPlugin plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> pollsCollection;
    private boolean connected = false;

    // Default MongoDB settings
    private String connectionString = "mongodb://localhost:27017";
    private String databaseName = "pollplugin";
    private String collectionName = "polls";
    private int connectionTimeoutMs = 10000;
    private int socketTimeoutMs = 30000;
    private int maxRetries = 3;

    public PollStorage(PollPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    /**
     * Get the plugin instance
     */
    public PollPlugin getPlugin() {
        return plugin;
    }

    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();

        // Set default values if not present
        config.addDefault("mongodb.connection-string", connectionString);
        config.addDefault("mongodb.database", databaseName);
        config.addDefault("mongodb.collection", collectionName);
        config.addDefault("mongodb.connection-timeout-ms", connectionTimeoutMs);
        config.addDefault("mongodb.socket-timeout-ms", socketTimeoutMs);
        config.addDefault("mongodb.max-retries", maxRetries);
        config.options().copyDefaults(true);
        plugin.saveConfig();

        // Load values from config
        connectionString = config.getString("mongodb.connection-string", connectionString);
        databaseName = config.getString("mongodb.database", databaseName);
        collectionName = config.getString("mongodb.collection", collectionName);
        connectionTimeoutMs = config.getInt("mongodb.connection-timeout-ms", connectionTimeoutMs);
        socketTimeoutMs = config.getInt("mongodb.socket-timeout-ms", socketTimeoutMs);
        maxRetries = config.getInt("mongodb.max-retries", maxRetries);

        // Validate configuration
        if (connectionString == null || connectionString.trim().isEmpty()) {
            plugin.getLogger().warning("Invalid MongoDB connection string, using default");
            connectionString = "mongodb://localhost:27017";
        }
        if (databaseName == null || databaseName.trim().isEmpty()) {
            plugin.getLogger().warning("Invalid database name, using default");
            databaseName = "pollplugin";
        }
        if (collectionName == null || collectionName.trim().isEmpty()) {
            plugin.getLogger().warning("Invalid collection name, using default");
            collectionName = "polls";
        }
    }

    public boolean initialize() {
        return initializeWithRetry(maxRetries);
    }

    private boolean initializeWithRetry(int retries) {
        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                plugin.getLogger().info("Attempting to connect to MongoDB (attempt " + attempt + "/" + retries + ")");

                // Build connection settings
                ConnectionString connString = new ConnectionString(connectionString);
                MongoClientSettings settings = MongoClientSettings.builder()
                        .applyConnectionString(connString)
                        .applyToSocketSettings(builder ->
                                builder.connectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
                                        .readTimeout(socketTimeoutMs, TimeUnit.MILLISECONDS))
                        .build();

                // Connect to MongoDB
                mongoClient = MongoClients.create(settings);
                database = mongoClient.getDatabase(databaseName);
                pollsCollection = database.getCollection(collectionName);

                // Test the connection
                database.runCommand(new Document("ping", 1));

                // Create indexes for better performance
                createIndexes();

                connected = true;
                plugin.getLogger().info("Successfully connected to MongoDB database: " + databaseName);

                // Start connection monitoring
                startConnectionMonitoring();

                return true;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to connect to MongoDB (attempt " + attempt + "/" + retries + "): " + e.getMessage());

                if (attempt < retries) {
                    try {
                        Thread.sleep(2000 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    plugin.getLogger().severe("Failed to connect to MongoDB after " + retries + " attempts");
                    plugin.getLogger().severe("Connection string: " + connectionString);
                    plugin.getLogger().severe("Database: " + databaseName);
                    plugin.getLogger().severe("Last error: " + e.getMessage());
                }
            }
        }

        connected = false;
        return false;
    }

    private void startConnectionMonitoring() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!testConnection()) {
                    plugin.getLogger().warning("MongoDB connection lost, attempting to reconnect...");
                    connected = false;

                    // Try to reconnect
                    if (initializeWithRetry(3)) {
                        plugin.getLogger().info("Successfully reconnected to MongoDB");
                    } else {
                        plugin.getLogger().severe("Failed to reconnect to MongoDB");
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Check every 5 minutes
    }

    public boolean isConnected() {
        return connected && testConnection();
    }

    public boolean savePoll(Poll poll) {
        if (!isConnected()) {
            plugin.getLogger().warning("Cannot save poll - not connected to database");
            return false;
        }

        if (poll == null) {
            plugin.getLogger().warning("Cannot save null poll");
            return false;
        }

        try {
            Document document = poll.toDocument();
            pollsCollection.replaceOne(
                    Filters.eq("_id", poll.getPollUUID().toString()),
                    document,
                    new ReplaceOptions().upsert(true)
            );
            return true;
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while saving poll: " + e.getMessage());
            connected = false; // Mark as disconnected to trigger reconnection
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save poll: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updatePoll(Poll poll) {
        return savePoll(poll); // MongoDB upsert handles both insert and update
    }

    public Poll getPoll(UUID pollUUID) {
        if (!isConnected() || pollUUID == null) {
            return null;
        }

        try {
            Document document = pollsCollection.find(Filters.eq("_id", pollUUID.toString())).first();
            if (document != null) {
                return Poll.fromDocument(document);
            }
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while getting poll: " + e.getMessage());
            connected = false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get poll " + pollUUID + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public List<Poll> getAllActivePolls() {
        List<Poll> polls = new ArrayList<>();
        if (!isConnected()) {
            plugin.getLogger().warning("Cannot get active polls - not connected to database");
            return polls;
        }

        try {
            pollsCollection.find(Filters.eq("active", true))
                    .forEach(document -> {
                        try {
                            Poll poll = Poll.fromDocument(document);
                            if (poll != null) {
                                polls.add(poll);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to parse poll from document: " + e.getMessage());
                        }
                    });
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while loading active polls: " + e.getMessage());
            connected = false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load active polls: " + e.getMessage());
            e.printStackTrace();
        }
        return polls;
    }

    public List<Poll> getAllPolls() {
        List<Poll> polls = new ArrayList<>();
        if (!isConnected()) {
            plugin.getLogger().warning("Cannot get all polls - not connected to database");
            return polls;
        }

        try {
            pollsCollection.find()
                    .forEach(document -> {
                        try {
                            Poll poll = Poll.fromDocument(document);
                            if (poll != null) {
                                polls.add(poll);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to parse poll from document: " + e.getMessage());
                        }
                    });
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while loading all polls: " + e.getMessage());
            connected = false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load all polls: " + e.getMessage());
            e.printStackTrace();
        }
        return polls;
    }

    public boolean deletePoll(UUID pollUUID) {
        if (!isConnected() || pollUUID == null) {
            return false;
        }

        try {
            pollsCollection.deleteOne(Filters.eq("_id", pollUUID.toString()));
            return true;
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while deleting poll: " + e.getMessage());
            connected = false;
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete poll " + pollUUID + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean pollExists(UUID pollUUID) {
        if (!isConnected() || pollUUID == null) {
            return false;
        }

        try {
            return pollsCollection.countDocuments(Filters.eq("_id", pollUUID.toString())) > 0;
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while checking poll existence: " + e.getMessage());
            connected = false;
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to check if poll exists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public long getActivePollCount() {
        if (!isConnected()) {
            return 0;
        }

        try {
            return pollsCollection.countDocuments(Filters.eq("active", true));
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while counting active polls: " + e.getMessage());
            connected = false;
            return 0;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to count active polls: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    public long getTotalPollCount() {
        if (!isConnected()) {
            return 0;
        }
        try {
            return pollsCollection.countDocuments();
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while counting total polls: " + e.getMessage());
            connected = false;
            return 0;
        }
    }

    public List<Poll> getPollsByCreator(UUID creatorUUID) {
        List<Poll> polls = new ArrayList<>();
        if (!isConnected() || creatorUUID == null) {
            return polls;
        }

        try {
            pollsCollection.find(Filters.eq("creatorUUID", creatorUUID.toString()))
                    .forEach(document -> {
                        try {
                            Poll poll = Poll.fromDocument(document);
                            if (poll != null) {
                                polls.add(poll);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to parse poll from document: " + e.getMessage());
                        }
                    });
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error while loading polls by creator: " + e.getMessage());
            connected = false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load polls by creator " + creatorUUID + ": " + e.getMessage());
            e.printStackTrace();
        }
        return polls;
    }

    public void close() {
        try {
            connected = false;
            if (mongoClient != null) {
                mongoClient.close();
                plugin.getLogger().info("MongoDB connection closed");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to close MongoDB connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Utility methods for database maintenance
    public boolean createIndexes() {
        if (!isConnected()) {
            return false;
        }

        try {
            // Create index on active field for faster queries
            pollsCollection.createIndex(
                    new Document("active", 1),
                    new IndexOptions().name("active_index")
            );

            // Create index on creatorUUID for faster creator queries
            pollsCollection.createIndex(
                    new Document("creatorUUID", 1),
                    new IndexOptions().name("creator_index")
            );

            // Create index on expiresAt for cleanup operations
            pollsCollection.createIndex(
                    new Document("expiresAt", 1),
                    new IndexOptions().name("expires_index")
            );

            // Create compound index for active polls by creator
            pollsCollection.createIndex(
                    new Document("active", 1).append("creatorUUID", 1),
                    new IndexOptions().name("active_creator_index")
            );

            plugin.getLogger().info("MongoDB indexes created successfully");
            return true;
        } catch (MongoException e) {
            plugin.getLogger().warning("MongoDB error while creating indexes: " + e.getMessage());
            connected = false;
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create MongoDB indexes: " + e.getMessage());
            return false;
        }
    }

    public boolean testConnection() {
        try {
            if (database == null) {
                return false;
            }
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int cleanupExpiredPolls() {
        if (!isConnected()) {
            return 0;
        }

        try {
            List<Document> expiredPolls = new ArrayList<>();
            pollsCollection.find(Filters.and(
                    Filters.eq("active", true),
                    Filters.lt("expiresAt", java.time.LocalDateTime.now().toString())
            )).forEach(expiredPolls::add);

            int updated = 0;
            for (Document doc : expiredPolls) {
                try {
                    // Mark as inactive
                    pollsCollection.updateOne(
                            Filters.eq("_id", doc.getString("_id")),
                            new Document("$set", new Document("active", false))
                    );
                    updated++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to update expired poll: " + e.getMessage());
                }
            }

            if (updated > 0) {
                plugin.getLogger().info("Cleaned up " + updated + " expired polls from database");
            }

            return updated;
        } catch (MongoException e) {
            plugin.getLogger().severe("MongoDB error during cleanup: " + e.getMessage());
            connected = false;
            return 0;
        } catch (Exception e) {
            plugin.getLogger().warning("Error during poll cleanup: " + e.getMessage());
            return 0;
        }
    }
}