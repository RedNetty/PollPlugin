package com.rednetty;

import com.rednetty.commands.CreatePollCommand;
import com.rednetty.commands.PollCommand;
import com.rednetty.poll.PollManager;
import com.rednetty.poll.PollStorage;
import org.bukkit.plugin.java.JavaPlugin;

public class PollPlugin extends JavaPlugin {

    private static PollPlugin instance;
    private PollManager pollManager;
    private PollStorage pollStorage;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize storage
        pollStorage = new PollStorage(this);
        if (!pollStorage.initialize()) {
            getLogger().severe("Failed to initialize database! Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize poll manager
        pollManager = new PollManager(pollStorage);

        // Register commands
        getCommand("createpoll").setExecutor(new CreatePollCommand(pollManager));
        getCommand("poll").setExecutor(new PollCommand(pollManager));

        getLogger().info("PollPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (pollStorage != null) {
            pollStorage.close();
        }
        getLogger().info("PollPlugin has been disabled!");
    }

    public static PollPlugin getInstance() {
        return instance;
    }

    public PollManager getPollManager() {
        return pollManager;
    }

    public PollStorage getPollStorage() {
        return pollStorage;
    }
}