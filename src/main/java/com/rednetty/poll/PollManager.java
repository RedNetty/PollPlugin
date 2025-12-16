package com.rednetty.poll;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.rednetty.PollPlugin;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PollManager {
    private final PollStorage storage;
    private final Map<UUID, Poll> activePolls = new HashMap<>();
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([dhm])");
    private BukkitRunnable cleanupTask;

    // Rate limiting for poll creation
    private final Map<UUID, Long> lastPollCreation = new HashMap<>();
    private final long POLL_CREATION_COOLDOWN = 60000;

    public PollManager(PollStorage storage) {
        this.storage = storage;
        loadActivePolls();
        startCleanupTask();
    }

    /**
     * Get the poll storage instance
     */
    public PollStorage getPollStorage() {
        return storage;
    }

    private void loadActivePolls() {
        try {
            List<Poll> polls = storage.getAllActivePolls();
            activePolls.clear();
            for (Poll poll : polls) {
                if (poll.isActive()) {
                    activePolls.put(poll.getPollUUID(), poll);
                } else {
                    // Auto-close expired polls during loading
                    poll.setActive(false);
                    storage.updatePoll(poll);
                }
            }
            PollPlugin.getInstance().getLogger().info("Loaded " + activePolls.size() + " active polls");
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().severe("Failed to load active polls: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredPolls();
            }
        };
        cleanupTask.runTaskTimerAsynchronously(PollPlugin.getInstance(), 6000L, 6000L);
    }

    private void cleanupExpiredPolls() {
        try {
            List<UUID> expiredPolls = new ArrayList<>();

            for (Map.Entry<UUID, Poll> entry : activePolls.entrySet()) {
                Poll poll = entry.getValue();
                if (!poll.isActive()) {
                    expiredPolls.add(entry.getKey());
                }
            }

            for (UUID pollId : expiredPolls) {
                Poll poll = activePolls.remove(pollId);
                if (poll != null) {
                    poll.setActive(false);
                    storage.updatePoll(poll);
                    PollPlugin.getInstance().getLogger().info("Auto-closed expired poll: " + poll.getQuestion());
                }
            }

            if (!expiredPolls.isEmpty()) {
                PollPlugin.getInstance().getLogger().info("Cleaned up " + expiredPolls.size() + " expired polls");
            }
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Error during poll cleanup: " + e.getMessage());
        }
    }

    public Poll createPoll(UUID creatorUUID, String question, String duration) {
        Long lastCreation = lastPollCreation.get(creatorUUID);
        if (lastCreation != null && System.currentTimeMillis() - lastCreation < POLL_CREATION_COOLDOWN) {
            return null; // Rate limited
        }

        UUID pollUUID = UUID.randomUUID();
        Poll poll = new Poll(pollUUID, creatorUUID, question);

        LocalDateTime expiresAt = parseDuration(duration);
        if (expiresAt != null) {
            poll.setExpiresAt(expiresAt);
        }

        return poll;
    }

    public boolean savePoll(Poll poll) {
        try {
            if (storage.savePoll(poll)) {
                if (poll.isActive()) {
                    activePolls.put(poll.getPollUUID(), poll);
                }
                // Update rate limiting
                lastPollCreation.put(poll.getCreatorUUID(), System.currentTimeMillis());
                return true;
            }
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().severe("Failed to save poll: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean vote(UUID pollUUID, UUID playerUUID, String option) {
        try {
            Poll poll = activePolls.get(pollUUID);
            if (poll == null || !poll.isActive() || poll.hasVoted(playerUUID)) {
                return false;
            }

            if (!poll.getOptions().contains(option)) {
                return false;
            }

            poll.vote(playerUUID, option);
            boolean success = storage.updatePoll(poll);

            if (success) {
                // Notify other players about the vote
                notifyVoteUpdate(poll, playerUUID, option);
            }

            return success;
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to process vote: " + e.getMessage());
            return false;
        }
    }

    private void notifyVoteUpdate(Poll poll, UUID voterUUID, String option) {
        // Optional potential feature??
    }

    public boolean closePoll(UUID pollUUID) {
        try {
            Poll poll = activePolls.get(pollUUID);
            if (poll != null) {
                poll.setActive(false);
                storage.updatePoll(poll);
                activePolls.remove(pollUUID);

                // Notify about poll closure
                notifyPollClosed(poll);
                return true;
            }
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to close poll: " + e.getMessage());
        }
        return false;
    }

    private void notifyPollClosed(Poll poll) {
        // Notify server about poll closure
        String message = "Poll closed: " + poll.getQuestion();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("poll.notifications")) {
                player.sendMessage("§6[Poll] §e" + message);
            }
        }
    }

    public boolean removePoll(UUID pollUUID) {
        try {
            activePolls.remove(pollUUID);
            return storage.deletePoll(pollUUID);
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to remove poll: " + e.getMessage());
            return false;
        }
    }

    public List<Poll> getActivePolls() {
        // Filter out expired polls and update cache
        List<Poll> active = new ArrayList<>();
        Iterator<Map.Entry<UUID, Poll>> iterator = activePolls.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Poll> entry = iterator.next();
            Poll poll = entry.getValue();

            if (poll.isActive()) {
                active.add(poll);
            } else {
                // Auto-close expired polls
                iterator.remove();
                poll.setActive(false);
                storage.updatePoll(poll);
            }
        }

        return active;
    }

    public Poll getPoll(UUID pollUUID) {
        if (pollUUID == null) {
            return null;
        }

        try {
            // First check active polls cache
            Poll poll = activePolls.get(pollUUID);
            if (poll != null && !poll.isActive()) {
                // Remove expired poll from cache
                activePolls.remove(pollUUID);
                poll.setActive(false);
                storage.updatePoll(poll);
                return poll; // Still return it for management purposes
            }

            // If not in cache or not active, get from storage
            if (poll == null) {
                poll = storage.getPoll(pollUUID);
            }

            return poll;
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to get poll: " + e.getMessage());
            return null;
        }
    }

    public Poll getPoll(String pollId) {
        if (pollId == null || pollId.trim().isEmpty()) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(pollId);
            return getPoll(uuid);
        } catch (IllegalArgumentException e) {
            // Try to find by short ID (first 8 characters)
            if (pollId.length() >= 8) {
                try {
                    List<Poll> allPolls = storage.getAllPolls();
                    for (Poll poll : allPolls) {
                        if (poll.getPollUUID().toString().startsWith(pollId)) {
                            return poll;
                        }
                    }
                } catch (Exception ex) {
                    PollPlugin.getInstance().getLogger().warning("Failed to search polls by short ID: " + ex.getMessage());
                }
            }
            return null;
        }
    }

    public boolean hasPermission(Player player, String permission) {
        return player != null && (player.hasPermission(permission) || player.isOp());
    }

    public boolean canCreatePoll(Player player) {
        if (!hasPermission(player, "poll.create")) {
            return false;
        }

        // Check rate limiting
        Long lastCreation = lastPollCreation.get(player.getUniqueId());
        return lastCreation == null || System.currentTimeMillis() - lastCreation >= POLL_CREATION_COOLDOWN;
    }

    public boolean canClosePoll(Player player, Poll poll) {
        return poll != null && (hasPermission(player, "poll.close") ||
                poll.getCreatorUUID().equals(player.getUniqueId()));
    }

    public boolean canRemovePoll(Player player, Poll poll) {
        return poll != null && (hasPermission(player, "poll.remove") ||
                poll.getCreatorUUID().equals(player.getUniqueId()));
    }

    private LocalDateTime parseDuration(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            return null;
        }

        try {
            Matcher matcher = DURATION_PATTERN.matcher(duration.toLowerCase().trim());
            if (!matcher.matches()) {
                return null;
            }

            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            // Validate reasonable limits
            LocalDateTime now = LocalDateTime.now();
            switch (unit) {
                case "m":
                    if (amount < 1 || amount > 10080) return null; // 1 minute to 1 week in minutes
                    return now.plusMinutes(amount);
                case "h":
                    if (amount < 1 || amount > 168) return null; // 1 hour to 1 week
                    return now.plusHours(amount);
                case "d":
                    if (amount < 1 || amount > 30) return null; // 1 day to 30 days
                    return now.plusDays(amount);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String formatDuration(String duration) {
        LocalDateTime parsed = parseDuration(duration);
        if (parsed == null) {
            return "Invalid duration format. Use: 1d, 5h, 30m";
        }

        try {
            Matcher matcher = DURATION_PATTERN.matcher(duration.toLowerCase().trim());
            if (matcher.matches()) {
                int amount = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                switch (unit) {
                    case "m":
                        return amount + " minute" + (amount != 1 ? "s" : "");
                    case "h":
                        return amount + " hour" + (amount != 1 ? "s" : "");
                    case "d":
                        return amount + " day" + (amount != 1 ? "s" : "");
                }
            }
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Error formatting duration: " + e.getMessage());
        }

        return duration;
    }

    public boolean isValidDuration(String duration) {
        return parseDuration(duration) != null;
    }

    public void refreshActivePolls() {
        try {
            // Reload polls from database and update cache
            loadActivePolls();
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to refresh active polls: " + e.getMessage());
        }
    }

    public int getActivePollCount() {
        return getActivePolls().size();
    }

    public List<Poll> getPollsByCreator(UUID creatorUUID) {
        if (creatorUUID == null) {
            return new ArrayList<>();
        }

        try {
            return storage.getPollsByCreator(creatorUUID);
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to get polls by creator: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Poll> getClosedPolls() {
        try {
            return storage.getAllPolls().stream()
                    .filter(poll -> !poll.isActive())
                    .collect(Collectors.toList()); // Compatible with older Java versions
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to get closed polls: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Poll> getAllPolls() {
        try {
            return storage.getAllPolls();
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Failed to get all polls: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public long getRemainingCooldown(UUID playerUUID) {
        Long lastCreation = lastPollCreation.get(playerUUID);
        if (lastCreation == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - lastCreation;
        return Math.max(0, POLL_CREATION_COOLDOWN - elapsed);
    }

    public void shutdown() {
        try {
            if (cleanupTask != null && !cleanupTask.isCancelled()) {
                cleanupTask.cancel();
            }

            // Final cleanup of expired polls
            cleanupExpiredPolls();

            PollPlugin.getInstance().getLogger().info("PollManager shutdown complete");
        } catch (Exception e) {
            PollPlugin.getInstance().getLogger().warning("Error during PollManager shutdown: " + e.getMessage());
        }
    }
}